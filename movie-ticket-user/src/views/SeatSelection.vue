<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <aside class="sidebar">
      <div class="logo-block" @click="router.push('/home')">
        <div class="logo-mark">
          <img class="brand-logo-image" src="/xiangzhi-ticket.png" alt="响指电影" />
        </div>
        <div class="logo-text">
          <span class="brand brand-wordmark" role="img" aria-label="响指电影"></span>
          <span class="brand-sub">premium booking</span>
        </div>
      </div>

      <nav class="nav">
        <a
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: item.key === activeNav }"
          @click="onNavClick(item)"
        >
          <MaterialIcon class="msr" :name="item.icon" />
          <span class="nav-label">{{ item.label }}</span>
        </a>
      </nav>

      <div class="sidebar-spacer"></div>

      <div class="sidebar-footer" v-if="userStore.isLoggedIn">
        <div class="avatar" @click="router.push('/profile')">{{ avatarChar }}</div>
        <div class="user-info" @click="router.push('/profile')">
          <span class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</span>
          <span class="user-tier">GOLD · {{ Number(userStore.userInfo?.points || 0).toLocaleString() }} 积分</span>
        </div>
      </div>
      <div v-else class="sidebar-footer guest">
        <div class="avatar guest-avatar">
          <MaterialIcon class="msr" name="person" />
        </div>
        <div class="user-info">
          <span class="user-name">访客</span>
          <span class="user-tier">登录解锁会员权益</span>
        </div>
        <button class="footer-login" @click="router.push('/login')">登录</button>
      </div>
    </aside>

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- Top Bar / Breadcrumb -->
      <div class="top-bar">
        <div class="breadcrumb">
          <a class="bc-link" @click="router.push('/home')">首页</a>
          <span class="bc-sep">/</span>
          <a class="bc-link" @click="router.push('/movies')">影片</a>
          <span class="bc-sep">/</span>
          <a class="bc-link" @click="goBackToMovie">{{ showtime?.movieTitle || '影片' }}</a>
          <span class="bc-sep">/</span>
          <span class="bc-current">选座</span>
        </div>
        <div class="tb-actions">
          <button class="tb-pill" @click="router.back()">
            <MaterialIcon class="msr" name="arrow_back" />
            <span>返回</span>
          </button>

        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="page-loading">
        <div class="loading-spin">
          <MaterialIcon class="msr" name="progress_activity" />
        </div>
      </div>

      <!-- Content -->
      <div v-else class="content">
        <!-- ============ MOVIE HEADER ============ -->
        <section class="movie-header" v-if="showtime">
          <div class="mh-poster" :style="{ background: posterGradient }">
            <img v-if="showtime.moviePoster" v-poster :src="showtime.moviePoster" :alt="showtime.movieTitle" />
            <span v-else class="mh-poster-label">{{ posterLabel }}</span>
          </div>
          <div class="mh-info">
            <div class="mh-title-row">
              <h2 class="mh-title">{{ showtime.movieTitle }}</h2>
              <span class="mh-title-en">{{ posterLabel }}</span>
            </div>
            <div class="mh-badges">
              <span class="mh-badge">{{ hallFormatBadge }}</span>
              <span class="mh-badge">2D</span>
              <span class="mh-badge">中英字幕</span>
              <span class="mh-badge">限量</span>
            </div>
            <div class="mh-meta">
              <span class="mh-rating">
                <MaterialIcon class="msr" name="star" />
                <span class="mh-rating-num">{{ ratingDisplay }}</span>
              </span>
              <span class="mh-meta-text">{{ showtime.cinemaName }} · {{ showtime.hallName }} · {{ showtime.movieDuration || 120 }} 分钟</span>
            </div>
          </div>
          <div class="mh-stats">
            <span class="mh-stats-label">本场剩余</span>
            <div class="mh-stats-num">
              <span class="mh-stats-dot"></span>
              <span class="mh-stats-value">{{ availableCount }} / {{ totalCount }}</span>
            </div>
            <span class="mh-stats-sub">可选座位</span>
          </div>
        </section>

        <!-- ============ SEAT BOARD ============ -->
        <section class="seat-board">
          <!-- Screen -->
          <div class="screen-wrap">
            <div class="screen-bar"></div>
            <span class="screen-label">银幕 SCREEN</span>
          </div>

          <!-- Seat Map -->
          <div class="seat-map" v-if="seatStatus">
            <!-- Left row labels -->
            <div class="row-labels">
              <div v-for="r in totalRows" :key="`L${r}`" class="row-label">{{ rowLetter(r - 1) }}</div>
            </div>

            <!-- Seat rows -->
            <div class="seat-grid">
              <div v-for="r in totalRows" :key="`row-${r}`" class="seat-row">
                <span v-if="hallLayout.aisleAfterCols.includes(0)" class="aisle" aria-label="最左侧过道">过道</span>
                <template v-for="c in totalCols" :key="`s-${r}-${c}`">
                  <div
                    class="seat"
                    :aria-label="seatName(r, c)"
                    :aria-disabled="!isSeatEnabled(r, c) || occupiedSet.has(seatName(r, c))"
                    :class="seatClass(r, c)"
                    @click="toggleSeat(r, c)"
                    @mouseenter="onSeatHover(r, c, $event)"
                    @mouseleave="onSeatLeave"
                  >
                    <MaterialIcon v-if="isRecommended(r, c)" class="msr seat-glow" name="stars" />
                  </div>
                  <span v-if="hallLayout.aisleAfterCols.includes(c)" class="aisle" :aria-label="c === totalCols ? '最右侧过道' : `第 ${c} 列后过道`">过道</span>
                </template>
              </div>
            </div>

            <!-- Right row labels -->
            <div class="row-labels right">
              <div v-for="r in totalRows" :key="`R${r}`" class="row-label">{{ r }}</div>
            </div>
          </div>

          <!-- Hover tooltip -->
          <div
            v-if="hoverTooltip.show"
            class="seat-tooltip"
            :style="{ left: hoverTooltip.x + 'px', top: hoverTooltip.y + 'px' }"
          >
            <div class="st-row">
              <span class="st-name">{{ hoverTooltip.name }}</span>
              <span class="st-status" :class="hoverTooltip.statusClass">{{ hoverTooltip.status }}</span>
            </div>
            <div class="st-sub" v-if="hoverTooltip.price">{{ hoverTooltip.price }} · 经典区</div>
          </div>

          <!-- Bottom legend + shortcuts -->
          <div class="board-bottom">
            <div class="legend">
              <div class="legend-item"><span class="legend-box aisle-legend"></span><span>过道</span></div>
              <div class="legend-item"><span class="legend-box occupied"></span><span>停用</span></div>
              <div class="legend-item">
                <span class="legend-box available"></span>
                <span>可选</span>
              </div>
              <div class="legend-item">
                <span class="legend-box selected"></span>
                <span>已选</span>
              </div>
              <div class="legend-item">
                <span class="legend-box occupied"></span>
                <span>已售</span>
              </div>
              <div class="legend-item">
                <span class="legend-box couple">
                  <MaterialIcon class="msr" name="favorite" />
                </span>
                <span>情侣座</span>
              </div>
              <div class="legend-item">
                <span class="legend-box accessible">
                  <MaterialIcon class="msr" name="accessible" />
                </span>
                <span>无障碍</span>
              </div>
            </div>
            <div class="shortcuts">
              <span class="sc-title">键鼠快捷</span>
              <div class="sc-group">
                <div class="sc-item">
                  <span class="kbd">←</span>
                  <span class="kbd">→</span>
                  <span class="kbd">↑</span>
                  <span class="kbd">↓</span>
                  <span class="sc-label">移动</span>
                </div>
                <div class="sc-item">
                  <span class="kbd wide">Space</span>
                  <span class="sc-label">选择</span>
                </div>
                <div class="sc-item">
                  <span class="kbd wide">Enter</span>
                  <span class="sc-label">确认</span>
                </div>
                <div class="sc-item">
                  <span class="kbd wide">Esc</span>
                  <span class="sc-label">返回</span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- ============ AI PANEL ============ -->
      <aside class="ai-panel" v-if="!loading && showtime">
        <!-- Main AI Card -->
        <div class="ai-card">
          <div class="ai-rim"></div>
          <div class="ai-head">
            <div class="ai-icon">
              <MaterialIcon class="msr" name="stars" />
            </div>
            <div class="ai-title-block">
              <span class="ai-title">AI 智能选座助手</span>
              <span class="ai-sub">{{ analysisSource === 'model' ? `${modelName || '大模型'} 实时分析` : '座位算法 · 实时推荐' }}</span>
            </div>
            <span class="ai-tag">NEW</span>
          </div>

          <p class="ai-desc">基于你的偏好和影厅热度，AI 为你推荐最佳座位</p>

          <div class="rec-settings">
            <div class="rec-setting-row">
              <span class="rec-setting-label">购票人数</span>
              <div class="party-options">
                <button
                  v-for="size in 5"
                  :key="size"
                  :class="{ active: partySize === size }"
                  @click="changePartySize(size)"
                >{{ size }}</button>
              </div>
            </div>
            <div class="preference-options">
              <button
                v-for="option in preferenceOptions"
                :key="option.value"
                :class="{ active: seatPreference === option.value }"
                @click="changePreference(option.value)"
              >
                <MaterialIcon class="msr" :name="option.icon" />
                <span>{{ option.label }}</span>
              </button>
            </div>
            <div class="natural-preference">
              <div class="natural-input-wrap">
                <MaterialIcon class="msr" name="chat_bubble" />
                <textarea
                  v-model="naturalPreference"
                  maxlength="300"
                  rows="2"
                  placeholder="直接告诉 AI：两个人，想坐中后排，安静一点，不要太靠边…"
                  @keydown.enter.exact.prevent="submitNaturalPreference"
                ></textarea>
                <button :disabled="recommendationLoading || !naturalPreference.trim()" @click="submitNaturalPreference">
                  <MaterialIcon class="msr" name="auto_awesome" />
                  <span>分析</span>
                </button>
              </div>
              <div class="natural-examples">
                <button v-for="example in naturalExamples" :key="example" @click="applyNaturalExample(example)">{{ example }}</button>
              </div>
              <div v-if="preferenceSummary" class="preference-summary">
                <MaterialIcon class="msr" :name="analysisSource === 'model' ? 'psychology' : 'tune'" />
                <span>{{ preferenceSummary }}</span>
                <em>{{ analysisSource === 'model' ? '模型解析' : '算法解析' }}</em>
              </div>
            </div>
          </div>

          <div class="ai-sec-head">
            <div class="ai-sec-title">
              <MaterialIcon class="msr" name="psychology" />
              <span>AI 为你精选</span>
            </div>
            <div class="ai-sec-live">
              <span class="live-dot"></span>
              <span>{{ recommendationLoading ? '计算中…' : `${recommendations.length} 个推荐 · ${lastUpdatedLabel}` }}</span>
              <button title="立即重新分析" :disabled="recommendationLoading" @click="loadRecommendations()">
                <MaterialIcon class="msr" name="refresh" />
              </button>
            </div>
          </div>

          <div class="rec-list">
            <div
              v-for="(rec, i) in recommendations"
              :key="i"
              class="rec-card"
              :class="{ active: i === activeRecIdx }"
            >
              <div class="rec-head">
                <div class="rec-name">
                  <MaterialIcon class="msr" :name="rec.icon" />
                  <span>{{ rec.title }}</span>
                </div>
                <span class="rec-match" :class="{ solid: i === activeRecIdx }">{{ rec.match }}% 匹配</span>
              </div>
              <div class="rec-body">
                <div class="rec-preview">
                  <div v-for="row in 6" :key="`pr-${i}-${row}`" class="rec-pr-row">
                    <span
                      v-for="col in 10"
                      :key="`pc-${i}-${row}-${col}`"
                      class="rec-pr-cell"
                      :class="{ on: isPreviewSeat(rec, row, col) }"
                    ></span>
                  </div>
                </div>
                <div class="rec-info">
                  <div class="rec-seat">
                    <MaterialIcon class="msr" name="chair" />
                    <span>{{ rec.seatLabel }}</span>
                  </div>
                  <div class="rec-price">
                    <span class="rec-price-num">¥{{ rec.unitPrice }}</span>
                    <span class="rec-price-sub">× {{ rec.count }} 张</span>
                  </div>
                </div>
              </div>
              <div class="rec-tags">
                <span v-for="(t, j) in rec.tags" :key="j" class="rec-tag">{{ t }}</span>
              </div>
              <div class="reason-source" :class="rec.reasonSource">
                <MaterialIcon class="msr" :name="rec.reasonSource === 'model' ? 'auto_awesome' : 'functions'" />
                <span>{{ rec.reasonSource === 'model' ? '大模型分析理由' : '评分算法理由' }}</span>
              </div>
              <p class="rec-reason">{{ rec.modelReason || rec.reason }}</p>
              <div class="score-grid">
                <div v-for="score in scoreItems(rec)" :key="score.label" class="score-item">
                  <span>{{ score.label }}</span>
                  <strong>{{ score.value }}</strong>
                </div>
                <div class="score-item penalty" v-if="rec.singleSeatPenalty > 0">
                  <span>留单座扣分</span>
                  <strong>-{{ rec.singleSeatPenalty }}</strong>
                </div>
              </div>
              <button class="rec-btn" :class="{ primary: isRecommendationApplied(rec) }" @click="applyRecommendation(i)">
                <MaterialIcon v-if="isRecommendationApplied(rec)" class="msr" name="check_circle" />
                <span>{{ isRecommendationApplied(rec) ? '已选择该方案' : '一键选座' }}</span>
              </button>
            </div>
            <div v-if="!recommendationLoading && recommendations.length === 0" class="rec-empty">
              暂无满足 {{ partySize }} 人同排且不跨过道的连座，请调整人数或场次
            </div>
          </div>
        </div>

        <!-- Insight Card -->
        <div class="ai-insight">
          <div class="ins-head">
            <div class="ins-title">
              <MaterialIcon class="msr" name="trending_up" />
              <span>热度对比</span>
            </div>
            <span class="ins-tag">实时</span>
          </div>
          <div class="ins-grid">
            <div class="ins-item">
              <span class="ins-num">12</span>
              <span class="ins-lbl">今日预订</span>
            </div>
            <div class="ins-item hot">
              <span class="ins-num">4</span>
              <span class="ins-lbl">情侣座剩余</span>
            </div>
          </div>
          <div class="ins-alert">
            <MaterialIcon class="msr" name="bolt" />
            <span>热门时段，建议尽快下单锁定座位</span>
          </div>
        </div>

        <!-- Switch Manual -->
        <button class="switch-manual" @click="manualMode = !manualMode">
          <MaterialIcon class="msr" name="swap_horiz" />
          <span>{{ manualMode ? '切换 AI 选座' : '切换手动选座' }}</span>
        </button>
      </aside>
    </main>

    <el-dialog
      v-model="showRefundPolicy"
      width="520px"
      class="refund-policy-dialog"
      :show-close="false"
      :close-on-click-modal="false"
      align-center
    >
      <template #header>
        <div class="policy-title">
          <span class="policy-title-icon"><MaterialIcon class="msr" name="warning" /></span>
          <div>
            <h3>重要提示</h3>
            <p>购票前请仔细阅读退票规则</p>
          </div>
        </div>
      </template>

      <div class="policy-list">
        <div class="policy-item policy-ok">
          <MaterialIcon class="msr" name="check_circle" />
          <div><strong>开场前 24 小时外</strong><span>可免费退票，全额退款</span></div>
        </div>
        <div class="policy-item policy-warn">
          <MaterialIcon class="msr" name="bolt" />
          <div><strong>开场前 24 小时内</strong><span>可退票，但需扣除 10% 手续费</span></div>
        </div>
        <div class="policy-item policy-danger">
          <MaterialIcon class="msr" name="cancel" />
          <div><strong>开场前 30 分钟内</strong><span>不可退票，不可改签</span></div>
        </div>
      </div>

      <div class="policy-note">
        <strong>退款说明</strong>
        <span>退款将原路退回支付账户，预计 1–7 个工作日到账；退票成功后座位立即释放。</span>
      </div>

      <template #footer>
        <button class="policy-cancel" @click="cancelRefundPolicy">取消</button>
        <button class="policy-confirm" @click="proceedToOrderConfirm">
          <MaterialIcon class="msr" name="check" />
          我已阅读并同意
        </button>
      </template>
    </el-dialog>

    <!-- ============ BOTTOM CHECKOUT BAR ============ -->
    <div class="checkout-bar" v-if="selectedSeats.length > 0 && !loading">
      <div class="cb-left">
        <span class="cb-count">已选 {{ selectedSeats.length }} 个座位</span>
        <div class="cb-seats">
          <span v-for="s in selectedSeats" :key="s" class="cb-seat-tag">
            {{ s }}
            <MaterialIcon class="msr cb-seat-close" @click.stop="removeSeat(s)" name="close" />
          </span>
        </div>
      </div>
      <div class="cb-right">
        <div class="cb-total">
          <span class="cb-total-label">总价</span>
          <span class="cb-total-num">¥{{ totalPrice }}</span>
        </div>
        <button class="cb-confirm" @click="confirmOrder">
          <MaterialIcon class="msr" name="check" />
          <span>确认选座</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 选座页：展示真实布局与占座状态，支持人工选座和智能连座推荐，确认后进入下单。
 */
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showtimeApi } from '@/api/showtime'
import { parseHallSeatLayout } from '@/utils/seatLayout'
import { useUserStore } from '@/stores/user'
import type { SeatPreference, SeatRecommendation, Showtime, SeatStatus } from '@/types'
import { ElMessage } from 'element-plus/es/components/message/index'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const showtime = ref<Showtime | null>(null)
const seatStatus = ref<SeatStatus | null>(null)
const selectedSeats = ref<string[]>([])
const activeNav = ref('movies')
const activeRecIdx = ref(0)
const showRefundPolicy = ref(false)
const manualMode = ref(false)
const requestedPeople = Number(route.query.people)
const partySize = ref(Number.isInteger(requestedPeople) && requestedPeople >= 1 && requestedPeople <= 5 ? requestedPeople : 2)
const seatPreference = ref<SeatPreference>('CENTER')
const recommendations = ref<SeatRecommendation[]>([])
const recommendationLoading = ref(false)
const realtimeRefreshing = ref(false)
const naturalPreference = ref('')
const preferenceSummary = ref('中央视野')
const analysisSource = ref<'model' | 'algorithm'>('algorithm')
const modelName = ref('')
const availabilityToken = ref('')
const lastGeneratedAt = ref<Date | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | undefined
const cursor = reactive({ row: 1, col: 1 })

