package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponVO {
    private Long id;
    private String name;
    private BigDecimal amount;
    private BigDecimal minSpend;
    private Integer status;
    private String statusText;
    private boolean available;
    private LocalDateTime validFrom;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;
}
