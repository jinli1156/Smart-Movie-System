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
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** 影厅资料与座位布局管理，校验所属影院、座位容量和布局合法性。 */
@Service
@RequiredArgsConstructor
public class HallService {

    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;

    /** 按筛选条件分页查询影厅，供列表页展示。 */
    public IPage<HallVO> getPage(Integer page, Integer size, Long cinemaId) {
        cinemaId = UserContext.scopeCinemaId(cinemaId);
        Page<Hall> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Hall> wrapper = new LambdaQueryWrapper<>();

        if (cinemaId != null) {
            wrapper.eq(Hall::getCinemaId, cinemaId);
        }

        wrapper.orderByDesc(Hall::getCreateTime);
        IPage<Hall> hallPage = hallMapper.selectPage(pageParam, wrapper);

        return hallPage.convert(this::convertToVO);
    }

    /** 读取指定影院下的影厅列表。 */
    public List<HallVO> getByCinemaId(Long cinemaId) {
        List<Hall> halls = hallMapper.selectList(
                new LambdaQueryWrapper<Hall>()
                        .eq(Hall::getCinemaId, cinemaId)
                        .eq(Hall::getStatus, 1)
                        .orderByAsc(Hall::getName)
        );
        return halls.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /** 根据 ID 查询影厅详情。 */
    public HallVO getById(Long id) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        return convertToVO(hall);
    }

    /** 新增影厅，由业务层执行参数及关联数据校验。 */
    public Long create(HallDTO dto) {
        UserContext.requireCinema(dto.getCinemaId());
        HallSeatLayout.parse(dto.getSeatLayout(), dto.getTotalRows(), dto.getTotalCols());
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
        return hall.getId();
    }

    /** 更新影厅，由业务层校验当前操作权限与业务条件。 */
    public void update(Long id, HallDTO dto) {
        UserContext.requireCinema(dto.getCinemaId());
        HallSeatLayout.parse(dto.getSeatLayout(), dto.getTotalRows(), dto.getTotalCols());
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }

        UserContext.requireCinema(hall.getCinemaId());
        BeanUtils.copyProperties(dto, hall);
        hall.setTotalSeats(dto.getTotalRows() * dto.getTotalCols());
        hall.setId(id);
        hallMapper.updateById(hall);
    }

    /** 删除影厅，由业务层检查权限及关联数据限制。 */
    public void delete(Long id) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        UserContext.requireCinema(hall.getCinemaId());
        hallMapper.deleteById(id);
    }

    /** 修改影厅状态，具体可用状态和操作限制由业务层校验。 */
    public void updateStatus(Long id, Integer status) {
        Hall hall = hallMapper.selectById(id);
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }
        UserContext.requireCinema(hall.getCinemaId());
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
