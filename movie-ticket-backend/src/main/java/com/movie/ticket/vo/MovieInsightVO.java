package com.movie.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 影片经营洞察，封装票房、场次、上座率与趋势指标。 */
@Data
@Builder
public class MovieInsightVO {
    private Long movieId;
    private BigDecimal todayBoxOffice;
    private long todayViewerCount;
    private double occupancyRate;
    private int trendPercent;
    private List<Long> trendPoints;
    private LocalDateTime generatedAt;
}
