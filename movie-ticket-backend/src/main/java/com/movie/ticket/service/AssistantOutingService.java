package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.vo.ShowtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** 根据日期、人数、预算和时间窗口筛选观影方案，组合可用座位及优惠试算结果。 */
@Service
@RequiredArgsConstructor
public class AssistantOutingService {
    private final ShowtimeMapper showtimes;
    private final AssistantBookingService booking;
    private final ObjectMapper json;

    /** 按观影条件生成场次、连座及优惠组合，提供可执行的观影候选方案。 */
    public Map<String, Object> plan(JsonNode criteria, Long userId) {
        AssistantWatchPlan.validate(criteria);
        if (!criteria.has("people")) return Map.of("plans", List.of(), "needs", List.of("people"), "message", "请先说明几个人观影，才能准确计算总价和检查连座。");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDate date = criteria.has("date") ? LocalDate.parse(criteria.get("date").asText()) : now.toLocalDate();
        if (date.isBefore(now.toLocalDate()) || date.isAfter(now.toLocalDate().plusDays(90))) throw new IllegalArgumentException("请选择今天起90天内的日期");
        int people = criteria.get("people").asInt();
        LocalDateTime start = criteria.has("startAfter") ? date.atTime(LocalTime.parse(criteria.get("startAfter").asText())) : date.atStartOfDay();
        LocalDateTime end = criteria.has("startBefore") ? date.atTime(LocalTime.parse(criteria.get("startBefore").asText())) : date.plusDays(1).atStartOfDay();
        Map<String, Object> filters = new LinkedHashMap<>();
        for (String key : List.of("genre", "minRating", "maxDuration", "hallKeyword", "cinemaKeyword", "people", "excludedGenres", "excludedMovieIds", "showtimeId"))
            if (criteria.has(key)) filters.put(key, json.convertValue(criteria.get(key), Object.class));
        if (criteria.has("endBefore")) filters.put("endBefore", date.atTime(LocalTime.parse(criteria.get("endBefore").asText())));
        var candidates = showtimes.selectAssistantCandidates(id(criteria, "movieId"), id(criteria, "cinemaId"), start, end, now.plusMinutes(15),
                amount(criteria, "maxPrice"), criteria.path("priceExclusive").asBoolean(false), filters);
        boolean usePoints = criteria.path("usePoints").asBoolean(true), useCoupons = criteria.path("useCoupons").asBoolean(true);
        var prices = booking.previewPrices(userId, candidates, people, usePoints, useCoupons);
        var affordable = new ArrayList<ShowtimeVO>();
        for (var candidate : candidates) if (!criteria.has("maxTotal") || total(prices.get(candidate.getId())).compareTo(amount(criteria, "maxTotal")) <= 0) affordable.add(candidate);
        String scene = criteria.path("scene").asText("");
        affordable.sort(Comparator.<ShowtimeVO>comparingInt(s -> -AssistantSceneMatcher.score(scene, s.getMovieGenre()))
                .thenComparing(s -> total(prices.get(s.getId())))
                .thenComparing(s -> json.valueToTree(prices.get(s.getId())).path("bestPlan").path("pointsUsed").decimalValue())
                .thenComparing(ShowtimeVO::getStartTime).thenComparing(ShowtimeVO::getId));
        boolean together = criteria.path("requireTogether").asBoolean(people > 1);
        List<Map<String, Object>> plans = new ArrayList<>();
        int checked = 0, noTogether = 0, unavailable = 0;
        int limit = criteria.path("limit").asInt(3);
        // Check in final ranking order; only stop once enough actually feasible plans exist.
        for (var candidate : affordable) {
            if (plans.size() >= limit) break;
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("查询已中断");
            checked++;
            List<com.movie.ticket.vo.SeatRecommendationVO> seats;
            try {
                seats = booking.planSeats(candidate.getId(), people, criteria.path("preference").asText("CENTER"), criteria.path("naturalLanguage").asText(null));
            } catch (com.movie.ticket.exception.BusinessException ex) { unavailable++; continue; }
            if (seats.isEmpty() && together) { noTogether++; continue; }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("showtimeId", candidate.getId()); item.put("movieId", candidate.getMovieId());
            item.put("movieTitle", candidate.getMovieTitle()); item.put("moviePoster", candidate.getMoviePoster());
            item.put("movieGenre", candidate.getMovieGenre()); item.put("movieRating", candidate.getMovieRating());
            item.put("cinemaName", candidate.getCinemaName()); item.put("hallName", candidate.getHallName());
            item.put("startTime", candidate.getStartTime()); item.put("endTime", candidate.getEndTime()); item.put("people", people);
            item.putAll(prices.get(candidate.getId()));
            item.put("seats", seats.isEmpty() ? List.of() : seats.getFirst().getSeats());
            item.put("seatReason", seats.isEmpty() ? "未找到连座，可进入选座页自行选择分散座位" : seats.getFirst().getReason());
            item.put("reason", scene.isEmpty() ? "满足本次筛选条件，按优惠后总实付、积分消耗和开场时间排序" : AssistantSceneMatcher.evidence(scene, candidate.getMovieGenre()));
            plans.add(item);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("plans", plans); result.put("criteria", criteria); result.put("date", date.toString());
        result.put("people", people); result.put("personalized", userId != null); result.put("usePoints", usePoints); result.put("useCoupons", useCoupons);
        result.put("candidateCount", candidates.size()); result.put("budgetMatchedCount", affordable.size()); result.put("seatCheckedCount", checked);
        result.put("noTogetherCount", noTogether); result.put("unavailableCount", unavailable); result.put("queriedAt", now.toString());
        result.put("diagnosis", candidates.isEmpty() ? "当前日期及影片、影院、时段、时长、票面预算、人数和排除条件的交集没有可售场次，尚不能归因于某一个条件。"
                : affordable.isEmpty() ? "存在满足场次条件的候选，但优惠后的总实付均超过总预算。"
                : plans.isEmpty() ? "符合预算的场次未找到满足连座要求的方案，或在复查时已不可售。" : "已找到符合条件的观影方案。");
        result.put("notice", "本系统查询快照，优惠与座位尚未保留；点击方案进入选座页重新校验。"
                + (userId == null ? "访客只按原价比较，登录后可计算个人优惠。" : "实付使用当前账号优惠券与积分计算，支付时再次核验。")
                + (scene.isEmpty() ? "" : AssistantSceneMatcher.NOTICE));
        return result;
    }
    private Long id(JsonNode node, String field) { return node.has(field) ? node.get(field).asLong() : null; }
    private BigDecimal amount(JsonNode node, String field) { return node.has(field) ? node.get(field).decimalValue() : null; }
    private BigDecimal total(Map<String, Object> price) { return json.valueToTree(price).path("bestPlan").path("totalPrice").decimalValue(); }
}
