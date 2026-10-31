package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.PropertyPlaceholderHelper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: three real model calls using synthetic viewing data, no database writes. */
@EnabledIfEnvironmentVariable(named = "RECOMMENDATION_MODEL_LIVE", matches = "1")
class RecommendationModelLiveTest {
    @Test void realModelGeneratesRankingProfileAndQuickSeatPreference() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties config = yaml.getObject();
        Properties local = new Properties();
        Path localFile = Path.of("../.local/assistant.properties");
        if (Files.exists(localFile)) try (var reader = Files.newBufferedReader(localFile)) { local.load(reader); }
        var placeholders = new PropertyPlaceholderHelper("${", "}", ":", false);
        var client = new DeepSeekClient(new ObjectMapper());
        var seatAi = new SeatAiRecommendationService();
        for (Object target : List.of(client, seatAi)) {
            for (var item : Map.of("apiKey", "api-key", "baseUrl", "base-url", "model", "model").entrySet()) {
                String value = placeholders.replacePlaceholders(config.getProperty("assistant." + item.getValue()),
                        key -> System.getenv(key) != null ? System.getenv(key) : local.getProperty(key));
                ReflectionTestUtils.setField(target, item.getKey(), value);
            }
            ReflectionTestUtils.setField(target, "connectTimeoutMs", 5000);
            ReflectionTestUtils.setField(target, "readTimeoutMs", 20000);
        }
        var service = new MovieRecommendationAiService(client);
        var drama = new MovieRecommendationVO(); drama.setId(1L); drama.setTitle("剧情测试片"); drama.setGenre("剧情");
        var action = new MovieRecommendationVO(); action.setId(2L); action.setTitle("动作测试片"); action.setGenre("动作");
        var evidence = Map.of("explicitGenres", List.of("剧情"), "weightedGenres", Map.of("剧情", 8));
        var ranked = service.rank(List.of(drama, action), evidence, 2);
        assertEquals("model", ranked.getFirst().getAnalysisSource(), "Real movie ranking must use model output");
        var profile = service.describe(RecommendationProfileVO.builder().favoriteGenres(List.of("剧情"))
                .viewedCount(3).purchasedCount(1).build(), evidence);
        assertEquals("model", profile.getAnalysisSource(), "Real profile must use model output");
        var seat = SeatRecommendationVO.builder().build(); seat.setSeatLabel("5排 3-4座"); seat.setSeats(List.of("5排3座", "5排4座"));
        var result = seatAi.analyze("靠后安静", 2, 6, 8, List.of(seat));
        assertTrue(result.isPresent(), "Real seat analysis must use model output");
        assertTrue(result.get().ranking().stream().anyMatch(item -> seat.getSeatLabel().equals(item.seatLabel())));
    }
}
