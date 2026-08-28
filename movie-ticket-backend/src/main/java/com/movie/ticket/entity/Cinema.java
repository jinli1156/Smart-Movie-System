package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cinema")
public class Cinema {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private String phone;

    private String description;

    private String images;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
