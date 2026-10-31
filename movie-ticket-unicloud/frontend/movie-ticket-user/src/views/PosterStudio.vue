<template>
  <div class="poster-app">
    <AppSidebar />
    <main class="studio-main">
      <header class="studio-header">
        <router-link to="/movies" class="back-link"><MaterialIcon name="arrow_back" /> 发现电影</router-link>
        <span class="header-label">响指电影 / 创意放映室</span>
      </header>
      <section class="studio-intro">
        <div><p class="eyebrow">AI POSTER STUDIO</p><h1>这一次，你是电影主角<span>。</span></h1>
          <p class="intro-copy">把喜欢的电影，拍成属于你的故事。选一种风格，让想象登上大银幕。</p></div>
        <div class="edition"><MaterialIcon name="movie" /><span>你的故事<br /><strong>独家上映</strong></span></div>
      </section>

      <div v-if="loadError" class="notice error" role="alert">{{ loadError }} <button @click="refresh">重新加载</button></div>
      <div v-else-if="capabilities && !capabilities.available" class="notice" role="status">{{ capabilities.message }}</div>

      <div class="workspace">
        <form class="creation-panel" @submit.prevent="generate">
          <div class="panel-heading"><h2>导演你的海报</h2><span>01 — 创作设定</span></div>
          <div class="form-section">
            <label for="movie-theme" class="field-label"><span class="step">01</span> 电影主题 <span class="optional">可选</span></label>
            <input id="movie-theme" v-model="movieTitle" maxlength="80" placeholder="例如：哪吒、星际穿越、千与千寻" />
          </div>
          <fieldset class="form-section style-fieldset">
            <legend class="field-label"><span class="step">02</span> 选择电影风格</legend>
            <div class="style-grid">
              <label v-for="item in styles" :key="item.id" class="style-card" :class="{ selected: style === item.id }">
                <input v-model="style" type="radio" name="poster-style" :value="item.id" :aria-label="item.name" />
                <span class="style-art" :class="item.id.toLowerCase()"><span class="art-orbit"></span><span class="art-horizon"></span><span class="style-check" v-if="style === item.id">✓</span></span>
                <span class="style-name">{{ item.name }}</span>
              </label>
            </div>
          </fieldset>
          <div class="form-section">
            <div class="field-label"><label for="portrait"><span class="step">03</span> 加入你的照片</label><span class="optional">可选</span></div>
            <div class="upload-box" :class="{ 'has-photo': photoUrl, disabled: !capabilities?.photoSupported }">
              <img v-if="photoUrl" :src="photoUrl" alt="已选择的人物参考照片" />
              <MaterialIcon v-else name="add_photo_alternate" />
              <div><strong>{{ photo ? photo.name : '让你成为海报主角' }}</strong><p>{{ capabilities && !capabilities.photoSupported ? '当前服务暂不支持人物照片，可直接描述创意' : '选择一张清晰的人像 · JPG / PNG · 最大 1.4MB' }}</p></div>
              <button v-if="photo" type="button" class="remove-photo" aria-label="移除照片" @click="clearPhoto"><MaterialIcon name="close" /></button>
              <input id="portrait" ref="photoInput" type="file" accept="image/jpeg,image/png" :disabled="!capabilities?.photoSupported" aria-label="上传人物参考照片" @change="choosePhoto" />
            </div>
            <p class="privacy-note">{{ photo ? '生成时，照片会发送至当前生图服务作为人物参考；成功后删除服务器上的原始参考照片。' : '不上传照片也能创作；“我是主角”将以虚构人物呈现。' }}</p>
          </div>
          <div class="form-section">
            <label for="poster-prompt" class="field-label"><span class="step">04</span> 描述你的电影时刻</label>
            <textarea id="poster-prompt" v-model="prompt" :maxlength="promptLimit" rows="4" placeholder="帮我生成一张哪吒电影的赛博朋克风格观影海报，我是主角。霓虹城市，风火轮变成悬浮装置。"></textarea>
            <div class="prompt-footer"><button type="button" @click="useExample">试试「赛博哪吒」灵感</button><span>{{ prompt.length }}/{{ promptLimit }}</span></div>
          </div>
          <fieldset class="ratio-fieldset"><legend class="field-label">海报画幅</legend>
            <div class="ratio-options"><label v-for="item in ratios" :key="item.value" :class="{ selected: ratio === item.value }">
              <input v-model="ratio" type="radio" name="ratio" :value="item.value" /><span class="ratio-icon" :style="{ aspectRatio: item.value.replace(':', '/') }"></span>{{ item.label }}</label></div>
          </fieldset>
          <div v-if="submitError" class="inline-error" role="alert">{{ submitError }}</div>
          <button class="generate-button" :disabled="submitting || !capabilities?.available || capabilities.remaining <= 0 || prompt.length > promptLimit || (!movieTitle.trim() && !prompt.trim())">
            <MaterialIcon :name="submitting ? 'progress_activity' : 'auto_awesome'" :class="{ spinning: submitting }" />{{ submitting ? '正在提交创意…' : '生成我的电影海报' }}<span v-if="!submitting">↗</span>
          </button>
          <p class="quota-note">{{ capabilities ? `今日还可创作 ${capabilities.remaining} 张 · 每日 ${capabilities.dailyLimit} 张` : '正在连接创作服务…' }}<span>提交成功后可离开，请在24小时内回来保存</span></p>
        </form>

        <section class="preview-panel" aria-label="海报预览">
          <div class="preview-heading"><span class="eyebrow">{{ selected ? 'YOUR EXCLUSIVE POSTER' : 'THE NEXT LEADING ROLE' }}</span><span class="preview-tag">{{ selected ? statusText[selected.status] : '创作灵感预览' }}</span></div>
          <div class="preview-stage">
            <img v-if="imageUrl && selected?.status === 'SUCCEEDED'" class="result-image" :src="imageUrl" :alt="`${selected.movieTitle || '我的电影'}的专属电影海报`" />
            <div v-else-if="selected" class="task-state" aria-live="polite">
              <div class="task-symbol" :class="{ 'is-generating': busy }"><MaterialIcon :name="selected.status === 'FAILED' ? 'error_outline' : selected.status === 'SUCCEEDED' ? 'image' : 'auto_awesome'" /></div>
              <p class="eyebrow">{{ styleName(selected.style) }} / {{ selected.ratio }}</p>
              <h2>{{ selected.status === 'QUEUED' ? '创意已就位，等待开拍' : selected.status === 'RUNNING' ? '你的电影世界正在成形' : selected.status === 'FAILED' ? '这次创作暂未完成' : '海报已完成' }}</h2>
              <p>{{ selected.errorMessage || (busy ? 'AI 正在设计画面、人物与光影。已提交的图片会继续生成，请在24小时内回来查看并保存。' : imageError || '正在加载你的专属海报…') }}</p>
              <div v-if="busy" class="progress-track"><span></span></div>
              <button v-if="selected.status === 'FAILED' && selected.retryable" type="button" class="secondary-button" :disabled="submitting" @click="retry">重试生成 / 继续保存</button>
              <button v-if="selected.status === 'SUCCEEDED' && imageError" type="button" class="secondary-button" @click="loadImage">重新加载海报</button>
            </div>
            <div v-else class="inspiration-poster" :class="style.toLowerCase()">
              <div class="poster-grain"></div><div class="poster-sun"></div><div class="poster-ring"></div><div class="poster-city"></div>
              <div class="poster-topline"><span>XIANGZHI PICTURES</span><span>特别呈现</span></div>
              <div class="poster-figure"><span></span></div>
              <div class="poster-title"><p>每个人，都有自己的高光时刻</p><h2>你的名字<br /><span>即将上映</span></h2><div>STARRING YOU · A FILM OF YOUR OWN</div></div>
              <div class="poster-bottomline"><span>{{ styleName(style) }} · 私人创作</span><span>想象，不设限</span></div>
            </div>
          </div>
          <div class="preview-caption"><div><strong>{{ selected ? selected.movieTitle || '我的电影时刻' : '主角席位，为你预留' }}</strong><p>{{ selected ? `${styleName(selected.style)} · ${selected.ratio}${selected.hasPortrait ? ' · 人物照片参考' : ''}` : '左侧设定你的故事，生成后将在这里展示。' }}</p></div>
            <button v-if="selected" type="button" class="text-button" @click="newCreation">新建创作</button></div>
          <div v-if="imageUrl && selected?.status === 'SUCCEEDED'" class="result-actions"><button class="download-button" @click="download"><MaterialIcon name="download" />下载 PNG 海报</button><button class="secondary-button" @click="share"><MaterialIcon name="share" />分享</button></div>
          <p class="art-note">{{ selected ? 'AI 个性化创作 · 人物还原效果因模型而异 · 仅作个人观影纪念' : '当前画面为风格示意，生成结果将依据你的描述与照片创作。' }}</p>
        </section>
      </div>

      <section class="history-section"><div class="history-heading"><div><p class="eyebrow">MY COLLECTION</p><h2>我的电影宇宙 <span>{{ posters.length.toString().padStart(2, '0') }}</span></h2></div><button class="text-button" :disabled="refreshing" @click="refresh"><MaterialIcon name="refresh" />刷新作品</button></div>
        <p v-if="!posters.length" class="empty-history">你的第一张电影海报，从一个想象开始。</p>
        <div v-else class="history-grid"><button v-for="item in posters" :key="item.id" class="history-card" :class="[{ active: selected?.id === item.id }, item.style.toLowerCase()]" @click="selectPoster(item)">
          <span class="history-icon"><MaterialIcon name="movie" /></span><span class="history-info"><strong>{{ item.movieTitle || '我的电影时刻' }}</strong><span>{{ styleName(item.style) }} · {{ item.ratio }} · {{ formatDate(item.createdAt) }}</span></span><span class="history-status" :class="item.status.toLowerCase()">{{ statusText[item.status] }}</span></button></div>
        <p v-if="posters.length >= 50" class="art-note">展示最近 50 次创作。</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import AppSidebar from '@/components/AppSidebar.vue'
