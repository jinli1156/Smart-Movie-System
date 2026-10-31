package com.movie.ticket.vo;

import com.movie.ticket.entity.User;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/** 用户个人资料视图，只向客户端暴露允许展示的字段。 */
@Data
public class UserProfileVO {

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

    public static UserProfileVO from(User user) {
        UserProfileVO profile = new UserProfileVO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setNickname(user.getNickname());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        profile.setAvatar(user.getAvatar());
        profile.setGender(user.getGender());
        profile.setBirthday(user.getBirthday());
        profile.setCity(user.getCity());
        profile.setAddress(user.getAddress());
        profile.setFavoriteGenres(user.getFavoriteGenres());
        profile.setSignature(user.getSignature());
        profile.setRole(user.getRole());
        profile.setCinemaId(user.getCinemaId());
        profile.setPoints(user.getPoints() == null ? BigDecimal.ZERO : user.getPoints());
        return profile;
    }
}
