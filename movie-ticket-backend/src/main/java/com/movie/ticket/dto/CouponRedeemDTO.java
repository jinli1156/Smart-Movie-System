package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 领取优惠券请求，携带用户输入的兑换码。 */
@Data
public class CouponRedeemDTO {

    @NotBlank(message = "请输入兑换码")
    @Size(max = 32, message = "兑换码长度不能超过 32 位")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "兑换码只能包含字母、数字和短横线")
    private String code;
}
