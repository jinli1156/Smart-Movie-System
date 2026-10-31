package com.movie.ticket.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 个性化推荐影片，附带匹配分数及可解释的推荐理由。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieRecommendationVO extends MovieVO {
    private int matchScore;
    private String recommendationReason;
    private RecommendationScoreVO recommendationScores;
    private String analysisSource;
    private String model;
}
