package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long userId;

    private String userName;

    private Long showtimeId;

    private Long movieId;

    private String movieTitle;

    private String moviePoster;

    private String cinemaName;

    private String hallName;

    private LocalDateTime showtime;

    private String seats;

    private Integer seatCount;

    private BigDecimal totalPrice;

    private BigDecimal originalPrice;

    private BigDecimal discountAmount;

    private Long couponId;

    private Integer status;

    private String statusText;

    private String ticketCode;

    private Integer isPicked;

    private LocalDateTime pickTime;

    private LocalDateTime payTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
