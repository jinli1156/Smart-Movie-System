package com.movie.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulingPredictionRequest {

    @NotNull(message = "影片不能为空")
    private Long movieId;

    @NotNull(message = "影院不能为空")
    private Long cinemaId;

    @NotNull(message = "影厅不能为空")
    private Long hallId;

    @NotNull(message = "开场时间不能为空")
    private LocalDateTime startTime;
}
