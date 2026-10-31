<template>
  <div class="favorites-page">
    <AppSidebar />

    <main class="favorites-main">
      <header class="favorites-header">
        <div>
          <p class="eyebrow">MY WATCHLIST · 想看清单</p>
          <h1>留给下一次心动</h1>
          <p class="subtitle">你的每次想看都会参与个性化推荐，越使用越懂你。</p>
        </div>
        <div class="count-card">
          <MaterialIcon class="msr" name="favorite" />
          <strong>{{ favorites.length }}</strong>
          <span>部想看</span>
        </div>
      </header>

      <section class="toolbar">
        <label class="search-box">
          <MaterialIcon class="msr" name="search" />
          <input v-model="keyword" placeholder="搜索片名、导演或类型" />
          <button v-if="keyword" aria-label="清空搜索" @click="keyword = ''">
            <MaterialIcon class="msr" name="close" />
          </button>
        </label>
        <div class="filter-tabs">
          <button
            v-for="item in statusOptions"
            :key="item.value"
            :class="{ active: statusFilter === item.value }"
            @click="statusFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>
        <select v-model="sortMode" aria-label="排序方式">
          <option value="recent">最近加入</option>
          <option value="rating">评分最高</option>
          <option value="release">上映日期</option>
        </select>
      </section>

      <section v-loading="loading" class="favorites-content">
        <div v-if="displayMovies.length" class="favorites-grid">
          <article v-for="movie in displayMovies" :key="movie.id" class="favorite-card" @click="goToMovie(movie.id)">
            <div class="poster">
              <img v-if="movie.poster" v-poster :src="movie.poster" :alt="movie.title" />
              <div v-else class="poster-empty">
                <MaterialIcon class="msr" name="movie" />
                <span>{{ movie.title }}</span>
              </div>
              <span class="status" :class="`status-${movie.status}`">{{ movie.statusText || statusLabel(movie.status) }}</span>
              <span v-if="movie.rating" class="rating">
                <MaterialIcon class="msr" name="star" />{{ Number(movie.rating).toFixed(1) }}
              </span>
              <WatchlistHeartButton
                class="remove-button"
                active
                size="medium"
                variant="overlay"
                label="移出想看"
                :loading="removingIds.has(movie.id)"
                :disabled="removingIds.has(movie.id)"
                @click.stop="remove(movie)"
              />
            </div>
            <div class="card-body">
              <p class="added-time">{{ formatFavoriteTime(movie.favoriteTime) }}</p>
              <h2>{{ movie.title }}</h2>
              <p class="english-title">{{ movie.englishTitle || movie.director || '响指电影 MOVIE' }}</p>
              <div class="meta">
                <span>{{ movie.genre || '电影' }}</span>
                <i></i>
                <span>{{ movie.duration || '—' }} 分钟</span>
              </div>
              <p class="description">{{ movie.description || '暂无影片简介' }}</p>
              <button class="detail-button" @click.stop="goToMovie(movie.id)">
                <span>{{ movie.status === 1 ? '查看场次' : '查看详情' }}</span>
                <MaterialIcon class="msr" name="arrow_forward" />
              </button>
            </div>
          </article>
        </div>

        <div v-else-if="!loading" class="empty-state">
          <div class="empty-icon"><MaterialIcon class="msr" :name="favorites.length ? 'search_off' : 'favorite_border'" /></div>
          <h2>{{ favorites.length ? '没有符合条件的影片' : '想看清单还是空的' }}</h2>
          <p>{{ favorites.length ? '换个关键词或筛选条件试试。' : '遇到喜欢的影片时点一下爱心，它就会出现在这里。' }}</p>
          <button v-if="favorites.length" @click="resetFilters">重置筛选</button>
          <button v-else @click="router.push('/movies')">去发现电影</button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import AppSidebar from '@/components/AppSidebar.vue'
import WatchlistHeartButton from '@/components/WatchlistHeartButton.vue'
import { recommendApi } from '@/api/recommend'
import type { Movie } from '@/types'

const router = useRouter()
const loading = ref(false)
const favorites = ref<Movie[]>([])
const keyword = ref('')
const statusFilter = ref<'all' | 'playing' | 'coming'>('all')
const sortMode = ref<'recent' | 'rating' | 'release'>('recent')
const removingIds = ref(new Set<number>())

