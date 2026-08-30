<template>
  <div v-loading="loading" class="smart-page">
    <header class="hero">
      <div>
        <span class="eyebrow">SMART PROGRAMMING · 可解释决策模型</span>
        <h1>智能排片建议</h1>
        <p>把历史订单转化为可执行的增减场建议，并在真正调整前模拟上座率变化。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="selectedCinemaId" placeholder="选择影院" style="width: 220px" @change="handleCinemaChange">
          <el-option v-for="cinema in cinemas" :key="cinema.id" :label="cinema.name" :value="cinema.id!" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="loadInsights">重新计算</el-button>
      </div>
    </header>

    <section class="metrics">
      <article class="metric orange">
        <span>未来平均上座率</span><strong>{{ formatRate(summary.predictedAverageRate) }}</strong>
        <small>基于未来已排场次</small>
      </article>
      <article class="metric red">
        <span>低上座率预警</span><strong>{{ summary.lowOccupancyCount || 0 }}<i>场</i></strong>
        <small>预测低于 35%</small>
      </article>
      <article class="metric green">
        <span>可执行建议</span><strong>{{ summary.actionableSuggestionCount || 0 }}<i>条</i></strong>
        <small>增加或减少场次</small>
      </article>
      <article class="metric blue">
        <span>模型置信度</span><strong>{{ summary.modelConfidence || '低' }}</strong>
        <small>{{ summary.historicalSamples || 0 }} 个历史场次</small>
      </article>
    </section>

    <section class="main-grid">
      <el-card class="heat-card">
        <template #header>
          <div class="card-heading">
            <div><span>客流热力图</span><small>星期 × 时间段 × 影厅</small></div>
            <el-select v-model="selectedHallId" size="small" placeholder="选择影厅" style="width: 150px" @change="renderHeatmap">
              <el-option v-for="hall in insight.halls || []" :key="hall.id" :label="hall.name" :value="hall.id" />
            </el-select>
          </div>
        </template>
        <div ref="heatmapRef" class="heatmap"></div>
        <el-empty v-if="!selectedHeatmap.length && !loading" description="暂无历史客流样本" :image-size="70" />
      </el-card>

      <el-card class="predict-card">
        <template #header><div class="card-heading"><div><span>单场上座率预测</span><small>可自由组合影片、影厅和时间</small></div></div></template>
        <el-form label-position="top">
          <el-form-item label="影片">
            <el-select v-model="predictionForm.movieId" filterable placeholder="选择影片">
              <el-option v-for="movie in movies" :key="movie.id" :label="movie.title" :value="movie.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="影厅">
            <el-select v-model="predictionForm.hallId" placeholder="选择影厅">
              <el-option v-for="hall in halls" :key="hall.id" :label="hall.name" :value="hall.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="开场时间">
            <el-date-picker v-model="predictionForm.startTime" type="datetime" placeholder="选择日期时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-button type="primary" :loading="predicting" style="width:100%" @click="runPrediction">开始预测</el-button>
        </el-form>
        <div v-if="predictionResult" class="prediction-result">
          <div class="prediction-rate"><span>预计上座率</span><strong>{{ formatRate(predictionResult.predictedRate) }}</strong><em>置信度 {{ predictionResult.confidence }}</em></div>
          <div class="driver-list">
            <span v-for="driver in predictionResult.drivers || []" :key="driver.name" :class="driver.direction === 'UP' ? 'up' : 'down'">
              {{ driver.name }} {{ signed(driver.impact) }}%
            </span>
          </div>
        </div>
      </el-card>
    </section>

    <el-card class="section-card warning-card">
      <template #header><div class="card-heading"><div><span>低上座率场次预警</span><small>未来 14 天 · 支持一键模拟替换影片</small></div><el-tag type="danger">{{ lowShowtimes.length }} 场待关注</el-tag></div></template>
      <el-table :data="lowShowtimes" stripe>
        <el-table-column prop="movieTitle" label="当前影片" min-width="150" />
        <el-table-column label="场次" min-width="170"><template #default="{ row }"><div>{{ formatDateTime(row.startTime) }}</div><small class="muted">{{ row.hallName }}</small></template></el-table-column>
        <el-table-column label="预测上座率" width="130"><template #default="{ row }"><span class="rate danger">{{ formatRate(row.predictedRate) }}</span></template></el-table-column>
        <el-table-column label="建议替换" min-width="180"><template #default="{ row }"><template v-if="row.recommendedMovieId"><strong>{{ row.recommendedMovieTitle }}</strong><small class="lift">预计 +{{ row.potentialLift }}%</small></template><span v-else class="muted">建议调整时段或减少场次</span></template></el-table-column>
        <el-table-column prop="reason" label="主要原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="108" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="!row.recommendedMovieId" @click="openSimulation(row)">模拟调整</el-button></template></el-table-column>
        <template #empty><el-empty description="未来 14 天没有低上座率预警" :image-size="72" /></template>
      </el-table>
    </el-card>

    <section class="recommend-grid">
      <el-card class="section-card golden-card">
        <template #header><div class="card-heading"><div><span>黄金时段影片推荐</span><small>优先安排在周五/周末 19:00-22:00</small></div></div></template>
        <div class="golden-list">
          <article v-for="(item, index) in goldenRecommendations" :key="item.movieId">
            <span class="rank">{{ String(Number(index) + 1).padStart(2, '0') }}</span>
            <div><strong>{{ item.movieTitle }}</strong><small>{{ item.reason }}</small></div>
            <div class="golden-rate"><strong>{{ formatRate(item.predictedRate) }}</strong><small>{{ item.confidence }}置信度</small></div>
          </article>
          <el-empty v-if="!goldenRecommendations.length" description="暂无推荐" :image-size="70" />
        </div>
      </el-card>

      <el-card class="section-card frequency-card">
        <template #header><div class="card-heading"><div><span>增减场次建议</span><small>影片需求与未来 7 天供给对比</small></div></div></template>
        <el-table :data="frequencyRecommendations" size="small" max-height="370">
          <el-table-column prop="movieTitle" label="影片" min-width="130" show-overflow-tooltip />
          <el-table-column label="历史上座" width="92"><template #default="{ row }">{{ formatRate(row.historicalRate) }}</template></el-table-column>
          <el-table-column label="当前/周" width="82"><template #default="{ row }">{{ row.currentWeeklyShowtimes }} 场</template></el-table-column>
          <el-table-column label="建议" width="105"><template #default="{ row }"><el-tag :type="actionType(row.action)">{{ actionText(row) }}</el-tag></template></el-table-column>
          <el-table-column prop="reason" label="依据" min-width="190" show-overflow-tooltip />
        </el-table>
      </el-card>
    </section>

    <el-card class="model-card">
      <div class="model-row">
        <div><span class="eyebrow">MODEL TRANSPARENCY</span><h3>{{ insight.model?.name || '可解释加权模型' }}</h3><p>{{ insight.model?.training }}</p></div>
        <div class="weights"><span v-for="(weight, name) in insight.model?.weights || {}" :key="name"><i :style="{ width: `${weight}%` }"></i><em>{{ name }}</em><strong>{{ weight }}%</strong></span></div>
        <div class="accuracy"><span>历史回测准确度</span><strong>{{ formatRate(insight.evaluation?.accuracy) }}</strong><small>平均绝对误差 {{ formatRate(insight.evaluation?.meanAbsoluteError) }}</small></div>
      </div>
    </el-card>

    <el-dialog v-model="simulationVisible" title="排片方案模拟" width="620px">
      <div v-if="simulationSource" class="simulation-source"><span>{{ simulationSource.movieTitle }}</span><el-icon><Right /></el-icon><strong>{{ simulationSource.recommendedMovieTitle }}</strong></div>
      <div v-if="simulationResult" class="simulation-result">
        <div><span>原方案</span><strong>{{ formatRate(simulationResult.original?.predictedRate) }}</strong></div>
        <div class="adjusted"><span>调整后</span><strong>{{ formatRate(simulationResult.adjusted?.predictedRate) }}</strong></div>
        <div class="lift-box"><span>预计提升</span><strong>{{ signed(simulationResult.occupancyLift) }}%</strong><small>客流 {{ signed(simulationResult.expectedExtraAudience) }} 人 · 收入 {{ signedMoney(simulationResult.expectedRevenueLift) }}</small></div>
        <p>{{ simulationResult.recommendation }}</p>
      </div>
      <el-skeleton v-else :rows="4" animated />
      <template #footer><el-button @click="simulationVisible = false">关闭</el-button><el-button type="primary" @click="$router.push('/showtime')">前往排片管理</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { Refresh, Right } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { cinemaApi } from '@/api/cinema'