const preferenceOptions: Array<{ value: SeatPreference; label: string; icon: string }> = [
  { value: 'CENTER', label: '中央视野', icon: 'preview' },
  { value: 'QUIET', label: '靠后安静', icon: 'weekend' },
  { value: 'EASY_ACCESS', label: '方便进出', icon: 'meeting_room' },
  { value: 'COUPLE', label: '情侣', icon: 'favorite' },
  { value: 'ACCESSIBLE', label: '无障碍', icon: 'accessible' }
]

const naturalExamples = ['两人中后排安静些', '靠过道方便进出', '带轮椅家人观影']

const lastUpdatedLabel = computed(() => {
  if (!lastGeneratedAt.value) return '实时'
  return lastGeneratedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
})

const navItems = [
  { key: 'home', label: '首页', icon: 'home', route: '/home' },
  { key: 'movies', label: '影片', icon: 'movie', route: '/movies' },
  { key: 'cinemas', label: '影院', icon: 'theaters', route: '/cinemas' },
  { key: 'profile', label: '个人中心', icon: 'person', route: '/profile' },
  { key: 'orders', label: '订单', icon: 'receipt_long', route: '/orders' },
  { key: 'favorites', label: '想看', icon: 'favorite', route: '/favorites' },
]

const totalRows = computed(() => seatStatus.value?.totalRows || 10)
const totalCols = computed(() => seatStatus.value?.totalCols || 15)
const hallLayout = computed(() => parseHallSeatLayout(seatStatus.value?.seatLayout, totalRows.value, totalCols.value))
const isSeatEnabled = (r: number, c: number) => hallLayout.value.valid && hallLayout.value.seats[r - 1]?.[c - 1] === 1
const totalCount = computed(() => seatStatus.value?.totalSeats || totalRows.value * totalCols.value)
const availableCount = computed(() => {
  if (!seatStatus.value) return 0
  return seatStatus.value.totalSeats - seatStatus.value.occupiedSeats.length - selectedSeats.value.length
})

