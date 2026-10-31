<template>
  <div class="listing-page">
    <nav v-if="!draft" class="import-switch" aria-label="导入方式">
      <button :class="{ selected: !isAiImport }" :aria-pressed="!isAiImport" :disabled="creating" @click="switchImportMode('template')"><el-icon><Document /></el-icon>模板导入</button>
      <button :class="{ selected: isAiImport }" :aria-pressed="isAiImport" :disabled="creating" @click="switchImportMode('ai')"><el-icon><MagicStick /></el-icon>AI 导入<span>无需固定格式</span></button>
    </nav>
    <header class="listing-hero">
      <div><span class="eyebrow">AI LISTING STUDIO</span><h1>{{ isAiImport ? '数据变影片，AI开场快一步。' : '把数据，变成开场内容。' }}</h1><p>{{ isAiImport ? '无需整理复杂文档格式，粘贴文字或上传原始资料，快速生成待审核的上架草稿。' : '按模板填写电影、影院或影厅信息，批量导入后逐项核对，再确认发布。' }}</p></div>
      <div class="hero-symbol"><el-icon><MagicStick /></el-icon><span>资料录入<br><strong>{{ isAiImport ? '交给 AI 帮忙' : '批量导入更省心' }}</strong></span></div>
    </header>
    <div class="workflow" aria-label="上架步骤">
      <div :class="{ active: !draft }"><b>01</b><span>{{ isAiImport ? 'AI 识别资料' : '导入模板' }}<small>{{ isAiImport ? '原始文档、海报或文字' : 'Excel、CSV 或 JSON' }}</small></span></div>
      <span class="step-line"></span><div :class="{ active: draft && draft.status !== 'PUBLISHED' }"><b>02</b><span>审核草稿<small>核对、修改与补充</small></span></div>
      <span class="step-line"></span><div :class="{ active: draft?.status === 'PUBLISHED' }"><b>03</b><span>确认发布<small>同步到管理目录</small></span></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
    <div v-if="!draft" class="intake-grid">
      <section class="workspace-panel intake-panel" aria-label="上传上架资料">
        <div class="panel-heading"><div><h2>{{ isAiImport ? 'AI 导入上架资料' : '新建上架资料' }}</h2><p>{{ isAiImport ? '无需固定标题、字段顺序或排版，AI 自动识别并拆分资料；每批最多 20 条。' : '支持单条和批量资料，一批最多导入 20 部电影、20 家影院或 20 个影厅。' }}</p></div><el-tag effect="plain">人工审核后上线</el-tag></div>
        <div class="kind-tabs" role="group" aria-label="上架类型">
          <button v-for="option in allowedKinds" :key="option" :class="{ selected: kind === option }" @click="kind = option" :disabled="creating"><el-icon><component :is="kindIcons[option]" /></el-icon>{{ kindNames[option] }}<span v-if="kind === option">✓</span></button>
        </div>
        <el-form label-position="top">
          <el-form-item v-if="kind === 'HALL'" label="所属影院（必选）"><el-select v-model="cinemaId" placeholder="请选择影院" :disabled="creating || !isPlatform" style="width:100%" aria-label="所属影院"><el-option v-for="cinema in cinemas" :key="cinema.id" :label="cinema.name" :value="cinema.id!" /></el-select></el-form-item>
          <el-upload :key="importMode" drag :auto-upload="false" :limit="3" v-model:file-list="fileList" :disabled="creating" :on-exceed="() => ElMessage.warning('一次最多上传 3 个文件')" :accept="isAiImport ? '.xlsx,.jpg,.jpeg,.png,.txt,.md,.json,.csv,.pdf,.docx' : '.xlsx,.csv,.json'" class="source-upload">
            <span class="upload-icon"><el-icon><UploadFilled /></el-icon></span><strong>将{{ isAiImport ? '原始资料' : '模板文件' }}拖到这里，或 <em>选择文件</em></strong><p>{{ isAiImport ? '发行资料、影片介绍、海报或影院／影厅说明，保留原有排版即可' : '下载对应模板填写，每行一条资料，字段自动对应' }}</p><small>{{ isAiImport ? 'PDF / DOCX / TXT / MD / JPG / PNG / Excel (.xlsx) / CSV / JSON' : 'Excel (.xlsx) / CSV / JSON' }}<br>单个文件不超过 5MB，最多 3 个，合计不超过 8MB</small>
          </el-upload>
          <div class="text-label">
            <label for="source-text">{{ isAiImport ? '直接粘贴原始资料，无需整理格式' : '也可以粘贴 CSV / JSON 模板内容' }}</label>
            <el-button v-if="isAiImport" text size="small" :disabled="creating" @click="fillExample">填入示例</el-button>
            <el-dropdown v-else split-button trigger="click" size="small" :disabled="downloading" @click="downloadTemplate('xlsx')" @command="downloadTemplate">
              <el-icon><Download /></el-icon>下载{{ kindNames[kind] }}批量模板
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item command="xlsx">Excel 批量模板（每行一条）</el-dropdown-item>
                <el-dropdown-item command="csv">CSV 批量模板</el-dropdown-item>
                <el-dropdown-item command="json">JSON 批量模板</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </div>
          <el-input id="source-text" v-model="sourceText" type="textarea" :rows="isAiImport ? 10 : 7" maxlength="40000" show-word-limit :disabled="creating" :placeholder="sourcePlaceholder" />
        </el-form>
        <div class="intake-footer"><span><el-icon><InfoFilled /></el-icon> 未提供的信息会留空，请在审核时补充。</span><el-button type="primary" size="large" :loading="creating" :disabled="!capabilities || !hasSource || kind === 'HALL' && !cinemaId" @click="createDraft"><el-icon v-if="!creating"><MagicStick /></el-icon>{{ creating ? '正在提取资料…' : isAiImport ? 'AI 识别并生成草稿' : '生成待审核草稿' }}</el-button></div>
        <p v-if="creating" class="processing" role="status">{{ isAiImport ? 'AI 正在读取资料、识别条目并整理字段，通常需要几秒到一分钟。请保持当前页面打开。' : '正在读取模板并生成草稿，请保持当前页面打开。' }}</p>
      </section>
      <aside class="intake-aside">
        <section class="workspace-panel guide"><span class="small-kicker">从资料到上线</span><h2>{{ isAiImport ? '资料不用重排，' : '少填一点，' }}<br>{{ isAiImport ? '上架更快一步。' : '多核对一点。' }}</h2><p>{{ isAiImport ? '适合快速整理零散资料、长篇介绍和多条上架内容。' : '标准表格批量导入，适合已整理好的上架资料。' }}</p><ol><li><b>{{ isAiImport ? '提供原始资料' : '填写并上传模板' }}</b><span>{{ isAiImport ? '直接粘贴介绍或上传文档、海报，不必按模板重排。' : '下载 Excel、CSV 或 JSON 模板，按字段填写后上传。' }}</span></li><li><b>核对提取结果</b><span>对照原文修改字段，缺失信息补全后再发布。</span></li><li><b>审核确认上线</b><span>保存草稿可稍后继续，发布前需明确确认。</span></li></ol></section>
        <section class="workspace-panel service-status"><h3>识别服务</h3><p><i :class="{ ready: capabilities?.textConfigured }"></i>文字提取<span>{{ capabilities?.textConfigured ? '已配置' : '未配置' }}</span></p><p><i :class="{ ready: capabilities?.visionConfigured }"></i>海报识别<span>{{ capabilities?.visionConfigured ? '已配置' : '未配置' }}</span></p><small>{{ isAiImport ? 'AI 只提取资料中明确出现的信息，不会自动编造缺失内容。扫描 PDF 请转成 JPG / PNG 后上传。' : 'Excel / CSV / JSON 模板无需 AI 服务。原始文档或海报请切换到 AI 导入。' }}{{ isAiImport && !capabilities?.textConfigured ? '文字识别尚未配置，可先使用模板导入。' : '' }}{{ isAiImport && !capabilities?.visionConfigured ? '海报识别尚未配置，建议同时提供文字资料。' : '' }}</small></section>
      </aside>
    </div>
    <template v-else>
      <section v-if="batchItems.length > 1" class="workspace-panel batch-panel" aria-label="本次批量识别结果">
        <div class="panel-heading"><div><h2>本次识别 {{ batchItems.length }} 条资料</h2><p>已发布 {{ batchItems.filter(item => item.status === 'PUBLISHED').length }} 条，其余资料可逐条核对、暂存和发布。</p></div><el-tag effect="plain">每条独立审核</el-tag></div>
        <div class="batch-records" role="group" aria-label="切换资料"><button v-for="(item, index) in batchItems" :key="item.id" :class="{ selected: item.id === draft.id }" :disabled="!!busy" @click="openDraft(item.id)"><b>{{ index + 1 }}</b><span>{{ item.title }}</span><small>{{ statusNames[item.status] }}</small></button></div>
      </section>
      <section class="draft-heading"><div><el-button text @click="closeDraft"><el-icon><ArrowLeft /></el-icon>返回上架工作台</el-button><h2>{{ draft.fields.title || draft.fields.name || '未命名资料' }}<el-tag :type="statusType(draft.status)">{{ statusNames[draft.status] }}</el-tag></h2><p>{{ kindNames[draft.kind] }}资料 · {{ modeName(draft.extractionMode) }} · 版本 {{ draft.version }}</p></div><router-link v-if="draft.status === 'PUBLISHED'" :to="catalogPath"><el-button type="primary">前往{{ kindNames[draft.kind] }}管理</el-button></router-link></section>
      <el-alert v-if="draft.extractionWarning" :title="draft.extractionWarning" type="warning" :closable="false" show-icon class="notice" />
      <el-alert v-if="draft.status === 'REJECTED'" :title="`驳回原因：${draft.reviewNote}。修改并暂存后可重新审核。`" type="error" :closable="false" show-icon class="notice" />
      <el-alert v-if="draft.status === 'PUBLISHED'" :title="`已审核发布，目录编号 #${draft.entityId}。后续修改请进入对应管理页面。`" type="success" :closable="false" show-icon class="notice" />
      <div class="review-grid">
        <section class="workspace-panel original-panel" aria-label="原始资料">
          <div class="panel-heading"><h2>原始资料</h2><span class="subtle">对照核对</span></div><div class="source-files"><el-tag v-for="name in draft.sourceNames" :key="name" type="info">{{ name }}</el-tag><span v-if="!draft.sourceNames.length">粘贴的文字资料</span></div>
          <el-image v-for="image in draft.sourceImages || []" :key="image" :src="image" :preview-src-list="draft.sourceImages" fit="contain" class="source-poster"><template #error><span>图片暂时无法加载</span></template></el-image>
          <pre class="source-content">{{ draft.sourceText }}</pre><el-collapse><el-collapse-item title="查看初次提取结果" name="extracted"><pre class="extracted-content">{{ JSON.stringify(draft.extracted, null, 2) }}</pre></el-collapse-item></el-collapse>
        </section>
        <section class="workspace-panel fields-panel" aria-label="审核资料字段">
          <div class="panel-heading"><div><h2>审核{{ kindNames[draft.kind] }}资料</h2><p>请逐项确认字段与原资料一致。</p></div><el-tag :type="missingFields.length ? 'warning' : 'success'">{{ missingFields.length ? `待补充 ${missingFields.length} 项` : '必填项已完整' }}</el-tag></div>
          <el-form label-position="top" :disabled="draft.status === 'PUBLISHED' || !!busy" class="review-form">
            <el-form-item v-if="draft.kind === 'HALL'" label="所属影院"><el-input :model-value="cinemas.find(c => c.id === draft?.cinemaId)?.name || `影院 #${draft.cinemaId}`" disabled /></el-form-item>
            <el-form-item v-for="field in currentSchema" :key="field.key" :label="field.label" :required="field.required" :class="{ wide: ['textarea','image'].includes(field.type), 'field-missing': field.required && !filled(editFields[field.key]) }">
              <el-input v-if="['text','url','image'].includes(field.type)" v-model="editFields[field.key]" :maxlength="field.maxLength || undefined" :aria-label="field.label" :placeholder="`请核对${field.label}`" @input="acknowledged = false" />
              <el-input v-else-if="field.type === 'textarea'" v-model="editFields[field.key]" type="textarea" :rows="field.key === 'seatLayout' ? 5 : 4" :maxlength="field.maxLength || undefined" :aria-label="field.label" @input="acknowledged = false" />
              <el-input v-else-if="['integer','decimal'].includes(field.type)" v-model="editFields[field.key]" :aria-label="field.label" :inputmode="field.type === 'integer' ? 'numeric' : 'decimal'" @input="acknowledged = false" />
              <el-date-picker v-else-if="field.type === 'date'" v-model="editFields[field.key]" type="date" value-format="YYYY-MM-DD" :aria-label="field.label" placeholder="选择日期" @change="acknowledged = false" />
              <el-select v-else-if="field.type === 'status'" v-model="editFields.status" aria-label="发布状态" @change="acknowledged = false"><el-option v-for="option in publishStatuses" :key="option.value" :label="option.label" :value="option.value" /></el-select>
              <div v-if="field.key === 'seatLayout' && draft.status !== 'PUBLISHED'" class="seat-helper"><el-button size="small" :disabled="!validDimensions" @click="generateSeats">生成全可用规则座位</el-button><small>仅适用于规则影厅；请核实停用座位和过道。</small></div>
            </el-form-item>
          </el-form>
          <div v-if="seatPreview" class="seat-preview" aria-label="座位预览"><span>银幕方向</span><div class="seat-scroll"><div v-for="(row, r) in seatPreview" :key="r" class="seat-row"><small>{{ r + 1 }}</small><i v-for="(seat, c) in row" :key="c" :class="{ disabled: seat === 0 }" :title="`${r + 1} 排 ${c + 1} 列${seat ? '' : '（停用）'}`"></i></div></div></div>
          <div v-if="draft.issues.length && draft.status !== 'PUBLISHED'" class="issues"><strong>{{ dirty ? '暂存前检查结果' : '发布前需处理' }}</strong><ul><li v-for="issue in draft.issues" :key="issue">{{ issue }}</li></ul><small v-if="dirty">已修改字段，请暂存后刷新检查。</small></div>
          <div v-if="draft.status !== 'PUBLISHED'" class="review-actions"><el-checkbox v-model="acknowledged" :disabled="!!busy || dirty">我已对照原资料核对全部字段，确认可以发布</el-checkbox><div><el-button :loading="busy === 'save'" :disabled="!!busy" @click="saveDraft">暂存草稿</el-button><el-button :disabled="!!busy" @click="rejectDialog = true">驳回</el-button><el-button type="primary" :loading="busy === 'publish'" :disabled="!!busy || !acknowledged || dirty || draft.status === 'REJECTED' || !!draft.issues.length" @click="publishDialog = true">审核通过并发布</el-button></div><small v-if="dirty">修改后请先暂存，再确认发布。</small></div>
        </section>
      </div>
      <section class="workspace-panel history-panel" aria-label="审核记录"><h3>操作记录</h3><div v-for="(event, index) in draft.history" :key="index"><span>{{ event.createdAt?.replace('T', ' ').slice(0, 19) }}</span><b>{{ actionNames[event.action] || event.action }}</b><span>管理员 #{{ event.operatorId }} · V{{ event.version }}</span><p v-if="event.note">{{ event.note }}</p></div></section>
    </template>
    <section v-if="!draft" class="workspace-panel drafts-panel" aria-label="上架草稿列表">
      <div class="panel-heading"><div><h2>上架记录</h2><p>资料自动保存为草稿，可随时继续审核。</p></div><el-button :loading="loading" @click="loadDrafts">刷新</el-button></div>
      <div class="draft-filters"><el-radio-group v-model="filterStatus" @change="page = 1; loadDrafts()"><el-radio-button value="">全部</el-radio-button><el-radio-button value="DRAFT">待审核</el-radio-button><el-radio-button value="REJECTED">已驳回</el-radio-button><el-radio-button value="PUBLISHED">已发布</el-radio-button></el-radio-group></div>
      <div v-loading="loading" class="draft-list"><div v-for="item in summaries" :key="item.id" class="draft-item"><span class="record-icon"><el-icon><component :is="kindIcons[item.kind]" /></el-icon></span><div><strong>{{ item.title }}</strong><p>{{ kindNames[item.kind] }} · {{ modeName(item.extractionMode) }} · {{ item.updatedAt?.replace('T', ' ').slice(0, 16) }}</p></div><el-tag :type="statusType(item.status)">{{ statusNames[item.status] }}</el-tag><el-button :disabled="loading" @click="openDraft(item.id)">{{ item.status === 'PUBLISHED' ? '查看记录' : '继续审核' }}</el-button></div><el-empty v-if="!loading && !summaries.length" description="暂无上架记录，上传第一份资料开始吧" :image-size="78" /></div>
      <el-pagination v-if="total > 10" v-model:current-page="page" :total="total" :page-size="10" layout="prev, pager, next" @current-change="loadDrafts" />
    </section>
    <el-dialog v-model="publishDialog" title="确认审核并发布" width="min(460px, 92vw)"><p>将「{{ draft?.fields.title || draft?.fields.name }}」写入{{ draft && kindNames[draft.kind] }}目录，发布状态为「{{ publishStatuses.find(s => s.value === Number(editFields.status))?.label }}」。</p><p>请确认资料和展示状态已核对无误。</p><template #footer><el-button :disabled="!!busy" @click="publishDialog = false">再检查一下</el-button><el-button type="primary" :loading="busy === 'publish'" :disabled="!!busy" @click="publishDraft">确认发布</el-button></template></el-dialog>
    <el-dialog v-model="rejectDialog" title="驳回上架资料" width="min(460px, 92vw)"><el-input v-model="rejectNote" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写需要修正的问题" aria-label="驳回原因" /><template #footer><el-button @click="rejectDialog = false">取消</el-button><el-button type="danger" :loading="busy === 'reject'" :disabled="!!busy || !rejectNote.trim()" @click="rejectDraft">确认驳回</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document, Download, Film, Grid, InfoFilled, MagicStick, OfficeBuilding, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import type { UploadUserFile } from 'element-plus'
