package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Deterministic next actions grounded in successful results; never invent a selected film or party size. */
public final class AssistantSuggestionService {
    private AssistantSuggestionService() {}
    public static List<String> suggest(ObjectMapper json, List<Map<String, Object>> results, JsonNode plan, boolean loggedIn) {
        for (int i = results.size() - 1; i >= 0; i--) {
            var result = results.get(i);
            if (!Boolean.TRUE.equals(result.get("ok"))) {
                if ("LOGIN_REQUIRED".equals(result.get("code"))) return List.of("先帮我看看可购票场次", "购票流程是什么？");
                continue;
            }
            String source = Objects.toString(result.get("source"), "");
            JsonNode data = json.valueToTree(result.get("data"));
            if (source.equals("prepare_ticket_order")) return List.of("确认下单", "不用积分，重新准备订单", "换一组座位，重新准备订单");
            if (source.equals("plan_movie_outing")) {
                if (data.path("needs").toString().contains("people")) return List.of("只有我一个人", "我们两个人", "我们三个人");
                if (data.path("plans").isEmpty()) return List.of("换成明天，其他条件不变", "有哪些条件可以调整？");
                return List.of("选第一个方案，帮我确认详情", "不用积分，其他条件不变，重新比较", "这些电影都看过了，换一批");
            }
            if (source.equals("compare_prices") || source.equals("search_showtimes")) {
                if (data.path(source.equals("compare_prices") ? "offers" : "items").isEmpty()) return List.of("换成明天，其他条件不变", "有哪些条件可以调整？");
                return plan.has("people") ? List.of("选第一个场次，推荐连座并计算实付", "换成明天，其他条件不变", "这些电影都看过了，换一批")
                        : List.of("我们两个人，比较实付并推荐连座", "只有我一个人，帮我安排", "换成明天，其他条件不变");
            }
            if (source.equals("recommend_movies") || source.equals("search_movies") || source.equals("discover_movies")) {
                if (data.path(source.equals("search_movies") ? "items" : "movies").isEmpty()) return List.of("有哪些条件可以调整？", "查看本次观影条件");
                return List.of("第一部讲什么？不要剧透", "选第一部，查今天的场次", "这些电影都看过了，换一批");
            }
            if (source.equals("recommend_seats")) return List.of("不用积分，重新计算实付", "换一个更方便进出的座位方案", "购票流程是什么？");
            if (source.equals("quote_price")) return List.of("不用积分，再算一下", "优惠券为什么有使用门槛？", "支付有时间限制吗？");
            if (source.equals("get_my_orders")) return List.of("帮我查看已支付订单", "本项目的退票规则是什么？", "如何取票？");
            if (source.equals("search_cinemas")) return List.of("今天晚上有哪些场次？", "我们两个人，帮我安排一场电影");
            if (source.equals("search_knowledge")) return loggedIn ? List.of("查看我的待支付订单", "今天有哪些可购票场次？") : List.of("今天有哪些可购票场次？", "如何注册并购票？");
        }
        if (!plan.has("people")) return List.of("只有我一个人", "我们两个人", "带家人一起看");
        return List.of("按这些条件安排观影", "想看轻松一点的电影", "查看本次观影条件");
    }
}
