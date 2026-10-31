package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntervalVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.movie.ticket.dto.SchedulingOptimizationRequest;
import com.movie.ticket.dto.SchedulingPredictionRequest;
import com.movie.ticket.dto.SchedulingSimulationRequest;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于历史售票数据的可解释排片决策模型。
 *
 * <p>先汇总影片、星期、时段和影厅的历史上座率，再通过固定权重预测单场需求，
 * 并为低上座率预警、换片模拟及整日 CP-SAT 约束优化提供统一的计算口径。</p>
 * <p>统计、预测和优化由本地算法完成，大模型基于同一份业务快照生成文字分析。
 * 本服务不写入真实排片，草案应用由 {@link SchedulingWorkspaceService} 负责。</p>
 */
@Service
@RequiredArgsConstructor
public class SchedulingIntelligenceService {

    /** 没有历史样本时使用的上座率先验；内部上座率通常使用 0～1 的比例。 */
    private static final double DEFAULT_OCCUPANCY = 0.45;
    /** 低上座率预警线，预测值严格低于 35% 时进入预警列表。 */
    private static final double LOW_OCCUPANCY_LINE = 0.35;
    /** 排片总览统计未来场次的天数。 */
    private static final int FUTURE_DAYS = 14;
    /** 优化候选从 10:00 开始，每 30 分钟枚举一次，最晚在 23:30 开场。 */
    private static final LocalTime OPTIMIZATION_OPEN_TIME = LocalTime.of(10, 0);
    private static final LocalTime OPTIMIZATION_LAST_START = LocalTime.of(23, 30);
    /** 进程内原生求解器库的加载标记，配合同步方法避免重复初始化。 */
    private static volatile boolean orToolsLoaded;

    private final MovieMapper movieMapper;
    private final ShowtimeMapper showtimeMapper;
    private final TicketOrderMapper orderMapper;
    private final HallMapper hallMapper;
    private final SchedulingAiService schedulingAiService;

    /**
     * 使用统一预测口径评估给定排片，支持人工微调后的草案，不逐场调用大模型。
     * 整体上座率按预计观众总数除以总容量计算，预计观众数在各场次内先取整。
     *
     * @param cinemaId 用于加载历史统计的影院 ID
     * @param showtimes 待评估的场次列表
     * @return 场次数、影片数、预计收入、预计观众、整体上座率和逐场明细
     */
    public Map<String, Object> evaluatePlan(Long cinemaId, List<Showtime> showtimes) {
        ModelContext context = loadContext(cinemaId);
        List<PlanItem> items = showtimes.stream().map(s -> planItem(s, context, 0, false)).filter(Objects::nonNull).toList();
        long audience = items.stream().mapToLong(PlanItem::expectedAudience).sum();
        int seats = items.stream().mapToInt(PlanItem::capacity).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("showtimeCount", showtimes.size());
        result.put("distinctMovies", showtimes.stream().map(Showtime::getMovieId).distinct().count());
        result.put("expectedRevenue", round(items.stream().mapToDouble(PlanItem::expectedRevenue).sum()));
        result.put("occupancyRate", seats == 0 ? null : round(audience * 100.0 / seats));
        result.put("expectedAudience", audience);
        result.put("historicalSamples", context.historical().size());
        result.put("items", items.stream().map(this::planItemMap).toList());
        return result;
    }

