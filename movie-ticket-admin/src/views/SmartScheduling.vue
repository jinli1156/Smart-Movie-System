<template>
  <div class="scheduling-workspace">
    <header class="workspace-header">
      <div><span class="eyebrow">CINEMA PROGRAMMING</span><h1>排好每一场好电影</h1><p>从一天的排片开始，让每个影厅都有合适的安排。</p></div>
      <div class="board-actions"><el-button :disabled="busy" @click="openSavedDrafts">暂存草案列表{{ savedDrafts.length ? `（${savedDrafts.length}）` : '' }}</el-button><el-button :disabled="busy || !cinemaId" @click="insightsVisible = true">经营分析</el-button></div>
    </header>
    <section class="scope-bar" aria-label="排片范围">
      <label>影院<el-select :model-value="cinemaId" aria-label="影院" :disabled="busy" @change="(id: number) => changeScope(id, targetDate)"><el-option v-for="cinema in cinemas" :key="cinema.id" :label="cinema.name" :value="cinema.id!" /></el-select></label>
      <label>排片日期<el-date-picker :model-value="targetDate" aria-label="排片日期" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="disabledDate" :disabled="busy" @update:model-value="(date: string) => changeScope(cinemaId, date)" /></label>
      <el-button :disabled="busy || !cinemaId" @click="changeScope(cinemaId, tomorrow())">明天</el-button>
      <div class="scope-note"><span class="status-dot" />{{ hasDraft ? '草案尚未应用' : '正在查看已发布排片' }}<small>{{ baseline.length }} 场已排 · {{ baseline.filter(s => s.locked).length }} 场锁定</small></div>
      <el-button :loading="loading" :disabled="busy" @click="refresh">刷新排片</el-button>
    </section>
    <el-alert v-if="currentSaved && !hasDraft" type="info" :closable="false" show-icon class="workspace-alert" :title="`当前影院和日期有暂存草案（${formatSavedAt(currentSaved.savedAt)}），可从暂存草案列表恢复`" />
    <el-alert v-if="savedError" type="warning" :closable="false" show-icon class="workspace-alert" :title="`暂存草案读取失败：${savedError}。可在暂存草案列表中重试。`" />
    <el-alert v-if="staleDraft" type="warning" :closable="false" show-icon class="workspace-alert" title="暂存后排片或售票状态已变化。草案已恢复供参考，请重新生成草案后再检查并应用。" />
    <div class="workbench-grid">
      <aside class="strategy-panel">
        <div class="section-label"><span>01</span><h2>选择排片策略</h2></div>
        <div class="strategy-options" role="group" aria-label="经营策略">
          <button v-for="option in strategies" :key="option.id" class="strategy-option" :class="{ selected: strategy === option.id }" :aria-pressed="strategy === option.id" :disabled="busy" @click="selectStrategy(option.id)"><span class="strategy-icon">{{ option.icon }}</span><span><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span><span class="radio-mark" /></button>
        </div>
        <el-form label-position="top" class="plan-form" :disabled="busy">
          <el-form-item label="参与影片"><el-select v-model="settings.movieIds" multiple collapse-tags collapse-tags-tooltip filterable clearable placeholder="全部正在上映影片" aria-label="参与影片"><el-option v-for="movie in eligibleMovies" :key="movie.id" :label="movie.title" :value="movie.id!" /></el-select></el-form-item>
          <div class="two-fields"><el-form-item label="每厅目标场次"><el-input-number v-model="settings.targetShowtimesPerHall" :min="1" :max="8" controls-position="right" aria-label="每厅目标场次" /></el-form-item><el-form-item label="清场时间 / 分钟"><el-input-number v-model="settings.cleaningMinutes" :min="minimumCleaning" :max="60" :step="5" controls-position="right" aria-label="清场时间" /></el-form-item></div>
          <details class="advanced"><summary>高级设置 <span>影片分配与优化参数</span></summary><div class="advanced-fields">
            <el-form-item label="单片最多场次"><el-input-number v-model="settings.maxShowtimesPerMovie" :min="1" :max="20" /></el-form-item>
            <el-form-item label="最少影片数"><el-input-number v-model="settings.minDistinctMovies" :min="1" :max="Math.max(1, eligibleMovies.length)" /></el-form-item>
            <el-form-item label="每厅黄金时段最少场次"><el-input-number v-model="settings.minGoldenShowtimesPerHall" :min="0" :max="3" /></el-form-item>
            <p>黄金时段 18:00–22:00；自动排片从 10:00 开始，最晚 23:30 开场。</p>
            <el-form-item label="预计收入权重"><el-slider v-model="settings.revenueWeight" :max="100" /></el-form-item>
            <el-form-item label="上座率权重"><el-slider v-model="settings.occupancyWeight" :max="100" /></el-form-item>
            <el-form-item label="影片多样性权重"><el-slider v-model="settings.diversityWeight" :max="100" /></el-form-item>
            <p :class="{ 'error-text': weightSum !== 100 }">权重合计 {{ weightSum }}%（需为 100%）</p>
            <el-form-item label="优化时间上限 / 秒"><el-input-number v-model="settings.solverSeconds" :min="1" :max="30" /></el-form-item>
          </div></details>
        </el-form>
        <el-button type="primary" class="generate-button" :loading="generating" :disabled="busy || !workspace || !eligibleMovies.length" @click="generate">{{ generating ? '正在生成排片草案' : hasDraft ? '重新生成草案' : '生成排片草案' }}</el-button>
        <p class="strategy-footnote">已售、待付款与已开场场次自动保留。生成后可在时间轴中逐场调整。</p>
      </aside>
      <main class="board-panel" v-loading="loading">
        <div class="board-heading"><div class="section-label"><span>02</span><div><h2>{{ hasDraft ? '预览与调整草案' : '当日排片时间轴' }}</h2><p>拖到其他场次上交换位置，拖到空白处调整时间与影厅</p></div></div><div class="board-actions"><el-radio-group v-if="hasDraft" v-model="viewMode" size="small" aria-label="查看方案"><el-radio-button value="draft">排片草案</el-radio-button><el-radio-button value="original">原排片</el-radio-button></el-radio-group><el-button :disabled="busy || !workspace || !eligibleMovies.length" @click="addShowtime">添加场次</el-button></div></div>
        <div class="board-legend"><span><i class="legend-fill" />可调整场次</span><span><i class="legend-locked" />已锁定</span><span><i class="legend-cleaning" />清场间隔</span><span class="golden-key">18:00–22:00 黄金时段</span></div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="workspace-alert" />
        <el-alert v-if="localIssues.length" :title="`${localIssues.length} 处需要调整`" :description="localIssues.slice(0, 3).join('；')" type="warning" :closable="false" show-icon class="workspace-alert" />
        <SchedulingTimeline v-if="workspace?.halls.length" :items="visibleItems" :halls="workspace.halls" :target-date="targetDate" :cleaning-minutes="hasDraft ? activeCleaning : Math.max(settings.cleaningMinutes, minimumCleaning)" :busy="busy" :read-only="hasDraft && viewMode === 'original'" :labels="visibleItems.map(row => hasDraft && viewMode === 'draft' ? changeLabel(row) : '已排')" @edit="editShowtime" @move="moveShowtime" @swap="swapShowtimes" />
        <el-empty v-else-if="!loading" :description="workspace ? '该影院暂无影厅，请先在影厅管理中添加' : '选择影院后开始排片'" />
        <div class="board-caption"><span>{{ targetDate }} · {{ visibleItems.length }} 场放映 · {{ new Set(visibleItems.map(s => s.movieId)).size }} 部影片</span><span>横向滚动查看全天 · 凌晨标记“次日”</span></div>
      </main>
    </div>
    <section v-if="hasDraft" class="review-panel">
      <div class="review-heading"><div class="section-label"><span>03</span><div><h2>检查变化，再应用排片</h2><p>{{ checkResult ? '已按当前草案重新计算；提交时还会复查售票状态与时间冲突。' : '草案已变化，请检查后查看最新预测与场次差异。' }}</p></div></div><el-button :loading="checking" :disabled="busy || staleDraft || !!localIssues.length" @click="checkDraft">检查并比较</el-button></div>
      <div class="change-summary"><span><b>{{ changeCounts.ADD }}</b> 新增</span><span><b>{{ changeCounts.UPDATE }}</b> 调整</span><span><b>{{ changeCounts.REMOVE }}</b> 移除</span><span><b>{{ draft.filter(s => s.locked).length }}</b> 锁定保留</span></div>
      <template v-if="checkResult"><div class="comparison-grid"><article><span>预计票房</span><strong>¥{{ money(checkResult.original.expectedRevenue) }} <i>→</i> ¥{{ money(checkResult.draft.expectedRevenue) }}</strong></article><article><span>预计上座率</span><strong>{{ rate(checkResult.original.occupancyRate) }} <i>→</i> {{ rate(checkResult.draft.occupancyRate) }}</strong></article><article><span>影片覆盖</span><strong>{{ checkResult.original.distinctMovies }} <i>→</i> {{ checkResult.draft.distinctMovies }} 部</strong></article></div><p class="data-note">{{ checkResult.draft.historicalSamples ? `依据本影院 ${checkResult.draft.historicalSamples} 个历史场次估算，预测不代表实际收益。` : '暂无历史样本，预测为冷启动估计；建议先少量试排并观察实际售票。' }}</p></template>
      <details class="change-details"><summary>查看逐场变化（{{ changes.length }} 项）</summary><div v-for="(change, index) in changes" :key="index" class="change-row"><el-tag :type="change.type === 'REMOVE' ? 'danger' : change.type === 'ADD' ? 'success' : 'warning'" size="small">{{ { ADD: '新增', UPDATE: '调整', REMOVE: '移除' }[change.type] }}</el-tag><span v-if="change.before">{{ describe(change.before) }}</span><span v-if="change.type === 'UPDATE'">→</span><span v-if="change.after">{{ describe(change.after) }}</span></div></details>
      <details v-if="optimization && !manuallyEdited" class="explanation"><summary>查看推荐依据</summary><p>本草案按“{{ generatedStrategy }}”生成，在影片时长、清场间隔及已锁定场次的约束下比较可用安排。</p><SchedulingAiAnalysis :analysis="optimization.aiAnalysis" /><details><summary>算法与方案评估</summary><p>{{ optimization.solver }} · {{ optimization.statusText }} · 求解 {{ optimization.solveTimeMs }} ms</p><p v-for="constraint in optimization.constraints" :key="constraint">{{ constraint }}</p></details></details>
      <div class="draft-save-status" role="status">{{ savedAt ? `上次暂存：${formatSavedAt(savedAt)}${hasUnsavedDraft ? ' · 有未暂存修改' : ' · 已暂存，可安全离开'}` : '草案尚未暂存' }}<small>暂存到当前账号；同一影院、同一日期保留最新一份。暂存不会发布场次，未完成的草案也可暂存。</small></div>
      <div class="publish-bar"><div><strong>{{ checkResult ? '草案检查通过' : '等待草案检查' }}</strong><small>应用后，购票端将显示新的场次安排</small></div><div><el-button :disabled="busy" @click="discard">放弃草案</el-button><el-button :loading="saving" :disabled="busy" @click="saveDraft">暂存草案</el-button><el-button type="primary" :disabled="busy || staleDraft || !checkResult || !changes.length || !!localIssues.length" @click="confirmVisible = true">确认并应用排片</el-button></div></div>
    </section>
    <el-dialog v-model="transitionVisible" title="当前草案有未暂存的修改" width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @close="finishTransition(false)">
      <p>暂存后可在下次进入工作台或刷新页面时继续编辑。暂存不会发布排片。</p>
      <p v-if="savedAt">放弃本次修改后，之前暂存的草案仍然保留。</p>
      <template #footer><div class="draft-transition-actions"><el-button :disabled="saving" @click="finishTransition(false)">继续编辑</el-button><el-button :disabled="saving" @click="finishTransition(true)">{{ transitionIsLeave ? '放弃修改并离开' : '放弃修改并继续' }}</el-button><el-button type="primary" :loading="saving" @click="saveBeforeTransition">{{ transitionIsLeave ? '暂存并离开' : '暂存并继续' }}</el-button></div></template>
    </el-dialog>
    <el-dialog v-model="editorVisible" :title="editing?.locked ? '场次详情 · 已锁定' : editingIndex < 0 ? '添加放映场次' : '调整放映场次'" width="min(520px, 94vw)" :close-on-click-modal="false">
      <template v-if="editing"><el-alert v-if="editing.locked" :title="`${editing.lockReason || '已售或已开场'}，该场次保持原安排`" type="info" :closable="false" show-icon />
        <el-form label-position="top" :disabled="editing.locked" class="editor-form">
          <el-form-item v-if="editingIndex < 0" label="快速查找影片">
            <div class="movie-quick-search">
              <el-input v-model="movieSearch" :prefix-icon="Search" clearable placeholder="输入影片名、导演或类型" aria-label="搜索影片" @keydown.enter="selectFirstSearchMovie" />
              <div v-if="movieSearch.trim()" class="movie-search-results" aria-label="影片搜索结果">
                <div class="movie-search-caption" aria-live="polite">{{ matchedEditorMovies.length ? `找到 ${matchedEditorMovies.length} 部影片，点击填入` : '没有找到匹配影片，试试其他关键词' }}</div>
                <button v-for="movie in matchedEditorMovies.slice(0, 8)" :key="movie.id" type="button" class="movie-search-item" @click="selectSearchMovie(movie)"><span><strong>{{ movie.title }}</strong><small>{{ [movie.duration ? `${movie.duration} 分钟` : '120 分钟', movie.genre, movie.director].filter(Boolean).join(' · ') }}</small></span><span class="movie-search-fill">填入</span></button>
                <p v-if="matchedEditorMovies.length > 8" class="movie-search-more">仅显示前 8 项，可输入更完整的关键词缩小范围。</p>
              </div>
              <p v-else class="movie-search-guide">搜索后点击结果，或按回车填入第一部影片。</p>
            </div>
          </el-form-item>
          <el-form-item label="影片"><el-select v-model="editing.movieId" aria-label="调整影片" filterable @change="updateEditingMovie"><el-option v-for="movie in editorMovies" :key="movie.id" :label="movie.title" :value="movie.id!" /></el-select></el-form-item><el-form-item label="影厅"><el-select v-model="editing.hallId" aria-label="调整影厅"><el-option v-for="hall in workspace?.halls.filter(h => h.status === 1 || h.id === editing?.hallId)" :key="hall.id" :label="hall.name" :value="hall.id!" /></el-select></el-form-item><div class="two-fields"><el-form-item label="开场时间"><el-time-picker v-model="editingTime" aria-label="开场时间" format="HH:mm" value-format="HH:mm" :clearable="false" @change="updateEditingTime" /></el-form-item><el-form-item label="基础票价 / 元"><el-input-number v-model="editing.basePrice" aria-label="基础票价" :precision="2" :min="0.01" :max="9999.99" controls-position="right" /></el-form-item></div><p>结束时间 {{ time(editing.endTime) }} · 散场后预留 {{ activeCleaning }} 分钟清场</p></el-form>
        <p v-if="editing.predictedRate != null" class="data-note">预计上座率 {{ rate(editing.predictedRate) }} · {{ editing.confidence }}置信度。修改后需重新检查草案。</p>
      </template><template #footer><el-button v-if="editing && !editing.locked && editingIndex >= 0" type="danger" plain @click="removeEditing">移除场次</el-button><el-button @click="editorVisible = false">关闭</el-button><el-button v-if="!editing?.locked" type="primary" @click="saveEditing">保存到草案</el-button></template>
    </el-dialog>
    <el-dialog v-model="confirmVisible" title="确认应用当日排片" width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!applying" :show-close="!applying"><p>{{ cinemas.find(c => c.id === cinemaId)?.name }} · {{ targetDate }}</p><p>新增 {{ changeCounts.ADD }} 场、调整 {{ changeCounts.UPDATE }} 场、移除 {{ changeCounts.REMOVE }} 场。锁定场次保持原安排。</p><p>将按已检查的草案更新真实排片。若期间产生新订单或排片变化，本次应用会停止，请刷新后重新检查。</p><template #footer><el-button :disabled="applying" @click="confirmVisible = false">继续检查</el-button><el-button type="primary" :loading="applying" @click="applyDraft">应用到影院</el-button></template></el-dialog>
    <el-drawer v-model="insightsVisible" title="经营分析与预测工具" size="min(1280px, 95vw)" destroy-on-close><SchedulingInsights v-if="insightsVisible" :cinema-id="cinemaId" /></el-drawer>
    <SchedulingSavedDrafts v-model="savedVisible" :items="savedDrafts" :current-id="hasDraft ? savedId : ''" :loading="listingSaved" :busy="busy" :error="savedError" :is-unavailable="date => disabledDate(new Date(`${date}T00:00:00`))" @refresh="loadSavedDrafts" @restore="restoreDraft" @delete="deleteSavedDraft" />
  </div>
