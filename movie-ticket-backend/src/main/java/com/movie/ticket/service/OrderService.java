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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private static final int LOCK_EXPIRE_MINUTES = 15;

    private static final Map<Integer, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(0, "待支付");
        STATUS_MAP.put(1, "已支付");
        STATUS_MAP.put(2, "已取消");
        STATUS_MAP.put(3, "已退款");
    }

    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO dto) {
        Long userId = UserContext.getUserId();

        Showtime showtime = showtimeMapper.selectById(dto.getShowtimeId());
        if (showtime == null) {
            throw new BusinessException("场次不存在");
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

        for (String seat : seats) {
            if (!lockSeat(dto.getShowtimeId(), seat)) {
                throw new BusinessException("座位 " + seat + " 已被占用");
            }
        }

        String orderNo = generateOrderNo();
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

        showtime.setAvailableSeats(showtime.getAvailableSeats() - seats.size());
        showtimeMapper.updateById(showtime);

        return orderNo;
    }

    private boolean lockSeat(Long showtimeId, String seat) {
        String key = SEAT_LOCK_PREFIX + showtimeId + ":" + seat;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(success);
    }

    private String generateOrderNo() {
        return ORDER_NO_PREFIX + System.currentTimeMillis() +
               String.format("%04d", new Random().nextInt(10000));
    }

    public IPage<OrderVO> getMyOrders(Integer page, Integer size, Integer status) {
        Long userId = UserContext.getUserId();
        Page<TicketOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(TicketOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(TicketOrder::getStatus, status);
        }

        wrapper.orderByDesc(TicketOrder::getCreateTime);
        IPage<TicketOrder> orderPage = orderMapper.selectPage(pageParam, wrapper);

        return orderPage.convert(this::convertToVO);
    }

    public IPage<OrderVO> getViewingHistory(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        Page<TicketOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketOrder::getUserId, userId)
                .eq(TicketOrder::getStatus, 1)
                .inSql(TicketOrder::getShowtimeId,
                        "SELECT id FROM showtime WHERE end_time < NOW()")
                .orderByDesc(TicketOrder::getPayTime);

        return orderMapper.selectPage(pageParam, wrapper).convert(this::convertToVO);
    }

    public IPage<OrderVO> getPage(Integer page, Integer size, String orderNo, Integer status) {
        Page<TicketOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();

        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.eq(TicketOrder::getOrderNo, orderNo);
        }

        if (status != null) {
            wrapper.eq(TicketOrder::getStatus, status);
        }

        wrapper.orderByDesc(TicketOrder::getCreateTime);
        IPage<TicketOrder> orderPage = orderMapper.selectPage(pageParam, wrapper);

        return orderPage.convert(this::convertToVO);
    }

    public OrderVO getByOrderNo(String orderNo) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Long userId = UserContext.getUserId();
        if (!UserContext.isAdmin() && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该订单");
        }

        return convertToVO(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long couponId) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Long userId = UserContext.getUserId();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }

        if (order.getStatus() != 0) {
            throw new BusinessException("订单状态异常");
        }

        BigDecimal originalPrice = order.getOriginalPrice() == null
                ? order.getTotalPrice() : order.getOriginalPrice();
        order.setOriginalPrice(originalPrice);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponId(null);

        if (couponId != null) {
            UserCoupon coupon = couponService.validateAvailableCoupon(couponId, userId, originalPrice);
            BigDecimal discount = coupon.getAmount().min(originalPrice);
            order.setCouponId(coupon.getId());
            order.setDiscountAmount(discount);
            order.setTotalPrice(originalPrice.subtract(discount));
            couponService.markUsed(coupon, order.getId());
        } else {
            order.setTotalPrice(originalPrice);
        }

        // 生成取票码：订单号后4位 + 4位随机数
        String ticketCode = generateTicketCode(orderNo);

        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        order.setTicketCode(ticketCode);
        order.setIsPicked(0);
        orderMapper.updateById(order);
    }

    private String generateTicketCode(String orderNo) {
        String orderSuffix = orderNo.substring(orderNo.length() - 4);
        int random = 1000 + new Random().nextInt(9000);
        return orderSuffix + random;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderVO pickTicket(String ticketCode) {
        // 通过取票码查询订单
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getTicketCode, ticketCode)
        );

        if (order == null) {
            throw new BusinessException("取票码无效");
        }

        if (order.getStatus() != 1) {
            throw new BusinessException("订单未支付或已取消");
        }

        if (order.getIsPicked() == 1) {
            throw new BusinessException("该订单已取票");
        }

        // 标记为已取票
        order.setIsPicked(1);
        order.setPickTime(LocalDateTime.now());
        orderMapper.updateById(order);

        return convertToVO(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Long userId = UserContext.getUserId();
        if (!UserContext.isAdmin() && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }

        if (order.getStatus() != 0) {
            throw new BusinessException("只能取消待支付订单");
        }

        order.setStatus(2);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);

        Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
        if (showtime != null) {
            showtime.setAvailableSeats(showtime.getAvailableSeats() + order.getTicketCount());
            showtimeMapper.updateById(showtime);
        }

        String[] seats = order.getSeats().split(",");
        for (String seat : seats) {
            unlockSeat(order.getShowtimeId(), seat);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderNo) {
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Long userId = UserContext.getUserId();
        if (!UserContext.isAdmin() && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }

        if (order.getStatus() != 1) {
            throw new BusinessException("只能退票已支付订单");
        }

        Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
        if (showtime == null) {
            throw new BusinessException("场次不存在");
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

        order.setStatus(3);
        order.setCancelTime(now);
        orderMapper.updateById(order);

        couponService.restoreCoupon(order.getCouponId());

        showtime.setAvailableSeats(showtime.getAvailableSeats() + order.getTicketCount());
        showtimeMapper.updateById(showtime);

        String[] seats = order.getSeats().split(",");
        for (String seat : seats) {
            unlockSeat(order.getShowtimeId(), seat);
        }
    }

    private void unlockSeat(Long showtimeId, String seat) {
        String key = SEAT_LOCK_PREFIX + showtimeId + ":" + seat;
        redisTemplate.delete(key);
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
