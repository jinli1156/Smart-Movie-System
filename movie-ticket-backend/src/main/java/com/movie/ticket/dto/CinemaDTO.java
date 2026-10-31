package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/** 影院新增或编辑参数，声明名称、地址、坐标及联系方式等输入约束。 */
@Data
public class CinemaDTO {

    @NotBlank(message = "影院名称不能为空")
    private String name;

    @NotBlank(message = "地址不能为空")
    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private String phone;

    private String description;

    private String images;

    private Integer status;
}
