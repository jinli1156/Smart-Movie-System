<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <AppSidebar />

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- Top Bar -->
      <div class="top-bar">
        <div class="tb-pill">
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
          <button class="tb-icon" @click="tip('通知')">
            <MaterialIcon class="msr" name="notifications" />
            <span class="tb-dot"></span>
          </button>
          <button class="tb-icon" @click="tip('购物车')">
            <MaterialIcon class="msr" name="shopping_cart" />
          </button>
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
          v-if="heroMovie"
          @mouseenter="stopAutoRotate"
          @mouseleave="startAutoRotate"
        >
          <div class="hero-main">
            <div class="hero-content">
              <div class="hero-left" :key="`hl-${heroMovie.id}`">
                <div class="hero-top">
                  <div class="tag-row">
                    <span class="badge-now">
                      <span class="dot-now"></span>
                      <span>NOW SHOWING</span>
                    </span>
                    <span class="badge-tag">IMAX</span>
                    <span class="badge-tag">3D</span>
                    <span class="badge-tag">杜比全景声</span>
                  </div>
                  <h1 class="hero-title">{{ heroMovie.title }}</h1>
                  <p class="hero-en">{{ (heroMovie.englishTitle || heroMovie.title).toUpperCase() }}</p>
                  <div class="hero-meta">
                    <div class="rating-block">
                      <MaterialIcon class="msr star" name="star" />
                      <span class="rating-num">{{ heroMovie.rating?.toFixed(1) || '9.2' }}</span>
                      <span class="rating-out">/ 10</span>
                    </div>
                    <span class="meta-sep">{{ heroMovie.genre || '科幻 · 冒险' }}</span>
                    <span class="meta-sep">{{ heroMovie.duration }} 分钟</span>
                    <span class="meta-sep">{{ heroMovie.director || '丹尼斯·维伦纽瓦' }}</span>
                  </div>
                  <p class="hero-desc">
                    {{ heroMovie.description || '保罗·厄崔迪在弗里曼人的支持下,对那些摧毁了他的家族的人进行复仇,并在他对未来的预见中阻止一场可怕的命运。' }}
                  </p>
                </div>
                <div class="hero-actions">
                  <button class="hero-cta" @click="goToDetail(heroMovie.id)">
                    <MaterialIcon class="msr" name="play_arrow" />
                    <span>立即购票</span>
                    <span class="kbd-b">B</span>
                  </button>
                  <button class="hero-ghost" @click="tip('观看预告')">
                    <MaterialIcon class="msr" name="play_circle" />
                    <span>观看预告</span>
                  </button>
                  <WatchlistHeartButton
                    size="large"
                    variant="dark"
                    :active="Boolean(heroMovie && favoriteMovieIds.has(heroMovie.id))"
                    @click="heroMovie && toggleFavorite(heroMovie)"
                  />
                  <button class="hero-icon" @click="tip('分享')">
                    <MaterialIcon class="msr" name="share" />
                  </button>
                </div>
              </div>
              <div class="hero-right">
                <div class="hero-poster" :key="`hp-${heroMovie.id}`">
                  <img
                    v-if="heroMovie.poster"
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
          <aside class="hero-side">
            <div class="up-next-head">
              <span>UP NEXT</span>
              <span class="up-next-all">查看全部 →</span>
            </div>
            <div
              v-for="(m, i) in upNextMovies"
              :key="m.id"
              class="up-next-card"
              @click="goToDetail(m.id)"
            >
              <div class="un-poster" :style="{ background: upNextGradient(i) }">
                <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                <span class="un-num">{{ String(i + 2).padStart(2, '0') }}</span>
              </div>
              <div class="un-info">
                <span class="un-title">{{ m.title }}</span>
                <span class="un-sub">{{ (m.englishTitle || m.title).toUpperCase() }}</span>
              </div>
              <MaterialIcon class="msr un-play" name="play_arrow" />
            </div>
          </aside>
        </section>

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
                  @click="activeDay = p.key"
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
                <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                <div class="card-top">
                  <span class="card-badge" :class="{ primary: i === featuredIndex }">
                    <span class="dot-now"></span>
                    <span>{{ i === featuredIndex ? 'FEATURED' : 'HOT' }}</span>
                  </span>
                  <span class="card-rating">
                    <MaterialIcon class="msr" name="star" />
                    <span>{{ m.rating?.toFixed(1) || '8.5' }}</span>
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
                    <span class="price-cur">¥</span>
                    <span class="price-num">{{ priceFor(m) }}</span>
                    <span class="price-unit">起</span>
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
                <span>地图查看</span>
              </button>
            </div>
            <div
              v-for="(c, i) in cinemas"
              :key="c.id"
              class="cinema-item"
              :class="{ active: i === 0 }"
            >
              <div class="cinema-rank" :class="{ active: i === 0 }">{{ String(i + 1).padStart(2, '0') }}</div>
              <div class="cinema-body">
                <div class="cinema-name-row">
                  <span class="cinema-name">{{ c.name }}</span>
                  <span class="cinema-tag" v-if="c.tag">{{ c.tag }}</span>
                </div>
                <p class="cinema-sub">{{ c.area }} · 起价 ¥{{ c.startPrice }}</p>
                <div class="cinema-meta">
                  <span class="cm-item"><MaterialIcon class="msr" name="star" />{{ c.rating }}</span>
                  <span class="cm-item"><MaterialIcon class="msr" name="straighten" />{{ c.distance }}</span>
                  <span class="cm-item"><MaterialIcon class="msr" name="directions_walk" />{{ c.walkTime }}</span>
                </div>
                <div class="cinema-features">
                  <span v-for="f in c.features" :key="f" class="cinema-feature">{{ f }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Right column -->
          <div class="right-col">
            <!-- Coming Soon -->
            <div class="cs-panel">
              <div class="cs-head">
                <div class="cs-title-row">
                  <MaterialIcon class="msr cs-ico" name="event" />
                  <h3 class="cs-title">即将上映</h3>
                </div>
                <span class="cs-all" @click="loadComingSoon">全部 →</span>
              </div>
              <div
                v-for="m in comingSoonDisplay"
                :key="m.id"
                class="cs-item"
                @click="goToDetail(m.id)"
              >
                <div class="cs-date">
                  <span class="cs-day">{{ dayOf(m.releaseDate) }}</span>
                  <span class="cs-mon">{{ monthOf(m.releaseDate) }}</span>
                </div>
                <div class="cs-poster" :style="{ background: csGradient(m.id) }">
                  <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                </div>
                <div class="cs-info">
                  <span class="cs-name">{{ m.title }}</span>
                  <span class="cs-genre">{{ m.genre || '剧情' }}</span>
                </div>
                <span class="cs-want">想看</span>
              </div>
              <el-empty v-if="!loadingComing && comingSoon.length === 0" description="暂无影片" :image-size="60" />
            </div>

            <!-- Shortcuts -->
            <div class="shortcuts">
              <div class="sc-head">
                <div class="sc-title-row">
                  <MaterialIcon class="msr sc-ico" name="bolt" />
                  <h3 class="sc-title">快捷功能</h3>
                </div>
                <span class="sc-all" @click="tip('查看全部')">查看全部</span>
              </div>
              <div class="sc-grid">
                <button
                  v-for="s in shortcuts"
                  :key="s.key"
                  class="sc-item"
                  @click="onShortcut(s)"
                >
                  <MaterialIcon class="msr" :style="{ color: s.color }" :name="s.icon" />
                  <span class="sc-label">{{ s.label }}</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- ============ AI SECTION ============ -->
        <section class="ai-section">
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
                    @click="activeRecTab = t.key"
                  >
                    {{ t.label }}
                  </button>
                </div>
                <button class="rec-refresh" @click="loadRecommendations">
                  <MaterialIcon class="msr" name="refresh" />
                  <span>换一批</span>
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
                  <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                  <span class="rec-match">{{ m.matchScore || matchScore(m.id) }}% 匹配</span>
                </div>
                <div class="rec-info">
                  <span class="rec-name">{{ m.title }}</span>
                  <span class="rec-meta">
                    <MaterialIcon class="msr" name="star" />
                    <span>{{ m.rating?.toFixed(1) || '8.5' }}</span>
                    <span class="sep">·</span>
                    <span>{{ m.genre || '剧情' }}</span>
                  </span>
                  <span v-if="m.recommendationReason" class="rec-reason">
                    推荐《{{ m.title }}》：{{ m.recommendationReason }}。
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
                <span>AI 洞察</span>
              </div>
              <p class="ai-insight-text">
                {{ userStore.isLoggedIn
                  ? '你近期偏爱硬核科幻题材,平均观影时长 142 分钟,推荐你尝试本周新上映的《沙丘》系列。'
                  : '登录后,AI 将为你生成专属观影洞察,推荐最适合你的影片与影院。' }}
              </p>
            </div>
            <button class="ai-cta" @click="onAiChat">
              <MaterialIcon class="msr" name="chat" />
              <span>与 AI 观影助手对话</span>
            </button>
          </aside>
        </section>

        <div class="page-foot">
          <span>© 2026 CINEMA+ · 沪ICP 备 2024XXXXX 号</span>
          <div class="foot-links">
            <span @click="tip('帮助中心')">帮助中心</span>
            <span @click="tip('隐私政策')">隐私政策</span>
            <span @click="tip('用户协议')">用户协议</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 扫码取票对话框 -->
    <QRScanner v-model="showScanner" @success="handleScanSuccess" />
    <ColdStartDialog v-model="showColdStart" :movies="nowShowing" @complete="handleColdStartComplete" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { movieApi } from '@/api/movie'
import { cinemaApi } from '@/api/cinema'
import { recommendApi } from '@/api/recommend'
import request from '@/utils/request'
import type { Movie, Cinema, RecommendationProfile } from '@/types'
import QRScanner from '@/components/QRScanner.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import ColdStartDialog from '@/components/ColdStartDialog.vue'
import WatchlistHeartButton from '@/components/WatchlistHeartButton.vue'
import { useLocation } from '@/composables/useLocation'

const router = useRouter()
const userStore = useUserStore()
const { location, ensure: ensureLocation } = useLocation()

const searchKeyword = ref('')
const loadingNow = ref(false)
const loadingComing = ref(false)
const loadingRec = ref(false)
const showScanner = ref(false)
const showColdStart = ref(false)
const recommendationProfile = ref<RecommendationProfile | null>(null)
const favoriteMovieIds = ref(new Set<number>())

const demoNowShowing: Movie[] = [
  {
    id: 1001,
    title: '星际穿越',
    director: '克里斯托弗·诺兰',
    actors: '马修·麦康纳, 安妮·海瑟薇',
    genre: '科幻 / 剧情',
    duration: 169,
    releaseDate: '2026-05-21',
    description: '一支宇航员小队穿越虫洞,为人类寻找新的家园。在跨越时空的旅途中,他们面对着前所未有的抉择与情感冲击。',
    rating: 9.4
  },
  {
    id: 1002,
    title: '沙丘 第二部',
    director: '丹尼斯·维伦纽瓦',
    actors: '提莫西·查拉梅, 赞达亚',
    genre: '科幻 / 冒险',
    duration: 166,
    releaseDate: '2026-05-15',
    description: '保罗·厄崔迪联合弗瑞曼人,对抗那些毁灭他家族的人。',
    rating: 9.1
  },
  {
    id: 1003,
    title: '奥本海默',
    director: '克里斯托弗·诺兰',
    actors: '基里安·墨菲, 艾米莉·布朗特',
    genre: '传记 / 历史',
    duration: 180,
    releaseDate: '2026-05-10',
    description: '原子弹之父奥本海默的故事。',
    rating: 9.3
  },
  {
    id: 1004,
    title: '蜘蛛侠:平行宇宙',
    director: '华金·多斯·桑托斯',
    actors: '沙梅克·摩尔, 海莉·斯坦菲尔德',
    genre: '动画 / 动作',
    duration: 140,
    releaseDate: '2026-04-28',
    description: '迈尔斯·莫拉莱斯穿越多元宇宙的冒险。',
    rating: 9.0
  },
  {
    id: 1005,
    title: '宇宙探索编辑部',
    director: '孔大山',
    actors: '杨皓宇, 艾丽娅',
    genre: '剧情 / 喜剧',
    duration: 118,
    releaseDate: '2026-05-01',
    description: '一群追寻外星文明的人,在路上发现了人生意义。',
    rating: 8.8
  }
]

const demoComingSoon: Movie[] = [
  {
    id: 2001,
    title: '阿凡达 3',
    director: '詹姆斯·卡梅隆',
    actors: '萨姆·沃辛顿',
    genre: '科幻 / 冒险',
    duration: 192,
    releaseDate: '2026-06-18',
    description: '潘多拉星球的全新故事。'
  },
  {
    id: 2002,
    title: '哥斯拉大战金刚 2',
    director: '亚当·温加德',
    actors: '丽贝卡·豪尔',
    genre: '动作 / 科幻',
    duration: 115,
    releaseDate: '2026-06-04',
    description: '两大泰坦再度联手对抗未知威胁。'
  },
  {
    id: 2003,
    title: '寄生兽:灰色部队',
    director: '延尚昊',
    actors: '全昭旻',
    genre: '惊悚 / 科幻',
    duration: 130,
    releaseDate: '2026-06-12',
    description: '寄生生物入侵后的人类生存。'
  },
  {
    id: 2004,
    title: '疯狂动物城 2',
    director: '杰瑞德·布什',
    actors: '金妮弗·古德温',
    genre: '动画 / 喜剧',
    duration: 108,
    releaseDate: '2026-06-25',
    description: '朱迪和尼克的全新案件。'
  }
]

const nowShowing = ref<Movie[]>([...demoNowShowing])
const comingSoon = ref<Movie[]>([...demoComingSoon])
const recommendations = ref<Movie[]>([...demoNowShowing])
const activeDay = ref<'today' | 'tomorrow' | 'week' | 'all'>('today')
const activeRecTab = ref<'all' | 'scifi' | 'action' | 'drama'>('all')

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

const shortcuts = [
  { key: 'card', label: '会员卡', icon: 'card_membership', color: '#FF8400' },
  { key: 'coupon', label: '优惠券', icon: 'confirmation_number', color: '#A855F7' },
  { key: 'gift', label: '礼品卡', icon: 'card_giftcard', color: '#EC4899' },
  { key: 'snack', label: '卖品', icon: 'lunch_dining', color: '#0EA5E9' },
  { key: 'redeem', label: '兑换券码', icon: 'redeem', color: '#22C55E' },
  { key: 'support', label: '客服', icon: 'support_agent', color: '#F59E0B' }
]

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
  tag: string
  area: string
  startPrice: number
  rating: string
  distance: string
  distanceNum: number
  walkTime: string
  features: string[]
}

