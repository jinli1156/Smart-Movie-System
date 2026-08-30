package com.movie.ticket.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class FavoriteMovieVO extends MovieVO {
    private LocalDateTime favoriteTime;
}
