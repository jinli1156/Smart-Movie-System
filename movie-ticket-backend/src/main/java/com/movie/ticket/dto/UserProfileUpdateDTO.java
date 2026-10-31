package com.movie.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 本人资料更新参数，仅开放昵称、基本资料和观影偏好等可编辑字段。 */
@Data
public class UserProfileUpdateDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称不能超过20个字符")
    private String nickname;

    @NotNull(message = "请选择性别")
    @Min(value = 0, message = "性别取值无效")
    @Max(value = 2, message = "性别取值无效")
    private Integer gender;

    @PastOrPresent(message = "生日不能晚于当前日期")
    private LocalDate birthday;

    @Size(max = 50, message = "城市不能超过50个字符")
    private String city;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @Size(max = 5, message = "最多选择5个喜欢的电影类型")
    private List<@NotBlank(message = "电影类型不能为空")
            @Size(max = 20, message = "电影类型不能超过20个字符") String> favoriteGenres;

    @Size(max = 50, message = "个性签名不能超过50个字符")
    private String signature;
}