const occupiedSet = computed(() => new Set(seatStatus.value?.occupiedSeats || []))
const selectedSet = computed(() => new Set(selectedSeats.value))

const totalPrice = computed(() => {
  if (!showtime.value) return '0.00'
  return (showtime.value.basePrice * selectedSeats.value.length).toFixed(2)
})

const ratingDisplay = computed(() => {
  return '8.7'
})

const posterLabel = computed(() => {
  if (!showtime.value?.movieTitle) return 'MOVIE'
  const t = showtime.value.movieTitle.toUpperCase()
  return t.length > 8 ? t.substring(0, 8) : t
})

const posterGradient = computed(() => {
  const palette = [
    'linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%)',
    'linear-gradient(200deg, #5c2a1a 0%, #2a0f08 100%)',
    'linear-gradient(200deg, #5c1a4a 0%, #2a0820 100%)',
    'linear-gradient(200deg, #5c5a1a 0%, #2a280a 100%)',
    'linear-gradient(200deg, #1a5c4a 0%, #082a20 100%)'
  ]
  const id = showtime.value?.movieId || 1
  return palette[id % palette.length]
})

const hallFormatBadge = computed(() => {
  const name = showtime.value?.hallName || ''
  if (name.includes('IMAX')) return 'IMAX'
  if (name.includes('VIP')) return 'VIP'
  if (name.includes('杜比')) return 'DOLBY'
  return 'IMAX'
})

