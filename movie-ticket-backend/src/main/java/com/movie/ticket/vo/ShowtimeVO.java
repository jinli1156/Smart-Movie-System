package com.movie.ticket.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShowtimeVO {

    private Long id;

    private Long movieId;

    private String movieTitle;

    private String moviePoster;

    private Integer movieDuration;

    private Long cinemaId;

    private String cinemaName;

    private Long hallId;

    private String hallName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal basePrice;

    private Integer totalSeats;

    private Integer availableSeats;

    private Integer status;

    private String statusText;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
