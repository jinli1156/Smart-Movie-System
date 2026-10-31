package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 兑换码发放规则实体，保存优惠、领取上限、已发数量及生效时间。 */
@Data
@TableName("coupon_code")
public class CouponCode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private BigDecimal amount;
    private BigDecimal minSpend;
    private Integer validDays;
    private Integer maxRedemptions;
    private Integer redeemedCount;
    private LocalDateTime validFrom;
    private LocalDateTime expireTime;
    /** 0-停用，1-启用。 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
