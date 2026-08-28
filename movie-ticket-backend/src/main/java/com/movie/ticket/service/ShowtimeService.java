package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.ShowtimeDTO;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.ShowtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    private final ShowtimeMapper showtimeMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final TicketOrderMapper orderMapper;

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(0, "可售");
        STATUS_MAP.put(1, "已结束");
        STATUS_MAP.put(2, "已取消");
    }

    public IPage<ShowtimeVO> getPage(Integer page, Integer size, Long movieId, Long cinemaId, LocalDate date, Integer status) {
        Page<Showtime> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Showtime> wrapper = new LambdaQueryWrapper<>();

        if (movieId != null) {
            wrapper.eq(Showtime::getMovieId, movieId);
        }

        if (cinemaId != null) {
            wrapper.eq(Showtime::getCinemaId, cinemaId);
        }

        if (date != null) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            wrapper.between(Showtime::getStartTime, startOfDay, endOfDay);
        }

        if (status != null) {
            wrapper.eq(Showtime::getStatus, status);
        }

        wrapper.orderByAsc(Showtime::getStatus)
                .orderByDesc(Showtime::getStartTime);
        IPage<Showtime> showtimePage = showtimeMapper.selectPage(pageParam, wrapper);

        return showtimePage.convert(this::convertToVO);
    }

    public List<ShowtimeVO> getByMovieAndCinema(Long movieId, Long cinemaId, LocalDate date) {
        LambdaQueryWrapper<Showtime> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Showtime::getMovieId, movieId)
                .eq(Showtime::getCinemaId, cinemaId)
                .eq(Showtime::getStatus, 0);

        if (date != null) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            wrapper.between(Showtime::getStartTime, startOfDay, endOfDay);
        }

        wrapper.orderByAsc(Showtime::getStartTime);
        List<Showtime> showtimes = showtimeMapper.selectList(wrapper);

        return showtimes.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    public ShowtimeVO getById(Long id) {
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }
        return convertToVO(showtime);
    }

    public void create(ShowtimeDTO dto) {
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }

        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }

        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        if (!hall.getCinemaId().equals(dto.getCinemaId())) {
            throw new BusinessException("影厅不属于该影院");
        }

        if (dto.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("开始时间不能早于当前时间");
        }

        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BusinessException("结束时间不能早于开始时间");
        }

        Long count = showtimeMapper.selectCount(
                new LambdaQueryWrapper<Showtime>()
                        .eq(Showtime::getHallId, dto.getHallId())
                        .and(w -> w
                                .between(Showtime::getStartTime, dto.getStartTime(), dto.getEndTime())
                                .or()
                                .between(Showtime::getEndTime, dto.getStartTime(), dto.getEndTime())
                        )
        );

        if (count > 0) {
            throw new BusinessException("该时间段影厅已有排片");
        }

        Showtime showtime = new Showtime();
        BeanUtils.copyProperties(dto, showtime);

        int totalSeats = hall.getTotalRows() * hall.getTotalCols();
        showtime.setTotalSeats(totalSeats);
        showtime.setAvailableSeats(totalSeats);

        showtime.setStatus(calculateStatus(dto.getEndTime(), null));

        showtimeMapper.insert(showtime);
    }

    public void update(Long id, ShowtimeDTO dto) {
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }

        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }

        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在");
        }

        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        if (!hall.getCinemaId().equals(dto.getCinemaId())) {
            throw new BusinessException("影厅不属于该影院");
        }

        BeanUtils.copyProperties(dto, showtime);
        showtime.setId(id);
        showtime.setStatus(calculateStatus(dto.getEndTime(), showtime.getStatus()));
        showtimeMapper.updateById(showtime);
    }

    public void delete(Long id) {
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }

        if (showtime.getAvailableSeats() < showtime.getTotalSeats()) {
            throw new BusinessException("该场次已有订单，无法删除");
        }

        showtimeMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }
        showtime.setStatus(status);
        showtimeMapper.updateById(showtime);
    }

    private Integer calculateStatus(LocalDateTime endTime, Integer currentStatus) {
        if (currentStatus != null && currentStatus == 2) {
            return 2;
        }
        if (endTime != null && endTime.isBefore(LocalDateTime.now())) {
            return 1;
        }
        return 0;
    }

    private ShowtimeVO convertToVO(Showtime showtime) {
        ShowtimeVO vo = new ShowtimeVO();
        BeanUtils.copyProperties(showtime, vo);
        vo.setStatusText(STATUS_MAP.getOrDefault(showtime.getStatus(), "未知"));

        Movie movie = movieMapper.selectById(showtime.getMovieId());
        if (movie != null) {
            vo.setMovieTitle(movie.getTitle());
            vo.setMoviePoster(movie.getPoster());
            vo.setMovieDuration(movie.getDuration());
        }

        Cinema cinema = cinemaMapper.selectById(showtime.getCinemaId());
        if (cinema != null) {
            vo.setCinemaName(cinema.getName());
        }

        Hall hall = hallMapper.selectById(showtime.getHallId());
        if (hall != null) {
            vo.setHallName(hall.getName());
        }

        return vo;
    }

    public Map<String, Object> getSeatStatus(Long id) {
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }

        Hall hall = hallMapper.selectById(showtime.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        List<TicketOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getShowtimeId, id)
                        .in(TicketOrder::getStatus, 0, 1)
        );

        List<String> occupiedSeats = orders.stream()
                .flatMap(order -> java.util.Arrays.stream(order.getSeats().split(",")))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalRows", hall.getTotalRows());
        result.put("totalCols", hall.getTotalCols());
        result.put("seatLayout", hall.getSeatLayout());
        result.put("occupiedSeats", occupiedSeats);
        result.put("availableSeats", showtime.getAvailableSeats());
        result.put("totalSeats", showtime.getTotalSeats());

        return result;
    }
}
