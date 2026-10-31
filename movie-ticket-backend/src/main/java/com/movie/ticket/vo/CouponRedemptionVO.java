package com.movie.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 兑换码领取记录视图，组合账号、兑换码和所发优惠券信息。 */
@Data
public class CouponRedemptionVO {
    private Long id;
    private Long couponCodeId;
    private String code;
    private String couponName;
    private Long userId;
    private String username;
    private String nickname;
    private Long userCouponId;
    private Integer couponStatus;
    private LocalDateTime redeemTime;
    private LocalDateTime useTime;
}
