<template>
  <el-card class="optimizer-card">
    <template #header>
      <div class="heading">
        <div>
          <span class="eyebrow">OR-TOOLS CP-SAT · MULTI-OBJECTIVE</span>
          <strong>约束条件下的多目标智能排片</strong>
          <small>同时优化预计收入、上座率与影片多样性；结果仅作决策模拟，不直接写入排片。</small>
        </div>
        <el-tag type="success" effect="dark">论文核心创新</el-tag>
      </div>
    </template>

    <el-form label-position="top" class="optimizer-form">
      <el-form-item label="目标日期">
        <el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :disabled-date="disableDate" />
      </el-form-item>
      <el-form-item label="参与影片（留空为全部）" class="movie-field">
        <el-select v-model="form.movieIds" multiple collapse-tags collapse-tags-tooltip filterable clearable placeholder="全部正在上映影片">
          <el-option v-for="movie in selectableMovies" :key="movie.id" :label="movie.title" :value="movie.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="每厅目标场次">
        <el-input-number v-model="form.targetShowtimesPerHall" :min="1" :max="8" />
      </el-form-item>
      <el-form-item label="单片最多场次">
        <el-input-number v-model="form.maxShowtimesPerMovie" :min="1" :max="20" />
      </el-form-item>
      <el-form-item label="最少影片数">
        <el-input-number v-model="form.minDistinctMovies" :min="1" :max="20" />
      </el-form-item>
      <el-form-item label="清场间隔（分钟）">
        <el-input-number v-model="form.cleaningMinutes" :min="10" :max="60" :step="5" />
      </el-form-item>
      <el-form-item label="每厅黄金时段最少场次">
        <el-input-number v-model="form.minGoldenShowtimesPerHall" :min="0" :max="3" />
      </el-form-item>
    </el-form>

    <div class="weights-title">
      <span>目标函数权重</span>
      <el-tag :type="weightSum === 100 ? 'success' : 'danger'">合计 {{ weightSum }}%</el-tag>
    </div>
    <div class="weight-grid">
      <label><span>预计收入</span><el-slider v-model="form.revenueWeight" :max="100" show-input :show-input-controls="false" /></label>
      <label><span>预计上座率</span><el-slider v-model="form.occupancyWeight" :max="100" show-input :show-input-controls="false" /></label>
      <label><span>影片多样性</span><el-slider v-model="form.diversityWeight" :max="100" show-input :show-input-controls="false" /></label>
    </div>
    <div class="actions">
      <span>求解上限 {{ form.solverSeconds }} 秒</span>
      <el-slider v-model="form.solverSeconds" :min="1" :max="30" :show-tooltip="false" />
      <el-button type="primary" :loading="optimizing" :disabled="!cinemaId" @click="runOptimization">{{ optimizing ? '正在求解并生成模型分析' : '运行优化与模型分析' }}</el-button>
    </div>

    <template v-if="result">
      <SchedulingAiAnalysis :analysis="result.aiAnalysis" />
      <el-alert v-if="result.status === 'INFEASIBLE'" :title="result.statusText" :description="result.reason || result.notice" type="warning" show-icon :closable="false" />
      <div v-else class="result-block">
        <div class="result-head">
          <div><el-tag type="success">{{ result.statusText }}</el-tag><span>{{ result.solver }}</span></div>
          <small>候选变量 {{ result.candidateVariables }} · 求解 {{ result.solveTimeMs }} ms · 最优差距 {{ number(result.optimalityGap) }}%</small>
        </div>
        <div class="lift-grid">
          <article><span>收入较人工方案</span><strong>{{ signed(result.revenueLiftVsManual) }}%</strong></article>
          <article><span>收入较启发式</span><strong>{{ signed(result.revenueLiftVsHeuristic) }}%</strong></article>
          <article><span>上座率较人工方案</span><strong>{{ signed(result.occupancyLiftVsManual) }} 个百分点</strong></article>
          <article><span>锁定已售场次</span><strong>{{ result.lockedShowtimes || 0 }} 场</strong></article>
        </div>
        <el-table :data="result.comparisons" stripe class="comparison-table">
          <el-table-column prop="method" label="方案" min-width="145" />
          <el-table-column prop="showtimeCount" label="场次" width="70" />
          <el-table-column label="预计收入" min-width="110"><template #default="{ row }">¥{{ money(row.expectedRevenue) }}</template></el-table-column>
          <el-table-column label="预计上座率" width="105"><template #default="{ row }">{{ number(row.occupancyRate) }}%</template></el-table-column>
          <el-table-column prop="distinctMovies" label="影片数" width="78" />
          <el-table-column label="约束满足率" width="108"><template #default="{ row }"><el-tag :type="row.constraintSatisfactionRate === 100 ? 'success' : 'warning'">{{ row.constraintSatisfactionRate }}%</el-tag></template></el-table-column>
          <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.calculationTimeMs }} ms</template></el-table-column>
        </el-table>
        <el-collapse class="schedule-collapse">
          <el-collapse-item :title="`查看 CP-SAT 推荐排片（${result.selectedShowtimes.length} 场）`">
            <el-table :data="result.selectedShowtimes" size="small" max-height="420">
              <el-table-column prop="hallName" label="影厅" width="100" />
              <el-table-column prop="movieTitle" label="影片" min-width="145" />
              <el-table-column label="开始时间" min-width="145"><template #default="{ row }">{{ dateTime(row.startTime) }}</template></el-table-column>
              <el-table-column label="上座率" width="86"><template #default="{ row }">{{ number(row.predictedRate) }}%</template></el-table-column>
              <el-table-column label="预计收入" width="105"><template #default="{ row }">¥{{ money(row.expectedRevenue) }}</template></el-table-column>
              <el-table-column label="状态" width="85"><template #default="{ row }"><el-tag v-if="row.locked" type="info">已售锁定</el-tag><span v-else>新方案</span></template></el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
        <p class="notice">{{ result.notice }}</p>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 排片优化面板：收集优化目标和约束，调用后端算法并展示候选方案。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { schedulingApi } from '@/api/scheduling'
