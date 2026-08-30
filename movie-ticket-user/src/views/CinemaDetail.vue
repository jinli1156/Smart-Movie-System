<template>
  <div class="app">
    <AppSidebar :show-quick-access="false" />

    <main class="main-area">
      <header class="top-bar">
        <button class="back-btn" @click="router.push('/cinemas')">
          <MaterialIcon class="msr" name="arrow_back" />
          返回影院列表
        </button>
        <div class="breadcrumb">
          <span @click="router.push('/home')">首页</span>
          <i>/</i>
          <span @click="router.push('/cinemas')">影院</span>
          <i>/</i>
          <strong>{{ cinema?.name || '影院详情' }}</strong>
        </div>
      </header>

      <div v-loading="loading" class="content">
        <section v-if="cinema" class="cinema-hero">
          <div class="hero-mark"><MaterialIcon class="msr" name="theaters" /></div>
          <div class="hero-main">
            <div class="eyebrow">CINEMA PROFILE</div>
            <h1>{{ cinema.name }}</h1>
            <p><MaterialIcon class="msr" name="location_on" />{{ cinema.address }}</p>
            <div class="service-tags">
              <span>可退票</span><span>可改签</span><span>免费停车</span><span>激光放映</span>
            </div>
          </div>
          <div class="hero-side">
            <div><strong>9.2</strong><span>影院评分</span></div>
            <i></i>
            <div><strong>{{ movies.length }}</strong><span>可选影片</span></div>
          </div>
        </section>

        <section class="movie-picker">
          <div class="section-head">
            <div>
              <div class="eyebrow orange">NOW SHOWING</div>
              <h2>选择电影</h2>
              <p>选择影片后，下方会展示该影院的可售场次</p>
            </div>
            <div class="picker-actions">
              <button aria-label="向左浏览电影" @click="scrollMovies(-1)"><MaterialIcon class="msr" name="chevron_left" /></button>
              <button aria-label="向右浏览电影" @click="scrollMovies(1)"><MaterialIcon class="msr" name="chevron_right" /></button>
            </div>
          </div>

          <div ref="movieRailRef" class="movie-rail">
            <button
              v-for="movie in movies"
              :key="movie.id"
              class="movie-card"
              :class="{ active: movie.id === selectedMovieId }"
              :aria-pressed="movie.id === selectedMovieId"
              @click="selectMovie(movie)"
            >
              <div class="poster">
                <img v-if="movie.poster" :src="movie.poster" :alt="movie.title" />
                <MaterialIcon v-else class="msr" name="movie" />
                <span v-if="showtimeCount(movie.id)" class="session-count">{{ showtimeCount(movie.id) }} 场</span>
              </div>
              <strong>{{ movie.title }}</strong>
              <span>{{ movie.genre || '精彩影片' }}</span>
            </button>
          </div>
          <el-empty v-if="!loading && movies.length === 0" description="该影院暂无可选影片" />
        </section>

        <template v-if="selectedMovie">
          <section class="movie-summary">
            <div class="summary-poster">
              <img v-if="selectedMovie.poster" :src="selectedMovie.poster" :alt="selectedMovie.title" />
              <MaterialIcon v-else class="msr" name="movie" />
            </div>
            <div class="summary-body">
              <div class="title-row">
                <h2>{{ selectedMovie.title }}</h2>
                <span class="rating">{{ selectedMovie.rating?.toFixed(1) || '8.5' }}<small>分</small></span>
              </div>
              <p class="english">{{ selectedMovie.englishTitle || 'CINEMA+ FEATURE' }}</p>
              <div class="movie-meta">
                <span><b>时长</b>{{ selectedMovie.duration }} 分钟</span>
                <span><b>类型</b>{{ selectedMovie.genre || '剧情' }}</span>
                <span><b>主演</b>{{ selectedMovie.actors || '演员阵容' }}</span>
              </div>
            </div>
            <button class="detail-btn" @click="router.push(`/movie/${selectedMovie.id}`)">
              查看影片详情 <MaterialIcon class="msr" name="arrow_forward" />
            </button>
          </section>

          <section class="showtime-panel">
            <div class="showtime-head">
              <div>
                <div class="eyebrow orange">SHOWTIMES</div>
                <h2>可选场次</h2>
              </div>
              <span class="showtime-total">
                {{ selectedShowtimesAll.length > selectedShowtimes.length ? `显示最近 ${selectedShowtimes.length} 场 · 共 ${selectedShowtimesAll.length} 场` : `共 ${selectedShowtimes.length} 场` }}
              </span>
            </div>

            <div class="date-selector" aria-label="选择观影日期">
              <button
                v-for="date in dateOptions"
                :key="date.value"
                :class="{ active: selectedDate === date.value }"
                :aria-pressed="selectedDate === date.value"
                @click="selectDate(date.value)"
              >
                <span>{{ date.label }}</span>
                <strong>{{ date.display }}</strong>
                <small>{{ date.week }}</small>
              </button>
            </div>

            <div v-if="selectedShowtimes.length" class="showtime-table">
              <div class="table-head">
                <span>放映时间</span><span>语言版本</span><span>放映厅</span><span>剩余座位</span><span>售价</span><span></span>
              </div>
              <div v-for="showtime in selectedShowtimes" :key="showtime.id" class="showtime-row">
                <div class="time-cell">
                  <strong>{{ formatTime(showtime.startTime) }}</strong>
                  <span>{{ formatDate(showtime.startTime) }} · {{ formatEndTime(showtime) }} 散场</span>
                </div>
                <span>国语 2D</span>
                <span>{{ showtime.hallName || '标准厅' }}</span>
                <span>{{ showtime.availableSeats ?? 0 }} / {{ showtime.totalSeats ?? 0 }}</span>
                <div class="price"><small>¥</small>{{ Number(showtime.basePrice).toFixed(0) }}</div>
                <button class="buy-btn" @click="goToSeats(showtime)">选座购票</button>
              </div>
            </div>
            <el-empty v-else description="该影片暂无可售场次，请选择其他电影" />
          </section>
        </template>

        <footer>© 2026 CINEMA+ · 智能电影购票系统</footer>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppSidebar from '@/components/AppSidebar.vue'