const featurePool: Record<number, string[]> = {
  0: ['IMAX 厅', '杜比全景声', 'CINITY'],
  1: ['激光厅', '4K 高清', '亲子厅'],
  2: ['IMAX 厅', 'VIP 厅', '巨幕厅'],
  3: ['CINITY', '4DX', '杜比全景声'],
  4: ['激光厅', 'VIP 厅', '巨幕厅']
}
const ratingPool = ['9.4', '9.1', '9.0', '8.9', '8.7']
const pricePool = [58, 52, 48, 55, 45]

const cinemas = computed<CinemaDisplayItem[]>(() => {
  const { lat: userLat, lng: userLng } = location.value
  const items: CinemaDisplayItem[] = allCinemas.value.map((c, i) => {
    let distanceNum = Infinity
    if (userLat != null && userLng != null && c.lat != null && c.lng != null) {
      distanceNum = haversine(userLat, userLng, Number(c.lat), Number(c.lng))
    }
    const distStr = distanceNum === Infinity ? '—' : distanceNum < 1000 ? `${Math.round(distanceNum)} m` : `${(distanceNum / 1000).toFixed(1)} km`
    const walkMin = distanceNum === Infinity ? '—' : distanceNum < 1000 ? `步行 ${Math.max(1, Math.round(distanceNum / 80))} 分` : `步行 ${Math.round(distanceNum / 80)} 分`
    const addr = c.address || ''
    const area = addr.includes('市') ? addr.split('市')[1]?.substring(0, 6) || addr.substring(0, 6) : addr.substring(0, 6)
    return {
      id: c.id,
      name: c.name,
      tag: ['IMAX', 'DOLBY', 'CINITY', '4DX', 'LUXE'][i % 5],
      area,
      startPrice: pricePool[i % pricePool.length],
      rating: ratingPool[i % ratingPool.length],
      distance: distStr,
      distanceNum,
      walkTime: walkMin,
      features: featurePool[i % 5]
    }
  })
  return items.sort((a, b) => a.distanceNum - b.distanceNum).slice(0, 5)
})

