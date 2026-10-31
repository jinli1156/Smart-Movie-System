package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 购票订单数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface TicketOrderMapper extends BaseMapper<TicketOrder> {

    /** 以待支付和已支付订单判断座位占用，避免 Redis 锁过期后重复出售。 */
    @Select("""
            SELECT COUNT(*)
            FROM ticket_order
            WHERE showtime_id = #{showtimeId}
              AND status IN (0, 1)
              AND FIND_IN_SET(#{seat}, seats) > 0
            """)
    long countActiveOrdersBySeat(@Param("showtimeId") Long showtimeId,
                                 @Param("seat") String seat);

    /** 仅将本人的待支付订单更新为已支付，更新行数用于阻止重复支付。 */
    @Update("""
            UPDATE ticket_order
            SET status = 1,
                original_price = #{originalPrice},
                discount_amount = #{discountAmount},
                points_used = #{pointsUsed},
                points_earned = #{pointsEarned},
                total_price = #{totalPrice},
                coupon_id = #{couponId},
                pay_time = #{payTime},
                ticket_code = #{ticketCode},
                is_picked = 0
            WHERE id = #{orderId}
              AND user_id = #{userId}
              AND status = 0
            """)
    int markPaidIfPending(@Param("orderId") Long orderId,
                          @Param("userId") Long userId,
                          @Param("originalPrice") BigDecimal originalPrice,
                          @Param("discountAmount") BigDecimal discountAmount,
                          @Param("pointsUsed") BigDecimal pointsUsed,
                          @Param("pointsEarned") BigDecimal pointsEarned,
                          @Param("totalPrice") BigDecimal totalPrice,
                          @Param("couponId") Long couponId,
                          @Param("payTime") LocalDateTime payTime,
                          @Param("ticketCode") String ticketCode);

    /** 仅核销已支付且尚未取票的订单，防止重复取票。 */
    @Update("""
            UPDATE ticket_order
            SET is_picked = 1,
                pick_time = #{pickTime}
            WHERE id = #{orderId}
              AND status = 1
              AND is_picked = 0
            """)
    int markPickedIfPaid(@Param("orderId") Long orderId,
                         @Param("pickTime") LocalDateTime pickTime);

    /** 仅取消仍待支付的订单，返回成功后才可回补库存。 */
    @Update("""
            UPDATE ticket_order
            SET status = 2,
                cancel_time = #{cancelTime}
            WHERE id = #{orderId}
              AND status = 0
            """)
    int cancelIfPending(@Param("orderId") Long orderId,
                        @Param("cancelTime") LocalDateTime cancelTime);

    /** 同时检查待支付状态和超时阈值，防止多实例重复处理超时订单。 */
    @Update("""
            UPDATE ticket_order
            SET status = 2,
                cancel_time = #{cancelTime}
            WHERE id = #{orderId}
              AND status = 0
              AND create_time < #{timeoutThreshold}
            """)
    int cancelIfTimedOut(@Param("orderId") Long orderId,
                         @Param("timeoutThreshold") LocalDateTime timeoutThreshold,
                         @Param("cancelTime") LocalDateTime cancelTime);

    /** 仅将未取票的已支付订单更新为已退款，防止重复退款。 */
    @Update("""
            UPDATE ticket_order
            SET status = 3,
                cancel_time = #{refundTime},
                remark = #{remark}
            WHERE id = #{orderId}
              AND status = 1
              AND is_picked = 0
            """)
    int refundIfPaidAndNotPicked(@Param("orderId") Long orderId,
                                 @Param("refundTime") LocalDateTime refundTime,
                                 @Param("remark") String remark);

    IPage<OrderVO> selectOrderPage(Page<OrderVO> page,
                                   @Param("userId") Long userId,
                                   @Param("orderNo") String orderNo,
                                   @Param("status") Integer status,
                                   @Param("historyOnly") boolean historyOnly);

    OrderVO selectOrderViewByOrderNo(@Param("orderNo") String orderNo);

    IPage<OrderVO> selectCinemaOrderPage(Page<OrderVO> page, @Param("cinemaId") Long cinemaId,
                                        @Param("orderNo") String orderNo, @Param("status") Integer status);

    Map<String, Object> selectCinemaDashboard(@Param("cinemaId") Long cinemaId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectCinemaBoxOffice(@Param("cinemaId") Long cinemaId, @Param("limit") int limit);

    List<Map<String, Object>> selectCinemaRevenue(@Param("cinemaId") Long cinemaId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    BigDecimal sumPaidRevenue(@Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectBoxOfficeRanking(@Param("limit") int limit);

    List<Map<String, Object>> selectHistoricalBoxOfficeRanking();

    List<Map<String, Object>> selectBoxOfficeRankingBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    BigDecimal sumPaidRevenueByShowtime(@Param("showtimeId") Long showtimeId);

    List<Map<String, Object>> selectRevenueByDate(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    Map<String, Object> selectCinemaAggregate(@Param("cinemaId") Long cinemaId);
}