import MaterialIcon from '@/components/MaterialIcon.vue'
import { posterApi, type PersonalPoster, type PosterCapabilities } from '@/api/poster'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const movieTitle = ref(typeof route.query.movie === 'string' ? route.query.movie.slice(0, 80) : '')
const prompt = ref(''), style = ref('CYBERPUNK'), ratio = ref('3:4')
const capabilities = ref<PosterCapabilities | null>(null), posters = ref<PersonalPoster[]>([])
const selected = ref<PersonalPoster | null>(null), submitting = ref(false), refreshing = ref(false)
const loadError = ref(''), submitError = ref(''), imageError = ref('')
const photo = ref<File | null>(null), photoUrl = ref(''), photoInput = ref<HTMLInputElement | null>(null)
const promptLimit = computed(() => photo.value ? 1000 : 700)
const imageUrl = ref(''), imageBlob = ref<Blob | null>(null)
const styles = computed(() => capabilities.value?.styles || [
  { id: 'CYBERPUNK', name: '赛博朋克' }, { id: 'FANTASY', name: '东方神话' }, { id: 'SCIFI', name: '星际科幻' },
  { id: 'ANIME', name: '日系动画' }, { id: 'RETRO', name: '复古胶片' }, { id: 'SUSPENSE', name: '悬疑电影' }
])
const ratios = [{ value: '3:4', label: '竖版 3:4' }, { value: '1:1', label: '方形 1:1' }, { value: '16:9', label: '横版 16:9' }]
const statusText = { QUEUED: '等待创作', RUNNING: '创作中', SUCCEEDED: '已完成', FAILED: '未完成' }
const busy = computed(() => selected.value && ['QUEUED', 'RUNNING'].includes(selected.value.status))
const styleName = (id: string) => styles.value.find(s => s.id === id)?.name || id
const formatDate = (value: string) => value.slice(5, 10).replace('-', '/')
const errorMessage = (e: any, fallback: string) => e?.response?.data?.message || e?.message || fallback
let timer: number | undefined, disposed = false, imageController: AbortController | undefined

