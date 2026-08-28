package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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
