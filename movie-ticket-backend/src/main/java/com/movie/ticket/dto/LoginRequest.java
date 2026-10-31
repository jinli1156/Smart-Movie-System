package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 账号登录参数，携带用户名、密码及一次性验证码挑战。 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请刷新验证码")
    private String captchaId;

    @NotBlank(message = "请输入验证码")
    private String captcha;
}
