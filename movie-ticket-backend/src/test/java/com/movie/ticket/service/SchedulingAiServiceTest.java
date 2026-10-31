package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SchedulingAiServiceTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final SchedulingAiService service = new SchedulingAiService(client);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> basis = Map.of("historicalSamples", 12, "source", "database");

    @Test void validatesEvidenceDeduplicatesAndPreservesServerMetrics() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("test-model");
        when(client.completeJson(anyString(), any())).thenAnswer(call -> {
            String payload = mapper.writeValueAsString(call.getArgument(1));
            assertFalse(payload.contains("private-user"));
            assertTrue(payload.contains("测试影片"));
            return mapper.readTree("""
                {"summary":"根据真实样本，优先关注需求偏低的场次。","actions":[
                 {"evidenceId":"E999","reason":"虚构影片"},
                 {"evidenceId":"E1","reason":"核对后减少低需求场次","predictedRate":999},
                 {"evidenceId":"E1","reason":"重复"},
                 {"evidenceId":"E2","reason":"删除已售场次"}],"risks":["预测不等于实际收入"]}
                """);
        });
        Map<String, Object> item = Map.of("movieId", 1L, "movieTitle", "测试影片", "predictedRate", 30.0, "userId", "private-user");
        Map<String, Object> report = Map.of("lowOccupancyShowtimes", List.of(item),
                "selectedShowtimes", List.of(Map.of("movieTitle", "已售影片", "locked", true)));
        var result = service.analyze("总览", report, basis);
        assertEquals("model", result.get("source"));
        assertEquals("test-model", result.get("model"));
        var actions = (List<?>) result.get("actions");
        assertEquals(1, actions.size());
        assertEquals(30.0, ((Map<?, ?>) actions.getFirst()).get("predictedRate"));
        assertEquals(30.0, item.get("predictedRate"));
    }

    @Test void missingConfigDoesNotCallModel() {
        assertEquals("unconfigured", service.analyze("总览", Map.of(), basis).get("status"));
        verify(client).configured();
        verifyNoMoreInteractions(client);
    }

    @Test void networkFailureAndInvalidResponseAreExplicitFallbacks() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.completeJson(anyString(), any())).thenThrow(new IOException("private upstream detail"))
                .thenReturn(mapper.readTree("{\"summary\":42}"));
        for (int i = 0; i < 2; i++) {
            var result = service.analyze("预测", Map.of("predictedRate", 35), basis);
            assertEquals("algorithm", result.get("source"));
            assertEquals("unavailable", result.get("status"));
            assertFalse(result.toString().contains("private upstream detail"));
        }
    }
}
