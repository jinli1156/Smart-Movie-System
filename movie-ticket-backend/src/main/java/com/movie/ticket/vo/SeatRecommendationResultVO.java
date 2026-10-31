package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 智能选座响应，组合座位方案、自然语言分析及结果来源。 */
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
