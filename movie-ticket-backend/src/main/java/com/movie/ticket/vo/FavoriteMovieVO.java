package com.movie.ticket.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 想看影片视图，组合影片资料及收藏时间。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FavoriteMovieVO extends MovieVO {
    private LocalDateTime favoriteTime;
}
