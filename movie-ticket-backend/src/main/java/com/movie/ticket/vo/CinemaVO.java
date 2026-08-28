package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CinemaVO {

    private Long id;

    private String name;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private String phone;

    private String description;

    private String images;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