import { listingApi, type ListingCapabilities, type ListingDraft, type ListingKind, type ListingSummary, type ListingStatus } from '@/api/listing'
import { cinemaApi } from '@/api/cinema'
import { useUserStore } from '@/stores/user'
import type { Cinema } from '@/types'

const route = useRoute(), router = useRouter(), user = useUserStore()
const isPlatform = computed(() => user.userInfo?.role === 'ADMIN')
const kindNames = { MOVIE: '电影', CINEMA: '影院', HALL: '影厅' }
const kindIcons = { MOVIE: Film, CINEMA: OfficeBuilding, HALL: Grid }
const statusNames = { DRAFT: '待审核', REJECTED: '已驳回', PUBLISHED: '已发布' }
const actionNames: Record<string, string> = { CREATED: '生成草稿', SAVED: '暂存修改', REJECTED: '驳回资料', PUBLISHED: '审核发布' }
const capabilities = ref<ListingCapabilities>(), kind = ref<ListingKind>(isPlatform.value ? 'MOVIE' : 'HALL')
const cinemas = ref<Cinema[]>([]), cinemaId = ref<number | undefined>(user.userInfo?.cinemaId || undefined)
type ImportMode = 'template' | 'ai'
const importMode = computed<ImportMode>(() => route.query.mode === 'ai' ? 'ai' : 'template')
const isAiImport = computed(() => importMode.value === 'ai')
const intakes = ref<Record<ImportMode, { files: UploadUserFile[]; text: string }>>({ template: { files: [], text: '' }, ai: { files: [], text: '' } })
const fileList = computed({ get: () => intakes.value[importMode.value].files, set: value => { intakes.value[importMode.value].files = value } })
const sourceText = computed({ get: () => intakes.value[importMode.value].text, set: value => { intakes.value[importMode.value].text = value } })
const creating = ref(false), loading = ref(false), busy = ref(''), error = ref(''), downloading = ref(false)
const sourcePlaceholder = computed(() => !isAiImport.value ? '粘贴带表头的 CSV，或 JSON 对象／数组；原始介绍文字请使用左上角的 AI 导入。' : ({
  MOVIE: '直接粘贴发行方介绍、影片资料或多部电影的说明，AI 会识别片名、导演、时长、类型和简介，并按电影拆分草稿。',
  CINEMA: '直接粘贴影院介绍、地址、电话等原始资料，多家影院可以放在一起，AI 会逐家整理。',
  HALL: '直接粘贴影厅名称、行列数和设备说明；如有座位矩阵也可一并提供，多间影厅会分别生成草稿。'
}[kind.value]))
const draft = ref<ListingDraft>(), editFields = ref<Record<string, any>>({}), acknowledged = ref(false)
const summaries = ref<ListingSummary[]>([]), filterStatus = ref(''), page = ref(1), total = ref(0)
const batchItems = ref<ListingSummary[]>([])
const publishDialog = ref(false), rejectDialog = ref(false), rejectNote = ref('')
const allowedKinds = computed(() => (Object.keys(capabilities.value?.schemas || (isPlatform.value ? kindNames : { HALL: '' })) as ListingKind[]))
const currentSchema = computed(() => capabilities.value?.schemas[draft.value?.kind || kind.value] || [])
const hasSource = computed(() => !!sourceText.value.trim() || !!fileList.value.length)
const dirty = computed(() => !!draft.value && JSON.stringify(editFields.value) !== JSON.stringify(draft.value.fields))
const filled = (value: unknown) => value != null && String(value).trim() !== ''
const missingFields = computed(() => currentSchema.value.filter(f => f.required && !filled(editFields.value[f.key])))
const catalogPath = computed(() => '/' + ({ MOVIE: 'movie', CINEMA: 'cinema', HALL: 'hall' }[draft.value?.kind || kind.value]))
const publishStatuses = computed(() => draft.value?.kind === 'MOVIE' ? [{ label: '即将上映', value: 2 }, { label: '上映中', value: 1 }, { label: '下架', value: 0 }] : [{ label: '启用', value: 1 }, { label: '停用', value: 0 }])
const validDimensions = computed(() => [editFields.value.totalRows, editFields.value.totalCols].every(v => Number.isInteger(Number(v)) && Number(v) >= 1 && Number(v) <= 50))
const seatPreview = computed<number[][] | null>(() => {
  if (draft.value?.kind !== 'HALL') return null
  try { const parsed = JSON.parse(editFields.value.seatLayout); const seats = Array.isArray(parsed) ? parsed : parsed.seats; return Array.isArray(seats) && seats.length <= 50 && seats.every((r: any) => Array.isArray(r) && r.length <= 50) ? seats : null } catch { return null }
})
const statusType = (status: ListingStatus) => status === 'PUBLISHED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
const modeName = (mode: string) => ({ STRUCTURED: '模板导入', AI_TEXT: 'AI 文字提取', AI_VISION: 'AI 图文识别', MANUAL: '待人工补充' }[mode] || mode)
const showFailure = (e: any) => { error.value = e.response?.data?.message || e.message || '操作失败，请稍后重试' }
async function switchImportMode(mode: ImportMode) {
  if (creating.value || mode === importMode.value) return
  await router.replace({ query: { ...route.query, mode, kind: kind.value } }); error.value = ''
}
function fillExample() {
  const example = {
    MOVIE: '【示例资料，请替换为真实发行资料】\n《银幕旅途》是一部剧情片，由林导演执导，片长 120 分钟，计划于 2026 年 11 月 6 日上映。影片讲述一位放映员带着老胶片重返故乡的故事。\n\n另一部电影《周末来信》，喜剧，95 分钟，由陈导演执导。故事围绕一封寄错地址的信展开。',
    CINEMA: '【示例资料，请替换为真实影院资料】\n东城影院位于东城路 18 号，联系电话 010-12345678。影院提供停车服务，营业时间为每天 10:00 至 23:00。',
    HALL: '【示例资料，请替换为真实影厅资料】\n1 号厅名为星光厅，设有 8 排座位，每排 10 座。另一间 2 号厅名为月光厅，设有 6 排座位，每排 8 座。具体座位布局待核实。'
  }[kind.value]
  sourceText.value = sourceText.value.trim() ? `${sourceText.value}\n\n${example}` : example
}
function setDraft(value: ListingDraft) {
  draft.value = value; editFields.value = structuredClone(value.fields); acknowledged.value = false; error.value = ''
  batchItems.value = batchItems.value.map(item => item.id === value.id ? { ...item, title: value.fields.title || value.fields.name || '未命名资料', status: value.status } : item)
}
async function loadDrafts() {
  loading.value = true
  try { const result = await listingApi.list({ status: filterStatus.value || undefined, page: page.value, size: 10 }); summaries.value = result.records; total.value = result.total } catch (e) { showFailure(e) } finally { loading.value = false }
}
async function openDraft(id: string) {
  if (draft.value?.id !== id && dirty.value && !await canLeave()) return
  try { const value = await listingApi.get(id); setDraft(value); batchItems.value = value.batchId ? await listingApi.batch(value.batchId) : []; await router.replace({ query: { ...route.query, draft: id } }) } catch (e) { showFailure(e) }
}
async function createDraft() {
  if (creating.value) return
  const rawFiles = fileList.value.map(f => f.raw).filter((f): f is NonNullable<typeof f> => !!f)
  if (rawFiles.some(f => f.size > 5 * 1024 * 1024) || rawFiles.reduce((sum, f) => sum + f.size, 0) > 8 * 1024 * 1024) { ElMessage.warning('单个文件限 5MB，合计限 8MB'); return }
  creating.value = true; error.value = ''
  const data = new FormData(); data.append('kind', kind.value); data.append('text', sourceText.value); data.append('importMode', isAiImport.value ? 'AI' : 'TEMPLATE')
  if (kind.value === 'HALL' && cinemaId.value) data.append('cinemaId', String(cinemaId.value))
  rawFiles.forEach(file => data.append('files', file))
  try {
    const result = await listingApi.create(data)
    batchItems.value = result.drafts.map(item => ({ id: item.id, kind: item.kind, title: item.fields.title || item.fields.name || '未命名资料', status: item.status, extractionMode: item.extractionMode, updatedAt: item.updatedAt }))
    setDraft(result.drafts[0]); sourceText.value = ''; fileList.value = []
    await router.replace({ query: { ...route.query, kind: kind.value, draft: result.drafts[0].id } }); ElMessage.success(`已生成 ${result.total} 份待审核草稿`)
  } catch (e) { showFailure(e) } finally { creating.value = false }
}
async function saveDraft() {
  if (!draft.value || busy.value) return
  busy.value = 'save'
  try { setDraft(await listingApi.save(draft.value.id, draft.value.version, editFields.value)); ElMessage.success('草稿已暂存，已刷新校验结果') } catch (e) { showFailure(e) } finally { busy.value = '' }
}
async function publishDraft() {
  if (!draft.value || busy.value || dirty.value || !acknowledged.value) return
  busy.value = 'publish'
  try { setDraft(await listingApi.publish(draft.value.id, draft.value.version)); publishDialog.value = false; ElMessage.success('已审核发布') } catch (e) { publishDialog.value = false; showFailure(e) } finally { busy.value = '' }
}
async function rejectDraft() {
  if (!draft.value || busy.value) return
  busy.value = 'reject'
  try { setDraft(await listingApi.reject(draft.value.id, draft.value.version, rejectNote.value)); rejectDialog.value = false; rejectNote.value = '' } catch (e) { showFailure(e) } finally { busy.value = '' }
}
async function canLeave() {
  if (creating.value || busy.value) { ElMessage.info('正在处理资料，请完成后再离开'); return false }
  if (!dirty.value && !Object.values(intakes.value).some(intake => intake.text.trim() || intake.files.length)) return true
  try { await ElMessageBox.confirm('尚有未保存的内容，离开后这些修改将丢失。已暂存的草稿仍可恢复。', '离开上架工作台', { confirmButtonText: '离开', cancelButtonText: '继续编辑', type: 'warning' }); return true } catch { return false }
}
async function closeDraft() { if (dirty.value && !await canLeave()) return; draft.value = undefined; batchItems.value = []; error.value = ''; await router.replace({ query: { ...route.query, kind: kind.value, draft: undefined } }); await loadDrafts() }
function generateSeats() {
  if (!validDimensions.value) return
  editFields.value.seatLayout = JSON.stringify({ seats: Array.from({ length: Number(editFields.value.totalRows) }, () => Array(Number(editFields.value.totalCols)).fill(1)), aisleAfterCols: [] }); acknowledged.value = false
}
async function downloadTemplate(format: 'xlsx' | 'csv' | 'json' = 'xlsx') {
  if (downloading.value) return
  downloading.value = true
  try {
    const blob = await listingApi.template(kind.value, format), url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = `${kind.value.toLowerCase()}-batch-template.${format}`; a.click(); URL.revokeObjectURL(url)
  } catch (e) { showFailure(e) } finally { downloading.value = false }
}
onBeforeRouteLeave(canLeave)
onMounted(async () => {
  try { capabilities.value = await listingApi.capabilities(); cinemas.value = await cinemaApi.getAll(); if (allowedKinds.value.includes(route.query.kind as ListingKind)) kind.value = route.query.kind as ListingKind; if (typeof route.query.draft === 'string') await openDraft(route.query.draft); else await loadDrafts() } catch (e) { showFailure(e) }
})
</script>

<style scoped src="@/styles/listing-workspace.css"></style>
