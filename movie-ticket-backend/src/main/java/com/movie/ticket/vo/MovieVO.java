package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 影片详情与列表展示数据。 */
@Data
public class MovieVO {

    private Long id;

    private String title;

    private String englishTitle;

    private String director;

    private String actors;

    private String genre;

    private String country;

    private String language;

    private Integer duration;

    private LocalDate releaseDate;

    private String poster;

    private String trailer;

    private String description;

    private BigDecimal rating;

    private Integer status;

    private String statusText;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
