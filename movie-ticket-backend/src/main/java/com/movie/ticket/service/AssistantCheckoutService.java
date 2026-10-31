package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.dto.OrderCreateDTO;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** A model can prepare a proposal; only the authenticated confirmation endpoint creates an order. */
@Service
@RequiredArgsConstructor
public class AssistantCheckoutService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AssistantOutingService outings;
    private final ShowtimeMapper showtimes;
    private final HallMapper halls;
    private final TicketOrderMapper tickets;
    private final OrderService orders;

    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }

    private record CheckoutRow(long showtimeId, String criteria, String snapshot,
                               LocalDateTime expiresAt, String orderNo) {}

    /** 生成五分钟有效的订单确认卡；此时不锁座，确认时仍需重新核验。 */
    public ObjectNode prepare(Long userId, JsonNode criteria, List<String> requestedSeats) {
        if (userId == null) throw new BusinessException(401, "请先登录，再让影票观准备订单");
        try { AssistantWatchPlan.validate(criteria); }
        catch (IllegalArgumentException ex) { throw new BusinessException(400, "观影条件无效，请检查场次、人数和预算"); }
        if (!criteria.has("showtimeId") || !criteria.has("people"))
            throw new BusinessException(400, "请先确定具体场次和人数，再准备订单确认卡");
        ObjectNode normalized = criteria.deepCopy();
        if (!normalized.has("date")) {
            var selected = showtimes.selectById(criteria.path("showtimeId").asLong());
            if (selected == null) throw new BusinessException(404, "场次不存在");
            normalized.put("date", selected.getStartTime().toLocalDate().toString());
        }
        criteria = normalized;
        ObjectNode preview = preview(userId, criteria, requestedSeats);
        String id = UUID.randomUUID().toString();
        LocalDateTime expires = now().plusMinutes(5);
        jdbc.update("INSERT INTO assistant_checkout(id,user_id,showtime_id,criteria,snapshot,expires_at) VALUES(?,?,?,?,?,?)",
                id, userId, criteria.get("showtimeId").asLong(), criteria.toString(), preview.toString(), Timestamp.valueOf(expires));
        preview.put("draftId", id).put("expiresAt", expires.toString()).put("status", "AWAITING_CONFIRMATION");
        preview.set("criteria", criteria.deepCopy());
        preview.put("notice", "请核对场次和座位，确认后创建待支付订单并进入支付页。当前尚未锁座；优惠券和积分在支付时再次核验。确认卡5分钟内有效，订单创建后10分钟内完成支付。");
        return preview;
    }

    private ObjectNode preview(Long userId, JsonNode criteria, List<String> requestedSeats) {
        ObjectNode single = criteria.deepCopy(); single.put("limit", 1);
        JsonNode result;
        try { result = json.valueToTree(outings.plan(single, userId)); }
        catch (IllegalArgumentException ex) { throw new BusinessException(400, "日期或观影条件无效，请重新查询后准备订单"); }
        if (result.path("plans").isEmpty()) throw new BusinessException(409, result.path("diagnosis").asText("该场次暂时无法按当前条件购票，请重新选择"));
        ObjectNode item = result.path("plans").get(0).deepCopy();
        if (item.path("showtimeId").asLong() != criteria.path("showtimeId").asLong())
            throw new BusinessException(409, "场次已变化，请重新选择");
        List<String> seats = requestedSeats == null || requestedSeats.isEmpty()
                ? json.convertValue(item.path("seats"), json.getTypeFactory().constructCollectionType(List.class, String.class)) : List.copyOf(requestedSeats);
        validateSeats(criteria.path("showtimeId").asLong(), criteria.path("people").asInt(), seats,
                criteria.path("requireTogether").asBoolean(criteria.path("people").asInt() > 1));
        item.set("seats", json.valueToTree(seats));
        item.put("usePoints", criteria.path("usePoints").asBoolean(true));
        item.put("useCoupons", criteria.path("useCoupons").asBoolean(true));
        return item;
    }

    private void validateSeats(long showtimeId, int people, List<String> seats, boolean together) {
        if (seats == null || seats.size() != people || people < 1 || people > 5 || new HashSet<>(seats).size() != people)
            throw new BusinessException(400, "需要为每人选择一个有效座位，最多5人");
        var showtime = showtimes.selectById(showtimeId);
        if (showtime == null) throw new BusinessException(409, "场次已不可用");
        var hall = halls.selectById(showtime.getHallId());
        if (hall == null || !Integer.valueOf(1).equals(hall.getStatus())) throw new BusinessException(409, "影厅已不可用");
        var layout = HallSeatLayout.parse(hall.getSeatLayout(), hall.getTotalRows(), hall.getTotalCols());
        var positions = new ArrayList<int[]>();
        for (String seat : seats) {
            var match = java.util.regex.Pattern.compile("([1-9][0-9]?)排([1-9][0-9]?)座").matcher(seat == null ? "" : seat);
            if (!match.matches()) throw new BusinessException(400, "座位格式无效，请重新选座");
            int row = Integer.parseInt(match.group(1)), col = Integer.parseInt(match.group(2));
            if (row > hall.getTotalRows() || col > hall.getTotalCols() || !layout.enabled()[row - 1][col - 1])
                throw new BusinessException(409, "所选座位已不可用，请重新选座");
            if (tickets.countActiveOrdersBySeat(showtimeId, seat) > 0)
                throw new BusinessException(409, "座位 " + seat + " 已被占用，请重新准备方案");
            positions.add(new int[]{row, col});
        }
        positions.sort(Comparator.comparingInt(p -> p[1]));
        if (together) for (int i = 1; i < positions.size(); i++) {
            int[] before = positions.get(i - 1), after = positions.get(i);
            if (before[0] != after[0] || before[1] + 1 != after[1] || layout.aisleAfterCols().contains(before[1]))
                throw new BusinessException(409, "所选座位不满足连座要求，请重新准备方案");
        }
    }

    /** 校验本人确认卡及有效期，重新核验座位后幂等创建待支付订单。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(String id) {
        Long userId = UserContext.getUserId();
        // DATETIME getObject() varies by JDBC driver (MySQL returns LocalDateTime).
        // Explicit JDBC getters keep the domain types independent of that default mapping.
        var rows = jdbc.query("SELECT showtime_id,criteria,snapshot,expires_at,order_no FROM assistant_checkout WHERE id=? AND user_id=? FOR UPDATE",
                (rs, n) -> new CheckoutRow(rs.getLong("showtime_id"), rs.getString("criteria"),
                        rs.getString("snapshot"), rs.getTimestamp("expires_at").toLocalDateTime(), rs.getString("order_no")), id, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "未找到当前账号的订单确认卡");
        var row = rows.getFirst();
        ObjectNode original = read(row.snapshot());
        if (row.orderNo() != null) return receipt(row.orderNo(), original);
        if (!row.expiresAt().isAfter(now()))
            throw new BusinessException(409, "订单确认卡已过期，请重新准备方案");
        // Serialize price/inventory changes with this order's atomic reservation.
        if (jdbc.queryForList("SELECT id FROM showtime WHERE id=? AND deleted=0 FOR UPDATE", row.showtimeId()).isEmpty())
            throw new BusinessException(409, "该场次已不可用，请重新选择");
        ObjectNode criteria = read(row.criteria());
        List<String> seats = json.convertValue(original.path("seats"), json.getTypeFactory().constructCollectionType(List.class, String.class));
        ObjectNode current = read(preview(userId, criteria, seats).toString());
        for (String field : List.of("movieId", "movieTitle", "cinemaName", "hallName", "startTime", "endTime", "originalPrice", "bestPlan"))
            if (!Objects.equals(original.get(field), current.get(field)))
                throw new BusinessException(409, "场次或优惠报价已变化，请重新准备并确认订单");
        var dto = new OrderCreateDTO(); dto.setShowtimeId(criteria.path("showtimeId").asLong()); dto.setSeats(seats);
        dto.setRemark("影票观 AI 确认下单");
        String orderNo = orders.createOrder(dto);
        jdbc.update("UPDATE assistant_checkout SET order_no=? WHERE id=? AND user_id=?", orderNo, id, userId);
        return receipt(orderNo, original);
    }

    private Map<String, Object> receipt(String orderNo, ObjectNode snapshot) {
        var order = orders.getByOrderNo(orderNo); // Re-check ownership and current status on every retry.
        var result = new LinkedHashMap<String, Object>();
        result.put("orderNo", orderNo); result.put("status", order.getStatus());
        result.put("usePoints", snapshot.path("usePoints").asBoolean()); result.put("useCoupons", snapshot.path("useCoupons").asBoolean());
        if (snapshot.path("bestPlan").has("couponId")) result.put("couponId", snapshot.path("bestPlan").get("couponId").asLong());
        result.put("message", Integer.valueOf(0).equals(order.getStatus()) ? "订单已创建，请在支付页完成支付" : "该确认卡已经创建过订单，请查看当前订单状态");
        return result;
    }

    private ObjectNode read(Object value) {
        try { return (ObjectNode) json.readTree(value.toString()); }
        catch (Exception ex) { throw new BusinessException(409, "订单确认卡无法读取，请重新准备"); }
    }
}
