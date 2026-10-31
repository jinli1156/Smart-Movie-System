<template>
  <el-dialog :model-value="modelValue" title="AI 观影纪念票" width="960px" class="souvenir-dialog"
    :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <div class="souvenir-studio" v-loading="loading">
      <section class="preview-area" aria-label="纪念票预览">
        <img v-if="imageUrl" class="finished-ticket" :src="imageUrl" :alt="`${current?.facts.movieTitle}的 AI 观影纪念票`" />
        <div v-else class="ticket-preview">
          <div class="ticket-art">
            <div class="cosmic-orbit"></div>
            <div class="art-shade"></div>
            <span class="ticket-brand">响指电影 / STELLAR ARCHIVE</span>
            <div class="ticket-film"><span>留住这一场光影</span><h3>{{ order.movieTitle }}</h3></div>
          </div>
          <div class="ticket-copy">
            <span class="ticket-kicker">星空科幻 · 专属光影收藏</span>
            <h4>{{ current?.headline || '故事会落幕，回忆会发光。' }}</h4>
            <p>{{ current?.caption || '将银幕上的片刻，收藏成属于你的电影记忆。' }}</p>
            <div class="ticket-facts"><strong>{{ order.cinemaName }}</strong><span>{{ formatTime(order.showtime) }} · {{ order.hallName }}</span><span>{{ order.seats }}</span><span>订单尾号 {{ order.orderNo.slice(-6) }}</span></div>
            <small>AI 创作 · 仅作观影纪念 · 非入场凭证</small>
          </div>
        </div>
        <span v-if="!imageUrl" class="preview-label">版式预览 · 生成后呈现 AI 原创星空背景</span>
        <span v-else class="preview-label">专属纪念卡 · 可保存原图分享</span>
      </section>

      <section class="studio-controls" aria-label="纪念票创作设置">
        <div class="studio-intro"><span class="eyebrow">YOUR MEMORY, AMONG THE STARS</span><h2>把这一场光影，<br />存入宇宙。</h2><p>基于真实购票记录，收藏一张属于你的星空科幻纪念票。</p></div>
        <div class="theme-panel"><span class="theme-orb">✦</span><div><strong>星空科幻</strong><p>深蓝星云 · 紫蓝光轨 · 全息粒子</p><small>专属星空背景，珍藏这一场电影的余韵。</small></div></div>
        <label class="note-label" for="souvenir-note">此刻的心情 <span>选填</span></label>
        <textarea id="souvenir-note" v-model="note" maxlength="120" rows="2" :disabled="busy || !!current"
          placeholder="例如：和朋友一起，收藏一个轻松的周末" />
        <span class="note-count">{{ note.length }}/120</span>

        <div v-if="pending" class="generation-status" role="status" aria-live="polite"><span class="spinner"></span><div><strong>{{ current?.status === 'QUEUED' ? '已加入创作队列' : 'AI 正在创作你的纪念票' }}</strong><p>画面与文案正在慢慢成形。已提交图片会继续生成，请在24小时内从订单回来查看并保存。</p></div></div>
        <div v-if="error || current?.status === 'FAILED'" class="error-state" role="alert">{{ error || current?.errorMessage || '生成暂未完成，请稍后重试' }}</div>
        <p v-if="capabilities && !capabilities.available && !current" class="service-notice">{{ capabilities.message }}</p>

        <template v-if="current?.status === 'SUCCEEDED'">
          <div class="result-actions"><button class="studio-button primary" :disabled="!imageUrl || busy" @click="save"><MaterialIcon name="download" />保存纪念票</button><button class="studio-button secondary" :disabled="!imageUrl || busy" @click="share"><MaterialIcon name="share" />分享</button></div>
          <button v-if="!imageUrl && !busy" class="text-button" @click="loadImage(current!)">重新加载图片</button>
        </template>
        <button v-else-if="current?.status === 'FAILED' && current.retryable" class="studio-button primary" :disabled="busy" @click="generate(true)">{{ busy ? '正在提交…' : '重试生成 / 继续保存' }}</button>
        <button v-else-if="!current" class="studio-button primary" :disabled="busy || !capabilities?.available || !loaded" @click="generate(false)"><MaterialIcon name="auto_awesome" />{{ busy ? '正在提交…' : '生成我的纪念票' }}</button>

        <button v-if="!loaded && !loading" class="text-button" @click="load">重新加载</button>
        <p class="privacy-note">仅作观影纪念，不能用于入场。票面保留真实场次与座位，订单号仅展示尾号，不含取票码。每笔订单珍藏一张，可随时回来保存。</p>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import type { Order } from '@/types'
