package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendationEvaluationVO {
    private int k;
    private int evaluatedUsers;
    private double precisionAtK;
    private double recallAtK;
    private double catalogCoverage;
    private double diversity;
    private String protocol;
}
