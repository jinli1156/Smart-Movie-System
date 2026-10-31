package com.movie.ticket.service;

import com.movie.ticket.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared by read-only quotes and the transactional payment flow. */
public final class TicketPriceCalculator {
    private TicketPriceCalculator() {}
    public static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");
    public static final BigDecimal MAX_POINTS_DISCOUNT = BigDecimal.ONE;

    /** 计算扣券后的积分使用上限，兼顾剩余应付金额与单笔抵扣上限。 */
    public static BigDecimal maxPoints(BigDecimal original, BigDecimal couponAmount) {
        return original.subtract(couponAmount.min(original)).max(BigDecimal.ZERO)
                .min(MAX_POINTS_DISCOUNT)
                .multiply(POINTS_PER_YUAN).setScale(1, RoundingMode.DOWN);
    }

    /** 校验积分余额和上限，统一返回原价、各项优惠及最终实付。 */
    public static Price calculate(BigDecimal original, BigDecimal couponAmount,
                                  BigDecimal requestedPoints, BigDecimal balance) {
        BigDecimal points = requestedPoints == null ? BigDecimal.ZERO : requestedPoints;
        if (points.signum() < 0) throw new BusinessException(400, "积分数量不能为负数");
        points = points.setScale(1, RoundingMode.DOWN);
        BigDecimal available = balance == null ? BigDecimal.ZERO : balance;
        if (points.compareTo(available) > 0) throw new BusinessException(400, "积分余额不足");
        BigDecimal maximum = maxPoints(original, couponAmount);
        if (points.compareTo(maximum) > 0) {
            throw new BusinessException(400, "本单最多可使用 " + maximum.stripTrailingZeros().toPlainString() + " 积分");
        }
        BigDecimal couponDiscount = couponAmount.min(original);
        BigDecimal pointsDiscount = points.divide(POINTS_PER_YUAN, 2, RoundingMode.HALF_UP);
        BigDecimal total = original.subtract(couponDiscount).subtract(pointsDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Price(original, couponDiscount, points, pointsDiscount,
                couponDiscount.add(pointsDiscount).setScale(2, RoundingMode.HALF_UP), total);
    }

    public record Price(BigDecimal originalPrice, BigDecimal couponDiscount, BigDecimal pointsUsed,
                        BigDecimal pointsDiscount, BigDecimal discountAmount, BigDecimal totalPrice) {}
}
