package com.movie.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 支付优惠选择，携带所选优惠券及申请使用的积分，最终抵扣由服务端核验。 */
@Data
public class OrderPayDTO {
    private Long couponId;

    /** 本次支付使用的积分，100 积分抵扣 1 元。 */
    private BigDecimal points;
}
