<template>
  <div class="marketing-workspace" v-loading="loading">
    <section class="studio-header">
      <div><span class="eyebrow">响指电影 · 内容运营</span><h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p></div>
      <el-button type="primary" size="large" @click="createVisible = true">新建营销需求</el-button>
    </section>
    <nav class="studio-nav" aria-label="营销工作台导航">
      <router-link v-for="tab in tabs" :key="tab.path" :to="{ path: tab.path, query: selectedId ? { campaign: selectedId } : {} }" :class="{ active: mode === tab.mode }">{{ tab.label }}</router-link>
    </nav>
    <el-alert v-if="capabilities && (!capabilities.imageConfigured || !capabilities.textConfigured)" class="notice" :closable="false" type="warning"
      :title="!capabilities.imageConfigured ? '图片服务尚未配置，配置后即可生成海报；仍可编辑已有内容。' : '语言模型尚未配置，配置后即可生成文案和绘图指令。'" />
    <el-alert v-if="capabilities && !capabilities.posterContentRequired" class="notice" :closable="false" type="warning" title="海报生成服务尚未更新，请重启后端服务后再根据文案生成海报。" />
    <div class="campaign-bar">
      <label for="marketing-campaign">当前营销需求</label>
      <el-select id="marketing-campaign" v-model="selectedId" placeholder="选择或新建营销需求" filterable aria-label="当前营销需求" @change="chooseCampaign">
        <el-option v-for="campaign in campaigns" :key="campaign.id" :label="campaign.name" :value="campaign.id" />
      </el-select>
      <el-button :disabled="!selectedId || refreshing" @click="refresh()">刷新</el-button>
    </div>
    <el-empty v-if="!detail && !loading" description="从一份影院活动需求开始，生成可审核、可发布的营销内容。" />
    <template v-if="detail">
      <section class="brief-card">
        <div><span class="eyebrow">{{ cinemaName(detail.cinemaId) }} · {{ detail.audience }}</span><h2>{{ detail.name }}</h2><p>{{ detail.brief }}</p></div>
        <div class="brief-tags"><el-tag v-for="movie in detail.movieSnapshot" :key="movie.id" effect="plain">{{ movie.title }}</el-tag><el-tag type="info" v-for="ratio in detail.ratios" :key="ratio">{{ ratio }}</el-tag></div>
      </section>
      <div class="metric-grid">
        <div><span>生成任务</span><strong>{{ detail.tasks.length }}</strong></div><div><span>完成 / 异常</span><strong>{{ succeeded }} <small>/ {{ failed }}</small></strong></div>
        <div><span>图片模型调用尝试</span><strong>{{ imageCalls }}</strong></div><div><span>内容版本 / 发布</span><strong>{{ detail.versions.length }} <small>/ {{ detail.publications.filter(p => p.status === 'PUBLISHED' && new Date(p.endAt).getTime() > Date.now()).length }}</small></strong></div>
        <div><span>生成成功率（终态）</span><strong>{{ successRate }}</strong></div><div><span>审核通过率</span><strong>{{ reviewRate }}</strong></div>
      </div>

      <MarketingGeneration v-if="mode === 'tasks'" :tasks="pendingImageTasks" :movies="detail.movieSnapshot" :submitting="false" />

      <template v-if="mode === 'studio'">
        <section class="panel">
          <div class="panel-head"><div><h2>生成候选文案</h2><p>根据影片资料创作文案，选用后在下方编辑，再生成与文案呼应的海报。</p></div>
            <el-button :loading="generating === 'COPY'" :disabled="busy || !capabilities?.textConfigured" @click="generate('COPY')">生成文案</el-button></div>
          <p v-if="activeTasks" class="muted">{{ activeTasks }} 个任务正在处理，结果会自动更新。可前往任务与素材页查看进度。</p>
          <div class="copy-grid"><article v-for="task in copyResults" :key="task.id" class="copy-card">
            <el-tag size="small" type="success">{{ movieTitle(task.movieId) }}</el-tag>
            <button v-for="title in task.result?.titles" :key="title" class="title-choice" @click="useCopy(task, title)">{{ title }} <span>选用</span></button>
            <p>{{ task.result?.shortCopy }}</p><small class="muted">生成来源：{{ task.model }}</small>
          </article></div>
        </section>
        <section class="panel content-editor" aria-label="编辑发布内容">
          <div class="panel-head"><div><h2>编辑发布内容</h2><p>先选用或编辑宣传文案，再根据影片和营销标题生成海报；选好海报后保存内容版本。</p></div></div>
          <div class="editor-grid">
            <el-form label-position="top" @submit.prevent="saveVersion">
              <el-form-item label="关联影片"><el-select v-model="edit.movieId" aria-label="关联影片" placeholder="请选择本次要宣传的影片" :disabled="busy" @change="changeMovie"><el-option v-for="movie in detail.movieSnapshot" :key="movie.id" :label="movie.title" :value="movie.id" /></el-select></el-form-item>
              <el-form-item label="营销标题"><el-input v-model="edit.title" aria-label="营销标题" maxlength="60" show-word-limit placeholder="选择候选标题或手动填写" /></el-form-item>
              <el-form-item label="短文案"><el-input v-model="edit.copy" aria-label="短文案" type="textarea" :rows="5" maxlength="300" show-word-limit placeholder="发布前请核对影片与活动信息" /></el-form-item>
              <div class="poster-generator">
                <strong>根据当前文案生成海报</strong>
                <p v-if="capabilities?.imageModel" class="muted">生图模型：{{ capabilities.imageModel }}</p>
                <p class="muted">{{ posterReady ? '将为《' + movieTitle(edit.movieId) + '》根据当前营销标题创作宣传海报。' : '请先选用候选文案，或选择关联影片并填写标题和短文案。' }}</p>
                <div class="actions"><label class="poster-count">海报张数
                  <el-input-number v-model="imageCount" aria-label="海报生成张数" :min="1" :max="maxImageCount" :step="1" step-strictly :precision="0" :disabled="busy" controls-position="right" style="width: 110px" />
                </label><el-button :loading="generating === 'IMAGE'" :disabled="busy || !posterReady || !capabilities?.imageConfigured || !capabilities?.textConfigured || !capabilities?.posterContentRequired" @click="generate('IMAGE')">生成海报（{{ imageCount ?? '—' }} 张）</el-button></div>
                <p class="muted">每次可选 1–{{ maxImageCount }} 张，仅生成当前影片的海报，按已选比例分配。</p>
              </div>
              <el-button type="primary" native-type="submit" :loading="saving" :disabled="!edit.assetId">保存内容版本</el-button>
            </el-form>
            <div><p class="muted">选择一张海报 · {{ editorAssets.length }} 张可用</p>
              <div class="asset-choices"><button v-for="asset in editorAssets" :key="asset.id" class="asset-choice" :class="{ selected: edit.assetId === asset.id }" @click="edit.assetId = asset.id" :aria-pressed="edit.assetId === asset.id">
                <MarketingImage :src="asset.previewUrl" :alt="movieTitle(asset.movieId) + '候选海报'" /><span>{{ asset.style }} · {{ asset.ratio }}{{ edit.assetId === asset.id ? ' · 已选' : '' }}</span>
              </button></div><el-empty v-if="!editorAssets.length" description="该影片尚无海报，生成完成后可在这里选择。" :image-size="70" />
            </div>
          </div>
          <MarketingGeneration :tasks="pendingImageTasks" :movies="detail.movieSnapshot" :submitting="generating === 'IMAGE'" />
        </section>
        <section class="panel"><h2>已保存的内容版本</h2><div class="version-list"><article v-for="version in detail.versions" :key="version.id" class="version-row">
          <div><strong>V{{ version.version }} · {{ version.payload.title }}</strong><p class="muted">{{ movieTitle(version.movieId) }} · {{ statusLabel(version.status) }}</p></div>
          <div class="actions"><el-button @click="showPreview(version)">预览</el-button><el-button @click="editVersion(version)">另存编辑</el-button><el-button v-if="version.status === 'DRAFT'" type="primary" :disabled="busy" @click="submit(version)">提交审核</el-button></div>
        </article></div><el-empty v-if="!detail.versions.length" description="保存后，内容版本将出现在这里。" :image-size="60" /></section>
      </template>

      <template v-if="mode === 'tasks'">
        <section class="panel"><div class="panel-head"><div><h2>任务进度</h2><p>模型调用尝试与生成文件数量分别统计；费用以供应商实际账单为准。</p></div><el-tag>{{ activeTasks }} 个处理中</el-tag></div>
          <el-table :data="detail.tasks" row-key="id">
            <el-table-column label="影片" min-width="130"><template #default="{ row }">{{ movieTitle(row.movieId) }}</template></el-table-column>
            <el-table-column label="任务" min-width="130"><template #default="{ row }">{{ row.kind === 'COPY' ? '文案' : row.style + ' / ' + row.ratio }}</template></el-table-column>
            <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="tagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="attempts" label="处理次数" width="90" />
            <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.durationMs ? (row.durationMs / 1000).toFixed(1) + 's' : '—' }}</template></el-table-column>
            <el-table-column prop="errorMessage" label="处理说明" min-width="220" />
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="['FAILED','SUBMIT_UNKNOWN'].includes(row.status)" link type="primary" :disabled="busy" @click="retry(row)">{{ row.status === 'SUBMIT_UNKNOWN' ? '核实后重试' : '重试' }}</el-button></template></el-table-column>
          </el-table><el-empty v-if="!detail.tasks.length" description="尚未生成内容，请到营销创作页创建任务。" :image-size="60" />
        </section>
        <section class="panel"><h2>素材库</h2><div class="asset-grid"><article v-for="asset in detail.assets" :key="asset.id" class="gallery-card"><MarketingImage :src="asset.previewUrl" :alt="movieTitle(asset.movieId) + '营销海报'" />
          <div><strong>{{ movieTitle(asset.movieId) }}</strong><p>{{ asset.style }} · {{ asset.ratio }} · {{ asset.width }} × {{ asset.height }}</p><p class="muted">{{ asset.sourceType === 'MODEL_POSTER' ? '模型生成海报' : asset.sourceType === 'MODEL_GENERATED' ? '模型生成并排版' : '复用背景重新排版' }} · {{ asset.model }}</p><el-button size="small" @click="downloadAsset(asset)">下载海报</el-button></div>
        </article></div></section>
      </template>

      <template v-if="mode === 'review'">
        <section class="panel"><div class="panel-head"><div><h2>内容审核</h2><p>{{ isAdmin ? '请核对影片事实、活动信息和画面，审核结论仅适用于当前版本。' : '在创作页保存并提交版本，由平台管理员审核和发布。' }}</p></div></div>
          <div class="version-list"><article v-for="version in detail.versions" :key="version.id" class="review-card">
            <div class="version-row"><div><el-tag :type="tagType(version.status)">{{ statusLabel(version.status) }}</el-tag><h3>V{{ version.version }} · {{ version.payload.title }}</h3><p class="muted">{{ movieTitle(version.movieId) }} · {{ version.createdAt.replace('T', ' ') }}</p></div>
              <div class="actions"><el-button @click="showPreview(version)">预览与对照</el-button>
                <el-button v-if="version.status === 'DRAFT'" :disabled="busy" @click="submit(version)">提交审核</el-button>
                <template v-if="isAdmin && version.status === 'PENDING_REVIEW'"><el-button type="danger" plain :disabled="busy" @click="review(version, false)">退回</el-button><el-button type="success" :disabled="busy" @click="review(version, true)">审核通过</el-button></template>
                <el-button v-if="isAdmin && ['APPROVED','PUBLISHED'].includes(version.status)" type="primary" @click="openPublish(version)">发布到站内</el-button>
              </div>
            </div><p class="review-copy">{{ version.payload.copy }}</p>
          </article></div><el-empty v-if="!detail.versions.length" description="尚无内容版本，请先在创作页保存。" :image-size="60" />
        </section>
        <section class="panel"><h2>发布记录</h2><el-table :data="detail.publications">
          <el-table-column label="内容版本" min-width="150"><template #default="{ row }">{{ publicationTitle(row.contentVersionId) }}</template></el-table-column>
          <el-table-column label="展示位置" width="130"><template #default="{ row }">{{ row.slot === 'HOME' ? '首页精选' : '影院活动位' }}</template></el-table-column>
          <el-table-column prop="endAt" label="展示截止" min-width="180" />
          <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.status === 'OFFLINE' ? '已撤下' : new Date(row.endAt).getTime() <= Date.now() ? '已到期' : new Date(row.startAt).getTime() > Date.now() ? '待展示' : '展示中' }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="isAdmin && row.status === 'PUBLISHED'" type="danger" link :disabled="busy" @click="offline(row.id)">撤下</el-button></template></el-table-column>
        </el-table></section>
        <section class="panel"><h2>操作记录</h2><div class="audit-row" v-for="log in detail.logs.filter(l => l.action !== 'IMAGE_RESERVE')" :key="log.id"><time>{{ log.createdAt.replace('T', ' ') }}</time><strong>{{ actionLabel(log.action) }}</strong><span>{{ log.reason }}</span></div></section>
      </template>
    </template>

    <el-dialog v-model="createVisible" title="新建营销需求" width="min(640px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="所属影院"><el-select v-model="form.cinemaId" placeholder="选择影院"><el-option v-for="cinema in allowedCinemas" :key="cinema.id" :label="cinema.name" :value="cinema.id" /></el-select></el-form-item>
        <el-form-item label="活动名称"><el-input v-model="form.name" maxlength="120" placeholder="例如：周末一起去看电影" /></el-form-item>
        <el-form-item label="活动需求"><el-input v-model="form.brief" type="textarea" :rows="3" maxlength="600" placeholder="描述主题、氛围和想传达的信息，避免填写未经确认的价格和优惠。" /></el-form-item>
        <el-form-item label="目标人群"><el-input v-model="form.audience" maxlength="80" placeholder="例如：周末结伴观影的朋友" /></el-form-item>
        <el-form-item label="关联影片"><el-select v-model="form.movieIds" multiple filterable placeholder="选择一部或多部影片"><el-option v-for="movie in movies" :key="movie.id" :label="movie.title" :value="movie.id" /></el-select></el-form-item>
        <el-form-item label="图片比例"><el-checkbox-group v-model="form.ratios"><el-checkbox v-for="ratio in ['16:9','3:4','1:1']" :key="ratio" :label="ratio" /></el-checkbox-group></el-form-item>
        <p class="muted">当前有 {{ form.movieIds.length * form.ratios.length }} 种影片与比例组合；生成时可自行选择总张数。保存需求不会调用模型。</p>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="createCampaign">保存营销需求</el-button></template>
    </el-dialog>
    <el-dialog v-model="previewVisible" title="内容预览与版本对照" width="min(1000px, 94vw)">
      <div v-if="preview" class="preview-grid"><MarketingImage :src="assetUrl(preview.payload.assetId)" :alt="preview.payload.title" />
        <div><el-tag>V{{ preview.version }} · {{ statusLabel(preview.status) }}</el-tag><h2>{{ preview.payload.title }}</h2><p>{{ preview.payload.copy }}</p><p class="muted">购票入口：{{ movieTitle(preview.movieId) }} · 查看场次</p>
          <div v-if="previousVersion" class="previous-version"><strong>上一内容版本 V{{ previousVersion.version }}</strong><h3>{{ previousVersion.payload.title }}</h3><p>{{ previousVersion.payload.copy }}</p></div>
        </div></div>
    </el-dialog>
    <el-dialog v-model="publishVisible" title="确认发布内容" width="min(600px, 94vw)">
      <p><strong>{{ publishingVersion?.payload.title }}</strong></p><p class="muted">将发布审核通过的 V{{ publishingVersion?.version }}，后续编辑将另存为新版本。</p>
      <el-form label-position="top"><el-form-item label="展示位置"><el-radio-group v-model="publishForm.slot"><el-radio label="CINEMA">影院活动位</el-radio><el-radio label="HOME">首页精选</el-radio></el-radio-group></el-form-item>
        <el-form-item label="开始展示"><el-date-picker v-model="publishForm.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空表示立即展示" /></el-form-item>
        <el-form-item label="展示截止"><el-date-picker v-model="publishForm.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-form>
      <template #footer><el-button @click="publishVisible = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publish">确认发布</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { marketingApi, marketingStatus, type MarketingAsset, type MarketingCampaign, type MarketingCapabilities, type MarketingDetail, type MarketingTask, type MarketingVersion } from '@/api/marketing'
