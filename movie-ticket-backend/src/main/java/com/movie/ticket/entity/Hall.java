package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hall")
public class Hall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cinemaId;

    private String name;

    private Integer totalRows;

    private Integer totalCols;

    private Integer totalSeats;

    private String seatLayout;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
