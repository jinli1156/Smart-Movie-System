package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.CinemaDTO;
import com.movie.ticket.entity.Cinema;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.vo.CinemaVO;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/** 影院资料查询与维护；管理操作校验影院权限及关联业务，避免越权或删除仍在使用的数据。 */
@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaMapper cinemaMapper;

    /** 按筛选条件分页查询影院，供列表页展示。 */
    public IPage<CinemaVO> getPage(Integer page, Integer size, String keyword) {
        Page<Cinema> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Cinema> wrapper = new LambdaQueryWrapper<>();
        Long scopedId = UserContext.scopeCinemaId(null);
        wrapper.eq(scopedId != null, Cinema::getId, scopedId);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Cinema::getName, keyword).or().like(Cinema::getAddress, keyword));
        }

        wrapper.orderByDesc(Cinema::getCreateTime);
        IPage<Cinema> cinemaPage = cinemaMapper.selectPage(pageParam, wrapper);

        return cinemaPage.convert(this::convertToVO);
    }

    /** 读取影院列表，供选择器或公开目录使用。 */
    public List<CinemaVO> getAll() {
        Long scopedId = UserContext.scopeCinemaId(null);
        List<Cinema> cinemas = cinemaMapper.selectList(
                new LambdaQueryWrapper<Cinema>()
                        .eq(scopedId != null, Cinema::getId, scopedId)
                        .eq(Cinema::getStatus, 1)
                        .orderByDesc(Cinema::getCreateTime)
        );
        return cinemas.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /** 根据 ID 查询影院详情。 */
    public CinemaVO getById(Long id) {
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        return convertToVO(cinema);
    }

    /** 新增影院，由业务层执行参数及关联数据校验。 */
    public Long create(CinemaDTO dto) {
        UserContext.requirePlatformAdmin();
        Cinema cinema = new Cinema();
        BeanUtils.copyProperties(dto, cinema);
        if (cinema.getStatus() == null) {
            cinema.setStatus(1);
        }
        cinemaMapper.insert(cinema);
        return cinema.getId();
    }

    /** 更新影院，由业务层校验当前操作权限与业务条件。 */
    public void update(Long id, CinemaDTO dto) {
        UserContext.requireCinema(id);
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        BeanUtils.copyProperties(dto, cinema);
        cinema.setId(id);
        cinemaMapper.updateById(cinema);
    }

    /** 删除影院，由业务层检查权限及关联数据限制。 */
    public void delete(Long id) {
        UserContext.requirePlatformAdmin();
        Cinema cinema = cinemaMapper.selectById(id);
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }
        cinemaMapper.deleteById(id);
    }

    /** 修改影院状态，具体可用状态和操作限制由业务层校验。 */
    public void updateStatus(Long id, Integer status) {
        UserContext.requireCinema(id);
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