const rowLetter = (idx: number) => {
  return String.fromCharCode(65 + idx)
}

const seatName = (r: number, c: number) => `${r}排${c}座`

const seatClass = (r: number, c: number) => {
  if (!isSeatEnabled(r, c)) return 'disabled'
  const name = seatName(r, c)
  if (occupiedSet.value.has(name)) return 'occupied'
  if (selectedSet.value.has(name)) return 'selected'
  if (isRecommended(r, c)) return 'recommended'
  if (cursor.row === r && cursor.col === c) return 'cursor'
  return 'available'
}

const parseSeatCoordinate = (seat: string) => {
  const match = seat.match(/^(\d+)排(\d+)座$/)
  return match ? { row: Number(match[1]), col: Number(match[2]) } : null
}

/** 根据行列位置与过道判断座位是否为同排连续组合。 */
const isContinuousGroup = (seats: string[]) => {
  const coordinates = seats.map(parseSeatCoordinate)
  if (!coordinates.length || coordinates.some(c => !c || !isSeatEnabled(c.row, c.col))) return false
  const ordered = coordinates.filter((c): c is { row: number; col: number } => c !== null).sort((a, b) => a.col - b.col)
  return ordered.every((c, i) => c.row === ordered[0].row && (i === 0 ||
    (c.col === ordered[i - 1].col + 1 && !hallLayout.value.aisleAfterCols.includes(ordered[i - 1].col))))
}

// URL proposals are untrusted: only apply a complete group after fresh layout/occupancy checks.
const applyAssistantSeats = () => {
  const raw = route.query.assistantSeats
  if (typeof raw !== 'string' || !seatStatus.value || !showtime.value) return
  const seats = raw.split(',')
  const valid = isContinuousGroup(seats) && showtime.value.status === 0 &&
    new Date(showtime.value.startTime).getTime() >= Date.now() + 15 * 60_000 &&
    seats.length > 0 && seats.length <= 5 && new Set(seats).size === seats.length && seats.every(seat => {
      const coordinate = parseSeatCoordinate(seat)
      if (!coordinate || coordinate.row < 1 || coordinate.row > totalRows.value || coordinate.col < 1 || coordinate.col > totalCols.value) return false
      if (occupiedSet.value.has(seat)) return false
      return isSeatEnabled(coordinate.row, coordinate.col)
    })
  if (valid) {
    selectedSeats.value = seats
    partySize.value = seats.length
    ElMessage.success('已预选助手推荐座位，尚未锁座，请确认后提交')
  } else {
    selectedSeats.value = []
    ElMessage.warning('助手方案已失效或包含不可选座位，请重新选择')
  }
  const query = { ...route.query }
  delete query.assistantSeats
  void router.replace({ path: route.path, query })
}

const isPreviewSeat = (rec: SeatRecommendation, previewRow: number, previewCol: number) => {
  return rec.seats.some(seat => {
    const coordinate = parseSeatCoordinate(seat)
    if (!coordinate) return false
    const row = Math.min(6, Math.max(1, Math.ceil(coordinate.row / totalRows.value * 6)))
    const col = Math.min(10, Math.max(1, Math.ceil(coordinate.col / totalCols.value * 10)))
    return row === previewRow && col === previewCol
  })
}

const scoreItems = (rec: SeatRecommendation) => [
  { label: '视野中心度', value: rec.scores.visionCenter },
  { label: '黄金距离', value: rec.scores.goldenDistance },
  { label: '连座完整性', value: rec.scores.continuity },
  { label: '进出便利度', value: rec.scores.accessConvenience },
  { label: '偏好匹配', value: rec.scores.preference },
  { label: '实时可用性', value: rec.scores.realtimeAvailability }
]

const sameSeats = (left: string[], right: string[]) => {
  if (left.length !== right.length) return false
  const rightSet = new Set(right)
  return left.every(seat => rightSet.has(seat))
}

const isRecommendationApplied = (rec: SeatRecommendation) => sameSeats(selectedSeats.value, rec.seats)

const isRecommended = (r: number, c: number) => {
  if (manualMode.value) return false
  const active = recommendations.value[activeRecIdx.value]
  if (!active) return false
  return active.seats.includes(seatName(r, c))
}

const hoverTooltip = reactive({
  show: false,
  x: 0,
  y: 0,
  name: '',
  status: '',
  statusClass: '',
  price: ''
})

const onSeatHover = (r: number, c: number, e: MouseEvent) => {
  const name = seatName(r, c)
  let status = '可选'
  let statusClass = 'ok'
  if (!isSeatEnabled(r, c)) {
    status = '停用'
    statusClass = 'sold'
  } else if (occupiedSet.value.has(name)) {
    status = '已售'
    statusClass = 'sold'
  } else if (selectedSet.value.has(name)) {
    status = '已选'
    statusClass = 'sel'
  }
  hoverTooltip.show = true
  hoverTooltip.name = name
  hoverTooltip.status = status
  hoverTooltip.statusClass = statusClass
  hoverTooltip.price = showtime.value ? `¥${showtime.value.basePrice}` : ''
  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const boardRect = target.closest('.seat-board')?.getBoundingClientRect()
  if (boardRect) {
    hoverTooltip.x = rect.left - boardRect.left + rect.width / 2
    hoverTooltip.y = rect.top - boardRect.top - 14
  }
}

const onSeatLeave = () => {
  hoverTooltip.show = false
}

/** 切换座位选择或可售状态，并执行当前页面的座位限制检查。 */
const toggleSeat = (r: number, c: number) => {
  if (!seatStatus.value) return
  if (!isSeatEnabled(r, c)) return
  const name = seatName(r, c)
  if (occupiedSet.value.has(name)) {
    ElMessage.warning('该座位已售')
    return
  }
  const idx = selectedSeats.value.indexOf(name)
  if (idx > -1) {
    selectedSeats.value.splice(idx, 1)
  } else {
    if (selectedSeats.value.length >= 5) {
      ElMessage.warning('最多只能选择 5 个座位')
      return
    }
    selectedSeats.value.push(name)
  }
}