import { cinemaApi } from '@/api/cinema'
import { movieApi } from '@/api/movie'
import { showtimeApi } from '@/api/showtime'
import { useUserStore } from '@/stores/user'
import type { Cinema, Movie, Showtime } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const cinema = ref<Cinema | null>(null)
const movies = ref<Movie[]>([])
const showtimes = ref<Showtime[]>([])
const selectedMovieId = ref<number | null>(null)
const movieRailRef = ref<HTMLElement | null>(null)

const toDateKey = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const selectedDate = ref(toDateKey(new Date()))
const dateOptions = computed(() => {
  const labels = ['今天', '明天', '后天']
  return labels.map((label, index) => {
    const date = new Date()
    date.setDate(date.getDate() + index)
    return {
      value: toDateKey(date),
      label,
      display: `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`,
      week: date.toLocaleDateString('zh-CN', { weekday: 'short' })
    }
  })
})

const selectedMovie = computed(() => movies.value.find(movie => movie.id === selectedMovieId.value) || null)
const selectedShowtimesAll = computed(() => showtimes.value
  .filter(showtime => showtime.movieId === selectedMovieId.value && toDateKey(new Date(showtime.startTime)) === selectedDate.value)
  .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()))
const selectedShowtimes = computed(() => selectedShowtimesAll.value.slice(0, 12))

const showtimeCount = (movieId: number) => showtimes.value.filter(item => item.movieId === movieId).length

