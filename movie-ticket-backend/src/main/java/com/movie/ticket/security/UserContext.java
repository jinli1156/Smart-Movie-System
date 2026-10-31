package com.movie.ticket.security;

import com.movie.ticket.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 Spring Security 上下文读取当前用户，集中执行角色判断与影院数据范围校验。 */
public class UserContext {

    /** 读取已认证用户 ID，未登录或身份无效时拒绝访问。 */
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) principal).getId();
        }

        throw new BusinessException(401, "无法获取用户信息");
    }

    /** 读取当前认证账号名称。 */
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "用户未登录");
        }

        return authentication.getName();
    }

    /** 读取当前认证用户的业务角色。 */
    public static String getRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) principal).getRole();
        }

        throw new BusinessException(401, "无法获取用户信息");
    }

    /** 判断当前用户是否为平台管理员。 */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /** 判断当前用户是否为影院管理员。 */
    public static boolean isCinemaAdmin() {
        return hasRole("CINEMA_ADMIN");
    }

    /** 判断当前用户是否拥有平台或影院管理角色。 */
    public static boolean isManager() {
        return isAdmin() || isCinemaAdmin();
    }

    private static boolean hasRole(String expected) {
        try {
            return expected.equals(getRole());
        } catch (Exception e) {
            return false;
        }
    }

    /** 为影院管理员绑定所属影院范围；未分配影院或指定其他影院时拒绝访问。 */
    public static Long scopeCinemaId(Long requested) {
        if (!isCinemaAdmin()) return requested;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long assigned = ((UserDetailsImpl) authentication.getPrincipal()).getCinemaId();
        if (assigned == null) throw new BusinessException(403, "尚未分配影院，请联系平台管理员");
        if (requested != null && !assigned.equals(requested)) {
            throw new BusinessException(403, "无权管理其他影院的数据");
        }
        return assigned;
    }

    /** 要求当前用户有管理权限，并校验目标影院在本人数据范围内。 */
    public static void requireCinema(Long cinemaId) {
        if (!isManager()) throw new BusinessException(403, "需要影院管理权限");
        if (cinemaId == null) throw new BusinessException(403, "无法确认数据所属影院");
        scopeCinemaId(cinemaId);
    }

    /** 要求当前用户拥有平台管理员角色。 */
    public static void requirePlatformAdmin() {
        if (!isAdmin()) throw new BusinessException(403, "需要平台管理员权限");
    }
}
