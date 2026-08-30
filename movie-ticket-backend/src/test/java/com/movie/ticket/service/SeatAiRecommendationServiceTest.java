package com.movie.ticket.service;

import com.movie.ticket.dto.SeatPreference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatAiRecommendationServiceTest {

    @Test
    void parsesNaturalLanguagePreferencesWithSafePriority() {
        assertEquals(SeatPreference.QUIET,
                SeatRecommendationService.inferPreference("想坐靠后一点，安静些", "CENTER"));
        assertEquals(SeatPreference.EASY_ACCESS,
                SeatRecommendationService.inferPreference("靠过道，方便进出", "CENTER"));
        assertEquals(SeatPreference.ACCESSIBLE,
                SeatRecommendationService.inferPreference("带坐轮椅的家人看电影", "COUPLE"));
    }

    @Test
    void parsesStrictModelJsonAndRemovesCodeFence() {
        SeatAiRecommendationService service = new SeatAiRecommendationService();
        String content = "```json\n{\"preference\":\"QUIET\",\"summary\":\"偏好安静后排\","
                + "\"ranking\":[{\"seatLabel\":\"8排 6-7座\",\"reason\":\"后排且与已售座位保持距离\"}]}\n```";

        SeatAiRecommendationService.SeatAiAnalysis result = service.parseAnalysis(content);

        assertEquals(SeatPreference.QUIET, result.preference());
        assertEquals("偏好安静后排", result.summary());
        assertEquals("8排 6-7座", result.ranking().getFirst().seatLabel());
    }
}
