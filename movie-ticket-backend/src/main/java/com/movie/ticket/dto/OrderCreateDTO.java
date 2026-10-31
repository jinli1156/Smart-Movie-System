package com.movie.ticket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 下单参数，只提交场次、座位和备注；票价、用户身份及订单状态由服务端确定。 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "场次ID不能为空")
    private Long showtimeId;

    @NotEmpty(message = "座位不能为空")
    private List<String> seats;

    private String remark;
}