function clearPhoto() {
  if (photoUrl.value) URL.revokeObjectURL(photoUrl.value)
  photo.value = null; photoUrl.value = ''
  if (photoInput.value) photoInput.value.value = ''
}
function choosePhoto(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 1.4 * 1024 * 1024 || file.size === 0) {
    ElMessage.warning('请选择不超过 1.4MB 的 JPG 或 PNG 照片'); input.value = ''; return
  }
  clearPhoto(); photo.value = file; photoUrl.value = URL.createObjectURL(file)
}
function useExample() { movieTitle.value = '哪吒'; style.value = 'CYBERPUNK'; prompt.value = '帮我生成一张哪吒电影的赛博朋克风格观影海报，我是主角。霓虹城市，风火轮变成悬浮装置，红色能量丝带划过夜空。' }
function clearImage() {
  imageController?.abort()
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''; imageBlob.value = null; imageError.value = ''
}
async function loadImage() {
  if (!selected.value || selected.value.status !== 'SUCCEEDED') return
  const id = selected.value.id
  clearImage(); const controller = new AbortController(); imageController = controller
  try {
    const blob = await posterApi.image(id, controller.signal)
    if (disposed || selected.value?.id !== id || controller.signal.aborted) return
    imageBlob.value = blob; imageUrl.value = URL.createObjectURL(blob)
  } catch (e) { if (!controller.signal.aborted && !disposed) imageError.value = errorMessage(e, '海报加载失败') }
}
function selectPoster(item: PersonalPoster) {
  clearImage(); selected.value = item
  if (item.status === 'SUCCEEDED') void loadImage()
}
function schedulePoll() {
  window.clearTimeout(timer)
  if (!disposed && (busy.value || posters.value.some(p => ['QUEUED', 'RUNNING'].includes(p.status)))) timer = window.setTimeout(() => { if (document.visibilityState === 'visible') void refresh(true); else schedulePoll() }, 8000)
}
async function refresh(polling?: unknown) {
  if (refreshing.value || disposed) return
  refreshing.value = true
  try {
    const [caps, list] = await Promise.all([polling === true ? Promise.resolve(capabilities.value) : posterApi.capabilities(), posterApi.list()])
    if (disposed) return
    capabilities.value = caps; posters.value = list; loadError.value = ''
    if (selected.value) {
      const updated = list.find(p => p.id === selected.value?.id)
      if (updated) {
        const completed = selected.value.status !== 'SUCCEEDED' && updated.status === 'SUCCEEDED'
        selected.value = updated
        if (completed) void loadImage()
      }
    }
  } catch (e) { if (!disposed) loadError.value = errorMessage(e, '创作服务连接失败，请重新加载') }
  finally { refreshing.value = false; schedulePoll() }
}
async function requestId(input: { movieTitle: string; style: string; prompt: string; ratio: string }, reference: File | null) {
  const photoHash = reference ? Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', await reference.arrayBuffer())), b => b.toString(16).padStart(2, '0')).join('') : ''
  const bytes = new TextEncoder().encode(JSON.stringify([input.movieTitle, input.style, input.prompt, input.ratio, photoHash]))
  const signature = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join('')
  const key = `personal-poster-request:${userStore.userInfo?.id}`
  let previous: { signature?: string; id?: string } = {}
  try { previous = JSON.parse(sessionStorage.getItem(key) || '{}') } catch { /* Invalid local state starts a fresh request. */ }
  if (previous.signature === signature && previous.id) return previous.id
  const id = crypto.randomUUID()
  sessionStorage.setItem(key, JSON.stringify({ signature, id })); return id
}
async function generate() {
  if (submitting.value || !capabilities.value?.available) return
  submitting.value = true; submitError.value = ''
  // Freeze the form before asynchronous file hashing so the request ID matches its content.
  const input = { movieTitle: movieTitle.value.trim(), style: style.value, prompt: prompt.value.trim(), ratio: ratio.value }
  const reference = photo.value
  try {
    const id = await requestId(input, reference)
    const result = await posterApi.create({ ...input, requestId: id }, reference)
    selectPoster(result); await refresh()
  } catch (e) { submitError.value = errorMessage(e, '创意提交失败，请稍后重试') }
  finally { submitting.value = false }
}
async function retry() {
  if (!selected.value || submitting.value) return
  submitting.value = true; submitError.value = ''
  try { selectPoster(await posterApi.retry(selected.value.id)); await refresh() }
  catch (e) { submitError.value = errorMessage(e, '重试暂未完成') }
  finally { submitting.value = false }
}
function newCreation() {
  clearImage(); selected.value = null; submitError.value = ''
  sessionStorage.removeItem(`personal-poster-request:${userStore.userInfo?.id}`)
}
function filename() { return `${(selected.value?.movieTitle || '我的电影').replace(/[\\/:*?"<>|]/g, '-')}-专属海报.png` }
function download() {
  if (!imageUrl.value) return
  const a = document.createElement('a'); a.href = imageUrl.value; a.download = filename(); a.click()
}
async function share() {
  if (!imageBlob.value) return
  const file = new File([imageBlob.value], filename(), { type: 'image/png' })
  try {
    if (navigator.canShare?.({ files: [file] })) await navigator.share({ title: '我的专属电影海报', files: [file] })
    else { download(); ElMessage.success('海报已下载，可以发送给朋友分享') }
  } catch (e: any) { if (e?.name !== 'AbortError') ElMessage.error('分享未完成，可以下载海报后分享') }
}
watch(() => route.query.movie, value => { if (typeof value === 'string') movieTitle.value = value.slice(0, 80) })
onMounted(async () => {
  await refresh()
  if (disposed || movieTitle.value) return
  const latest = posters.value[0]
  if (latest) selectPoster(latest)
})
onBeforeUnmount(() => { disposed = true; window.clearTimeout(timer); clearPhoto(); clearImage() })
</script>

