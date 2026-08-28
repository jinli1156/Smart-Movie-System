package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.movie.ticket.entity.Cinema;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CinemaMapper extends BaseMapper<Cinema> {
}
