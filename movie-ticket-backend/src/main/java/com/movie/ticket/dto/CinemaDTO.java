package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

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
