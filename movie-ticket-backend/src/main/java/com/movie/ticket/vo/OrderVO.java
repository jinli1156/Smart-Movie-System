package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单展示数据，组合影片、影院、场次、座位、优惠和取票信息。 */
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

    private BigDecimal pointsUsed;

    private BigDecimal pointsEarned;

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
