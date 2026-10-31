package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 用户优惠券数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Update("""
            UPDATE user_coupon
            SET status = CASE WHEN expire_time < #{now} THEN 2 ELSE 0 END,
                order_id = NULL,
                use_time = NULL
            WHERE id = #{couponId}
              AND status = 1
              AND order_id = #{orderId}
            """)
    int restoreIfUsedByOrder(@Param("couponId") Long couponId,
                             @Param("orderId") Long orderId,
                             @Param("now") LocalDateTime now);
}
