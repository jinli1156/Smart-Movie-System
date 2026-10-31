package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 影厅编辑参数，限定所属影院、行列数量、容量及座位布局。 */
@Data
public class HallDTO {

    @NotNull(message = "影院ID不能为空")
    private Long cinemaId;

    @NotBlank(message = "影厅名称不能为空")
    private String name;

    @NotNull(message = "总行数不能为空")
    private Integer totalRows;

    @NotNull(message = "总列数不能为空")
    private Integer totalCols;

    @NotBlank(message = "座位布局不能为空")
    private String seatLayout;

    private Integer status;
}
