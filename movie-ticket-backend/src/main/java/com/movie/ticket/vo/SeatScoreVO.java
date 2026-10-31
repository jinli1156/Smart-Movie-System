package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 座位方案评分明细，供页面解释视野、连座与通行等维度。 */
@Data
@AllArgsConstructor
public class SeatScoreVO {
    private int visionCenter;
    private int goldenDistance;
    private int continuity;
    private int accessConvenience;
    private int preference;
    private int realtimeAvailability;
}
