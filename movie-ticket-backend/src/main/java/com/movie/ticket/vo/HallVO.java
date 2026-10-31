package com.movie.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 影厅展示数据，包含所属影院及座位布局。 */
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
