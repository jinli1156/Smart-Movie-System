package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 影片新增或编辑参数，声明片名、片长、上映日期与媒体资料等字段约束。 */
@Data
public class MovieDTO {

    @NotBlank(message = "电影名称不能为空")
    private String title;

    private String englishTitle;

    private String director;

    private String actors;

    private String genre;

    private String country;

    private String language;

    @NotNull(message = "时长不能为空")
    private Integer duration;

    private LocalDate releaseDate;

    private String poster;

    private String trailer;

    private String description;

    private BigDecimal rating;

    private Integer status;
}
