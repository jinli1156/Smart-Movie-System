package com.movie.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 整日排片草案请求，携带影院、日期、版本快照、清场间隔及完整场次集合。 */
@Data
public class SchedulingDraftRequest {
    @NotNull private Long cinemaId;
    @NotNull @FutureOrPresent private LocalDate targetDate;
    /** 编辑时读取的排片快照版本，检查和应用时用于发现并发变化。 */
    @NotBlank private String version;
    @NotNull @Min(10) @Max(60) private Integer cleaningMinutes = 20;
    @NotEmpty @Size(max = 300) @Valid private List<Item> items;

    @Data
    public static class Item {
        private Long sourceShowtimeId;
        @NotNull private Long movieId;
        @NotNull private Long hallId;
        @NotNull private LocalDateTime startTime;
        @NotNull private LocalDateTime endTime;
        @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
        private BigDecimal basePrice;
    }
}
