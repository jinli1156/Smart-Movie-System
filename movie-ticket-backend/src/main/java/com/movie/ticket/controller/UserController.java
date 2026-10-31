package com.movie.ticket.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.common.Result;
import com.movie.ticket.dto.ChangePasswordRequest;
import com.movie.ticket.dto.UserProfileUpdateDTO;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.UserProfileService;
import com.movie.ticket.vo.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** 用户接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final CinemaMapper cinemaMapper;

    /** 返回当前登录用户资料，避免使用客户端指定身份。 */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserProfileVO> getCurrentUser() {
        return Result.success(userProfileService.getCurrentProfile());
    }

    /** 读取当前登录用户的积分余额。 */
    @GetMapping("/points")
    @PreAuthorize("isAuthenticated()")
    public Result<BigDecimal> getPoints() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.success(user.getPoints() == null ? BigDecimal.ZERO : user.getPoints());
    }

    /** 更新本人允许编辑的资料字段。 */
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO request) {
        return Result.success("资料已更新", userProfileService.updateCurrentProfile(request));
    }

    /** 验证旧密码后更新本人密码。 */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getPassword, passwordEncoder.encode(request.getNewPassword())));
        return Result.success("密码修改成功");
    }

    /** 按筛选条件分页查询用户，供列表页展示。 */
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<IPage<User>> getPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {

        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);

        IPage<User> result = userMapper.selectPage(pageParam, wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    /** 根据 ID 查询用户详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> getById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /** 更新用户，由业务层校验当前操作权限与业务条件。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody User user) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "用户不存在");
        }
        String role = user.getRole() == null ? existing.getRole() : user.getRole();
        if (!java.util.Set.of("USER", "CINEMA_ADMIN", "ADMIN").contains(role)) {
            throw new BusinessException(400, "角色无效");
        }
        Long cinemaId = "CINEMA_ADMIN".equals(role) ? user.getCinemaId() : null;
        if ("CINEMA_ADMIN".equals(role) && (cinemaId == null || cinemaMapper.selectById(cinemaId) == null)) {
            throw new BusinessException(400, "请为影院管理员分配有效影院");
        }
        if (user.getStatus() != null && user.getStatus() != 0 && user.getStatus() != 1) {
            throw new BusinessException(400, "账号状态无效");
        }
        // Explicit allowlist: do not change points, passwords, or unrelated profile fields.
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getRole, role).set(User::getCinemaId, cinemaId)
                .set(user.getNickname() != null, User::getNickname, user.getNickname())
                .set(user.getPhone() != null, User::getPhone, user.getPhone())
                .set(user.getEmail() != null, User::getEmail, user.getEmail())
                .set(user.getStatus() != null, User::getStatus, user.getStatus()));
        return Result.success("更新成功");
    }

    /** 修改用户状态，具体可用状态和操作限制由业务层校验。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BusinessException(400, "账号状态无效");
        if (userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getStatus, status)) != 1) throw new BusinessException(404, "用户不存在");
        return Result.success("状态更新成功");
    }

    /** 由平台管理员重置指定账号密码。 */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        if (password == null || password.length() < 6) {
            throw new BusinessException(400, "密码长度不能少于6位");
        }
        if (userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getPassword, passwordEncoder.encode(password))) != 1) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.success("密码重置成功");
    }

    /** 删除用户，由业务层检查权限及关联数据限制。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.success("删除成功");
    }
}
