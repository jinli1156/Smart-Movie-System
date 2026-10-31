package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.LoginRequest;
import com.movie.ticket.dto.RegisterRequest;
import com.movie.ticket.service.AuthService;
import com.movie.ticket.service.LoginCaptchaService;
import jakarta.servlet.http.HttpServletResponse;
import com.movie.ticket.vo.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 账号认证接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginCaptchaService loginCaptchaService;

    /** 生成一次性图形验证码，返回挑战 ID 与图片供登录页使用。 */
    @GetMapping("/captcha")
    public Result<LoginCaptchaService.Challenge> captcha(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(loginCaptchaService.create());
    }

    /** 校验验证码及账号凭据，成功后返回 JWT 和用户资料。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /** 注册普通用户账号，并初始化新用户权益。 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功");
    }
}