const statusOptions = [
  { value: 'all' as const, label: '全部' },
  { value: 'playing' as const, label: '正在热映' },
  { value: 'coming' as const, label: '即将上映' }
]

const displayMovies = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const result = favorites.value.filter(movie => {
    const matchesQuery = !query || [movie.title, movie.englishTitle, movie.director, movie.genre, movie.actors]
      .filter(Boolean).some(value => String(value).toLowerCase().includes(query))
    const matchesStatus = statusFilter.value === 'all'
      || (statusFilter.value === 'playing' && movie.status === 1)
      || (statusFilter.value === 'coming' && movie.status === 2)
    return matchesQuery && matchesStatus
  })
  return [...result].sort((a, b) => {
    if (sortMode.value === 'rating') return Number(b.rating || 0) - Number(a.rating || 0)
    if (sortMode.value === 'release') return String(b.releaseDate || '').localeCompare(String(a.releaseDate || ''))
    return String(b.favoriteTime || '').localeCompare(String(a.favoriteTime || ''))
  })
})

const loadFavorites = async () => {
  loading.value = true
  try {
    favorites.value = await recommendApi.favorites()
  } finally {
    loading.value = false
  }
}

const remove = async (movie: Movie) => {
  if (removingIds.value.has(movie.id)) return
  removingIds.value.add(movie.id)
  try {
    await recommendApi.removeFavorite(movie.id)
    favorites.value = favorites.value.filter(item => item.id !== movie.id)
    ElMessage.success(`已将《${movie.title}》移出想看`)
  } finally {
    removingIds.value.delete(movie.id)
  }
}

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = 'all'
  sortMode.value = 'recent'
}

const goToMovie = (id: number) => router.push(`/movie/${id}`)

const statusLabel = (status?: number) => ({ 0: '未上映', 1: '正在热映', 2: '即将上映', 3: '已下架' }[status ?? 0] || '影片')

const formatFavoriteTime = (value?: string) => {
  if (!value) return '已加入想看'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '已加入想看'
  return `${date.getMonth() + 1}月${date.getDate()}日加入想看`
}

onMounted(loadFavorites)
</script>