<style scoped>
.style-card input[type="radio"], .ratio-options input[type="radio"] {
  inset: 0; width: 100%; height: 100%; margin: 0; z-index: 2; cursor: pointer;
}
.poster-app{display:flex;min-height:100vh;background:#f2f3f0;color:#20211f;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif}.studio-main{flex:1;min-width:0;padding:0 40px 48px;max-width:1540px;margin:0 auto}.studio-header{height:76px;border-bottom:1px solid #dbddd6;display:flex;align-items:center;justify-content:space-between}.back-link{display:flex;gap:8px;align-items:center;color:#62665e;text-decoration:none;font-size:13px}.header-label{font-size:11px;letter-spacing:2px;color:#84887e}.studio-intro{display:flex;justify-content:space-between;align-items:center;padding:38px 0 30px}.eyebrow{font-size:10px;letter-spacing:2px;font-weight:700;color:#868779}.studio-intro h1{font-size:clamp(26px,2.6vw,40px);letter-spacing:-1px;margin:10px 0 12px;line-height:1.35}.studio-intro h1>span{color:#ee7d19}.intro-copy{font-size:13px;color:#777c70;line-height:1.8}.edition{display:flex;align-items:center;gap:12px;padding:15px 20px;border-left:1px solid #d7dacd;color:#939889;font-size:11px;letter-spacing:2px;line-height:1.8}.edition>svg{font-size:32px;color:#d1843d}.edition strong{font-size:14px;color:#3f443b}.workspace{display:grid;grid-template-columns:minmax(340px,1fr) minmax(300px,.95fr);gap:26px;align-items:start}.creation-panel{background:#fff;border:1px solid #dfe2d8;padding:26px;border-radius:12px}.panel-heading,.history-heading{display:flex;align-items:center;justify-content:space-between}.panel-heading{padding-bottom:22px;border-bottom:1px solid #eceee8;margin-bottom:23px}.panel-heading h2{font-size:18px}.panel-heading>span{font-size:10px;color:#989d90;letter-spacing:1px}.form-section{margin-bottom:23px}.field-label{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:650;margin-bottom:11px}.field-label>label{display:flex;align-items:center;gap:7px}.step{font-family:ui-monospace,monospace;font-size:10px;color:#acb09f;font-weight:400;margin-right:3px}.optional{font-size:10px;font-weight:400;color:#929688;margin-left:auto}input[type=text],input:not([type]),textarea{width:100%;padding:12px 13px;border:1px solid #dfe3d8;background:#fafbf8;border-radius:6px;font:inherit;font-size:12px;color:#2c3027;outline:none;line-height:1.7}input:focus,textarea:focus{border-color:#ed963e;box-shadow:0 0 0 2px #ff840014}textarea{resize:vertical;min-height:100px}fieldset{border:0;min-width:0;padding:0}.style-fieldset legend,.ratio-fieldset legend{padding:0}.style-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.style-card{cursor:pointer;border:1px solid #e2e5dc;border-radius:6px;padding:5px;background:#fff;position:relative;min-width:0}.style-card.selected{border-color:#ee8a2d;box-shadow:0 0 0 1px #ee8a2d;background:#fff9f2}.style-card input,.ratio-options input{position:absolute;opacity:0;width:1px;height:1px}.style-card:has(input:focus-visible),.ratio-options label:has(input:focus-visible){outline:2px solid #e87a18;outline-offset:3px}.style-art{height:54px;display:block;overflow:hidden;position:relative;border-radius:3px;isolation:isolate}.style-name{display:block;text-align:center;font-size:11px;padding:8px 0 4px}.cyberpunk{--art-accent:#f45283;--art-dark:#171e3b;background:linear-gradient(145deg,#193b5d,#332047 58%,#dc6166)}.fantasy{--art-accent:#f8d69c;--art-dark:#762f25;background:linear-gradient(145deg,#d69c60,#7a2924 60%,#352f30)}.scifi{--art-accent:#dcd9ff;--art-dark:#252d56;background:linear-gradient(135deg,#101e45,#554980 67%,#98aabe)}.anime{--art-accent:#fff1d1;--art-dark:#489392;background:linear-gradient(155deg,#94bac5,#b7dcd0 48%,#eab49a)}.retro{--art-accent:#ead49e;--art-dark:#4b5a47;background:linear-gradient(145deg,#9a975e,#bac5a2 50%,#5c7467)}.suspense{--art-accent:#dedac5;--art-dark:#152521;background:linear-gradient(145deg,#111b1e,#657477 60%,#b3a487)}.art-orbit{position:absolute;width:52px;height:52px;border-radius:50%;background:var(--art-accent);opacity:.7;top:-8px;left:55%;box-shadow:0 0 25px var(--art-accent)}.art-horizon{position:absolute;inset:45% -20% -10%;background:var(--art-dark);transform:rotate(-15deg);border-top:1px solid #ffffff4d;box-shadow:0 -8px 0 #ffffff0c}.style-check{position:absolute;right:3px;top:3px;width:16px;height:16px;border-radius:50%;background:#ff930f;color:white;text-align:center;font-size:11px}.upload-box{position:relative;display:flex;align-items:center;gap:13px;padding:16px;border:1px dashed #ccd3c0;border-radius:6px;background:#fafbf7;min-height:82px;overflow:hidden}.upload-box>svg{font-size:25px;color:#9ba48d;flex-shrink:0}.upload-box strong{display:block;font-size:12px;font-weight:500;word-break:break-all}.upload-box p{font-size:10px;color:#979d8d;margin-top:6px;line-height:1.5}.upload-box input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%;z-index:1}.upload-box.disabled{opacity:.7}.upload-box.disabled input{cursor:not-allowed}.upload-box>img{width:46px;height:54px;object-fit:cover;border-radius:3px;flex-shrink:0}.upload-box.has-photo{padding-right:44px}.remove-photo{position:absolute;right:10px;top:10px;z-index:2;border:0;border-radius:50%;padding:5px;background:#e9ecdf;cursor:pointer}.privacy-note{font-size:10px;line-height:1.7;color:#939989;margin-top:8px}.prompt-footer{display:flex;justify-content:space-between;align-items:center;margin-top:6px;font-size:10px;color:#a3a798}.prompt-footer button,.text-button{border:0;background:none;cursor:pointer;color:#8b7655;font:inherit;font-size:11px;display:inline-flex;gap:5px;align-items:center}.ratio-options{display:flex;gap:8px;margin:0 0 22px}.ratio-options label{display:flex;gap:7px;align-items:center;justify-content:center;flex:1;border:1px solid #e0e4d8;border-radius:5px;padding:11px 5px;font-size:11px;cursor:pointer;position:relative}.ratio-options label.selected{background:#fff6eb;border-color:#e6a258;color:#a65a13}.ratio-icon{display:inline-block;border:1px solid currentColor;height:14px;max-width:24px}.generate-button{display:flex;align-items:center;justify-content:center;gap:10px;background:#ff890c;border:1px solid #e57d0d;border-radius:6px;color:#241b0c;font:inherit;font-size:13px;font-weight:700;padding:15px;width:100%;cursor:pointer;box-shadow:0 3px 0 #ba620f14}.generate-button>svg{font-size:19px}.generate-button>span{margin-left:auto;font-size:18px}.generate-button:disabled{opacity:.45;cursor:not-allowed}.quota-note{font-size:10px;color:#989e8c;margin-top:11px;display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap}.preview-panel{padding:23px 24px 22px;background:#e8eae3;border:1px solid #dce0d3;border-radius:12px}.preview-heading{display:flex;justify-content:space-between;gap:8px;align-items:center;margin-bottom:19px}.preview-heading .eyebrow{font-size:9px;letter-spacing:1.6px}.preview-tag{font-size:9px;border:1px solid #cbd0bf;border-radius:3px;padding:4px 6px;color:#7e8773;white-space:nowrap}.preview-stage{min-height:440px;display:flex;align-items:center;justify-content:center;background:#dbdfd1;border-radius:6px;padding:22px;overflow:hidden}.inspiration-poster{position:relative;width:min(100%,350px);aspect-ratio:3/4;overflow:hidden;color:#fff6dc;box-shadow:0 15px 35px #1d26182e}.poster-grain{position:absolute;inset:0;opacity:.08;background-image:url("data:image/svg+xml,%3Csvg viewBox='0 0 120 120' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.7' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.8'/%3E%3C/svg%3E");z-index:5;pointer-events:none}.poster-sun{position:absolute;top:13%;left:26%;width:54%;aspect-ratio:1;border-radius:50%;background:radial-gradient(circle,#ffe5b9,var(--art-accent) 40%,transparent 72%);opacity:.65}.poster-ring{position:absolute;top:12%;left:12%;width:77%;aspect-ratio:1;border:1px solid #ffe5bc50;border-radius:50%;transform:rotate(-28deg) scaleY(.64);box-shadow:0 0 0 13px #ffe4d611,0 0 0 35px #ffe4d608}.poster-city{position:absolute;inset:37% -10% 0;background:repeating-linear-gradient(90deg,transparent 0 12%,#11253980 12% 17%,transparent 17% 20%),linear-gradient(0deg,var(--art-dark),transparent);transform:perspective(180px) rotateX(12deg)}.poster-topline,.poster-bottomline{position:absolute;left:9%;right:9%;display:flex;justify-content:space-between;font-size:7px;letter-spacing:1.4px;z-index:6}.poster-topline{top:7%;border-top:1px solid #eedec159;padding-top:9px}.poster-bottomline{bottom:6%;border-top:1px solid #eedec140;padding-top:10px;font-size:7px}.poster-figure{position:absolute;left:44%;bottom:25%;width:15%;height:30%;background:linear-gradient(90deg,#101c30,#23334a);clip-path:polygon(34% 0,63% 0,70% 8%,66% 16%,79% 21%,100% 65%,76% 67%,67% 45%,70% 100%,46% 100%,46% 69%,37% 100%,15% 100%,27% 47%,10% 65%,0 62%,21% 20%,30% 16%,26% 7%);filter:drop-shadow(0 0 12px #f07661)}.poster-title{position:absolute;left:9%;right:6%;bottom:13%;z-index:4;text-shadow:0 2px 20px #12203070}.poster-title p{font-size:9px;letter-spacing:2px;margin-bottom:10px;opacity:.85}.poster-title h2{font-weight:850;font-size:clamp(28px,3.4vw,54px);line-height:1.04;letter-spacing:2px}.poster-title h2 span{font-size:.63em;font-weight:350;letter-spacing:7px}.poster-title>div{font-family:ui-monospace,monospace;font-size:6px;letter-spacing:1.5px;margin-top:14px}.result-image{display:block;max-width:100%;max-height:560px;object-fit:contain;box-shadow:0 12px 32px #1d261829}.preview-caption{display:flex;justify-content:space-between;gap:8px;margin-top:20px}.preview-caption strong{font-size:14px}.preview-caption p{font-size:11px;color:#858c7a;margin-top:8px;line-height:1.7}.art-note{font-size:10px;color:#929986;text-align:center;line-height:1.7;margin-top:20px}.result-actions{display:flex;gap:9px;margin-top:18px}.download-button,.secondary-button{display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid #bfc7b2;border-radius:5px;padding:11px 14px;font:inherit;font-size:12px;cursor:pointer;background:#f5f7ef;color:#3e4c32}.download-button{background:#2d3829;color:#fff;border-color:#2d3829;flex:1}.secondary-button:disabled{opacity:.5;cursor:not-allowed}.task-state{text-align:center;display:flex;align-items:center;flex-direction:column;gap:18px;padding:36px 5px;max-width:310px}.task-state h2{font-size:20px}.task-state>p:not(.eyebrow){font-size:12px;line-height:1.9;color:#747e69;overflow-wrap:anywhere}.task-symbol{border:1px solid #b9c5a9;border-radius:50%;padding:23px;color:#80946c;font-size:32px;display:flex}.task-symbol.is-generating{animation:breathe 2.5s ease-in-out infinite}.progress-track{height:3px;background:#c4cdb9;border-radius:3px;width:180px;overflow:hidden}.progress-track span{height:100%;width:45%;display:block;background:#e38432;animation:travel 2s ease-in-out infinite}.history-section{margin-top:40px}.history-heading h2{font-size:20px;margin-top:9px}.history-heading h2>span{font-size:13px;font-weight:400;color:#a1a694;margin-left:10px}.history-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:20px}.history-card{border:1px solid #dce1d3;border-radius:7px;display:flex;align-items:center;gap:12px;padding:14px;text-align:left;cursor:pointer;min-width:0;background:#fbfcf8;font:inherit}.history-card.active{border-color:#d28a39;background:#fff9ed}.history-icon{border-radius:4px;background:var(--art-dark);color:var(--art-accent);padding:12px;font-size:22px;display:flex}.history-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:8px}.history-info strong{font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-info>span{font-size:10px;color:#929a84}.history-status{font-size:10px;color:#87947b;white-space:nowrap}.history-status.running{color:#b8792d}.history-status.failed{color:#b96b57}.history-status.succeeded{color:#568d61}.empty-history{font-size:12px;color:#9bA28f;padding:36px;text-align:center;border:1px dashed #d2d9c6;border-radius:7px;margin-top:20px}.notice{border:1px solid #e3d1b0;background:#fff4df;border-radius:6px;padding:13px 16px;font-size:12px;color:#9c7333;line-height:1.7;margin-bottom:20px}.notice.error,.inline-error{color:#a75d47}.notice button{border:0;background:none;color:inherit;text-decoration:underline;cursor:pointer;margin-left:10px}.inline-error{font-size:12px;line-height:1.7;margin-bottom:12px}.spinning{animation:spin 1s linear infinite}button:focus-visible,a:focus-visible{outline:2px solid #d58029;outline-offset:3px}@keyframes breathe{50%{box-shadow:0 0 0 14px #93a97b16;transform:scale(1.04)}}@keyframes travel{0%{transform:translateX(-100%)}100%{transform:translateX(330%)}}@keyframes spin{to{transform:rotate(360deg)}}@media(min-width:1600px){.studio-main{padding:0 60px 48px}.workspace{gap:36px}.creation-panel{padding:30px}.preview-stage{min-height:500px}}@media(max-width:1200px){.studio-main{padding:0 24px 36px}.workspace{gap:16px;grid-template-columns:minmax(300px,1fr) minmax(250px,.9fr)}.creation-panel,.preview-panel{padding:20px}.edition{display:none}.preview-stage{padding:16px;min-height:360px}.preview-heading .eyebrow{letter-spacing:.6px}}@media(max-width:1000px){.poster-app :deep(.sidebar){display:none}.studio-main{max-width:1000px}}@media(max-width:700px){.studio-main{padding:0 16px 32px}.studio-header{height:58px}.header-label{letter-spacing:0;font-size:10px}.studio-intro{padding:26px 0}.studio-intro h1{font-size:27px}.intro-copy{font-size:12px}.workspace{grid-template-columns:minmax(0,1fr)}.creation-panel{padding:21px}.preview-panel{padding:20px}.preview-stage{min-height:350px}.poster-title h2{font-size:48px}.history-grid{grid-template-columns:1fr}.style-art{height:60px}.history-section{margin-top:30px}}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}}
</style>