</template>

<script setup lang="ts">
/**
 * 排片工作台：加载影院日程，生成、微调、检查并应用草案，支持服务器暂存与恢复。
 */
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { cinemaApi } from '@/api/cinema'
import { movieApi } from '@/api/movie'
import { schedulingApi } from '@/api/scheduling'
import type { DraftShowtime, SchedulingWorkspace, SchedulingDraftCheck, SchedulingOptimizationResult, SchedulingDraftRequest, SchedulingSnapshot, SavedSchedulingSummary, SavedSchedulingDraft } from '@/api/scheduling'
import type { Cinema, Movie } from '@/types'
import SchedulingAiAnalysis from '@/components/SchedulingAiAnalysis.vue'
import SchedulingTimeline from '@/components/SchedulingTimeline.vue'
import SchedulingSavedDrafts from '@/components/SchedulingSavedDrafts.vue'
import { Search } from '@element-plus/icons-vue'
import { usePageState } from '@/composables/usePageState'
const SchedulingInsights = defineAsyncComponent(() => import('@/components/SchedulingInsights.vue'))
const pad = (n: number) => String(n).padStart(2, '0')
const dateString = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const tomorrow = () => { const d = new Date(); d.setDate(d.getDate() + 1); return dateString(d) }
const cinemas = ref<Cinema[]>([]), movies = ref<Movie[]>([]), cinemaId = ref<number>()
const targetDate = ref(tomorrow()), workspace = ref<SchedulingWorkspace>(), draft = ref<DraftShowtime[]>([])
const loading = ref(false), generating = ref(false), checking = ref(false), applying = ref(false)
const saving = ref(false), restoring = ref(false), deletingSaved = ref(false)
const busy = computed(() => loading.value || generating.value || checking.value || applying.value || saving.value || restoring.value || deletingSaved.value)
const savedDrafts = ref<SavedSchedulingSummary[]>([]), savedVisible = ref(false), listingSaved = ref(false), savedError = ref('')
const savedId = ref(''), savedAt = ref(''), savedFingerprint = ref(''), staleDraft = ref(false)
const currentSaved = computed(() => savedDrafts.value.find(s => s.cinemaId === cinemaId.value && s.targetDate === targetDate.value))
const hasDraft = ref(false), viewMode = ref('draft'), error = ref(''), manuallyEdited = ref(false)
const checkResult = ref<SchedulingDraftCheck>(), optimization = ref<SchedulingOptimizationResult>()
const confirmVisible = ref(false), insightsVisible = ref(false), editorVisible = ref(false)
const transitionVisible = ref(false), transitionIsLeave = ref(false)
let pendingTransition: Promise<boolean> | undefined, resolveTransition: ((proceed: boolean) => void) | undefined
const strategy = ref('balanced'), generatedStrategy = ref('')
const strategies = [
  { id: 'revenue', label: '收益优先', icon: '↗', description: '优先安排预计票房更高的场次', weights: [75, 20, 5] },
  { id: 'balanced', label: '均衡排片', icon: '◫', description: '兼顾收益、上座率和影片选择', weights: [50, 35, 15] },
  { id: 'diverse', label: '丰富片单', icon: '▦', description: '提高影片多样性在排片中的权重', weights: [30, 25, 45] }
]
const settings = reactive({ movieIds: [] as number[], targetShowtimesPerHall: 5, maxShowtimesPerMovie: 20, minDistinctMovies: 3, cleaningMinutes: 20, minGoldenShowtimesPerHall: 1, revenueWeight: 50, occupancyWeight: 35, diversityWeight: 15, solverSeconds: 8 })
usePageState('smart-scheduling', { cinemaId, targetDate, strategy, settings })
if (!strategies.some(option => option.id === strategy.value)) strategy.value = 'balanced'
if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate.value) || !Number.isFinite(new Date(`${targetDate.value}T00:00:00`).getTime()) || disabledDate(new Date(`${targetDate.value}T00:00:00`))) targetDate.value = tomorrow()
const draftCleaning = ref(20)
const minimumCleaning = computed(() => Math.max(10, workspace.value?.minimumCleaningMinutes || 15))
const activeCleaning = computed(() => hasDraft.value ? draftCleaning.value : minimumCleaning.value)
const weightSum = computed(() => settings.revenueWeight + settings.occupancyWeight + settings.diversityWeight)
const eligibleMovies = computed(() => movies.value.filter(m => !m.releaseDate || m.releaseDate <= targetDate.value))
const baseline = computed(() => workspace.value?.items || [])
const visibleItems = computed(() => hasDraft.value && viewMode.value === 'draft' ? draft.value : baseline.value)
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
function snapshot(): SchedulingSnapshot {
  return clone({ workspace: workspace.value!, draft: draft.value.map(({ predictedRate, confidence, ...row }) => row), settings: { ...settings }, strategy: strategy.value, generatedStrategy: generatedStrategy.value, draftCleaning: draftCleaning.value })
}
const hasUnsavedDraft = computed(() => hasDraft.value && JSON.stringify(snapshot()) !== savedFingerprint.value)
const formatSavedAt = (value: string) => value.replace('T', ' ').slice(0, 19)
/** 查询本人可访问的服务器草稿摘要。 */
async function loadSavedDrafts() {
  listingSaved.value = true; savedError.value = ''
  try { savedDrafts.value = await schedulingApi.savedDrafts(); return true } catch (e) { savedError.value = errorMessage(e); return false } finally { listingSaved.value = false }
}
async function openSavedDrafts() { savedVisible.value = true; await loadSavedDrafts() }
/** 将当前草案及快照暂存到服务器，便于稍后恢复。 */
async function saveDraft(): Promise<boolean> {
  if (busy.value || !hasDraft.value || !workspace.value || !cinemaId.value) return false
  const content = snapshot(); saving.value = true
  try {
    const saved = await schedulingApi.saveDraft(cinemaId.value, targetDate.value, content)
    savedId.value = saved.summary.id; savedAt.value = saved.summary.savedAt; savedFingerprint.value = JSON.stringify(content)
    savedDrafts.value = [saved.summary, ...savedDrafts.value.filter(s => s.cinemaId !== cinemaId.value || s.targetDate !== targetDate.value)]
    ElMessage.success('草案已暂存，可离开页面后继续编辑；尚未发布场次')
    return true
  } catch (e) { ElMessage.error(`暂存失败：${errorMessage(e)}`); return false } finally { saving.value = false }
}
/** 手动恢复与刷新恢复共用同一套快照校验和编辑状态初始化。 */
async function restoreSavedContent(saved: SavedSchedulingDraft, fresh: SchedulingWorkspace) {
  const content = saved.content
  if (!content?.workspace?.version || !Array.isArray(content.draft) || !Array.isArray(content.workspace.items) || !content.settings) throw new Error('草案数据不完整，无法恢复')
  hasDraft.value = false; settings.movieIds = []; Object.assign(settings, clone(content.settings))
  await nextTick()
  cinemaId.value = saved.summary.cinemaId; targetDate.value = saved.summary.targetDate
  workspace.value = { ...clone(content.workspace), halls: fresh.halls, minimumCleaningMinutes: fresh.minimumCleaningMinutes }
  draft.value = clone(content.draft); draftCleaning.value = content.draftCleaning
  strategy.value = strategies.some(s => s.id === content.strategy) ? content.strategy : 'balanced'
  generatedStrategy.value = content.generatedStrategy || ''; optimization.value = undefined; checkResult.value = undefined
  staleDraft.value = fresh.version !== content.workspace.version
  manuallyEdited.value = true; viewMode.value = 'draft'; hasDraft.value = true; error.value = ''; editorVisible.value = false
  savedId.value = saved.summary.id; savedAt.value = saved.summary.savedAt; savedFingerprint.value = JSON.stringify(snapshot()); savedVisible.value = false
}
/** 读取服务器草稿并恢复编辑上下文，后续发布仍需重新检查。 */
async function restoreDraft(item: SavedSchedulingSummary) {
  if (busy.value || !await abandonAllowed()) return
  restoring.value = true
  try {
    const saved = await schedulingApi.savedDraft(item.id)
    const fresh = await schedulingApi.workspace(saved.summary.cinemaId, saved.summary.targetDate)
    await restoreSavedContent(saved, fresh)
    ElMessage.success('已恢复暂存草案，请重新检查后再应用')
  } catch (e) { ElMessage.error(`恢复失败：${errorMessage(e)}`) } finally { restoring.value = false }
}
/** 删除选中的服务器暂存草稿并刷新列表。 */
async function deleteSavedDraft(item: SavedSchedulingSummary) {
  if (busy.value) return
  try { await ElMessageBox.confirm(`删除 ${item.cinemaName} ${item.targetDate} 的暂存草案？已发布排片不受影响。`, '删除暂存草案', { type: 'warning' }) } catch { return }
  deletingSaved.value = true
  try { await schedulingApi.deleteSavedDraft(item.id); savedDrafts.value = savedDrafts.value.filter(s => s.id !== item.id); if (savedId.value === item.id) { savedId.value = ''; savedAt.value = ''; savedFingerprint.value = '' } }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { deletingSaved.value = false }
}
function selectStrategy(id: string) { strategy.value = id; const s = strategies.find(s => s.id === id)!; [settings.revenueWeight, settings.occupancyWeight, settings.diversityWeight] = s.weights as [number, number, number] }
function same(a: DraftShowtime, b: DraftShowtime) { return a.movieId === b.movieId && a.hallId === b.hallId && new Date(a.startTime).getTime() === new Date(b.startTime).getTime() && new Date(a.endTime).getTime() === new Date(b.endTime).getTime() && Number(a.basePrice) === Number(b.basePrice) }
function changeLabel(row: DraftShowtime) { const old = baseline.value.find(s => s.sourceShowtimeId != null && s.sourceShowtimeId === row.sourceShowtimeId); return !old ? '新增' : same(old, row) ? '保留' : '调整' }
const changes = computed(() => {
  const result: { type: 'ADD' | 'UPDATE' | 'REMOVE'; before?: DraftShowtime; after?: DraftShowtime }[] = []
  for (const row of draft.value) { const old = baseline.value.find(s => s.sourceShowtimeId != null && s.sourceShowtimeId === row.sourceShowtimeId); if (!old) result.push({ type: 'ADD', after: row }); else if (!same(old, row)) result.push({ type: 'UPDATE', before: old, after: row }) }
  for (const row of baseline.value) if (!draft.value.some(s => s.sourceShowtimeId === row.sourceShowtimeId)) result.push({ type: 'REMOVE', before: row })
  return result
})
const changeCounts = computed(() => ({ ADD: changes.value.filter(c => c.type === 'ADD').length, UPDATE: changes.value.filter(c => c.type === 'UPDATE').length, REMOVE: changes.value.filter(c => c.type === 'REMOVE').length }))
const minute = (value: string) => (new Date(value).getTime() - new Date(`${targetDate.value}T00:00:00`).getTime()) / 60000
const time = (s: string) => `${s.slice(0, 10) > targetDate.value ? '次日 ' : ''}${s.slice(11, 16)}`
const rate = (n?: number | null) => n == null ? '—' : `${Number(n).toFixed(1)}%`
const money = (n: number) => Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
const describe = (s: DraftShowtime) => `${s.hallName || workspace.value?.halls.find(h => h.id === s.hallId)?.name} · ${s.movieTitle || movies.value.find(m => m.id === s.movieId)?.title} · ${time(s.startTime)}–${time(s.endTime)} · ¥${s.basePrice}`
const localIssues = computed(() => {
  if (!hasDraft.value) return []
  const issues: string[] = []
  if (!draft.value.length) issues.push('请至少保留一场放映')
  const rows = [...draft.value].sort((a, b) => minute(a.startTime) - minute(b.startTime))
  for (let i = 0; i < rows.length; i++) for (let j = i + 1; j < rows.length; j++) {
    const a = rows[i], b = rows[j]
    if (a.hallId === b.hallId && minute(a.endTime) + draftCleaning.value > minute(b.startTime) && (changeLabel(a) !== '保留' || changeLabel(b) !== '保留')) issues.push(`${a.hallName} ${time(a.startTime)} 与 ${time(b.startTime)} 重叠或清场不足`)
  }
  return issues
})
/** 草案发生变化时清除旧检查结果，避免继续使用过期结论。 */
function invalidate() { checkResult.value = undefined; error.value = ''; manuallyEdited.value = true }
watch(() => settings.cleaningMinutes, value => { if (hasDraft.value) { draftCleaning.value = value; invalidate() } })
function disabledDate(date: Date) { const d = dateString(date); const max = new Date(); max.setDate(max.getDate() + 30); return d < dateString(new Date()) || d > dateString(max) }
/** 仅拦截未暂存修改；保存成功后才继续原导航或操作。 */
function abandonAllowed(leaving = false): Promise<boolean> {
  if (!hasUnsavedDraft.value) return Promise.resolve(true)
  if (pendingTransition) return pendingTransition
  transitionIsLeave.value = leaving; transitionVisible.value = true
  pendingTransition = new Promise(resolve => { resolveTransition = resolve })
  return pendingTransition
}
function finishTransition(proceed: boolean) {
  const resolve = resolveTransition
  resolveTransition = undefined; pendingTransition = undefined; transitionVisible.value = false
  resolve?.(proceed)
}
async function saveBeforeTransition() {
  if (await saveDraft()) finishTransition(true)
}
async function changeScope(id: number | undefined, date: string) { if (busy.value || !id || !date || (id === cinemaId.value && date === targetDate.value)) return; if (!await abandonAllowed()) return; cinemaId.value = id; targetDate.value = date; await loadWorkspace() }
/** 按当前条件重新请求数据，并更新加载与错误状态。 */
async function refresh() { if (busy.value || !await abandonAllowed()) return; await loadWorkspace() }
/** 加载当前影院日期的场次与版本快照，建立草案编辑基础。 */
async function loadWorkspace(recoverSaved = true) {
  if (!cinemaId.value) return
  loading.value = true; workspace.value = undefined; hasDraft.value = false; draft.value = []; checkResult.value = undefined; optimization.value = undefined; error.value = ''; editorVisible.value = false
  savedId.value = ''; savedAt.value = ''; savedFingerprint.value = ''; staleDraft.value = false
  try {
    workspace.value = await schedulingApi.workspace(cinemaId.value, targetDate.value)
    settings.cleaningMinutes = Math.max(settings.cleaningMinutes, minimumCleaning.value); settings.minDistinctMovies = Math.min(settings.minDistinctMovies, Math.max(1, eligibleMovies.value.length)); settings.movieIds = settings.movieIds.filter(id => eligibleMovies.value.some(m => m.id === id))
    if (await loadSavedDrafts() && recoverSaved && currentSaved.value) {
      try { await restoreSavedContent(await schedulingApi.savedDraft(currentSaved.value.id), workspace.value) }
      catch (e) { savedError.value = errorMessage(e) }
    }
  }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
const errorMessage = (e: any) => e.response?.data?.message || e.message || '操作失败，请重试'
/** 按当前策略生成排片草案，更新可编辑时间轴。 */
async function generate() {
  if (!cinemaId.value || !workspace.value || busy.value) return
  if (weightSum.value !== 100) return ElMessage.warning('请将三个权重的合计调整为 100%')
  const count = settings.movieIds.length || eligibleMovies.value.length
  if (settings.minDistinctMovies > count) return ElMessage.warning('最少影片数不能超过参与影片数，请修改高级设置')
  if (!await abandonAllowed()) return
  generating.value = true; error.value = ''; checkResult.value = undefined
  try {
    const result = await schedulingApi.optimize({ cinemaId: cinemaId.value, targetDate: targetDate.value, ...settings, movieIds: settings.movieIds.length ? settings.movieIds : undefined })
    if (!['OPTIMAL', 'FEASIBLE'].includes(result.status)) { error.value = result.reason || '暂未找到可用方案，请减少每厅场次或放宽高级设置后重试'; return }
    optimization.value = result; generatedStrategy.value = strategies.find(s => s.id === strategy.value)!.label
    draft.value = result.selectedShowtimes.map(s => { const original = baseline.value.find(old => old.sourceShowtimeId === s.sourceShowtimeId); return { ...s, basePrice: s.unitPrice ?? original?.basePrice ?? 45, lockReason: original?.lockReason } })
    // 不完整的历史数据也不能导致快照中的保护场次被遗漏。
    for (const locked of baseline.value.filter(s => s.locked)) { const index = draft.value.findIndex(s => s.sourceShowtimeId === locked.sourceShowtimeId); if (index < 0) draft.value.push(clone(locked)); else draft.value[index] = { ...draft.value[index], ...clone(locked) } }
    hasDraft.value = true; draftCleaning.value = settings.cleaningMinutes; viewMode.value = 'draft'; manuallyEdited.value = false
    await checkDraft()
  } catch (e) { error.value = errorMessage(e) } finally { generating.value = false }
}
/** 整理当前完整排片方案与基础快照，构造检查或应用请求。 */
function requestDraft(): SchedulingDraftRequest { return { cinemaId: cinemaId.value!, targetDate: targetDate.value, version: workspace.value!.version, cleaningMinutes: draftCleaning.value, items: draft.value.map(({ sourceShowtimeId, movieId, hallId, startTime, endTime, basePrice }) => ({ sourceShowtimeId, movieId, hallId, startTime, endTime, basePrice })) } }
/** 校验整日草案并记录检查结果，供应用前核对。 */
async function checkDraft() {
  if (!workspace.value || !hasDraft.value || localIssues.value.length) return
  checking.value = true; checkResult.value = undefined; error.value = ''
  try { checkResult.value = await schedulingApi.check(requestDraft()); for (const row of draft.value) { const evaluated = checkResult.value.draft.items.find(s => s.movieId === row.movieId && s.hallId === row.hallId && new Date(s.startTime).getTime() === new Date(row.startTime).getTime()); row.predictedRate = evaluated?.predictedRate; row.confidence = evaluated?.confidence } }
  catch (e) { error.value = errorMessage(e) } finally { checking.value = false }
}
/** 提交当前草案应用，成功后重新加载正式排片。 */
async function applyDraft() {
  if (!checkResult.value || busy.value) return
  applying.value = true
  try {
    await schedulingApi.apply(requestDraft()); hasDraft.value = false; confirmVisible.value = false
    ElMessage.success('当日排片已应用，购票端可查看新的场次')
    if (savedId.value) {
      try { await schedulingApi.deleteSavedDraft(savedId.value) }
      catch (e: any) { if (e.response?.status !== 404) ElMessage.warning('排片已应用，但暂存草案清理失败，请在草案列表中处理') }
    }
    await loadWorkspace(false)
  }
  catch (e) { error.value = errorMessage(e); checkResult.value = undefined; confirmVisible.value = false } finally { applying.value = false }
}
async function discard() { if (!await abandonAllowed()) return; hasDraft.value = false; draft.value = []; checkResult.value = undefined; optimization.value = undefined; error.value = '' }
const editing = ref<DraftShowtime>(), editingIndex = ref(-1), editingTime = ref('10:00'), movieSearch = ref('')
const editorMovies = computed(() => { const list = [...eligibleMovies.value]; if (editing.value && !list.some(m => m.id === editing.value!.movieId)) list.push({ id: editing.value.movieId, title: editing.value.movieTitle, duration: 120 }); return list })
const matchedEditorMovies = computed(() => {
  const terms = movieSearch.value.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  if (!terms.length) return []
  return eligibleMovies.value.filter(movie => {
    const searchable = [movie.title, movie.englishTitle, movie.director, movie.actors, movie.genre].filter(Boolean).join(' ').toLocaleLowerCase()
    return terms.every(term => searchable.includes(term))
  })
})
function selectSearchMovie(movie: Movie) {
  if (!editing.value || editing.value.locked || movie.id == null) return
  editing.value.movieId = movie.id; editing.value.movieTitle = movie.title
  updateEditingTime(); movieSearch.value = ''
}
function selectFirstSearchMovie(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  if (matchedEditorMovies.value[0]) selectSearchMovie(matchedEditorMovies.value[0])
}
function ensureDraft() { if (!hasDraft.value) { draft.value = clone(baseline.value); hasDraft.value = true; draftCleaning.value = settings.cleaningMinutes } viewMode.value = 'draft' }
/** 拖动只写入草案，保留影片时长、票价与来源，重新检查后才能应用。 */
function moveShowtime(index: number, row: DraftShowtime) {
  if (busy.value || (hasDraft.value && viewMode.value === 'original') || visibleItems.value[index]?.locked) return
  ensureDraft()
  draft.value[index] = clone(row)
  invalidate()
}
/** 两场交换一次写回，避免出现只移动一场的中间状态。 */
function swapShowtimes(changes: { index: number; row: DraftShowtime }[]) {
  if (busy.value || (hasDraft.value && viewMode.value === 'original') || changes.length !== 2 || changes[0].index === changes[1].index) return
  if (changes.some(change => !visibleItems.value[change.index] || visibleItems.value[change.index].locked)) return
  ensureDraft()
  const next = [...draft.value]
  for (const change of changes) next[change.index] = clone(change.row)
  draft.value = next
  invalidate()
}
function editShowtime(row: DraftShowtime) { if (hasDraft.value && viewMode.value === 'original' && !row.locked) { ElMessage.info('请切换到排片草案后调整场次'); return } movieSearch.value = ''; editing.value = clone(row); editingIndex.value = (hasDraft.value ? draft.value : baseline.value).indexOf(row); editingTime.value = row.startTime.slice(11, 16); editorVisible.value = true }
function addShowtime() { const movie = eligibleMovies.value[0]; const hall = workspace.value?.halls.find(h => h.status === 1); if (!movie || !hall) return ElMessage.warning('请先添加可用的影片和影厅'); movieSearch.value = ''; editingIndex.value = -1; editingTime.value = '10:00'; editing.value = { movieId: movie.id!, movieTitle: movie.title, hallId: hall.id!, hallName: hall.name, startTime: `${targetDate.value}T10:00:00`, endTime: '', basePrice: 45, locked: false }; updateEditingTime(); editorVisible.value = true }
function updateEditingMovie() { if (!editing.value) return; editing.value.movieTitle = movies.value.find(m => m.id === editing.value!.movieId)?.title || ''; updateEditingTime() }
function updateEditingTime() { if (!editing.value || editing.value.locked) return; const m = movies.value.find(m => m.id === editing.value!.movieId); editing.value.startTime = `${targetDate.value}T${editingTime.value}:00`; const end = new Date(new Date(editing.value.startTime).getTime() + (m?.duration || 120) * 60000); editing.value.endTime = `${dateString(end)}T${pad(end.getHours())}:${pad(end.getMinutes())}:00`; editing.value.predictedRate = undefined }
/** 校验场次编辑内容并写回草案，使旧检查结果失效。 */
function saveEditing() { if (!editing.value || editing.value.locked) return; if (new Date(editing.value.startTime) <= new Date()) return ElMessage.warning('请选择尚未开始的时间'); if (!editing.value.basePrice || editing.value.basePrice <= 0) return ElMessage.warning('请填写有效票价'); editing.value.hallName = workspace.value?.halls.find(h => h.id === editing.value!.hallId)?.name || ''; ensureDraft(); if (editingIndex.value < 0) draft.value.push(clone(editing.value)); else draft.value[editingIndex.value] = clone(editing.value); invalidate(); editorVisible.value = false }
/** 从草案移除当前可编辑场次，并要求重新检查。 */
function removeEditing() { if (editing.value?.locked) return; ensureDraft(); draft.value.splice(editingIndex.value, 1); invalidate(); editorVisible.value = false }
/** 在浏览器关闭或刷新时提示未保存的排片修改。 */
function beforeUnload(e: BeforeUnloadEvent) { if (hasUnsavedDraft.value) { e.preventDefault(); e.returnValue = '' } }
onBeforeRouteLeave(async () => !busy.value && await abandonAllowed(true))
onMounted(async () => { window.addEventListener('beforeunload', beforeUnload); loading.value = true; try { const [cs, ms] = await Promise.all([cinemaApi.getAll(), movieApi.getNowShowing()]); cinemas.value = cs; movies.value = ms; if (!cs.some(cinema => cinema.id === cinemaId.value)) cinemaId.value = cs[0]?.id; await loadWorkspace() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false } })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); finishTransition(false) })
</script>
<style scoped src="../styles/scheduling-workspace.css"></style>