<style scoped>
.favorites-page { min-height: 100vh; display: flex; color: #202421; background: #f3f4f1; font-family: 'Geist','PingFang SC','Microsoft YaHei',sans-serif; }
.favorites-main { min-width: 0; flex: 1; padding: 46px clamp(28px,5vw,76px) 72px; }
.favorites-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; max-width: 1440px; margin: 0 auto 32px; }
.eyebrow { margin: 0 0 10px; color: #ec4899; font: 700 12px/1.4 ui-monospace,monospace; letter-spacing: 1.6px; }
.favorites-header h1 { margin: 0; font-size: clamp(34px,4vw,58px); line-height: 1.05; letter-spacing: -2px; }
.subtitle { margin: 14px 0 0; color: #777e79; font-size: 14px; }
.count-card { min-width: 152px; padding: 18px 20px; display: grid; grid-template-columns: 34px 1fr; align-items: center; border: 1px solid #dddeda; border-radius: 18px; background: #fff; box-shadow: 0 10px 30px rgba(45,50,46,.06); }
.count-card .msr { grid-row: 1 / 3; color: #ec4899; font-size: 28px; }
.count-card strong { font: 800 25px/1 ui-monospace,monospace; }
.count-card span { color: #8b918d; font-size: 11px; }
.toolbar { max-width: 1440px; margin: 0 auto 28px; padding: 10px; display: flex; align-items: center; gap: 12px; border: 1px solid #dedfdb; border-radius: 17px; background: rgba(255,255,255,.82); }
.search-box { min-width: 220px; flex: 1; display: flex; align-items: center; gap: 9px; padding: 0 12px; }
.search-box .msr { color: #969c98; font-size: 20px; }
.search-box input { width: 100%; border: 0; outline: 0; color: #252925; background: transparent; font: inherit; font-size: 13px; }
.search-box button { width: 28px; height: 28px; display: grid; place-items: center; border: 0; border-radius: 8px; background: #eee; cursor: pointer; }
.filter-tabs { display: flex; gap: 5px; }
.filter-tabs button,.toolbar select { height: 38px; padding: 0 14px; border: 0; border-radius: 10px; color: #6d736f; background: #eeefec; font: inherit; font-size: 12px; cursor: pointer; }
.filter-tabs button.active { color: #111; background: #ff8b0a; font-weight: 700; }
.toolbar select { outline: 0; }
.favorites-content { min-height: 360px; max-width: 1440px; margin: auto; }
.favorites-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(245px,1fr)); gap: 22px; }
.favorite-card { overflow: hidden; border: 1px solid #dedfdb; border-radius: 22px; background: #fff; box-shadow: 0 10px 30px rgba(35,40,36,.06); cursor: pointer; transition: transform .2s,box-shadow .2s; }
.favorite-card:hover { transform: translateY(-5px); box-shadow: 0 18px 42px rgba(35,40,36,.12); }
.poster { position: relative; aspect-ratio: 3/4; overflow: hidden; background: linear-gradient(145deg,#2c312e,#111311); }
.poster img { width: 100%; height: 100%; object-fit: cover; transition: transform .35s; }
.favorite-card:hover .poster img { transform: scale(1.035); }
.poster::after { content: ''; position: absolute; inset: auto 0 0; height: 38%; background: linear-gradient(transparent,rgba(0,0,0,.62)); pointer-events: none; }
.poster-empty { height: 100%; display: grid; place-content: center; gap: 10px; color: #aeb4b0; text-align: center; }
.poster-empty .msr { margin: auto; color: #ff8b0a; font-size: 50px; }
.status,.rating { position: absolute; z-index: 1; top: 13px; padding: 6px 9px; border-radius: 999px; color: #fff; background: rgba(16,18,17,.78); backdrop-filter: blur(8px); font-size: 10px; font-weight: 700; }
.status { left: 13px; }.status-1 { color: #111; background: #ff8b0a; }.status-2 { background: #7c3aed; }
.rating { right: 13px; display: flex; align-items: center; gap: 3px; }.rating .msr { color: #ffc342; font-size: 14px; }
.remove-button { position: absolute; z-index: 2; right: 13px; bottom: 13px; }
.card-body { padding: 18px; }
.added-time { margin: 0 0 8px; color: #ec4899; font: 700 10px/1.3 ui-monospace,monospace; text-transform: uppercase; }
.card-body h2 { margin: 0; overflow: hidden; color: #242824; font-size: 19px; white-space: nowrap; text-overflow: ellipsis; }
.english-title { margin: 5px 0 12px; overflow: hidden; color: #9a9f9b; font-size: 10px; letter-spacing: .6px; white-space: nowrap; text-overflow: ellipsis; text-transform: uppercase; }
.meta { display: flex; align-items: center; gap: 8px; color: #69706b; font-size: 11px; }.meta i { width: 3px; height: 3px; border-radius: 50%; background: #ff8b0a; }
.description { min-height: 42px; margin: 12px 0 16px; display: -webkit-box; overflow: hidden; color: #858b87; font-size: 12px; line-height: 1.65; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.detail-button { width: 100%; height: 39px; padding: 0 13px; display: flex; align-items: center; justify-content: space-between; border: 0; border-radius: 11px; color: #20231f; background: #eff0ed; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.detail-button:hover { background: #ff8b0a; }.detail-button .msr { font-size: 17px; }
.empty-state { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.empty-icon { width: 82px; height: 82px; display: grid; place-items: center; border-radius: 28px; color: #ec4899; background: #fff; box-shadow: 0 12px 35px rgba(35,40,36,.08); }.empty-icon .msr { font-size: 39px; }
.empty-state h2 { margin: 22px 0 8px; font-size: 24px; }.empty-state p { margin: 0 0 24px; color: #858b87; font-size: 13px; }
.empty-state button { padding: 11px 20px; border: 0; border-radius: 12px; color: #111; background: #ff8b0a; font-weight: 700; cursor: pointer; }
@media (max-width: 800px) { .favorites-main{padding:28px 16px 60px}.favorites-header{align-items:flex-start}.count-card{min-width:120px}.toolbar{align-items:stretch;flex-direction:column}.filter-tabs{overflow-x:auto}.filter-tabs button{flex:1;white-space:nowrap}.favorites-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.card-body{padding:14px}.description{display:none} }
@media (max-width: 520px) { .favorites-header{display:block}.count-card{width:138px;margin-top:22px}.favorites-grid{grid-template-columns:1fr}.favorite-card{display:grid;grid-template-columns:120px 1fr}.poster{height:100%;aspect-ratio:auto}.english-title{max-width:170px} }
</style>
