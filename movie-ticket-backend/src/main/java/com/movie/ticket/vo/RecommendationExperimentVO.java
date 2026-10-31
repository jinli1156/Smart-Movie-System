package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

/** 推荐实验对比结果，承载模型、基线或消融实验的评估数据。 */
@Data
@Builder
public class RecommendationExperimentVO {
    private String key;
    private String name;
    private String category;
    private int evaluatedUsers;
    private double precisionAtK;
    private double recallAtK;
    private double ndcgAtK;
    private double catalogCoverage;
    private double diversity;
    private double recallChangeVsFull;
    private String description;
}
