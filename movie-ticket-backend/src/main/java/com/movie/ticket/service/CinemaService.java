package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.CinemaDTO;
import com.movie.ticket.entity.Cinema;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.vo.CinemaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaMapper cinemaMapper;

    public IPage<CinemaVO> getPage(Integer page, Integer size, String keyword) {
        Page<Cinema> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Cinema> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Cinema::getName, keyword)
                    .or()
                    .like(Cinema::getAddress, keyword);
        }

        wrapper.orderByDesc(Cinema::getCreateTime);
        IPage<Cinema> cinemaPage = cinemaMapper.selectPage(pageParam, wrapper);

        return cinemaPage.convert(this::convertToVO);
    }

    public List<CinemaVO> getAll() {
        List<Cinema> cinemas = cinemaMapper.selectList(
                new LambdaQueryWrapper<Cinema>()
                        .eq(Cinema::getStatus, 1)
                        .orderByDesc(Cinema::getCreateTime)
        );
        return cinemas.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    public CinemaVO getById(Long id) {
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        return convertToVO(cinema);
    }

    public void create(CinemaDTO dto) {
        Cinema cinema = new Cinema();
        BeanUtils.copyProperties(dto, cinema);
        if (cinema.getStatus() == null) {
            cinema.setStatus(1);
        }
        cinemaMapper.insert(cinema);
    }

    public void update(Long id, CinemaDTO dto) {
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        BeanUtils.copyProperties(dto, cinema);
        cinema.setId(id);
        cinemaMapper.updateById(cinema);
    }

    public void delete(Long id) {
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        cinemaMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        cinema.setStatus(status);
        cinemaMapper.updateById(cinema);
    }

    private CinemaVO convertToVO(Cinema cinema) {
        CinemaVO vo = new CinemaVO();
        BeanUtils.copyProperties(cinema, vo);
        return vo;
    }
}
