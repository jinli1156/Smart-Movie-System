package com.movie.ticket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ColdStartRequest {
    @NotEmpty(message = "请选择喜欢的电影")
    @Size(min = 3, max = 10, message = "请选择 3-10 部喜欢的电影")
    private List<Long> movieIds;

    @Size(max = 5, message = "最多选择 5 个类型")
    private List<String> favoriteGenres;
}
