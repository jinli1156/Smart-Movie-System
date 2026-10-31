package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.SeatPreference;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.SeatRecommendationVO;
import com.movie.ticket.vo.SeatRecommendationResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 结合实时占座、影厅布局与人数生成连座方案，协调规则评分和可选的模型分析。 */
@Service
@RequiredArgsConstructor
public class SeatRecommendationService {

    private final ShowtimeMapper showtimeMapper;
    private final HallMapper hallMapper;
    private final TicketOrderMapper orderMapper;
    private final SeatAiRecommendationService seatAiRecommendationService;
    private final SeatRecommendationEngine engine = new SeatRecommendationEngine();

    /** 根据实时座位状态、人数与偏好筛选可用座位组合。 */
    public List<SeatRecommendationVO> recommend(Long showtimeId,
                                                int people,
                                                String preferenceValue,
                                                int limit) {
        if (people < 1 || people > 5) {
            throw new BusinessException("购票人数须为 1-5 人");
        }
        if (limit < 1 || limit > 5) {
            throw new BusinessException("推荐数量须为 1-5 组");
        }
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }
        Hall hall = hallMapper.selectById(showtime.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        Set<String> occupied = loadOccupiedSeats(showtimeId);
        HallSeatLayout layout = parseLayout(hall);
        return engine.recommend(hall.getTotalRows(), hall.getTotalCols(), layout.enabled(), occupied, people,
                SeatPreference.from(preferenceValue), limit, showtime.getBasePrice(), layout.aisleAfterCols());
    }

