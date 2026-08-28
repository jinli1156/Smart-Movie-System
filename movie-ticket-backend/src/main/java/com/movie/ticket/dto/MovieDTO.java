package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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
