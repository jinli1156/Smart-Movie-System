<template>
  <div class="movie-assistant" :class="{ open }">
    <transition name="assistant-panel">
      <section v-if="open" class="assistant-panel" aria-label="智能电影小助手">
        <header class="assistant-header">
          <div class="assistant-identity">
            <span class="assistant-avatar"><MaterialIcon class="msr" name="smart_toy" /></span>
            <div>
              <strong>智能电影小助手</strong>
              <span><i></i>{{ sourceLabel }}</span>
            </div>
          </div>
          <div class="assistant-actions">
            <button title="清空对话" @click="resetChat"><MaterialIcon class="msr" name="restart_alt" /></button>
            <button title="关闭" @click="open = false"><MaterialIcon class="msr" name="close" /></button>
          </div>
        </header>

        <div ref="messageList" class="assistant-messages">
          <div v-for="(item, index) in messages" :key="index" class="message-row" :class="item.role">
            <span v-if="item.role === 'assistant'" class="message-avatar"><MaterialIcon class="msr" name="smart_toy" /></span>
            <div class="message-bubble">{{ item.content }}</div>
          </div>
          <div v-if="loading" class="message-row assistant">
            <span class="message-avatar"><MaterialIcon class="msr" name="smart_toy" /></span>
            <div class="message-bubble typing"><i></i><i></i><i></i></div>
          </div>
        </div>

        <div v-if="suggestions.length" class="assistant-suggestions">
          <button v-for="suggestion in suggestions" :key="suggestion" @click="send(suggestion)">{{ suggestion }}</button>
        </div>

        <footer class="assistant-composer">
          <textarea
            v-model="draft"
            maxlength="500"
            rows="1"
            placeholder="问我电影、导演、演员、IMAX…"
            @keydown.enter.exact.prevent="send()"
          ></textarea>
          <button class="send-button" :disabled="loading || !draft.trim()" @click="send()">
            <MaterialIcon class="msr" name="send" />
          </button>
          <p>Enter 发送 · Shift + Enter 换行</p>
        </footer>
      </section>
    </transition>

    <button class="assistant-fab" :aria-label="open ? '关闭智能电影小助手' : '打开智能电影小助手'" @click="open = !open">
      <MaterialIcon v-if="!open" class="msr" name="smart_toy" />
      <MaterialIcon v-else class="msr" name="close" />
      <span v-if="!open">电影小助手</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { assistantApi, type AssistantHistoryMessage } from '@/api/assistant'

const WELCOME = '你好，我是 CINEMA+ 智能电影小助手。可以帮你查询热映影片、推荐电影，也可以回答导演、演员、类型和影院制式等电影知识。'
const STORAGE_KEY = 'cinema-assistant-history'

const open = ref(false)
const draft = ref('')
const loading = ref(false)
const source = ref<'model' | 'local'>('local')
const messageList = ref<HTMLElement | null>(null)
const messages = ref<AssistantHistoryMessage[]>([{ role: 'assistant', content: WELCOME }])
const suggestions = ref(['现在有什么电影？', '推荐几部高分电影', 'IMAX 和杜比有什么区别？'])

const sourceLabel = computed(() => source.value === 'model' ? 'Qwen3.8-Flash 在线' : '本地电影知识库')

const scrollToBottom = async () => {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

const localFallback = (question: string) => {
  if (/imax/i.test(question) && /杜比|dolby/i.test(question)) {
    return 'IMAX 更强调大银幕、画面规模与沉浸感；杜比影院更突出高对比度画面和全景声定位。视觉奇观可优先 IMAX，重视暗场和声音细节可选择杜比影院。'
  }
  return '小助手暂时没有连接到后端知识服务。你仍可以浏览“影片”和“热映榜单”，稍后再向我提问。'
}

const send = async (preset?: string) => {
  const question = (preset ?? draft.value).trim()
  if (!question || loading.value) return
  const history = messages.value.slice(-10)
  messages.value.push({ role: 'user', content: question })
  draft.value = ''
  suggestions.value = []
  loading.value = true
  await scrollToBottom()
  try {
    const result = await assistantApi.chat(question, history)
    messages.value.push({ role: 'assistant', content: result.answer })
    suggestions.value = result.suggestions || []
    source.value = result.source
  } catch {
    messages.value.push({ role: 'assistant', content: localFallback(question) })
    suggestions.value = ['现在有什么电影？', '打开热映榜单', 'IMAX 和杜比有什么区别？']
    source.value = 'local'
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const resetChat = () => {
  messages.value = [{ role: 'assistant', content: WELCOME }]
  suggestions.value = ['现在有什么电影？', '推荐几部高分电影', 'IMAX 和杜比有什么区别？']
  sessionStorage.removeItem(STORAGE_KEY)
}

const openAssistant = (event: Event) => {
  open.value = true
  const question = (event as CustomEvent<string>).detail
  if (question) void send(question)
}

watch(messages, value => {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value.slice(-20)))
}, { deep: true })

watch(open, value => {
  if (value) scrollToBottom()
})

onMounted(() => {
  window.addEventListener('open-movie-assistant', openAssistant)
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]')
    if (Array.isArray(saved) && saved.length) messages.value = saved.slice(-20)
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
  }
})

onBeforeUnmount(() => window.removeEventListener('open-movie-assistant', openAssistant))
</script>

