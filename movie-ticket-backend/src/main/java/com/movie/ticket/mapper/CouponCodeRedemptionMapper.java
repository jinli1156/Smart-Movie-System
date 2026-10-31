package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.entity.CouponCodeRedemption;
import com.movie.ticket.vo.CouponRedemptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 兑换码领取记录数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface CouponCodeRedemptionMapper extends BaseMapper<CouponCodeRedemption> {

    @Select("""
            <script>
            SELECT r.id,
                   r.coupon_code_id,
                   c.code,
                   c.name AS coupon_name,
                   r.user_id,
                   u.username,
                   u.nickname,
                   r.user_coupon_id,
                   uc.status AS coupon_status,
                   r.redeem_time,
                   uc.use_time
            FROM coupon_code_redemption r
            INNER JOIN coupon_code c ON c.id = r.coupon_code_id
            INNER JOIN `user` u ON u.id = r.user_id
            INNER JOIN user_coupon uc ON uc.id = r.user_coupon_id
            WHERE 1 = 1
            <if test="couponCodeId != null">
              AND r.coupon_code_id = #{couponCodeId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                   OR c.code LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY r.redeem_time DESC
            </script>
            """)
    IPage<CouponRedemptionVO> selectAdminPage(Page<CouponRedemptionVO> page,
                                               @Param("couponCodeId") Long couponCodeId,
                                               @Param("keyword") String keyword);
}
