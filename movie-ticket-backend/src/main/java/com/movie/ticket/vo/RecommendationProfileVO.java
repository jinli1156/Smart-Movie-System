package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 推荐偏好画像，向用户解释类型倾向、行为特征与推荐依据。 */
@Data
@Builder
public class RecommendationProfileVO {
    private boolean needsColdStart;
    private List<String> favoriteGenres;
    private int viewedCount;
    private int favoriteCount;
    private int purchasedCount;
    private int ratedCount;
    private String summary;
    private String analysisSource;
    private String model;
}
