package com.movie.ticket.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.User;
import com.movie.ticket.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUser("admin", "admin123", "系统管理员", "ADMIN");
        initUser("cinema_admin", "admin123", "影院管理员", "CINEMA_ADMIN");
        initUser("user1", "admin123", "测试用户", "USER");
    }

    private void initUser(String username, String rawPassword, String nickname, String role) {
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null) {
            return;
        } else {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setNickname(nickname);
            user.setRole(role);
            user.setStatus(1);
            userMapper.insert(user);
        }
    }
}
