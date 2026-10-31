package com.movie.ticket.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 排片优化参数，限定影院日期、优化目标、影片选择及经营约束。 */
@Data
public class SchedulingOptimizationRequest {

    @NotNull(message = "影院不能为空")
    private Long cinemaId;

    @NotNull(message = "排片日期不能为空")
    @FutureOrPresent(message = "只能优化今天或未来日期")
    private LocalDate targetDate;

    /** 留空表示使用全部正在上映影片。 */
    private List<Long> movieIds;

    @Min(value = 1, message = "每厅目标场次至少为 1")
    @Max(value = 8, message = "每厅目标场次最多为 8")
    private Integer targetShowtimesPerHall = 5;

    @Min(value = 1, message = "单片最多场次至少为 1")
    @Max(value = 20, message = "单片最多场次不能超过 20")
    private Integer maxShowtimesPerMovie = 4;

    @Min(value = 1, message = "影片多样性至少为 1")
    @Max(value = 20, message = "影片多样性不能超过 20")
    private Integer minDistinctMovies = 3;

    @Min(value = 10, message = "清场时间至少为 10 分钟")
    @Max(value = 60, message = "清场时间最多为 60 分钟")
    private Integer cleaningMinutes = 20;

    @Min(value = 0, message = "每厅黄金时段场次不能为负数")
    @Max(value = 3, message = "每厅黄金时段场次最多为 3")
    private Integer minGoldenShowtimesPerHall = 1;

    @Min(value = 0, message = "收益权重不能为负数")
    @Max(value = 100, message = "收益权重不能超过 100")
    private Integer revenueWeight = 50;

    @Min(value = 0, message = "上座率权重不能为负数")
    @Max(value = 100, message = "上座率权重不能超过 100")
    private Integer occupancyWeight = 35;

    @Min(value = 0, message = "多样性权重不能为负数")
    @Max(value = 100, message = "多样性权重不能超过 100")
    private Integer diversityWeight = 15;

    @Min(value = 1, message = "求解时间至少为 1 秒")
    @Max(value = 30, message = "求解时间最多为 30 秒")
    private Integer solverSeconds = 8;
}
