package com.movie.ticket.service;

import com.movie.ticket.dto.UserProfileUpdateDTO;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户资料服务：从登录上下文取得本人身份，更新允许编辑的个人资料字段。 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;

    /** 从登录上下文读取本人资料。 */
    public UserProfileVO getCurrentProfile() {
        return findProfile(UserContext.getUserId());
    }

    /** 只更新本人可编辑的资料字段，再返回最新资料。 */
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateCurrentProfile(UserProfileUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        User update = new User();
        update.setId(userId);
        update.setNickname(dto.getNickname().trim());
        update.setGender(dto.getGender());
        update.setBirthday(dto.getBirthday());
        update.setCity(trimToNull(dto.getCity()));
        update.setAddress(trimToNull(dto.getAddress()));
        update.setFavoriteGenres(dto.getFavoriteGenres());
        update.setSignature(trimToNull(dto.getSignature()));

        if (userMapper.updateById(update) != 1) {
            throw new BusinessException(404, "用户不存在");
        }
        return findProfile(userId);
    }

    private UserProfileVO findProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return UserProfileVO.from(user);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
