package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 推荐评分明细，展示各特征对影片匹配结果的贡献。 */
@Data
@AllArgsConstructor
public class RecommendationScoreVO {
    private int content;
    private int userPreference;
    private int behavior;
    private int scene;
    private int trend;
}
