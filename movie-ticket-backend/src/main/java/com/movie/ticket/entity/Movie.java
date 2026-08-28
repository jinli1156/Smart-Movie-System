package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("movie")
public class Movie {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