const nearestArea = computed(() => {
  if (cinemas.value.length === 0) return '附近'
  return cinemas.value[0].area
})

const heroCount = computed(() => Math.min(nowShowing.value.length, 5))

const heroMovie = computed(() => {
  if (nowShowing.value.length === 0) return null
  return nowShowing.value[currentHeroIndex.value] || nowShowing.value[0]
})

const upNextMovies = computed(() => {
  const list = nowShowing.value
  if (list.length <= 1) return []
  const arr: Movie[] = []
  for (let i = 1; i <= 3; i++) {
    const idx = (currentHeroIndex.value + i) % list.length
    if (idx === currentHeroIndex.value) break
    arr.push(list[idx])
  }
  return arr
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
const recDisplay = computed(() => recommendations.value.slice(0, 6))

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

const upNextPalette = [
  'linear-gradient(180deg, #1a3a5c 0%, #000000 100%)',
  'linear-gradient(180deg, #5c1a3a 0%, #000000 100%)',
  'linear-gradient(180deg, #3a5c1a 0%, #000000 100%)'
]
const upNextGradient = (i: number) => upNextPalette[i % upNextPalette.length]

const csGradients = [
  'linear-gradient(180deg, #1f3a1a 0%, #0a1f08 100%)',
  'linear-gradient(180deg, #3a1a1a 0%, #1f0808 100%)',
  'linear-gradient(180deg, #5c4a1a 0%, #2a200a 100%)',
  'linear-gradient(180deg, #1a5c4a 0%, #082a20 100%)'
]
const csGradient = (id: number) => csGradients[id % csGradients.length]

const priceFor = (m: Movie) => {
  const base = 38 + (m.id % 7) * 6
  return base
}

const matchScore = (id: number) => 80 + (id % 20)

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
    if (data && data.length > 0) nowShowing.value = data
  } catch (e) {
    console.error('加载正在热映失败:', e)
  } finally {
    loadingNow.value = false
  }
}

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
    if (data && data.length > 0) comingSoon.value = data
  } catch (e) {
    console.error('加载即将上映失败:', e)
  } finally {
    loadingComing.value = false
  }
}

