package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.movie.ticket.dto.PriceCompareRequest;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.ShowtimeVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PriceComparisonTest {
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final MovieMapper movies = mock(MovieMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserCouponMapper coupons = mock(UserCouponMapper.class);
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final SeatRecommendationService seats = mock(SeatRecommendationService.class);
    private final AssistantBookingService service = new AssistantBookingService(showtimes, movies,
            mock(CinemaMapper.class), mock(HallMapper.class), orders, users, coupons, seats);
    private final LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), UserCoupon.class);
        Movie movie = new Movie(); movie.setId(2L); movie.setStatus(1); movie.setTitle("测试电影");
        when(movies.selectById(2L)).thenReturn(movie);
        User user = new User(); user.setPoints(new BigDecimal("200"));
        when(users.selectById(7L)).thenReturn(user);
        UserCoupon coupon = new UserCoupon(); coupon.setId(9L); coupon.setName("满82减10");
        coupon.setMinSpend(new BigDecimal("82")); coupon.setAmount(BigDecimal.TEN);
        when(coupons.selectList(any())).thenReturn(List.of(coupon));
        when(showtimes.selectPriceComparisonViews(anyLong(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(showtime(1, "40"), showtime(2, "41")));
    }

    private ShowtimeVO showtime(long id, String price) {
        var s = new ShowtimeVO(); s.setId(id); s.setCinemaId(id); s.setCinemaName("影院" + id);
        s.setHallName("标准厅"); s.setBasePrice(new BigDecimal(price)); s.setAvailableSeats(8);
        s.setStartTime(tomorrow.atTime(19, 0)); return s;
    }
    private PriceCompareRequest request() {
        var r = new PriceCompareRequest(); r.setMovieId(2L); r.setPeople(2); r.setDate(tomorrow); return r;
    }

    @Test void couponThresholdChangesRankingAndBudgetAppliesAfterDiscounts() {
        var r = request(); r.setMaxTotal(new BigDecimal("71"));
        var result = service.compare(7L, r);
        assertEquals(2, result.comparedCount()); assertEquals(1, result.matchedCount());
        var best = result.offers().getFirst(); assertEquals(2L, best.showtimeId());
        assertEquals(new BigDecimal("82"), best.originalPrice());
        assertEquals(new BigDecimal("71.00"), best.bestPlan().get("totalPrice"));
        assertEquals(9L, best.bestPlan().get("couponId"));
        assertEquals(new BigDecimal("100.0"), best.bestPlan().get("pointsUsed"));
        verify(users).selectById(7L);
        verify(coupons).selectList(argThat(w -> {
            var q = (LambdaQueryWrapper<UserCoupon>) w;
            String sql = q.getSqlSegment();
            return sql.contains("user_id =") && sql.contains("status =") && sql.contains("valid_from <=")
                    && sql.contains("expire_time >=") && q.getParamNameValuePairs().containsValue(7L);
        }));
        verifyNoMoreInteractions(users, coupons); verifyNoInteractions(orders, seats);
    }

    @Test void anonymousComparisonNeverReadsPersonalAssets() {
        var result = service.compare(null, request());
        assertFalse(result.personalized());
        assertEquals(1L, result.offers().getFirst().showtimeId());
        assertEquals(new BigDecimal("80.00"), result.offers().getFirst().bestPlan().get("totalPrice"));
        verifyNoInteractions(users, coupons, orders, seats);
    }

    @Test void preservingAssetsChangesBestShowtime() {
        var r = request(); r.setUsePoints(false); r.setUseCoupons(false);
        var result = service.compare(7L, r);
        assertEquals(1L, result.offers().getFirst().showtimeId());
        assertEquals(new BigDecimal("80.00"), result.offers().getFirst().bestPlan().get("totalPrice"));
        verifyNoInteractions(coupons);
        r.setUseCoupons(true);
        result = service.compare(7L, r);
        assertEquals(new BigDecimal("72.00"), result.offers().getFirst().bestPlan().get("totalPrice"));
        assertEquals(new BigDecimal("0.0"), result.offers().getFirst().bestPlan().get("pointsUsed"));
    }

    @Test void selectedShowtimeIdentifiesMovieAndDate() {
        var selected = new Showtime(); selected.setMovieId(2L); selected.setStartTime(tomorrow.atTime(20, 0));
        when(showtimes.selectById(99L)).thenReturn(selected);
        var r = request(); r.setMovieId(null); r.setShowtimeId(99L); r.setDate(null);
        var result = service.compare(null, r);
        assertEquals(2L, result.movieId()); assertEquals(tomorrow, result.date());
        verify(showtimes).selectPriceComparisonViews(eq(2L), eq(tomorrow.atStartOfDay()),
                eq(tomorrow.plusDays(1).atStartOfDay()), any(), eq(2));
    }

    @Test void ranksAllCandidatesBeforeReturningTenAndReturnsEmptyHonestly() {
        var candidates = new ArrayList<ShowtimeVO>();
        for (int i = 1; i <= 11; i++) candidates.add(showtime(i, String.valueOf(60 - i)));
        when(showtimes.selectPriceComparisonViews(anyLong(), any(), any(), any(), anyInt())).thenReturn(candidates);
        var result = service.compare(null, request());
        assertEquals(11, result.comparedCount()); assertEquals(11, result.matchedCount());
        assertEquals(10, result.offers().size()); assertEquals(11L, result.offers().getFirst().showtimeId());
        when(showtimes.selectPriceComparisonViews(anyLong(), any(), any(), any(), anyInt())).thenReturn(List.of());
        assertTrue(service.compare(null, request()).offers().isEmpty());
    }

    @Test void invalidMovieDateAndAmbiguousSelectionAreRejected() {
        var r = request(); r.setShowtimeId(99L);
        assertThrows(BusinessException.class, () -> service.compare(null, r));
        r.setShowtimeId(null); r.setDate(tomorrow.minusDays(2));
        assertThrows(BusinessException.class, () -> service.compare(null, r));
        r.setDate(tomorrow.plusDays(90));
        assertThrows(BusinessException.class, () -> service.compare(null, r));
        r.setDate(tomorrow); r.setPeople(6);
        assertThrows(BusinessException.class, () -> service.compare(null, r));
        r.setPeople(2); when(movies.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.compare(null, r));
    }
}
