package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.dto.PriceCompareRequest;
import com.movie.ticket.vo.PriceComparisonVO;
import com.movie.ticket.entity.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** All personal reads use the identity captured by the controller, including SSE workers. */
@Service
@RequiredArgsConstructor
public class AssistantBookingService {
    private final ShowtimeMapper showtimes;
    private final MovieMapper movies;
    private final CinemaMapper cinemas;
    private final HallMapper halls;
    private final TicketOrderMapper orders;
    private final UserMapper users;
    private final UserCouponMapper coupons;
    private final SeatRecommendationService seatRecommendations;

    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }

    private Showtime bookable(Long id) {
        Showtime showtime = showtimes.selectById(id);
        if (showtime == null) throw new BusinessException("场次不存在");
        Movie movie = movies.selectById(showtime.getMovieId());
        Cinema cinema = cinemas.selectById(showtime.getCinemaId());
        Hall hall = halls.selectById(showtime.getHallId());
        if (!Integer.valueOf(0).equals(showtime.getStatus()) || showtime.getStartTime().isBefore(now().plusMinutes(15))
                || movie == null || !Integer.valueOf(1).equals(movie.getStatus())
                || cinema == null || !Integer.valueOf(1).equals(cinema.getStatus())
                || hall == null || !Integer.valueOf(1).equals(hall.getStatus())) {
            throw new BusinessException("该场次已不可购票，请重新选择场次");
        }
        return showtime;
    }

    /** 为助手查询当前场次的可用座位方案。 */
    public Map<String, Object> recommendSeats(Long showtimeId, SeatRecommendationRequest request) {
        bookable(showtimeId);
        var recommendation = seatRecommendations.recommendSmart(showtimeId, request);
        var selected = bookable(showtimeId);
        var result = new LinkedHashMap<String, Object>(Map.of("showtimeId", showtimeId, "people", request.getPeople(),
                "recommendations", recommendation.getRecommendations(),
                "preferenceSummary", recommendation.getPreferenceSummary(),
                "analysisSource", recommendation.getAnalysisSource(),
                "notice", "推荐为查询快照，点击方案进入选座页后重新校验；未锁座、未创建订单。"));
        addMoviePresentation(result, selected);
        return result;
    }

    private void addMoviePresentation(Map<String, Object> result, Showtime showtime) {
        if (showtime == null) return;
        Movie movie = movies.selectById(showtime.getMovieId());
        if (movie == null) return;
        result.put("movieTitle", movie.getTitle());
        if (movie.getPoster() != null) result.put("moviePoster", movie.getPoster());
    }

    /** Local algorithm for cross-showtime planning; avoids a paid model call for every candidate. */
    public List<com.movie.ticket.vo.SeatRecommendationVO> planSeats(Long showtimeId, int people, String preference, String text) {
        bookable(showtimeId);
        var inferred = SeatRecommendationService.inferPreference(text, preference);
        var result = seatRecommendations.recommend(showtimeId, people, inferred.name(), 1);
        bookable(showtimeId);
        return result;
    }

    /** 批量试算场次价格及优惠，不占用资产。 */
    public Map<Long, Map<String, Object>> previewPrices(Long userId, List<com.movie.ticket.vo.ShowtimeVO> candidates,
            int people, boolean usePoints, boolean useCoupons) {
        if (people < 1 || people > 5) throw new IllegalArgumentException();
        BigDecimal balance = BigDecimal.ZERO;
        List<UserCoupon> available = List.of();
        if (userId != null) {
            User user = users.selectById(userId);
            if (user == null) throw new BusinessException(401, "当前账号不可用，请重新登录");
            balance = user.getPoints() == null ? BigDecimal.ZERO : user.getPoints();
            if (useCoupons) available = coupons.selectList(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getStatus, 0)
                    .le(UserCoupon::getValidFrom, now()).ge(UserCoupon::getExpireTime, now())
                    .orderByAsc(UserCoupon::getExpireTime).orderByAsc(UserCoupon::getId));
        }
        var result = new LinkedHashMap<Long, Map<String, Object>>();
        for (var showtime : candidates) {
            BigDecimal original = showtime.getBasePrice().multiply(BigDecimal.valueOf(people));
            var eligible = available.stream().filter(c -> c.getMinSpend().compareTo(original) <= 0).toList();
            result.put(showtime.getId(), Map.of("originalPrice", original,
                    "bestPlan", plans(original, balance, eligible, usePoints).getFirst()));
        }
        return result;
    }

    /** 根据当前用户资产试算价格；试算不扣券、不扣积分。 */
    public Map<String, Object> quote(Long userId, Long showtimeId, Integer people, String orderNo, boolean usePoints) {
        if (userId == null) throw new BusinessException("请先登录后试算自己的优惠");
        BigDecimal original;
        Map<String, Object> result = new LinkedHashMap<>();
        if (orderNo != null) {
            TicketOrder order = orders.selectOne(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getUserId, userId).eq(TicketOrder::getOrderNo, orderNo));
            if (order == null || !userId.equals(order.getUserId())) throw new BusinessException("未找到当前账号的订单");
            if (!Integer.valueOf(0).equals(order.getStatus()) || order.getCreateTime() == null
                    || !order.getCreateTime().plusMinutes(10).isAfter(now())) {
                throw new BusinessException("仅支持未超时的待支付订单试算，请刷新订单状态");
            }
            original = order.getOriginalPrice() == null ? order.getTotalPrice() : order.getOriginalPrice();
            result.put("orderNo", order.getOrderNo());
            result.put("people", order.getTicketCount());
            addMoviePresentation(result, showtimes.selectById(order.getShowtimeId()));
        } else {
            if (showtimeId == null || people == null || people < 1 || people > 5) throw new IllegalArgumentException();
            Showtime showtime = bookable(showtimeId);
            if (showtime.getAvailableSeats() < people) throw new BusinessException("该场次余票不足，请换一个场次");
            original = showtime.getBasePrice().multiply(BigDecimal.valueOf(people));
            result.put("showtimeId", showtimeId);
            result.put("people", people);
            addMoviePresentation(result, showtime);
        }
        User user = users.selectById(userId);
        if (user == null) throw new BusinessException("当前账号不可用，请重新登录");
        BigDecimal balance = user.getPoints() == null ? BigDecimal.ZERO : user.getPoints();
        LocalDateTime queriedAt = now();
        List<UserCoupon> available = coupons.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getStatus, 0)
                .le(UserCoupon::getValidFrom, queriedAt).ge(UserCoupon::getExpireTime, queriedAt)
                .le(UserCoupon::getMinSpend, original).orderByAsc(UserCoupon::getExpireTime).orderByAsc(UserCoupon::getId));
        List<Map<String, Object>> plans = plans(original, balance, available, usePoints);
        result.put("originalPrice", original);
        result.put("availablePoints", balance);
        result.put("eligibleCouponCount", available.size());
        result.put("plans", plans.stream().limit(3).toList());
        result.put("bestPlan", plans.getFirst());
        result.put("usePoints", usePoints);
        result.put("notice", "仅试算，不消耗优惠券或积分；每单最多一张券，先扣券再按100积分抵1元计算，积分每单最多抵1元。最优指当前最低实付，支付时重新校验，不保证连座。");
        return result;
    }

    /** 比较符合条件的场次与优惠组合，返回实付价格方案。 */
    public PriceComparisonVO compare(Long userId, PriceCompareRequest request) {
        if ((request.getMovieId() == null) == (request.getShowtimeId() == null)
                || request.getPeople() < 1 || request.getPeople() > 5) {
            throw new BusinessException(400, "请提供电影或场次（二选一），人数须为1至5人");
        }
        LocalDateTime queriedAt = now();
        Long movieId = request.getMovieId();
        LocalDate date = request.getDate();
        if (request.getShowtimeId() != null) {
            Showtime selected = showtimes.selectById(request.getShowtimeId());
            if (selected == null) throw new BusinessException(404, "场次不存在");
            movieId = selected.getMovieId();
            if (date == null) date = selected.getStartTime().toLocalDate();
        }
        if (date == null) date = queriedAt.toLocalDate();
        if (date.isBefore(queriedAt.toLocalDate()) || date.isAfter(queriedAt.toLocalDate().plusDays(90))) {
            throw new BusinessException(400, "请选择今天起90天内的观影日期");
        }
        Movie movie = movies.selectById(movieId);
        if (movie == null || !Integer.valueOf(1).equals(movie.getStatus())) {
            throw new BusinessException(400, "该电影暂不可购票");
        }
        BigDecimal balance = BigDecimal.ZERO;
        List<UserCoupon> available = List.of();
        if (userId != null) {
            User user = users.selectById(userId);
            if (user == null) throw new BusinessException(401, "当前账号不可用，请重新登录");
            balance = user.getPoints() == null ? BigDecimal.ZERO : user.getPoints();
            if (request.isUseCoupons()) available = coupons.selectList(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getStatus, 0)
                    .le(UserCoupon::getValidFrom, queriedAt).ge(UserCoupon::getExpireTime, queriedAt)
                    .orderByAsc(UserCoupon::getExpireTime).orderByAsc(UserCoupon::getId));
        }
        var candidates = showtimes.selectPriceComparisonViews(movieId, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), queriedAt.plusMinutes(15), request.getPeople());
        var offers = new ArrayList<PriceComparisonVO.Offer>();
        for (var showtime : candidates) {
            BigDecimal original = showtime.getBasePrice().multiply(BigDecimal.valueOf(request.getPeople()));
            var eligible = available.stream().filter(c -> c.getMinSpend().compareTo(original) <= 0).toList();
            var best = plans(original, balance, eligible, request.isUsePoints()).getFirst();
            if (request.getMaxTotal() != null && ((BigDecimal) best.get("totalPrice")).compareTo(request.getMaxTotal()) > 0) continue;
            offers.add(new PriceComparisonVO.Offer(showtime.getId(), showtime.getCinemaId(), showtime.getCinemaName(),
                    showtime.getHallName(), showtime.getStartTime(), showtime.getAvailableSeats(), showtime.getBasePrice(),
                    original, best, "按当前优惠后总实付升序；同价优先少用积分，再优先较早开场"));
        }
        offers.sort(Comparator.<PriceComparisonVO.Offer, BigDecimal>comparing(o -> (BigDecimal) o.bestPlan().get("totalPrice"))
                .thenComparing(o -> (BigDecimal) o.bestPlan().get("pointsUsed"))
                .thenComparing(PriceComparisonVO.Offer::startTime).thenComparing(PriceComparisonVO.Offer::showtimeId));
        return new PriceComparisonVO(movieId, movie.getTitle(), movie.getPoster(), date, request.getPeople(), userId != null,
                request.isUsePoints(), request.isUseCoupons(), balance, candidates.size(), offers.size(), queriedAt,
                offers.stream().limit(10).toList(), "站内同片、同日、同人数比价，展示最低实付的前10个场次。"
                + (userId == null ? "访客仅比较原价，登录后可匹配本人优惠券与积分。" : "每单最多一张券，先扣券再使用积分；100积分抵1元，每单最多抵1元。")
                + "最优指当前最低现金实付；查询不占券、不扣积分、不锁座，余票不保证连座，支付时重新校验。");
    }

    private List<Map<String, Object>> plans(BigDecimal original, BigDecimal balance,
            List<UserCoupon> available, boolean usePoints) {
        List<Map<String, Object>> plans = new ArrayList<>();
        addPlans(plans, null, original, balance, usePoints);
        for (UserCoupon coupon : available) addPlans(plans, coupon, original, balance, usePoints);
        plans.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(p -> (BigDecimal) p.get("totalPrice"))
                .thenComparing(p -> (BigDecimal) p.get("pointsUsed"))
                .thenComparingInt(p -> p.containsKey("couponId") ? 1 : 0));
        return plans;
    }

    private void addPlans(List<Map<String, Object>> plans, UserCoupon coupon, BigDecimal original,
                          BigDecimal balance, boolean usePoints) {
        BigDecimal amount = coupon == null ? BigDecimal.ZERO : coupon.getAmount();
        plans.add(plan(coupon, TicketPriceCalculator.calculate(original, amount, BigDecimal.ZERO, balance)));
        BigDecimal points = balance.min(TicketPriceCalculator.maxPoints(original, amount))
                .max(BigDecimal.ZERO).setScale(1, RoundingMode.DOWN);
        if (usePoints && points.signum() > 0) {
            var price = TicketPriceCalculator.calculate(original, amount, points, balance);
            if (price.pointsDiscount().signum() > 0) plans.add(plan(coupon, price));
        }
    }

    private Map<String, Object> plan(UserCoupon coupon, TicketPriceCalculator.Price price) {
        Map<String, Object> plan = new LinkedHashMap<>();
        if (coupon != null) plan.put("couponId", coupon.getId());
        plan.put("couponName", coupon == null ? "不使用优惠券" : coupon.getName());
        plan.put("couponDiscount", price.couponDiscount());
        plan.put("pointsUsed", price.pointsUsed());
        plan.put("pointsDiscount", price.pointsDiscount());
        plan.put("discountAmount", price.discountAmount());
        plan.put("totalPrice", price.totalPrice());
        return plan;
    }
}