import { cinemaApi } from '@/api/cinema'
import { movieApi } from '@/api/movie'
import { useUserStore } from '@/stores/user'
import MarketingImage from './MarketingImage.vue'
import MarketingGeneration from './MarketingGeneration.vue'
const props = defineProps<{ mode: 'studio' | 'tasks' | 'review' }>()
const route = useRoute(), router = useRouter(), store = useUserStore()
const isAdmin = computed(() => store.userInfo?.role === 'ADMIN')
const tabs = [{ mode: 'studio', path: '/marketing/studio', label: '营销创作' }, { mode: 'tasks', path: '/marketing/tasks', label: '任务与素材' }, { mode: 'review', path: '/marketing/review', label: '审核与发布' }]
const pageTitle = computed(() => props.mode === 'studio' ? '让好电影，被更多人看见' : props.mode === 'tasks' ? '任务与素材中心' : '把每一份内容，认真发布')
const pageDescription = computed(() => props.mode === 'studio' ? '从真实影片资料出发，批量创作海报与文案。' : props.mode === 'tasks' ? '查看生成进度，保留可用素材，单独处理失败任务。' : '人工核对、版本审核与站内上架。')
const loading = ref(true), refreshing = ref(false), creating = ref(false), saving = ref(false), operating = ref(false), publishing = ref(false), generating = ref('')
const busy = computed(() => !!generating.value || saving.value || operating.value || publishing.value)
const campaigns = ref<MarketingCampaign[]>([]), detail = ref<MarketingDetail>(), capabilities = ref<MarketingCapabilities>()
const cinemas = ref<{ id: number; name: string }[]>([]), movies = ref<{ id: number; title: string }[]>([])
const allowedCinemas = computed(() => isAdmin.value ? cinemas.value : cinemas.value.filter(c => c.id === store.userInfo?.cinemaId))
const selectedId = ref(typeof route.query.campaign === 'string' ? route.query.campaign : '')
const createVisible = ref(false), previewVisible = ref(false), publishVisible = ref(false)
const preview = ref<MarketingVersion>(), publishingVersion = ref<MarketingVersion>()
const form = reactive({ cinemaId: undefined as number | undefined, name: '', brief: '', audience: '', movieIds: [] as number[], styles: ['自由创作'], ratios: ['3:4'] })
const edit = reactive({ movieId: undefined as number | undefined, title: '', copy: '', assetId: '' })
const posterReady = computed(() => !!edit.movieId && !!edit.title.trim() && !!edit.copy.trim())
const publishForm = reactive({ slot: 'CINEMA', startAt: '', endAt: '', idempotencyKey: '' })
let disposed = false, timer: ReturnType<typeof setInterval> | undefined
const succeeded = computed(() => detail.value?.tasks.filter(t => t.status === 'SUCCEEDED').length || 0)
const failed = computed(() => detail.value?.tasks.filter(t => ['FAILED','SUBMIT_UNKNOWN'].includes(t.status)).length || 0)
const successRate = computed(() => { const terminal = detail.value?.tasks.filter(t => ['SUCCEEDED', 'FAILED'].includes(t.status)).length || 0; return terminal ? (succeeded.value / terminal * 100).toFixed(0) + '%' : '—' })
const reviewRate = computed(() => { const approved = detail.value?.versions.filter(v => ['APPROVED', 'PUBLISHED'].includes(v.status)).length || 0; const rejected = detail.value?.versions.filter(v => v.status === 'REJECTED').length || 0; return approved + rejected ? (approved / (approved + rejected) * 100).toFixed(0) + '%' : '—' })
const activeStatuses = ['QUEUED','RUNNING','PLANNING','SUBMITTING','PERSISTING']
const activeTasks = computed(() => detail.value?.tasks.filter(t => activeStatuses.includes(t.status)).length || 0)
const pendingImageTasks = computed(() => detail.value?.tasks.filter(t => t.kind === 'IMAGE' && activeStatuses.includes(t.status)) || [])
const imageCalls = computed(() => detail.value?.tasks.filter(t => t.kind === 'IMAGE').reduce((sum, t) => sum + t.providerCalls, 0) || 0)
const imageCount = ref<number | undefined>(1)
const maxImageCount = computed(() => capabilities.value?.maxImagesPerBatch || 18)
const copyResults = computed(() => detail.value?.tasks.filter(t => t.kind === 'COPY' && t.status === 'SUCCEEDED') || [])
const editorAssets = computed(() => detail.value?.assets.filter(a => a.movieId === edit.movieId) || [])
const previousVersion = computed(() => preview.value && detail.value?.versions.find(v => v.movieId === preview.value?.movieId && v.version < preview.value.version))
const movieTitle = (id: number | undefined) => detail.value?.movieSnapshot.find(m => m.id === id)?.title || '影片'
const cinemaName = (id: number) => cinemas.value.find(c => c.id === id)?.name || '所属影院'
const publicationTitle = (id: string) => { const version = detail.value?.versions.find(v => v.id === id); return version ? 'V' + version.version + ' · ' + version.payload.title : '内容版本' }
const statusLabel = (status: string) => marketingStatus[status] || status
const tagType = (status: string): 'success' | 'warning' | 'danger' | 'info' => ['SUCCEEDED','APPROVED','PUBLISHED'].includes(status) ? 'success' : ['FAILED','REJECTED'].includes(status) ? 'danger' : ['SUBMIT_UNKNOWN','PENDING_REVIEW','SUBMITTING'].includes(status) ? 'warning' : 'info'
const actionLabel = (action: string) => ({ BATCH_CREATE: '创建生成批次', TASK_RETRY: '重试任务', VERSION_CREATE: '保存内容版本', SUBMIT_REVIEW: '提交审核', APPROVE: '审核通过', REJECT: '退回修改', PUBLISH: '发布内容', OFFLINE: '撤下内容' } as Record<string,string>)[action] || action
const assetUrl = (id: string) => '/api/admin/marketing/assets/' + id + '/file'
async function refresh() {
  if (!selectedId.value || refreshing.value) return
  const id = selectedId.value; refreshing.value = true
  try {
    const result = await marketingApi.detail(id)
    if (!disposed && selectedId.value === id) {
      detail.value = result
      if (edit.movieId && !result.movieSnapshot.some(m => m.id === edit.movieId)) { edit.movieId = undefined; edit.title = ''; edit.copy = ''; edit.assetId = '' }
      if (!edit.movieId && result.movieSnapshot.length === 1) edit.movieId = result.movieSnapshot[0]?.id
    }
  } finally { refreshing.value = false }
}
async function chooseCampaign() {
  detail.value = undefined; edit.movieId = undefined; edit.title = ''; edit.copy = ''; edit.assetId = ''
  await router.replace({ query: { campaign: selectedId.value } })
  await refresh()
}
async function createCampaign() {
  if (!form.cinemaId || !form.name.trim() || !form.brief.trim() || !form.audience.trim() || !form.movieIds.length || !form.ratios.length) return void ElMessage.warning('请完整填写影院、需求、受众、影片和比例')
  creating.value = true
  try { const result = await marketingApi.create(form); campaigns.value.unshift(result); selectedId.value = result.id; createVisible.value = false; await chooseCampaign(); ElMessage.success('营销需求已保存') } finally { creating.value = false }
}
async function generate(kind: 'COPY' | 'IMAGE') {
  if (!detail.value || busy.value) return
  const cid = selectedId.value
  const count = kind === 'COPY' ? undefined : imageCount.value
  const posterContent = kind === 'IMAGE' && edit.movieId ? { movieId: edit.movieId, title: edit.title.trim(), copy: edit.copy.trim() } : undefined
  if (kind === 'IMAGE') {
    if (!capabilities.value?.posterContentRequired) return void ElMessage.warning('请重启后端服务，再根据编辑文案生成海报')
    if (!posterContent || !posterReady.value) return void ElMessage.warning('请先选用或填写营销标题和短文案，再生成海报')
    if (count == null || !Number.isInteger(count) || count < 1 || count > maxImageCount.value) return void ElMessage.warning('请选择 1–' + maxImageCount.value + ' 张海报')
    try { await ElMessageBox.confirm('将为《' + movieTitle(posterContent.movieId) + '》根据营销标题「' + posterContent.title + '」生成 ' + count + ' 张宣传海报，先由语言模型编写提示词，再调用生图模型。', '生成宣传海报', { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'info' }) } catch { return }
  }
  generating.value = kind
  const storageKey = 'marketing-request:' + cid + ':' + kind + ':' + JSON.stringify({ count, posterContent })
  const key = sessionStorage.getItem(storageKey) || crypto.randomUUID()
  sessionStorage.setItem(storageKey, key)
  try { await marketingApi.generate(cid, kind, key, count, posterContent); sessionStorage.removeItem(storageKey); await refresh(); ElMessage.success('生成任务已创建，可离开页面后回来查看') } finally { generating.value = '' }
}
function useCopy(task: MarketingTask, title: string) { edit.movieId = task.movieId; edit.title = title; edit.copy = task.result?.shortCopy || ''; resetAsset() }
function resetAsset() { if (!editorAssets.value.some(a => a.id === edit.assetId)) edit.assetId = '' }
function changeMovie() { edit.title = ''; edit.copy = ''; edit.assetId = '' }
async function saveVersion() {
  if (!edit.movieId || !edit.title.trim() || !edit.copy.trim() || !edit.assetId) return void ElMessage.warning('请填写标题、文案并选择海报')
  saving.value = true
  try { await marketingApi.save(selectedId.value, edit); await refresh(); ElMessage.success('内容版本已保存，可预览并提交审核') } finally { saving.value = false }
}
function editVersion(version: MarketingVersion) { edit.movieId = version.movieId; edit.title = version.payload.title; edit.copy = version.payload.copy; edit.assetId = version.payload.assetId; ElMessage.success('已带入编辑区，保存会创建新版本') }
function showPreview(version: MarketingVersion) { preview.value = version; previewVisible.value = true }
async function operation(action: () => Promise<unknown>, message: string) { if (busy.value) return; operating.value = true; try { await action(); await refresh(); ElMessage.success(message) } finally { operating.value = false } }
async function submit(version: MarketingVersion) { await operation(() => marketingApi.submit(version.id), '已提交审核') }
async function review(version: MarketingVersion, approve: boolean) {
  let reason = '已核对影片事实、活动信息和海报画面'
  try {
    if (approve) await ElMessageBox.confirm('请在预览中核对图文和影片信息。确认通过 V' + version.version + ' 的审核？', '确认审核', { confirmButtonText: '审核通过', cancelButtonText: '取消' })
    else { const response = await ElMessageBox.prompt('请说明需要修改的内容', '退回修改', { inputPattern: /\S+/, inputErrorMessage: '请填写退回原因', inputValidator: value => (value?.length || 0) <= 300 || '最多 300 字' }); reason = response.value }
  } catch { return }
  await operation(() => marketingApi.review(version.id, approve, reason), approve ? '审核通过' : '已退回修改')
}
async function retry(task: MarketingTask) {
  const uncertain = task.status === 'SUBMIT_UNKNOWN'
  if (uncertain) {
    try { await ElMessageBox.confirm('供应商可能已经受理此任务。请先核实调用记录；重新生成可能再次计费。', '核实后重新生成', { confirmButtonText: '已核实，重新生成', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  }
  await operation(() => marketingApi.retry(task.id, uncertain), '任务已重新排队')
}
function localDate(date: Date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0,19) }
function openPublish(version: MarketingVersion) { publishingVersion.value = version; publishForm.slot = 'CINEMA'; publishForm.startAt = ''; publishForm.endAt = localDate(new Date(Date.now() + 7 * 86400000)); publishForm.idempotencyKey = crypto.randomUUID(); publishVisible.value = true }
async function publish() {
  if (!publishingVersion.value || !publishForm.endAt) return void ElMessage.warning('请选择展示截止时间')
  publishing.value = true
  try { await marketingApi.publish(publishingVersion.value.id, { ...publishForm, startAt: publishForm.startAt || null }); publishVisible.value = false; await refresh(); ElMessage.success('内容已发布') } finally { publishing.value = false }
}
async function offline(id: string) { await operation(() => marketingApi.offline(id), '内容已撤下') }
async function downloadAsset(asset: MarketingAsset) {
  const response = await fetch(asset.previewUrl, { headers: { Authorization: 'Bearer ' + store.token } })
  if (!response.ok) return void ElMessage.error('素材下载失败')
  const url = URL.createObjectURL(await response.blob()), anchor = document.createElement('a')
  anchor.href = url; anchor.download = movieTitle(asset.movieId) + '-' + asset.ratio.replace(':','x') + '.png'; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(async () => {
  try {
    const [cap, list, cinemaList, moviePage] = await Promise.all([marketingApi.capabilities(), marketingApi.campaigns(), cinemaApi.getAll(), movieApi.getPage({ page: 1, size: 1000 })])
    capabilities.value = cap; campaigns.value = list
    cinemas.value = cinemaList.filter(c => c.id != null).map(c => ({ id: c.id!, name: c.name }))
    movies.value = moviePage.records.filter(m => m.id != null && (m.status === 1 || m.status === 2)).map(m => ({ id: m.id!, title: m.title }))
    form.cinemaId = allowedCinemas.value[0]?.id
    if (!selectedId.value) selectedId.value = list[0]?.id || ''
    if (selectedId.value) await refresh()
    timer = setInterval(() => { if (!disposed) void refresh().catch(() => {}) }, 2500)
  } finally { loading.value = false }
})
onBeforeUnmount(() => { disposed = true; if (timer) clearInterval(timer) })
</script>

<style scoped>
.marketing-workspace{max-width:1500px;margin:0 auto;color:#243149}
.studio-header{display:flex;justify-content:space-between;align-items:center;gap:24px;padding:24px 0 28px}
.eyebrow{font-size:12px;font-weight:700;color:#a35c27;letter-spacing:1px}
h1{font-size:30px;letter-spacing:-1px;margin:10px 0}h2{font-size:19px;margin:0 0 10px}h3{font-size:17px;margin:12px 0}p{line-height:1.7;margin:8px 0;color:#59677d}
.studio-nav{display:flex;gap:28px;border-bottom:1px solid #dfe5ed;margin-bottom:24px}.studio-nav a{padding:14px 3px;color:#657187;text-decoration:none;font-weight:600;border-bottom:3px solid transparent}.studio-nav a.active{color:#a35c27;border-bottom-color:#bc7844}
.poster-count{display:flex;align-items:center;gap:8px;font-size:12px;color:#59677d;white-space:nowrap}
.poster-generator{margin:0 0 20px;padding:16px;background:#fffaf3;border:1px solid #eee1d0;border-radius:10px}.poster-generator>strong{font-size:14px;color:#775133}.content-editor>.generation-panel{margin:22px 0 0}
.notice{margin-bottom:18px}.campaign-bar{display:flex;align-items:center;gap:16px;margin:18px 0}.campaign-bar label{font-size:13px;font-weight:600;white-space:nowrap}.campaign-bar .el-select{max-width:400px;flex:1}
.brief-card{background:linear-gradient(115deg,#f8f1e8,#fffaf4);border:1px solid #ecdfcf;padding:24px 28px;border-radius:14px;display:flex;justify-content:space-between;gap:24px}.brief-card h2{font-size:23px;margin:10px 0}.brief-tags{display:flex;gap:8px;flex-wrap:wrap;align-content:flex-start;max-width:340px}
.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:20px 0}.metric-grid>div{background:white;border:1px solid #e4e9f0;border-radius:12px;padding:18px 22px}.metric-grid span{display:block;font-size:12px;color:#6c7a91;margin-bottom:10px}.metric-grid strong{font-size:27px}.metric-grid small{font-size:18px;color:#8793a5}
.panel{background:#fff;border:1px solid #e4e9f0;border-radius:14px;padding:24px;margin-bottom:22px}.panel-head{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;margin-bottom:18px}.panel-head p{font-size:13px}.actions{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.actions .el-button+.el-button{margin-left:0}.muted{font-size:12px;color:#8490a3}
.copy-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}.copy-card{padding:20px;background:#f8fafc;border-radius:12px;border:1px solid #edf0f5}.title-choice{border:0;text-align:left;display:flex;justify-content:space-between;gap:12px;width:100%;background:transparent;padding:12px 0;border-bottom:1px solid #e3e9f0;color:#25354d;cursor:pointer;font:inherit;font-size:14px;line-height:1.5}.title-choice:hover{color:#a35c27}.title-choice span{font-size:12px;white-space:nowrap;color:#a35c27}
.editor-grid{display:grid;grid-template-columns:minmax(280px,1fr) minmax(280px,1.2fr);gap:36px}.el-select{width:100%}.asset-choices{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-height:580px;overflow:auto}.asset-choice{padding:4px;border:2px solid transparent;border-radius:10px;background:#f4f6f9;cursor:pointer;overflow:hidden}.asset-choice.selected{border-color:#bc7844;background:#fff7ed}.asset-choice .marketing-image{height:155px;border-radius:6px}.asset-choice>span{font-size:11px;display:block;padding:10px 0;color:#5f6f85}
.version-row{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:18px 0;border-bottom:1px solid #edf0f5}.version-row p{margin-bottom:0}.review-card{padding:14px 20px;background:#f8fafc;border-radius:12px;margin:12px 0}.review-card .version-row{padding-top:4px}.review-copy{white-space:pre-wrap}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:20px}.gallery-card{border:1px solid #e4e9f0;border-radius:12px;overflow:hidden}.gallery-card .marketing-image{height:280px}.gallery-card>div:last-child{padding:16px}.gallery-card p{font-size:12px}.audit-row{display:flex;gap:20px;border-bottom:1px solid #edf0f5;padding:13px 0;font-size:12px}.audit-row time{color:#8490a3;white-space:nowrap}.audit-row span{overflow-wrap:anywhere;color:#59677d}.preview-grid{display:grid;grid-template-columns:1fr 1fr;gap:28px}.preview-grid .marketing-image{max-height:650px}.previous-version{border-top:1px solid #dfe5ed;margin-top:28px;padding-top:20px;color:#728098}.preview-grid p{white-space:pre-wrap}
@media(max-width:1100px){.panel-head{flex-direction:column}.asset-choices{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.studio-header,.brief-card{flex-direction:column;align-items:flex-start}.metric-grid{grid-template-columns:repeat(2,1fr)}.editor-grid,.preview-grid{grid-template-columns:1fr}.version-row{align-items:flex-start;flex-direction:column}.panel{padding:18px}.campaign-bar{flex-wrap:wrap}.studio-nav{gap:20px}.audit-row{flex-wrap:wrap;gap:8px}.studio-header h1{font-size:25px}}
</style>