import { souvenirApi, type SouvenirCapabilities, type SouvenirTicket } from '@/api/souvenir'

const props = defineProps<{ modelValue: boolean; order: Order }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const note = ref(''), imageUrl = ref(''), error = ref('')
const loading = ref(false), busy = ref(false), loaded = ref(false)
const tickets = ref<SouvenirTicket[]>([]), current = ref<SouvenirTicket | null>(null)
const capabilities = ref<SouvenirCapabilities | null>(null)
const pending = computed(() => current.value?.status === 'QUEUED' || current.value?.status === 'RUNNING')
let timer: ReturnType<typeof setTimeout> | undefined
let version = 0, imageVersion = 0, imageBlob: Blob | null = null, disposed = false
const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : ''
const message = (e: any) => e?.response?.data?.message || e?.message || '暂时无法连接，请稍后重试'
function clearImage() {
  imageVersion++
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''; imageBlob = null
}
async function loadImage(ticket: SouvenirTicket) {
  clearImage()
  const expected = imageVersion
  try {
    const blob = await souvenirApi.image(ticket.id)
    if (disposed || expected !== imageVersion) return
    imageBlob = blob; imageUrl.value = URL.createObjectURL(blob); error.value = ''
  } catch (e) { if (!disposed && expected === imageVersion) error.value = message(e) }
}
function selectTicket(ticket: SouvenirTicket) {
  current.value = ticket; note.value = ticket.note; error.value = ''
  clearImage()
  if (ticket.status === 'SUCCEEDED') void loadImage(ticket)
}
function schedulePoll(expected: number) {
  clearTimeout(timer)
  if (!tickets.value.some(t => t.status === 'QUEUED' || t.status === 'RUNNING') || !props.modelValue || disposed) return
  timer = setTimeout(async () => {
    if (document.visibilityState !== 'visible') { schedulePoll(expected); return }
    try {
      const list = await souvenirApi.list(props.order.orderNo)
      if (disposed || expected !== version) return
      tickets.value = list
      const updated = list.find(t => t.id === current.value?.id)
      if (updated) {
        const becameReady = updated.status === 'SUCCEEDED' && current.value?.status !== 'SUCCEEDED'
        current.value = updated
        if (becameReady) void loadImage(updated)
      }
      error.value = ''
    } catch (e) { if (!disposed && expected === version) error.value = '状态更新暂时中断，正在尝试重新连接；已提交的任务仍会继续。' }
    if (!disposed && expected === version) schedulePoll(expected)
  }, 8000)
}
async function load() {
  const expected = ++version
  clearTimeout(timer); loading.value = true; loaded.value = false; error.value = ''
  try {
    const [caps, list] = await Promise.all([souvenirApi.capabilities(), souvenirApi.list(props.order.orderNo)])
    if (disposed || expected !== version) return
    capabilities.value = caps; tickets.value = list; loaded.value = true
    if (list.length) selectTicket(list[0])
    schedulePoll(expected)
  } catch (e) { if (!disposed && expected === version) error.value = message(e) }
  finally { if (!disposed && expected === version) loading.value = false }
}
async function generate(retry: boolean) {
  if (busy.value) return
  const expected = version
  busy.value = true; error.value = ''
  try {
    const ticket = await souvenirApi.create(props.order.orderNo, note.value, retry)
    if (disposed || expected !== version) return
    tickets.value = [ticket, ...tickets.value.filter(t => t.id !== ticket.id)]
    selectTicket(ticket); schedulePoll(expected)
  } catch (e) { if (!disposed && expected === version) error.value = message(e) }
  finally { if (!disposed && expected === version) busy.value = false }
}
function save() {
  if (!imageUrl.value) return
  const link = document.createElement('a'); link.href = imageUrl.value
  link.download = `${(current.value?.facts.movieTitle || '我的电影').replace(/[<>:"/\\|?*]/g, '_')}-观影纪念票.png`
  document.body.appendChild(link); link.click(); link.remove()
}
async function share() {
  if (!imageBlob) return
  const file = new File([imageBlob], 'cinema-memory.png', { type: 'image/png' })
  try {
    if (navigator.canShare?.({ files: [file] }) && navigator.share) {
      await navigator.share({ title: '我的 AI 观影纪念票', files: [file] })
    } else { save(); ElMessage.info('纪念票已下载，可以将图片分享给朋友') }
  } catch (e: any) {
    if (e.name !== 'AbortError') { save(); ElMessage.info('请将下载的纪念票图片分享给朋友') }
  }
}
watch(() => props.modelValue, open => {
  if (open) void load()
  else { version++; clearTimeout(timer); clearImage(); busy.value = false }
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; version++; clearTimeout(timer); clearImage() })
</script>

