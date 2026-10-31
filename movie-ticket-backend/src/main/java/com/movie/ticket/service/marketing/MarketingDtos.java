package com.movie.ticket.service.marketing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class MarketingDtos {
    private MarketingDtos() {}
    public record Campaign(
        @NotNull @Positive Long cinemaId,
        @NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=600) String brief,
        @NotBlank @Size(max=80) String audience,
        @NotEmpty @Size(max=10) List<@NotNull @Positive Long> movieIds,
        @NotEmpty @Size(max=2) List<@NotBlank String> styles,
        @NotEmpty @Size(max=3) List<@NotBlank String> ratios) {}
    public record Batch(@NotBlank @Pattern(regexp="ALL|COPY|IMAGE") String kind,
                        @NotBlank @Size(max=80) String requestKey,
                        @Min(value=1,message="海报生成张数至少为 1") Integer imageCount,
                        @Valid PosterContent posterContent) {
        public Batch(String kind,String requestKey) { this(kind,requestKey,null,null); }
        public Batch(String kind,String requestKey,Integer imageCount) { this(kind,requestKey,imageCount,null); }
    }
    public record PosterContent(@NotNull @Positive Long movieId,
        @NotBlank @Size(max=60) String title, @NotBlank @Size(max=300) String copy) {}
    public record Version(@NotNull @Positive Long movieId,
        @NotBlank @Size(max=60) String title, @NotBlank @Size(max=300) String copy,
        @NotBlank String assetId) {}
    public record Review(boolean approve, @Size(max=300) String reason) {}
    public record Publish(@NotBlank @Pattern(regexp="HOME|CINEMA") String slot,
        LocalDateTime startAt, @NotNull LocalDateTime endAt,
        @NotBlank @Size(max=80) String idempotencyKey) {}
    public record Retry(boolean acknowledgePossibleCharge) {}
}
