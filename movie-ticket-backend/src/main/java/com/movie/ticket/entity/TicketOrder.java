package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 购票订单实体，记录座位、金额优惠、积分变化及支付、取消和取票状态。 */
@Data
@TableName("ticket_order")
public class TicketOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long showtimeId;

    /** 座位名称以逗号分隔，供订单展示与释放占座使用。 */
    private String seats;

    @TableField("seat_count")
    private Integer ticketCount;

    private BigDecimal totalPrice;

    private BigDecimal originalPrice;

    private BigDecimal discountAmount;

    @TableField("points_used")
    private BigDecimal pointsUsed;

    @TableField("points_earned")
    private BigDecimal pointsEarned;

    private Long couponId;

    private String remark;

    /** 订单状态：0 待支付、1 已支付、2 已取消、3 已退款。 */
    private Integer status;

    private String ticketCode;

    /** 取票标记：0 未取票、1 已取票；与订单支付状态分别记录。 */
    private Integer isPicked;

    private LocalDateTime pickTime;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
