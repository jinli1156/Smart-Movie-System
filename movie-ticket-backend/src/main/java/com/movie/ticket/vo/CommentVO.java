package com.movie.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 观影评价展示数据，组合评价内容及用户展示信息。 */
@Data
public class CommentVO {

    private Long id;

    private Long movieId;

    private Long userId;

    private String userName;

    private String userAvatar;

    private Integer rating;

    private String content;

    private LocalDateTime createTime;
}
