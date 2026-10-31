<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <AppSidebar />

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- Top Bar -->
      <div class="top-bar">
        <div
          class="tb-pill"
          role="button"
          tabindex="0"
          :title="location.source === 'ip' ? 'IP 定位可能受代理影响，点击选择城市' : '点击选择城市'"
          @click="cityPickerVisible = true"
          @keyup.enter="cityPickerVisible = true"
        >
          <MaterialIcon class="msr tb-pill-ico primary" name="location_on" />
          <span class="tb-pill-text">{{ location.label }}</span>
          <MaterialIcon class="msr tb-pill-caret" name="keyboard_arrow_down" />
        </div>
        <div class="tb-search">
          <MaterialIcon class="msr tb-search-ico" @click="onSearch" name="search" />
          <input
            type="text"
            placeholder="搜索电影 · 影院 · 演员"
            v-model="searchKeyword"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="tb-pill">
          <MaterialIcon class="msr" name="calendar_month" />
          <span class="tb-pill-text">{{ todayLabel }}</span>
        </div>
        <div class="tb-icon-group">


        </div>
        <button class="tb-cta" @click="showScanner = true">
          <MaterialIcon class="msr" name="qr_code_scanner" />
          <span>扫码取票</span>
        </button>
      </div>

      <!-- Content Scroll -->
      <div class="content">
        <!-- ============ HERO ============ -->
        <section
          class="hero"
          v-if="heroMovie || comingSoon.length || loadingComing"
          @mouseenter="stopAutoRotate"
          @mouseleave="startAutoRotate"
        >
          <div class="hero-main" v-if="heroMovie">
            <div class="hero-content">
              <div class="hero-left" :key="`hl-${heroMovie.id}`">
                <div class="hero-top">
                  <div class="tag-row">
                    <span class="badge-now">
                      <span class="dot-now"></span>
                      <span>NOW SHOWING</span>
                    </span>
                    <span v-if="heroMovie.language" class="badge-tag">{{ heroMovie.language }}</span>
                  </div>
                  <h1 class="hero-title">{{ heroMovie.title }}</h1>
                  <p class="hero-en">{{ (heroMovie.englishTitle || heroMovie.title).toUpperCase() }}</p>
                  <div class="hero-meta">
                    <div class="rating-block">
                      <MaterialIcon class="msr star" name="star" />
                      <span class="rating-num">{{ heroMovie.rating?.toFixed(1) || '暂无评分' }}</span>
                      <span class="rating-out">/ 10</span>
                    </div>
                    <span class="meta-sep">{{ heroMovie.genre || '类型未提供' }}</span>
                    <span class="meta-sep">{{ heroMovie.duration }} 分钟</span>
                    <span class="meta-sep">{{ heroMovie.director || '导演未提供' }}</span>
                  </div>
                  <p class="hero-desc">
                    {{ heroMovie.description || '暂无简介' }}
                  </p>
                </div>
                <div class="hero-actions">
                  <button class="hero-cta" @click="goToDetail(heroMovie.id)">
                    <MaterialIcon class="msr" name="play_arrow" />
                    <span>立即购票</span>
                    <span class="kbd-b">B</span>
                  </button>

                  <WatchlistHeartButton
                    size="large"
                    variant="dark"
                    :active="Boolean(heroMovie && favoriteMovieIds.has(heroMovie.id))"
                    @click="heroMovie && toggleFavorite(heroMovie)"
                  />

                </div>
              </div>
              <div class="hero-right">
                <div class="hero-poster" :key="`hp-${heroMovie.id}`">
                  <img
                    v-if="heroMovie.poster"
                    v-poster="{ eager: true }"
                    :src="heroMovie.poster"
                    :alt="heroMovie.title"
                    class="poster-img"
                  />
                  <div v-else class="poster-placeholder">
                    <MaterialIcon class="msr" name="movie" />
                  </div>
                </div>
                <div class="hero-controls">
                  <div class="page-num">
                    <span class="page-cur">{{ heroPageCur }}</span>
                    <span class="page-line"></span>
                    <span class="page-tot">{{ heroPageTot }}</span>
                  </div>
                  <div class="arrow-group">
                    <button class="arrow-btn" :disabled="heroCount <= 1" @click="goPrevHero">
                      <MaterialIcon class="msr" name="arrow_back" />
                    </button>
                    <button class="arrow-btn solid" :disabled="heroCount <= 1" @click="goNextHero">
                      <MaterialIcon class="msr" name="arrow_forward" />
                    </button>
                  </div>
                  <div class="dots">
                    <span
                      v-for="i in heroCount"
                      :key="i"
                      class="dot"
                      :class="{ active: i - 1 === currentHeroIndex }"
                      @click="goToHero(i - 1)"
                    ></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
            <aside class="hero-side cs-panel" aria-label="即将上映" v-loading="loadingComing">
              <div class="cs-head">
                <div class="cs-title-row">
                  <MaterialIcon class="msr cs-ico" name="event" />
                  <h3 class="cs-title">即将上映</h3>
                </div>
                <router-link class="cs-all" :to="{ path: '/movies', query: { ranking: 'upcoming' } }">全部 →</router-link>
              </div>
              <div
                v-for="m in comingSoonDisplay"
                :key="m.id"
                class="cs-item" role="button" tabindex="0" @keydown.enter="goToDetail(m.id)" @keydown.space.prevent="goToDetail(m.id)"
                @click="goToDetail(m.id)"
              >
                <div class="cs-date">
                  <span class="cs-day">{{ dayOf(m.releaseDate) }}</span>
                  <span class="cs-mon">{{ monthOf(m.releaseDate) }}</span>
                </div>
                <div class="cs-poster" :style="{ background: csGradient(m.id) }">
                  <img v-if="m.poster" v-poster :src="m.poster" :alt="m.title" />
                </div>
                <div class="cs-info">
                  <span class="cs-name">{{ m.title }}</span>
                  <span class="cs-genre">{{ m.genre || '剧情' }}</span>
                </div>
              </div>
              <el-empty v-if="!loadingComing && comingSoon.length === 0" description="暂无影片" :image-size="60" />
            </aside>
        </section>

        <MarketingPlacements slot="HOME" />
        <!-- ============ NOW SHOWING ============ -->
        <section class="ns-section">
          <div class="ns-head">
            <div class="ns-title-block">
              <div class="ns-title-row">
                <h2 class="ns-title">正在热映</h2>
                <span class="ns-count">{{ nowShowing.length }} 部</span>
              </div>
              <p class="ns-sub">本周新片 · 全国院线同步上映</p>
            </div>
            <div class="ns-tools">
              <div class="ns-pills">
                <button
                  v-for="p in dayPills"
                  :key="p.key"
                  class="ns-pill"
                  :class="{ active: activeDay === p.key }"
                  @click="p.key === 'all' ? router.push('/movies') : activeDay = p.key"
                >
                  {{ p.label }}
                </button>
              </div>
              <button class="ns-sort" @click="goToHotRanking">
                <MaterialIcon class="msr" name="sort" />
                <span>热度排序</span>
              </button>
              <div class="ns-arrows">
                <button class="ns-arrow" @click="scrollNs(-1)">
                  <MaterialIcon class="msr" name="arrow_back" />
                </button>
                <button class="ns-arrow dark" @click="scrollNs(1)">
                  <MaterialIcon class="msr" name="arrow_forward" />
                </button>
              </div>
            </div>
          </div>
          <div class="ns-grid" ref="nsGridRef" v-loading="loadingNow">
            <article
              v-for="(m, i) in nowShowingDisplay"
              :key="m.id"
              class="movie-card"
              :class="{ featured: i === featuredIndex }"
              @click="goToDetail(m.id)"
            >
              <div class="card-poster" :style="{ background: cardGradient(i) }">
                <img v-if="m.poster" v-poster :src="m.poster" :alt="m.title" />
                <div class="card-top">
                  <span class="card-badge" :class="{ primary: i === featuredIndex }">
                    <span class="dot-now"></span>
                    <span>{{ i === featuredIndex ? 'FEATURED' : 'HOT' }}</span>
                  </span>
                  <span class="card-rating">
                    <MaterialIcon class="msr" name="star" />
                    <span>{{ m.rating?.toFixed(1) || '暂无评分' }}</span>
                  </span>
                </div>
              </div>
              <div class="card-title-panel">
                <h3 class="card-title-below">{{ m.title }}</h3>
                <p class="card-eng-below">{{ (m.englishTitle || m.title).toUpperCase() }}</p>
              </div>
              <div class="card-foot">
                <div class="card-meta">
                  <div class="card-genre-row">
                    <MaterialIcon class="msr genre-ico" name="local_fire_department" />
                    <span class="card-genre">{{ m.genre || '剧情' }}</span>
                  </div>
                  <div class="card-price-row">
                    <span class="price-unit">票价以场次为准</span>
                  </div>
                </div>
                <button class="card-cta" :class="{ primary: i === featuredIndex }" @click.stop="goToDetail(m.id)">
                  <span>选座</span>
                  <MaterialIcon class="msr" name="arrow_forward" />
                </button>
              </div>
            </article>
          </div>
          <el-empty v-if="!loadingNow && nowShowing.length === 0" description="暂无影片" />
        </section>

        <!-- ============ TWO COL ROW ============ -->
        <section class="two-col">
          <div class="main-col">
            <!-- Main recommendation -->
            <div class="rec-panel">
              <div class="rec-head">
                <div class="rec-head-left">
                  <div class="rec-title-row">
                    <span class="rec-icon-wrap">
                      <MaterialIcon class="msr" name="stars" />
                    </span>
                    <h3 class="rec-title">AI 智能推荐</h3>
                    <span class="rec-tag">PERSONALIZED</span>
                  </div>
                  <p class="rec-sub">
                    {{ userStore.isLoggedIn ? '根据你的观影历史和偏好,AI 为你精选 6 部影片' : '登录后,AI 将根据你的观影历史推荐影片' }}
                  </p>
                </div>
                <div class="rec-controls">
                  <div class="rec-tabs">
                    <button
                      v-for="t in recTabs"
                      :key="t.key"
                      class="rec-tab"
                      :class="{ active: activeRecTab === t.key }"
                      @click="t.key === 'all' ? router.push('/movies') : activeRecTab = t.key"
                    >
                      {{ t.label }}
                    </button>
                  </div>
                  <button class="rec-refresh" :disabled="loadingRec" @click="showNextRecommendations">
                    <MaterialIcon class="msr" name="refresh" />
                    <span>{{ loadingRec ? '加载中' : '换一批' }}</span>
                  </button>
                </div>
              </div>
              <div class="rec-grid">
                <article
                  v-for="m in recDisplay"
                  :key="m.id"
                  class="rec-card"
                  @click="goToDetail(m.id)"
                >
                  <div class="rec-poster" :style="{ background: cardGradient(m.id) }">
                    <img v-if="m.poster" v-poster :src="m.poster" :alt="m.title" />
                    <span v-if="m.matchScore != null" class="rec-match">{{ m.matchScore }}% 匹配</span>
                  </div>
                  <div class="rec-info">
                    <span class="rec-name">{{ m.title }}</span>
                    <span class="rec-meta">
                      <MaterialIcon class="msr" name="star" />
                      <span>{{ m.rating?.toFixed(1) || '暂无评分' }}</span>
                      <span class="sep">·</span>
                      <span>{{ m.genre || '剧情' }}</span>
                    </span>
                    <span v-if="m.recommendationReason" class="rec-reason">
                      {{ m.analysisSource === 'model' ? '大模型推荐' : '实时推荐' }}《{{ m.title }}》：{{ m.recommendationReason }}。
                    </span>
                  </div>
                </article>
              </div>
              <div v-if="recDisplay.length === 0 && !loadingRec" class="rec-empty">
                <MaterialIcon class="msr" name="auto_awesome" />
                <p>{{ userStore.isLoggedIn ? '暂无推荐内容' : '登录后查看你的专属推荐' }}</p>
                <button v-if="!userStore.isLoggedIn" class="rec-login-btn" @click="router.push('/login')">
                  立即登录
                </button>
              </div>
            </div>

            <!-- Cinema Panel -->
            <div class="cinema-panel" v-loading="loadingCinemas">
              <div class="cp-head">
                <div class="cp-head-left">
                  <div class="cp-title-row">
                    <MaterialIcon class="msr cp-ico" name="location_on" />
                    <h3 class="cp-title">附近影院</h3>
                  </div>
                  <p class="cp-sub">已为您按距离排序 · {{ nearestArea }}附近</p>
                </div>
                <button class="cp-map" @click="router.push('/cinemas')">
                  <MaterialIcon class="msr" name="map" />
                  <span>查看影院</span>
                </button>
              </div>
              <div
                v-for="(c, i) in cinemas"
                :key="c.id"
                class="cinema-item"
                role="button"
                tabindex="0"
                :aria-label="`查看${c.name}详情`"
                @click="goToCinema(c.id)"
                @keydown.enter="goToCinema(c.id)"
                @keydown.space.prevent="goToCinema(c.id)"
              >
                <div class="cinema-rank">{{ String(i + 1).padStart(2, '0') }}</div>
                <div class="cinema-body">
                  <div class="cinema-name-row">
                    <span class="cinema-name">{{ c.name }}</span>
                  </div>
                  <p class="cinema-sub">{{ c.area }}</p>
                  <div class="cinema-meta">
                    <span class="cm-item"><MaterialIcon class="msr" name="straighten" />{{ c.distance }}</span>
                    <span class="cm-item"><MaterialIcon class="msr" name="directions_walk" />{{ c.walkTime }}</span>
                  </div>
                </div>
              </div>

              <div v-if="!loadingCinemas && cinemas.length === 0" class="cinema-empty">
                <MaterialIcon class="msr" name="location_on" />
                <p>该城市暂未开通影院，点击顶部城市可切换</p>
              </div>
            </div>
          </div>

          <!-- Right column -->
          <div class="right-col">
            <!-- AI Personalization Card -->
            <aside class="ai-card">
              <div class="ai-head">
                <div class="ai-icon-wrap">
                  <MaterialIcon class="msr" name="stars" />
                </div>
                <div class="ai-title-block">
                  <span class="ai-title">AI 个性化分析</span>
                  <span class="ai-sub">基于你的观影数据</span>
                </div>
                <span class="ai-beta">BETA</span>
              </div>
              <div class="ai-profile">
                <span class="ai-section-label">观影画像</span>
                <div class="profile-row">
                  <span class="profile-label">
                    <MaterialIcon class="msr" name="movie" />
                    <span>已观看</span>
                  </span>
                  <span class="profile-val">{{ userStore.isLoggedIn ? `${recommendationProfile?.purchasedCount || 0} 部` : '— 部' }}</span>
                </div>
                <div class="profile-row">
                  <span class="profile-label">
                    <MaterialIcon class="msr" name="category" />
                    <span>偏好类型</span>
                  </span>
                  <span class="profile-val">{{ userStore.isLoggedIn ? (recommendationProfile?.favoriteGenres.join(' / ') || '待探索') : '— / —' }}</span>
                </div>
                <div class="profile-row">
                  <span class="profile-label">
                    <MaterialIcon class="msr" name="star" />
                    <span>已评价</span>
                  </span>
                  <span class="profile-val">{{ userStore.isLoggedIn ? `${recommendationProfile?.ratedCount || 0} 次` : '—' }}</span>
                </div>
              </div>
              <RecommendationRadar
                v-if="userStore.isLoggedIn && recommendationRadar"
                :data="recommendationRadar"
              />
              <div class="ai-tags-block">
                <span class="ai-section-label">你的偏好标签</span>
                <div class="ai-tags">
                  <span v-for="t in (userStore.isLoggedIn ? userTags : guestTags)" :key="t" class="ai-tag">
                    {{ t }}
                  </span>
                </div>
              </div>
              <div class="ai-line"></div>
              <div class="ai-insight">
                <div class="ai-insight-head">
                  <MaterialIcon class="msr insight-ico" name="stars" />
                  <span>{{ recommendationProfile?.analysisSource === 'model' ? 'AI 洞察 · 大模型分析' : '观影洞察 · 行为统计' }}</span>
                </div>
                <p class="ai-insight-text">
                  {{ aiInsightText }}
                </p>
              </div>
              <button class="ai-cta" @click="onAiChat">
                <MaterialIcon class="msr" name="chat" />
                <span>与 AI 观影助手对话</span>
              </button>
            </aside>
          </div>
        </section>

        <div class="page-foot">
          <span>© 2026 响指电影 · 沪ICP 备 2024XXXXX 号</span>
          <div class="foot-links">



          </div>
        </div>
      </div>
    </main>

    <!-- 扫码取票对话框 -->
    <QRScanner v-if="showScanner" v-model="showScanner" @success="handleScanSuccess" />
    <ColdStartDialog v-model="showColdStart" :movies="nowShowing" @complete="handleColdStartComplete" />
    <CityPickerDialog v-model="cityPickerVisible" />
  </div>