const loadPage = async () => {
  const cinemaId = Number(route.params.id)
  if (!cinemaId) {
    router.replace('/cinemas')
    return
  }
  loading.value = true
  try {
    const [cinemaData, pageData, nowShowing] = await Promise.all([
      cinemaApi.getById(cinemaId),
      showtimeApi.getPage({ page: 1, size: 200, cinemaId, status: 0 }),
      movieApi.getNowShowing(true)
    ])
    cinema.value = cinemaData
    const now = Date.now()
    showtimes.value = (pageData?.records || []).filter(item => new Date(item.endTime || item.startTime).getTime() > now)

    const merged = new Map<number, Movie>()
    ;(nowShowing || []).forEach(movie => merged.set(movie.id, movie))
    const missingIds = Array.from(new Set(showtimes.value.map(item => item.movieId)))
      .filter(id => !merged.has(id))
    const missingMovies = await Promise.all(missingIds.map(id => movieApi.getById(id)))
    missingMovies.forEach(movie => merged.set(movie.id, movie))
    movies.value = Array.from(merged.values()).sort((a, b) => showtimeCount(b.id) - showtimeCount(a.id))

    const requestedMovieId = Number(route.query.movieId)
    selectedMovieId.value = movies.value.some(movie => movie.id === requestedMovieId)
      ? requestedMovieId
      : movies.value.find(movie => showtimeCount(movie.id) > 0)?.id || movies.value[0]?.id || null

    const requestedDate = String(route.query.date || '')
    if (dateOptions.value.some(option => option.value === requestedDate)) {
      selectedDate.value = requestedDate
    }
  } catch (error) {
    console.error('加载影院详情失败:', error)
    ElMessage.error('影院详情加载失败')
  } finally {
    loading.value = false
  }
}

const selectMovie = (movie: Movie) => {
  selectedMovieId.value = movie.id
  router.replace({ query: { movieId: String(movie.id), date: selectedDate.value } })
}

const selectDate = (date: string) => {
  selectedDate.value = date
  router.replace({ query: { movieId: String(selectedMovieId.value || ''), date } })
}

const scrollMovies = (direction: number) => {
  movieRailRef.value?.scrollBy({ left: direction * 560, behavior: 'smooth' })
}

const formatTime = (value: string) => new Date(value).toLocaleTimeString('zh-CN', {
  hour: '2-digit', minute: '2-digit', hour12: false
})

const formatDate = (value: string) => new Date(value).toLocaleDateString('zh-CN', {
  month: '2-digit', day: '2-digit', weekday: 'short'
})

const formatEndTime = (showtime: Showtime) => {
  if (showtime.endTime) return formatTime(showtime.endTime)
  const end = new Date(showtime.startTime)
  end.setMinutes(end.getMinutes() + (showtime.movieDuration || selectedMovie.value?.duration || 120))
  return formatTime(end.toISOString())
}

const goToSeats = (showtime: Showtime) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后选座购票')
    router.push('/login')
    return
  }
  router.push(`/showtime/${showtime.id}/seats`)
}

onMounted(loadPage)
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0&display=swap');

