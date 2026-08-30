<template>
  <div class="dashboard-page" :class="{ 'is-loading': loading }">
    <div class="dashboard-grid-overlay"></div>

    <header class="screen-header">
      <div class="title-group">
        <div class="eyebrow"><span class="live-dot"></span>CINEMA OPERATION CENTER</div>
        <h1>实时数据中心</h1>
        <p>实时汇聚影片、影院、订单与票房数据，洞察今日运营表现</p>
      </div>
      <div class="header-actions">
        <div class="system-status">
          <span class="status-dot"></span>
          <div><strong>系统运行正常</strong><span>数据服务在线</span></div>
        </div>
        <div class="clock-block"><strong>{{ currentTime }}</strong><span>{{ currentDate }}</span></div>
        <button class="refresh-button" :disabled="loading" @click="loadAllData">
          <el-icon :class="{ spinning: loading }"><Refresh /></el-icon>刷新数据
        </button>
      </div>
    </header>

    <section class="metric-grid">
      <article v-for="item in metricCards" :key="item.key" class="metric-card" :class="item.tone">
        <div class="metric-topline">
          <span class="metric-label">{{ item.label }}</span>
          <span class="metric-icon"><el-icon><component :is="item.icon" /></el-icon></span>
        </div>
        <div class="metric-value"><span v-if="item.prefix" class="metric-prefix">{{ item.prefix }}</span>{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
        <div class="metric-foot"><span>{{ item.description }}</span><span class="metric-state"><i></i>实时</span></div>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel revenue-panel">
        <div class="panel-corner top-left"></div><div class="panel-corner bottom-right"></div>
        <header class="panel-header">
          <div><span class="panel-kicker">REVENUE TREND</span><h2>近 7 日营收趋势</h2></div>
          <div class="panel-summary"><span>周期营收</span><strong>¥{{ formatNumber(periodRevenue) }}</strong></div>
        </header>
        <div ref="revenueChartRef" class="chart chart-large"></div>
        <div v-if="!revenueData.length && !loading" class="chart-empty">暂无营收数据</div>
      </article>

      <article class="panel ranking-panel">
        <div class="panel-corner top-left"></div>
        <header class="panel-header">
          <div><span class="panel-kicker">BOX OFFICE TOP</span><h2>实时票房排行</h2></div>
          <button class="text-button" @click="router.push('/movie')">影片管理 <el-icon><ArrowRight /></el-icon></button>
        </header>
        <div class="ranking-list">
          <div v-for="(item, index) in boxOfficeData.slice(0, 6)" :key="item.movieId || index" class="ranking-row">
            <span class="rank-index" :class="{ top: index < 3 }">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="rank-main">
              <div class="rank-title-row"><strong>{{ item.movieTitle || '未命名影片' }}</strong><span>¥{{ formatNumber(item.boxOffice) }}</span></div>
              <div class="rank-track"><i :style="{ width: getRankWidth(item.boxOffice) }"></i></div>
              <small>{{ formatNumber(item.ticketCount) }} 张票</small>
            </div>
          </div>
          <div v-if="!boxOfficeData.length && !loading" class="list-empty">暂无票房数据</div>
        </div>
      </article>

      <article class="panel boxoffice-panel">
        <header class="panel-header compact"><div><span class="panel-kicker">SALES COMPARISON</span><h2>影片销售对比</h2></div></header>
        <div ref="boxOfficeChartRef" class="chart chart-medium"></div>
        <div v-if="!boxOfficeData.length && !loading" class="chart-empty">暂无影片销售数据</div>
      </article>

      <article class="panel structure-panel">
        <header class="panel-header compact"><div><span class="panel-kicker">BUSINESS MATRIX</span><h2>业务资源矩阵</h2></div></header>
        <div class="structure-body">
          <div ref="structureChartRef" class="chart chart-medium"></div>
          <div class="structure-legend">
            <div v-for="item in structureItems" :key="item.name" class="legend-row">
              <i :style="{ background: item.color, boxShadow: `0 0 12px ${item.color}` }"></i><span>{{ item.name }}</span><strong>{{ formatNumber(item.value) }}</strong>
            </div>
          </div>
        </div>
      </article>

      <article class="panel operation-panel">
        <header class="panel-header compact">
          <div><span class="panel-kicker">OPERATION HUB</span><h2>运营快捷入口</h2></div><span class="update-time">更新于 {{ lastUpdated }}</span>
        </header>
        <div class="operation-grid">
          <button v-for="action in quickActions" :key="action.path" class="operation-card" @click="router.push(action.path)">
            <span class="operation-icon"><el-icon><component :is="action.icon" /></el-icon></span>
            <span class="operation-copy"><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
            <el-icon class="operation-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </article>
    </section>

    <footer class="screen-footer">
      <span><i></i>数据中心连接正常</span><span>JINLI CINEMA · DIGITAL OPERATION PLATFORM</span><span>自动刷新周期 60s</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowRight, Calendar, DataAnalysis, Film, Money, OfficeBuilding, Refresh, Tickets, TrendCharts, User } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface DashboardStats { movieCount?: number; cinemaCount?: number; todayOrderCount?: number; todayRevenue?: number | string; userCount?: number }
