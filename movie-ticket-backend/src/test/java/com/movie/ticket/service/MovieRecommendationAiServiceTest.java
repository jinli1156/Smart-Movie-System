package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.vo.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MovieRecommendationAiServiceTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final MovieRecommendationAiService service = new MovieRecommendationAiService(client);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void usesModelOrderButRejectsInventedIdsAndDuplicates() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("test-model");
        when(client.completeJson(anyString(), any())).thenReturn(mapper.readTree("""
            {"ranking":[{"id":999,"reason":"虚构电影"},{"id":2,"reason":"更符合剧情偏好"},
            {"id":2,"reason":"重复"},{"id":1,"reason":""}]}
            """));
        var result = service.rank(List.of(movie(1), movie(2), movie(3)), Map.of(), 3);
        assertEquals(List.of(2L, 1L, 3L), result.stream().map(MovieVO::getId).toList());
        assertEquals("model", result.getFirst().getAnalysisSource());
        assertEquals("更符合剧情偏好", result.getFirst().getRecommendationReason());
        assertEquals("algorithm", result.get(1).getAnalysisSource());
    }

    @Test void timeoutRetainsAlgorithmOrderAndReasons() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.completeJson(anyString(), any())).thenThrow(new IOException("timeout"));
        var result = service.rank(List.of(movie(1), movie(2)), Map.of(), 1);
        assertEquals(1L, result.getFirst().getId());
        assertEquals("原始依据", result.getFirst().getRecommendationReason());
        assertEquals("algorithm", result.getFirst().getAnalysisSource());
    }

    @Test void profileUsesModelSummaryWithoutChangingActualCounts() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.completeJson(anyString(), any())).thenReturn(mapper.readTree("""
            {"summary":"你近期更关注剧情类影片。","purchasedCount":999}
            """));
        var profile = RecommendationProfileVO.builder().favoriteGenres(List.of("剧情"))
                .viewedCount(3).purchasedCount(1).build();
        var result = service.describe(profile, Map.of("weightedGenres", Map.of("剧情", 5)));
        assertEquals("你近期更关注剧情类影片。", result.getSummary());
        assertEquals(1, result.getPurchasedCount());
        assertEquals("model", result.getAnalysisSource());
    }

    @Test void emptyProfileResponseRemainsExplicitFallback() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.completeJson(anyString(), any())).thenReturn(mapper.readTree("{}"));
        var result = service.describe(RecommendationProfileVO.builder().needsColdStart(true)
                .favoriteGenres(List.of()).build(), Map.of());
        assertEquals("algorithm", result.getAnalysisSource());
        assertTrue(result.getSummary().contains("尚无足够"));
    }

    private MovieRecommendationVO movie(long id) {
        var movie = new MovieRecommendationVO();
        movie.setId(id); movie.setTitle("电影" + id); movie.setRecommendationReason("原始依据");
        return movie;
    }
}
