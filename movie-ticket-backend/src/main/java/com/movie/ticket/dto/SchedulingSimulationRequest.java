package com.movie.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 排片模拟参数，用于比较调整方案与已有场次的预计经营表现。 */
@Data
public class SchedulingSimulationRequest {

    @NotNull(message = "原场次不能为空")
    private Long originalShowtimeId;

    @NotNull(message = "调整后的影片不能为空")
    private Long targetMovieId;

    private Long targetHallId;

    private LocalDateTime targetStartTime;
}
