package com.movie.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HallVO {

    private Long id;

    private Long cinemaId;

    private String cinemaName;

    private String name;

    private Integer totalRows;

    private Integer totalCols;

    private Integer totalSeats;

    private String seatLayout;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