const removeSeat = (seat: string) => {
  const idx = selectedSeats.value.indexOf(seat)
  if (idx > -1) selectedSeats.value.splice(idx, 1)
}

let recommendationRequestId = 0
/** 按当前偏好和场景加载推荐结果，更新加载状态。 */
const loadRecommendations = async (showLoading = true) => {
  const id = Number(route.params.id)
  if (!id) return
  const requestId = ++recommendationRequestId
  recommendationLoading.value = true
  try {
    const result = await showtimeApi.getSmartSeatRecommendations(id, {
      people: partySize.value,
      preference: seatPreference.value,
      naturalLanguage: naturalPreference.value.trim() || undefined,
      limit: 3
    })
    if (requestId !== recommendationRequestId) return
    recommendations.value = result.recommendations.filter(rec => rec.seats.length === partySize.value && isContinuousGroup(rec.seats))
    seatPreference.value = result.interpretedPreference
    preferenceSummary.value = result.preferenceSummary
    analysisSource.value = result.analysisSource
    modelName.value = result.model || ''
    availabilityToken.value = result.availabilityToken
    lastGeneratedAt.value = result.generatedAt ? new Date(result.generatedAt) : new Date()
    if (activeRecIdx.value >= recommendations.value.length) activeRecIdx.value = 0
  } catch (error) {
    if (requestId !== recommendationRequestId) return
    console.error('加载智能选座推荐失败:', error)
    if (showLoading) ElMessage.error('智能选座推荐加载失败')
  } finally {
    if (requestId === recommendationRequestId) recommendationLoading.value = false
  }
}

const changePartySize = async (size: number) => {
  if (partySize.value === size) return
  partySize.value = size
  activeRecIdx.value = 0
  selectedSeats.value = []
  await loadRecommendations()
}

const changePreference = async (preference: SeatPreference) => {
  if (seatPreference.value === preference) return
  seatPreference.value = preference
  naturalPreference.value = ''
  analysisSource.value = 'algorithm'
  activeRecIdx.value = 0
  await loadRecommendations()
}

/** 提交自然语言偏好，获取结构化分析及座位推荐。 */
const submitNaturalPreference = async () => {
  if (!naturalPreference.value.trim()) return
  activeRecIdx.value = 0
  await loadRecommendations()
}

const applyNaturalExample = async (example: string) => {
  naturalPreference.value = example
  await submitNaturalPreference()
}

/** 将选中的推荐组合应用为当前已选座位。 */
const applyRecommendation = async (i: number) => {
  activeRecIdx.value = i
  const rec = recommendations.value[i]
  if (!rec) return
  const available = rec.seats.filter(s => !occupiedSet.value.has(s))
  if (available.length !== rec.seats.length || !isContinuousGroup(rec.seats)) {
    await refreshSeatRecommendations(true)
    return
  }
  selectedSeats.value = [...rec.seats]
  ElMessage.success(`已为你选择 ${rec.title}`)
}

/** 根据人数和偏好刷新智能选座方案。 */
const refreshSeatRecommendations = async (notifyOnReplacement = false) => {
  if (loading.value || recommendationLoading.value || realtimeRefreshing.value) return
  const id = Number(route.params.id)
  if (!id) return
  const previousActive = recommendations.value[activeRecIdx.value]
  const wasApplied = !!previousActive && isRecommendationApplied(previousActive)
  realtimeRefreshing.value = true
  try {
    const latestStatus = await showtimeApi.getSeatStatus(id)
    const latestOccupied = new Set(latestStatus.occupiedSeats)
    seatStatus.value = latestStatus
    const becameUnavailable = !!previousActive && (previousActive.seats.some(seat => latestOccupied.has(seat)) || !isContinuousGroup(previousActive.seats))
    if (becameUnavailable) {
      activeRecIdx.value = 0
      await loadRecommendations(false)
      recommendations.value = recommendations.value.filter(rec =>
        isContinuousGroup(rec.seats) && rec.seats.every(seat => !latestOccupied.has(seat))
      )
      if (wasApplied) {
        const replacement = recommendations.value[0]
        selectedSeats.value = replacement ? [...replacement.seats] : []
      } else {
        selectedSeats.value = selectedSeats.value.filter(seat => !latestOccupied.has(seat))
      }
      if (notifyOnReplacement || wasApplied) {
        ElMessage.warning(recommendations.value.length
          ? '原推荐座位已售，已自动生成并切换到下一组'
          : '原推荐座位已售，暂无满足条件的连续座位')
      }
    } else {
      selectedSeats.value = selectedSeats.value.filter(seat => !latestOccupied.has(seat))
      lastGeneratedAt.value = new Date()
    }
  } catch (error) {
    console.error('刷新座位推荐失败:', error)
  } finally {
    realtimeRefreshing.value = false
  }
}

const goBackToMovie = () => {
  if (showtime.value?.movieId) {
    router.push(`/movie/${showtime.value.movieId}`)
  } else {
    router.back()
  }
}

const onNavClick = (item: { key: string; route?: string }) => {
  activeNav.value = item.key
  if (item.route) router.push(item.route)
}

const avatarChar = computed(() => {
  const n = userStore.userInfo?.nickname || '用'
  return n.charAt(0).toUpperCase()
})


/** 检查已选座位和购票规则，再进入订单确认流程。 */
const confirmOrder = async () => {
  if (selectedSeats.value.length === 0) {
    ElMessage.warning('请至少选择一个座位')
    return
  }
  showRefundPolicy.value = true
}

const cancelRefundPolicy = () => {
  showRefundPolicy.value = false
  ElMessage.info('已取消下单')
}

/** 携带场次及已选座位跳转订单确认页。 */
const proceedToOrderConfirm = async () => {
  showRefundPolicy.value = false
  try {
    await router.push({
      name: 'OrderConfirm',
      query: {
        showtimeId: String(route.params.id),
        pricingPoints: route.query.pricingPoints,
        pricingCoupons: route.query.pricingCoupons,
        seats: selectedSeats.value.join(',')
      }
    })
  } catch (error) {
    console.error('进入订单确认页失败:', error)
    ElMessage.error('进入订单确认页失败，请重试')
  }
}

const handleKey = (e: KeyboardEvent) => {
  if (!seatStatus.value) return
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return
  const rows = totalRows.value
  const cols = totalCols.value
  if (e.key === 'ArrowUp') {
    cursor.row = Math.max(1, cursor.row - 1)
    e.preventDefault()
  } else if (e.key === 'ArrowDown') {
    cursor.row = Math.min(rows, cursor.row + 1)
    e.preventDefault()
  } else if (e.key === 'ArrowLeft') {
    cursor.col = Math.max(1, cursor.col - 1)
    e.preventDefault()
  } else if (e.key === 'ArrowRight') {
    cursor.col = Math.min(cols, cursor.col + 1)
    e.preventDefault()
  } else if (e.key === ' ') {
    toggleSeat(cursor.row, cursor.col)
    e.preventDefault()
  } else if (e.key === 'Enter') {
    if (selectedSeats.value.length > 0) confirmOrder()
    e.preventDefault()
  } else if (e.key === 'Escape') {
    if (selectedSeats.value.length > 0) {
      selectedSeats.value = []
    } else {
      router.back()
    }
    e.preventDefault()
  }
}

