package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SeatRecommendationVO {
    private String title;
    private String icon;
    private int match;
    private String seatLabel;
    private List<String> seats;
    private int count;
    private BigDecimal unitPrice;
    private String reason;
    private String modelReason;
    private String reasonSource;
    private List<String> tags;
    private SeatScoreVO scores;
    private int singleSeatPenalty;
}
