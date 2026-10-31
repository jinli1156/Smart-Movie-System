package com.movie.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/** 用户实体，保存账号凭据、角色、所属影院、个人资料及积分余额。 */
@Data
@TableName(value = "user", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String phone;

    private String email;

    private String avatar;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate birthday;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;

    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<String> favoriteGenres;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String signature;

    private String role;

    /** 由平台管理员分配；普通用户与平台管理员无需绑定影院。 */
    private Long cinemaId;

    private Integer status;

    /** 可用积分，允许 0.5 的精度（消费 1 元获得 0.5 积分）。 */
    private BigDecimal points;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
