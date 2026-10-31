package com.movie.ticket.service;

import com.movie.ticket.dto.ShowtimeDTO;
import com.movie.ticket.entity.Cinema;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {
    @org.junit.jupiter.api.BeforeEach void loginManager() { com.movie.ticket.TestIdentity.admin(); }
    @org.junit.jupiter.api.AfterEach void clearManager() { com.movie.ticket.TestIdentity.clear(); }

    private static final long MOVIE_ID = 1L;
    private static final long CINEMA_ID = 2L;
    private static final long HALL_ID = 3L;
    private static final long SHOWTIME_ID = 4L;

    @Mock
    private ShowtimeMapper showtimeMapper;
    @Mock
    private MovieMapper movieMapper;
    @Mock
    private CinemaMapper cinemaMapper;
    @Mock
    private HallMapper hallMapper;
    @Mock
    private TicketOrderMapper orderMapper;

    @InjectMocks
    private ShowtimeService showtimeService;

    private ShowtimeDTO dto;

    @BeforeEach
    void setUp() {
        dto = new ShowtimeDTO();
        dto.setMovieId(MOVIE_ID);
        dto.setCinemaId(CINEMA_ID);
        dto.setHallId(HALL_ID);
        dto.setStartTime(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0));
        dto.setEndTime(dto.getStartTime().plusHours(2));
        dto.setBasePrice(new BigDecimal("49.90"));

        Movie movie = new Movie();
        movie.setId(MOVIE_ID);
        movie.setStatus(1);
        when(movieMapper.selectById(MOVIE_ID)).thenReturn(movie);

        Cinema cinema = new Cinema();
        cinema.setId(CINEMA_ID);
        lenient().when(cinemaMapper.selectById(CINEMA_ID)).thenReturn(cinema);

        Hall hall = new Hall();
        hall.setId(HALL_ID);
        hall.setCinemaId(CINEMA_ID);
        hall.setTotalRows(8);
        hall.setTotalCols(10);
        lenient().when(hallMapper.selectById(HALL_ID)).thenReturn(hall);
    }

    @Test
    void createRejectsConflictWhenExistingShowtimeContainsNewInterval() {
        when(showtimeMapper.countConflictingShowtimes(
                HALL_ID,
                dto.getStartTime().minusMinutes(15),
                dto.getEndTime().plusMinutes(15),
                null
        )).thenReturn(1L);

        assertThatThrownBy(() -> showtimeService.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该时间段影厅已有排片（需预留15分钟清场时间）");

        verify(showtimeMapper, never()).insert(any(Showtime.class));
    }

    @Test
    void createRejectsMovieThatIsNotNowShowing() {
        Movie upcomingMovie = new Movie();
        upcomingMovie.setId(MOVIE_ID);
        upcomingMovie.setStatus(2);
        when(movieMapper.selectById(MOVIE_ID)).thenReturn(upcomingMovie);

        assertThatThrownBy(() -> showtimeService.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅正在热映的影片可以排片");

        verify(showtimeMapper, never()).insert(any(Showtime.class));
    }

    @Test
    void updateRechecksConflictAndExcludesCurrentShowtime() {
        Showtime current = new Showtime();
        current.setId(SHOWTIME_ID);
        current.setCinemaId(CINEMA_ID);
        current.setStatus(0);
        when(showtimeMapper.selectById(SHOWTIME_ID)).thenReturn(current);
        when(showtimeMapper.countConflictingShowtimes(
                HALL_ID,
                dto.getStartTime().minusMinutes(15),
                dto.getEndTime().plusMinutes(15),
                SHOWTIME_ID
        )).thenReturn(1L);

        assertThatThrownBy(() -> showtimeService.update(SHOWTIME_ID, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该时间段影厅已有排片（需预留15分钟清场时间）");

        verify(showtimeMapper, never()).updateById(any(Showtime.class));
    }
}
