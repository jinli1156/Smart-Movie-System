package com.movie.ticket.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 站内比价条件，限定影片或场次、日期、人数及优惠使用偏好。 */
@Data
public class PriceCompareRequest {
    @Positive private Long movieId;
    @Positive private Long showtimeId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate date;
    @Min(1) @Max(5) private int people = 1;
    private boolean usePoints = true;
    private boolean useCoupons = true;
    @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 2)
    private BigDecimal maxTotal;
}
