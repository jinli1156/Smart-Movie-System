package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户对影片的浏览与想看行为记录，供推荐特征与偏好画像使用。 */
@Data
@TableName("user_movie_behavior")
public class UserMovieBehavior {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long movieId;
    private String behaviorType;
    private Integer behaviorCount;
    private Integer active;
    private LocalDateTime lastOccurredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
