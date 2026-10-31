package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 用户数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 只更新积分字段，避免资料字段的空值被误写入。 */
    @Update("UPDATE `user` SET points = #{points}, update_time = CURRENT_TIMESTAMP WHERE id = #{userId} AND deleted = 0")
    int updatePointsOnly(@Param("userId") Long userId, @Param("points") BigDecimal points);

    /** 锁定用户行，串行处理同一账号的积分变更。 */
    @Select("SELECT * FROM `user` WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    User selectForUpdate(@Param("userId") Long userId);

    /** 退款时扣回本单奖励积分并返还本单抵扣积分，余额最低为零。 */
    @Update("UPDATE `user` SET points = GREATEST(0, COALESCE(points, 0) - #{pointsEarned}) + #{pointsUsed} WHERE id = #{userId}")
    int restorePoints(@Param("userId") Long userId,
                      @Param("pointsUsed") BigDecimal pointsUsed,
                      @Param("pointsEarned") BigDecimal pointsEarned);
}
