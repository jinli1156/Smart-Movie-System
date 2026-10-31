package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

/** Small, auditable lexical RAG corpus. Rules are maintained alongside business code. */
@Service
public class AssistantKnowledgeService {
    private final List<JsonNode> documents = new ArrayList<>();

    public AssistantKnowledgeService(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("assistant/knowledge.json").getInputStream()) {
            mapper.readTree(input).forEach(documents::add);
        }
    }

    /** 检索与问题相关的本地业务规则条目。 */
    public List<JsonNode> search(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return documents.stream().filter(d -> score(d, normalized) > 0)
                .sorted(Comparator.comparingInt((JsonNode d) -> score(d, normalized)).reversed())
                .limit(3).toList();
    }

    private int score(JsonNode document, String query) {
        int score = 0;
        for (String keyword : document.path("keywords").asText().split(" ")) {
            if (query.contains(keyword.toLowerCase(Locale.ROOT))) score += keyword.length();
        }
        return score;
    }
}
