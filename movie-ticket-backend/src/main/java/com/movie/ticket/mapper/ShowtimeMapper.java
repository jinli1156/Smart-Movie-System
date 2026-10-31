package com.movie.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.vo.ShowtimeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/** 场次数据访问接口，封装 MyBatis-Plus 基础操作及本模块的自定义 SQL。 */
@Mapper
public interface ShowtimeMapper extends BaseMapper<Showtime> {
    /** 按影片、影院、时间及票价等条件查询助手可推荐的场次。 */
    List<ShowtimeVO> selectAssistantCandidates(@Param("movieId") Long movieId, @Param("cinemaId") Long cinemaId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("bookableAfter") LocalDateTime bookableAfter, @Param("maxPrice") BigDecimal maxPrice,
            @Param("priceExclusive") boolean priceExclusive, @Param("filters") java.util.Map<String, Object> filters);

    /** 查询同日满足人数和可售时间要求的比价候选场次。 */
    List<ShowtimeVO> selectPriceComparisonViews(@Param("movieId") Long movieId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("bookableAfter") LocalDateTime bookableAfter, @Param("people") int people);

    IPage<ShowtimeVO> selectAssistantPage(Page<ShowtimeVO> page,
            @Param("movieId") Long movieId, @Param("cinemaId") Long cinemaId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("bookableAfter") LocalDateTime bookableAfter,
            @Param("maxPrice") BigDecimal maxPrice, @Param("priceExclusive") boolean priceExclusive,
            @Param("filters") java.util.Map<String, Object> filters);

    /** 查询影厅占用时间段冲突，编辑时排除当前场次。 */
    @Select("""
            SELECT COUNT(*)
            FROM showtime
            WHERE hall_id = #{hallId}
              AND deleted = 0 AND status <> 2
              AND start_time < #{occupiedUntil}
              AND end_time > #{occupiedFrom}
              AND (#{excludedShowtimeId} IS NULL OR id <> #{excludedShowtimeId})
            """)
    long countConflictingShowtimes(@Param("hallId") Long hallId,
                                   @Param("occupiedFrom") LocalDateTime occupiedFrom,
                                   @Param("occupiedUntil") LocalDateTime occupiedUntil,
                                   @Param("excludedShowtimeId") Long excludedShowtimeId);

    /** 仅在场次可售且余票充足时原子扣减；返回 1 表示成功，避免并发超卖。 */
    @Update("""
            UPDATE showtime
            SET available_seats = available_seats - #{seatCount}
            WHERE id = #{showtimeId}
              AND deleted = 0
              AND status = 0
              AND available_seats >= #{seatCount}
            """)
    int reserveAvailableSeats(@Param("showtimeId") Long showtimeId,
                              @Param("seatCount") int seatCount);

    /** 原子回补余票并限制不超过影厅容量，调用方须检查更新行数。 */
    @Update("""
            UPDATE showtime
            SET available_seats = available_seats + #{seatCount}
            WHERE id = #{showtimeId}
              AND available_seats + #{seatCount} <= total_seats
            """)
    int restoreAvailableSeats(@Param("showtimeId") Long showtimeId,
                              @Param("seatCount") int seatCount);

    IPage<ShowtimeVO> selectShowtimePage(Page<ShowtimeVO> page,
                                         @Param("movieId") Long movieId,
                                         @Param("cinemaId") Long cinemaId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("status") Integer status);

    List<ShowtimeVO> selectShowtimeViews(@Param("movieId") Long movieId,
                                         @Param("cinemaId") Long cinemaId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("status") Integer status,
                                         @Param("showtimeId") Long showtimeId);
}
