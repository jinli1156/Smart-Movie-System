package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.LoginRequest;
import com.movie.ticket.dto.RegisterRequest;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.utils.JwtUtil;
import com.movie.ticket.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录与注册业务：校验验证码、账号状态和密码，签发 JWT，并初始化新用户资料及优惠券。 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CouponService couponService;
    private final LoginCaptchaService loginCaptchaService;

    /** 校验验证码及账号凭据，成功后返回 JWT 和用户资料。 */
    public LoginResponse login(LoginRequest request) {
        loginCaptchaService.verify(request.getCaptchaId(), request.getCaptcha());

        // 根据用户名查询用户信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        // 用户不存在时统一提示用户名或密码错误，避免暴露具体账号信息
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 使用密码加密器校验用户输入的明文密码与数据库中的加密密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 检查账号状态，状态为 0 表示账号已被禁用
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 登录验证通过，根据用户 ID、用户名和角色生成 JWT 身份认证令牌
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setPhone(user.getPhone());
        userInfo.setEmail(user.getEmail());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setGender(user.getGender());
        userInfo.setBirthday(user.getBirthday());
        userInfo.setCity(user.getCity());
        userInfo.setAddress(user.getAddress());
        userInfo.setFavoriteGenres(user.getFavoriteGenres());
        userInfo.setSignature(user.getSignature());
        userInfo.setRole(user.getRole());
        userInfo.setCinemaId(user.getCinemaId());
        userInfo.setPoints(user.getPoints() == null ? java.math.BigDecimal.ZERO : user.getPoints());

        return new LoginResponse(token, userInfo);
    }

    /** 注册普通用户账号，并初始化新用户权益。 */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (count > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        if (request.getPhone() != null) {
            count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, request.getPhone()));
            if (count > 0) {
                throw new BusinessException(409, "手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setRole("USER");
        user.setStatus(1);

        userMapper.insert(user);
        couponService.grantNewUserCoupons(user.getId());
    }
}