import type { SchedulingOptimizationResult } from '@/api/scheduling'
import type { Movie } from '@/types'
import SchedulingAiAnalysis from '@/components/SchedulingAiAnalysis.vue'

const props = defineProps<{ cinemaId?: number; movies: Movie[] }>()
const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1)
const dateValue = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const form = reactive({ targetDate: dateValue(tomorrow), movieIds: [] as number[], targetShowtimesPerHall: 5,
  maxShowtimesPerMovie: 4, minDistinctMovies: 3, cleaningMinutes: 20, revenueWeight: 50,
  minGoldenShowtimesPerHall: 1, occupancyWeight: 35, diversityWeight: 15, solverSeconds: 8 })
const optimizing = ref(false)
const result = ref<SchedulingOptimizationResult>()
const selectableMovies = computed(() => props.movies.filter(movie => movie.id != null))
const weightSum = computed(() => form.revenueWeight + form.occupancyWeight + form.diversityWeight)
watch(() => props.cinemaId, () => { result.value = undefined })

/** 提交优化目标和约束，获取可比较的排片候选方案。 */
const runOptimization = async () => {
  if (!props.cinemaId) return ElMessage.warning('请先选择影院')
  if (weightSum.value !== 100) return ElMessage.warning('三个目标权重之和必须为 100%')
  if (form.minGoldenShowtimesPerHall > form.targetShowtimesPerHall) return ElMessage.warning('黄金时段最少场次不能超过每厅目标场次')
  if (form.movieIds.length && form.minDistinctMovies > form.movieIds.length) return ElMessage.warning('最少影片数不能超过已选影片数')
  optimizing.value = true
  const cinemaId = props.cinemaId
  result.value = undefined
  try {
    const response = await schedulingApi.optimize({ cinemaId, ...form,
      movieIds: form.movieIds.length ? form.movieIds : undefined })
    if (cinemaId !== props.cinemaId) return
    result.value = response
    if (result.value.status === 'INFEASIBLE') ElMessage.warning(result.value.reason || '当前约束下无可行解')
    else ElMessage.success('多目标排片求解完成')
  } finally { optimizing.value = false }
}
const disableDate = (date: Date) => {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const max = new Date(today); max.setDate(max.getDate() + 30)
  return date < today || date > max
}
const number = (value: unknown) => Number(value || 0).toFixed(1)
const money = (value: unknown) => Number(value || 0).toFixed(2)
const signed = (value: unknown) => value == null ? '—' : `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(1)}`
const dateTime = (value: string) => new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
</script>

<style scoped>
.optimizer-card{overflow:visible}.heading{display:flex;align-items:center;justify-content:space-between;gap:20px}.heading>div{display:flex;flex-direction:column;gap:5px}.heading strong{font-size:17px}.heading small{color:var(--admin-muted);font-size:11px}.eyebrow{color:#b96000;font:700 9px 'JetBrains Mono';letter-spacing:.12em}.optimizer-form{display:grid;grid-template-columns:1fr minmax(280px,2fr) repeat(4,1fr);gap:12px}.optimizer-form :deep(.el-form-item){margin-bottom:5px}.optimizer-form :deep(.el-select),.optimizer-form :deep(.el-date-editor),.optimizer-form :deep(.el-input-number){width:100%}.weights-title{display:flex;align-items:center;justify-content:space-between;margin-top:18px;padding-top:15px;border-top:1px solid rgba(160,167,160,.2);font-size:12px;font-weight:700}.weight-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:28px;margin-top:8px}.weight-grid label>span{font-size:10px;color:var(--admin-muted)}.weight-grid :deep(.el-slider){margin:4px 0}.actions{display:grid;grid-template-columns:auto minmax(150px,320px) auto;align-items:center;justify-content:end;gap:16px;margin-top:8px}.actions>span{font:600 10px 'JetBrains Mono';color:var(--admin-muted)}.result-block{margin-top:20px;padding-top:18px;border-top:1px solid rgba(160,167,160,.2)}.result-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.result-head>div{display:flex;align-items:center;gap:10px}.result-head span,.result-head small{color:var(--admin-muted);font-size:10px}.lift-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:14px 0}.lift-grid article{padding:13px;border-radius:12px;box-shadow:inset 3px 3px 6px var(--admin-shadow-dark),inset -3px -3px 6px var(--admin-shadow-light)}.lift-grid span{display:block;color:var(--admin-muted);font-size:9px}.lift-grid strong{display:block;margin-top:5px;color:#278c67;font:800 16px 'JetBrains Mono'}.comparison-table{width:100%}.schedule-collapse{margin-top:12px}.notice{margin:10px 0 0;color:var(--admin-muted);font-size:10px}@media(max-width:1200px){.optimizer-form{grid-template-columns:repeat(3,1fr)}.movie-field{grid-column:span 2}.lift-grid{grid-template-columns:1fr 1fr}}@media(max-width:720px){.heading{align-items:flex-start;flex-direction:column}.optimizer-form,.weight-grid,.lift-grid{grid-template-columns:1fr}.movie-field{grid-column:auto}.actions{grid-template-columns:1fr}.result-head{align-items:flex-start;flex-direction:column}}
</style>