    /**
     * 汇总排片预警、黄金时段推荐、增减场建议、历史热力图和模型误差，并附加 AI 分析。
     *
     * @param cinemaId 统计范围内的影院 ID，为空时加载全部影院数据
     * @return 排片决策总览及数据生成时间
     */
    public Map<String, Object> getInsights(Long cinemaId) {
        cinemaId = UserContext.scopeCinemaId(cinemaId);
        ModelContext context = loadContext(cinemaId);
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> allLowOccupancy = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0))
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .filter(s -> s.getStartTime().isBefore(now.plusDays(FUTURE_DAYS)))
                .map(s -> lowOccupancyItem(s, context))
                .filter(item -> ((Number) item.get("predictedRate")).doubleValue() < LOW_OCCUPANCY_LINE * 100)
                .sorted(Comparator.comparingDouble(item -> ((Number) item.get("predictedRate")).doubleValue()))
                .toList();
        // 预警总数按全部命中场次统计，页面明细仅展示预测上座率最低的 12 场。
        List<Map<String, Object>> lowOccupancy = allLowOccupancy.stream().limit(12).toList();

        List<Map<String, Object>> goldenRecommendations = buildGoldenRecommendations(context, cinemaId);
        List<Map<String, Object>> frequencyRecommendations = buildFrequencyRecommendations(context);
        List<Map<String, Object>> heatmap = buildHeatmap(context);
        Map<String, Object> evaluation = buildEvaluation(context);

        // 总览平均值对各场次预测比例取算术平均，与整日方案按容量汇总的上座率口径不同。
        var futureRates = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0) && s.getStartTime() != null && s.getStartTime().isAfter(now))
                .filter(s -> s.getStartTime().isBefore(now.plusDays(FUTURE_DAYS)))
                .mapToDouble(s -> predict(s.getMovieId(), s.getHallId(), s.getStartTime(), context).rate())
                .summaryStatistics();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("predictedAverageRate", futureRates.getCount() == 0 ? null : percent(futureRates.getAverage()));
        summary.put("futureShowtimeCount", futureRates.getCount());
        summary.put("lowOccupancyCount", allLowOccupancy.size());
        summary.put("actionableSuggestionCount", frequencyRecommendations.stream()
                .filter(item -> !"KEEP".equals(item.get("action"))).count());
        summary.put("historicalSamples", context.historical().size());
        summary.put("modelConfidence", confidence(context.historical().size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("lowOccupancyShowtimes", lowOccupancy);
        result.put("goldenTimeRecommendations", goldenRecommendations);
        result.put("frequencyRecommendations", frequencyRecommendations);
        result.put("heatmap", heatmap);
        result.put("halls", context.halls().values().stream()
                .sorted(Comparator.comparing(Hall::getName, Comparator.nullsLast(String::compareTo)))
                .map(h -> Map.<String, Object>of("id", h.getId(), "name", h.getName()))
                .toList());
        result.put("evaluation", evaluation);
        result.put("model", modelDescription());
        result.put("generatedAt", now);
        return withAi("影院排片总览", result, context);
    }

    /** 校验影片及影厅归属后预测单场上座率，返回主要影响因素并附加 AI 解释。 */
    public Map<String, Object> predict(SchedulingPredictionRequest request) {
        UserContext.requireCinema(request.getCinemaId());
        validateTarget(request.getMovieId(), request.getCinemaId(), request.getHallId());
        ModelContext context = loadContext(request.getCinemaId());
        Prediction prediction = predict(request.getMovieId(), request.getHallId(), request.getStartTime(), context);
        return withAi("单场上座率预测", predictionResult(request.getMovieId(), request.getHallId(), request.getStartTime(), prediction, context), context);
    }

    /** 模拟更换影片、影厅或开场时间的效果，比较调整前后的上座率、观众数和收入，不修改原场次。 */
    public Map<String, Object> simulate(SchedulingSimulationRequest request) {
        Showtime original = showtimeMapper.selectById(request.getOriginalShowtimeId());
        if (original == null) {
            throw new BusinessException("原场次不存在");
        }
        UserContext.requireCinema(original.getCinemaId());
        // 未指定影厅或时间时沿用原安排，前后方案在同一份历史上下文中预测。
        Long targetHallId = request.getTargetHallId() == null ? original.getHallId() : request.getTargetHallId();
        LocalDateTime targetStart = request.getTargetStartTime() == null ? original.getStartTime() : request.getTargetStartTime();
        validateTarget(request.getTargetMovieId(), original.getCinemaId(), targetHallId);

        ModelContext context = loadContext(original.getCinemaId());
        Prediction before = predict(original.getMovieId(), original.getHallId(), original.getStartTime(), context);
        Prediction after = predict(request.getTargetMovieId(), targetHallId, targetStart, context);
        Hall beforeHall = context.halls().get(original.getHallId());
        Hall afterHall = context.halls().get(targetHallId);
        int beforeCapacity = capacity(original, beforeHall);
        int afterCapacity = afterHall == null || afterHall.getTotalSeats() == null ? beforeCapacity : afterHall.getTotalSeats();
        // 收入比较沿用原场次票价，仅反映需求及影厅容量变化，不引入调价因素。
        double beforeRevenue = before.rate() * beforeCapacity * money(original.getBasePrice());
        double afterRevenue = after.rate() * afterCapacity * money(original.getBasePrice());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("original", predictionResult(original.getMovieId(), original.getHallId(), original.getStartTime(), before, context));
        result.put("adjusted", predictionResult(request.getTargetMovieId(), targetHallId, targetStart, after, context));
        result.put("occupancyLift", round((after.rate() - before.rate()) * 100));
        result.put("expectedExtraAudience", Math.round(after.rate() * afterCapacity - before.rate() * beforeCapacity));
        result.put("expectedRevenueLift", BigDecimal.valueOf(afterRevenue - beforeRevenue).setScale(2, RoundingMode.HALF_UP));
        // 上座率差异超过 2 个百分点才给出明确倾向，较小差异交由人工判断。
        result.put("recommendation", after.rate() > before.rate() + 0.02 ? "建议采用调整方案" :
                after.rate() < before.rate() - 0.02 ? "建议保留原排片" : "两种方案效果接近，可结合人工判断");
        result.put("notice", "模拟结果仅用于决策参考，不会修改真实排片");
        return withAi("换片方案模拟", result, context);
    }

    /**
     * 使用 Google OR-Tools CP-SAT 生成整日多目标排片方案。已有售票或已经开始的场次会被锁定，
     * 其余未锁定场次只作为“人工现状”基线，不会被此接口直接修改。
     * 此处锁定指在优化模型中强制保留，不会获取数据库行锁。
     *
     * @param request 目标影院、日期、影片范围、场次数约束、目标权重及求解时限
     * @return 求解状态、选中场次，以及人工现状、启发式方案和优化方案的对比指标
     */
    public Map<String, Object> optimize(SchedulingOptimizationRequest request) {
        UserContext.requireCinema(request.getCinemaId());
        validateOptimizationRequest(request);
        ensureOrToolsLoaded();
        long startedAt = System.nanoTime();
        ModelContext context = loadContext(request.getCinemaId());
        List<Hall> halls = context.halls().values().stream()
                .filter(hall -> Objects.equals(hall.getCinemaId(), request.getCinemaId()))
                .sorted(Comparator.comparing(Hall::getId)).toList();
        if (halls.isEmpty()) throw new BusinessException("所选影院没有可用影厅");

        // 影片范围为空时使用全部上映影片，并排除在目标日期尚未上映的影片。
        Set<Long> requestedMovieIds = request.getMovieIds() == null ? Set.of() : request.getMovieIds().stream()
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Movie> movies = context.movies().values().stream()
                .filter(movie -> Objects.equals(movie.getStatus(), 1))
                .filter(movie -> movie.getReleaseDate() == null || !movie.getReleaseDate().isAfter(request.getTargetDate()))
                .filter(movie -> requestedMovieIds.isEmpty() || requestedMovieIds.contains(movie.getId()))
                .sorted(Comparator.comparing(Movie::getId)).toList();
        if (movies.isEmpty()) throw new BusinessException("没有可参与优化的正在上映影片");
        if (!requestedMovieIds.isEmpty() && movies.size() != requestedMovieIds.size()) {
            throw new BusinessException("影片范围中包含不存在或非正在上映的影片");
        }
        if (request.getMinDistinctMovies() > movies.size()) {
            throw new BusinessException("最低影片数不能超过参与优化的影片数量");
        }

        LocalDateTime dayStart = request.getTargetDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Showtime> existing = context.showtimes().stream()
                .filter(showtime -> Objects.equals(showtime.getCinemaId(), request.getCinemaId()))
                .filter(showtime -> showtime.getStartTime() != null && !showtime.getStartTime().isBefore(dayStart)
                        && showtime.getStartTime().isBefore(dayEnd))
                .filter(showtime -> !Objects.equals(showtime.getStatus(), 2)).toList();
        List<PlanItem> manualPlan = existing.stream()
                .map(showtime -> planItem(showtime, context, request.getCleaningMinutes(), false))
                .filter(Objects::nonNull).toList();
        LocalDateTime now = LocalDateTime.now();
        // 售票统计同时参考已支付订单和座位台账，已占用座位或已开场的原安排必须保留。
        List<PlanItem> lockedPlan = existing.stream()
                .filter(showtime -> context.soldSeats().getOrDefault(showtime.getId(), 0) > 0
                        || !showtime.getStartTime().isAfter(now))
                .map(showtime -> planItem(showtime, context, request.getCleaningMinutes(), true))
                .filter(Objects::nonNull).toList();

        Map<Long, Integer> lockedByHall = countsBy(lockedPlan, PlanItem::hallId);
        Map<Long, Integer> lockedByMovie = countsBy(lockedPlan, PlanItem::movieId);
        Map<Long, Integer> lockedGoldenByHall = countsBy(lockedPlan.stream().filter(item -> isGolden(item.startTime()))
                .toList(), PlanItem::hallId);
        // 已锁定场次数高于请求目标时，以锁定数量为下限，避免为满足目标而移除受保护场次。
        Map<Long, Integer> targetByHall = halls.stream().collect(Collectors.toMap(Hall::getId,
                hall -> Math.max(request.getTargetShowtimesPerHall(), lockedByHall.getOrDefault(hall.getId(), 0))));
        Map<Long, Double> prices = estimatedPrices(context, movies);
        List<Showtime> adjacent = context.showtimes().stream()
                .filter(s -> !s.getStartTime().toLocalDate().equals(request.getTargetDate())).toList();
        // 候选必须在未来开场，并避开其他日期场次及双方清场时间形成的占厅区间。
        List<OptimizationCandidate> candidates = buildCandidates(request, context, halls, movies, prices, dayStart).stream()
                .filter(c -> c.startTime().isAfter(now))
                .filter(c -> adjacent.stream().noneMatch(s -> Objects.equals(s.getHallId(), c.hallId())
                        && s.getStartTime().isBefore(c.endTime().plusMinutes(request.getCleaningMinutes()))
                        && s.getEndTime().plusMinutes(request.getCleaningMinutes()).isAfter(c.startTime())))
                .toList();
        double maxRevenue = candidates.stream().mapToDouble(OptimizationCandidate::expectedRevenue).max().orElse(1);

        CpModel model = new CpModel();
        List<CandidateDecision> decisions = new ArrayList<>();
        Map<Long, List<IntervalVar>> intervalsByHall = new HashMap<>();
        for (Hall hall : halls) intervalsByHall.put(hall.getId(), new ArrayList<>());
        // 锁定场次建为必选固定区间，占用时长包含放映时长和清场时间。
        for (PlanItem locked : lockedPlan) {
            long startMinute = Duration.between(dayStart, locked.startTime()).toMinutes();
            long occupiedMinutes = Math.max(1, Duration.between(locked.startTime(), locked.endTime()).toMinutes()
                    + request.getCleaningMinutes());
            intervalsByHall.get(locked.hallId()).add(model.newFixedInterval(startMinute, occupiedMinutes,
                    "locked_" + locked.sourceShowtimeId()));
        }
        // 每个候选对应一个布尔决策变量，仅选中时，其可选区间才参与同厅不重叠约束。
        for (int index = 0; index < candidates.size(); index++) {
            OptimizationCandidate candidate = candidates.get(index);
            BoolVar selected = model.newBoolVar("x_" + index);
            long startMinute = Duration.between(dayStart, candidate.startTime()).toMinutes();
            long occupiedMinutes = candidate.durationMinutes() + request.getCleaningMinutes();
            IntervalVar interval = model.newOptionalFixedInterval(startMinute, occupiedMinutes, selected,
                    "show_" + index);
            intervalsByHall.get(candidate.hallId()).add(interval);
            decisions.add(new CandidateDecision(candidate, selected,
                    candidateObjective(candidate, maxRevenue, request)));
        }
        intervalsByHall.values().forEach(model::addNoOverlap);

        // 每厅候选选中数必须补足目标，同时补齐锁定场次尚未满足的黄金时段最低场次。
        for (Hall hall : halls) {
            List<CandidateDecision> hallDecisions = decisions.stream()
                    .filter(item -> Objects.equals(item.candidate().hallId(), hall.getId())).toList();
            int required = targetByHall.get(hall.getId()) - lockedByHall.getOrDefault(hall.getId(), 0);
            if (hallDecisions.isEmpty() && required > 0) {
                return infeasibleOptimizationResult(request, manualPlan, List.of(), context, targetByHall,
                        "影厅“" + hall.getName() + "”没有足够的可用候选场次", startedAt);
            }
            model.addEquality(sumVariables(hallDecisions), required);
            int requiredGolden = Math.max(0, request.getMinGoldenShowtimesPerHall()
                    - lockedGoldenByHall.getOrDefault(hall.getId(), 0));
            if (requiredGolden > 0) {
                List<CandidateDecision> goldenDecisions = hallDecisions.stream()
                        .filter(item -> isGolden(item.candidate().startTime())).toList();
                model.addGreaterOrEqual(sumVariables(goldenDecisions), requiredGolden);
            }
        }

        // 单片上限先扣除锁定场次；已锁定数量超限时保留原数量，不再增加该片场次。
        Map<Long, BoolVar> newMovieUsed = new HashMap<>();
        for (Movie movie : movies) {
            List<CandidateDecision> movieDecisions = decisions.stream()
                    .filter(item -> Objects.equals(item.candidate().movieId(), movie.getId())).toList();
            int lockedCount = lockedByMovie.getOrDefault(movie.getId(), 0);
            int effectiveMaximum = Math.max(request.getMaxShowtimesPerMovie(), lockedCount);
            model.addLessOrEqual(sumVariables(movieDecisions), effectiveMaximum - lockedCount);
            if (lockedCount == 0 && !movieDecisions.isEmpty()) {
                // 通过两条约束将 used 与“该影片至少有一个候选被选中”等价关联。
                BoolVar used = model.newBoolVar("movie_used_" + movie.getId());
                model.addLessOrEqual(sumVariables(movieDecisions),
                        LinearExpr.term(used, movieDecisions.size()));
                model.addGreaterOrEqual(sumVariables(movieDecisions), used);
                newMovieUsed.put(movie.getId(), used);
            }
        }
        // 已锁定影片计入多样性，只需补足最低影片数中尚未覆盖的部分。
        int lockedDistinct = (int) lockedPlan.stream().map(PlanItem::movieId).distinct().count();
        int requiredNewDistinct = Math.max(0, Math.min(request.getMinDistinctMovies(), movies.size()) - lockedDistinct);
        if (requiredNewDistinct > 0) {
            if (newMovieUsed.size() < requiredNewDistinct) {
                return infeasibleOptimizationResult(request, manualPlan, List.of(), context, targetByHall,
                        "可用影片不足，无法满足最少影片多样性约束", startedAt);
            }
            model.addGreaterOrEqual(LinearExpr.sum(newMovieUsed.values().toArray(LinearArgument[]::new)),
                    requiredNewDistinct);
        }

        // 最大化收入与上座率加权分，并对每部新覆盖的影片奖励一次多样性得分。
        // 锁定场次对所有可行方案的贡献相同，不必作为常数项加入目标函数。
        List<LinearArgument> objectiveVariables = new ArrayList<>();
        List<Long> objectiveCoefficients = new ArrayList<>();
        decisions.forEach(decision -> {
            objectiveVariables.add(decision.selected());
            objectiveCoefficients.add(decision.objectiveScore());
        });
        newMovieUsed.values().forEach(used -> {
            objectiveVariables.add(used);
            objectiveCoefficients.add((long) request.getDiversityWeight() * 1_000L);
        });
        if (!objectiveVariables.isEmpty()) {
            model.maximize(LinearExpr.weightedSum(objectiveVariables.toArray(LinearArgument[]::new),
                    objectiveCoefficients.stream().mapToLong(Long::longValue).toArray()));
        }

        // 从同一候选集合生成贪心基线，便于比较约束求解与逐场择优的结果。
        List<PlanItem> heuristicPlan = heuristicPlan(candidates, lockedPlan, request, targetByHall, lockedByMovie);
        CpSolver solver = new CpSolver();
        // 限制求解时长与工作线程数，并固定随机种子。
        solver.getParameters().setMaxTimeInSeconds(request.getSolverSeconds())
                .setNumSearchWorkers(Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors())))
                .setRandomSeed(2026).setLogSearchProgress(false);
        CpSolverStatus status = solver.solve(model);
        // 只有已找到可行解时才读取决策变量；不可行或限时内未找到解时返回诊断结果。
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return infeasibleOptimizationResult(request, manualPlan, heuristicPlan, context, targetByHall,
                    status == CpSolverStatus.INFEASIBLE ? "约束组合无可行解，请减少目标场次或最低影片数" : "求解器未在限定时间内找到可行解",
                    startedAt);
        }

        // 将强制保留场次与求解器选中的候选合并，并用同一口径评估三种方案。
        List<PlanItem> optimizedPlan = new ArrayList<>(lockedPlan);
        decisions.stream().filter(decision -> solver.booleanValue(decision.selected()))
                .map(decision -> planItem(decision.candidate(), request.getCleaningMinutes()))
                .forEach(optimizedPlan::add);
        optimizedPlan.sort(Comparator.comparing(PlanItem::startTime).thenComparing(PlanItem::hallId));
        long elapsedMs = Math.round(solver.wallTime() * 1_000);
        PlanMetrics manualMetrics = planMetrics("人工现状", manualPlan, 0, request, targetByHall);
        PlanMetrics heuristicMetrics = planMetrics("加权启发式", heuristicPlan,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000 - elapsedMs), request, targetByHall);
        PlanMetrics optimizedMetrics = planMetrics("CP-SAT 多目标优化", optimizedPlan, elapsedMs, request, targetByHall);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status.name());
        result.put("statusText", status == CpSolverStatus.OPTIMAL ? "已找到全局最优解" : "已找到可行近似最优解");
        result.put("solver", "Google OR-Tools CP-SAT 9.11");
        result.put("targetDate", request.getTargetDate());
        result.put("objectiveValue", round(solver.objectiveValue()));
        result.put("bestObjectiveBound", round(solver.bestObjectiveBound()));
        result.put("optimalityGap", solver.objectiveValue() == 0 ? 0
                : round(Math.abs(solver.objectiveValue() - solver.bestObjectiveBound())
                / Math.abs(solver.objectiveValue()) * 100));
        result.put("solveTimeMs", elapsedMs);
        result.put("candidateVariables", decisions.size());
        result.put("lockedShowtimes", lockedPlan.size());
        result.put("selectedShowtimes", optimizedPlan.stream().map(this::planItemMap).toList());
        result.put("comparisons", List.of(metricsMap(manualMetrics), metricsMap(heuristicMetrics), metricsMap(optimizedMetrics)));
        result.put("revenueLiftVsManual", liftPercent(optimizedMetrics.expectedRevenue(), manualMetrics.expectedRevenue()));
        result.put("revenueLiftVsHeuristic", liftPercent(optimizedMetrics.expectedRevenue(), heuristicMetrics.expectedRevenue()));
        result.put("occupancyLiftVsManual", round((optimizedMetrics.occupancyRate() - manualMetrics.occupancyRate()) * 100));
        result.put("occupancyLiftVsHeuristic", round((optimizedMetrics.occupancyRate() - heuristicMetrics.occupancyRate()) * 100));
        result.put("constraints", List.of(
                "同一影厅场次不重叠，并预留 " + request.getCleaningMinutes() + " 分钟清场",
                "每个影厅目标 " + request.getTargetShowtimesPerHall() + " 场，已售票场次强制保留",
                "单部影片最多 " + request.getMaxShowtimesPerMovie() + " 场",
                "方案至少覆盖 " + Math.min(request.getMinDistinctMovies(), movies.size()) + " 部影片",
                "每厅至少安排 " + request.getMinGoldenShowtimesPerHall() + " 场 18:00-22:00 黄金时段",
                "预计观众数不超过对应影厅座位容量"
        ));
        result.put("weights", Map.of("预计收入", request.getRevenueWeight(), "预计上座率",
                request.getOccupancyWeight(), "影片多样性", request.getDiversityWeight()));
        result.put("notice", "结果为决策模拟，不会自动修改真实排片；预计收入和上座率来自历史模型而非财务承诺");
        return withAi("整日约束排片方案", result, context);
    }

    /** 校验优化参数之间的关系：权重总和为 100、日期不超过未来 30 天、黄金场次数不超过每厅目标。 */
    private void validateOptimizationRequest(SchedulingOptimizationRequest request) {
        if (request.getRevenueWeight() + request.getOccupancyWeight() + request.getDiversityWeight() != 100) {
            throw new BusinessException("预计收入、上座率和影片多样性权重之和必须为 100");
        }
        if (request.getTargetDate().isAfter(LocalDate.now().plusDays(30))) {
            throw new BusinessException("单次最多优化未来 30 天内的排片");
        }
        if (request.getMinGoldenShowtimesPerHall() > request.getTargetShowtimesPerHall()) {
            throw new BusinessException("每厅黄金时段最少场次不能超过每厅目标场次");
        }
    }

    /** 首次优化时同步加载 OR-Tools 原生库，仅在加载成功后设置标记。 */
    private static synchronized void ensureOrToolsLoaded() {
        if (orToolsLoaded) return;
        try {
            Loader.loadNativeLibraries();
            orToolsLoaded = true;
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            throw new BusinessException("CP-SAT 原生求解器加载失败，请检查当前操作系统运行库");
        }
    }

    /** 按上下文中同片场次的基础票价均值估价，没有票价样本时使用 45 元。 */
    private Map<Long, Double> estimatedPrices(ModelContext context, List<Movie> movies) {
        Map<Long, List<BigDecimal>> values = context.showtimes().stream().filter(s -> s.getBasePrice() != null)
                .collect(Collectors.groupingBy(Showtime::getMovieId,
                        Collectors.mapping(Showtime::getBasePrice, Collectors.toList())));
        return movies.stream().collect(Collectors.toMap(Movie::getId, movie -> {
            List<BigDecimal> prices = values.getOrDefault(movie.getId(), List.of());
            return prices.isEmpty() ? 45.0 : prices.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(45);
        }));
    }

    /**
     * 枚举影厅、影片和半小时间隔的开场时间，构造带预测观众数、收入的候选场次。
     * 放映及清场必须在次日 02:00 前完成；未来时间及邻日冲突由调用方进一步过滤。
     */
    private List<OptimizationCandidate> buildCandidates(SchedulingOptimizationRequest request, ModelContext context,
                                                         List<Hall> halls, List<Movie> movies,
                                                         Map<Long, Double> prices, LocalDateTime dayStart) {
        List<OptimizationCandidate> result = new ArrayList<>();
        LocalDateTime lastAllowedEnd = dayStart.plusDays(1).plusHours(2);
        for (Hall hall : halls) {
            int capacity = hall.getTotalSeats() == null ? 0 : hall.getTotalSeats();
            if (capacity <= 0) continue;
            for (Movie movie : movies) {
                int duration = movie.getDuration() == null ? 120 : movie.getDuration();
                if (duration <= 0) continue;
                LocalDateTime firstStart = request.getTargetDate().atTime(OPTIMIZATION_OPEN_TIME);
                LocalDateTime lastStart = request.getTargetDate().atTime(OPTIMIZATION_LAST_START);
                for (LocalDateTime start = firstStart; !start.isAfter(lastStart);
                     start = start.plusMinutes(30)) {
                    LocalDateTime end = start.plusMinutes(duration);
                    if (end.plusMinutes(request.getCleaningMinutes()).isAfter(lastAllowedEnd)) continue;
                    Prediction prediction = predict(movie.getId(), hall.getId(), start, context);
                    long audience = Math.round(prediction.rate() * capacity);
                    double revenue = audience * prices.getOrDefault(movie.getId(), 45.0);
                    result.add(new OptimizationCandidate(movie.getId(), movie.getTitle(), hall.getId(), hall.getName(),
                            start, end, duration, capacity, prediction.rate(), audience, revenue,
                            prices.getOrDefault(movie.getId(), 45.0), prediction.confidence()));
                }
            }
        }
        return result;
    }

    /**
     * 将收入按候选最大收入归一化，上座率按比例缩放，取整至千分尺度后加权。
     * 返回整数系数供 CP-SAT 使用；影片多样性通过独立的影片启用变量计分。
     */
    private long candidateObjective(OptimizationCandidate candidate, double maxRevenue,
                                    SchedulingOptimizationRequest request) {
        long revenueScore = Math.round(candidate.expectedRevenue() / Math.max(1, maxRevenue) * 1_000);
        long occupancyScore = Math.round(candidate.predictedRate() * 1_000);
        return request.getRevenueWeight() * revenueScore + request.getOccupancyWeight() * occupancyScore;
    }

    /** 对候选的选中变量求和，表示选中场次数；空集合转换为常数零。 */
    private LinearExpr sumVariables(Collection<CandidateDecision> decisions) {
        if (decisions.isEmpty()) return LinearExpr.constant(0);
        return LinearExpr.sum(decisions.stream().map(CandidateDecision::selected)
                .toArray(LinearArgument[]::new));
    }

    /** 将候选转换为方案项，来源场次 ID 为空，表示尚未写入数据库的新安排。 */
    private PlanItem planItem(OptimizationCandidate candidate, int cleaningMinutes) {
        return new PlanItem(null, candidate.movieId(), candidate.movieTitle(), candidate.hallId(),
                candidate.hallName(), candidate.startTime(), candidate.endTime(), candidate.durationMinutes(),
                cleaningMinutes, candidate.capacity(), candidate.predictedRate(), candidate.expectedAudience(),
                candidate.expectedRevenue(), candidate.unitPrice(), candidate.confidence(), false);
    }

    /** 将已有场次转换为带统一预测指标的方案项；影片、影厅或开场时间缺失时跳过该场次。 */
    private PlanItem planItem(Showtime showtime, ModelContext context, int cleaningMinutes, boolean locked) {
        Movie movie = context.movies().get(showtime.getMovieId());
        Hall hall = context.halls().get(showtime.getHallId());
        if (movie == null || hall == null || showtime.getStartTime() == null) return null;
        int duration = movie.getDuration() == null ? 120 : movie.getDuration();
        LocalDateTime end = showtime.getEndTime() == null ? showtime.getStartTime().plusMinutes(duration)
                : showtime.getEndTime();
        Prediction prediction = predict(movie.getId(), hall.getId(), showtime.getStartTime(), context);
        int seats = capacity(showtime, hall);
        long audience = Math.round(prediction.rate() * seats);
        double price = money(showtime.getBasePrice());
        if (price <= 0) price = 45;
        return new PlanItem(showtime.getId(), movie.getId(), movie.getTitle(), hall.getId(), hall.getName(),
                showtime.getStartTime(), end, (int) Math.max(1, Duration.between(showtime.getStartTime(), end).toMinutes()),
                cleaningMinutes, seats, prediction.rate(), audience, audience * price, price,
                prediction.confidence(), locked);
    }

    /**
     * 从锁定场次出发，按影厅逐次选取效用最高且不冲突、不超单片上限的候选，作为对比基线。
     * 该贪心过程不强制满足最低影片数和黄金时段数量，选不到候选时也可能未达到每厅目标。
     */
    private List<PlanItem> heuristicPlan(List<OptimizationCandidate> candidates, List<PlanItem> lockedPlan,
                                         SchedulingOptimizationRequest request, Map<Long, Integer> targetByHall,
                                         Map<Long, Integer> initialMovieCounts) {
        List<PlanItem> result = new ArrayList<>(lockedPlan);
        Set<OptimizationCandidate> selected = new HashSet<>();
        Map<Long, Integer> movieCounts = new HashMap<>(initialMovieCounts);
        Set<Long> usedMovies = result.stream().map(PlanItem::movieId).collect(Collectors.toSet());
        for (Map.Entry<Long, Integer> target : targetByHall.entrySet()) {
            while (result.stream().filter(item -> Objects.equals(item.hallId(), target.getKey())).count() < target.getValue()) {
                OptimizationCandidate best = candidates.stream()
                        .filter(candidate -> Objects.equals(candidate.hallId(), target.getKey()))
                        .filter(candidate -> !selected.contains(candidate))
                        .filter(candidate -> movieCounts.getOrDefault(candidate.movieId(), 0)
                                < Math.max(request.getMaxShowtimesPerMovie(), initialMovieCounts.getOrDefault(candidate.movieId(), 0)))
                        .filter(candidate -> result.stream().noneMatch(item -> overlaps(item, candidate,
                                request.getCleaningMinutes())))
                        .max(Comparator.comparingDouble(candidate -> heuristicUtility(candidate, usedMovies, request)))
                        .orElse(null);
                if (best == null) break;
                selected.add(best);
                result.add(planItem(best, request.getCleaningMinutes()));
                movieCounts.merge(best.movieId(), 1, Integer::sum);
                usedMovies.add(best.movieId());
            }
        }
        result.sort(Comparator.comparing(PlanItem::startTime).thenComparing(PlanItem::hallId));
        return result;
    }

    /** 贪心效用由收入、上座率和首次覆盖影片奖励构成，其分值尺度与 CP-SAT 目标函数不同。 */
    private double heuristicUtility(OptimizationCandidate candidate, Set<Long> usedMovies,
                                    SchedulingOptimizationRequest request) {
        double diversityBonus = usedMovies.contains(candidate.movieId()) ? 0 : request.getDiversityWeight() * 250;
        return candidate.expectedRevenue() * request.getRevenueWeight() / 100.0
                + candidate.predictedRate() * 10_000 * request.getOccupancyWeight() / 100.0 + diversityBonus;
    }

    /** 判断同厅两个场次加上各自清场时间后是否重叠，恰好首尾衔接时允许安排。 */
    private boolean overlaps(PlanItem existing, OptimizationCandidate candidate, int cleaningMinutes) {
        if (!Objects.equals(existing.hallId(), candidate.hallId())) return false;
        return existing.startTime().isBefore(candidate.endTime().plusMinutes(cleaningMinutes))
                && candidate.startTime().isBefore(existing.endTime().plusMinutes(existing.cleaningMinutes()));
    }

    /** 按指定字段分组计数，用于统计每厅、每片及黄金时段场次数。 */
    private <K> Map<K, Integer> countsBy(List<PlanItem> items, Function<PlanItem, K> classifier) {
        return items.stream().collect(Collectors.groupingBy(classifier, Collectors.summingInt(ignored -> 1)));
    }

    /** 汇总方案收入、观众、影片数和约束满足率；整体上座率按预计观众总数除以总容量计算。 */
    private PlanMetrics planMetrics(String method, List<PlanItem> plan, long calculationTimeMs,
                                    SchedulingOptimizationRequest request, Map<Long, Integer> targetByHall) {
        double revenue = plan.stream().mapToDouble(PlanItem::expectedRevenue).sum();
        long audience = plan.stream().mapToLong(PlanItem::expectedAudience).sum();
        long capacity = plan.stream().mapToLong(PlanItem::capacity).sum();
        double occupancy = capacity == 0 ? 0 : audience / (double) capacity;
        int distinct = (int) plan.stream().map(PlanItem::movieId).distinct().count();
        double satisfaction = constraintSatisfaction(plan, request, targetByHall);
        return new PlanMetrics(method, plan.size(), revenue, audience, occupancy, distinct,
                calculationTimeMs, satisfaction);
    }

    /**
     * 对不重叠、每厅目标、单片上限、最低影片数、黄金场次五类约束分别整体评分。
     * 每类全部通过计 20 分，返回 0～100 的满足率，用于方案对比。
     */
    private double constraintSatisfaction(List<PlanItem> plan, SchedulingOptimizationRequest request,
                                          Map<Long, Integer> targetByHall) {
        int passed = 0;
        boolean noOverlap = true;
        for (List<PlanItem> hallPlan : plan.stream().collect(Collectors.groupingBy(PlanItem::hallId)).values()) {
            List<PlanItem> ordered = hallPlan.stream().sorted(Comparator.comparing(PlanItem::startTime)).toList();
            for (int index = 1; index < ordered.size(); index++) {
                if (ordered.get(index).startTime().isBefore(ordered.get(index - 1).endTime()
                        .plusMinutes(ordered.get(index - 1).cleaningMinutes()))) noOverlap = false;
            }
        }
        if (noOverlap) passed++;
        Map<Long, Integer> hallCounts = countsBy(plan, PlanItem::hallId);
        if (targetByHall.entrySet().stream().allMatch(entry -> hallCounts.getOrDefault(entry.getKey(), 0)
                == entry.getValue())) passed++;
        Map<Long, List<PlanItem>> plansByMovie = plan.stream().collect(Collectors.groupingBy(PlanItem::movieId));
        if (plansByMovie.values().stream().allMatch(items -> {
            long lockedCount = items.stream().filter(PlanItem::locked).count();
            return items.size() <= Math.max(request.getMaxShowtimesPerMovie(), lockedCount);
        })) passed++;
        if (plan.stream().map(PlanItem::movieId).distinct().count() >= request.getMinDistinctMovies()) passed++;
        Map<Long, Integer> goldenCounts = countsBy(plan.stream().filter(item -> isGolden(item.startTime())).toList(),
                PlanItem::hallId);
        if (targetByHall.keySet().stream().allMatch(hallId -> goldenCounts.getOrDefault(hallId, 0)
                >= request.getMinGoldenShowtimesPerHall())) passed++;
        return passed * 20.0;
    }

    /** 以开场时间判断黄金时段，范围为 18:00（含）至 22:00（不含）。 */
    private boolean isGolden(LocalDateTime startTime) {
        LocalTime time = startTime.toLocalTime();
        return !time.isBefore(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(22, 0));
    }

    /** 输出方案明细，将上座率转换为百分数，并将收入及票价保留两位小数。 */
    private Map<String, Object> planItemMap(PlanItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceShowtimeId", item.sourceShowtimeId());
        result.put("movieId", item.movieId());
        result.put("movieTitle", item.movieTitle());
        result.put("hallId", item.hallId());
        result.put("hallName", item.hallName());
        result.put("startTime", item.startTime());
        result.put("endTime", item.endTime());
        result.put("durationMinutes", item.durationMinutes());
        result.put("cleaningMinutes", item.cleaningMinutes());
        result.put("predictedRate", percent(item.predictedRate()));
        result.put("expectedAudience", item.expectedAudience());
        result.put("expectedRevenue", BigDecimal.valueOf(item.expectedRevenue()).setScale(2, RoundingMode.HALF_UP));
        result.put("unitPrice", BigDecimal.valueOf(item.unitPrice()).setScale(2, RoundingMode.HALF_UP));
        result.put("confidence", item.confidence());
        result.put("locked", item.locked());
        return result;
    }

    /** 将方案指标转换为接口字段，上座率输出百分数，约束满足率直接使用已有百分制数值。 */
    private Map<String, Object> metricsMap(PlanMetrics metrics) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", metrics.method());
        result.put("showtimeCount", metrics.showtimeCount());
        result.put("expectedRevenue", BigDecimal.valueOf(metrics.expectedRevenue()).setScale(2, RoundingMode.HALF_UP));
        result.put("expectedAudience", metrics.expectedAudience());
        result.put("occupancyRate", percent(metrics.occupancyRate()));
        result.put("distinctMovies", metrics.distinctMovies());
        result.put("calculationTimeMs", metrics.calculationTimeMs());
        result.put("constraintSatisfactionRate", metrics.constraintSatisfactionRate());
        return result;
    }

    /** 计算相对基线的提升百分比；基线不为正时返回空值，避免给出无意义的增长率。 */
    private Double liftPercent(double optimized, double baseline) {
        return baseline <= 0 ? null : round((optimized - baseline) / baseline * 100);
    }

    /** 在无法取得可行解时返回原因、人工及启发式基线和 AI 约束分析，选中场次列表为空。 */
    private Map<String, Object> infeasibleOptimizationResult(SchedulingOptimizationRequest request,
                                                              List<PlanItem> manualPlan,
                                                              List<PlanItem> heuristicPlan,
                                                              ModelContext context,
                                                              Map<Long, Integer> targetByHall,
                                                              String reason, long startedAt) {
        PlanMetrics manual = planMetrics("人工现状", manualPlan, 0, request, targetByHall);
        PlanMetrics heuristic = planMetrics("加权启发式", heuristicPlan,
                (System.nanoTime() - startedAt) / 1_000_000, request, targetByHall);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "INFEASIBLE");
        result.put("statusText", "当前约束下没有可行方案");
        result.put("reason", reason);
        result.put("solver", "Google OR-Tools CP-SAT 9.11");
        result.put("targetDate", request.getTargetDate());
        result.put("selectedShowtimes", List.of());
        result.put("comparisons", List.of(metricsMap(manual), metricsMap(heuristic)));
        result.put("notice", "请调整影片范围、目标场次数、单片上限或最低影片数后重试");
        return withAi("无可行排片方案的约束分析", result, context);
    }

    /** 构造单场预警明细，并在存在明显更优影片时附上同厅同时段的换片建议。 */
    private Map<String, Object> lowOccupancyItem(Showtime showtime, ModelContext context) {
        Prediction prediction = predict(showtime.getMovieId(), showtime.getHallId(), showtime.getStartTime(), context);
        Movie movie = context.movies().get(showtime.getMovieId());
        Hall hall = context.halls().get(showtime.getHallId());
        Alternative alternative = bestAlternative(showtime, prediction.rate(), context);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("showtimeId", showtime.getId());
        item.put("movieId", showtime.getMovieId());
        item.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
        item.put("hallId", showtime.getHallId());
        item.put("hallName", hall == null ? "未知影厅" : hall.getName());
        item.put("startTime", showtime.getStartTime());
        item.put("predictedRate", percent(prediction.rate()));
        item.put("confidence", prediction.confidence());
        item.put("reason", prediction.drivers().isEmpty() ? "综合需求低于预警线" : prediction.drivers().get(0).get("description"));
        if (alternative != null) {
            item.put("recommendedMovieId", alternative.movieId());
            item.put("recommendedMovieTitle", alternative.title());
            item.put("recommendedRate", percent(alternative.rate()));
            item.put("potentialLift", round((alternative.rate() - prediction.rate()) * 100));
        }
        return item;
    }

    /** 在容量最大的可用影厅和统一黄金参考时间下比较上映影片，返回预测上座率最高的六部。 */
    private List<Map<String, Object>> buildGoldenRecommendations(ModelContext context, Long cinemaId) {
        if (context.movies().isEmpty() || context.halls().isEmpty()) {
            return List.of();
        }
        Hall referenceHall = context.halls().values().stream()
                .max(Comparator.comparingInt(h -> h.getTotalSeats() == null ? 0 : h.getTotalSeats()))
                .orElseThrow();
        LocalDateTime goldenTime = nextGoldenTime();

        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1))
                .map(movie -> {
                    Prediction prediction = predict(movie.getId(), referenceHall.getId(), goldenTime, context);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("movieId", movie.getId());
                    item.put("movieTitle", movie.getTitle());
                    item.put("rating", movie.getRating());
                    item.put("recommendedTime", goldenTime);
                    item.put("timeLabel", "周五/周末 19:00-22:00");
                    item.put("hallId", referenceHall.getId());
                    item.put("hallName", referenceHall.getName());
                    item.put("predictedRate", percent(prediction.rate()));
                    item.put("confidence", prediction.confidence());
                    item.put("reason", prediction.drivers().isEmpty() ? "综合表现稳定" : prediction.drivers().get(0).get("description"));
                    return item;
                })
                .sorted((a, b) -> Double.compare(number(b.get("predictedRate")), number(a.get("predictedRate"))))
                .limit(6)
                .toList();
    }

    /** 根据同片历史上座率的平滑估计和未来七天已排场次数，给出增场、减场或维持建议。 */
    private List<Map<String, Object>> buildFrequencyRecommendations(ModelContext context) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> scheduledCounts = context.showtimes().stream()
                .filter(s -> Objects.equals(s.getStatus(), 0) && s.getStartTime() != null)
                .filter(s -> s.getStartTime().isAfter(now) && s.getStartTime().isBefore(now.plusDays(7)))
                .collect(Collectors.groupingBy(Showtime::getMovieId, Collectors.counting()));

        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1))
                .map(movie -> {
                    Aggregate demand = context.movieStats().get(movie.getId());
                    double expected = smoothed(demand, context.globalOccupancy(), 3);
                    long current = scheduledCounts.getOrDefault(movie.getId(), 0L);
                    // 需求达到 68% 时建议增场；不高于 34% 且已有未来排片时才建议减场。
                    String action = expected >= 0.68 ? "ADD" : expected <= 0.34 && current > 0 ? "REDUCE" : "KEEP";
                    int change = "ADD".equals(action) ? Math.max(1, (int) Math.ceil((expected - 0.60) * 10)) :
                            "REDUCE".equals(action) ? -Math.max(1, (int) Math.ceil((0.42 - expected) * 8)) : 0;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("movieId", movie.getId());
                    item.put("movieTitle", movie.getTitle());
                    item.put("historicalRate", demand == null || demand.count() == 0 ? null : percent(demand.average()));
                    item.put("estimatedRate", percent(expected));
                    item.put("historicalSamples", demand == null ? 0 : demand.count());
                    item.put("currentWeeklyShowtimes", current);
                    item.put("action", action);
                    item.put("suggestedChange", change);
                    item.put("reason", frequencyReason(action, expected, demand == null ? 0 : demand.count()));
                    return item;
                })
                .sorted((a, b) -> {
                    int actionCompare = actionPriority(String.valueOf(a.get("action"))) - actionPriority(String.valueOf(b.get("action")));
                    return actionCompare != 0 ? actionCompare : Double.compare(number(b.get("historicalRate")), number(a.get("historicalRate")));
                })
                .limit(12)
                .toList();
    }

    /** 输出影厅、星期、时段三个维度的历史平均上座率；无样本的格子保留空值，不填预测值。 */
    private List<Map<String, Object>> buildHeatmap(ModelContext context) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Hall hall : context.halls().values()) {
            for (int day = 1; day <= 7; day++) {
                for (int slot = 0; slot < 4; slot++) {
                    Aggregate aggregate = context.heatStats().get(heatKey(hall.getId(), day, slot));
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("hallId", hall.getId());
                    point.put("hallName", hall.getName());
                    point.put("weekday", day);
                    point.put("weekdayLabel", weekdayLabel(day));
                    point.put("timeSlot", slot);
                    point.put("timeSlotLabel", slotLabel(slot));
                    point.put("occupancyRate", aggregate == null || aggregate.count() == 0 ? null : percent(aggregate.average()));
                    point.put("sampleCount", aggregate == null ? 0 : aggregate.count());
                    result.add(point);
                }
            }
        }
        return result;
    }

    /**
     * 按时间先后将历史场次约按 80%／20% 分为统计样本和测试样本，评估本地预测算法。
     * 测试场次不参与预测所用的历史聚合统计；不足三场时不划分测试集，误差字段返回空值。
     */
    private Map<String, Object> buildEvaluation(ModelContext context) {
        List<Showtime> ordered = context.historical().stream()
                .sorted(Comparator.comparing(Showtime::getStartTime)).toList();
        int splitIndex = ordered.size() < 3 ? ordered.size() : Math.max(1, (int) Math.floor(ordered.size() * .8));
        List<Showtime> training = ordered.subList(0, splitIndex);
        List<Showtime> testing = ordered.subList(splitIndex, ordered.size());
        // 仅用前段样本重建全局基准和各维度统计，后段样本只用于与预测结果比较。
        ModelContext trainingContext = rebuildContext(context.showtimes(), training, context.movies(),
                context.halls(), context.soldSeats());
        // MAE 为绝对误差均值，RMSE 为均方根误差，后者对较大的预测偏差更敏感。
        double mae = testing.stream().mapToDouble(s -> Math.abs(actualRate(s, context.soldSeats())
                - predict(s.getMovieId(), s.getHallId(), s.getStartTime(), trainingContext).rate()))
                .average().orElse(0);
        double rmse = Math.sqrt(testing.stream().mapToDouble(s -> {
            double error = actualRate(s, context.soldSeats())
                    - predict(s.getMovieId(), s.getHallId(), s.getStartTime(), trainingContext).rate();
            return error * error;
        }).average().orElse(0));
        // MAPE 按实际上座率计算相对误差，排除实际值低于 5% 的样本，避免小分母放大误差。
        double mape = testing.stream().mapToDouble(s -> {
            double actual = actualRate(s, context.soldSeats());
            if (actual < .05) return Double.NaN;
            return Math.abs(actual - predict(s.getMovieId(), s.getHallId(), s.getStartTime(), trainingContext).rate()) / actual;
        }).filter(value -> !Double.isNaN(value)).average().orElse(0);
        // 基线对所有测试场次统一使用训练集全局上座率，便于衡量加权模型的误差改善。
        double baselineMae = testing.stream().mapToDouble(s -> Math.abs(
                actualRate(s, context.soldSeats()) - trainingContext.globalOccupancy())).average().orElse(0);
        List<Map<String, Object>> comparisons = testing.stream().limit(8).map(s -> {
            Movie movie = context.movies().get(s.getMovieId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("showtimeId", s.getId());
            item.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
            item.put("startTime", s.getStartTime());
            item.put("predictedRate", percent(predict(s.getMovieId(), s.getHallId(), s.getStartTime(), trainingContext).rate()));
            item.put("actualRate", percent(actualRate(s, context.soldSeats())));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        // MAE、RMSE 及基线误差转换为上座率百分点；MAPE 转换为相对误差百分比。
        result.put("meanAbsoluteError", testing.isEmpty() ? null : percent(mae));
        result.put("rootMeanSquaredError", testing.isEmpty() ? null : percent(rmse));
        result.put("meanAbsolutePercentageError", testing.isEmpty() ? null : percent(mape));
        result.put("baselineMeanAbsoluteError", testing.isEmpty() ? null : percent(baselineMae));
        result.put("maeImprovementVsGlobalBaseline", testing.isEmpty() ? null : percent(baselineMae - mae));
        result.put("trainingSampleCount", training.size());
        result.put("testSampleCount", testing.size());
        result.put("splitTime", testing.isEmpty() ? null : testing.getFirst().getStartTime());
        result.put("protocol", "按开场时间排序，前 80% 构建模型、后 20% 只用于测试；测试场次不参与自身预测");
        result.put("comparisons", comparisons);
        return result;
    }

    /**
     * 用六个固定权重因素计算上座率，并返回相对历史基准影响最大的三个因素。
     * 历史不足的分组通过先验平滑回退到全局基准，最终预测限制在 8%～95%。
     */
    private Prediction predict(Long movieId, Long hallId, LocalDateTime startTime, ModelContext context) {
        double baseline = context.globalOccupancy();
        Aggregate movieAggregate = context.movieStats().get(movieId);
        double movieHistory = smoothed(movieAggregate, baseline, 3);
        long movieTickets = context.movieTickets().getOrDefault(movieId, 0L);
        // 票数相对最热门影片归一化后开平方，再与同片历史上座率按 1:3 混合形成需求分。
        double popularity = context.maxMovieTickets() == 0 ? baseline : Math.sqrt((double) movieTickets / context.maxMovieTickets());
        double movieDemand = movieHistory * 0.75 + popularity * 0.25;
        // 星期和时段统计各引入五个先验样本，减弱少量历史场次造成的波动。
        double weekday = smoothed(context.weekdayStats().get(startTime.getDayOfWeek().getValue()), baseline, 5);
        double slot = smoothed(context.slotStats().get(slot(startTime.toLocalTime())), baseline, 5);
        Movie movie = context.movies().get(movieId);
        double rating = movie == null || movie.getRating() == null ? 0.65 : clamp(movie.getRating().doubleValue() / 10, 0.30, 0.98);
        double freshness = freshness(movie, startTime.toLocalDate());
        Aggregate hallAggregate = context.hallStats().get(hallId);
        Hall hall = context.halls().get(hallId);
        double hallHistory = smoothed(hallAggregate, baseline, 4);
        // 影厅匹配结合历史表现与容量因素，容量超过 120 座后逐步降低容量项得分。
        double capacityFit = hall == null || hall.getTotalSeats() == null ? hallHistory :
                hallHistory * 0.75 + clamp(1 - Math.max(0, hall.getTotalSeats() - 120) / 400.0, 0.35, 1) * 0.25;

        // 影片热度、星期、时段、评分、新鲜度、影厅匹配的权重依次为 35%、18%、20%、10%、9%、8%。
        double rate = movieDemand * 0.35 + weekday * 0.18 + slot * 0.20 + rating * 0.10
                + freshness * 0.09 + capacityFit * 0.08;
        rate = clamp(rate, 0.08, 0.95);

        List<Map<String, Object>> drivers = new ArrayList<>();
        addDriver(drivers, "影片热度", movieDemand, baseline, 0.35);
        addDriver(drivers, "星期客流", weekday, baseline, 0.18);
        addDriver(drivers, "时段客流", slot, baseline, 0.20);
        addDriver(drivers, "影片评分", rating, baseline, 0.10);
        addDriver(drivers, "上映新鲜度", freshness, baseline, 0.09);
        addDriver(drivers, "影厅匹配", capacityFit, baseline, 0.08);
        drivers.sort((a, b) -> Double.compare(Math.abs(number(b.get("impact"))), Math.abs(number(a.get("impact")))));

        // 以同片和同星期样本数之和估计证据量，并以历史总场次为上限，映射为置信等级。
        int evidence = (movieAggregate == null ? 0 : movieAggregate.count())
                + (context.weekdayStats().get(startTime.getDayOfWeek().getValue()) == null ? 0 : context.weekdayStats().get(startTime.getDayOfWeek().getValue()).count());
        return new Prediction(rate, confidence(Math.min(context.historical().size(), evidence)), drivers.stream().limit(3).toList());
    }

    /** 组装单场预测结果，补充影片、影厅名称，并按影厅容量估算观众数。 */
    private Map<String, Object> predictionResult(Long movieId, Long hallId, LocalDateTime startTime,
                                                  Prediction prediction, ModelContext context) {
        Movie movie = context.movies().get(movieId);
        Hall hall = context.halls().get(hallId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movieId", movieId);
        result.put("movieTitle", movie == null ? "未知影片" : movie.getTitle());
        result.put("hallId", hallId);
        result.put("hallName", hall == null ? "未知影厅" : hall.getName());
        result.put("startTime", startTime);
        result.put("predictedRate", percent(prediction.rate()));
        result.put("confidence", prediction.confidence());
        result.put("expectedAudience", Math.round(prediction.rate() * (hall == null || hall.getTotalSeats() == null ? 0 : hall.getTotalSeats())));
        result.put("drivers", prediction.drivers());
        return result;
    }

    /** 在同厅、同开场时间下寻找其他上映影片，仅返回预计上座率提升超过 5 个百分点的最佳候选。 */
    private Alternative bestAlternative(Showtime showtime, double currentRate, ModelContext context) {
        return context.movies().values().stream()
                .filter(m -> Objects.equals(m.getStatus(), 1) && !Objects.equals(m.getId(), showtime.getMovieId()))
                .map(m -> new Alternative(m.getId(), m.getTitle(),
                        predict(m.getId(), showtime.getHallId(), showtime.getStartTime(), context).rate()))
                .filter(a -> a.rate() > currentRate + 0.05)
                .max(Comparator.comparingDouble(Alternative::rate))
                .orElse(null);
    }

    /** 将本地计算结果及历史样本范围、统计口径交给 AI 分析，并把分析内容附加到响应中。 */
    private Map<String, Object> withAi(String task, Map<String, Object> result, ModelContext context) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("source", "database");
        basis.put("historicalSamples", context.historical().size());
        basis.put("historicalFrom", context.historical().stream().map(Showtime::getStartTime).min(LocalDateTime::compareTo).orElse(null));
        basis.put("historicalTo", context.historical().stream().map(Showtime::getEndTime).max(LocalDateTime::compareTo).orElse(null));
        basis.put("historicalAverageRate", context.historical().isEmpty() ? null : percent(context.historical().stream()
                .mapToDouble(s -> actualRate(s, context.soldSeats())).average().orElse(0)));
        basis.put("hallCount", context.halls().size());
        basis.put("activeMovieCount", context.movies().values().stream().filter(m -> Objects.equals(m.getStatus(), 1)).count());
        basis.put("coldStart", context.historical().isEmpty());
        basis.put("occupancyBasis", "已结束且未取消场次；售票数取已支付订单票数与场次座位台账占用数的较大值，并限制在容量内");
        basis.put("predictionBasis", "可解释加权算法估计；无历史样本时使用先验，不能视作实测数据。MAE仅评估本地预测算法。");
        result.put("aiAnalysis", schedulingAiService.analyze(task, result, basis));
        return result;
    }

    /**
     * 从数据库读取未取消场次、已支付订单、相关影片及可用影厅，构建一次计算所用的上下文。
     * 历史样本仅保留已经结束且场次容量有效的记录；影院 ID 为空时不限定影院范围。
     */
    private ModelContext loadContext(Long cinemaId) {
        LambdaQueryWrapper<Showtime> showtimeQuery = new LambdaQueryWrapper<Showtime>()
                .ne(Showtime::getStatus, 2)
                .orderByDesc(Showtime::getStartTime);
        if (cinemaId != null) {
            showtimeQuery.eq(Showtime::getCinemaId, cinemaId);
        }
        List<Showtime> showtimes = showtimeMapper.selectList(showtimeQuery);
        Set<Long> showtimeIds = showtimes.stream().map(Showtime::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<TicketOrder> orders = showtimeIds.isEmpty() ? List.of() : orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 1).in(TicketOrder::getShowtimeId, showtimeIds));

        // 先汇总已支付订单票数，再与场次座位台账占用数取较大值，避免重复相加。
        Map<Long, Integer> soldSeats = new HashMap<>();
        for (TicketOrder order : orders) {
            soldSeats.merge(order.getShowtimeId(), order.getTicketCount() == null ? 0 : order.getTicketCount(), Integer::sum);
        }
        for (Showtime showtime : showtimes) {
            int storedSold = showtime.getTotalSeats() == null || showtime.getAvailableSeats() == null ? 0 :
                    Math.max(0, showtime.getTotalSeats() - showtime.getAvailableSeats());
            soldSeats.merge(showtime.getId(), storedSold, Math::max);
        }

        // 上映影片用于推荐和候选生成，同时补齐历史场次引用的影片，保留历史评估所需资料。
        List<Movie> activeMovies = movieMapper.selectList(new LambdaQueryWrapper<Movie>().eq(Movie::getStatus, 1));
        Set<Long> movieIds = showtimes.stream().map(Showtime::getMovieId).collect(Collectors.toSet());
        activeMovies.forEach(m -> movieIds.add(m.getId()));
        Map<Long, Movie> movies = activeMovies.stream().collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
        if (!movieIds.isEmpty()) {
            movieMapper.selectBatchIds(movieIds).forEach(m -> movies.put(m.getId(), m));
        }

        LambdaQueryWrapper<Hall> hallQuery = new LambdaQueryWrapper<Hall>().eq(Hall::getStatus, 1);
        if (cinemaId != null) {
            hallQuery.eq(Hall::getCinemaId, cinemaId);
        }
        Map<Long, Hall> halls = hallMapper.selectList(hallQuery).stream()
                .collect(Collectors.toMap(Hall::getId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<Showtime> historical = showtimes.stream()
                .filter(s -> s.getStartTime() != null && s.getEndTime() != null && s.getEndTime().isBefore(now))
                .filter(s -> s.getTotalSeats() != null && s.getTotalSeats() > 0)
                .toList();
        return rebuildContext(showtimes, historical, movies, halls, soldSeats);
    }

    /**
     * 根据指定历史子集重建影片、星期、时段、影厅及热力图统计，供正常预测和时间切分评估复用。
     * 传入的场次、影片和座位映射作为基础资料保存，预测聚合值仅由 historical 子集计算。
     */
    private ModelContext rebuildContext(List<Showtime> showtimes, List<Showtime> historical,
                                        Map<Long, Movie> movies, Map<Long, Hall> halls,
                                        Map<Long, Integer> soldSeats) {
        // 全局先验对各历史场次的上座率取算术平均，并限制在 15%～85%，避免极端基准。
        double global = historical.stream().mapToDouble(s -> actualRate(s, soldSeats)).average().orElse(DEFAULT_OCCUPANCY);
        global = clamp(global, 0.15, 0.85);

        Map<Long, Aggregate> movieStats = new HashMap<>();
        Map<Integer, Aggregate> weekdayStats = new HashMap<>();
        Map<Integer, Aggregate> slotStats = new HashMap<>();
        Map<Long, Aggregate> hallStats = new HashMap<>();
        Map<String, Aggregate> heatStats = new HashMap<>();
        Map<Long, Long> movieTickets = new HashMap<>();
        for (Showtime showtime : historical) {
            double actual = actualRate(showtime, soldSeats);
            add(movieStats, showtime.getMovieId(), actual);
            int weekday = showtime.getStartTime().getDayOfWeek().getValue();
            int timeSlot = slot(showtime.getStartTime().toLocalTime());
            add(weekdayStats, weekday, actual);
            add(slotStats, timeSlot, actual);
            add(hallStats, showtime.getHallId(), actual);
            add(heatStats, heatKey(showtime.getHallId(), weekday, timeSlot), actual);
            movieTickets.merge(showtime.getMovieId(), (long) soldSeats.getOrDefault(showtime.getId(), 0), Long::sum);
        }
        long maxMovieTickets = movieTickets.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return new ModelContext(showtimes, historical, movies, halls, soldSeats, movieStats, weekdayStats,
                slotStats, hallStats, heatStats, movieTickets, maxMovieTickets, global);
    }

    /** 校验预测或模拟目标的影片、影厅存在，且影厅属于所选影院。 */
    private void validateTarget(Long movieId, Long cinemaId, Long hallId) {
        Movie movie = movieMapper.selectById(movieId);
        if (movie == null) throw new BusinessException("影片不存在");
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new BusinessException("影厅不存在");
        if (!Objects.equals(hall.getCinemaId(), cinemaId)) throw new BusinessException("影厅不属于所选影院");
    }

    /** 返回前端展示的模型名称、固定权重及预警阈值，权重采用百分制。 */
    private Map<String, Object> modelDescription() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", "可解释加权上座率模型 v1");
        model.put("weights", Map.of("影片热度", 35, "时段", 20, "星期", 18, "影片评分", 10, "上映天数", 9, "影厅匹配", 8));
        model.put("lowOccupancyThreshold", 35);
        model.put("training", "实时读取已支付历史订单，无需离线训练");
        return model;
    }

    /** 取今天起首个周五或周六的 19:30 作为统一推荐参考时间，允许选中当天。 */
    private LocalDateTime nextGoldenTime() {
        LocalDate date = LocalDate.now();
        while (date.getDayOfWeek() != DayOfWeek.FRIDAY && date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return LocalDateTime.of(date, LocalTime.of(19, 30));
    }

    /** 按目标日期距上映日的天数分段赋予新鲜度得分，资料缺失或尚未上映时使用预设值。 */
    private double freshness(Movie movie, LocalDate targetDate) {
        if (movie == null || movie.getReleaseDate() == null) return 0.60;
        long days = ChronoUnit.DAYS.between(movie.getReleaseDate(), targetDate);
        if (days < 0) return 0.55;
        if (days <= 14) return 0.92;
        if (days <= 30) return 0.82;
        if (days <= 90) return 0.68;
        if (days <= 180) return 0.58;
        return 0.48;
    }

    /** 以“因素分数与基准之差 × 权重”计算解释性影响，转换为百分点并标记拉升或拖累方向。 */
    private void addDriver(List<Map<String, Object>> drivers, String name, double score, double baseline, double weight) {
        double impact = (score - baseline) * weight * 100;
        String direction = impact >= 0 ? "UP" : "DOWN";
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", name);
        driver.put("score", percent(score));
        driver.put("weight", round(weight * 100));
        driver.put("impact", round(impact));
        driver.put("direction", direction);
        driver.put("description", name + (impact >= 0 ? "高于" : "低于") + "历史基准，预计" + (impact >= 0 ? "拉升" : "拖累") + "上座率");
        drivers.add(driver);
    }

    /** 为指定统计分组累加上座率及样本数。 */
    private static <K> void add(Map<K, Aggregate> map, K key, double value) {
        map.compute(key, (ignored, current) -> current == null ? new Aggregate(value, 1) : current.add(value));
    }

    /** 按场次总座位数计算实际上座率，并限制在 0～100%，容量无效时返回零。 */
    private double actualRate(Showtime showtime, Map<Long, Integer> soldSeats) {
        if (showtime.getTotalSeats() == null || showtime.getTotalSeats() <= 0) return 0;
        return clamp((double) soldSeats.getOrDefault(showtime.getId(), 0) / showtime.getTotalSeats(), 0, 1);
    }

    /** 优先使用场次记录的有效容量，缺失时回退到影厅容量。 */
    private int capacity(Showtime showtime, Hall hall) {
        if (showtime.getTotalSeats() != null && showtime.getTotalSeats() > 0) return showtime.getTotalSeats();
        return hall == null || hall.getTotalSeats() == null ? 0 : hall.getTotalSeats();
    }

    /**
     * 将先验视为若干虚拟样本，按（历史上座率之和 + 先验 × 先验样本数）／总样本数平滑。
     * 历史样本越少越接近先验，没有该分组历史时直接返回先验。
     */
    private double smoothed(Aggregate aggregate, double prior, int priorSamples) {
        if (aggregate == null) return prior;
        return (aggregate.sum() + prior * priorSamples) / (aggregate.count() + priorSamples);
    }

    /** 以 12:00、18:00、22:00 为分界映射到四个时段；当前实现将 00:00～12:00 归入第零组。 */
    private int slot(LocalTime time) {
        if (time.isBefore(LocalTime.NOON)) return 0;
        if (time.isBefore(LocalTime.of(18, 0))) return 1;
        if (time.isBefore(LocalTime.of(22, 0))) return 2;
        return 3;
    }

    /** 拼接影厅、星期和时段编号，作为历史热力图的分组键。 */
    private String heatKey(Long hallId, int weekday, int slot) {
        return hallId + ":" + weekday + ":" + slot;
    }

    /** 将 1～7 的星期编号转换为中文名称，与 DayOfWeek 的编号一致。 */
    private String weekdayLabel(int day) {
        return List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日").get(day - 1);
    }

    /** 返回时段编号对应的页面展示标签。 */
    private String slotLabel(int slot) {
        return List.of("上午 06-12", "下午 12-18", "黄金 18-22", "深夜 22-06").get(slot);
    }

    /** 结合历史样本量和需求估计生成增减场建议理由，注明无同片样本时使用全局基准。 */
    private String frequencyReason(String action, double rate, int samples) {
        String evidence = samples == 0 ? "暂无同片历史，按全局基准估算" : "基于 " + samples + " 个历史场次";
        return switch (action) {
            case "ADD" -> evidence + "，需求旺盛（" + percent(rate) + "%），建议增加黄金时段供给";
            case "REDUCE" -> evidence + "，需求偏弱（" + percent(rate) + "%），建议减少非黄金场次";
            default -> evidence + "，供需暂时平衡，建议维持当前场次数";
        };
    }

    /** 建议列表按增场、减场、维持的顺序展示。 */
    private int actionPriority(String action) {
        return "ADD".equals(action) ? 0 : "REDUCE".equals(action) ? 1 : 2;
    }

    /** 按样本量给出经验置信等级：30 场起为高、8 场起为中，其余为低，不代表统计置信概率。 */
    private String confidence(int samples) {
        if (samples >= 30) return "高";
        if (samples >= 8) return "中";
        return "低";
    }

    /** 将金额转换为计算用数值，缺失金额按零处理。 */
    private double money(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    /** 提取接口字段中的数值，空值或非数值类型按零处理，供排序和解释指标计算使用。 */
    private double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    /** 将比例转换为保留一位小数的百分数。 */
    private double percent(double value) {
        return round(value * 100);
    }

    /** 将统计展示值四舍五入到一位小数。 */
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** 将数值限制在指定闭区间内。 */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 一组历史上座率的和与样本数，用于计算均值和平滑估计。 */
    private record Aggregate(double sum, int count) {
        Aggregate add(double value) { return new Aggregate(sum + value, count + 1); }
        double average() { return count == 0 ? 0 : sum / count; }
    }

    /** 单场预测比例、基于样本量的置信等级及主要影响因素。 */
    private record Prediction(double rate, String confidence, List<Map<String, Object>> drivers) {}
    /** 同厅同时段换片的备选影片及预计上座率。 */
    private record Alternative(Long movieId, String title, double rate) {}
    /** 可供优化选择的单场安排，包含时间、容量、预测收益和估计票价。 */
    private record OptimizationCandidate(Long movieId, String movieTitle, Long hallId, String hallName,
                                         LocalDateTime startTime, LocalDateTime endTime, int durationMinutes,
                                         int capacity, double predictedRate, long expectedAudience,
                                         double expectedRevenue, double unitPrice, String confidence) {}
    /** 候选场次与 CP-SAT 选中变量及整数目标系数的关联。 */
    private record CandidateDecision(OptimizationCandidate candidate, BoolVar selected, long objectiveScore) {}
    /** 用于评估及展示的统一方案项，保留原场次来源 ID 和模型内强制保留标记。 */
    private record PlanItem(Long sourceShowtimeId, Long movieId, String movieTitle, Long hallId, String hallName,
                            LocalDateTime startTime, LocalDateTime endTime, int durationMinutes,
                            int cleaningMinutes, int capacity, double predictedRate, long expectedAudience,
                            double expectedRevenue, double unitPrice, String confidence, boolean locked) {}
    /** 方案对比指标；上座率为 0～1 的比例，约束满足率为 0～100 的百分制数值。 */
    private record PlanMetrics(String method, int showtimeCount, double expectedRevenue, long expectedAudience,
                               double occupancyRate, int distinctMovies, long calculationTimeMs,
                               double constraintSatisfactionRate) {}
    /** 一次计算使用的业务资料、历史样本和分组统计，支持复用及按训练子集重建。 */
    private record ModelContext(List<Showtime> showtimes, List<Showtime> historical,
                                Map<Long, Movie> movies, Map<Long, Hall> halls,
                                Map<Long, Integer> soldSeats, Map<Long, Aggregate> movieStats,
                                Map<Integer, Aggregate> weekdayStats, Map<Integer, Aggregate> slotStats,
                                Map<Long, Aggregate> hallStats, Map<String, Aggregate> heatStats,
                                Map<Long, Long> movieTickets, long maxMovieTickets, double globalOccupancy) {}
}