<style scoped>
.movie-assistant { position: fixed; right: 26px; bottom: 24px; z-index: 3000; font-family: 'Geist','PingFang SC','Microsoft YaHei',sans-serif; }
.assistant-fab { min-width: 58px; height: 56px; padding: 0 18px; display:flex; align-items:center; justify-content:center; gap:9px; border:0; border-radius:18px; color:#111; background:linear-gradient(135deg,#ff9a31,#ff8400); box-shadow:0 12px 30px rgba(255,132,0,.32),0 4px 10px rgba(0,0,0,.12); font-size:14px; font-weight:800; cursor:pointer; transition:.2s ease; }
.assistant-fab:hover { transform:translateY(-2px); box-shadow:0 16px 34px rgba(255,132,0,.38),0 5px 12px rgba(0,0,0,.14); }
.assistant-fab .msr { font-size:24px; }
.open .assistant-fab { min-width:56px; width:56px; padding:0; border-radius:50%; }
.assistant-panel { position:absolute; right:0; bottom:70px; width:min(390px,calc(100vw - 32px)); height:min(590px,calc(100vh - 120px)); display:flex; flex-direction:column; overflow:hidden; border:1px solid rgba(255,255,255,.72); border-radius:24px; background:#f4f5f2; box-shadow:0 26px 70px rgba(28,31,29,.22),inset 1px 1px 0 #fff; }
.assistant-header { padding:17px 18px; display:flex; align-items:center; justify-content:space-between; background:linear-gradient(145deg,#272c29,#151816); color:#fff; }
.assistant-identity { display:flex; align-items:center; gap:11px; }
.assistant-avatar,.message-avatar { display:grid; place-items:center; flex:0 0 auto; color:#111; background:#ff8b0a; }
.assistant-avatar { width:40px; height:40px; border-radius:13px; box-shadow:0 6px 16px rgba(255,132,0,.3); }
.assistant-avatar .msr { font-size:23px; }
.assistant-identity div { display:flex; flex-direction:column; gap:4px; }
.assistant-identity strong { font-size:15px; }
.assistant-identity span { display:flex; align-items:center; gap:6px; color:#aeb6b1; font-size:10px; }
.assistant-identity i { width:6px; height:6px; border-radius:50%; background:#35d07f; box-shadow:0 0 8px #35d07f; }
.assistant-actions { display:flex; gap:5px; }
.assistant-actions button { width:34px; height:34px; display:grid; place-items:center; border:0; border-radius:10px; color:#cbd1cd; background:rgba(255,255,255,.08); cursor:pointer; }
.assistant-actions button:hover { color:#fff; background:rgba(255,255,255,.15); }
.assistant-actions .msr { font-size:18px; }
.assistant-messages { min-height:0; flex:1; padding:18px 15px; overflow-y:auto; scroll-behavior:smooth; }
.message-row { margin-bottom:14px; display:flex; align-items:flex-end; gap:8px; }
.message-row.user { justify-content:flex-end; }
.message-avatar { width:28px; height:28px; border-radius:9px; }
.message-avatar .msr { font-size:16px; }
.message-bubble { max-width:82%; padding:11px 13px; border-radius:15px 15px 15px 4px; color:#373d39; background:#fff; box-shadow:3px 3px 9px rgba(184,188,182,.45),-3px -3px 8px rgba(255,255,255,.9); font-size:13px; line-height:1.65; white-space:pre-wrap; }
.user .message-bubble { border-radius:15px 15px 4px 15px; color:#111; background:#ff8b0a; box-shadow:0 6px 14px rgba(255,132,0,.2); }
.typing { display:flex; gap:4px; padding:15px; }
.typing i { width:5px; height:5px; border-radius:50%; background:#9ba29d; animation:typing 1s infinite alternate; }
.typing i:nth-child(2) { animation-delay:.18s; }.typing i:nth-child(3) { animation-delay:.36s; }
.assistant-suggestions { padding:0 14px 10px; display:flex; gap:7px; overflow-x:auto; }
.assistant-suggestions button { flex:0 0 auto; padding:7px 10px; border:1px solid #d7d9d5; border-radius:999px; color:#6d746f; background:#fff; font-size:11px; cursor:pointer; }
.assistant-suggestions button:hover { border-color:#ff8b0a; color:#d56e00; }
.assistant-composer { position:relative; padding:12px 14px 23px; border-top:1px solid #dedfdb; background:#eff0ed; }
.assistant-composer textarea { width:100%; min-height:48px; max-height:96px; padding:13px 48px 11px 13px; resize:none; outline:none; border:1px solid #d5d7d3; border-radius:15px; color:#252a27; background:#fff; font:inherit; font-size:13px; box-shadow:inset 2px 2px 5px rgba(197,200,194,.28); }
.assistant-composer textarea:focus { border-color:#ff8b0a; }
.send-button { position:absolute; right:21px; top:20px; width:34px; height:34px; display:grid; place-items:center; border:0; border-radius:11px; color:#111; background:#ff8b0a; cursor:pointer; }
.send-button:disabled { opacity:.4; cursor:not-allowed; }
.send-button .msr { font-size:18px; }
.assistant-composer p { position:absolute; left:17px; bottom:6px; margin:0; color:#a3a8a4; font-size:9px; }
.assistant-panel-enter-active,.assistant-panel-leave-active { transition:.2s ease; transform-origin:bottom right; }
.assistant-panel-enter-from,.assistant-panel-leave-to { opacity:0; transform:translateY(12px) scale(.96); }
@keyframes typing { to { transform:translateY(-4px); background:#ff8b0a; } }
@media (max-width:600px) { .movie-assistant{right:14px;bottom:14px}.assistant-panel{position:fixed;right:10px;bottom:80px;width:calc(100vw - 20px);height:min(620px,calc(100vh - 96px))}.assistant-fab span{display:none}.assistant-fab{min-width:56px;width:56px;padding:0;border-radius:50%} }
</style>
