package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.ShowtimeDTO;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.ShowtimeVO;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 场次查询与维护，校验影院权限、影厅时间冲突，并汇总实时座位占用状态。 */
@Service
@RequiredArgsConstructor
public class ShowtimeService {

    /** 场次结束后预留的散场、保洁和下一场入场准备时间。 */
    @Value("${movie.showtime.cleanup-minutes:15}")
    private long cleanupMinutes = 15L;

    private final ShowtimeMapper showtimeMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final TicketOrderMapper orderMapper;

    /** 按筛选条件分页查询场次，供列表页展示。 */
    public IPage<ShowtimeVO> getPage(Integer page, Integer size, Long movieId, Long cinemaId, LocalDate date, Integer status) {
        cinemaId = UserContext.scopeCinemaId(cinemaId);
        Page<ShowtimeVO> pageParam = new Page<>(page, size);
        LocalDateTime startOfDay = null;
        LocalDateTime endOfDay = null;
        if (date != null) {
            startOfDay = date.atStartOfDay();
            endOfDay = date.plusDays(1).atStartOfDay();
        }
        return showtimeMapper.selectShowtimePage(
                pageParam, movieId, cinemaId, startOfDay, endOfDay, status);
    }

    /** 查询指定影片在影院和日期范围内的可选场次。 */
    public List<ShowtimeVO> getByMovieAndCinema(Long movieId, Long cinemaId, LocalDate date) {
        LocalDateTime startOfDay = null;
        LocalDateTime endOfDay = null;
        if (date != null) {
            startOfDay = date.atStartOfDay();
            endOfDay = date.plusDays(1).atStartOfDay();
        }
        return showtimeMapper.selectShowtimeViews(
                movieId, cinemaId, startOfDay, endOfDay, 0, null);
    }

    /** 根据 ID 查询场次详情。 */
    public ShowtimeVO getById(Long id) {
        List<ShowtimeVO> showtimes = showtimeMapper.selectShowtimeViews(
                null, null, null, null, null, id);
        if (showtimes.isEmpty()) {
            throw new BusinessException("场次不存在");
        }
        return showtimes.get(0);
    }

    /** 新增场次，由业务层执行参数及关联数据校验。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void create(ShowtimeDTO dto) {
        UserContext.requireCinema(dto.getCinemaId());
        hallMapper.lockCinemaHalls(dto.getCinemaId());
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        validateMovieCanSchedule(movie);

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

        validateTimeRange(dto, true);
        validateHallConflict(null, dto);

        Showtime showtime = new Showtime();
        BeanUtils.copyProperties(dto, showtime);

        int totalSeats = hall.getTotalRows() * hall.getTotalCols();
        showtime.setTotalSeats(totalSeats);
        showtime.setAvailableSeats(totalSeats);

        showtime.setStatus(calculateStatus(dto.getEndTime(), null));

        showtimeMapper.insert(showtime);
    }

    /** 更新场次，由业务层校验当前操作权限与业务条件。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void update(Long id, ShowtimeDTO dto) {
        UserContext.requireCinema(dto.getCinemaId());
        Showtime source = showtimeMapper.selectById(id);
        if (source == null) throw new BusinessException("场次不存在");
        UserContext.requireCinema(source.getCinemaId());
        java.util.stream.Stream.of(source.getCinemaId(), dto.getCinemaId()).distinct().sorted()
                .forEach(hallMapper::lockCinemaHalls);
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }

        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new BusinessException("电影不存在");
        }
        validateMovieCanSchedule(movie);

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

        validateTimeRange(dto, false);
        validateHallConflict(id, dto);

        BeanUtils.copyProperties(dto, showtime);
        showtime.setId(id);
        showtime.setStatus(calculateStatus(dto.getEndTime(), showtime.getStatus()));
        showtimeMapper.updateById(showtime);
    }

    /** 删除场次，由业务层检查权限及关联数据限制。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Showtime source = showtimeMapper.selectById(id);
        if (source == null) throw new BusinessException("场次不存在");
        UserContext.requireCinema(source.getCinemaId());
        hallMapper.lockCinemaHalls(source.getCinemaId());
        Showtime showtime = showtimeMapper.selectById(id);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }

        if (showtime.getAvailableSeats() < showtime.getTotalSeats()) {
            throw new BusinessException("该场次已有订单，无法删除");
        }

        showtimeMapper.deleteById(id);
    }

    /** 修改场次状态，具体可用状态和操作限制由业务层校验。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Showtime source = showtimeMapper.selectById(id);
        if (source == null) throw new BusinessException("场次不存在");
        UserContext.requireCinema(source.getCinemaId());
        hallMapper.lockCinemaHalls(source.getCinemaId());
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

    private void validateMovieCanSchedule(Movie movie) {
        if (!Integer.valueOf(1).equals(movie.getStatus())) {
            throw new BusinessException("仅正在热映的影片可以排片");
        }
    }

    private void validateTimeRange(ShowtimeDTO dto, boolean requireFutureStart) {
        if (requireFutureStart && dto.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("开始时间不能早于当前时间");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }

    private void validateHallConflict(Long excludedShowtimeId, ShowtimeDTO dto) {
        // 等价于：existing.start < new.end + cleanup
        //       AND existing.end + cleanup > new.start。
        LocalDateTime occupiedFrom = dto.getStartTime().minusMinutes(cleanupMinutes);
        LocalDateTime occupiedUntil = dto.getEndTime().plusMinutes(cleanupMinutes);
        long conflictCount = showtimeMapper.countConflictingShowtimes(
                dto.getHallId(), occupiedFrom, occupiedUntil, excludedShowtimeId);

        if (conflictCount > 0) {
            throw new BusinessException(String.format(
                    "该时间段影厅已有排片（需预留%d分钟清场时间）", cleanupMinutes));
        }
    }

    /** 合并座位布局、有效订单与临时占用，返回当前座位状态。 */
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
