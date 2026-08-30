package com.movie.ticket.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MovieRecommendationVO extends MovieVO {
    private int matchScore;
    private String recommendationReason;
    private RecommendationScoreVO recommendationScores;
}
