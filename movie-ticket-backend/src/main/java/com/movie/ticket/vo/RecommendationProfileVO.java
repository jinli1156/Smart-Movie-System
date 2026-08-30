package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationProfileVO {
    private boolean needsColdStart;
    private List<String> favoriteGenres;
    private int viewedCount;
    private int favoriteCount;
    private int purchasedCount;
    private int ratedCount;
}
