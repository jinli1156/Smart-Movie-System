package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/** 观影评价数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
