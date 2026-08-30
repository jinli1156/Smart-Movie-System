package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecommendationScoreVO {
    private int content;
    private int userPreference;
    private int behavior;
    private int scene;
    private int trend;
}
