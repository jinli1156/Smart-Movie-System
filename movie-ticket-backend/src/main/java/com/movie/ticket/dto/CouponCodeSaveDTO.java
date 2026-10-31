package com.movie.ticket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 兑换码发放规则输入，校验优惠金额、使用门槛、有效期与领取上限。 */
@Data
public class CouponCodeSaveDTO {

    @NotBlank(message = "请输入兑换码")
    @Size(max = 32, message = "兑换码长度不能超过32位")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "兑换码只能包含字母、数字和短横线")
    private String code;

    @NotBlank(message = "请输入优惠券名称")
    @Size(max = 100, message = "优惠券名称不能超过100个字符")
    private String name;

    @NotNull(message = "请输入优惠金额")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "请输入使用门槛")
    @DecimalMin(value = "0.00", message = "使用门槛不能小于0")
    private BigDecimal minSpend;

    @NotNull(message = "请输入领取后有效天数")
    @Min(value = 1, message = "有效天数不能少于1天")
    @Max(value = 3650, message = "有效天数不能超过3650天")
    private Integer validDays;

    @NotNull(message = "请输入最大发放数量")
    @Min(value = 1, message = "最大发放数量不能少于1张")
    private Integer maxRedemptions;

    @NotNull(message = "请选择生效时间")
    private LocalDateTime validFrom;

    @NotNull(message = "请选择截止时间")
    private LocalDateTime expireTime;

    @NotNull(message = "请选择状态")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 1, message = "状态值无效")
    private Integer status;
}
