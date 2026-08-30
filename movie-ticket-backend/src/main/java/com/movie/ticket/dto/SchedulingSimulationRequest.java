package com.movie.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulingSimulationRequest {

    @NotNull(message = "原场次不能为空")
    private Long originalShowtimeId;

    @NotNull(message = "调整后的影片不能为空")
    private Long targetMovieId;

    private Long targetHallId;

    private LocalDateTime targetStartTime;
}
