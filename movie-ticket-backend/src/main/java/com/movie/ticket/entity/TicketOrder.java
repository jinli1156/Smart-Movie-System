package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ticket_order")
public class TicketOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long showtimeId;

    private String seats;

    @TableField("seat_count")
    private Integer ticketCount;

    private BigDecimal totalPrice;

    private BigDecimal originalPrice;

    private BigDecimal discountAmount;

    private Long couponId;

    private String remark;

    private Integer status;

    private String ticketCode;

    private Integer isPicked;

    private LocalDateTime pickTime;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
