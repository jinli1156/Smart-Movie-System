package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.SchedulingDraftRequest;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 排片工作台服务，负责读取整日排片快照、校验草案并应用变更。
 * 草案表示目标日期的完整排片，未保留的原场次会被移除；已售、待付款或已开场的场次必须原样保留。
 * 检查草案时不写库，应用时加锁后重新校验，并在同一事务中完成场次替换。
 */
@Service
@RequiredArgsConstructor
public class SchedulingWorkspaceService {
    private final ShowtimeMapper showtimeMapper;
    private final HallMapper hallMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final TicketOrderMapper orderMapper;
    private final SchedulingIntelligenceService intelligence;
    /** 系统要求的最短清场时间，草案只能采用相同或更长的间隔，单位为分钟。 */
    @Value("${movie.showtime.cleanup-minutes:15}")
    private int cleanupMinutes = 15;

    /**
     * 获取指定影院、日期的排片工作台数据，标记不可编辑的场次。
     *
     * @param cinemaId 影院 ID
     * @param date 目标日期，允许今天至今天之后第 30 天（含边界）
     * @return 快照版本、当日场次、最短清场时间及该影院的影厅列表
     */
    public Map<String, Object> workspace(Long cinemaId, LocalDate date) {
        UserContext.requireCinema(cinemaId);
        validateScope(cinemaId, date);
        // 展示开场日在目标日期的场次，但版本同时覆盖邻近场次，以识别跨日排片变化。
        List<Showtime> nearby = nearby(cinemaId, date, false);
        List<Showtime> current = onDay(nearby, date);
        Set<Long> protectedIds = protectedIds(current);
        List<Map<String, Object>> items = current.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceShowtimeId", s.getId());
            item.put("movieId", s.getMovieId()); item.put("hallId", s.getHallId());
            item.put("startTime", s.getStartTime()); item.put("endTime", s.getEndTime());
            item.put("basePrice", s.getBasePrice());
            Movie movie = movieMapper.selectById(s.getMovieId());
            Hall hall = hallMapper.selectById(s.getHallId());
            item.put("movieTitle", movie == null ? "已移除影片" : movie.getTitle());
            item.put("hallName", hall == null ? "已移除影厅" : hall.getName());
            item.put("locked", protectedIds.contains(s.getId()));
            item.put("lockReason", !s.getStartTime().isAfter(LocalDateTime.now()) ? "已开场"
                    : protectedIds.contains(s.getId()) ? "已售或待付款" : "");
            return item;
        }).toList();
        return Map.of("version", version(nearby), "items", items, "minimumCleaningMinutes", cleanupMinutes,
                "halls", hallMapper.selectList(new LambdaQueryWrapper<Hall>().eq(Hall::getCinemaId, cinemaId).orderByAsc(Hall::getId)));
    }

    /**
     * 校验完整草案，返回场次变更明细和原方案、草案的评估结果，不写入数据库。
     * 此结果仅反映检查时的状态，正式应用时仍需重新校验。
     *
     * @param request 包含原快照版本和目标日期完整场次列表的草案
     * @return 校验结果、变更明细、方案评估及实际采用的清场时间
     */
    public Map<String, Object> check(SchedulingDraftRequest request) {
        UserContext.requireCinema(request.getCinemaId());
        validateScope(request.getCinemaId(), request.getTargetDate());
        Validated validated = validate(request, nearby(request.getCinemaId(), request.getTargetDate(), false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("changes", changes(validated.current(), validated.proposed()));
        result.put("original", intelligence.evaluatePlan(request.getCinemaId(), validated.current()));
        result.put("draft", intelligence.evaluatePlan(request.getCinemaId(), validated.proposed()));
        result.put("cleaningMinutes", Math.max(cleanupMinutes, request.getCleaningMinutes()));
        return result;
    }

    /**
     * 在事务内加锁、重新校验并应用草案，任一步骤失败都会回滚。
     * 采用读已提交隔离级别，使获得锁后的查询能够读取其他事务已提交的最新状态。
     *
     * @param request 待应用的完整排片草案
     * @return 新建和移除的场次数量；修改一场会同时计入新建和移除
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> apply(SchedulingDraftRequest request) {
        UserContext.requireCinema(request.getCinemaId());
        validateScope(request.getCinemaId(), request.getTargetDate());
        // 与手工排片使用同一影厅锁；场次行锁串行化正在进行的余票扣减。
        hallMapper.lockCinemaHalls(request.getCinemaId());
        Validated validated = validate(request, nearby(request.getCinemaId(), request.getTargetDate(), true));
        // 带来源 ID 的草案项对应原场次，用于区分保留、修改和移除。
        Map<Long, Showtime> proposedById = validated.proposed().stream().filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Showtime::getId, s -> s));
        int created = 0;
        int removed = 0;
        for (Showtime current : validated.current()) {
            Showtime replacement = proposedById.get(current.getId());
            if (replacement != null && same(current, replacement)) continue;
            // 修改时也换一个场次 ID，阻止读取了旧场次但尚未扣票的并发订单购买旧安排。
            current.setStatus(2);
            showtimeMapper.updateById(current);
            showtimeMapper.deleteById(current.getId());
            removed++;
        }
        // 原样保留的场次不写库；新增或修改的场次均以新记录插入。
        Map<Long, Showtime> currentById = validated.current().stream().collect(Collectors.toMap(Showtime::getId, s -> s));
        for (Showtime proposed : validated.proposed()) {
            Showtime old = currentById.get(proposed.getId());
            if (old != null && same(old, proposed)) continue;
            proposed.setId(null); proposed.setStatus(0);
            showtimeMapper.insert(proposed);
            created++;
        }
        return Map.of("created", created, "removed", removed, "message", "排片已应用");
    }

    /** 校验快照版本、场次归属、保护规则、影片影厅条件及清场冲突，并构造可应用的场次列表。 */
    private Validated validate(SchedulingDraftRequest request, List<Showtime> nearby) {
        // 排片或余票等快照字段发生变化时，拒绝基于旧版本提交的草案。
        if (!Objects.equals(version(nearby), request.getVersion())) {
            throw new BusinessException(409, "排片或售票状态已变化，请刷新排片后重新生成草案");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) throw new BusinessException("草案至少保留一场放映");
        List<Showtime> current = onDay(nearby, request.getTargetDate());
        Map<Long, Showtime> currentById = current.stream().collect(Collectors.toMap(Showtime::getId, s -> s));
        Set<Long> protectedIds = protectedIds(current);
        Set<Long> usedIds = new HashSet<>();
        List<Showtime> proposed = new ArrayList<>();
        for (SchedulingDraftRequest.Item item : request.getItems()) {
            // 来源 ID 只能引用本影院目标日期的原场次，且同一原场次不能在草案中出现多次。
            Showtime old = currentById.get(item.getSourceShowtimeId());
            if (item.getSourceShowtimeId() != null && (old == null || !usedIds.add(item.getSourceShowtimeId()))) {
                throw new BusinessException("草案包含重复场次或其他日期、影院的场次");
            }
            Showtime next = new Showtime();
            next.setId(item.getSourceShowtimeId()); next.setCinemaId(request.getCinemaId());
            next.setMovieId(item.getMovieId()); next.setHallId(item.getHallId());
            next.setStartTime(item.getStartTime()); next.setEndTime(item.getEndTime()); next.setBasePrice(item.getBasePrice());
            // 未改动的场次直接复用原记录，保留座位及状态信息，也允许保留已开场的场次。
            if (old != null && same(old, next)) { proposed.add(old); continue; }
            if (old != null && protectedIds.contains(old.getId())) throw new BusinessException("已售、待付款或已开场的场次不可修改");
            if (!item.getStartTime().toLocalDate().equals(request.getTargetDate()) || !item.getStartTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException("开场时间必须是目标日期内的未来时间");
            }
            Movie movie = movieMapper.selectById(item.getMovieId());
            if (movie == null || !Objects.equals(movie.getStatus(), 1)) throw new BusinessException("只能安排正在上映的影片");
            if (movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(request.getTargetDate())) throw new BusinessException("场次不能早于影片上映日期");
            // 影片未维护时长时按 120 分钟校验，结束时间必须与开场时间和影片时长严格对应。
            int duration = movie.getDuration() == null ? 120 : movie.getDuration();
            if (duration <= 0 || !item.getEndTime().equals(item.getStartTime().plusMinutes(duration))) {
                throw new BusinessException("结束时间必须与影片时长一致，请重新选择影片");
            }
            Hall hall = hallMapper.selectById(item.getHallId());
            if (hall == null || !Objects.equals(hall.getCinemaId(), request.getCinemaId()) || !Objects.equals(hall.getStatus(), 1)) {
                throw new BusinessException("请选择该影院的可用影厅");
            }
            // 优先使用影厅总座位数，缺失时按行列数计算；新增或替换的场次以满额余票初始化。
            int capacity = hall.getTotalSeats() != null ? hall.getTotalSeats() :
                    Optional.ofNullable(hall.getTotalRows()).orElse(0) * Optional.ofNullable(hall.getTotalCols()).orElse(0);
            if (capacity <= 0) throw new BusinessException("影厅没有可用座位");
            next.setTotalSeats(capacity); next.setAvailableSeats(capacity);
            proposed.add(next);
        }
        // 草案是整日快照，遗漏原场次意味着删除，因此所有受保护场次都必须显式保留。
        if (!usedIds.containsAll(protectedIds)) throw new BusinessException("必须保留全部已售、待付款或已开场的场次");
        // 用草案替换当日排片，并保留查询范围内其他日期的场次，一起检查跨日占厅和清场冲突。
        List<Showtime> occupied = new ArrayList<>(proposed);
        nearby.stream().filter(s -> !s.getStartTime().toLocalDate().equals(request.getTargetDate())).forEach(occupied::add);
        occupied.sort(Comparator.comparing(Showtime::getHallId).thenComparing(Showtime::getStartTime));
        // 清场时间不得低于系统下限；下一场恰好在清场结束时开场视为合法。
        int cleaning = Math.max(cleanupMinutes, request.getCleaningMinutes());
        for (int i = 0; i < occupied.size(); i++) for (int j = i + 1; j < occupied.size(); j++) {
            Showtime a = occupied.get(i), b = occupied.get(j);
            if (Objects.equals(a.getHallId(), b.getHallId()) && a.getEndTime().plusMinutes(cleaning).isAfter(b.getStartTime())) {
                // 既存且完全未改动的历史冲突不阻止调整其他场次。
                if (unchanged(a, nearby) && unchanged(b, nearby)) continue;
                throw new BusinessException("影厅场次冲突：" + a.getStartTime() + " 与 " + b.getStartTime()
                        + "，需要预留 " + cleaning + " 分钟清场（含跨日场次）");
            }
        }
        return new Validated(current, proposed);
    }

    /** 判断场次是否仍对应同一条原记录且排片内容未变，用于豁免既存冲突。 */
    private boolean unchanged(Showtime item, List<Showtime> originals) {
        return item.getId() != null && originals.stream().anyMatch(s -> Objects.equals(s.getId(), item.getId()) && same(s, item));
    }

    /** 按来源 ID 对比原方案和草案，生成新增、修改、移除明细，供应用前预览。 */
    private List<Map<String, Object>> changes(List<Showtime> current, List<Showtime> proposed) {
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Showtime item : proposed) {
            Showtime old = current.stream().filter(s -> Objects.equals(s.getId(), item.getId())).findFirst().orElse(null);
            if (old == null) changes.add(Map.of("type", "ADD", "after", item));
            else if (!same(old, item)) changes.add(Map.of("type", "UPDATE", "before", old, "after", item));
        }
        for (Showtime item : current) {
            if (proposed.stream().noneMatch(s -> Objects.equals(s.getId(), item.getId()))) changes.add(Map.of("type", "REMOVE", "before", item));
        }
        return changes;
    }

    /** 校验目标日期位于今天至今天之后第 30 天（含边界），且影院存在并处于可用状态。 */
    private void validateScope(Long cinemaId, LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(30))) throw new BusinessException("请选择今天起 30 天内的日期");
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null || !Objects.equals(cinema.getStatus(), 1)) throw new BusinessException("影院不可用");
    }

    /**
     * 查询用于版本校验和冲突检查的邻近场次，排除已取消场次。
     * 范围为结束时间晚于目标日零点前一小时，且开场时间早于目标日后第二天零点的场次。
     *
     * @param lock 是否追加行锁；应用草案时在事务内启用，以协调并发售票和排片修改
     */
    private List<Showtime> nearby(Long cinemaId, LocalDate date, boolean lock) {
        LambdaQueryWrapper<Showtime> query = new LambdaQueryWrapper<Showtime>().eq(Showtime::getCinemaId, cinemaId)
                .ne(Showtime::getStatus, 2).lt(Showtime::getStartTime, date.plusDays(2).atStartOfDay())
                .gt(Showtime::getEndTime, date.atStartOfDay().minusHours(1)).orderByAsc(Showtime::getId);
        if (lock) query.last("FOR UPDATE");
        return showtimeMapper.selectList(query);
    }

    /** 按开场日期归属筛选当日场次，跨午夜结束的场次仍属于其开场日。 */
    private List<Showtime> onDay(List<Showtime> nearby, LocalDate date) {
        return nearby.stream().filter(s -> s.getStartTime().toLocalDate().equals(date)).toList();
    }

    /** 汇总不可修改或移除的场次，包括已开场、座位信息缺失、余票减少或存在待付款／已支付订单的场次。 */
    private Set<Long> protectedIds(List<Showtime> current) {
        Set<Long> result = current.stream().filter(s -> !s.getStartTime().isAfter(LocalDateTime.now())
                || s.getTotalSeats() == null || s.getAvailableSeats() == null || s.getAvailableSeats() < s.getTotalSeats())
                .map(Showtime::getId).collect(Collectors.toSet());
        if (!current.isEmpty()) {
            // 除余票外再检查订单，状态 0 为待付款、1 为已支付，任一命中都保护对应场次。
            orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>().in(TicketOrder::getShowtimeId,
                    current.stream().map(Showtime::getId).toList()).in(TicketOrder::getStatus, 0, 1))
                    .forEach(o -> result.add(o.getShowtimeId()));
        }
        return result;
    }

    /**
     * 将邻近场次按 ID 排序后生成 SHA-256 快照摘要，避免查询顺序影响版本。
     * 摘要包含排片、价格、座位、状态和更新时间，使相关数据变化后旧草案失效。
     */
    private String version(List<Showtime> rows) {
        String value = rows.stream().sorted(Comparator.comparing(Showtime::getId)).map(s ->
                List.of(s.getId(), s.getMovieId(), s.getHallId(), s.getStartTime(), s.getEndTime(),
                        String.valueOf(s.getBasePrice()), String.valueOf(s.getTotalSeats()), String.valueOf(s.getAvailableSeats()),
                        String.valueOf(s.getStatus()), String.valueOf(s.getUpdateTime())).toString()).collect(Collectors.joining("|"));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /**
     * 比较草案可编辑的排片字段，不比较 ID、余票和状态；记录身份由调用方另行匹配。
     * 票价按数值比较，因此 30.0 与 30.00 视为相同。
     */
    private boolean same(Showtime a, Showtime b) {
        return Objects.equals(a.getMovieId(), b.getMovieId()) && Objects.equals(a.getHallId(), b.getHallId())
                && Objects.equals(a.getStartTime(), b.getStartTime()) && Objects.equals(a.getEndTime(), b.getEndTime())
                && a.getBasePrice() != null && b.getBasePrice() != null && a.getBasePrice().compareTo(b.getBasePrice()) == 0;
    }
    /** 校验后的当日原场次和拟应用场次，供变更预览、方案评估及事务写入复用。 */
    private record Validated(List<Showtime> current, List<Showtime> proposed) {}
}