const loadRecommendations = async () => {
  loadingRec.value = true
  try {
    if (userStore.isLoggedIn) {
      try {
        recommendationProfile.value = await recommendApi.profile()
        showColdStart.value = recommendationProfile.value.needsColdStart
        const r = await recommendApi.personal({
          limit: 6,
          latitude: location.value.lat ?? undefined,
          longitude: location.value.lng ?? undefined
        })
        if (r && r.length > 0) {
          recommendations.value = r
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
      params: { limit: 6 },
      silent: true
    })
    if (hot && hot.length > 0) recommendations.value = hot
  } catch (e) {
    console.error('加载推荐失败:', e)
  } finally {
    loadingRec.value = false
  }
}

const handleColdStartComplete = async () => {
  recommendationProfile.value = await recommendApi.profile()
  await loadRecommendations()
}

const goToDetail = (id: number) => {
  router.push(`/movie/${id}`)
}

const goToHotRanking = () => {
  router.push({ path: '/movies', query: { ranking: 'hot' } })
}

const onSearch = () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/movies', query: { q: kw } })
}

const onShortcut = (s: { label: string }) => {
  ElMessage.info(`${s.label} 功能敬请期待`)
}

const onAiChat = () => {
  window.dispatchEvent(new Event('open-movie-assistant'))
}