interface BoxOfficeItem { movieId?: number; movieTitle?: string; boxOffice?: number | string; ticketCount?: number }
interface RevenueItem { date: string; revenue: number | string; orderCount: number }

const router = useRouter()
const loading = ref(false)
const stats = ref<DashboardStats>({})
const boxOfficeData = ref<BoxOfficeItem[]>([])
const revenueData = ref<RevenueItem[]>([])
const revenueChartRef = ref<HTMLElement>()
const boxOfficeChartRef = ref<HTMLElement>()
const structureChartRef = ref<HTMLElement>()
const now = ref(new Date())
const lastUpdated = ref('--:--:--')
let clockTimer: number | undefined
let refreshTimer: number | undefined

const pad = (value: number) => String(value).padStart(2, '0')
const formatDateParam = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const currentTime = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}:${pad(now.value.getSeconds())}`)
const currentDate = computed(() => `${now.value.getFullYear()}年${pad(now.value.getMonth() + 1)}月${pad(now.value.getDate())}日`)
const formatNumber = (value: number | string | undefined) => {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : '0'
}

const metricCards = computed(() => [
  { key: 'revenue', label: '今日营收', value: formatNumber(stats.value.todayRevenue), prefix: '¥', unit: '', description: '已支付订单收入', icon: Money, tone: 'orange' },
  { key: 'orders', label: '今日订单', value: formatNumber(stats.value.todayOrderCount), prefix: '', unit: '笔', description: '当日订单总量', icon: Tickets, tone: 'cyan' },
  { key: 'movies', label: '在库影片', value: formatNumber(stats.value.movieCount), prefix: '', unit: '部', description: '全量影片资源', icon: Film, tone: 'violet' },
  { key: 'cinemas', label: '合作影院', value: formatNumber(stats.value.cinemaCount), prefix: '', unit: '家', description: '平台影院数量', icon: OfficeBuilding, tone: 'green' },
  { key: 'users', label: '平台用户', value: formatNumber(stats.value.userCount), prefix: '', unit: '人', description: '累计注册用户', icon: User, tone: 'pink' }
])
const periodRevenue = computed(() => revenueData.value.reduce((total, item) => total + Number(item.revenue || 0), 0))
const maxBoxOffice = computed(() => Math.max(...boxOfficeData.value.map(item => Number(item.boxOffice || 0)), 1))
const getRankWidth = (value: number | string | undefined) => `${Math.max(4, Number(value || 0) / maxBoxOffice.value * 100)}%`
const structureItems = computed(() => [
  { name: '影片资源', value: Number(stats.value.movieCount || 0), color: '#ff8a3d' },
  { name: '影院网络', value: Number(stats.value.cinemaCount || 0), color: '#28d7ff' },
  { name: '用户规模', value: Number(stats.value.userCount || 0), color: '#8b7cff' },
  { name: '今日订单', value: Number(stats.value.todayOrderCount || 0), color: '#45e0a8' }
])
const quickActions = [
  { label: '智能排片', description: '预测上座率与模拟调整', path: '/smart-scheduling', icon: TrendCharts },
  { label: '排片调度', description: '管理电影场次与时间', path: '/showtime', icon: Calendar },
  { label: '订单中心', description: '查看订单及支付状态', path: '/order', icon: Tickets },
  { label: '影片运营', description: '维护上映影片资料', path: '/movie', icon: Film },
  { label: '影院管理', description: '管理影院与影厅资源', path: '/cinema', icon: DataAnalysis }
]

const chartTextColor = '#68716b'
const chartGridColor = 'rgba(101, 112, 104, 0.15)'
const initCharts = () => {
  if (revenueChartRef.value) {
    const chart = echarts.getInstanceByDom(revenueChartRef.value) || echarts.init(revenueChartRef.value)
    chart.setOption({ animationDuration: 900, tooltip: { trigger: 'axis', backgroundColor: 'rgba(240,242,237,.98)', borderColor: '#c9cdc7', textStyle: { color: '#333b36' }, extraCssText: 'box-shadow: 4px 4px 10px rgba(172,177,171,.45), -4px -4px 10px rgba(255,255,255,.8); border-radius: 10px;' }, grid: { left: 16, right: 18, top: 36, bottom: 8, containLabel: true }, xAxis: { type: 'category', boundaryGap: false, data: revenueData.value.map(item => item.date.slice(5)), axisLine: { lineStyle: { color: chartGridColor } }, axisLabel: { color: chartTextColor }, axisTick: { show: false } }, yAxis: { type: 'value', splitLine: { lineStyle: { color: chartGridColor, type: 'dashed' } }, axisLabel: { color: chartTextColor, formatter: (value: number) => `¥${value}` } }, series: [{ name: '营收', type: 'line', smooth: true, symbol: 'circle', symbolSize: 8, data: revenueData.value.map(item => Number(item.revenue || 0)), lineStyle: { color: '#ff8400', width: 3 }, itemStyle: { color: '#ff8400', borderColor: '#f3f4f0', borderWidth: 2 }, areaStyle: { color: 'rgba(255,132,0,.12)' } }] }, true)
  }
  if (boxOfficeChartRef.value) {
    const data = boxOfficeData.value.slice(0, 6).reverse()
    const chart = echarts.getInstanceByDom(boxOfficeChartRef.value) || echarts.init(boxOfficeChartRef.value)
    chart.setOption({ tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: 'rgba(240,242,237,.98)', borderColor: '#c9cdc7', textStyle: { color: '#333b36' }, extraCssText: 'box-shadow: 4px 4px 10px rgba(172,177,171,.45), -4px -4px 10px rgba(255,255,255,.8); border-radius: 10px;' }, grid: { left: 12, right: 24, top: 8, bottom: 8, containLabel: true }, xAxis: { type: 'value', splitLine: { lineStyle: { color: chartGridColor } }, axisLabel: { color: chartTextColor } }, yAxis: { type: 'category', data: data.map(item => item.movieTitle || '未命名'), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#4d5751', width: 72, overflow: 'truncate' } }, series: [{ type: 'bar', barWidth: 9, data: data.map(item => Number(item.boxOffice || 0)), showBackground: true, backgroundStyle: { color: 'rgba(122,130,124,.08)', borderRadius: 8 }, itemStyle: { borderRadius: 8, color: '#ff8400' } }] }, true)
  }
  if (structureChartRef.value) {
    const chart = echarts.getInstanceByDom(structureChartRef.value) || echarts.init(structureChartRef.value)
    chart.setOption({ tooltip: { trigger: 'item', backgroundColor: 'rgba(240,242,237,.98)', borderColor: '#c9cdc7', textStyle: { color: '#333b36' }, extraCssText: 'box-shadow: 4px 4px 10px rgba(172,177,171,.45), -4px -4px 10px rgba(255,255,255,.8); border-radius: 10px;' }, series: [{ type: 'pie', radius: ['58%', '78%'], center: ['50%', '50%'], startAngle: 100, label: { show: false }, labelLine: { show: false }, itemStyle: { borderColor: '#e7e8e5', borderWidth: 4, borderRadius: 6 }, data: structureItems.value.map(item => ({ name: item.name, value: item.value || 0.001, itemStyle: { color: item.color } })) }], graphic: [{ type: 'text', left: 'center', top: '43%', style: { text: '业务\n结构', fill: '#3f4943', font: '700 15px Geist', textAlign: 'center', lineHeight: 22 } }] }, true)
  }
}

const loadAllData = async () => {
  if (loading.value) return
  loading.value = true
  const endDate = new Date()
  const startDate = new Date()
  startDate.setDate(endDate.getDate() - 6)
  try {
    const [dashboard, ranking, revenue] = await Promise.all([
      request.get<any, DashboardStats>('/statistics/dashboard'), request.get<any, BoxOfficeItem[]>('/statistics/box-office', { params: { limit: 10 } }),
      request.get<any, RevenueItem[]>('/statistics/revenue', { params: { startDate: formatDateParam(startDate), endDate: formatDateParam(endDate) } })
    ])
    stats.value = dashboard || {}; boxOfficeData.value = ranking || []; revenueData.value = revenue || []
    lastUpdated.value = currentTime.value
    await nextTick(); initCharts()
  } catch (error) { console.error('加载统计数据失败:', error) } finally { loading.value = false }
}
const resizeCharts = () => [revenueChartRef.value, boxOfficeChartRef.value, structureChartRef.value].forEach(element => { if (element) echarts.getInstanceByDom(element)?.resize() })
onMounted(() => { loadAllData(); clockTimer = window.setInterval(() => { now.value = new Date() }, 1000); refreshTimer = window.setInterval(loadAllData, 60000); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { if (clockTimer) window.clearInterval(clockTimer); if (refreshTimer) window.clearInterval(refreshTimer); window.removeEventListener('resize', resizeCharts); [revenueChartRef.value, boxOfficeChartRef.value, structureChartRef.value].forEach(element => { if (element) echarts.getInstanceByDom(element)?.dispose() }) })
</script>

<style scoped>
.dashboard-page { --screen-bg:#050d1d; --screen-card:rgba(9,24,49,.88); --screen-border:rgba(71,142,211,.34); --screen-text:#edf6ff; --screen-muted:#8093b8; position:relative; min-height:calc(100vh - 144px); padding:24px; overflow:hidden; border:1px solid #18395e; border-radius:22px; color:var(--screen-text); background:radial-gradient(circle at 50% -10%,rgba(45,105,176,.25),transparent 40%),radial-gradient(circle at 100% 70%,rgba(96,55,181,.16),transparent 30%),linear-gradient(145deg,#07152b 0%,var(--screen-bg) 48%,#061329 100%); box-shadow:0 22px 60px rgba(3,10,24,.25); isolation:isolate; }
.dashboard-grid-overlay { position:absolute; inset:0; z-index:-1; opacity:.24; background-image:linear-gradient(rgba(56,115,174,.16) 1px,transparent 1px),linear-gradient(90deg,rgba(56,115,174,.16) 1px,transparent 1px); background-size:42px 42px; mask-image:linear-gradient(to bottom,rgba(0,0,0,.8),transparent 85%); }
.screen-header{display:flex;align-items:center;justify-content:space-between;gap:24px;margin-bottom:22px}.eyebrow,.panel-kicker{color:#52d9ff;font-family:'JetBrains Mono',monospace;font-size:10px;font-weight:700;letter-spacing:.16em}.eyebrow{display:flex;align-items:center;gap:8px;margin-bottom:7px}.live-dot,.status-dot{width:7px;height:7px;border-radius:50%;background:#45e0a8;box-shadow:0 0 14px #45e0a8;animation:pulse 1.8s infinite}.title-group h1{margin:0;color:#fff;font-size:clamp(24px,2.1vw,34px);font-weight:800;letter-spacing:-.035em;text-shadow:0 0 24px rgba(92,190,255,.18)}.title-group p{margin:7px 0 0;color:var(--screen-muted);font-size:12px}.header-actions{display:flex;align-items:center;gap:14px}.system-status,.clock-block{display:flex;align-items:center;gap:10px;padding:10px 14px;border:1px solid var(--screen-border);border-radius:11px;background:rgba(4,15,34,.72)}.system-status div,.clock-block{flex-direction:column}.system-status strong,.clock-block strong{font-family:'JetBrains Mono',monospace;font-size:12px}.system-status span,.clock-block span{color:var(--screen-muted);font-size:9px}.clock-block{align-items:flex-end;gap:2px}.clock-block strong{color:#52d9ff;font-size:17px;letter-spacing:.05em}.refresh-button{height:44px;display:inline-flex;align-items:center;gap:7px;padding:0 16px;border:1px solid rgba(255,138,61,.5);border-radius:11px;color:#fff;background:linear-gradient(135deg,rgba(255,132,0,.94),rgba(255,91,46,.88));font-size:12px;font-weight:700;cursor:pointer;box-shadow:0 6px 20px rgba(255,105,44,.2)}.refresh-button:disabled{opacity:.65;cursor:wait}.spinning{animation:spin .8s linear infinite}
.metric-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px;margin-bottom:14px}.metric-card{position:relative;min-width:0;padding:17px 18px 15px;overflow:hidden;border:1px solid var(--screen-border);border-radius:14px;background:linear-gradient(145deg,rgba(14,34,65,.92),rgba(7,20,43,.88));transition:transform .2s ease,border-color .2s ease}.metric-card::after{content:'';position:absolute;right:-28px;bottom:-44px;width:115px;height:115px;border-radius:50%;background:var(--glow);filter:blur(30px);opacity:.18}.metric-card:hover{transform:translateY(-3px);border-color:var(--accent)}.metric-card.orange{--accent:#ff8a3d;--glow:#ff8a3d}.metric-card.cyan{--accent:#28d7ff;--glow:#28d7ff}.metric-card.violet{--accent:#8b7cff;--glow:#8b7cff}.metric-card.green{--accent:#45e0a8;--glow:#45e0a8}.metric-card.pink{--accent:#ff6ba6;--glow:#ff6ba6}.metric-card::before{content:'';position:absolute;top:0;left:18px;width:42px;height:2px;background:var(--accent);box-shadow:0 0 12px var(--accent)}.metric-topline,.metric-foot{display:flex;align-items:center;justify-content:space-between;gap:8px}.metric-label{color:#aebdda;font-size:12px;font-weight:600}.metric-icon{width:30px;height:30px;display:grid;place-items:center;border:1px solid color-mix(in srgb,var(--accent) 45%,transparent);border-radius:9px;color:var(--accent);background:color-mix(in srgb,var(--accent) 10%,transparent)}.metric-value{margin:12px 0 9px;color:#fff;font-family:'JetBrains Mono',monospace;font-size:clamp(23px,2vw,31px);font-weight:800;white-space:nowrap}.metric-prefix{margin-right:2px;color:var(--accent);font-size:15px}.metric-value small{margin-left:5px;color:var(--screen-muted);font-family:'Geist',sans-serif;font-size:11px;font-weight:600}.metric-foot{color:var(--screen-muted);font-size:9px}.metric-state{display:inline-flex;align-items:center;gap:4px;color:#45e0a8}.metric-state i{width:4px;height:4px;border-radius:50%;background:currentColor;box-shadow:0 0 7px currentColor}
.content-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:14px}.panel{position:relative;min-width:0;min-height:300px;padding:18px;overflow:hidden;border:1px solid var(--screen-border);border-radius:15px;background:linear-gradient(145deg,var(--screen-card),rgba(5,17,37,.92));box-shadow:inset 0 1px rgba(255,255,255,.025)}.panel::after{content:'';position:absolute;inset:0;z-index:-1;pointer-events:none;background:linear-gradient(110deg,transparent 0 70%,rgba(42,215,255,.035))}.panel-corner{position:absolute;width:18px;height:18px;pointer-events:none}.panel-corner.top-left{top:-1px;left:-1px;border-top:2px solid #52d9ff;border-left:2px solid #52d9ff}.panel-corner.bottom-right{right:-1px;bottom:-1px;border-right:2px solid #ff8a3d;border-bottom:2px solid #ff8a3d}.revenue-panel{grid-column:span 7}.ranking-panel{grid-column:span 5}.boxoffice-panel,.structure-panel,.operation-panel{grid-column:span 4}.panel-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:8px}.panel-header h2{margin:5px 0 0;color:#f2f7ff;font-size:16px;font-weight:700}.panel-header.compact{margin-bottom:2px}.panel-summary{text-align:right}.panel-summary span{display:block;color:var(--screen-muted);font-size:9px}.panel-summary strong{display:block;margin-top:3px;color:#ff9e5d;font-family:'JetBrains Mono',monospace;font-size:18px}.text-button{display:inline-flex;align-items:center;gap:4px;padding:5px 0;border:0;color:#52d9ff;background:transparent;font-size:10px;cursor:pointer}.chart{width:100%}.chart-large{height:260px}.chart-medium{height:230px}.chart-empty,.list-empty{position:absolute;inset:70px 18px 18px;display:grid;place-items:center;color:#597092;font-size:12px}
.ranking-list{display:flex;flex-direction:column;gap:13px;padding-top:8px}.ranking-row{display:flex;align-items:center;gap:12px}.rank-index{width:28px;flex:0 0 auto;color:#526886;font-family:'JetBrains Mono',monospace;font-size:12px;font-weight:700}.rank-index.top{color:#ff9e5d;text-shadow:0 0 12px rgba(255,138,61,.6)}.rank-main{min-width:0;flex:1}.rank-title-row{display:flex;justify-content:space-between;gap:12px}.rank-title-row strong{overflow:hidden;color:#dce8f8;font-size:11px;font-weight:600;text-overflow:ellipsis;white-space:nowrap}.rank-title-row span{color:#52d9ff;font-family:'JetBrains Mono',monospace;font-size:10px}.rank-track{height:3px;margin:6px 0 4px;overflow:hidden;border-radius:3px;background:rgba(255,255,255,.06)}.rank-track i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#ff7f32,#ffc276);box-shadow:0 0 10px rgba(255,138,61,.5)}.rank-main small{color:#5f7394;font-size:8px}
.structure-body{display:grid;grid-template-columns:minmax(0,1fr) 120px;align-items:center}.structure-legend{display:flex;flex-direction:column;gap:13px}.legend-row{display:grid;grid-template-columns:7px 1fr auto;align-items:center;gap:7px;font-size:10px}.legend-row i{width:6px;height:6px;border-radius:50%}.legend-row span{color:var(--screen-muted)}.legend-row strong{color:#dce8f8;font-family:'JetBrains Mono',monospace}.update-time{color:#5d7397;font-family:'JetBrains Mono',monospace;font-size:9px}.operation-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding-top:16px}.operation-card{min-width:0;display:flex;align-items:center;gap:9px;padding:13px 11px;border:1px solid rgba(72,117,167,.24);border-radius:11px;color:#fff;background:rgba(12,31,59,.72);text-align:left;cursor:pointer;transition:border-color .18s,transform .18s,background .18s}.operation-card:hover{transform:translateY(-2px);border-color:rgba(82,217,255,.58);background:rgba(18,43,78,.9)}.operation-icon{width:32px;height:32px;display:grid;place-items:center;flex:0 0 auto;border-radius:9px;color:#52d9ff;background:rgba(40,215,255,.1)}.operation-copy{min-width:0;display:flex;flex:1;flex-direction:column;gap:3px}.operation-copy strong{font-size:11px}.operation-copy small{overflow:hidden;color:#6f84a6;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.operation-arrow{color:#526987;font-size:12px}.screen-footer{display:flex;justify-content:space-between;gap:16px;margin-top:14px;padding:0 4px;color:#526987;font-family:'JetBrains Mono',monospace;font-size:8px;letter-spacing:.08em}.screen-footer span:first-child{display:flex;align-items:center;gap:6px;color:#45e0a8}.screen-footer i{width:5px;height:5px;border-radius:50%;background:currentColor;box-shadow:0 0 8px currentColor}
@keyframes pulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.5;transform:scale(.75)}}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:1400px){.metric-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.boxoffice-panel,.structure-panel{grid-column:span 6}.operation-panel{grid-column:span 12}.operation-grid{grid-template-columns:repeat(4,1fr)}}
@media(max-width:1050px){.screen-header{align-items:flex-start}.system-status{display:none}.revenue-panel,.ranking-panel,.boxoffice-panel,.structure-panel{grid-column:span 12}}
@media(max-width:760px){.dashboard-page{min-height:auto;padding:16px;border-radius:16px}.screen-header,.header-actions{align-items:flex-start;flex-direction:column}.header-actions{width:100%;display:grid;grid-template-columns:1fr auto}.clock-block{align-items:flex-start}.title-group p{line-height:1.6}.metric-grid{grid-template-columns:1fr 1fr}.metric-card:first-child{grid-column:1/-1}.operation-grid{grid-template-columns:1fr 1fr}.structure-body{grid-template-columns:1fr}.structure-legend{display:grid;grid-template-columns:1fr 1fr}.screen-footer{flex-direction:column}}
@media(max-width:480px){.metric-grid,.operation-grid{grid-template-columns:1fr}.metric-card:first-child{grid-column:auto}}

/* 与管理后台一致的浅色新拟物主题 */
.dashboard-page {
  --soft-surface: #e7e8e5;
  --soft-surface-light: #eef0eb;
  --soft-shadow-dark: #c2c5bf;
  --soft-shadow-light: #fafbf8;
  --screen-card: var(--soft-surface);
  --screen-border: transparent;
  --screen-text: #303833;
  --screen-muted: #6b746e;
  border: 0;
  color: var(--screen-text);
  background: var(--soft-surface);
  box-shadow:
    10px 10px 22px rgba(188, 192, 185, 0.82),
    -10px -10px 22px rgba(250, 251, 248, 0.92);
}

.dashboard-grid-overlay,
.panel-corner {
  display: none;
}

.eyebrow,
.panel-kicker {
  color: #d66f00;
}

.title-group h1 {
  color: #2d3530;
  text-shadow: none;
}

.title-group p,
.system-status span,
.clock-block span {
  color: var(--screen-muted);
}

.system-status,
.clock-block,
.metric-card,
.panel,
.operation-card {
  border: 0;
  background: var(--soft-surface);
  box-shadow:
    7px 7px 15px var(--soft-shadow-dark),
    -7px -7px 15px var(--soft-shadow-light);
}

.system-status,
.clock-block {
  min-height: 44px;
}

.system-status strong,
.clock-block strong {
  color: #3b443e;
}

.clock-block strong {
  color: #d66f00;
}

.live-dot,
.status-dot {
  background: #42a77a;
  box-shadow: 0 0 0 4px rgba(66, 167, 122, 0.12);
  animation: none;
}

.refresh-button {
  border: 0;
  color: #9f5600;
  background: var(--soft-surface);
  box-shadow:
    6px 6px 12px var(--soft-shadow-dark),
    -6px -6px 12px var(--soft-shadow-light);
  transition: box-shadow 0.3s ease-in-out, color 0.3s ease-in-out;
}

.refresh-button:hover:not(:disabled) {
  color: #ff8400;
  box-shadow:
    3px 3px 7px var(--soft-shadow-dark),
    -3px -3px 7px var(--soft-shadow-light);
}

.refresh-button:active:not(:disabled) {
  box-shadow:
    inset 4px 4px 8px var(--soft-shadow-dark),
    inset -4px -4px 8px var(--soft-shadow-light);
}

.metric-card {
  transition: box-shadow 0.3s ease-in-out;
}

.metric-card:hover {
  transform: none;
  border-color: transparent;
  box-shadow:
    4px 4px 9px var(--soft-shadow-dark),
    -4px -4px 9px var(--soft-shadow-light);
}

.metric-card::after {
  display: none;
}

.metric-card::before {
  top: 15px;
  left: 18px;
  width: 5px;
  height: 24px;
  border-radius: 999px;
  box-shadow: none;
}

.metric-label {
  padding-left: 12px;
  color: #606a64;
}

.metric-icon {
  border: 0;
  box-shadow:
    inset 3px 3px 6px rgba(184, 188, 182, 0.8),
    inset -3px -3px 6px rgba(250, 251, 248, 0.9);
}

.metric-value,
.panel-header h2,
.rank-title-row strong,
.legend-row strong,
.operation-card {
  color: #303833;
}

.metric-foot,
.rank-main small,
.legend-row span,
.operation-copy small,
.update-time,
.screen-footer {
  color: #747d77;
}

.panel::after {
  display: none;
}

.panel-summary strong {
  color: #d66f00;
}

.text-button {
  color: #c76600;
}

.ranking-row {
  padding: 7px 9px;
  border-radius: 10px;
  box-shadow:
    inset 2px 2px 5px rgba(190, 194, 188, 0.62),
    inset -2px -2px 5px rgba(250, 251, 248, 0.86);
}

.rank-index {
  color: #8a918d;
}

.rank-index.top,
.rank-title-row span {
  color: #d66f00;
  text-shadow: none;
}

.rank-track {
  background: rgba(116, 125, 119, 0.12);
  box-shadow:
    inset 2px 2px 4px rgba(184, 188, 182, 0.72),
    inset -2px -2px 4px rgba(250, 251, 248, 0.8);
}

.rank-track i {
  background: #ff8400;
  box-shadow: none;
}

.operation-card {
  transition: box-shadow 0.3s ease-in-out;
}

.operation-card:hover {
  transform: none;
  border-color: transparent;
  background: var(--soft-surface);
  box-shadow:
    3px 3px 7px var(--soft-shadow-dark),
    -3px -3px 7px var(--soft-shadow-light);
}

.operation-card:active {
  box-shadow:
    inset 4px 4px 8px var(--soft-shadow-dark),
    inset -4px -4px 8px var(--soft-shadow-light);
}

.operation-icon {
  color: #d66f00;
  background: rgba(255, 132, 0, 0.08);
  box-shadow:
    inset 2px 2px 5px rgba(184, 188, 182, 0.65),
    inset -2px -2px 5px rgba(250, 251, 248, 0.86);
}

.operation-arrow {
  color: #8b938e;
}

.chart-empty,
.list-empty {
  color: #8b938e;
}

.screen-footer span:first-child,
.metric-state {
  color: #378b67;
}

@media (max-width: 760px) {
  .dashboard-page {
    box-shadow:
      5px 5px 11px rgba(188, 192, 185, 0.78),
      -5px -5px 11px rgba(250, 251, 248, 0.9);
  }

  .metric-card,
  .panel,
  .operation-card,
  .system-status,
  .clock-block {
    box-shadow:
      4px 4px 9px var(--soft-shadow-dark),
      -4px -4px 9px var(--soft-shadow-light);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-page *,
  .dashboard-page *::before,
  .dashboard-page *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
