package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/** 登录成功响应，向客户端返回 JWT 与可展示的用户身份资料。 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String nickname;
        private String phone;
        private String email;
        private String avatar;
        private Integer gender;
        private LocalDate birthday;
        private String city;
        private String address;
        private List<String> favoriteGenres;
        private String signature;
        private String role;
        private Long cinemaId;
        private BigDecimal points;
    }
}