/** 加载当前场次、影厅布局与占座状态，再恢复选择及推荐。 */
const loadData = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const [stData, ssData] = await Promise.all([
      showtimeApi.getById(id),
      showtimeApi.getSeatStatus(id)
    ])
    showtime.value = stData
    seatStatus.value = ssData
    cursor.row = Math.ceil(ssData.totalRows / 2)
    cursor.col = Math.ceil(ssData.totalCols / 2)
    applyAssistantSeats()
    await loadRecommendations(false)
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  refreshTimer = setInterval(() => refreshSeatRecommendations(), 5000)
  window.addEventListener('keydown', handleKey)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('keydown', handleKey)
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
  --sidebar: #E7E8E5;
  --sidebar-accent: #CBCCC9;
  --sidebar-fg: #666666;
  --sidebar-accent-fg: #18181b;
  --sidebar-border: #CBCCC9;
  --muted-bg: #F2F3F0;
  --ai-purple: #A855F7;
  --ai-pink: #EC4899;
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

/* ============ SIDEBAR ============ */
.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--sidebar);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}
.logo-block {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 90px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--sidebar-border);
  cursor: pointer;
}
.logo-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-mark .msr { font-size: 24px; }
.logo-text { display: flex; flex-direction: column; gap: 2px; }
.brand {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--sidebar-accent-fg);
  letter-spacing: 1.5px;
}
.brand-sub { font-size: 12px; color: var(--sidebar-fg); }

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.nav-item:hover { background: rgba(0, 0, 0, 0.04); }
.nav-item .msr { font-size: 24px; color: var(--sidebar-fg); }
.nav-label {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.nav-item.active { background: var(--primary); }
.nav-item.active .msr,
.nav-item.active .nav-label { color: var(--primary-foreground); font-weight: 600; }

.sidebar-spacer { flex: 1; }
.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-top: 1px solid var(--sidebar-border);
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400, #FF3F33);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.guest-avatar { background: var(--sidebar-accent); color: var(--sidebar-fg); }
.user-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; cursor: pointer; transition: opacity 0.15s; }
.user-info:hover { opacity: 0.8; }
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sidebar-accent-fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-tier { font-family: var(--font-mono); font-size: 12px; color: var(--sidebar-fg); }
.footer-login {
  padding: 6px 14px;
  border-radius: 8px;
  border: none;
  background: var(--primary);
  color: var(--primary-foreground);
  font-weight: 600;
  font-size: 12px;
  cursor: pointer;
}

/* ============ MAIN AREA ============ */
.main-area {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 380px;
  grid-template-rows: auto 1fr;
  min-width: 0;
}

.top-bar {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 32px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}
.breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.bc-link {
  color: var(--muted);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s;
}
.bc-link:hover { color: var(--fg); }
.bc-sep { color: var(--muted); }
.bc-current { color: var(--fg); font-weight: 700; }

.tb-actions { display: flex; align-items: center; gap: 8px; }
.tb-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  background: var(--card);
  border-radius: 999px;
  font-size: 13px;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.tb-pill:hover { border-color: var(--primary); color: var(--primary); }
.tb-pill .msr { font-size: 16px; }
.tb-icon {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.15s;
}
.tb-icon:hover { border-color: var(--primary); }
.tb-icon .msr { font-size: 18px; color: var(--muted); }

/* ============ CONTENT ============ */
.content {
  padding: 24px 24px 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}
.page-loading {
  grid-column: 1 / -1;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
}
.loading-spin {
  width: 56px;
  height: 56px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: spin 1.2s linear infinite;
}
.loading-spin .msr { font-size: 28px; color: var(--primary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ============ MOVIE HEADER ============ */
.movie-header {
  display: flex;
  gap: 20px;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  align-items: stretch;
}
.mh-poster {
  width: 84px;
  height: 112px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  padding: 8px;
  overflow: hidden;
  position: relative;
}
.mh-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mh-poster-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.8);
  letter-spacing: 1.5px;
  position: relative;
  z-index: 1;
}
.mh-info { flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.mh-title-row { display: flex; align-items: center; gap: 10px; }
.mh-title { font-size: 22px; font-weight: 700; margin: 0; color: var(--fg); }
.mh-title-en {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 2px;
}
.mh-badges { display: flex; gap: 6px; flex-wrap: wrap; }
.mh-badge {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--muted-bg);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--muted);
}
.mh-meta { display: flex; align-items: center; gap: 14px; }
.mh-rating { display: flex; align-items: center; gap: 4px; }
.mh-rating .msr { font-size: 14px; color: var(--primary); }
.mh-rating-num { font-family: var(--font-mono); font-size: 13px; font-weight: 700; color: var(--fg); }
.mh-meta-text { font-size: 12px; color: var(--muted); }

.mh-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.mh-stats-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 1px;
}
.mh-stats-num { display: flex; align-items: center; gap: 6px; }
.mh-stats-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
}
.mh-stats-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--fg);
}
.mh-stats-sub { font-size: 11px; color: var(--muted); }

/* ============ SEAT BOARD ============ */
.seat-board {
  position: relative;
  border-radius: 20px;
  background: linear-gradient(180deg, #1a1a1f 0%, #0d0d11 100%);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  color: #fff;
  flex: 1;
  min-height: 540px;
}

.screen-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.screen-bar {
  width: 100%;
  max-width: 720px;
  height: 36px;
  border-radius: 40px 40px 6px 6px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8) 0%, rgba(255, 255, 255, 0.4) 50%, rgba(255, 255, 255, 0.08) 100%);
  box-shadow: 0 20px 60px rgba(255, 132, 0, 0.4);
}
.screen-label {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 6px;
}

.seat-map {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: 14px;
  padding: 8px 0;
}
.row-labels {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 20px;
}
.row-label {
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
}

.seat-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.seat-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.seat {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  position: relative;
}
.seat.available:hover {
  background: rgba(255, 132, 0, 0.4);
  border-color: var(--primary);
  transform: scale(1.1);
}
.seat.selected {
  background: var(--primary);
  border-color: var(--primary);
  box-shadow: 0 0 12px rgba(255, 132, 0, 0.6);
}
.seat.occupied {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.05);
  cursor: not-allowed;
}
.seat.disabled {
  opacity: 0.25;
  cursor: not-allowed;
  background: repeating-linear-gradient(45deg, transparent, transparent 4px, #aaa 5px, transparent 6px);
}
.seat.recommended {
  background: rgba(168, 85, 247, 0.3);
  border-color: rgba(168, 85, 247, 0.8);
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.4);
}
.seat.recommended:hover {
  background: rgba(168, 85, 247, 0.5);
  transform: scale(1.1);
}
.seat.cursor {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
.seat-glow {
  font-size: 12px;
  color: #fff;
}
.aisle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  flex-shrink: 0;
  height: 24px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.6);
  border-inline: 1px dashed rgba(255, 255, 255, 0.2);
}
.aisle-legend { border-inline: 1px dashed rgba(255, 255, 255, 0.6); }

