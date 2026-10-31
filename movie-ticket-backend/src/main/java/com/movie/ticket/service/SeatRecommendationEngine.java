package com.movie.ticket.service;

import com.movie.ticket.dto.SeatPreference;
import com.movie.ticket.vo.SeatRecommendationVO;
import com.movie.ticket.vo.SeatScoreVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Enumerates every available same-row seat block and ranks it with explainable scores. */
public class SeatRecommendationEngine {

    /** 枚举符合人数和连座约束的候选组合，并按偏好评分排序。 */
    public List<SeatRecommendationVO> recommend(int rows,
                                                int cols,
                                                boolean[][] enabled,
                                                Set<String> occupied,
                                                int people,
                                                SeatPreference preference,
                                                int limit,
                                                BigDecimal unitPrice) {
        return recommend(rows, cols, enabled, occupied, people, preference, limit, unitPrice, Set.of());
    }

    /** 枚举符合人数和连座约束的候选组合，并按偏好评分排序。 */
    public List<SeatRecommendationVO> recommend(int rows, int cols, boolean[][] enabled,
                                                Set<String> occupied, int people, SeatPreference preference,
                                                int limit, BigDecimal unitPrice, Set<Integer> aisleAfterCols) {
        List<Candidate> candidates = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int start = 1; start <= cols - people + 1; start++) {
                int end = start + people - 1;
                int blockStart = start;
                if (aisleAfterCols.stream().anyMatch(col -> col >= blockStart && col < end)
                        || !isAvailableBlock(row, start, end, enabled, occupied)) {
                    continue;
                }
                candidates.add(score(row, start, end, rows, cols, enabled, occupied, people, preference, aisleAfterCols));
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::total).reversed()
                .thenComparingInt(Candidate::singleSeatCount)
                .thenComparingInt(Candidate::row)
                .thenComparingInt(Candidate::start));

        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlaps = selected.stream().anyMatch(existing -> existing.overlaps(candidate));
            if (!overlaps) {
                selected.add(candidate);
            }
            if (selected.size() == limit) {
                break;
            }
        }

        List<SeatRecommendationVO> result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            result.add(toView(selected.get(i), i, preference, people, unitPrice));
        }
        return result;
    }

    private Candidate score(int row,
                            int start,
                            int end,
                            int rows,
                            int cols,
                            boolean[][] enabled,
                            Set<String> occupied,
                            int people,
                            SeatPreference preference, Set<Integer> aisleAfterCols) {
        double blockCenter = (start + end) / 2.0;
        double hallCenter = (cols + 1) / 2.0;
        double center = closeness(blockCenter, hallCenter, Math.max(1.0, (cols - 1) / 2.0));

        double goldenRow = 1.0 + (rows - 1) * 0.65;
        double distance = closeness(row, goldenRow, Math.max(1.0, rows - 1));

        int freeRun = freeRunLength(row, start, end, cols, enabled, occupied, aisleAfterCols);
        double continuity = clamp(70.0 + 30.0 * people / Math.max(people, freeRun));

        double nearestExitDistance = Math.min(start - 1.0, cols - end);
        for (int aisleLeft : aisleAfterCols) {
            nearestExitDistance = Math.min(nearestExitDistance,
                    Math.min(Math.abs(start - (aisleLeft + 1)), Math.abs(end - aisleLeft)));
        }
        double access = clamp(100.0 * (1.0 - nearestExitDistance / Math.max(1.0, cols / 2.0)));

        double realtime = realtimeAvailability(row, start, end, rows, cols, enabled, occupied);
        double preferenceScore = preferenceScore(preference, people, row, rows, center, distance, access,
                occupied, start, end, cols);
        int singleSeats = countSingleSeatsLeft(row, start, end, cols, enabled, occupied, aisleAfterCols);
        int penalty = singleSeats * 18;
        double total = center * 0.30
                + distance * 0.25
                + continuity * 0.20
                + access * 0.10
                + preferenceScore * 0.10
                + realtime * 0.05
                - penalty;

        return new Candidate(row, start, end, center, distance, continuity, access, preferenceScore,
                realtime, singleSeats, clamp(total));
    }

    private double preferenceScore(SeatPreference preference,
                                   int people,
                                   int row,
                                   int rows,
                                   double center,
                                   double distance,
                                   double access,
                                   Set<String> occupied,
                                   int start,
                                   int end,
                                   int cols) {
        double rear = rows == 1 ? 100.0 : 100.0 * (row - 1) / (rows - 1.0);
        return switch (preference) {
            case CENTER -> center * 0.6 + distance * 0.4;
            case QUIET -> rear * 0.65 + occupiedSeparation(row, start, end, cols, occupied) * 0.35;
            case EASY_ACCESS -> access;
            case COUPLE -> (people == 2 ? 50.0 : 20.0) + rear * 0.25 + center * 0.25;
            case ACCESSIBLE -> access * 0.7 + rear * 0.3;
        };
    }

    private double realtimeAvailability(int row,
                                        int start,
                                        int end,
                                        int rows,
                                        int cols,
                                        boolean[][] enabled,
                                        Set<String> occupied) {
        int available = 0;
        int valid = 0;
        for (int r = Math.max(1, row - 1); r <= Math.min(rows, row + 1); r++) {
            for (int c = Math.max(1, start - 2); c <= Math.min(cols, end + 2); c++) {
                if (!enabled[r - 1][c - 1]) continue;
                valid++;
                if (!occupied.contains(seatName(r, c))) available++;
            }
        }
        return valid == 0 ? 0 : 100.0 * available / valid;
    }

    private double occupiedSeparation(int row, int start, int end, int cols, Set<String> occupied) {
        int nearest = cols;
        for (int col = 1; col <= cols; col++) {
            if (occupied.contains(seatName(row, col))) {
                int distance = col < start ? start - col : col > end ? col - end : 0;
                nearest = Math.min(nearest, distance);
            }
        }
        return clamp(100.0 * nearest / Math.max(1.0, cols / 3.0));
    }

    private int freeRunLength(int row,
                              int start,
                              int end,
                              int cols,
                              boolean[][] enabled,
                              Set<String> occupied, Set<Integer> aisleAfterCols) {
        int left = start;
        while (left > 1 && !aisleAfterCols.contains(left - 1) && isAvailable(row, left - 1, enabled, occupied)) left--;
        int right = end;
        while (right < cols && !aisleAfterCols.contains(right) && isAvailable(row, right + 1, enabled, occupied)) right++;
        return right - left + 1;
    }

    private int countSingleSeatsLeft(int row,
                                     int start,
                                     int end,
                                     int cols,
                                     boolean[][] enabled,
                                     Set<String> occupied, Set<Integer> aisleAfterCols) {
        int singles = 0;
        int runLength = 0;
        for (int col = 1; col <= cols + 1; col++) {
            if (aisleAfterCols.contains(col - 1)) {
                if (runLength == 1) singles++;
                runLength = 0;
            }
            boolean freeAfterSelection = col <= cols
                    && isAvailable(row, col, enabled, occupied)
                    && (col < start || col > end);
            if (freeAfterSelection) {
                runLength++;
            } else {
                if (runLength == 1) singles++;
                runLength = 0;
            }
        }
        return singles;
    }

    private boolean isAvailableBlock(int row,
                                     int start,
                                     int end,
                                     boolean[][] enabled,
                                     Set<String> occupied) {
        for (int col = start; col <= end; col++) {
            if (!isAvailable(row, col, enabled, occupied)) return false;
        }
        return true;
    }

    private boolean isAvailable(int row, int col, boolean[][] enabled, Set<String> occupied) {
        return enabled[row - 1][col - 1] && !occupied.contains(seatName(row, col));
    }

    private SeatRecommendationVO toView(Candidate candidate,
                                        int index,
                                        SeatPreference preference,
                                        int people,
                                        BigDecimal unitPrice) {
        List<String> seats = new ArrayList<>();
        for (int col = candidate.start(); col <= candidate.end(); col++) {
            seats.add(seatName(candidate.row(), col));
        }
        List<String> tags = topTags(candidate, preference);
        String prefix = switch (preference) {
            case CENTER -> "中央视野";
            case QUIET -> "靠后安静";
            case EASY_ACCESS -> "方便进出";
            case COUPLE -> "情侣优选";
            case ACCESSIBLE -> "无障碍友好";
        };
        String title = index == 0 ? prefix + "最佳方案" : prefix + "备选 " + (index + 1);
        String icon = switch (preference) {
            case COUPLE -> "favorite";
            case ACCESSIBLE -> "accessible";
            case EASY_ACCESS -> "meeting_room";
            case QUIET -> "weekend";
            default -> "thumb_up";
        };
        String reason = String.join("，", tags);
        if (candidate.singleSeatCount() == 0) {
            reason += "，且不会留下孤立单座";
        }
        return SeatRecommendationVO.builder()
                .title(title)
                .icon(icon)
                .match((int) Math.round(candidate.total()))
                .seatLabel(candidate.row() + "排 " + candidate.start() + "-" + candidate.end() + "座")
                .seats(seats)
                .count(people)
                .unitPrice(unitPrice)
                .reason(reason)
                .tags(tags)
                .scores(new SeatScoreVO(
                        rounded(candidate.center()),
                        rounded(candidate.distance()),
                        rounded(candidate.continuity()),
                        rounded(candidate.access()),
                        rounded(candidate.preference()),
                        rounded(candidate.realtime())))
                .singleSeatPenalty(candidate.singleSeatCount() * 18)
                .build();
    }

    private List<String> topTags(Candidate candidate, SeatPreference preference) {
        List<TagScore> scores = new ArrayList<>(List.of(
                new TagScore("中央视野", candidate.center()),
                new TagScore("黄金观影距离", candidate.distance()),
                new TagScore("连座完整", candidate.continuity()),
                new TagScore("方便进出", candidate.access()),
                new TagScore("周边余座充足", candidate.realtime())
        ));
        scores.add(new TagScore(switch (preference) {
            case CENTER -> "符合中央视野偏好";
            case QUIET -> "符合靠后安静偏好";
            case EASY_ACCESS -> "靠近通道或出口";
            case COUPLE -> "适合双人观影";
            case ACCESSIBLE -> "无障碍出入更便利";
        }, candidate.preference() + 5));
        scores.sort(Comparator.comparingDouble(TagScore::score).reversed());
        return scores.stream().limit(3).map(TagScore::label).toList();
    }

    private static String seatName(int row, int col) {
        return row + "排" + col + "座";
    }

    private static double closeness(double value, double target, double maxDistance) {
        return clamp(100.0 * (1.0 - Math.abs(value - target) / maxDistance));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static int rounded(double value) {
        return (int) Math.round(clamp(value));
    }

    private record TagScore(String label, double score) {}

    private record Candidate(int row,
                             int start,
                             int end,
                             double center,
                             double distance,
                             double continuity,
                             double access,
                             double preference,
                             double realtime,
                             int singleSeatCount,
                             double total) {
        boolean overlaps(Candidate other) {
            if (row != other.row) return false;
            return start <= other.end && other.start <= end;
        }
    }
}
