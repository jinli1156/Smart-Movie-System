package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 影院持久化实体，保存地址、经纬度、联系方式与营业状态。 */
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

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
