package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.dto.AssistantChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in paid model verification; no server is started and no database is mutated. */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_LIVE", matches = "1")
class AssistantBudgetModelLiveTest {
    @Test void modelQueriesOneAffordableShowtimeDirectly() throws Exception { verifyBudget(true); }
    @Test void modelStopsWhenBudgetHasNoMatches() throws Exception { verifyBudget(false); }

    private void verifyBudget(boolean hasMatch) throws Exception {
        var mapper = new ObjectMapper();
        var client = new DeepSeekClient(mapper);
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            var properties = new Properties();
            try (var reader = Files.newBufferedReader(Path.of("../.local/assistant.properties"))) { properties.load(reader); }
            key = properties.getProperty("DEEPSEEK_API_KEY");
        }
        assertNotNull(key, "Configure the private DeepSeek API key before this opt-in test");
        ReflectionTestUtils.setField(client, "apiKey", key);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(client, "model", "deepseek-v4-flash");
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 5000);
        ReflectionTestUtils.setField(client, "readTimeoutMs", 60000);
        var tools = mock(AssistantToolService.class);
        try (var input = new ClassPathResource("assistant/tools.json").getInputStream()) { when(tools.definitions()).thenReturn(mapper.readTree(input)); }
        var data = Map.of("items", hasMatch ? List.of(Map.of("id", 99, "movieTitle", "预算内测试电影", "cinemaName", "测试影城", "startTime", "2026-09-05 18:00:00", "basePrice", 38, "availableSeats", 10)) : List.of(),
                "total", hasMatch ? 1 : 0, "date", "2026-09-05", "size", 1, "maxPrice", 40, "priceExclusive", true);
        when(tools.execute(eq("search_showtimes"), anyString(), isNull())).thenReturn(Map.of("ok", true, "source", "search_showtimes", "data", data));
        var request = new AssistantChatRequest(); request.setMessage("推荐一部票价40元以下的电影");
        var response = new MovieAssistantService(client, tools, mapper).converse(request, null, (e, d) -> {}, () -> false);
        assertEquals("model", response.getSource(), response.getAnswer());
        var arguments = ArgumentCaptor.forClass(String.class);
        verify(tools, times(1)).execute(eq("search_showtimes"), arguments.capture(), isNull());
        var args = mapper.readTree(arguments.getValue());
        assertEquals(40, args.path("maxPrice").asInt());
        assertEquals(1, args.path("limit").asInt());
        assertTrue(args.path("priceExclusive").asBoolean());
        assertFalse(args.has("movieId"));
        assertEquals(1, response.getToolResults().size(), "Budget queries must not enumerate movie lists");
        if (hasMatch) assertTrue(response.getAnswer().contains("预算内测试电影"));
    }
}
