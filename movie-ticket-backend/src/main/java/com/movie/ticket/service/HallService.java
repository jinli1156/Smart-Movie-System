package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.HallDTO;
import com.movie.ticket.entity.Cinema;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.vo.HallVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HallService {

    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;

    public IPage<HallVO> getPage(Integer page, Integer size, Long cinemaId) {
        Page<Hall> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Hall> wrapper = new LambdaQueryWrapper<>();

        if (cinemaId != null) {
            wrapper.eq(Hall::getCinemaId, cinemaId);
        }

        wrapper.orderByDesc(Hall::getCreateTime);
        IPage<Hall> hallPage = hallMapper.selectPage(pageParam, wrapper);

        return hallPage.convert(this::convertToVO);
    }

    public List<HallVO> getByCinemaId(Long cinemaId) {
        List<Hall> halls = hallMapper.selectList(
                new LambdaQueryWrapper<Hall>()
                        .eq(Hall::getCinemaId, cinemaId)
                        .eq(Hall::getStatus, 1)
                        .orderByAsc(Hall::getName)
        );
        return halls.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    public HallVO getById(Long id) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        return convertToVO(hall);
    }

    public void create(HallDTO dto) {
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }

        Hall hall = new Hall();
        BeanUtils.copyProperties(dto, hall);
        hall.setTotalSeats(dto.getTotalRows() * dto.getTotalCols());
        if (hall.getStatus() == null) {
            hall.setStatus(1);
        }
        hallMapper.insert(hall);
    }

    public void update(Long id, HallDTO dto) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }

        BeanUtils.copyProperties(dto, hall);
        hall.setTotalSeats(dto.getTotalRows() * dto.getTotalCols());
        hall.setId(id);
        hallMapper.updateById(hall);
    }

    public void delete(Long id) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        hallMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        hall.setStatus(status);
        hallMapper.updateById(hall);
    }

    private HallVO convertToVO(Hall hall) {
        HallVO vo = new HallVO();
        BeanUtils.copyProperties(hall, vo);

        Cinema cinema = cinemaMapper.selectById(hall.getCinemaId());
        if (cinema != null) {
            vo.setCinemaName(cinema.getName());
        }

        return vo;
    }
}
