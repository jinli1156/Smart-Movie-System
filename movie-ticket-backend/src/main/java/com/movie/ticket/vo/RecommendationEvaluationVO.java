package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 推荐评估汇总，记录评估口径、样本与各算法指标。 */
@Data
@Builder
public class RecommendationEvaluationVO {
    private int k;
    private int evaluatedUsers;
    private double precisionAtK;
    private double recallAtK;
    private double ndcgAtK;
    private double catalogCoverage;
    private double diversity;
    private int skippedUsers;
    private List<RecommendationExperimentVO> baselines;
    private List<RecommendationExperimentVO> ablations;
    private String protocol;
    private String candidatePolicy;
    private LocalDateTime generatedAt;
}
