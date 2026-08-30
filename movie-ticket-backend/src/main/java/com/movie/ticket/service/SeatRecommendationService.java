package com.movie.ticket.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.SeatPreference;
import com.movie.ticket.dto.SeatRecommendationRequest;
import com.movie.ticket.entity.Hall;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.vo.SeatRecommendationVO;
import com.movie.ticket.vo.SeatRecommendationResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeatRecommendationService {

    private final ShowtimeMapper showtimeMapper;
    private final HallMapper hallMapper;
    private final TicketOrderMapper orderMapper;
    private final SeatAiRecommendationService seatAiRecommendationService;
    private final SeatRecommendationEngine engine = new SeatRecommendationEngine();

    public List<SeatRecommendationVO> recommend(Long showtimeId,
                                                int people,
                                                String preferenceValue,
                                                int limit) {
        if (people < 1 || people > 5) {
            throw new BusinessException("购票人数须为 1-5 人");
        }
        if (limit < 1 || limit > 5) {
            throw new BusinessException("推荐数量须为 1-5 组");
        }
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) {
            throw new BusinessException("场次不存在");
        }
        Hall hall = hallMapper.selectById(showtime.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在");
        }

        Set<String> occupied = loadOccupiedSeats(showtimeId);
        boolean[][] enabled = parseLayout(hall);
        return engine.recommend(hall.getTotalRows(), hall.getTotalCols(), enabled, occupied, people,
                SeatPreference.from(preferenceValue), limit, showtime.getBasePrice());
    }

    public SeatRecommendationResultVO recommendSmart(Long showtimeId, SeatRecommendationRequest request) {
        validate(request.getPeople(), request.getLimit());
        Showtime showtime = requireShowtime(showtimeId);
        Hall hall = requireHall(showtime.getHallId());
        boolean[][] enabled = parseLayout(hall);
        Set<String> occupied = loadOccupiedSeats(showtimeId);
        SeatPreference inferred = inferPreference(request.getNaturalLanguage(), request.getPreference());
        boolean naturalLanguageMode = request.getNaturalLanguage() != null
                && !request.getNaturalLanguage().isBlank();

        List<SeatRecommendationVO> candidates = naturalLanguageMode
                ? candidatePool(hall, enabled, occupied, request.getPeople(), inferred, showtime)
                : engine.recommend(hall.getTotalRows(), hall.getTotalCols(), enabled, occupied,
                        request.getPeople(), inferred, request.getLimit(), showtime.getBasePrice());
        candidates.forEach(candidate -> candidate.setReasonSource("algorithm"));

        String source = "algorithm";
        String summary = preferenceLabel(inferred);
        String model = null;
        List<SeatRecommendationVO> ranked = new ArrayList<>(candidates);
        if (naturalLanguageMode) {
            var analysis = seatAiRecommendationService.analyze(request.getNaturalLanguage(), request.getPeople(),
                    hall.getTotalRows(), hall.getTotalCols(), candidates);
            if (analysis.isPresent()) {
                SeatAiRecommendationService.SeatAiAnalysis value = analysis.get();
                inferred = value.preference();
                summary = value.summary().isBlank() ? preferenceLabel(inferred) : value.summary();
                ranked = applyModelRanking(candidates, value.ranking());
                source = "model";
                model = seatAiRecommendationService.modelName();
            } else {
                summary = "已按“" + compact(request.getNaturalLanguage(), 32) + "”匹配为" + preferenceLabel(inferred);
            }
        }

        ranked = new ArrayList<>(ranked.stream().limit(request.getLimit()).toList());

        // 模型分析期间座位可能发生变化，返回前再查一次并用算法补足安全候选。
        Set<String> latestOccupied = loadOccupiedSeats(showtimeId);
        if (!latestOccupied.equals(occupied)) {
            ranked.removeIf(item -> item.getSeats().stream().anyMatch(latestOccupied::contains));
            List<SeatRecommendationVO> fresh = engine.recommend(hall.getTotalRows(), hall.getTotalCols(), enabled,
                    latestOccupied, request.getPeople(), inferred, 5, showtime.getBasePrice());
            appendDistinct(ranked, fresh, request.getLimit());
        }

        return SeatRecommendationResultVO.builder()
                .recommendations(ranked.stream().limit(request.getLimit()).toList())
                .interpretedPreference(inferred.name())
                .preferenceSummary(summary)
                .analysisSource(source)
                .model(model)
                .availabilityToken(availabilityToken(latestOccupied))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    static SeatPreference inferPreference(String naturalLanguage, String fallback) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) return SeatPreference.from(fallback);
        String value = naturalLanguage.toLowerCase(Locale.ROOT);
        if (containsAny(value, "无障碍", "轮椅", "行动不便", "老人", "残障")) return SeatPreference.ACCESSIBLE;
        if (containsAny(value, "情侣", "约会", "两个人", "二人", "双人")) return SeatPreference.COUPLE;
        if (containsAny(value, "方便进出", "出口", "通道", "过道", "靠边", "常去厕所")) return SeatPreference.EASY_ACCESS;
        if (containsAny(value, "安静", "靠后", "后排", "人少", "不被打扰")) return SeatPreference.QUIET;
        if (containsAny(value, "中央", "中间", "正中", "视野", "最佳位置")) return SeatPreference.CENTER;
        return SeatPreference.from(fallback);
    }

    private List<SeatRecommendationVO> candidatePool(Hall hall,
                                                      boolean[][] enabled,
                                                      Set<String> occupied,
                                                      int people,
                                                      SeatPreference primary,
                                                      Showtime showtime) {
        List<SeatPreference> preferences = new ArrayList<>(List.of(SeatPreference.values()));
        preferences.remove(primary);
        preferences.addFirst(primary);
        Map<String, SeatRecommendationVO> unique = new LinkedHashMap<>();
        for (SeatPreference preference : preferences) {
            List<SeatRecommendationVO> items = engine.recommend(hall.getTotalRows(), hall.getTotalCols(), enabled,
                    occupied, people, preference, 3, showtime.getBasePrice());
            for (SeatRecommendationVO item : items) {
                unique.putIfAbsent(String.join("|", item.getSeats()), item);
            }
        }
        return unique.values().stream().limit(12).toList();
    }

    private List<SeatRecommendationVO> applyModelRanking(
            List<SeatRecommendationVO> candidates,
            List<SeatAiRecommendationService.RankedReason> ranking) {
        Map<String, SeatRecommendationVO> remaining = candidates.stream().collect(
                LinkedHashMap::new, (map, item) -> map.put(item.getSeatLabel(), item), Map::putAll);
        List<SeatRecommendationVO> result = new ArrayList<>();
        for (SeatAiRecommendationService.RankedReason ranked : ranking) {
            SeatRecommendationVO candidate = remaining.remove(ranked.seatLabel());
            if (candidate == null) continue;
            candidate.setModelReason(ranked.reason());
            candidate.setReasonSource("model");
            result.add(candidate);
        }
        result.addAll(remaining.values());
        return result;
    }

    private void appendDistinct(List<SeatRecommendationVO> target,
                                List<SeatRecommendationVO> fresh,
                                int limit) {
        Set<String> keys = target.stream().map(item -> String.join("|", item.getSeats()))
                .collect(java.util.stream.Collectors.toSet());
        for (SeatRecommendationVO item : fresh) {
            if (target.size() >= limit) break;
            if (keys.add(String.join("|", item.getSeats()))) {
                item.setReasonSource("algorithm");
                target.add(item);
            }
        }
    }

    private void validate(int people, int limit) {
        if (people < 1 || people > 5) throw new BusinessException("购票人数须为 1-5 人");
        if (limit < 1 || limit > 5) throw new BusinessException("推荐数量须为 1-5 组");
    }

    private Showtime requireShowtime(Long showtimeId) {
        Showtime showtime = showtimeMapper.selectById(showtimeId);
        if (showtime == null) throw new BusinessException("场次不存在");
        return showtime;
    }

    private Hall requireHall(Long hallId) {
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new BusinessException("影厅不存在");
        return hall;
    }

    private String preferenceLabel(SeatPreference preference) {
        return switch (preference) {
            case CENTER -> "中央视野";
            case QUIET -> "靠后安静";
            case EASY_ACCESS -> "方便进出";
            case COUPLE -> "情侣双人";
            case ACCESSIBLE -> "无障碍友好";
        };
    }

    private String compact(String value, int max) {
        String normalized = value.trim().replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private String availabilityToken(Set<String> occupied) {
        String stable = occupied.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining("|"));
        return Integer.toHexString(stable.hashCode());
    }

    private static boolean containsAny(String value, String... keywords) {
        return Arrays.stream(keywords).anyMatch(value::contains);
    }

    private Set<String> loadOccupiedSeats(Long showtimeId) {
        List<TicketOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getShowtimeId, showtimeId)
                        .in(TicketOrder::getStatus, 0, 1));
        Set<String> occupied = new HashSet<>();
        for (TicketOrder order : orders) {
            if (order.getSeats() != null && !order.getSeats().isBlank()) {
                occupied.addAll(Arrays.asList(order.getSeats().split(",")));
            }
        }
        return occupied;
    }

    private boolean[][] parseLayout(Hall hall) {
        int rows = hall.getTotalRows();
        int cols = hall.getTotalCols();
        boolean[][] result = new boolean[rows][cols];
        for (boolean[] row : result) Arrays.fill(row, true);
        if (hall.getSeatLayout() == null || hall.getSeatLayout().isBlank()) {
            return result;
        }
        try {
            int[][] layout = JSON.parseObject(hall.getSeatLayout(), int[][].class);
            if (layout.length != rows) return result;
            for (int row = 0; row < rows; row++) {
                if (layout[row].length != cols) return result;
                for (int col = 0; col < cols; col++) {
                    result[row][col] = layout[row][col] != 0;
                }
            }
        } catch (RuntimeException ignored) {
            // Older halls may not have a valid layout; retain the rectangular fallback.
        }
        return result;
    }
}