<style>
.souvenir-dialog.el-dialog { max-width: calc(100vw - 24px); border-radius: 22px; padding: 24px; margin-top: 5vh; background: #10172b; border: 1px solid #35456d; color: #dce7fa; box-shadow: 0 25px 100px #080b2660; }
.souvenir-dialog .el-dialog__title { color: #e1eafa; letter-spacing: 1px; }
.souvenir-dialog .el-dialog__body { padding-top: 8px; }
.souvenir-dialog .el-dialog__headerbtn .el-dialog__close { color: #b2c6ee; }
</style>
<style scoped>
.souvenir-studio { display: grid; grid-template-columns: 1fr 1fr; gap: 32px; color: #dce7fa; }
.preview-area { background: radial-gradient(ellipse at 20% 20%, #30295b88, transparent 70%), #0a1020; padding: 25px 30px 18px; border-radius: 14px; border: 1px solid #263452; display: flex; flex-direction: column; align-items: center; justify-content: center; min-width: 0; }
.ticket-preview, .finished-ticket { width: 100%; max-width: 330px; background: #080e26; box-shadow: 0 0 2px #96ddff, 0 0 24px #6473ff35, 0 18px 35px #0007; border-radius: 8px; overflow: hidden; }
.ticket-preview { position: relative; border: 1px solid #7294c8; }
.ticket-preview::after { content: ''; pointer-events: none; position: absolute; inset: 7px; border: 1px solid #7397d16b; clip-path: polygon(10px 0, calc(100% - 10px) 0, 100% 10px, 100% calc(100% - 10px), calc(100% - 10px) 100%, 10px 100%, 0 calc(100% - 10px), 0 10px); }
.ticket-art { position: relative; height: 255px; overflow: hidden; background: radial-gradient(ellipse at 68% 30%, #a584dfaa 0, #554b9270 16%, transparent 47%), radial-gradient(ellipse at 18% 62%, #3e62a6a0, transparent 49%), linear-gradient(145deg, #0a1638, #19193b 60%, #0c1027); }
.ticket-art::before { content: ''; position: absolute; inset: 0; background-image: radial-gradient(1px 1px at 15% 20%, #fff, transparent), radial-gradient(2px 2px at 65% 18%, #d1d6ff, transparent), radial-gradient(1px 1px at 45% 45%, white, transparent), radial-gradient(1px 1px at 85% 65%, #aedbff, transparent), radial-gradient(2px 2px at 28% 70%, #cfc1ff, transparent), radial-gradient(1px 1px at 75% 35%, white, transparent); background-size: 90px 100px, 150px 145px, 120px 90px, 170px 160px, 180px 155px, 110px 145px; }
.cosmic-orbit { position: absolute; width: 280px; height: 95px; border-radius: 50%; border: 1px solid #9adfff90; box-shadow: 0 0 10px #8d8dff88, inset 0 0 12px #8d8dff45; transform: rotate(-28deg); top: 65px; left: 15px; }
.cosmic-orbit::before { content: ''; position: absolute; width: 85px; height: 85px; border-radius: 50%; background: radial-gradient(circle at 35% 25%, #9ab2e7, #4a5389 45%, #172143 75%); box-shadow: -2px -2px 20px #a6c5ff50; top: -1px; left: 90px; }
.art-shade { position: absolute; inset: 0; background: linear-gradient(0deg, #080e26, transparent 72%); }
.ticket-brand { position: absolute; top: 22px; left: 22px; right: 18px; font-size: 9px; letter-spacing: 1px; color: #b9d8ff; }
.ticket-film { position: absolute; bottom: 15px; left: 22px; right: 22px; color: white; }.ticket-film span { font-size: 11px; color: #9edfff; }.ticket-film h3 { font-size: 25px; line-height: 1.3; margin: 9px 0 0; overflow-wrap: anywhere; }
.ticket-copy { padding: 16px 22px 23px; }.ticket-kicker { font-size: 10px; color: #b8a4f8; letter-spacing: 1px; }.ticket-copy h4 { margin: 14px 0 10px; font-size: 18px; line-height: 1.5; color: #e9edff; }.ticket-copy p { font-size: 11px; line-height: 1.9; color: #a4b1cf; margin: 0 0 20px; }.ticket-facts { border-top: 1px dashed #475b8a; padding-top: 15px; display: grid; gap: 7px; font-size: 11px; color: #d2dff8; }.ticket-facts span { font-size: 10px; color: #a8b9de; }.ticket-copy small { display: block; font-size: 9px; color: #86d8f5; margin-top: 20px; }
.preview-label { font-size: 10px; color: #8796b8; text-align: center; margin-top: 16px; line-height: 1.6; }
.studio-controls { min-width: 0; }.eyebrow { font-size: 9px; letter-spacing: 1.4px; color: #9c9fdb; }.studio-intro h2 { font-size: 29px; line-height: 1.45; letter-spacing: -1px; margin: 12px 0; color: #eef1ff; }.studio-intro p { font-size: 12px; line-height: 1.8; color: #96a8cc; margin-bottom: 22px; }
.theme-panel { display: flex; gap: 14px; align-items: center; margin: 18px 0 24px; padding: 16px 14px; border: 1px solid #54528c; border-radius: 12px; background: linear-gradient(115deg, #29234180, #15233b80); }.theme-orb { display: grid; place-items: center; font-size: 35px; color: #b5b4ff; text-shadow: 0 0 18px #a6b9ff; width: 45px; flex-shrink: 0; }.theme-panel strong { font-size: 15px; color: #dbd6ff; }.theme-panel p { font-size: 11px; color: #aebcde; margin: 7px 0; }.theme-panel small { font-size: 10px; color: #8c9abb; }
.note-label { display: block; font-size: 12px; font-weight: 600; margin-bottom: 10px; color: #ced8ee; }.note-label span { font-weight: 400; color: #94a3c1; font-size: 10px; }.studio-controls textarea { box-sizing: border-box; width: 100%; resize: vertical; border: 1px solid #364567; border-radius: 9px; padding: 10px; font: inherit; font-size: 12px; line-height: 1.7; background: #0c1427; color: #d3dff5; }.studio-controls textarea::placeholder { color: #8493b0; }.studio-controls textarea:focus { outline: 2px solid #7768ba; }.note-count { display: block; text-align: right; font-size: 10px; color: #8493b0; margin: 4px 0 18px; }
.studio-button { border: 1px solid transparent; border-radius: 10px; min-height: 46px; padding: 10px 16px; display: flex; justify-content: center; align-items: center; gap: 8px; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; width: 100%; }.studio-button.primary { background: linear-gradient(100deg, #6479ce, #8a6bca); border-color: #a6a9f2; box-shadow: 0 0 22px #7a6cff35; color: #fff; }.studio-button.secondary { background: #1d2944; border-color: #3f5275; color: #d9e7ff; }.studio-button:disabled { opacity: .5; cursor: default; }.result-actions { display: flex; gap: 8px; }.result-actions .primary { flex: 2; }.result-actions .secondary { flex: 1; }
.generation-status { display: flex; align-items: flex-start; gap: 10px; padding: 13px; background: #202c47; border: 1px solid #3d5073; border-radius: 10px; margin-bottom: 12px; }.generation-status strong { font-size: 12px; color: #c9d7f6; }.generation-status p { font-size: 11px; line-height: 1.8; margin: 5px 0 0; color: #9badcf; }.spinner { flex-shrink: 0; width: 16px; height: 16px; margin-top: 2px; border: 2px solid #52648f; border-top-color: #ad9ff2; border-radius: 50%; animation: souvenir-spin 1s linear infinite; }@keyframes souvenir-spin { to { transform: rotate(360deg); } }
.error-state, .service-notice { font-size: 12px; line-height: 1.7; border-radius: 8px; padding: 10px; margin-bottom: 12px; background: #39243c; color: #efb7cc; border: 1px solid #634561; }.privacy-note { font-size: 10px; line-height: 1.9; color: #899ab9; margin: 16px 0 0; }.text-button { background: none; border: 0; color: #b1b9ff; padding: 10px 0 0; font: inherit; font-size: 11px; cursor: pointer; }
@media (max-width: 680px) { .souvenir-studio { grid-template-columns: minmax(0, 1fr); gap: 22px; }.preview-area { padding: 18px 22px; }.ticket-preview, .finished-ticket { max-width: 300px; }.studio-intro h2 { font-size: 25px; }.ticket-art { height: 215px; } }
@media (prefers-reduced-motion: reduce) { .spinner { animation: none; } }
</style>
