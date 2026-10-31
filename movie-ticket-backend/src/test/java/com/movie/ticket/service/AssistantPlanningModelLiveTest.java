package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.dto.AssistantChatRequest;
import com.movie.ticket.dto.AssistantMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real model, synthetic inventory: validates multi-turn intent handling without changing business data. */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_PLANNING_LIVE", matches = "1")
class AssistantPlanningModelLiveTest {
    @Test void fiveTurnOutingKeepsBudgetExclusionsAndResolvesSecondOption() throws Exception {
        var json = new ObjectMapper(); var client = new DeepSeekClient(json);
        var properties = new Properties();
        if (Files.exists(Path.of("../.local/assistant.properties"))) try (var reader = Files.newBufferedReader(Path.of("../.local/assistant.properties"))) { properties.load(reader); }
        String key = System.getenv().getOrDefault("DEEPSEEK_API_KEY", properties.getProperty("DEEPSEEK_API_KEY", ""));
        assertFalse(key.isBlank(), "Configure a model key before running this opt-in test");
        ReflectionTestUtils.setField(client, "apiKey", key);
        ReflectionTestUtils.setField(client, "baseUrl", System.getenv().getOrDefault("AI_ASSISTANT_BASE_URL", "https://api.deepseek.com"));
        ReflectionTestUtils.setField(client, "model", System.getenv().getOrDefault("AI_ASSISTANT_MODEL", "deepseek-v4-flash"));
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 5000); ReflectionTestUtils.setField(client, "readTimeoutMs", 60000);
        var tools = mock(AssistantToolService.class);
        try (var input = new ClassPathResource("assistant/tools.json").getInputStream()) { when(tools.definitions()).thenReturn(json.readTree(input)); }
        when(tools.execute(anyString(), anyString(), eq(7L))).thenAnswer(invocation -> {
            String name = invocation.getArgument(0); JsonNode criteria = json.readTree((String) invocation.getArgument(1));
            if (!name.equals("plan_movie_outing")) return Map.of("ok", false, "code", "USE_WATCH_PLAN", "message", "这是组合观影需求，请用本次观影条件调用plan_movie_outing");
            var plans = new ArrayList<Map<String, Object>>();
            for (int i = 1; i <= 2; i++) {
                if (criteria.has("movieId") && criteria.get("movieId").asInt() != i) continue;
                var item = new LinkedHashMap<String, Object>(); item.put("movieId", i); item.put("showtimeId", i * 101);
                item.put("movieTitle", "合成测试影片" + i); item.put("cinemaName", "合成测试影院");
                item.put("startTime", criteria.path("date").asText() + "T20:00:00"); item.put("endTime", criteria.path("date").asText() + "T21:40:00");
                item.put("originalPrice", 80); item.put("bestPlan", Map.of("totalPrice", criteria.path("usePoints").asBoolean(true) ? 69 : 70, "couponDiscount", 10, "pointsUsed", criteria.path("usePoints").asBoolean(true) ? 100 : 0));
                item.put("seats", List.of("5排5座", "5排6座")); plans.add(item);
            }
            return Map.of("ok", true, "source", name, "data", Map.of("plans", plans, "people", 2, "date", criteria.path("date").asText(), "notice", "合成测试查询结果，尚未锁座"));
        });
        var service = new MovieAssistantService(client, tools, json);
        JsonNode state = null; List<AssistantMessage> history = new ArrayList<>();
        Files.createDirectories(Path.of("../.local/assistant-upgrade"));
        Files.writeString(Path.of("../.local/assistant-upgrade/model-trace.jsonl"), "");
        List<String> questions = List.of("今晚两个人看电影，总预算80元，不要恐怖片，帮我安排观影", "七点半以后，十点之前结束，其他不变", "选第二个，中间靠后，要连座", "不用积分，再算一下，其他不变", "换成明天，其他条件不变");
        for (int i = 0; i < questions.size(); i++) {
            var request = new AssistantChatRequest(); request.setMessage(questions.get(i)); request.setHistory(history); request.setWatchPlan(state);
            var response = service.converse(request, 7L, (event, value) -> {}, () -> false);
            Files.writeString(Path.of("../.local/assistant-upgrade/model-trace.jsonl"), json.writeValueAsString(Map.of("turn", i + 1, "response", response)) + "\n", java.nio.file.StandardOpenOption.APPEND);
            assertEquals("model", response.getSource(), "Turn " + (i + 1) + ": " + response.getAnswer());
            state = response.getWatchPlan(); var criteria = state.path("criteria");
            assertEquals(2, criteria.path("people").asInt(), "Turn " + (i + 1)); assertEquals(80, criteria.path("maxTotal").asInt());
            assertTrue(criteria.path("excludedGenres").toString().contains("恐怖"));
            if (i >= 1) { assertEquals("19:30", criteria.path("startAfter").asText()); assertEquals("22:00", criteria.path("endBefore").asText()); }
            if (i >= 2) assertEquals(2, criteria.path("movieId").asInt());
            if (i >= 3) assertFalse(criteria.path("usePoints").asBoolean(true));
            if (i == 4) { assertFalse(criteria.has("showtimeId")); assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString(), criteria.path("date").asText()); }
            var user = new AssistantMessage(); user.setRole("user"); user.setContent(questions.get(i)); history.add(user);
            var assistant = new AssistantMessage(); assistant.setRole("assistant"); assistant.setContent(response.getAnswer()); history.add(assistant);
        }
    }
}