    /** 根据自然语言分析和规则评分生成可解释的座位方案。 */
    public SeatRecommendationResultVO recommendSmart(Long showtimeId, SeatRecommendationRequest request) {
        validate(request.getPeople(), request.getLimit());
        Showtime showtime = requireShowtime(showtimeId);
        Hall hall = requireHall(showtime.getHallId());
        HallSeatLayout layout = parseLayout(hall);
        Set<String> occupied = loadOccupiedSeats(showtimeId);
        SeatPreference inferred = inferPreference(request.getNaturalLanguage(), request.getPreference());
        boolean naturalLanguageMode = request.getNaturalLanguage() != null
                && !request.getNaturalLanguage().isBlank();

        List<SeatRecommendationVO> candidates = candidatePool(hall, layout, occupied,
                request.getPeople(), inferred, showtime);
        candidates.forEach(candidate -> candidate.setReasonSource("algorithm"));

        String source = "algorithm";
        String summary = preferenceLabel(inferred);
        String model = null;
        List<SeatRecommendationVO> ranked = new ArrayList<>(candidates);
        if (!candidates.isEmpty()) {
            String preferenceText = naturalLanguageMode ? request.getNaturalLanguage() : preferenceLabel(inferred);
            var analysis = seatAiRecommendationService.analyze(preferenceText, request.getPeople(),
                    hall.getTotalRows(), hall.getTotalCols(), candidates);
            if (analysis.isPresent() && analysis.get().ranking().stream().anyMatch(item ->
                    candidates.stream().anyMatch(candidate -> candidate.getSeatLabel().equals(item.seatLabel())))) {
                SeatAiRecommendationService.SeatAiAnalysis value = analysis.get();
                inferred = value.preference();
                summary = value.summary().isBlank() ? preferenceLabel(inferred) : value.summary();
                ranked = applyModelRanking(candidates, value.ranking());
                source = "model";
                model = seatAiRecommendationService.modelName();
            } else {
                summary = "模型暂不可用，已按“" + compact(preferenceText, 32) + "”提供实时座位推荐";
            }
        }

        ranked = new ArrayList<>(ranked.stream().limit(request.getLimit()).toList());

        // 模型分析期间座位可能发生变化，返回前再查一次并用算法补足安全候选。
        Set<String> latestOccupied = loadOccupiedSeats(showtimeId);
        if (!latestOccupied.equals(occupied)) {
            ranked.removeIf(item -> item.getSeats().stream().anyMatch(latestOccupied::contains));
            List<SeatRecommendationVO> fresh = engine.recommend(hall.getTotalRows(), hall.getTotalCols(), layout.enabled(),
                    latestOccupied, request.getPeople(), inferred, 5, showtime.getBasePrice(), layout.aisleAfterCols());
            appendDistinct(ranked, fresh, request.getLimit());
        }

        if (ranked.stream().noneMatch(item -> "model".equals(item.getReasonSource()))) {
            source = "algorithm";
            model = null;
        }
        return SeatRecommendationResultVO.builder()
                .recommendations(ranked.stream().limit(request.getLimit()).toList())
                .interpretedPreference(inferred.name())
                .preferenceSummary(summary)
                .analysisSource(source)
                .model(model)
                .availabilityToken(availabilityToken(latestOccupied))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    static SeatPreference inferPreference(String naturalLanguage, String fallback) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) return SeatPreference.from(fallback);
        String value = naturalLanguage.toLowerCase(Locale.ROOT);
        if (containsAny(value, "无障碍", "轮椅", "行动不便", "老人", "残障")) return SeatPreference.ACCESSIBLE;
        if (containsAny(value, "情侣", "约会", "两个人", "二人", "双人")) return SeatPreference.COUPLE;
        if (containsAny(value, "方便进出", "出口", "通道", "过道", "靠边", "常去厕所")) return SeatPreference.EASY_ACCESS;
        if (containsAny(value, "安静", "靠后", "后排", "人少", "不被打扰")) return SeatPreference.QUIET;
        if (containsAny(value, "中央", "中间", "正中", "视野", "最佳位置")) return SeatPreference.CENTER;
        return SeatPreference.from(fallback);
    }

    private List<SeatRecommendationVO> candidatePool(Hall hall,
                                                      HallSeatLayout layout,
                                                      Set<String> occupied,
                                                      int people,
                                                      SeatPreference primary,
                                                      Showtime showtime) {
        List<SeatPreference> preferences = new ArrayList<>(List.of(SeatPreference.values()));
        preferences.remove(primary);
        preferences.addFirst(primary);
        Map<String, SeatRecommendationVO> unique = new LinkedHashMap<>();
        for (SeatPreference preference : preferences) {
            List<SeatRecommendationVO> items = engine.recommend(hall.getTotalRows(), hall.getTotalCols(), layout.enabled(),
                    occupied, people, preference, 3, showtime.getBasePrice(), layout.aisleAfterCols());
            for (SeatRecommendationVO item : items) {
                unique.putIfAbsent(String.join("|", item.getSeats()), item);
            }
        }
        return unique.values().stream().limit(12).toList();
    }

    private List<SeatRecommendationVO> applyModelRanking(
            List<SeatRecommendationVO> candidates,
            List<SeatAiRecommendationService.RankedReason> ranking) {
        Map<String, SeatRecommendationVO> remaining = candidates.stream().collect(
                LinkedHashMap::new, (map, item) -> map.put(item.getSeatLabel(), item), Map::putAll);
        List<SeatRecommendationVO> result = new ArrayList<>();
        for (SeatAiRecommendationService.RankedReason ranked : ranking) {
            SeatRecommendationVO candidate = remaining.remove(ranked.seatLabel());
            if (candidate == null) continue;
            candidate.setModelReason(ranked.reason());
            candidate.setReasonSource("model");
            result.add(candidate);
        }
        result.addAll(remaining.values());
        return result;
    }

    private void appendDistinct(List<SeatRecommendationVO> target,
                                List<SeatRecommendationVO> fresh,
                                int limit) {
        Set<String> keys = target.stream().map(item -> String.join("|", item.getSeats()))
                .collect(java.util.stream.Collectors.toSet());
        for (SeatRecommendationVO item : fresh) {
            if (target.size() >= limit) break;
            if (keys.add(String.join("|", item.getSeats()))) {
                item.setReasonSource("algorithm");
                target.add(item);
            }
        }
    }

    private void validate(int people, int limit) {
        if (people < 1 || people > 5) throw new BusinessException("购票人数须为 1-5 人");
        if (limit < 1 || limit > 5) throw new BusinessException("推荐数量须为 1-5 组");
    }

    private Showtime requireShowtime(Long showtimeId) {
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) throw new BusinessException("场次不存在");
        return showtime;
    }

    private Hall requireHall(Long hallId) {
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new BusinessException("影厅不存在");
        return hall;
    }

    private String preferenceLabel(SeatPreference preference) {
        return switch (preference) {
            case CENTER -> "中央视野";
            case QUIET -> "靠后安静";
            case EASY_ACCESS -> "方便进出";
            case COUPLE -> "情侣双人";
            case ACCESSIBLE -> "无障碍友好";
        };
    }

    private String compact(String value, int max) {
        String normalized = value.trim().replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private String availabilityToken(Set<String> occupied) {
        String stable = occupied.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining("|"));
        return Integer.toHexString(stable.hashCode());
    }

    private static boolean containsAny(String value, String... keywords) {
        return Arrays.stream(keywords).anyMatch(value::contains);
    }

    private Set<String> loadOccupiedSeats(Long showtimeId) {
        List<TicketOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getShowtimeId, showtimeId)
                        .in(TicketOrder::getStatus, 0, 1));
        Set<String> occupied = new HashSet<>();
        for (TicketOrder order : orders) {
            if (order.getSeats() != null && !order.getSeats().isBlank()) {
                occupied.addAll(Arrays.asList(order.getSeats().split(",")));
            }
        }
        return occupied;
    }

    private HallSeatLayout parseLayout(Hall hall) {
        return HallSeatLayout.parse(hall.getSeatLayout(), hall.getTotalRows(), hall.getTotalCols());
    }
}
