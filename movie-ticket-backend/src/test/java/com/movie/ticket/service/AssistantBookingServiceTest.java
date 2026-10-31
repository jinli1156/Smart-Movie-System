package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.vo.SeatRecommendationResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantBookingServiceTest {
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final MovieMapper movies = mock(MovieMapper.class);
    private final CinemaMapper cinemas = mock(CinemaMapper.class);
    private final HallMapper halls = mock(HallMapper.class);
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserCouponMapper coupons = mock(UserCouponMapper.class);
    private final SeatRecommendationService seats = mock(SeatRecommendationService.class);
    private final AssistantBookingService service = new AssistantBookingService(showtimes, movies, cinemas, halls, orders, users, coupons, seats);

    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, TicketOrder.class);
        TableInfoHelper.initTableInfo(assistant, UserCoupon.class);
        var showtime = new Showtime(); showtime.setId(1L); showtime.setMovieId(2L); showtime.setCinemaId(3L); showtime.setHallId(4L);
        showtime.setStatus(0); showtime.setStartTime(LocalDateTime.now().plusDays(1)); showtime.setBasePrice(new BigDecimal("40")); showtime.setAvailableSeats(5);
        when(showtimes.selectById(1L)).thenReturn(showtime);
        var movie = new Movie(); movie.setStatus(1); when(movies.selectById(2L)).thenReturn(movie);
        var cinema = new Cinema(); cinema.setStatus(1); when(cinemas.selectById(3L)).thenReturn(cinema);
        var hall = new Hall(); hall.setStatus(1); when(halls.selectById(4L)).thenReturn(hall);
        var user = new User(); user.setId(7L); user.setPoints(new BigDecimal("500")); when(users.selectById(7L)).thenReturn(user);
        when(coupons.selectList(any())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> best(Map<String, Object> result) { return (Map<String, Object>) result.get("bestPlan"); }

    private UserCoupon coupon(long id, String amount) {
        var coupon = new UserCoupon(); coupon.setId(id); coupon.setName("测试优惠券"); coupon.setAmount(new BigDecimal(amount)); return coupon;
    }

    @Test void quotesUseRealPriceAndChooseLowestPayableWithoutMutation() {
        when(coupons.selectList(any())).thenReturn(List.of(coupon(11L, "6.99"), coupon(12L, "10")));
        var result = service.quote(7L, 1L, 2, null, true);
        assertEquals(new BigDecimal("80"), result.get("originalPrice"));
        assertEquals(12L, best(result).get("couponId"));
        // 100 points redeem 1 yuan; each order is capped at 1 yuan.
        assertEquals(new BigDecimal("69.00"), best(result).get("totalPrice"));
        assertEquals(new BigDecimal("100.0"), best(result).get("pointsUsed"));
        verify(users).selectById(7L);
        verify(coupons).selectList(argThat(wrapper -> {
            var query = (LambdaQueryWrapper<UserCoupon>) wrapper;
            String sql = query.getSqlSegment();
            return sql.contains("user_id =") && sql.contains("status =") && sql.contains("valid_from <=")
                    && sql.contains("expire_time >=") && sql.contains("min_spend <=")
                    && query.getParamNameValuePairs().containsValue(7L)
                    && query.getParamNameValuePairs().containsValue(new BigDecimal("80"));
        }));
        verifyNoMoreInteractions(coupons, users);
        verifyNoInteractions(orders, seats);
    }

    @Test void noCouponsAndOptingOutOfPointsKeepFullPrice() {
        var result = service.quote(7L, 1L, 2, null, false);
        assertEquals(0, result.get("eligibleCouponCount"));
        assertEquals(new BigDecimal("80.00"), best(result).get("totalPrice"));
        assertEquals(new BigDecimal("0.0"), best(result).get("pointsUsed"));
        assertFalse(best(result).containsKey("couponId"));
    }

    @Test void oversizedCouponNeverCreatesNegativePayableOrConsumesPoints() {
        when(coupons.selectList(any())).thenReturn(List.of(coupon(11L, "100")));
        var result = service.quote(7L, 1L, 2, null, true);
        assertEquals(new BigDecimal("0.00"), best(result).get("totalPrice"));
        assertEquals(new BigDecimal("0.0"), best(result).get("pointsUsed"));
    }

    @Test void unknownOrOtherUsersOrderNeverExposesPriceOrCoupons() {
        assertThrows(BusinessException.class, () -> service.quote(7L, null, null, "OTHER", true));
        verify(orders).selectOne(argThat(wrapper -> {
            var query = (LambdaQueryWrapper<TicketOrder>) wrapper;
            return query.getSqlSegment().contains("user_id =") && query.getParamNameValuePairs().containsValue(7L);
        }));
        verifyNoInteractions(users, coupons);
    }

    @Test void pendingOrderUsesStoredPriceAndExpiredOrdersAreRejected() {
        var order = new TicketOrder(); order.setUserId(7L); order.setOrderNo("OWN"); order.setStatus(0);
        order.setShowtimeId(1L);
        order.setCreateTime(LocalDateTime.now()); order.setTicketCount(2); order.setOriginalPrice(new BigDecimal("88.00"));
        when(orders.selectOne(any())).thenReturn(order);
        assertEquals(new BigDecimal("87.00"), best(service.quote(7L, null, null, "OWN", true)).get("totalPrice"));
        verify(showtimes).selectById(1L); // Presentation metadata is loaded, while the stored order price remains authoritative.
        clearInvocations(showtimes);
        order.setCreateTime(LocalDateTime.now().minusMinutes(11));
        assertThrows(BusinessException.class, () -> service.quote(7L, null, null, "OWN", true));
        verifyNoInteractions(showtimes);
    }

    @Test void unavailableShowtimeRejectsSeatRecommendationsAndQuotes() {
        var showtime = showtimes.selectById(1L); showtime.setStartTime(LocalDateTime.now().plusMinutes(10));
        assertThrows(BusinessException.class, () -> service.recommendSeats(1L, new SeatRecommendationRequest()));
        assertThrows(BusinessException.class, () -> service.quote(7L, 1L, 2, null, true));
        verifyNoInteractions(seats, coupons, users);
    }

    @Test void seatRecommendationReusesExistingEngineAndKeepsEmptyResultHonest() {
        var request = new SeatRecommendationRequest(); request.setNaturalLanguage("中间靠后");
        when(seats.recommendSmart(1L, request)).thenReturn(SeatRecommendationResultVO.builder()
                .recommendations(List.of()).preferenceSummary("靠后安静").analysisSource("algorithm").build());
        var result = service.recommendSeats(1L, request);
        assertEquals(List.of(), result.get("recommendations"));
        verify(seats).recommendSmart(1L, request);
        verifyNoInteractions(orders, coupons, users);
    }
}
