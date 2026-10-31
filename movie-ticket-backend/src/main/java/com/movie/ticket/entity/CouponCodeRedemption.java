package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 兑换码领取记录，关联兑换码、领取账号及实际发放的优惠券。 */
@Data
@TableName("coupon_code_redemption")
public class CouponCodeRedemption {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponCodeId;
    private Long userId;
    private Long userCouponId;
    private LocalDateTime redeemTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
