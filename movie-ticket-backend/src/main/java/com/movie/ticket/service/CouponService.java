package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.movie.ticket.entity.UserCoupon;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.UserCouponMapper;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final UserCouponMapper couponMapper;

    @Transactional(rollbackFor = Exception.class)
    public void grantNewUserCoupons(Long userId) {
        grant(userId, "新人专享 8 元券", new BigDecimal("8.00"));
        grant(userId, "新人专享 5 元券", new BigDecimal("5.00"));
    }

    private void grant(Long userId, String name, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setName(name);
        coupon.setAmount(amount);
        coupon.setMinSpend(BigDecimal.ZERO);
        coupon.setStatus(0);
        coupon.setValidFrom(now);
        coupon.setExpireTime(now.plusDays(30));
        couponMapper.insert(coupon);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CouponVO> getMyCoupons() {
        Long userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByAsc(UserCoupon::getStatus)
                        .orderByAsc(UserCoupon::getExpireTime)
                        .orderByDesc(UserCoupon::getAmount)
        );

        coupons.stream()
                .filter(coupon -> coupon.getStatus() == 0 && coupon.getExpireTime().isBefore(now))
                .forEach(coupon -> {
                    coupon.setStatus(2);
                    couponMapper.updateById(coupon);
                });

        return coupons.stream().map(this::toVO).toList();
    }

    public UserCoupon validateAvailableCoupon(Long couponId, Long userId, BigDecimal orderAmount) {
        UserCoupon coupon = couponMapper.selectById(couponId);
        LocalDateTime now = LocalDateTime.now();
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getStatus() != 0) {
            throw new BusinessException("优惠券已使用或已过期");
        }
        if (coupon.getValidFrom().isAfter(now) || coupon.getExpireTime().isBefore(now)) {
            throw new BusinessException("优惠券不在有效期内");
        }
        if (orderAmount.compareTo(coupon.getMinSpend()) < 0) {
            throw new BusinessException("订单金额未达到优惠券使用门槛");
        }
        return coupon;
    }

    public void markUsed(UserCoupon coupon, Long orderId) {
        int updated = couponMapper.update(null,
                new LambdaUpdateWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, coupon.getId())
                        .eq(UserCoupon::getStatus, 0)
                        .set(UserCoupon::getStatus, 1)
                        .set(UserCoupon::getOrderId, orderId)
                        .set(UserCoupon::getUseTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException("优惠券已被使用，请刷新后重试");
        }
    }

    public void restoreCoupon(Long couponId) {
        if (couponId == null) return;
        UserCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() != 1) return;
        if (coupon.getExpireTime().isBefore(LocalDateTime.now())) {
            coupon.setStatus(2);
        } else {
            coupon.setStatus(0);
        }
        coupon.setOrderId(null);
        coupon.setUseTime(null);
        couponMapper.updateById(coupon);
    }

    private CouponVO toVO(UserCoupon coupon) {
        CouponVO vo = new CouponVO();
        vo.setId(coupon.getId());
        vo.setName(coupon.getName());
        vo.setAmount(coupon.getAmount());
        vo.setMinSpend(coupon.getMinSpend());
        vo.setStatus(coupon.getStatus());
        vo.setStatusText(switch (coupon.getStatus()) {
            case 0 -> "可使用";
            case 1 -> "已使用";
            case 2 -> "已过期";
            default -> "未知";
        });
        vo.setAvailable(coupon.getStatus() == 0);
        vo.setValidFrom(coupon.getValidFrom());
        vo.setExpireTime(coupon.getExpireTime());
        vo.setUseTime(coupon.getUseTime());
        return vo;
    }
}
