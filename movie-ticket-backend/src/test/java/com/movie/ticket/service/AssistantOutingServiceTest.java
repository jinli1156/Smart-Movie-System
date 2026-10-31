package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantOutingServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final MovieMapper movies = mock(MovieMapper.class);
    private final CinemaMapper cinemas = mock(CinemaMapper.class);
    private final HallMapper halls = mock(HallMapper.class);
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserCouponMapper coupons = mock(UserCouponMapper.class);
    private final SeatRecommendationService seats = mock(SeatRecommendationService.class);
    private final AssistantBookingService booking = new AssistantBookingService(showtimes, movies, cinemas, halls, orders, users, coupons, seats);
    private final AssistantOutingService service = new AssistantOutingService(showtimes, booking, json);

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "outing"), UserCoupon.class);
        var user = new User(); user.setId(7L); user.setPoints(new BigDecimal("100")); when(users.selectById(7L)).thenReturn(user);
        var coupon = new UserCoupon(); coupon.setId(1L); coupon.setName("满90减20"); coupon.setAmount(new BigDecimal("20")); coupon.setMinSpend(new BigDecimal("90"));
        when(coupons.selectList(any())).thenReturn(List.of(coupon));
        var movie = new Movie(); movie.setId(1L); movie.setStatus(1); when(movies.selectById(1L)).thenReturn(movie);
        var cinema = new Cinema(); cinema.setStatus(1); when(cinemas.selectById(1L)).thenReturn(cinema);
        var hall = new Hall(); hall.setStatus(1); when(halls.selectById(1L)).thenReturn(hall);
        var candidates = List.of(candidate(1, "36"), candidate(2, "45"));
        when(showtimes.selectAssistantCandidates(any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap())).thenReturn(candidates);
        when(seats.recommend(anyLong(), eq(2), anyString(), eq(1))).thenReturn(List.of(SeatRecommendationVO.builder()
                .seats(List.of("5排5座", "5排6座")).reason("可用连座").build()));
    }
    private ShowtimeVO candidate(long id, String price) {
        var start = LocalDate.now().plusDays(1).atTime(20, 0);
        var entity = new Showtime(); entity.setId(id); entity.setMovieId(1L); entity.setCinemaId(1L); entity.setHallId(1L);
        entity.setStatus(0); entity.setStartTime(start); entity.setAvailableSeats(10); entity.setBasePrice(new BigDecimal(price));
        when(showtimes.selectById(id)).thenReturn(entity);
        var view = new ShowtimeVO(); view.setId(id); view.setMovieId(1L); view.setMovieTitle("测试喜剧"); view.setMovieGenre("喜剧");
        view.setCinemaName("测试影院"); view.setHallName("1号厅"); view.setStartTime(start); view.setEndTime(start.plusMinutes(100)); view.setBasePrice(new BigDecimal(price));
        return view;
    }
    @Test void couponThresholdCanMakeHigherBasePriceFitTotalBudgetWithoutPerTicketPrefilter() throws Exception {
        var result = json.valueToTree(service.plan(json.readTree("{\"people\":2,\"maxTotal\":70,\"limit\":1}"), 7L));
        assertEquals(2, result.at("/plans/0/showtimeId").asInt());
        assertEquals(new BigDecimal("69.00"), result.at("/plans/0/bestPlan/totalPrice").decimalValue().setScale(2));
        assertEquals(2, result.at("/plans/0/seats").size());
        verify(showtimes).selectAssistantCandidates(isNull(), isNull(), any(), any(), any(), isNull(), eq(false), anyMap());
        verifyNoInteractions(orders); verify(seats, never()).recommendSmart(anyLong(), any());
    }
    @Test void soldOutTogetherCandidateIsSkippedAndNextActuallyFeasiblePlanWins() throws Exception {
        when(seats.recommend(2L, 2, "CENTER", 1)).thenReturn(List.of());
        var result = json.valueToTree(service.plan(json.readTree("{\"people\":2,\"maxTotal\":80,\"limit\":1}"), 7L));
        assertEquals(1, result.at("/plans/0/showtimeId").asInt());
        assertEquals(1, result.get("noTogetherCount").asInt()); assertEquals(2, result.get("seatCheckedCount").asInt());
    }
    @Test void preservingPointsRecomputesBudgetAndGuestDoesNotReadPersonalAssets() throws Exception {
        var noPoints = json.valueToTree(service.plan(json.readTree("{\"people\":2,\"maxTotal\":69,\"usePoints\":false}"), 7L));
        assertTrue(noPoints.get("plans").isEmpty()); assertTrue(noPoints.get("diagnosis").asText().contains("总预算"));
        clearInvocations(users, coupons);
        var guest = json.valueToTree(service.plan(json.readTree("{\"people\":2,\"maxTotal\":70}"), null));
        assertTrue(guest.get("plans").isEmpty()); assertFalse(guest.get("personalized").asBoolean());
        verifyNoInteractions(users, coupons);
    }
    @Test void missingPeopleRequiresClarificationWithoutQueryingOrGuessing() throws Exception {
        var result = json.valueToTree(service.plan(json.readTree("{\"maxTotal\":80}"), null));
        assertEquals("people", result.get("needs").get(0).asText());
        verifyNoInteractions(users, coupons, seats); verify(showtimes, never()).selectAssistantCandidates(any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap());
    }
}
