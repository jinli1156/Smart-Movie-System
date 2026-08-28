package com.movie.ticket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateDTO {

    @NotNull(message = "场次ID不能为空")
    private Long showtimeId;

    @NotEmpty(message = "座位不能为空")
    private List<String> seats;

    private String remark;
}
