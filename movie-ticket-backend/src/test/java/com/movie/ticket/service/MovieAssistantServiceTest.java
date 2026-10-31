package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.dto.AssistantChatRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovieAssistantServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final AssistantToolService tools = mock(AssistantToolService.class);

    @Test void explicitNoPointsChangeForcesFreshQueryAndEmptyInventoryOverridesInventedPrice() throws Exception {
        when(client.configured()).thenReturn(true); when(tools.definitions()).thenReturn(mapper.createArrayNode());
        var request = new AssistantChatRequest(); request.setMessage("不用积分，再算一下，其他不变");
        request.setWatchPlan(mapper.readTree("{\"criteria\":{\"people\":2,\"maxTotal\":80},\"options\":[]}"));
        var rounds = new java.util.concurrent.atomic.AtomicInteger();
        when(tools.execute(eq("plan_movie_outing"), anyString(), eq(7L))).thenAnswer(call -> {
            assertFalse(mapper.readTree((String) call.getArgument(1)).get("usePoints").asBoolean());
            return Map.of("ok", true, "source", "plan_movie_outing", "data", Map.of("plans", List.of()));
        });
        when(client.complete(anyList(), any(), any(), any())).thenAnswer(call -> {
            rounds.incrementAndGet(); Consumer<String> delta = call.getArgument(2);
            List<JsonNode> messages = call.getArgument(0); assertEquals("tool", messages.getLast().get("role").asText());
            delta.accept("不用积分实付70元"); return mapper.readTree("{\"role\":\"assistant\",\"content\":\"不用积分实付70元\"}");
        });
        var response = new MovieAssistantService(client, tools, mapper).converse(request, 7L, (event, value) -> {}, () -> false);
        assertEquals("model", response.getSource()); assertFalse(response.getAnswer().contains("70"));
        assertFalse(response.getWatchPlan().at("/criteria/usePoints").asBoolean());
        assertEquals(1, rounds.get());
    }

    @Test void executesModelToolCallAndReturnsResultToNextRound() throws Exception {
        when(client.configured()).thenReturn(true);
        when(tools.definitions()).thenReturn(mapper.createArrayNode());
        when(tools.execute("get_my_orders", "{}", 7L)).thenReturn(Map.of("ok", true, "data", Map.of("total", 0)));
        var requests = new ArrayList<List<JsonNode>>();
        when(client.complete(anyList(), any(), any(), any())).thenAnswer(invocation -> {
            requests.add(new ArrayList<>(invocation.getArgument(0)));
            if (requests.size() == 1) return mapper.readTree("""
                {"role":"assistant","content":"","tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_my_orders","arguments":"{}"}}]}
                """);
            Consumer<String> consumer = invocation.getArgument(2); consumer.accept("本次未查询到订单。");
            return mapper.readTree("{\"role\":\"assistant\",\"content\":\"本次未查询到订单。\"}");
        });
        var request = new AssistantChatRequest(); request.setMessage("我的订单");
        var service = new MovieAssistantService(client, tools, mapper);
        var result = service.converse(request, 7L, (e, d) -> {}, () -> false);
        assertEquals("model", result.getSource());
        assertEquals(1, result.getToolResults().size());
        verify(tools).execute("get_my_orders", "{}", 7L);
        JsonNode toolReply = requests.get(1).get(requests.get(1).size() - 1);
        assertEquals("tool", toolReply.path("role").asText());
        assertEquals("call_1", toolReply.path("tool_call_id").asText());
        assertTrue(toolReply.path("content").asText().contains("total"));
        assertTrue(requests.get(0).get(0).path("content").asText().contains("智能电影票务系统助手"));
    }

    @Test void missingKeyAndProviderFailureDoNotPretendModelSucceeded() throws Exception {
        var service = new MovieAssistantService(client, tools, mapper);
        var request = new AssistantChatRequest(); request.setMessage("电影票多少钱");
        assertEquals("unavailable", service.chat(request).getSource());
        verifyNoInteractions(tools);
        when(client.configured()).thenReturn(true);
        when(tools.definitions()).thenReturn(mapper.createArrayNode());
        when(client.complete(anyList(), any(), any(), any())).thenThrow(new RuntimeException("secret"));
        var failed = service.chat(request);
        assertEquals("unavailable", failed.getSource());
        assertFalse(failed.getAnswer().contains("secret"));
    }

    @Test void repeatedToolRequestsHaveBoundedExecution() throws Exception {
        when(client.configured()).thenReturn(true);
        when(tools.definitions()).thenReturn(mapper.createArrayNode());
        when(tools.execute(anyString(), anyString(), isNull())).thenReturn(Map.of("ok", false));
        when(client.complete(anyList(), any(), any(), any())).thenReturn(mapper.readTree("""
            {"role":"assistant","tool_calls":[{"id":"x","function":{"name":"search_movies","arguments":"{}"}}]}
            """));
        var request = new AssistantChatRequest(); request.setMessage("一直查电影");
        var result = new MovieAssistantService(client, tools, mapper).chat(request);
        assertEquals("unavailable", result.getSource());
        assertTrue(result.getAnswer().contains("次数上限"));
        assertFalse(result.getAnswer().contains("服务暂时不可用"));
        verify(client, times(6)).complete(anyList(), any(), any(), any());
        verify(tools, times(5)).execute(anyString(), anyString(), isNull());
    }

    @Test void lastRoundConcludesWithoutToolsAndDiscardsExplorationNarration() throws Exception {
        when(client.configured()).thenReturn(true);
        when(tools.definitions()).thenReturn(mapper.readTree("[{\"type\":\"function\"}]"));
        when(tools.execute(anyString(), anyString(), isNull())).thenAnswer(call -> Map.of("ok", true, "source", call.getArgument(0), "data", Map.of("total", 0, "items", List.of())));
        var finalOnly = new ArrayList<Boolean>();
        when(client.complete(anyList(), any(), any(), any())).thenAnswer(invocation -> {
            JsonNode definitions = invocation.getArgument(1);
            finalOnly.add(definitions.isEmpty());
            Consumer<String> delta = invocation.getArgument(2);
            if (definitions.isEmpty()) {
                delta.accept("当前查询范围内没有符合预算的场次。");
                return mapper.readTree("{\"role\":\"assistant\",\"content\":\"当前查询范围内没有符合预算的场次。\"}");
            }
            delta.accept("让我再查更多电影");
            return mapper.readTree("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"t\",\"function\":{\"name\":\"search_showtimes\",\"arguments\":\"{}\"}}]}");
        });
        var request = new AssistantChatRequest(); request.setMessage("推荐一部40元以下电影");
        var events = new ArrayList<String>();
        var result = new MovieAssistantService(client, tools, mapper).converse(request, null, (e, d) -> events.add(e), () -> false);
        assertEquals("model", result.getSource());
        assertFalse(result.getAnswer().contains("让我"));
        assertEquals(List.of(false, false, false, false, false, true), finalOnly);
        assertEquals(5L, events.stream().filter("answer_reset"::equals).count());
    }
}
