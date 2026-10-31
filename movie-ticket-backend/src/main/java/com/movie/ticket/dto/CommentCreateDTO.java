package com.movie.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增观影评价请求，限定影片、评分与评价内容。 */
@Data
public class CommentCreateDTO {

    @NotNull(message = "电影ID不能为空")
    private Long movieId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低为1星")
    @Max(value = 5, message = "评分最高为5星")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过500字")
    private String content;
}