.seat-tooltip {
  position: absolute;
  transform: translate(-50%, -100%);
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  z-index: 10;
  pointer-events: none;
  white-space: nowrap;
}
.seat-tooltip::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 6px solid #fff;
}
.st-row { display: flex; align-items: center; gap: 8px; }
.st-name { font-size: 13px; font-weight: 700; color: var(--fg); }
.st-status {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}
.st-status.ok { background: rgba(255, 132, 0, 0.15); color: var(--primary); }
.st-status.sel { background: rgba(255, 132, 0, 0.9); color: #fff; }
.st-status.sold { background: rgba(102, 102, 102, 0.15); color: var(--muted); }
.st-sub { font-size: 11px; color: var(--muted); margin-top: 2px; }

.board-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-top: auto;
}
.legend {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  font-weight: 500;
}
.legend-box {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.legend-box.available { background: rgba(255, 255, 255, 0.15); border: 1px solid rgba(255, 255, 255, 0.1); }
.legend-box.selected { background: var(--primary); box-shadow: 0 0 6px var(--primary); }
.legend-box.occupied { background: rgba(255, 255, 255, 0.08); }
.legend-box.couple { background: rgba(255, 255, 255, 0.15); border: 1px solid rgba(255, 255, 255, 0.1); }
.legend-box.couple .msr { font-size: 10px; color: #FF6B9D; }
.legend-box.accessible { background: rgba(255, 255, 255, 0.12); border: 1px solid rgba(255, 255, 255, 0.25); }
.legend-box.accessible .msr { font-size: 10px; color: rgba(255, 255, 255, 0.7); }

.shortcuts { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.sc-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 2px;
}
.sc-group { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.sc-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.sc-item .kbd, .kbd {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  min-width: 18px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: #fff;
}
.kbd.wide { padding: 2px 8px; }
.sc-label { font-size: 11px; color: rgba(255, 255, 255, 0.7); font-weight: 500; }

/* ============ AI PANEL ============ */
.ai-panel {
  grid-column: 2;
  grid-row: 2;
  padding: 24px 24px 24px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.ai-card {
  position: relative;
  border-radius: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: -8px 24px 48px rgba(0, 0, 0, 0.08);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}
.ai-rim {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #FF8400 0%, #A855F7 50%, #EC4899 100%);
}
.ai-head { display: flex; align-items: center; gap: 12px; }
.ai-icon {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400, #EC4899);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(168, 85, 247, 0.25);
  flex-shrink: 0;
}
.ai-icon .msr { font-size: 22px; color: #fff; }
.ai-title-block { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.ai-title { font-size: 15px; font-weight: 600; color: var(--fg); }
.ai-sub { font-family: var(--font-mono); font-size: 11px; color: var(--muted); }
.ai-tag {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--ai-purple);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.ai-desc { font-size: 12px; color: var(--muted); line-height: 1.6; margin: 0; }

.rec-settings {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  background: var(--muted-bg);
  border: 1px solid var(--border);
}
.rec-setting-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.rec-setting-label { font-size: 11px; font-weight: 600; color: var(--muted); }
.party-options { display: flex; gap: 4px; }
.party-options button {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  cursor: pointer;
}
.party-options button.active {
  border-color: var(--ai-purple);
  background: var(--ai-purple);
  color: #fff;
}
.preference-options { display: flex; flex-wrap: wrap; gap: 5px; }
.preference-options button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 7px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--muted);
  font-size: 10px;
  cursor: pointer;
}
.preference-options button .msr { font-size: 12px; }
.preference-options button.active {
  border-color: rgba(168, 85, 247, 0.45);
  background: rgba(168, 85, 247, 0.1);
  color: var(--ai-purple);
  font-weight: 600;
}
.natural-preference { display: flex; flex-direction: column; gap: 7px; }
.natural-input-wrap {
  display: grid;
  grid-template-columns: 20px 1fr auto;
  align-items: center;
  gap: 7px;
  padding: 8px;
  border: 1px solid rgba(168,85,247,.28);
  border-radius: 10px;
  background: var(--card);
}
.natural-input-wrap > .msr { color: var(--ai-purple); font-size: 16px; }
.natural-input-wrap textarea {
  min-width: 0;
  resize: none;
  outline: none;
  border: 0;
  color: var(--fg);
  background: transparent;
  font: inherit;
  font-size: 10px;
  line-height: 1.5;
}
.natural-input-wrap textarea::placeholder { color: #9ca19e; }
.natural-input-wrap button {
  height: 31px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: var(--ai-purple);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}
.natural-input-wrap button:disabled { opacity: .45; cursor: not-allowed; }
.natural-input-wrap button .msr { font-size: 13px; }
.natural-examples { display: flex; gap: 5px; overflow-x: auto; scrollbar-width: none; }
.natural-examples button {
  flex: 0 0 auto;
  padding: 4px 7px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--muted);
  background: var(--card);
  font-size: 9px;
  cursor: pointer;
}
.natural-examples button:hover { border-color: var(--ai-purple); color: var(--ai-purple); }
.preference-summary {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 8px;
  border-radius: 8px;
  color: #6f36a9;
  background: rgba(168,85,247,.08);
  font-size: 9px;
}
.preference-summary .msr { font-size: 13px; }
.preference-summary span { flex: 1; line-height: 1.4; }
.preference-summary em { padding: 2px 5px; border-radius: 5px; background: rgba(168,85,247,.13); font-style: normal; white-space: nowrap; }

.ai-sec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-sec-title { display: flex; align-items: center; gap: 6px; }
.ai-sec-title .msr { font-size: 14px; color: var(--ai-purple); }
.ai-sec-title span:last-child {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}
.ai-sec-live { display: flex; align-items: center; gap: 5px; color: var(--ai-purple); font-family: var(--font-mono); font-size: 9px; }
.live-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 7px rgba(34,197,94,.7); }
.ai-sec-live button { width: 23px; height: 23px; display: grid; place-items: center; border: 0; border-radius: 7px; color: var(--ai-purple); background: rgba(168,85,247,.1); cursor: pointer; }
.ai-sec-live button:disabled { opacity: .45; }
.ai-sec-live button .msr { font-size: 13px; }
.ai-sec-count {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(168, 85, 247, 0.1);
  color: var(--ai-purple);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
}

.rec-list { display: flex; flex-direction: column; gap: 10px; }
.rec-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-radius: 14px;
  background: var(--card);
  border: 1px solid var(--border);
  transition: all 0.15s;
}
.rec-card.active {
  background: rgba(168, 85, 247, 0.06);
  border: 2px solid var(--ai-purple);
  padding: 13px;
}
.rec-head { display: flex; justify-content: space-between; align-items: center; }
.rec-name { display: flex; align-items: center; gap: 6px; }
.rec-name .msr { font-size: 16px; color: var(--fg); }
.rec-card.active .rec-name .msr { color: var(--ai-purple); }
.rec-name span:last-child {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}
.rec-match {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(168, 85, 247, 0.1);
  color: var(--ai-purple);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}
.rec-match.solid {
  background: var(--ai-purple);
  color: #fff;
}

.rec-body { display: flex; gap: 12px; align-items: stretch; }
.rec-preview {
  width: 88px;
  height: 64px;
  border-radius: 6px;
  background: #0d0d11;
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 5px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 3px;
  flex-shrink: 0;
}
.rec-pr-row { display: flex; gap: 1px; justify-content: center; }
.rec-pr-cell {
  width: 6px;
  height: 5px;
  border-radius: 1px;
  background: rgba(255, 255, 255, 0.15);
}
.rec-pr-cell.on { background: var(--ai-purple); }

.rec-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}
.rec-seat { display: flex; align-items: center; gap: 4px; }
.rec-seat .msr { font-size: 14px; color: var(--muted); }
.rec-seat span:last-child {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}
.rec-price { display: flex; align-items: baseline; gap: 4px; }
.rec-price-num { font-size: 18px; font-weight: 700; color: var(--primary); }
.rec-price-sub { font-family: var(--font-mono); font-size: 11px; color: var(--muted); }

.rec-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.rec-tag {
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--muted-bg);
  font-size: 10px;
  color: var(--muted);
}
.rec-reason {
  margin: 0;
  font-size: 10px;
  line-height: 1.5;
  color: var(--muted);
}
.reason-source { display: flex; align-items: center; gap: 4px; color: #727873; font-size: 9px; font-weight: 700; }
.reason-source .msr { font-size: 12px; }
.reason-source.model { color: var(--ai-purple); }
.score-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 10px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(168, 85, 247, 0.05);
}
.score-item { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.score-item span { font-size: 9px; color: var(--muted); }
.score-item strong { font-family: var(--font-mono); font-size: 10px; color: var(--fg); }
.score-item.penalty strong { color: #dc2626; }
.rec-empty {
  padding: 20px 12px;
  border: 1px dashed var(--border);
  border-radius: 10px;
  color: var(--muted);
  font-size: 11px;
  text-align: center;
}

.rec-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.rec-btn:hover { border-color: var(--ai-purple); color: var(--ai-purple); }
.rec-btn.primary {
  background: var(--ai-purple);
  border-color: var(--ai-purple);
  color: #fff;
}
.rec-btn.primary:hover { background: #9333EA; }
.rec-btn .msr { font-size: 14px; }

/* AI Insight */
.ai-insight {
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.12) 0%, rgba(236, 72, 153, 0.03) 100%);
  border: 1px solid rgba(168, 85, 247, 0.25);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ins-head { display: flex; justify-content: space-between; align-items: center; }
.ins-title { display: flex; align-items: center; gap: 6px; }
.ins-title .msr { font-size: 14px; color: var(--ai-purple); }
.ins-title span:last-child {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}
.ins-tag {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid rgba(168, 85, 247, 0.25);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--ai-purple);
}
.ins-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.ins-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ins-item.hot { border-color: rgba(168, 85, 247, 0.24); }
.ins-num {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--fg);
}
.ins-item.hot .ins-num { color: var(--ai-purple); }
.ins-lbl { font-size: 11px; color: var(--muted); }
.ins-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 132, 0, 0.08);
}
.ins-alert .msr { font-size: 12px; color: var(--primary); }
.ins-alert span:last-child { font-size: 11px; color: var(--fg); font-weight: 500; }

