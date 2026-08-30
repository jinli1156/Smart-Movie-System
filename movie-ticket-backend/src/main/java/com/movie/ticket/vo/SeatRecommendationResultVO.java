package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SeatRecommendationResultVO {
    private List<SeatRecommendationVO> recommendations;
    private String interpretedPreference;
    private String preferenceSummary;
    private String analysisSource;
    private String model;
    private String availabilityToken;
    private LocalDateTime generatedAt;
}
