package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.CouponCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 优惠券兑换码数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface CouponCodeMapper extends BaseMapper<CouponCode> {

    /** 锁定兑换码规则行，串行校验本轮领取。 */
    @Select("SELECT * FROM coupon_code WHERE code = #{code} FOR UPDATE")
    CouponCode selectByCodeForUpdate(@Param("code") String code);

    /** 只在兑换码启用且未达发放上限时增加已领取数量。 */
    @Update("""
            UPDATE coupon_code
            SET redeemed_count = redeemed_count + 1
            WHERE id = #{id}
              AND status = 1
              AND redeemed_count < max_redemptions
            """)
    int incrementRedeemedCount(@Param("id") Long id);
}
