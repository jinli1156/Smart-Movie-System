package com.movie.ticket.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 站内比价结果，包含可选场次及各优惠组合的实付信息。 */
public record PriceComparisonVO(Long movieId, String movieTitle, String moviePoster, LocalDate date, int people,
        boolean personalized, boolean usePoints, boolean useCoupons, BigDecimal availablePoints,
        int comparedCount, int matchedCount, LocalDateTime queriedAt, List<Offer> offers, String notice) {
    public record Offer(Long showtimeId, Long cinemaId, String cinemaName, String hallName,
            LocalDateTime startTime, int availableSeats, BigDecimal basePrice, BigDecimal originalPrice,
            Map<String, Object> bestPlan, String reason) {}
}