const loadFavorites = async () => {
  if (!userStore.isLoggedIn) return
  const list = await recommendApi.favorites().catch(() => [] as Movie[])
  favoriteMovieIds.value = new Set(list.map(movie => movie.id))
}

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

const tip = (label: string) => {
  ElMessage.info(`${label} 功能敬请期待`)
}

const handleScanSuccess = (ticketInfo: any) => {
  console.log('取票成功:', ticketInfo)
  ElMessage.success(`取票成功！${ticketInfo.movieTitle}`)
}

onMounted(async () => {
  await loadNowShowing()
  loadCinemas()
  loadComingSoon()
  loadRecommendations()
  loadFavorites()
  startAutoRotate()
  ensureLocation()
})

onBeforeUnmount(() => {
  stopAutoRotate()
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0&display=swap');

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
  letter-spacing: 2px;
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

.cinema-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}
.cinema-item:hover { transform: translateY(-2px); }
.cinema-item.active {
  background: rgba(255, 132, 0, 0.08);
  border-color: rgba(255, 132, 0, 0.4);
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
}
.cinema-rank.active {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
}
.cinema-body { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.cinema-name-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cinema-name { font-size: 15px; font-weight: 700; color: var(--fg); }
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
.cinema-item.active .cinema-tag { background: var(--primary); color: var(--primary-foreground); }
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
.right-col { display: flex; flex-direction: column; gap: 24px; }

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
.cs-all { font-size: 12px; font-weight: 500; color: var(--muted); cursor: pointer; }
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

.shortcuts {
  padding: 24px;
  background: linear-gradient(135deg, #111111 0%, #2a2a2a 100%);
  border-radius: 20px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sc-head { display: flex; justify-content: space-between; align-items: center; }
.sc-title-row { display: flex; align-items: center; gap: 10px; }
.sc-ico { color: var(--primary); font-size: 20px; }
.sc-title { margin: 0; font-size: 18px; font-weight: 700; color: #fff; }
.sc-all { font-size: 11px; font-weight: 500; color: rgba(255, 255, 255, 0.5); cursor: pointer; }
.sc-all:hover { color: #fff; }
.sc-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.sc-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.9);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.sc-item:hover { background: rgba(255, 255, 255, 0.1); }
.sc-item .msr { font-size: 24px; }

/* ============ AI SECTION ============ */
.ai-section { display: grid; grid-template-columns: 1fr 380px; gap: 24px; align-items: start; }

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
.rec-refresh:hover { border-color: var(--primary); color: var(--primary); }
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
  .hero-side { display: none; }
  .ns-grid { grid-template-columns: repeat(3, 1fr); }
  .two-col { grid-template-columns: 1fr; }
  .ai-section { grid-template-columns: 1fr; }
}

@media (max-width: 1024px) {
  .ns-grid, .rec-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-content { padding: 32px; }
  .hero-title { font-size: 42px; }
  .top-bar { gap: 12px; padding: 14px 20px; }
  .content { padding: 20px; gap: 24px; }
}

@media (max-width: 640px) {
  .ns-grid, .rec-grid, .sc-grid { grid-template-columns: 1fr 1fr; }
  .hero-right { display: none; }
  .hero-content { padding: 24px; }
  .hero-title { font-size: 32px; }
  .top-bar { flex-wrap: wrap; }
  .tb-search { order: 5; width: 100%; flex: 0 0 100%; }
}
</style>
