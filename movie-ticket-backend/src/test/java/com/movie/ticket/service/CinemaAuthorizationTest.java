package com.movie.ticket.service;

import com.movie.ticket.TestIdentity;
import com.movie.ticket.dto.*;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.vo.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CinemaAuthorizationTest {
    private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
    private final ShowtimeMapper showtimes = mock(ShowtimeMapper.class);
    private final HallMapper halls = mock(HallMapper.class);
    private final CinemaMapper cinemas = mock(CinemaMapper.class);
    private final MovieMapper movies = mock(MovieMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final OrderService service = new OrderService(orders, showtimes, movies, cinemas, halls, users,
            mock(StringRedisTemplate.class), mock(CouponService.class));
    @AfterEach void cleanup() { TestIdentity.clear(); }

    private void denied(Runnable action) {
        assertEquals(403, assertThrows(BusinessException.class, action::run).getCode());
    }
    private void screening(Long cinema) {
        Showtime s = new Showtime(); s.setId(100L); s.setCinemaId(cinema);
        when(showtimes.selectById(100L)).thenReturn(s);
    }
    private TicketOrder ticket() {
        TicketOrder order = new TicketOrder(); order.setId(9L); order.setUserId(7L); order.setShowtimeId(100L);
        order.setStatus(1); order.setIsPicked(0);
        when(orders.selectOne(any())).thenReturn(order);
        return order;
    }
    @Test void managementListAlwaysUsesAssignedCinemaAndUnassignedFailsClosed() {
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L);
        service.getPage(1, 10, null, null);
        verify(orders).selectCinemaOrderPage(any(), eq(10L), isNull(), isNull());
        verify(orders, never()).selectOrderPage(any(), any(), any(), any(), anyBoolean());
        clearInvocations(orders);
        TestIdentity.login(2L, "CINEMA_ADMIN", null);
        denied(() -> service.getPage(1, 10, null, null));
        verifyNoInteractions(orders);
    }
    @Test void orderDetailsAllowOnlyOwnerAssignedCinemaOrPlatform() {
        OrderVO order = new OrderVO(); order.setUserId(7L); order.setShowtimeId(100L);
        when(orders.selectOrderViewByOrderNo("T")).thenReturn(order);
        screening(10L);
        TestIdentity.login(8L, "USER", null); denied(() -> service.getByOrderNo("T"));
        TestIdentity.login(7L, "USER", null); assertSame(order, service.getByOrderNo("T"));
        TestIdentity.login(2L, "CINEMA_ADMIN", 20L); denied(() -> service.getByOrderNo("T"));
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L); assertSame(order, service.getByOrderNo("T"));
        TestIdentity.admin(); assertSame(order, service.getByOrderNo("T"));
    }
    @Test void knowingTicketCodeDoesNotAuthorizePickupAndForeignManagersCannotMutate() {
        ticket(); screening(10L);
        TestIdentity.login(8L, "USER", null); denied(() -> service.pickTicket("known-code"));
        TestIdentity.login(2L, "CINEMA_ADMIN", 20L);
        denied(() -> service.pickTicket("known-code"));
        denied(() -> service.cancelOrder("T"));
        denied(() -> service.refundOrder("T"));
        verify(orders, never()).markPickedIfPaid(any(), any());
        verify(orders, never()).cancelIfPending(any(), any());
        verify(orders, never()).refundIfPaidAndNotPicked(any(), any(), any());
    }
    @Test void hallCannotBeCreatedUpdatedOrMovedAcrossCinemaBoundary() {
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L);
        HallService service = new HallService(halls, cinemas);
        HallDTO dto = new HallDTO(); dto.setCinemaId(20L);
        denied(() -> service.create(dto)); denied(() -> service.update(1L, dto));
        Hall foreign = new Hall(); foreign.setId(1L); foreign.setCinemaId(20L);
        when(halls.selectById(1L)).thenReturn(foreign);
        denied(() -> service.updateStatus(1L, 0)); denied(() -> service.delete(1L));
        verify(halls, never()).insert(any()); verify(halls, never()).updateById(any()); verify(halls, never()).deleteById(anyLong());
    }
    @Test void showtimeChecksSourceAndDestinationBeforeLockingOrWriting() {
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L);
        ShowtimeService service = new ShowtimeService(showtimes, movies, cinemas, halls, orders);
        ShowtimeDTO dto = new ShowtimeDTO(); dto.setCinemaId(20L);
        denied(() -> service.create(dto)); denied(() -> service.update(100L, dto));
        dto.setCinemaId(10L); screening(20L);
        denied(() -> service.update(100L, dto)); denied(() -> service.updateStatus(100L, 2)); denied(() -> service.delete(100L));
        verify(halls, never()).lockCinemaHalls(any()); verify(showtimes, never()).updateById(any());
    }
    @Test void schedulingCannotReadOrApplyForeignCinemaEvenWithACorrectDraft() {
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L);
        var intelligence = new SchedulingIntelligenceService(movies, showtimes, orders, halls, mock(SchedulingAiService.class));
        var workspace = new SchedulingWorkspaceService(showtimes, halls, movies, cinemas, orders, intelligence);
        var draft = new SchedulingDraftRequest(); draft.setCinemaId(20L);
        denied(() -> workspace.workspace(20L, java.time.LocalDate.now()));
        denied(() -> workspace.check(draft)); denied(() -> workspace.apply(draft));
        denied(() -> intelligence.getInsights(20L));
        var request = new SchedulingOptimizationRequest(); request.setCinemaId(20L);
        denied(() -> intelligence.optimize(request));
        verifyNoInteractions(movies, showtimes, orders, halls, cinemas);
    }
    @Test void privateStatisticsAreScopedAndCannotReadForeignShowtime() {
        TestIdentity.login(2L, "CINEMA_ADMIN", 10L);
        var statistics = new StatisticsService(movies, cinemas, showtimes, orders, users);
        statistics.getDashboardStats(); statistics.getBoxOfficeRanking(10);
        when(orders.selectCinemaRevenue(any(), any(), any())).thenReturn(List.of());
        statistics.getRevenueByDate(java.time.LocalDate.now(), java.time.LocalDate.now());
        verify(orders).selectCinemaDashboard(eq(10L), any(), any());
        verify(orders).selectCinemaBoxOffice(10L, 10);
        verify(orders).selectCinemaRevenue(eq(10L), any(), any());
        verifyNoInteractions(users);
        screening(20L); denied(() -> statistics.getShowtimeStats(100L));
        verify(orders, never()).sumPaidRevenueByShowtime(any());
    }
}
