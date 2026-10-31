package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户优惠券视图，附带可展示的状态与使用信息。 */
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
