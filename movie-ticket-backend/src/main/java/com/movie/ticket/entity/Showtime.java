package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 场次实体，关联影片、影院与影厅，保存票价、放映时间及可用座位数。 */
@Data
@TableName("showtime")
public class Showtime {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long movieId;

    private Long cinemaId;

    private Long hallId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal basePrice;

    private Integer totalSeats;

    /** 当前可用座位数，通过数据库条件更新扣减或回补。 */
    private Integer availableSeats;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
