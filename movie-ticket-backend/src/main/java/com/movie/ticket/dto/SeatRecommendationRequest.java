package com.movie.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 智能选座请求，携带观影人数、结构化偏好及自然语言需求。 */
@Data
public class SeatRecommendationRequest {
    @Min(value = 1, message = "购票人数至少为 1 人")
    @Max(value = 5, message = "购票人数最多为 5 人")
    private int people = 2;

    private String preference = "CENTER";

    @Size(max = 300, message = "自然语言偏好不能超过 300 字")
    private String naturalLanguage;

    @Min(value = 1, message = "至少返回 1 组推荐")
    @Max(value = 5, message = "最多返回 5 组推荐")
    private int limit = 3;
}