</template>

<script setup lang="ts">
/**
 * 首页：展示热映、待映、实时票房和个性化推荐，连接搜索、想看与购票入口。
 */
import { ref, computed, defineAsyncComponent, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { useUserStore } from '@/stores/user'
import { movieApi } from '@/api/movie'
import { cinemaApi } from '@/api/cinema'
import { recommendApi } from '@/api/recommend'
import request from '@/utils/request'
import type { Movie, Cinema, RecommendationProfile, RecommendationRadar as RecommendationRadarData } from '@/types'
import AppSidebar from '@/components/AppSidebar.vue'
import MarketingPlacements from '@/components/MarketingPlacements.vue'
import CityPickerDialog from '@/components/CityPickerDialog.vue'
import ColdStartDialog from '@/components/ColdStartDialog.vue'
import RecommendationRadar from '@/components/RecommendationRadar.vue'
import WatchlistHeartButton from '@/components/WatchlistHeartButton.vue'
import { useLocation } from '@/composables/useLocation'
import { cityOfAddress, normalizeCityName } from '@/utils/city'

const QRScanner = defineAsyncComponent(() => import('@/components/QRScanner.vue'))

const router = useRouter()
const userStore = useUserStore()
const { location, ensure: ensureLocation } = useLocation()
const cityPickerVisible = ref(false)

const searchKeyword = ref('')
const loadingNow = ref(false)
const loadingComing = ref(false)
const loadingRec = ref(false)
const showScanner = ref(false)
const showColdStart = ref(false)
const recommendationProfile = ref<RecommendationProfile | null>(null)
const recommendationRadar = ref<RecommendationRadarData | null>(null)
let homeDisposed = false
const favoriteMovieIds = ref(new Set<number>())

const nowShowing = ref<Movie[]>([])
const comingSoon = ref<Movie[]>([])
const recommendations = ref<Movie[]>([])
const activeDay = ref<'today' | 'tomorrow' | 'week' | 'all'>('today')
const activeRecTab = ref<'all' | 'scifi' | 'action' | 'drama'>('all')
const recommendationBatch = ref(0)
const RECOMMENDATION_BATCH_SIZE = 6

const dayPills = [
  { key: 'today', label: '今日' },
  { key: 'tomorrow', label: '明日' },
  { key: 'week', label: '本周' },
  { key: 'all', label: '全部' }
] as const

const recTabs = [
  { key: 'all', label: '全部' },
  { key: 'scifi', label: '科幻' },
  { key: 'action', label: '动作' },
  { key: 'drama', label: '剧情' }
] as const

const userTags = computed(() => recommendationProfile.value?.favoriteGenres.length
  ? recommendationProfile.value.favoriteGenres
  : ['画像学习中'])
const guestTags = ['立即注册', '探索更多', '解锁专属']

const currentHeroIndex = ref(0)
const AUTO_ROTATE_MS = 4500
let autoRotateTimer: ReturnType<typeof setInterval> | null = null
const loadingCinemas = ref(false)
const allCinemas = ref<Cinema[]>([])

const haversine = (lat1: number, lng1: number, lat2: number, lng2: number) => {
  const R = 6371000
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

interface CinemaDisplayItem {
  id: number
  name: string
  area: string
  distance: string
  distanceNum: number
  walkTime: string
}

const cinemas = computed<CinemaDisplayItem[]>(() => {
  const { lat: userLat, lng: userLng } = location.value
  const selectedCity = normalizeCityName(location.value.city)
  const source = selectedCity
    ? allCinemas.value.filter(c => normalizeCityName(cityOfAddress(c.address || '')) === selectedCity)
    : allCinemas.value
  const items: CinemaDisplayItem[] = source.map(c => {
    let distanceNum = Infinity
    if (userLat != null && userLng != null && c.lat != null && c.lng != null) {
      distanceNum = haversine(userLat, userLng, Number(c.lat), Number(c.lng))
    }
    const distStr = distanceNum === Infinity ? '—' : distanceNum < 1000 ? `${Math.round(distanceNum)} m` : `${(distanceNum / 1000).toFixed(1)} km`
    const walkMin = distanceNum === Infinity ? '距离未知' : '直线距离估算'
    const addr = c.address || ''
    const area = addr.includes('市') ? addr.split('市')[1]?.substring(0, 6) || addr.substring(0, 6) : addr.substring(0, 6)
    return {
      id: c.id,
      name: c.name,
      area,
      distance: distStr,
      distanceNum,
      walkTime: walkMin
    }
  })
  return items.sort((a, b) => a.distanceNum - b.distanceNum).slice(0, 5)
})

const nearestArea = computed(() => {
  if (cinemas.value.length === 0) return normalizeCityName(location.value.city) || ''
  return cinemas.value[0].area
})

const heroCount = computed(() => Math.min(nowShowing.value.length, 5))

const heroMovie = computed(() => {
  if (nowShowing.value.length === 0) return null
  return nowShowing.value[currentHeroIndex.value] || nowShowing.value[0]
})

const heroPageCur = computed(() =>
  String(currentHeroIndex.value + 1).padStart(2, '0')
)
const heroPageTot = computed(() =>
  String(Math.max(heroCount.value, 1)).padStart(2, '0')
)

const nowShowingDisplay = computed(() => nowShowing.value.slice(0, 5))
const featuredIndex = computed(() => Math.min(4, nowShowingDisplay.value.length - 1))
const comingSoonDisplay = computed(() => comingSoon.value.slice(0, 4))
const filteredRecommendations = computed(() => {
  const tokens: Record<typeof activeRecTab.value, string[]> = {
    all: [],
    scifi: ['科幻'],
    action: ['动作', '冒险'],
    drama: ['剧情']
  }
  const expected = tokens[activeRecTab.value]
  if (expected.length === 0) return recommendations.value
  return recommendations.value.filter(movie => expected.some(token => movie.genre?.includes(token)))
})
const recDisplay = computed(() => {
  const list = filteredRecommendations.value
  if (list.length <= RECOMMENDATION_BATCH_SIZE) return list
  const start = recommendationBatch.value * RECOMMENDATION_BATCH_SIZE % list.length
  return Array.from(
    { length: Math.min(RECOMMENDATION_BATCH_SIZE, list.length) },
    (_, index) => list[(start + index) % list.length]
  )
})

const asSentence = (value?: string) => {
  const text = value?.trim().replace(/[。！？!?]+$/, '')
  return text ? `${text}。` : ''
}

const aiInsightText = computed(() => {
  if (!userStore.isLoggedIn) {
    return '登录后，系统将根据你的观影、想看、购票和评分数据生成专属洞察。'
  }
  if (loadingRec.value && !recommendationProfile.value && !recommendationRadar.value) {
    return '正在汇总你的真实观影数据并生成个性化分析…'
  }

  const profile = recommendationProfile.value
  const radar = recommendationRadar.value
  if (!profile && !radar) {
    return '暂时无法读取个性化分析，请稍后刷新重试。'
  }

  if (profile?.summary) return profile.summary
  const topMovie = recommendations.value[0]
  const summary = radar?.summary || (profile?.needsColdStart
    ? '暂无足够的用户行为证据，当前以影片口碑和近期趋势作为冷启动依据'
    : '')
  const genres = radar?.topGenres?.length
    ? radar.topGenres.slice(0, 3)
    : profile?.favoriteGenres.slice(0, 3) || []
  const preference = genres.length > 0 ? `你的主要偏好类型是${genres.join('、')}` : ''
  const recommendation = topMovie
    ? `当前首选《${topMovie.title}》${topMovie.recommendationReason ? `，推荐理由：${topMovie.recommendationReason}` : ''}`
    : ''

  return [summary, preference, recommendation]
    .map(asSentence)
    .filter(Boolean)
    .join('') || '还没有足够的数据生成个性化洞察，多浏览、收藏或评价几部影片后会更准确。'
})

watch(activeRecTab, () => {
  recommendationBatch.value = 0
})

const goPrevHero = () => {
  const n = heroCount.value
  if (n <= 1) return
  currentHeroIndex.value = (currentHeroIndex.value - 1 + n) % n
  restartAutoRotate()
}

const goNextHero = () => {
  const n = heroCount.value
  if (n <= 1) return
  currentHeroIndex.value = (currentHeroIndex.value + 1) % n
  restartAutoRotate()
}

const goToHero = (i: number) => {
  if (i < 0 || i >= heroCount.value) return
  currentHeroIndex.value = i
  restartAutoRotate()
}

const startAutoRotate = () => {
  stopAutoRotate()
  if (heroCount.value <= 1) return
  autoRotateTimer = setInterval(() => {
    currentHeroIndex.value = (currentHeroIndex.value + 1) % heroCount.value
  }, AUTO_ROTATE_MS)
}

const stopAutoRotate = () => {
  if (autoRotateTimer) {
    clearInterval(autoRotateTimer)
    autoRotateTimer = null
  }
}

const restartAutoRotate = () => {
  stopAutoRotate()
  startAutoRotate()
}

watch(
  () => nowShowing.value.length,
  () => {
    if (currentHeroIndex.value >= heroCount.value) {
      currentHeroIndex.value = 0
    }
    restartAutoRotate()
  }
)

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd} ${week}`
})

const nsGridRef = ref<HTMLElement | null>(null)
const scrollNs = (dir: number) => {
  if (!nsGridRef.value) return
  nsGridRef.value.scrollBy({ left: dir * 320, behavior: 'smooth' })
}

const cardGradients = [
  'linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%)',
  'linear-gradient(200deg, #5c2a1a 0%, #2a0f08 100%)',
  'linear-gradient(200deg, #5c1a4a 0%, #2a0820 100%)',
  'linear-gradient(200deg, #5c5a1a 0%, #2a280a 100%)',
  'linear-gradient(200deg, #FF6B9D 0%, #8B2F5C 100%)',
  'linear-gradient(200deg, #1a5c4a 0%, #082a20 100%)'
]
const cardGradient = (i: number) => cardGradients[i % cardGradients.length]

const csGradients = [
  'linear-gradient(180deg, #1f3a1a 0%, #0a1f08 100%)',
  'linear-gradient(180deg, #3a1a1a 0%, #1f0808 100%)',
  'linear-gradient(180deg, #5c4a1a 0%, #2a200a 100%)',
  'linear-gradient(180deg, #1a5c4a 0%, #082a20 100%)'
]
const csGradient = (id: number) => csGradients[id % csGradients.length]

const dayOf = (date?: string) => {
  if (!date) return '--'
  return String(new Date(date).getDate()).padStart(2, '0')
}
const monthOf = (date?: string) => {
  if (!date) return '--'
  return `${String(new Date(date).getMonth() + 1).padStart(2, '0')} 月`
}

const loadNowShowing = async () => {
  loadingNow.value = true
  try {
    const data = await movieApi.getNowShowing(true)
    nowShowing.value = data || []
  } catch (e) {
    console.error('加载正在热映失败:', e)
  } finally {
    loadingNow.value = false
  }
}

/** 加载影院列表，供影院选择或地图展示。 */
const loadCinemas = async () => {
  loadingCinemas.value = true
  try {
    const data = await cinemaApi.getAll(true)
    allCinemas.value = data || []
  } catch (e) {
    console.error('加载影院失败:', e)
    allCinemas.value = []
  } finally {
    loadingCinemas.value = false
  }
}

const loadComingSoon = async () => {
  loadingComing.value = true
  try {
    const data = await movieApi.getComingSoon(true)
    comingSoon.value = data || []
  } catch (e) {
    console.error('加载即将上映失败:', e)
  } finally {
    loadingComing.value = false
  }
}

/** 按当前偏好和场景加载推荐结果，更新加载状态。 */
const loadRecommendations = async () => {
  loadingRec.value = true
  try {
    if (userStore.isLoggedIn) {
      try {
        const [profile, radar, r] = await Promise.all([
          recommendApi.profile().catch(() => null),
          recommendApi.radar().catch(() => null),
          recommendApi.personal({ limit: 18,
            latitude: location.value.lat ?? undefined,
            longitude: location.value.lng ?? undefined })
        ])
        recommendationProfile.value = profile
        recommendationRadar.value = radar
        showColdStart.value = profile?.needsColdStart ?? false
        if (r && r.length > 0) {
          recommendations.value = r
          recommendationBatch.value = 0
          return
        }
      } catch (error: any) {
        if ([401, 403].includes(error.response?.status)) {
          userStore.clearSession()
        }
        // fall through to hot
      }
    }
    const hot = await request.get<any, Movie[]>('/recommend/hot', {
      params: { limit: 18 },
      silent: true
    })
    recommendations.value = hot || []
    recommendationBatch.value = 0
  } catch (e) {
    console.error('加载推荐失败:', e)
  } finally {
    loadingRec.value = false
  }
}

const showNextRecommendations = async () => {
  if (loadingRec.value) return
  const previousBatch = recommendationBatch.value
  if (userStore.isLoggedIn) await loadRecommendations()
  recommendationBatch.value = previousBatch
  const count = filteredRecommendations.value.length
  if (count <= RECOMMENDATION_BATCH_SIZE) {
    ElMessage.info(count > 0 ? '当前分类的推荐已全部展示' : '当前分类暂无推荐')
    return
  }
  const batchCount = Math.ceil(count / RECOMMENDATION_BATCH_SIZE)
  recommendationBatch.value = (recommendationBatch.value + 1) % batchCount
}

/** 接收初始偏好收集结果并刷新个性化推荐。 */
const handleColdStartComplete = async () => {
  await loadRecommendations()
}

const goToDetail = (id: number) => {
  router.push(`/movie/${id}`)
}

const goToCinema = (id: number) => {
  router.push(`/cinema/${id}`)
}

const goToHotRanking = () => {
  router.push({ path: '/movies', query: { ranking: 'hot' } })
}

const onSearch = () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/movies', query: { q: kw } })
}

const onAiChat = () => {
  void router.push('/assistant')
}

/** 读取本人想看记录，并同步影片收藏状态。 */
const loadFavorites = async () => {
  if (!userStore.isLoggedIn) return
  const list = await recommendApi.favorites().catch(() => [] as Movie[])
  favoriteMovieIds.value = new Set(list.map(movie => movie.id))
}

/** 切换影片想看状态，请求成功后更新页面反馈。 */
const toggleFavorite = async (movie: Movie) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能加入想看')
    router.push('/login')
    return
  }
  const active = await recommendApi.toggleFavorite(movie.id)
  const next = new Set(favoriteMovieIds.value)
  active ? next.add(movie.id) : next.delete(movie.id)
  favoriteMovieIds.value = next
  ElMessage.success(active ? `已将《${movie.title}》加入想看` : `已将《${movie.title}》移出想看`)
}


const handleScanSuccess = (ticketInfo: any) => {
  console.log('取票成功:', ticketInfo)
  ElMessage.success(`取票成功！${ticketInfo.movieTitle}`)
}

onMounted(async () => {
  await loadNowShowing()
  if (homeDisposed) return
  loadCinemas()
  loadComingSoon()
  loadRecommendations()
  loadFavorites()
  startAutoRotate()
  ensureLocation()
})

onBeforeUnmount(() => {
  homeDisposed = true
  stopAutoRotate()
})
</script>

<style scoped>

.app {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --fg: #111111;
  --muted: #666666;
  --border: #CBCCC9;
  --secondary: #E7E8E5;
  --radius-m: 16px;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  display: flex;
  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
  color: var(--fg);
}

.msr {
  font-family: 'Material Symbols Rounded';
  font-weight: normal;
  font-style: normal;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
  font-size: 20px;
  user-select: none;
}

/* ============ MAIN AREA ============ */
.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* Top Bar */
.top-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 32px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(8px);
}
.tb-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.tb-pill:hover { border-color: var(--primary); }
.tb-pill .msr { font-size: 16px; color: var(--muted); }
.tb-pill .primary { color: var(--primary); }
.tb-pill-caret { font-size: 14px !important; }
.tb-pill-text { font-size: 13px; font-weight: 500; }
.tb-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.15s;
}
.tb-search:focus-within { border-color: var(--primary); }
.tb-search .msr { font-size: 18px; color: var(--muted); }
.tb-search-ico { cursor: pointer; transition: color 0.15s; }
.tb-search-ico:hover { color: var(--primary); }
.tb-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--fg);
}
.tb-search input::placeholder { color: var(--muted); }
.tb-icon-group { display: flex; gap: 8px; }
.tb-icon {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  transition: border-color 0.15s;
}
.tb-icon:hover { border-color: var(--primary); }
.tb-icon .msr { font-size: 18px; color: var(--fg); }
.tb-dot {
  position: absolute;
  top: 7px;
  right: 9px;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
  border: 2px solid var(--card);
  box-sizing: content-box;
}
.tb-cta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
  transition: transform 0.15s, box-shadow 0.2s;
}
.tb-cta:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35); }
.tb-cta .msr { font-size: 16px; }

/* Content */
.content {
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 28px 32px;
}

/* ============ HERO ============ */
.hero {
  display: flex;
  gap: 16px;
  height: 460px;
}
.hero-main {
  flex: 1;
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  background: linear-gradient(200deg, #1a1a2e 0%, #16213e 45%, #3d1f1a 100%);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.hero-content {
  display: flex;
  gap: 16px;
  height: 100%;
  padding: 48px;
  align-items: stretch;
}
.hero-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  animation: heroFadeIn 0.45s ease-out;
}
@keyframes heroFadeIn {
  from { opacity: 0; transform: translateX(-12px); }
  to { opacity: 1; transform: translateX(0); }
}
@keyframes heroPosterIn {
  from { opacity: 0; transform: rotate(-4deg) translateY(8px); }
  to { opacity: 1; transform: rotate(-4deg) translateY(0); }
}
.hero-top { display: flex; flex-direction: column; gap: 16px; }
.tag-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.badge-now {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(255, 132, 0, 0.13);
  border: 1px solid rgba(255, 132, 0, 0.4);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--primary);
}
.dot-now {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
  box-shadow: 0 0 8px var(--primary);
}
.badge-tag {
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: rgba(255, 255, 255, 0.8);
}
.hero-title {
  margin: 0;
  font-size: 64px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -1px;
  color: #fff;
}
.hero-en {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.5);
}
.hero-meta {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-top: 12px;
  flex-wrap: wrap;
}
.rating-block { display: flex; align-items: center; gap: 6px; }
.rating-block .star { color: var(--primary); font-size: 22px; }
.rating-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}
.rating-out {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.4);
}
.meta-sep {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}
.hero-desc {
  max-width: 580px;
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.7);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hero-cta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(255, 132, 0, 0.4);
  transition: transform 0.15s, box-shadow 0.2s;
}
.hero-cta:hover { transform: translateY(-1px); box-shadow: 0 10px 28px rgba(255, 132, 0, 0.5); }
.hero-cta .msr { font-size: 20px; }
.kbd-b {
  padding: 2px 7px;
  border-radius: 5px;
  background: rgba(17, 17, 17, 0.2);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}
.hero-ghost {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.hero-ghost:hover { background: rgba(255, 255, 255, 0.15); }
.hero-ghost .msr { font-size: 20px; }
.hero-actions :deep(.watchlist-heart.is-dark:not(.is-active)) {
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.08);
  box-shadow: none;
}
.hero-actions :deep(.watchlist-heart.is-dark:not(.is-active):hover:not(:disabled)) {
  background: rgba(255, 255, 255, 0.15);
  box-shadow: none;
}
.hero-icon {
  width: 48px;
  height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}
.hero-icon:hover { background: rgba(255, 255, 255, 0.15); }
.hero-icon.active { border-color: #ec4899; color: #fff; background: #ec4899; }

.hero-right {
  width: 460px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.hero-poster {
  width: 260px;
  height: 360px;
  border-radius: 16px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.15);
  transform: rotate(-4deg);
  box-shadow: -16px 24px 48px rgba(0, 0, 0, 0.6);
  background: linear-gradient(200deg, #C97B4A 0%, #7A3A20 50%, #1f1815 100%);
  animation: heroPosterIn 0.45s ease-out;
}
.poster-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.poster-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.3);
}
.poster-placeholder .msr { font-size: 80px; }

.hero-controls { display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.page-num {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-cur {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  color: #fff;
}
.page-line {
  width: 60px;
  height: 2px;
  background: rgba(255, 255, 255, 0.2);
}
.page-tot {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.4);
}
.arrow-group { display: flex; gap: 8px; }
.arrow-btn {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}
.arrow-btn:hover { background: rgba(255, 255, 255, 0.18); }
.arrow-btn.solid { background: #fff; color: #111; }
.arrow-btn.solid:hover { background: #f0f0f0; }
.dots { display: flex; align-items: center; gap: 6px; }
.dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  transition: width 0.25s, background 0.25s;
  cursor: pointer;
}
.dot:hover { background: rgba(255, 255, 255, 0.45); }
.dot.active {
  width: 24px;
  background: var(--primary);
}
.arrow-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.arrow-btn:disabled:hover { background: rgba(255, 255, 255, 0.1); }
.arrow-btn.solid:disabled:hover { background: #fff; }

.hero-side {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.up-next-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}
.up-next-head > span:first-child {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
}
.up-next-all {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg);
  cursor: pointer;
}
.up-next-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}
.up-next-card:hover {
  transform: translateY(-2px);
  border-color: var(--primary);
}
.un-poster {
  width: 56px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  padding: 4px;
}
.un-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.un-num {
  position: relative;
  z-index: 1;
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.8);
}
.un-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.un-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.un-sub {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.5px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.un-play { font-size: 20px; color: var(--muted); flex-shrink: 0; }

/* ============ NOW SHOWING ============ */
.ns-section { display: flex; flex-direction: column; gap: 20px; }
.ns-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}
.ns-title-block { display: flex; flex-direction: column; gap: 4px; }
.ns-title-row { display: flex; align-items: center; gap: 12px; }
.ns-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--fg);
}
.ns-count {
  padding: 3px 10px;
  background: rgba(255, 132, 0, 0.13);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
}
.ns-sub { margin: 0; font-size: 13px; color: var(--muted); }

.ns-tools { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ns-pills {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
}
.ns-pill {
  padding: 8px 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}
.ns-pill.active {
  background: var(--bg);
  color: var(--fg);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
.ns-sort {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  cursor: pointer;
  transition: border-color 0.15s;
}
.ns-sort:hover { border-color: var(--primary); }
.ns-sort .msr { font-size: 16px; }
.ns-arrows { display: flex; gap: 6px; }
.ns-arrow {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--fg);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}
.ns-arrow:hover { border-color: var(--primary); }
.ns-arrow .msr { font-size: 16px; }
.ns-arrow.dark { background: var(--fg); color: var(--card); border-color: var(--fg); }
.ns-arrow.dark:hover { opacity: 0.88; }

.ns-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  scroll-behavior: smooth;
}

.movie-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.2s;
}
.movie-card:hover { transform: translateY(-4px); }
.card-poster {
  position: relative;
  height: 380px;
  border-radius: 16px;
  overflow: hidden;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid var(--border);
}
.movie-card.featured .card-poster {
  border: 2px solid var(--primary);
  box-shadow: 0 16px 32px rgba(255, 132, 0, 0.25);
}
.card-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 1;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  position: relative;
  z-index: 1;
}
.card-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
}
.card-badge.primary {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.card-badge .dot-now { box-shadow: none; background: currentColor; }
.card-rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}
.card-rating .msr { font-size: 14px; color: var(--primary); }
.card-title-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  min-height: 54px;
  padding: 0 8px;
  text-align: center;
}
.card-title-below {
  width: 100%;
  margin: 0;
  color: var(--fg);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-eng-below {
  width: 100%;
  margin: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}
.card-meta { display: flex; flex-direction: column; gap: 4px; }
.card-genre-row { display: flex; align-items: center; gap: 6px; }
.genre-ico { font-size: 14px; color: var(--primary); }
.card-genre {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg);
}
.card-price-row { display: flex; align-items: flex-end; gap: 4px; }
.price-cur { font-family: var(--font-mono); font-size: 12px; font-weight: 700; color: var(--primary); }
.price-num { font-family: var(--font-mono); font-size: 20px; font-weight: 800; color: var(--fg); line-height: 1; }
.price-unit { font-size: 11px; color: var(--muted); margin-bottom: 2px; }
.card-cta {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border: none;
  border-radius: 999px;
  background: var(--fg);
  color: var(--card);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.card-cta:hover { opacity: 0.88; }
.card-cta .msr { font-size: 14px; }
.card-cta.primary { background: var(--primary); color: var(--primary-foreground); }

/* ============ TWO COL ============ */
.two-col { display: grid; grid-template-columns: 1fr 460px; gap: 24px; align-items: start; }

.cinema-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
}
.cp-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.cp-head-left { display: flex; flex-direction: column; gap: 4px; }
.cp-title-row { display: flex; align-items: center; gap: 10px; }
.cp-ico { color: var(--primary); font-size: 22px; }
.cp-title { margin: 0; font-size: 20px; font-weight: 700; color: var(--fg); }
.cp-sub { margin: 0; font-size: 12px; color: var(--muted); }
.cp-map {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 999px;
  background: var(--secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: background 0.15s;
}
.cp-map:hover { background: var(--border); }
.cp-map .msr { font-size: 14px; }

.cinema-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 12px;
  background: var(--bg);
  border: 1px dashed var(--border);
  border-radius: 14px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
.cinema-empty .msr { font-size: 24px; color: var(--primary); }
.cinema-empty p { margin: 0; }

.cinema-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transform: translate3d(0, 0, 0);
  transition:
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.28s ease,
    background-color 0.28s ease,
    box-shadow 0.28s ease;
}
.cinema-item:hover,
.cinema-item:focus-visible {
  transform: translate3d(6px, -3px, 0);
  border-color: rgba(255, 132, 0, 0.58);
  background: rgba(255, 132, 0, 0.055);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  outline: none;
}
.cinema-rank {
  width: 56px;
  height: 56px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  transition:
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.28s ease,
    background-color 0.28s ease,
    color 0.28s ease,
    box-shadow 0.28s ease;
}
.cinema-item:hover .cinema-rank,
.cinema-item:focus-visible .cinema-rank {
  transform: rotate(-6deg) scale(1.08);
  border-color: var(--primary);
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 8px 18px rgba(255, 132, 0, 0.24);
}
.cinema-body { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.cinema-name-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cinema-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--fg);
  transition: color 0.22s ease, transform 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}
.cinema-item:hover .cinema-name,
.cinema-item:focus-visible .cinema-name {
  color: var(--primary);
  transform: translateX(3px);
}
.cinema-tag {
  padding: 2px 8px;
  background: var(--secondary);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--fg);
}
@media (prefers-reduced-motion: reduce) {
  .cinema-item,
  .cinema-rank,
  .cinema-name {
    transition: none;
  }
  .cinema-item:hover,
  .cinema-item:focus-visible,
  .cinema-item:hover .cinema-rank,
  .cinema-item:focus-visible .cinema-rank,
  .cinema-item:hover .cinema-name,
  .cinema-item:focus-visible .cinema-name {
    transform: none;
  }
}
.cinema-sub { margin: 0; font-size: 12px; color: var(--muted); }
.cinema-meta { display: flex; gap: 16px; flex-wrap: wrap; }
.cm-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--fg);
}
.cm-item .msr { font-size: 14px; color: var(--muted); }
.cinema-features { display: flex; gap: 6px; padding-top: 4px; flex-wrap: wrap; }
.cinema-feature {
  padding: 4px 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  color: var(--fg);
}

/* Right column */
.main-col, .right-col { display: flex; flex-direction: column; gap: 24px; min-width: 0; }

.cs-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 24px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
}
.cs-head { display: flex; justify-content: space-between; align-items: center; }
.cs-title-row { display: flex; align-items: center; gap: 10px; }
.cs-ico { color: var(--primary); font-size: 20px; }
.cs-title { margin: 0; font-size: 18px; font-weight: 700; color: var(--fg); }
.cs-all { text-decoration: none; font-size: 12px; font-weight: 500; color: var(--muted); cursor: pointer; }
.cs-all:hover { color: var(--primary); }
.cs-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.cs-item:hover { background: var(--bg); }
.cs-date {
  width: 52px;
  height: 52px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cs-day { font-family: var(--font-mono); font-size: 16px; font-weight: 700; color: var(--fg); }
.cs-mon { font-family: var(--font-mono); font-size: 9px; font-weight: 500; color: var(--muted); }
.cs-poster {
  width: 44px;
  height: 56px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}
.cs-poster img { width: 100%; height: 100%; object-fit: cover; }
.cs-info { flex: 1; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.cs-name { font-size: 14px; font-weight: 600; color: var(--fg); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cs-genre { font-size: 11px; color: var(--muted); }
.cs-want {
  padding: 4px 10px;
  background: var(--secondary);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg);
  flex-shrink: 0;
}

/* ============ AI SECTION ============ */

.rec-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px;
  background: var(--card);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(168, 85, 247, 0.12);
}
.rec-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.rec-head-left { display: flex; flex-direction: column; gap: 8px; }
.rec-title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.rec-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, #A855F7 0%, #EC4899 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(168, 85, 247, 0.4);
}
.rec-icon-wrap .msr { font-size: 20px; }
.rec-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--fg); }
.rec-tag {
  padding: 4px 10px;
  background: rgba(168, 85, 247, 0.13);
  border: 1px solid rgba(168, 85, 247, 0.4);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #A855F7;
}
.rec-sub { margin: 0; font-size: 13px; color: var(--muted); }

.rec-controls { display: flex; align-items: center; gap: 8px; }
.rec-tabs {
  display: flex;
  gap: 2px;
  padding: 4px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
}
.rec-tab {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}
.rec-tab.active {
  background: var(--card);
  color: var(--fg);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
.rec-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.rec-refresh:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.rec-refresh:disabled { cursor: wait; opacity: 0.65; }
.rec-refresh .msr { font-size: 14px; }

.rec-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.rec-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.15s;
}
.rec-card:hover { transform: translateY(-3px); }
.rec-poster {
  position: relative;
  height: 200px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.rec-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rec-match {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 10px;
  background: rgba(168, 85, 247, 0.85);
  backdrop-filter: blur(4px);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: #fff;
}
.rec-info { display: flex; flex-direction: column; gap: 4px; padding: 0 4px; }
.rec-name { font-size: 14px; font-weight: 600; color: var(--fg); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}
.rec-meta .msr { font-size: 13px; color: var(--primary); }
.rec-meta .sep { color: var(--border); }
.rec-reason {
  display: -webkit-box;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.rec-empty {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 24px;
  border: 1.5px dashed var(--border);
  border-radius: 16px;
  background: var(--bg);
  text-align: center;
}
.rec-empty .msr { font-size: 40px; color: var(--muted); }
.rec-empty p { margin: 0; font-size: 14px; color: var(--muted); }
.rec-login-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.rec-login-btn:hover { opacity: 0.88; }

/* AI Card */
.ai-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1F1340 0%, #2D1B4E 50%, #1A0F2E 100%);
  border: 1px solid rgba(168, 85, 247, 0.4);
  box-shadow: 0 12px 32px rgba(168, 85, 247, 0.25);
  color: #fff;
}
.ai-head { display: flex; align-items: center; gap: 12px; }
.ai-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #A855F7 0%, #EC4899 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(168, 85, 247, 0.4);
}
.ai-icon-wrap .msr { font-size: 22px; color: #fff; }
.ai-title-block { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.ai-title { font-family: var(--font-mono); font-size: 16px; font-weight: 700; color: #fff; }
.ai-sub { font-size: 11px; color: rgba(255, 255, 255, 0.5); }
.ai-beta {
  padding: 3px 8px;
  background: rgba(168, 85, 247, 0.25);
  border: 1px solid #A855F7;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
}

.ai-profile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
}
.ai-section-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.5);
}
.profile-row { display: flex; justify-content: space-between; align-items: center; }
.profile-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}
.profile-label .msr { font-size: 14px; color: #C084FC; }
.profile-val {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.ai-tags-block { display: flex; flex-direction: column; gap: 10px; }
.ai-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.ai-tag {
  padding: 4px 10px;
  background: rgba(168, 85, 247, 0.18);
  border: 1px solid rgba(168, 85, 247, 0.35);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  color: #fff;
}

.ai-line { height: 1px; background: rgba(255, 255, 255, 0.1); }

.ai-insight { display: flex; flex-direction: column; gap: 8px; }
.ai-insight-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.5);
}
.insight-ico { font-size: 12px; color: #C084FC; }
.ai-insight-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.85);
}

.ai-cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 18px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #A855F7 0%, #D946EF 50%, #EC4899 100%);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(168, 85, 247, 0.4);
  transition: transform 0.15s, box-shadow 0.2s;
}
.ai-cta:hover { transform: translateY(-1px); box-shadow: 0 10px 22px rgba(168, 85, 247, 0.55); }
.ai-cta .msr { font-size: 18px; }

/* Page foot */
.page-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 4px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--muted);
}
.foot-links { display: flex; gap: 16px; }
.foot-links span { cursor: pointer; transition: color 0.15s; }
.foot-links span:hover { color: var(--primary); }

/* Responsive */
@media (max-width: 1440px) {
  .hero-right { width: 380px; }
  .hero-title { font-size: 52px; }
  .ns-grid { grid-template-columns: repeat(4, 1fr); }
}

@media (max-width: 1280px) {
  .hero { flex-direction: column; height: auto; }
  .hero-main { min-height: 460px; }
  .hero-side { width: 100%; }
  .ns-grid { grid-template-columns: repeat(3, 1fr); }
  .two-col { grid-template-columns: 1fr; }
}

@media (max-width: 1024px) {
  .ns-grid, .rec-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-content { padding: 32px; }
  .hero-title { font-size: 42px; }
  .top-bar { gap: 12px; padding: 14px 20px; }
  .content { padding: 20px; gap: 24px; }
}

@media (max-width: 640px) {
  .ns-grid, .rec-grid { grid-template-columns: 1fr 1fr; }
  .hero-right { display: none; }
  .hero-content { padding: 24px; }
  .hero-title { font-size: 32px; }
  .top-bar { flex-wrap: wrap; }
  .tb-search { order: 5; width: 100%; flex: 0 0 100%; }
}
</style>
