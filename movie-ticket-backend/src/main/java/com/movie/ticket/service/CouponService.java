package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.movie.ticket.entity.UserCoupon;
import com.movie.ticket.entity.CouponCode;
import com.movie.ticket.entity.CouponCodeRedemption;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CouponCodeMapper;
import com.movie.ticket.mapper.CouponCodeRedemptionMapper;
import com.movie.ticket.mapper.UserCouponMapper;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 用户优惠券领取、可用性校验、支付占用及退款恢复，统一处理有效期与使用门槛。 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final UserCouponMapper couponMapper;
    private final CouponCodeMapper couponCodeMapper;
    private final CouponCodeRedemptionMapper redemptionMapper;

    /** 为新注册账号发放初始优惠券。 */
    @Transactional(rollbackFor = Exception.class)
    public void grantNewUserCoupons(Long userId) {
        grant(userId,
                "新人专享满 80 减 6.99 元券",
                new BigDecimal("6.99"),
                new BigDecimal("80.00"),
                7);
    }

    private void grant(Long userId, String name, BigDecimal amount,
                       BigDecimal minSpend, int validDays) {
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setName(name);
        coupon.setAmount(amount);
        coupon.setMinSpend(minSpend);
        coupon.setStatus(0);
        coupon.setValidFrom(now);
        coupon.setExpireTime(now.plusDays(validDays));
        couponMapper.insert(coupon);
    }

    /** 校验兑换码的有效期、发放限额和重复领取限制，为本人发券。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponVO redeem(String rawCode) {
        Long userId = UserContext.getUserId();
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        // 行锁串行处理同一兑换码，避免并发兑换突破总次数限制。
        CouponCode couponCode = couponCodeMapper.selectByCodeForUpdate(code);
        if (couponCode == null) {
            throw new BusinessException(404, "兑换码不存在");
        }
        if (!Integer.valueOf(1).equals(couponCode.getStatus())) {
            throw new BusinessException(409, "兑换码已停用");
        }
        if (couponCode.getValidFrom().isAfter(now)) {
            throw new BusinessException(400, "兑换码尚未生效");
        }
        if (couponCode.getExpireTime().isBefore(now)) {
            throw new BusinessException(400, "兑换码已过期");
        }
        if (couponCode.getRedeemedCount() >= couponCode.getMaxRedemptions()) {
            throw new BusinessException(409, "兑换码已达到领取上限");
        }

        long redeemed = redemptionMapper.selectCount(
                new LambdaQueryWrapper<CouponCodeRedemption>()
                        .eq(CouponCodeRedemption::getCouponCodeId, couponCode.getId())
                        .eq(CouponCodeRedemption::getUserId, userId));
        if (redeemed > 0) {
            throw new BusinessException(409, "该兑换码每个账号只能兑换一次");
        }

        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setName(couponCode.getName());
        coupon.setAmount(couponCode.getAmount());
        coupon.setMinSpend(couponCode.getMinSpend());
        coupon.setStatus(0);
        coupon.setValidFrom(now);
        coupon.setExpireTime(now.plusDays(couponCode.getValidDays()));
        if (couponMapper.insert(coupon) != 1) {
            throw new BusinessException(409, "优惠券发放失败，请稍后重试");
        }

        CouponCodeRedemption redemption = new CouponCodeRedemption();
        redemption.setCouponCodeId(couponCode.getId());
        redemption.setUserId(userId);
        redemption.setUserCouponId(coupon.getId());
        redemption.setRedeemTime(now);
        if (redemptionMapper.insert(redemption) != 1
                || couponCodeMapper.incrementRedeemedCount(couponCode.getId()) != 1) {
            throw new BusinessException(409, "兑换码状态已变化，请重试");
        }

        return toVO(coupon);
    }

    /** 查询本人优惠券，返回使用状态与有效期。 */
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

    /** 校验优惠券归属、状态、有效期及订单金额门槛。 */
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

    /** 将优惠券关联至已支付订单并标记已使用。 */
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

    /** 退款时恢复该订单使用的优惠券，并重新判断是否已过期。 */
    public void restoreCoupon(Long couponId, Long orderId) {
        if (couponId == null) {
            return;
        }
        int updated = couponMapper.restoreIfUsedByOrder(couponId, orderId, LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException(409, "优惠券状态已变化，请刷新后重试");
        }
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