* { box-sizing: border-box; }
.msr { font-family:'Material Symbols Rounded'; font-weight:normal; font-style:normal; font-size:20px; line-height:1; letter-spacing:normal; text-transform:none; white-space:nowrap; word-wrap:normal; direction:ltr; -webkit-font-feature-settings:'liga'; -webkit-font-smoothing:antialiased; }
.app { --orange:#ff8400; --ink:#111; --muted:#70716e; --line:#d8d9d6; min-height:100vh; display:flex; background:#f2f3f0; color:var(--ink); font-family:'Geist','Microsoft YaHei',sans-serif; }
.main-area { flex:1; min-width:0; }
.top-bar { height:76px; padding:0 32px; display:flex; align-items:center; gap:22px; position:sticky; top:0; z-index:20; background:rgba(242,243,240,.94); border-bottom:1px solid var(--line); backdrop-filter:blur(12px); }
.back-btn { display:flex; align-items:center; gap:7px; padding:10px 15px; border:1px solid var(--line); border-radius:999px; background:#fff; font-weight:650; cursor:pointer; }
.back-btn:hover { border-color:var(--orange); color:#d76f00; }
.breadcrumb { display:flex; align-items:center; gap:10px; font-size:13px; color:var(--muted); }
.breadcrumb span { cursor:pointer; }.breadcrumb span:hover { color:var(--orange); }.breadcrumb i { font-style:normal; color:#aaa; }.breadcrumb strong { color:var(--ink); }
.content { padding:30px 34px 42px; max-width:1500px; margin:0 auto; min-height:calc(100vh - 76px); }
.cinema-hero { min-height:230px; display:flex; align-items:center; gap:26px; padding:36px 42px; border-radius:26px; color:#fff; background:radial-gradient(circle at 78% 24%,rgba(255,132,0,.22),transparent 30%),linear-gradient(135deg,#241230,#3f285b 58%,#241331); box-shadow:0 18px 40px rgba(35,18,48,.22); }
.hero-mark { width:116px; height:116px; border-radius:24px; display:grid; place-items:center; background:linear-gradient(145deg,#ff9c32,#ff7900); box-shadow:0 14px 32px rgba(255,132,0,.35); }.hero-mark .msr{font-size:54px;color:#18110a;}
.hero-main { flex:1; min-width:0; }.eyebrow { font:700 10px/1 'JetBrains Mono'; letter-spacing:2.4px; color:#cdb8e9; }.eyebrow.orange{color:var(--orange);}.hero-main h1{margin:12px 0 10px;font-size:34px;}.hero-main p{margin:0;display:flex;gap:7px;align-items:center;color:rgba(255,255,255,.72);}.hero-main p .msr{font-size:18px;}
.service-tags{display:flex;gap:8px;flex-wrap:wrap;margin-top:20px;}.service-tags span{padding:6px 10px;border:1px solid rgba(255,255,255,.22);border-radius:7px;font-size:12px;background:rgba(255,255,255,.07);}
.hero-side{display:flex;align-items:center;gap:24px;padding:20px 26px;border:1px solid rgba(255,255,255,.13);border-radius:18px;background:rgba(255,255,255,.06);}.hero-side div{display:flex;flex-direction:column;align-items:center;gap:5px;}.hero-side strong{font:800 30px/1 'JetBrains Mono';color:#ff9a2e;}.hero-side span{font-size:11px;color:rgba(255,255,255,.6);}.hero-side i{height:45px;width:1px;background:rgba(255,255,255,.14);}
.movie-picker,.movie-summary,.showtime-panel{margin-top:24px;padding:26px;border:1px solid var(--line);border-radius:22px;background:#fff;box-shadow:0 8px 22px rgba(30,31,28,.04);}.section-head,.showtime-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;}.section-head h2,.showtime-head h2{margin:7px 0 3px;font-size:24px;}.section-head p{margin:0;color:var(--muted);font-size:13px;}.picker-actions{display:flex;gap:8px;}.picker-actions button{width:38px;height:38px;border:1px solid var(--line);border-radius:50%;background:#fff;display:grid;place-items:center;cursor:pointer;}.picker-actions button:hover{border-color:var(--orange);color:var(--orange);}
.movie-rail{display:flex;gap:18px;overflow-x:auto;padding:24px 4px 16px;scrollbar-width:none;scroll-snap-type:x proximity;}.movie-rail::-webkit-scrollbar{display:none;}.movie-card{width:154px;flex:0 0 154px;border:0;background:transparent;text-align:left;cursor:pointer;scroll-snap-align:start;color:var(--ink);}.poster{height:214px;border:3px solid transparent;border-radius:12px;overflow:hidden;background:#e8e9e6;position:relative;display:grid;place-items:center;transition:.2s;}.poster img{width:100%;height:100%;object-fit:cover;}.poster>.msr{font-size:48px;color:#aaa;}.movie-card:hover .poster{transform:translateY(-4px);box-shadow:0 12px 22px rgba(0,0,0,.12);}.movie-card.active .poster{border-color:var(--orange);box-shadow:0 0 0 3px rgba(255,132,0,.14),0 12px 24px rgba(255,132,0,.2);}.movie-card.active .poster:after{content:'';position:absolute;left:50%;bottom:-1px;transform:translateX(-50%);border:9px solid transparent;border-bottom-color:var(--orange);}.session-count{position:absolute;right:7px;top:7px;padding:4px 7px;border-radius:999px;background:rgba(17,17,17,.78);color:#fff;font:700 10px 'JetBrains Mono';}.movie-card>strong{display:block;margin-top:11px;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}.movie-card>span{display:block;margin-top:4px;font-size:11px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.movie-summary{display:flex;align-items:center;gap:20px;padding:22px 26px;}.summary-poster{width:88px;height:118px;border-radius:10px;overflow:hidden;background:#e8e9e6;display:grid;place-items:center;}.summary-poster img{width:100%;height:100%;object-fit:cover;}.summary-poster .msr{font-size:36px;color:#aaa;}.summary-body{flex:1;min-width:0;}.title-row{display:flex;align-items:center;gap:18px;}.title-row h2{margin:0;font-size:28px;}.rating{font:800 26px 'JetBrains Mono';color:var(--orange);}.rating small{font-size:12px;margin-left:3px;}.english{margin:4px 0 17px;color:#999;font:600 11px 'JetBrains Mono';letter-spacing:1.3px;}.movie-meta{display:flex;gap:28px;flex-wrap:wrap;font-size:13px;}.movie-meta span{display:flex;gap:7px;}.movie-meta b{color:#aaa;font-weight:500;}.detail-btn{display:flex;align-items:center;gap:7px;padding:11px 16px;border:1px solid var(--line);border-radius:10px;background:#fff;font-weight:650;cursor:pointer;}.detail-btn:hover{border-color:var(--orange);color:#d76f00;}
.showtime-total{padding:7px 11px;border-radius:999px;background:#f4f1ed;color:#8b5a24;font:700 11px 'JetBrains Mono';}.showtime-table{margin-top:20px;border:1px solid var(--line);border-radius:14px;overflow:hidden;}.table-head,.showtime-row{display:grid;grid-template-columns:1.25fr .9fr 1.1fr .8fr .7fr .8fr;align-items:center;gap:14px;padding:15px 20px;}.table-head{background:#f0f1ee;font-size:12px;font-weight:700;color:#666;}.showtime-row{min-height:88px;border-top:1px solid #e6e7e4;font-size:13px;}.showtime-row:hover{background:#fffaf4;}.time-cell{display:flex;flex-direction:column;gap:4px;}.time-cell strong{font:800 23px 'JetBrains Mono';}.time-cell span{font-size:11px;color:var(--muted);}.price{font:800 23px 'JetBrains Mono';color:#ff6b34;}.price small{font-size:12px;margin-right:2px;}.buy-btn{justify-self:end;padding:11px 17px;border:0;border-radius:999px;background:var(--orange);color:#17110a;font-weight:750;cursor:pointer;box-shadow:0 7px 16px rgba(255,132,0,.25);}.buy-btn:hover{transform:translateY(-1px);box-shadow:0 9px 20px rgba(255,132,0,.35);}
.date-selector{display:flex;gap:10px;margin-top:20px;padding-bottom:18px;border-bottom:1px solid #e5e6e3;}.date-selector button{min-width:112px;padding:10px 15px;border:1px solid var(--line);border-radius:12px;background:#fff;display:grid;grid-template-columns:auto auto;align-items:center;gap:2px 8px;text-align:left;color:var(--ink);cursor:pointer;transition:.18s;}.date-selector button:hover{border-color:rgba(255,132,0,.65);}.date-selector button.active{border-color:var(--orange);background:#fff7ed;box-shadow:0 0 0 3px rgba(255,132,0,.1);}.date-selector span{font-size:13px;font-weight:750;}.date-selector strong{font:700 13px 'JetBrains Mono';}.date-selector small{grid-column:1/-1;color:var(--muted);font-size:10px;}.date-selector button.active span,.date-selector button.active strong{color:#df7200;}
footer{padding:30px 4px 0;text-align:center;color:#969793;font-size:11px;}
@media(max-width:1050px){.cinema-hero{align-items:flex-start;flex-wrap:wrap}.hero-side{width:100%;justify-content:center}.table-head{display:none}.showtime-row{grid-template-columns:1fr 1fr 1fr}.buy-btn{justify-self:start}.movie-summary{align-items:flex-start;flex-wrap:wrap}.detail-btn{margin-left:108px}}
@media(max-width:720px){.top-bar{height:auto;padding:12px 16px;flex-wrap:wrap}.breadcrumb{display:none}.content{padding:18px 14px}.cinema-hero{padding:24px}.hero-mark{width:78px;height:78px}.hero-main h1{font-size:25px}.movie-picker,.movie-summary,.showtime-panel{padding:18px}.date-selector{overflow-x:auto}.date-selector button{min-width:100px}.showtime-row{grid-template-columns:1fr 1fr}.showtime-row>span:nth-of-type(2),.showtime-row>span:nth-of-type(3){display:none}.detail-btn{margin-left:0}.summary-poster{width:72px;height:98px}}
</style>
