package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

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
