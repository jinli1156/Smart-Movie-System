package com.movie.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 场次编辑参数，限定影片、影院、影厅、起止时间和基础票价。 */
@Data
public class ShowtimeDTO {

    @NotNull(message = "电影ID不能为空")
    private Long movieId;

    @NotNull(message = "影院ID不能为空")
    private Long cinemaId;

    @NotNull(message = "影厅ID不能为空")
    private Long hallId;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "基础票价不能为空")
    private BigDecimal basePrice;

    private Integer status;
}
