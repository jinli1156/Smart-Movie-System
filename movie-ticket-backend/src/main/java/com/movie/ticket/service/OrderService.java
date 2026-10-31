package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.dto.OrderCreateDTO;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 购票业务：创建订单、支付、取消、退票与取票；通过数据库事务和 Redis 座位锁协调库存。 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final CouponService couponService;

    private static final String SEAT_LOCK_PREFIX = "seat:lock:";
    private static final String ORDER_NO_PREFIX = "ORDER";
    /** Redis 锁保留 15 分钟用于并发保护；订单支付时限为 10 分钟，由订单回收任务处理。 */
    private static final int LOCK_EXPIRE_MINUTES = 15;
    private static final BigDecimal POINTS_EARNED_PER_YUAN = new BigDecimal("0.5");
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(0, "待支付");
        STATUS_MAP.put(1, "已支付");
        STATUS_MAP.put(2, "已取消");
        STATUS_MAP.put(3, "已退款");
    }

    /** 校验场次与座位，获取座位锁并扣减余票，创建待支付订单。 */
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO dto) {
        Long userId = UserContext.getUserId();

        Showtime showtime = showtimeMapper.selectById(dto.getShowtimeId());
        if (showtime == null) {
            throw new BusinessException(404, "场次不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        if (showtime.getStartTime().isBefore(now)) {
            throw new BusinessException("该场次已开始，无法购票");
        }

        // 开场前15分钟不能购票
        if (showtime.getStartTime().minusMinutes(15).isBefore(now)) {
            throw new BusinessException("距离开场不足15分钟，无法购票");
        }

        if (showtime.getStatus() != 0) {
            throw new BusinessException("该场次已停售");
        }

        List<String> seats = dto.getSeats();
        if (seats.isEmpty()) {
            throw new BusinessException("请选择座位");
        }

        if (seats.stream().anyMatch(seat -> seat == null || seat.isBlank())) {
            throw new BusinessException("座位信息不能为空");
        }

        if (new HashSet<>(seats).size() != seats.size()) {
            throw new BusinessException("不能重复选择同一座位");
        }

        List<String> acquiredSeats = new ArrayList<>();
        String orderNo = generateOrderNo();
        try {
            for (String seat : seats) {
                // Redis 锁会过期，数据库中的待支付/已支付订单才是最终已售事实。
                if (orderMapper.countActiveOrdersBySeat(dto.getShowtimeId(), seat) > 0
                        || !lockSeat(dto.getShowtimeId(), seat, orderNo)) {
                    throw new BusinessException(409, "座位 " + seat + " 已被占用");
                }
                acquiredSeats.add(seat);
            }

            // An enclosing assistant confirmation may still roll back after createOrder returns.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                var lockedSeats = List.copyOf(acquiredSeats);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) unlockSeatsQuietly(dto.getShowtimeId(), lockedSeats, orderNo);
                    }
                });
            }

            // 条件更新由 MySQL 原子执行，多个不同座位并发下单时也不会把余票扣成负数。
            if (showtimeMapper.reserveAvailableSeats(dto.getShowtimeId(), seats.size()) != 1) {
                throw new BusinessException(409, "余票不足，请重新选择座位");
            }

            BigDecimal totalPrice = showtime.getBasePrice().multiply(new BigDecimal(seats.size()));

            TicketOrder order = new TicketOrder();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShowtimeId(dto.getShowtimeId());
            order.setSeats(String.join(",", seats));
            order.setTicketCount(seats.size());
            order.setTotalPrice(totalPrice);
            order.setOriginalPrice(totalPrice);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setStatus(0);
            order.setRemark(dto.getRemark());

            orderMapper.insert(order);

            return orderNo;
        } catch (RuntimeException exception) {
            // Redis 不参与数据库事务，失败路径必须显式释放本次请求已经拿到的锁。
            unlockSeatsQuietly(dto.getShowtimeId(), acquiredSeats, orderNo);
            throw exception;
        }
    }

    /** 使用订单号作为锁归属标识，原子占用座位并设置过期时间。 */
    private boolean lockSeat(Long showtimeId, String seat, String ownerToken) {
        String key = SEAT_LOCK_PREFIX + showtimeId + ":" + seat;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, ownerToken, LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(success);
    }

    private String generateOrderNo() {
        return ORDER_NO_PREFIX + System.currentTimeMillis() +
               String.format("%04d", new Random().nextInt(10000));
    }

    /** 仅分页查询当前登录用户的订单。 */
    public IPage<OrderVO> getMyOrders(Integer page, Integer size, Integer status) {
        Long userId = UserContext.getUserId();
        Page<OrderVO> pageParam = new Page<>(page, size);
        return orderMapper.selectOrderPage(pageParam, userId, null, status, false);
    }

    /** 查询本人已完成观影的购票记录。 */
    public IPage<OrderVO> getViewingHistory(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        Page<OrderVO> pageParam = new Page<>(page, size);
        return orderMapper.selectOrderPage(pageParam, userId, null, null, true);
    }

    /** 按筛选条件分页查询订单，供列表页展示。 */
    public IPage<OrderVO> getPage(Integer page, Integer size, String orderNo, Integer status) {
        if (!UserContext.isManager()) throw new BusinessException(403, "需要订单管理权限");
        Page<OrderVO> pageParam = new Page<>(page, size);
        if (UserContext.isCinemaAdmin()) {
            return orderMapper.selectCinemaOrderPage(pageParam, UserContext.scopeCinemaId(null), orderNo, status);
        }
        return orderMapper.selectOrderPage(pageParam, null, orderNo, status, false);
    }

    /** 按订单号读取详情，并校验本人或管理员的数据访问范围。 */
    public OrderVO getByOrderNo(String orderNo) {
        OrderVO order = orderMapper.selectOrderViewByOrderNo(orderNo);

        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        requireOrderAccess(order.getUserId(), order.getShowtimeId());

        return order;
    }

    /** 校验订单状态、优惠券和积分，完成支付并记录取票凭证及积分变化。 */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long couponId) {
        payOrder(orderNo, couponId, BigDecimal.ZERO);
    }

    /** 校验订单状态、优惠券和积分，完成支付并记录取票凭证及积分变化。 */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long couponId, BigDecimal requestedPoints) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        Long userId = UserContext.getUserId();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该订单");
        }

        if (!Objects.equals(order.getStatus(), 0)) {
            throw new BusinessException(409, "订单状态已变化，请刷新后重试");
        }

        BigDecimal originalPrice = order.getOriginalPrice() == null
                ? order.getTotalPrice() : order.getOriginalPrice();
        UserCoupon coupon = null;

        if (couponId != null) {
            coupon = couponService.validateAvailableCoupon(couponId, userId, originalPrice);
        }

        User user = userMapper.selectForUpdate(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        BigDecimal availablePoints = user.getPoints() == null
                ? BigDecimal.ZERO : user.getPoints();
        var price = TicketPriceCalculator.calculate(originalPrice,
                coupon == null ? BigDecimal.ZERO : coupon.getAmount(), requestedPoints, availablePoints);
        BigDecimal pointsToUse = price.pointsUsed();
        BigDecimal totalPrice = price.totalPrice();
        BigDecimal discountAmount = price.discountAmount();
        BigDecimal pointsEarned = totalPrice.multiply(POINTS_EARNED_PER_YUAN)
                .setScale(1, RoundingMode.HALF_UP);

        // 生成取票码：订单号后4位 + 4位随机数
        String ticketCode = generateTicketCode(orderNo);
        LocalDateTime payTime = LocalDateTime.now();

        int updated = orderMapper.markPaidIfPending(
                order.getId(), userId, originalPrice, discountAmount, pointsToUse, pointsEarned, totalPrice,
                coupon == null ? null : coupon.getId(), payTime, ticketCode);
        if (updated != 1) {
            throw new BusinessException(409, "订单状态已变化，支付未完成");
        }

        // 与订单状态更新处于同一数据库事务；占券失败会回滚已支付状态。
        if (coupon != null) {
            couponService.markUsed(coupon, order.getId());
        }

        // 先扣除本单使用积分，再按实际支付金额发放积分；两步与订单支付处于同一事务。
        // 用户资料允许显式清空，不能用只填积分的 User 调用 updateById。
        if (userMapper.updatePointsOnly(userId, availablePoints.subtract(pointsToUse).add(pointsEarned)) != 1) {
            throw new BusinessException(409, "积分余额更新失败，请稍后重试");
        }
    }

    /** 本人可访问自己的订单，影院管理员限所属影院，平台管理员可管理全部订单。 */
    private void requireOrderAccess(Long ownerId, Long showtimeId) {
        if (UserContext.isAdmin()) return;
        if (UserContext.isCinemaAdmin()) {
            Showtime showtime = showtimeMapper.selectById(showtimeId);
            UserContext.requireCinema(showtime == null ? null : showtime.getCinemaId());
            return;
        }
        if (!Objects.equals(ownerId, UserContext.getUserId())) {
            throw new BusinessException(403, "无权访问或操作该订单");
        }
    }

    private String generateTicketCode(String orderNo) {
        String orderSuffix = orderNo.substring(orderNo.length() - 4);
        int random = 1000 + new Random().nextInt(9000);
        return orderSuffix + random;
    }

    /** 通过取票码核验已支付订单，并记录取票状态。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pickTicket(String ticketCode) {
        // 通过取票码查询订单
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getTicketCode, ticketCode)
        );

        if (order == null) {
            throw new BusinessException(404, "取票码无效");
        }

        requireOrderAccess(order.getUserId(), order.getShowtimeId());

        if (!Objects.equals(order.getStatus(), 1)) {
            throw new BusinessException(409, "订单未支付、已取消或已退款");
        }

        if (Objects.equals(order.getIsPicked(), 1)) {
            throw new BusinessException(409, "该订单已取票");
        }

        LocalDateTime pickTime = LocalDateTime.now();
        if (orderMapper.markPickedIfPaid(order.getId(), pickTime) != 1) {
            throw new BusinessException(409, "订单状态已变化，取票失败");
        }

        order.setIsPicked(1);
        order.setPickTime(pickTime);

        return convertToVO(order);
    }

    /** 取消符合条件的待支付订单，并回补余票及释放座位锁。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        requireOrderAccess(order.getUserId(), order.getShowtimeId());

        if (!Objects.equals(order.getStatus(), 0)) {
            throw new BusinessException(409, "只能取消待支付订单");
        }

        LocalDateTime cancelTime = LocalDateTime.now();
        if (orderMapper.cancelIfPending(order.getId(), cancelTime) != 1) {
            throw new BusinessException(409, "订单状态已变化，取消失败");
        }

        restoreInventoryOrFail(order);

        unlockOrderSeatsAfterCommit(order);
    }

    /** 按退票规则更新退款状态，返还优惠券和积分，并恢复座位库存。 */
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderNo) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        requireOrderAccess(order.getUserId(), order.getShowtimeId());

        if (!Objects.equals(order.getStatus(), 1)) {
            throw new BusinessException(409, "只能退票已支付订单");
        }

        if (Objects.equals(order.getIsPicked(), 1)) {
            throw new BusinessException(409, "已取票订单不能退款");
        }

        Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
        if (showtime == null) {
            throw new BusinessException(404, "场次不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = showtime.getStartTime();

        // 开场前 30 分钟内不可退票
        if (startTime.minusMinutes(30).isBefore(now)) {
            throw new BusinessException("距离开场不足 30 分钟，无法退票");
        }

        // 计算退款金额
        BigDecimal refundAmount = order.getTotalPrice();

        // 开场前 24 小时内退票，扣除 10% 手续费
        if (startTime.minusHours(24).isBefore(now)) {
            BigDecimal fee = order.getTotalPrice().multiply(new BigDecimal("0.10"));
            refundAmount = order.getTotalPrice().subtract(fee);
            order.setRemark("退票手续费：¥" + fee.setScale(2, RoundingMode.HALF_UP) +
                    "，退款金额：¥" + refundAmount.setScale(2, RoundingMode.HALF_UP));
        } else {
            // 开场前 24 小时外，全额退款
            order.setRemark("全额退款：¥" + refundAmount.setScale(2, RoundingMode.HALF_UP));
        }

        if (orderMapper.refundIfPaidAndNotPicked(order.getId(), now, order.getRemark()) != 1) {
            throw new BusinessException(409, "订单状态已变化，退款失败");
        }

        couponService.restoreCoupon(order.getCouponId(), order.getId());

        BigDecimal pointsUsed = order.getPointsUsed() == null ? BigDecimal.ZERO : order.getPointsUsed();
        BigDecimal pointsEarned = order.getPointsEarned() == null ? BigDecimal.ZERO : order.getPointsEarned();
        if ((pointsUsed.signum() > 0 || pointsEarned.signum() > 0)
                && userMapper.restorePoints(order.getUserId(), pointsUsed, pointsEarned) != 1) {
            throw new BusinessException(409, "积分回退失败，请稍后重试");
        }

        restoreInventoryOrFail(order);

        unlockOrderSeatsAfterCommit(order);
    }

    private void restoreInventoryOrFail(TicketOrder order) {
        if (showtimeMapper.restoreAvailableSeats(order.getShowtimeId(), order.getTicketCount()) != 1) {
            throw new IllegalStateException("场次余票回补失败，订单号：" + order.getOrderNo());
        }
    }

    /** 数据库事务提交后再释放锁，避免库存尚未生效时被新的下单请求抢占。 */
    private void unlockOrderSeatsAfterCommit(TicketOrder order) {
        Runnable unlockAction = () -> unlockSeatsQuietly(
                order.getShowtimeId(), Arrays.asList(order.getSeats().split(",")), order.getOrderNo());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    unlockAction.run();
                }
            });
        } else {
            // 兼容没有 Spring 事务代理的单元测试调用。
            unlockAction.run();
        }
    }

    /** 通过 Lua 比较订单号后删除锁，避免释放其他订单新取得的座位锁。 */
    private void unlockSeat(Long showtimeId, String seat, String ownerToken) {
        String key = SEAT_LOCK_PREFIX + showtimeId + ":" + seat;
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), ownerToken);
    }

    /** 通过 Lua 比较订单号后删除锁，避免释放其他订单新取得的座位锁。 */
    private void unlockSeatsQuietly(Long showtimeId, Collection<String> seats, String ownerToken) {
        for (String seat : seats) {
            try {
                unlockSeat(showtimeId, seat, ownerToken);
            } catch (RuntimeException ignored) {
                // 保留原始业务异常；过期时间仍会兜底清理锁。
            }
        }
    }

    private OrderVO convertToVO(TicketOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setShowtimeId(order.getShowtimeId());
        vo.setSeats(order.getSeats());
        vo.setSeatCount(order.getTicketCount());
        vo.setTotalPrice(order.getTotalPrice());
        vo.setOriginalPrice(order.getOriginalPrice() == null ? order.getTotalPrice() : order.getOriginalPrice());
        vo.setDiscountAmount(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        vo.setCouponId(order.getCouponId());
        vo.setStatus(order.getStatus());
        vo.setStatusText(STATUS_MAP.getOrDefault(order.getStatus(), "未知"));
        vo.setTicketCode(order.getTicketCode());
        vo.setIsPicked(order.getIsPicked());
        vo.setPickTime(order.getPickTime());
        vo.setPayTime(order.getPayTime());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        User user = userMapper.selectById(order.getUserId());
        if (user != null) {
            vo.setUserName(user.getNickname());
        }

        Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
        if (showtime != null) {
            vo.setShowtime(showtime.getStartTime());

            Movie movie = movieMapper.selectById(showtime.getMovieId());
            if (movie != null) {
                vo.setMovieId(movie.getId());
                vo.setMovieTitle(movie.getTitle());
                vo.setMoviePoster(movie.getPoster());
            }

            Cinema cinema = cinemaMapper.selectById(showtime.getCinemaId());
            if (cinema != null) {
                vo.setCinemaName(cinema.getName());
            }

            Hall hall = hallMapper.selectById(showtime.getHallId());
            if (hall != null) {
                vo.setHallName(hall.getName());
            }
        }

        return vo;
    }
}