import { hallApi } from '@/api/hall'
import { movieApi } from '@/api/movie'
import { schedulingApi } from '@/api/scheduling'
import type { Cinema, Hall, Movie } from '@/types'

const loading = ref(false)
const predicting = ref(false)
const insight = ref<any>({})
const cinemas = ref<Cinema[]>([])
const halls = ref<Hall[]>([])
const movies = ref<Movie[]>([])
const selectedCinemaId = ref<number>()
const selectedHallId = ref<number>()
const heatmapRef = ref<HTMLElement>()
const predictionResult = ref<any>()
const simulationVisible = ref(false)
const simulationSource = ref<any>()
const simulationResult = ref<any>()
const predictionForm = reactive<{ movieId?: number; hallId?: number; startTime: string }>({ startTime: nextDefaultTime() })

const summary = computed(() => insight.value.summary || {})
const lowShowtimes = computed(() => insight.value.lowOccupancyShowtimes || [])
const goldenRecommendations = computed(() => insight.value.goldenTimeRecommendations || [])
const frequencyRecommendations = computed(() => insight.value.frequencyRecommendations || [])
const selectedHeatmap = computed(() => (insight.value.heatmap || []).filter((item: any) => item.hallId === selectedHallId.value && item.occupancyRate != null))

function nextDefaultTime() {
  const date = new Date(); date.setDate(date.getDate() + 1); date.setHours(19, 30, 0, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}
const formatRate = (value: any) => `${Number(value || 0).toFixed(1)}%`
const signed = (value: any) => `${Number(value || 0) >= 0 ? '+' : ''}${Number(value || 0).toFixed(1)}`
const signedMoney = (value: any) => `${Number(value || 0) >= 0 ? '+' : '-'}¥${Math.abs(Number(value || 0)).toFixed(2)}`
const formatDateTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : '-'
const actionType = (action: string) => action === 'ADD' ? 'success' : action === 'REDUCE' ? 'danger' : 'info'
const actionText = (row: any) => row.action === 'ADD' ? `增加 ${row.suggestedChange} 场` : row.action === 'REDUCE' ? `减少 ${Math.abs(row.suggestedChange)} 场` : '维持'

const loadInitial = async () => {
  try {
    const [cinemaList, movieList] = await Promise.all([cinemaApi.getAll(), movieApi.getNowShowing()])
    cinemas.value = cinemaList || []; movies.value = movieList || []
    selectedCinemaId.value = cinemas.value[0]?.id
    predictionForm.movieId = movies.value[0]?.id
    if (selectedCinemaId.value) await handleCinemaChange()
  } catch (error) { console.error(error) }
}

const handleCinemaChange = async () => {
  if (!selectedCinemaId.value) return
  halls.value = await hallApi.getByCinemaId(selectedCinemaId.value)
  predictionForm.hallId = halls.value[0]?.id
  predictionResult.value = undefined
  await loadInsights()
}

const loadInsights = async () => {
  if (!selectedCinemaId.value) return
  loading.value = true
  try {
    insight.value = await schedulingApi.getInsights(selectedCinemaId.value)
    if (!(insight.value.halls || []).some((hall: any) => hall.id === selectedHallId.value)) selectedHallId.value = insight.value.halls?.[0]?.id
    await nextTick(); renderHeatmap()
  } catch (error) { console.error(error) } finally { loading.value = false }
}

const runPrediction = async () => {
  if (!selectedCinemaId.value || !predictionForm.movieId || !predictionForm.hallId || !predictionForm.startTime) return ElMessage.warning('请完整选择影片、影厅和开场时间')
  predicting.value = true
  try { predictionResult.value = await schedulingApi.predict({ cinemaId: selectedCinemaId.value, movieId: predictionForm.movieId, hallId: predictionForm.hallId, startTime: predictionForm.startTime }) }
  finally { predicting.value = false }
}

const openSimulation = async (row: any) => {
  simulationSource.value = row; simulationResult.value = undefined; simulationVisible.value = true
  try { simulationResult.value = await schedulingApi.simulate({ originalShowtimeId: row.showtimeId, targetMovieId: row.recommendedMovieId }) }
  catch (error) { simulationVisible.value = false; console.error(error) }
}

const renderHeatmap = () => {
  if (!heatmapRef.value) return
  const chart = echarts.getInstanceByDom(heatmapRef.value) || echarts.init(heatmapRef.value)
  const points = (insight.value.heatmap || []).filter((item: any) => item.hallId === selectedHallId.value)
  chart.setOption({
    tooltip: { formatter: (p: any) => `${p.data[3]} ${p.data[4]}<br/>上座率：${p.data[2] == null ? '暂无样本' : `${p.data[2]}%`}<br/>样本：${p.data[5]} 场` },
    grid: { left: 82, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: ['周一','周二','周三','周四','周五','周六','周日'], splitArea: { show: true }, axisTick: { show: false } },
    yAxis: { type: 'category', data: ['上午 06-12','下午 12-18','黄金 18-22','深夜 22-06'], splitArea: { show: true }, axisTick: { show: false } },
    visualMap: { min: 0, max: 100, calculable: false, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#e3e5e0','#ffd399','#ff8a30','#bd3c19'] }, text: ['高','低'] },
    series: [{ type: 'heatmap', data: points.map((item: any) => [item.weekday - 1, item.timeSlot, item.occupancyRate, item.weekdayLabel, item.timeSlotLabel, item.sampleCount]), label: { show: true, formatter: (p: any) => p.data[2] == null ? '—' : `${p.data[2]}%` }, itemStyle: { borderColor: '#e7e8e5', borderWidth: 5, borderRadius: 8 }, emphasis: { itemStyle: { shadowBlur: 12, shadowColor: 'rgba(255,132,0,.35)' } } }]
  }, true)
}
const resize = () => heatmapRef.value && echarts.getInstanceByDom(heatmapRef.value)?.resize()
onMounted(() => { loadInitial(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); if (heatmapRef.value) echarts.getInstanceByDom(heatmapRef.value)?.dispose() })
</script>

<style scoped>
.smart-page{display:flex;flex-direction:column;gap:20px}.hero{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:26px 28px;border-radius:20px;background:linear-gradient(135deg,#303a34,#18201c);color:#fff;box-shadow:8px 8px 18px #c2c5bf,-8px -8px 18px #fafbf8}.eyebrow{color:#ff9d3f;font-family:'JetBrains Mono',monospace;font-size:10px;font-weight:700;letter-spacing:.12em}.hero h1{margin:8px 0 7px;font-size:30px}.hero p{margin:0;color:#aeb9b2;font-size:13px}.hero-actions{display:flex;gap:12px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.metric{position:relative;padding:19px 20px;border-radius:16px;background:var(--admin-card);box-shadow:var(--admin-shadow);overflow:hidden}.metric:before{content:'';position:absolute;left:0;top:18px;bottom:18px;width:4px;border-radius:0 4px 4px 0;background:var(--tone)}.metric.orange{--tone:#ff8400}.metric.red{--tone:#dd5839}.metric.green{--tone:#32a879}.metric.blue{--tone:#4b8ccc}.metric span,.metric small{display:block;color:var(--admin-muted);font-size:11px}.metric strong{display:block;margin:8px 0 4px;font-family:'JetBrains Mono',monospace;font-size:25px}.metric strong i{margin-left:4px;font:600 11px Geist}.main-grid,.recommend-grid{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(310px,.7fr);gap:20px}.recommend-grid{grid-template-columns:1fr 1.25fr}.card-heading{display:flex;align-items:center;justify-content:space-between;gap:15px;width:100%}.card-heading>div{display:flex;flex-direction:column;gap:4px}.card-heading span{font-weight:700}.card-heading small,.muted{color:var(--admin-muted);font-size:11px}.heatmap{height:350px}.predict-card :deep(.el-card__body){padding-top:16px!important}.predict-card :deep(.el-form-item){margin-bottom:14px}.predict-card :deep(.el-select),.predict-card :deep(.el-date-editor){width:100%}.prediction-result{margin-top:18px;padding:15px;border-radius:13px;box-shadow:inset 4px 4px 8px var(--admin-shadow-dark),inset -4px -4px 8px var(--admin-shadow-light)}.prediction-rate{display:grid;grid-template-columns:1fr auto;align-items:end}.prediction-rate span,.prediction-rate em{color:var(--admin-muted);font-size:10px;font-style:normal}.prediction-rate strong{grid-row:1/3;grid-column:2;font-family:'JetBrains Mono';font-size:27px;color:#c76700}.driver-list{display:flex;flex-wrap:wrap;gap:6px;margin-top:12px}.driver-list span{padding:4px 7px;border-radius:6px;font-size:9px}.driver-list .up{color:#218760;background:#d9eee6}.driver-list .down{color:#bd4933;background:#f5dfda}.section-card{overflow:visible}.rate{font-family:'JetBrains Mono';font-weight:700}.rate.danger{color:#c74d33}.lift{display:block;margin-top:4px;color:#249065}.golden-list{display:flex;flex-direction:column;gap:4px}.golden-list article{display:grid;grid-template-columns:34px 1fr auto;align-items:center;gap:12px;padding:13px 10px;border-bottom:1px solid rgba(160,167,160,.2)}.golden-list article:last-child{border-bottom:0}.rank{color:#c16a10;font-family:'JetBrains Mono';font-weight:700}.golden-list article>div{min-width:0;display:flex;flex-direction:column;gap:4px}.golden-list article small{overflow:hidden;color:var(--admin-muted);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.golden-rate{text-align:right}.golden-rate strong{color:#bf6509;font-family:'JetBrains Mono';font-size:17px}.model-row{display:grid;grid-template-columns:1fr 1.7fr .55fr;align-items:center;gap:28px}.model-row h3{margin:7px 0 5px}.model-row p{margin:0;color:var(--admin-muted);font-size:11px}.weights{display:grid;grid-template-columns:1fr 1fr;gap:8px 18px}.weights span{position:relative;display:grid;grid-template-columns:1fr auto;gap:8px;padding-bottom:7px;overflow:hidden}.weights span:after,.weights i{content:'';position:absolute;bottom:0;left:0;height:3px;border-radius:3px}.weights span:after{right:0;background:#d1d4ce}.weights i{z-index:1;background:#ff8400}.weights em{font-size:10px;font-style:normal}.weights strong{font:700 10px 'JetBrains Mono'}.accuracy{padding:15px;text-align:center;border-radius:13px;box-shadow:inset 4px 4px 8px var(--admin-shadow-dark),inset -4px -4px 8px var(--admin-shadow-light)}.accuracy span,.accuracy small{display:block;color:var(--admin-muted);font-size:9px}.accuracy strong{display:block;margin:5px;font:800 23px 'JetBrains Mono';color:#bd6207}.simulation-source{display:flex;align-items:center;justify-content:center;gap:14px;padding:14px;border-radius:12px;background:#f0f1ed}.simulation-result{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:16px}.simulation-result>div{padding:18px;border-radius:13px;text-align:center;box-shadow:inset 4px 4px 8px var(--admin-shadow-dark),inset -4px -4px 8px var(--admin-shadow-light)}.simulation-result span,.simulation-result small{display:block;color:var(--admin-muted);font-size:10px}.simulation-result strong{display:block;margin:6px;font:800 24px 'JetBrains Mono'}.simulation-result .adjusted strong,.simulation-result .lift-box strong{color:#278c67}.simulation-result .lift-box{grid-column:1/-1}.simulation-result p{grid-column:1/-1;margin:0;padding:12px;text-align:center;color:#7a4b15;background:#f4e4ce;border-radius:10px;font-size:12px}@media(max-width:1200px){.metrics{grid-template-columns:1fr 1fr}.main-grid,.recommend-grid{grid-template-columns:1fr}.model-row{grid-template-columns:1fr 1.5fr}.accuracy{grid-column:1/-1}}@media(max-width:720px){.hero{align-items:flex-start;flex-direction:column}.hero-actions{width:100%;flex-direction:column}.hero-actions :deep(.el-select){width:100%!important}.metrics{grid-template-columns:1fr}.model-row{grid-template-columns:1fr}.weights{grid-template-columns:1fr}.heatmap{height:310px}}
/* 表格的固有宽度不能反向撑大 Grid 轨道，否则黄金推荐会被挤成竖排。 */
.smart-page {
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric,
.main-grid,
.recommend-grid,
.main-grid > *,
.recommend-grid > *,
.card-heading,
.card-heading > div,
.section-card,
.golden-list,
.golden-list article {
  min-width: 0;
}

.recommend-grid {
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.35fr);
}

.section-card {
  overflow: hidden;
}

.section-card :deep(.el-card__body) {
  min-width: 0;
  overflow: hidden;
}

.section-card :deep(.el-table) {
  width: 100% !important;
  max-width: 100%;
}

.golden-list article {
  grid-template-columns: 34px minmax(0, 1fr) auto;
}

@media (max-width: 1200px) {
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recommend-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .metrics {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
