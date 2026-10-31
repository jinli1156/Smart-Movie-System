package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.CouponCodeSaveDTO;
import com.movie.ticket.entity.CouponCode;
import com.movie.ticket.entity.CouponCodeRedemption;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CouponCodeMapper;
import com.movie.ticket.mapper.CouponCodeRedemptionMapper;
import com.movie.ticket.vo.CouponRedemptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** 维护优惠券兑换码及领取记录；通过服务端校验约束发放数量、启用状态和有效时间。 */
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponCodeMapper couponCodeMapper;
    private final CouponCodeRedemptionMapper redemptionMapper;

    /** 按筛选条件分页查询CouponAdmin，供列表页展示。 */
    public IPage<CouponCode> getPage(Integer page, Integer size, String keyword, Integer status) {
        LambdaQueryWrapper<CouponCode> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(CouponCode::getCode, value)
                    .or().like(CouponCode::getName, value));
        }
        if (status != null) {
            wrapper.eq(CouponCode::getStatus, status);
        }
        wrapper.orderByDesc(CouponCode::getCreateTime);
        return couponCodeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 分页查询兑换码领取记录，便于核对发放情况。 */
    public IPage<CouponRedemptionVO> getRedemptions(Integer page, Integer size,
                                                     Long couponCodeId, String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return redemptionMapper.selectAdminPage(
                new Page<>(page, size), couponCodeId, normalizedKeyword);
    }

    /** 新增CouponAdmin，由业务层执行参数及关联数据校验。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponCode create(CouponCodeSaveDTO request) {
        String code = normalizeCode(request.getCode());
        ensureCodeUnique(code, null);
        validateRequest(request, 0);

        CouponCode couponCode = new CouponCode();
        apply(request, couponCode, code);
        couponCode.setRedeemedCount(0);
        couponCodeMapper.insert(couponCode);
        return couponCode;
    }

    /** 更新CouponAdmin，由业务层校验当前操作权限与业务条件。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponCode update(Long id, CouponCodeSaveDTO request) {
        CouponCode existing = requireCouponCode(id);
        String code = normalizeCode(request.getCode());
        ensureCodeUnique(code, id);
        validateRequest(request, existing.getRedeemedCount());

        apply(request, existing, code);
        couponCodeMapper.updateById(existing);
        return couponCodeMapper.selectById(id);
    }

    /** 修改CouponAdmin状态，具体可用状态和操作限制由业务层校验。 */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态值无效");
        }
        CouponCode existing = requireCouponCode(id);
        existing.setStatus(status);
        couponCodeMapper.updateById(existing);
    }

    /** 删除CouponAdmin，由业务层检查权限及关联数据限制。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CouponCode existing = requireCouponCode(id);
        long redemptionCount = redemptionMapper.selectCount(
                new LambdaQueryWrapper<CouponCodeRedemption>()
                        .eq(CouponCodeRedemption::getCouponCodeId, id));
        if (redemptionCount > 0 || existing.getRedeemedCount() > 0) {
            throw new BusinessException(409, "该兑换码已有兑换记录，请停用后保留历史数据");
        }
        couponCodeMapper.deleteById(id);
    }

    private CouponCode requireCouponCode(Long id) {
        CouponCode couponCode = couponCodeMapper.selectById(id);
        if (couponCode == null) {
            throw new BusinessException(404, "兑换码不存在");
        }
        return couponCode;
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<CouponCode> wrapper = new LambdaQueryWrapper<CouponCode>()
                .eq(CouponCode::getCode, code);
        if (excludeId != null) {
            wrapper.ne(CouponCode::getId, excludeId);
        }
        if (couponCodeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "兑换码已存在");
        }
    }

    private void validateRequest(CouponCodeSaveDTO request, int redeemedCount) {
        if (!request.getValidFrom().isBefore(request.getExpireTime())) {
            throw new BusinessException(400, "截止时间必须晚于生效时间");
        }
        if (request.getMaxRedemptions() < redeemedCount) {
            throw new BusinessException(400, "最大发放数量不能小于已兑换数量");
        }
        if (request.getMinSpend().signum() > 0
                && request.getAmount().compareTo(request.getMinSpend()) > 0) {
            throw new BusinessException(400, "优惠金额不能大于使用门槛");
        }
        if (request.getExpireTime().isBefore(LocalDateTime.now())
                && Integer.valueOf(1).equals(request.getStatus())) {
            throw new BusinessException(400, "已过期的兑换码不能设为启用");
        }
    }

    private void apply(CouponCodeSaveDTO request, CouponCode target, String code) {
        target.setCode(code);
        target.setName(request.getName().trim());
        target.setAmount(request.getAmount());
        target.setMinSpend(request.getMinSpend());
        target.setValidDays(request.getValidDays());
        target.setMaxRedemptions(request.getMaxRedemptions());
        target.setValidFrom(request.getValidFrom());
        target.setExpireTime(request.getExpireTime());
        target.setStatus(request.getStatus());
    }

    private String normalizeCode(String rawCode) {
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }
}
