package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {
    private final DeepSeekClient client = new DeepSeekClient(new ObjectMapper());

    @Test void textIsForwardedBeforeStreamCompletes() throws Exception {
        var deltas = new ArrayList<String>();
        var message = client.readStream(reader("""
                : keepalive
                data: {"choices":[{"delta":{"content":"你好"}}]}

                data: {"choices":[{"delta":{"content":"，观众"},"finish_reason":"stop"}]}

                data: [DONE]
                """), deltas::add, () -> false);
        assertEquals(java.util.List.of("你好", "，观众"), deltas);
        assertEquals("你好，观众", message.path("content").asText());
    }

    @Test void assemblesFragmentedParallelToolCalls() throws Exception {
        var message = client.readStream(reader("""
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"search_movies","arguments":"{"}},{"index":1,"id":"call_2","function":{"name":"search_cinemas","arguments":"{}"}}]}}]}
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}}]},"finish_reason":"tool_calls"}]}
                data: [DONE]
                """), text -> fail("Tool fragments must not appear as answer text"), () -> false);
        assertEquals(2, message.path("tool_calls").size());
        assertEquals("{}", message.path("tool_calls").path(0).path("function").path("arguments").asText());
        assertEquals("call_2", message.path("tool_calls").path(1).path("id").asText());
    }

    @Test void truncatedErroredOrCancelledStreamsAreNotSuccessfulAnswers() {
        assertThrows(IOException.class, () -> client.readStream(reader("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n"), t -> {}, () -> false));
        assertThrows(IOException.class, () -> client.readStream(reader("data: {\"error\":{\"message\":\"secret\"}}\n"), t -> {}, () -> false));
        assertThrows(IOException.class, () -> client.readStream(reader("data: {}\n"), t -> {}, () -> true));
    }

    private BufferedReader reader(String text) { return new BufferedReader(new StringReader(text)); }
}
