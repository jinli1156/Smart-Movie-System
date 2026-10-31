package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 偏好雷达图数据，封装各维度得分及解释依据。 */
@Data
@Builder
public class RecommendationRadarVO {
    private Map<String, Integer> dimensions;
    private List<String> topGenres;
    private int evidenceCount;
    private String summary;
}
