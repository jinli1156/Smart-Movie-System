package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.Hall;
import org.apache.ibatis.annotations.Mapper;

/** 影厅数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface HallMapper extends BaseMapper<Hall> {
    /** 按 ID 顺序锁定影院影厅，协调手工排片与整日草案发布的并发修改。 */
    @org.apache.ibatis.annotations.Select("SELECT id FROM hall WHERE cinema_id = #{cinemaId} AND deleted = 0 ORDER BY id FOR UPDATE")
    java.util.List<Long> lockCinemaHalls(@org.apache.ibatis.annotations.Param("cinemaId") Long cinemaId);
}
