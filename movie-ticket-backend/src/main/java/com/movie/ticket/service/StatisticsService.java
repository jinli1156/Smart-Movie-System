package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.entity.*;
import com.movie.ticket.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final ShowtimeMapper showtimeMapper;
    private final TicketOrderMapper orderMapper;
    private final UserMapper userMapper;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        long movieCount = movieMapper.selectCount(null);
        stats.put("movieCount", movieCount);

        long cinemaCount = cinemaMapper.selectCount(null);
        stats.put("cinemaCount", cinemaCount);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        Long todayOrderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<TicketOrder>()
                        .ge(TicketOrder::getCreateTime, todayStart)
                        .lt(TicketOrder::getCreateTime, todayEnd)
        );
        stats.put("todayOrderCount", todayOrderCount != null ? todayOrderCount : 0);

        List<TicketOrder> todayOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getStatus, 1)
                        .ge(TicketOrder::getPayTime, todayStart)
                        .lt(TicketOrder::getPayTime, todayEnd)
        );

        BigDecimal todayRevenue = todayOrders.stream()
                .map(TicketOrder::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todayRevenue", todayRevenue);

        long userCount = userMapper.selectCount(null);
        stats.put("userCount", userCount);

        return stats;
    }

    public List<Map<String, Object>> getBoxOfficeRanking(Integer limit) {
        List<Movie> movies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
        );

        List<TicketOrder> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getStatus, 1)
        );

        Set<Long> showtimeIds = paidOrders.stream()
                .map(TicketOrder::getShowtimeId)
                .collect(Collectors.toSet());

        Map<Long, Long> movieIdByShowtimeId = showtimeIds.isEmpty()
                ? Collections.emptyMap()
                : showtimeMapper.selectBatchIds(showtimeIds).stream()
                        .collect(Collectors.toMap(Showtime::getId, Showtime::getMovieId));

        Map<Long, BigDecimal> boxOfficeByMovieId = new HashMap<>();
        Map<Long, Long> ticketCountByMovieId = new HashMap<>();
        for (TicketOrder order : paidOrders) {
            Long movieId = movieIdByShowtimeId.get(order.getShowtimeId());
            if (movieId == null) {
                continue;
            }
            boxOfficeByMovieId.merge(movieId, order.getTotalPrice(), BigDecimal::add);
            ticketCountByMovieId.merge(movieId, order.getTicketCount().longValue(), Long::sum);
        }

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Movie movie : movies) {
            BigDecimal boxOffice = boxOfficeByMovieId.getOrDefault(movie.getId(), BigDecimal.ZERO);
            Long ticketCount = ticketCountByMovieId.getOrDefault(movie.getId(), 0L);

            Map<String, Object> item = new HashMap<>();
            item.put("movieId", movie.getId());
            item.put("movieTitle", movie.getTitle());
            item.put("moviePoster", movie.getPoster());
            item.put("boxOffice", boxOffice);
            item.put("ticketCount", ticketCount);

            ranking.add(item);
        }

        ranking.sort((a, b) -> {
            BigDecimal boxOfficeA = (BigDecimal) a.get("boxOffice");
            BigDecimal boxOfficeB = (BigDecimal) b.get("boxOffice");
            return boxOfficeB.compareTo(boxOfficeA);
        });

        return ranking.stream()
                .limit(limit != null ? limit : 10)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getShowtimeStats(Long showtimeId) {
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSeats", showtime.getTotalSeats());
        stats.put("availableSeats", showtime.getAvailableSeats());
        stats.put("soldSeats", showtime.getTotalSeats() - showtime.getAvailableSeats());

        double occupancyRate = 0;
        if (showtime.getTotalSeats() > 0) {
            occupancyRate = (double) (showtime.getTotalSeats() - showtime.getAvailableSeats())
                    / showtime.getTotalSeats() * 100;
        }
        stats.put("occupancyRate", String.format("%.2f", occupancyRate));

        List<TicketOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getShowtimeId, showtimeId)
                        .eq(TicketOrder::getStatus, 1)
        );

        BigDecimal revenue = orders.stream()
                .map(TicketOrder::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("revenue", revenue);

        return stats;
    }

    public List<Map<String, Object>> getRevenueByDate(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> result = new ArrayList<>();

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LocalDateTime dayStart = currentDate.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            List<TicketOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<TicketOrder>()
                            .eq(TicketOrder::getStatus, 1)
                            .ge(TicketOrder::getPayTime, dayStart)
                            .lt(TicketOrder::getPayTime, dayEnd)
            );

            BigDecimal revenue = orders.stream()
                    .map(TicketOrder::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Long orderCount = (long) orders.size();

            Map<String, Object> item = new HashMap<>();
            item.put("date", currentDate.toString());
            item.put("revenue", revenue);
            item.put("orderCount", orderCount);

            result.add(item);
            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    public Map<String, Object> getCinemaStats(Long cinemaId) {
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("cinemaId", cinema.getId());
        stats.put("cinemaName", cinema.getName());

        List<Showtime> showtimes = showtimeMapper.selectList(
                new LambdaQueryWrapper<Showtime>()
                        .eq(Showtime::getCinemaId, cinemaId)
        );

        int totalSeats = showtimes.stream()
                .mapToInt(Showtime::getTotalSeats)
                .sum();

        int soldSeats = showtimes.stream()
                .mapToInt(s -> s.getTotalSeats() - s.getAvailableSeats())
                .sum();

        double occupancyRate = 0;
        if (totalSeats > 0) {
            occupancyRate = (double) soldSeats / totalSeats * 100;
        }

        stats.put("totalSeats", totalSeats);
        stats.put("soldSeats", soldSeats);
        stats.put("occupancyRate", String.format("%.2f", occupancyRate));

        Set<Long> showtimeIds = showtimes.stream()
                .map(Showtime::getId)
                .collect(Collectors.toSet());

        List<TicketOrder> orders = showtimeIds.isEmpty()
                ? Collections.emptyList()
                : orderMapper.selectList(
                        new LambdaQueryWrapper<TicketOrder>()
                                .eq(TicketOrder::getStatus, 1)
                                .in(TicketOrder::getShowtimeId, showtimeIds)
                );

        BigDecimal revenue = orders.stream()
                .map(TicketOrder::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("revenue", revenue);
        stats.put("orderCount", orders.size());

        return stats;
    }
}