.switch-manual {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  background: transparent;
  border: none;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s;
}
.switch-manual:hover { color: var(--primary); }
.switch-manual .msr { font-size: 14px; }

/* ============ REFUND POLICY DIALOG ============ */
:global(.refund-policy-dialog) {
  --el-dialog-border-radius: 18px;
  overflow: hidden;
  border: 1px solid #CBCCC9;
  box-shadow: 0 24px 70px rgba(17, 17, 17, 0.2);
}
:global(.refund-policy-dialog .el-dialog__header) { margin: 0; padding: 22px 24px 16px; }
:global(.refund-policy-dialog .el-dialog__body) { padding: 0 24px 20px; }
:global(.refund-policy-dialog .el-dialog__footer) { padding: 16px 24px 22px; border-top: 1px solid #E7E8E5; }
.policy-title { display: flex; align-items: center; gap: 12px; }
.policy-title-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #B85F00;
  background: rgba(255, 132, 0, 0.12);
}
.policy-title-icon .msr { font-size: 23px; }
.policy-title h3 { margin: 0 0 3px; color: #111; font-size: 18px; }
.policy-title p { margin: 0; color: #666; font-size: 12px; }
.policy-list { display: flex; flex-direction: column; gap: 10px; }
.policy-item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 14px;
  border: 1px solid #E7E8E5;
  border-radius: 11px;
  background: #F8F9F6;
}
.policy-item > .msr { margin-top: 1px; font-size: 19px; }
.policy-item > div { display: flex; flex-direction: column; gap: 3px; }
.policy-item strong { color: #111; font-size: 13px; }
.policy-item span { color: #666; font-size: 12px; }
.policy-ok > .msr { color: #16A34A; }
.policy-warn > .msr { color: #D97706; }
.policy-danger > .msr { color: #DC2626; }
.policy-note {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #F2F3F0;
}
.policy-note strong { color: #111; font-size: 12px; }
.policy-note span { color: #666; font-size: 11px; line-height: 1.6; }
.policy-cancel,
.policy-confirm { padding: 10px 17px; border-radius: 9px; font: 700 13px/1 var(--font-sans); cursor: pointer; }
.policy-cancel { margin-right: 8px; border: 1px solid #CBCCC9; background: #FFF; color: #333; }
.policy-confirm { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #FF8400; background: #FF8400; color: #111; }
.policy-confirm .msr { font-size: 16px; }
.policy-confirm:hover { box-shadow: 0 6px 16px rgba(255, 132, 0, 0.22); }

/* ============ CHECKOUT BAR ============ */
.checkout-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  width: min(960px, calc(100vw - 320px));
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 14px 20px;
  border-radius: 16px;
  background: rgba(17, 17, 17, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.4);
  z-index: 100;
  color: #fff;
}
.cb-left { display: flex; align-items: center; gap: 16px; flex: 1; min-width: 0; }
.cb-count {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 1px;
  white-space: nowrap;
}
.cb-seats { display: flex; gap: 6px; flex-wrap: wrap; }
.cb-seat-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 4px 10px;
  border-radius: 6px;
  background: rgba(255, 132, 0, 0.15);
  border: 1px solid var(--primary);
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
}
.cb-seat-close {
  font-size: 14px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s;
}
.cb-seat-close:hover { opacity: 1; }

.cb-right { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.cb-total { display: flex; align-items: baseline; gap: 6px; }
.cb-total-label { font-size: 12px; color: rgba(255, 255, 255, 0.6); }
.cb-total-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--primary);
}
.cb-confirm {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  border-radius: 10px;
  background: var(--primary);
  color: var(--primary-foreground);
  border: none;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.15s;
}
.cb-confirm:hover { transform: translateY(-1px); }
.cb-confirm .msr { font-size: 18px; }
</style>
