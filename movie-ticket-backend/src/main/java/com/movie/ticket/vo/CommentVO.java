package com.movie.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

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
