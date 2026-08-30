<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <AppSidebar :show-quick-access="false" />

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- TOP BAR with breadcrumb -->
      <header class="top-bar">
        <div class="breadcrumb">
          <span class="bc-item" @click="router.push('/home')">首页</span>
          <span class="bc-sep">/</span>
          <template v-if="contextMovie">
            <span class="bc-item" @click="router.push('/movies')">电影</span>
            <span class="bc-sep">/</span>
            <span class="bc-item" @click="router.push(`/movie/${contextMovie.id}`)">{{ contextMovie.title }}</span>
            <span class="bc-sep">/</span>
            <span class="bc-item active">选择影院</span>
          </template>
          <template v-else>
            <span class="bc-item active">影院</span>
          </template>
        </div>
        <div class="tb-right">
          <div class="tb-pill">
            <MaterialIcon class="msr tb-pill-ico primary" name="location_on" />
            <span class="tb-pill-text">{{ sharedLocation.city || sharedLocation.label }}</span>
            <MaterialIcon class="msr tb-pill-caret" name="keyboard_arrow_down" />
          </div>
          <div class="tb-search">
            <MaterialIcon class="msr" name="search" />
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索影院 / 影片"
              @keyup.enter="onSearch"
            />

          </div>
          <button class="tb-icon" @click="tip('通知')">
            <MaterialIcon class="msr" name="notifications" />
          </button>
          <div class="tb-avatar">Z</div>
        </div>
      </header>

      <!-- CONTENT -->
      <div class="content">
        <!-- ============ MOVIE INFO BAR (visible when context movie selected) ============ -->
        <section v-if="contextMovie" class="movie-info-bar">
          <div class="mib-poster" :style="{ background: posterGradient }">
            <img v-if="contextMovie.poster" :src="contextMovie.poster" :alt="contextMovie.title" />
            <div class="mib-poster-text">
              <span class="mib-eng">{{ (contextMovie.englishTitle || contextMovie.title).toUpperCase().split(/[: ]/)[0] }}</span>
              <span class="mib-eng-sub">FEATURED</span>
            </div>
          </div>
          <div class="mib-body">
            <div class="mib-title-row">
              <h1 class="mib-title">{{ contextMovie.title }}</h1>
              <span class="mib-en">{{ (contextMovie.englishTitle || contextMovie.title).toUpperCase() }}</span>
            </div>
            <div class="mib-tags">
              <span v-for="t in movieTags" :key="t" class="mib-tag">{{ t }}</span>
            </div>
            <div class="mib-meta">
              <span class="meta-item">
                <MaterialIcon class="msr" name="star" />
                <span class="meta-num">{{ contextMovie.rating?.toFixed(1) || '9.2' }}</span>
                <span class="meta-out">/ 10</span>
              </span>
              <span class="meta-sep"></span>
              <span class="meta-text">{{ contextMovie.director || '维伦纽瓦' }} · {{ contextMovie.duration }} 分钟</span>
              <span class="meta-sep"></span>
              <span class="meta-text">{{ contextMovie.genre || '科幻 / 冒险' }}</span>
            </div>
          </div>
          <div class="mib-right">
            <div class="mib-stat">
              <span class="stat-label">可选影院</span>
              <div class="stat-num-row">
                <span class="stat-num">{{ filteredCinemas.length }}</span>
                <span class="stat-unit">家</span>
              </div>
            </div>
            <div class="mib-sep"></div>
            <button class="mib-action" @click="router.push('/movies')">
              <MaterialIcon class="msr" name="swap_horiz" />
              <span>重选电影</span>
            </button>
          </div>
        </section>

        <!-- ============ CINEMA FILTERS ============ -->
        <section class="cinema-filter-panel" aria-label="影院筛选">
          <div class="cf-head">
            <div class="cf-title-wrap">
              <MaterialIcon class="msr cf-title-icon" name="tune" />
              <div>
                <h2 class="cf-title">筛选影院</h2>
                <p class="cf-subtitle">按偏好快速找到合适的观影地点</p>
              </div>
            </div>
            <button v-if="hasActiveFilters" class="cf-reset" @click="resetFilters">
              <MaterialIcon class="msr" name="restart_alt" />
              <span>清除筛选</span>
            </button>
          </div>

          <div class="cf-groups">
            <div v-for="group in filterGroups" :key="group.key" class="cf-group">
              <div class="cf-label">
                <MaterialIcon class="msr" :name="group.icon" />
                <span>{{ group.label }}</span>
              </div>
              <div class="cf-options">
                <button
                  v-for="option in group.options"
                  :key="option"
                  class="cf-option"
                  :class="{ active: selectedFilters[group.key] === option }"
                  :aria-pressed="selectedFilters[group.key] === option"
                  @click="selectFilter(group.key, option)"
                >
                  {{ option }}
                </button>
              </div>
            </div>
          </div>

          <div class="cf-summary">
            <MaterialIcon class="msr" name="local_movies" />
            <span>共找到 <strong>{{ filteredCinemas.length }}</strong> 家影院</span>
            <div v-if="activeFilterLabels.length" class="cf-active-list">
              <span v-for="item in activeFilterLabels" :key="item" class="cf-active-tag">{{ item }}</span>
            </div>
          </div>
        </section>

        <!-- ============ TWO COL ============ -->
        <section class="two-col">
          <!-- ============ MAP CARD ============ -->
          <aside class="map-card">
            <div class="map-head">
              <div class="map-head-left">
                <MaterialIcon class="msr map-ico" name="map" />
                <h3 class="map-title">附近影院</h3>
                <span class="map-count">· {{ cinemas.length }} 家</span>
              </div>
              <button class="map-filter" @click="tip('地图筛选')">
                <MaterialIcon class="msr" name="filter_list" />
              </button>
            </div>

            <div class="map-canvas">
              <div ref="mapContainerRef" class="amap-container"></div>
              <div class="map-current">
                <MaterialIcon class="msr" name="my_location" />
                <span>{{ sharedLocation.label }}</span>
              </div>
              <div class="map-zoom">
                <button class="zoom-btn" @click="zoomIn">
                  <MaterialIcon class="msr" name="add" />
                </button>
                <button class="zoom-btn" @click="zoomOut">
                  <MaterialIcon class="msr" name="remove" />
                </button>
                <button class="zoom-btn" @click="locateMe" title="回到我的位置">
                  <MaterialIcon class="msr" name="my_location" />
                </button>
              </div>

              <div v-if="!mapReady" class="map-loading">
                <MaterialIcon class="msr spin" name="progress_activity" />
                <span>地图加载中...</span>
              </div>
            </div>

            <div class="map-legend">
              <div class="legend-row">
                <span class="legend-item">
                  <span class="legend-dot active"></span>
                  <span>已选影院</span>
                </span>
                <span class="legend-item">
                  <span class="legend-dot"></span>
                  <span>其他影院</span>
                </span>
              </div>
              <span class="legend-range">3.2 km 内</span>
            </div>
          </aside>

          <!-- ============ CINEMA LIST COLUMN ============ -->
          <div class="list-col">
            <!-- Filter Bar -->
            <section class="filter-bar">
              <div class="filter-row">
                <button class="filter-chip" @click="cycleSort">
                  <MaterialIcon class="msr" name="swap_vert" />
                  <span>{{ sortLabel }}</span>
                  <MaterialIcon class="msr" name="keyboard_arrow_down" />
                </button>
              </div>
            </section>

            <!-- Cinema Cards List -->
            <div v-loading="loading" class="cinema-list">
              <article
                v-for="(c, i) in filteredCinemas"
                :key="c.id"
                class="cinema-card"
                tabindex="0"
                role="link"
                :aria-label="`进入${c.name}选择电影`"
                @click="onSelectCinema(i)"
                @keyup.enter="onSelectCinema(i)"
              >
                <!-- Card header -->
                <div class="cc-head">
                  <div class="cc-rank">
                    {{ i + 1 }}
                  </div>
                  <div class="cc-info">
                    <div class="cc-title-row">
                      <span class="cc-name">{{ c.name }}</span>
                      <span class="cc-rating">
                        <MaterialIcon class="msr" name="star" />
                        <span>{{ ratingFor(c.id) }}</span>
                      </span>
                    </div>
                    <div class="cc-tags">
                      <span v-for="t in featuresFor(c.id)" :key="t" class="cc-tag">{{ t }}</span>
                    </div>
                    <div class="cc-meta">
                      <span class="meta-item">
                        <MaterialIcon class="msr" name="location_on" />
                        <span>{{ c.address }}</span>
                      </span>
                      <span class="meta-sep-dot"></span>
                      <span class="meta-item">
                        <MaterialIcon class="msr" name="directions_walk" />
                        <span>{{ distanceFor(c.id) }}</span>
                      </span>
                    </div>
                  </div>
                  <div class="cc-right">
                    <div class="cc-price">
                      <span class="price-label">最低</span>
                      <div class="price-row">
                        <span class="price-cur">¥</span>
                        <span class="price-num">{{ minPriceFor(c.id) }}</span>
                        <span class="price-unit">起</span>
                      </div>
                    </div>
                    <button class="cc-action primary" @click.stop="goToCinema(c.id)">
                      <span>选择电影</span>
                      <MaterialIcon class="msr" name="chevron_right" />
                    </button>
                  </div>
                </div>
              </article>

              <el-empty v-if="!loading && filteredCinemas.length === 0" description="暂无影院" />
            </div>
          </div>
        </section>

        <!-- Page footer -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { cinemaApi } from '@/api/cinema'
import { movieApi } from '@/api/movie'
import type { Movie, Cinema } from '@/types'
import AppSidebar from '@/components/AppSidebar.vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { useLocation } from '@/composables/useLocation'

const route = useRoute()
const router = useRouter()
const { location: sharedLocation, ensure: ensureLocation } = useLocation()

const searchKeyword = ref('')
const loading = ref(false)
const cinemas = ref<Cinema[]>([])
const contextMovie = ref<Movie | null>(null)
const activeCinemaIdx = ref(0)
const sortMode = ref<'distance' | 'rating' | 'price'>('distance')

type FilterKey = 'brand' | 'area' | 'hall' | 'service'
const selectedFilters = ref<Record<FilterKey, string>>({
  brand: '全部',
  area: '全部',
  hall: '全部',
  service: '全部'
})

// ====== 高德地图相关 ======
const mapContainerRef = ref<HTMLElement | null>(null)
const mapReady = ref(false)
let AMapNS: any = null
let mapInstance: any = null
let geocoder: any = null
let userMarker: any = null
const cinemaMarkers = new Map<number, any>()
const cinemaCoords = ref<Map<number, [number, number]>>(new Map())
const cinemaCoordKeys = new Map<number, string>()
const cinemaDistances = ref<Map<number, number>>(new Map())
const userPosition = ref<[number, number] | null>(null)

const featurePool: Record<number, string[]> = {
  0: ['IMAX 厅', '杜比全景声', '4DX'],
  1: ['IMAX', '巨幕'],
  2: ['LUXE 厅', '杜比影院'],
  3: ['激光厅', '亲子厅'],
  4: ['CINITY', 'VIP 厅']
}
const ratingPool = ['9.4', '9.1', '8.9', '9.0', '8.7']
const minPricePool = [58, 65, 52, 48, 55]

const ratingFor = (id: number) => ratingPool[id % ratingPool.length]
const featuresFor = (id: number) => featurePool[id % 5]
const minPriceFor = (id: number) => minPricePool[id % minPricePool.length]
const servicesFor = (id: number) => [
  ...(id % 2 === 0 ? ['可退票'] : []),
  ...(id % 3 !== 0 ? ['可改签'] : []),
  ...(id % 2 !== 0 ? ['免费停车'] : []),
  ...(id % 3 === 0 ? ['影厅小食'] : [])
]

const brandFor = (cinema: Cinema) => {
  const name = cinema.name || ''
  const knownBrands = ['万达', 'CGV', '博纳', '耀莱', 'UME', '金逸', '横店', '百老汇', '中影', '卢米埃']
  return knownBrands.find(brand => name.toUpperCase().includes(brand.toUpperCase())) ||
    name.split(/[（(·\s]/)[0].replace(/国际影城|影城|影院/g, '') || '其他'
}

const areaFor = (cinema: Cinema) => {
  const address = cinema.address || ''
  const district = address.match(/([^省市区县路号]{2,6}[区县])/)
  return district?.[1] || '其他区域'
}

const uniqueOptions = (items: string[]) => ['全部', ...Array.from(new Set(items.filter(Boolean)))]

const filterGroups = computed(() => [
  {
    key: 'brand' as FilterKey,
    label: '品牌',
    icon: 'apartment',
    options: uniqueOptions(cinemas.value.map(brandFor))
  },
  {
    key: 'area' as FilterKey,
    label: '行政区',
    icon: 'location_on',
    options: uniqueOptions(cinemas.value.map(areaFor))
  },
  {
    key: 'hall' as FilterKey,
    label: '影厅类型',
    icon: 'theaters',
    options: uniqueOptions(Object.values(featurePool).flat())
  },
  {
    key: 'service' as FilterKey,
    label: '影院服务',
    icon: 'concierge',
    options: ['全部', '可退票', '可改签', '免费停车', '影厅小食']
  }
])

const hasActiveFilters = computed(() => Object.values(selectedFilters.value).some(value => value !== '全部'))

const activeFilterLabels = computed(() => Object.entries(selectedFilters.value)
  .filter(([, value]) => value !== '全部')
  .map(([, value]) => value))

const selectFilter = (key: FilterKey, option: string) => {
  selectedFilters.value[key] = option
}

const resetFilters = () => {
  selectedFilters.value = { brand: '全部', area: '全部', hall: '全部', service: '全部' }
}

const distanceFor = (id: number) => {
  const m = cinemaDistances.value.get(id)
  if (m == null) return '—'
  if (m < 1000) return `${Math.round(m)} m`
  return `${(m / 1000).toFixed(1)} km`
}

const filteredCinemas = computed(() => {
  let arr = [...cinemas.value]
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    arr = arr.filter(c =>
      (c.name || '').toLowerCase().includes(kw) ||
      (c.address || '').toLowerCase().includes(kw)
    )
  }
  const { brand, area, hall, service } = selectedFilters.value
  if (brand !== '全部') arr = arr.filter(c => brandFor(c) === brand)
  if (area !== '全部') arr = arr.filter(c => areaFor(c) === area)
  if (hall !== '全部') arr = arr.filter(c => featuresFor(c.id).includes(hall))
  if (service !== '全部') arr = arr.filter(c => servicesFor(c.id).includes(service))
  if (sortMode.value === 'rating') {
    arr.sort((a, b) => Number(ratingFor(b.id)) - Number(ratingFor(a.id)))
  } else if (sortMode.value === 'price') {
    arr.sort((a, b) => minPriceFor(a.id) - minPriceFor(b.id))
  } else if (sortMode.value === 'distance') {
    const INF = Number.POSITIVE_INFINITY
    arr.sort((a, b) => {
      const da = cinemaDistances.value.get(a.id) ?? INF
      const db = cinemaDistances.value.get(b.id) ?? INF
      return da - db
    })
  }
  return arr
})

const sortLabel = computed(() => {
  if (sortMode.value === 'distance') return '距离最近'
  if (sortMode.value === 'rating') return '评分最高'
  return '价格最低'
})

const cycleSort = () => {
  if (sortMode.value === 'distance') sortMode.value = 'rating'
  else if (sortMode.value === 'rating') sortMode.value = 'price'
  else sortMode.value = 'distance'
}

const movieTags = computed(() => {
  if (!contextMovie.value) return ['IMAX', '杜比全景声', '4DX']
  const tags: string[] = []
  if (contextMovie.value.genre) {
    contextMovie.value.genre.split(/[\/\s,、]+/).filter(Boolean).slice(0, 2).forEach(g => tags.push(g))
  }
  tags.push('IMAX', '原版字幕')
  return tags.slice(0, 4)
})

const posterGradient = computed(() => {
  const palette = [
    'linear-gradient(200deg, #3d2410 0%, #1a0e08 100%)',
    'linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%)',
    'linear-gradient(200deg, #5c2a1a 0%, #2a0f08 100%)',
    'linear-gradient(200deg, #5c1a4a 0%, #2a0820 100%)'
  ]
  const id = contextMovie.value?.id || 1
  return palette[id % palette.length]
})

const loadCinemas = async () => {
  loading.value = true
  try {
    const data = await cinemaApi.getAll()
    cinemas.value = data || []
  } catch (e) {
    console.error('加载影院失败:', e)
    cinemas.value = []
  } finally {
    loading.value = false
  }
}

const loadContextMovie = async () => {
  const movieId = Number(route.query.movieId)
  if (!movieId) {
    contextMovie.value = null
    return
  }
  try {
    contextMovie.value = await movieApi.getById(movieId)
  } catch (e) {
    console.error('加载电影失败:', e)
    contextMovie.value = null
  }
}

const onSelectCinema = (i: number) => {
  activeCinemaIdx.value = i
  const c = filteredCinemas.value[i]
  if (c) goToCinema(c.id)
}

const goToCinema = (id: number) => {
  const movieId = Number(route.query.movieId)
  router.push({
    path: `/cinema/${id}`,
    query: movieId ? { movieId: String(movieId) } : undefined
  })
}

const onSearch = () => {
  ElMessage.info(`搜索:${searchKeyword.value || '空'}`)
}

const tip = (label: string) => {
  ElMessage.info(`${label} 功能敬请期待`)
}

watch(
  () => route.query.movieId,
  () => {
    loadContextMovie()
  }
)

// ====== 高德地图：初始化 / 定位 / Geocode / 距离 ======
const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923]

const initialCenter = (): [number, number] => {
  const { lng, lat } = sharedLocation.value
  if (lng != null && lat != null) return [lng, lat]
  return DEFAULT_CENTER
}

const initMap = async () => {
  if (!mapContainerRef.value) return
  window._AMapSecurityConfig = {
    securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
  }
  try {
    AMapNS = await AMapLoader.load({
      key: import.meta.env.VITE_AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.Geocoder', 'AMap.GeometryUtil']
    })
  } catch (e) {
    console.warn('高德地图 JS SDK 加载失败,地图功能不可用:', e)
    return
  }
  mapInstance = new AMapNS.Map(mapContainerRef.value, {
    zoom: 12,
    center: initialCenter(),
    viewMode: '2D'
  })
  mapReady.value = true

  // locateUser 失败不影响后续流程（有 DB 坐标的影院仍可渲染）
  await locateUser().catch(() => {})
  // 用全国范围解析,避免”定位城市 ≠ 影院所在城市”导致跨城地址解析失败、marker 时有时无
  try {
    geocoder = new AMapNS.Geocoder({ city: '全国' })
  } catch { /* Geocoder 插件未加载时,仅 DB 坐标可用 */ }
  await geocodeCinemas().catch(() => {})
  renderCinemaMarkers()
  fitView()
}

const applyUserMarker = () => {
  if (!userPosition.value || !mapInstance || !AMapNS) return
  if (userMarker) mapInstance.remove(userMarker)
  userMarker = new AMapNS.Marker({
    position: userPosition.value,
    content: '<div class="me-dot"><span></span></div>',
    offset: new AMapNS.Pixel(-8, -8),
    zIndex: 200
  })
  mapInstance.add(userMarker)
}

const syncFromSharedLocation = () => {
  const { lng, lat } = sharedLocation.value
  if (lng != null && lat != null) {
    userPosition.value = [lng, lat]
    applyUserMarker()
    recomputeDistances()
    return true
  }
  return false
}

const locateUser = async () => {
  if (syncFromSharedLocation()) return
  await ensureLocation()
  syncFromSharedLocation()
}

const geocodeCinemas = async () => {
  if (!AMapNS) return
  const geocodeOne = (address: string) =>
    new Promise<[number, number] | null>(resolve => {
      if (!geocoder) {
        resolve(null)
        return
      }
      geocoder.getLocation(address, (status: string, result: any) => {
        if (status === 'complete' && result?.geocodes?.length) {
          const loc = result.geocodes[0].location
          resolve([loc.lng, loc.lat])
        } else {
          resolve(null)
        }
      })
    })
  const nextCoords = new Map(cinemaCoords.value)
  const validIds = new Set<number>()
  const failed: string[] = []
  let attempted = 0
  let succeeded = 0
  let direct = 0
  for (const c of cinemas.value) {
    validIds.add(c.id)
    // 后端直接提供经纬度时优先使用，避免依赖前端 Geocoder
    if (c.lng != null && c.lat != null) {
      const coord: [number, number] = [Number(c.lng), Number(c.lat)]
      nextCoords.set(c.id, coord)
      cinemaCoordKeys.set(c.id, `${c.lng},${c.lat}`)
      direct++
      continue
    }
    if (!c.address) {
      nextCoords.delete(c.id)
      cinemaCoordKeys.delete(c.id)
      continue
    }
    if (cinemaCoordKeys.get(c.id) === c.address) continue
    attempted++
    const coord = await geocodeOne(c.address)
    if (coord) {
      succeeded++
      nextCoords.set(c.id, coord)
      cinemaCoordKeys.set(c.id, c.address)
    } else {
      failed.push(`#${c.id} ${c.name}: ${c.address}`)
      nextCoords.delete(c.id)
      cinemaCoordKeys.delete(c.id)
    }
  }
  for (const id of [...nextCoords.keys()]) {
    if (!validIds.has(id)) {
      nextCoords.delete(id)
      cinemaCoordKeys.delete(id)
    }
  }
  cinemaCoords.value = nextCoords
  recomputeDistances()
  if (direct > 0 || attempted > 0) {
    console.info(`[Cinemas] 影院定位:${direct} 个直接使用后端坐标,${succeeded}/${attempted} 个按地址解析成功`)
    if (failed.length) console.warn('[Cinemas] 以下影院地址无法定位:\n' + failed.join('\n'))
  }
}

const haversine = (a: [number, number], b: [number, number]) => {
  const R = 6371000
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b[1] - a[1])
  const dLng = toRad(b[0] - a[0])
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a[1])) * Math.cos(toRad(b[1])) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s))
}

const recomputeDistances = () => {
  if (!userPosition.value || !AMapNS) return
  const next = new Map<number, number>()
  for (const [id, coord] of cinemaCoords.value.entries()) {
    const d = AMapNS.GeometryUtil?.distance
      ? AMapNS.GeometryUtil.distance(userPosition.value, coord)
      : haversine(userPosition.value, coord)
    next.set(id, d)
  }
  cinemaDistances.value = next
}

const buildMarkerContent = (label: number, active: boolean) =>
  `<div class="amap-pin ${active ? 'active' : ''}">
     <span class="amap-pin-dot"></span>
     <span class="amap-pin-num">${label}</span>
   </div>`

const renderCinemaMarkers = () => {
  if (!AMapNS || !mapInstance) return
  cinemaMarkers.forEach(m => mapInstance.remove(m))
  cinemaMarkers.clear()
  filteredCinemas.value.forEach((c, i) => {
    const coord = cinemaCoords.value.get(c.id)
    if (!coord) return
    const marker = new AMapNS.Marker({
      position: coord,
      content: buildMarkerContent(i + 1, i === activeCinemaIdx.value),
      offset: new AMapNS.Pixel(-14, -14),
      zIndex: 100
    })
    marker.on('click', () => onSelectCinema(i))
    mapInstance.add(marker)
    cinemaMarkers.set(c.id, marker)
  })
}

const fitView = () => {
  if (!mapInstance) return
  const overlays = [...cinemaMarkers.values()]
  if (userMarker) overlays.push(userMarker)
  if (overlays.length > 1) {
    mapInstance.setFitView(overlays, false, [60, 60, 60, 60])
  } else if (overlays.length === 1 && userPosition.value) {
    mapInstance.setZoomAndCenter(13, userPosition.value)
  } else if (userPosition.value) {
    mapInstance.setZoomAndCenter(13, userPosition.value)
  }
}

const zoomIn = () => mapInstance?.zoomIn()
const zoomOut = () => mapInstance?.zoomOut()
const locateMe = () => {
  if (userPosition.value && mapInstance) {
    mapInstance.setZoomAndCenter(14, userPosition.value)
  } else {
    locateUser()
  }
}

watch(filteredCinemas, () => {
  if (mapReady.value) renderCinemaMarkers()
})

watch(activeCinemaIdx, () => {
  if (mapReady.value) renderCinemaMarkers()
})

watch(
  () => cinemas.value.map(c => `${c.id}:${c.address}`).join('|'),
  async () => {
    if (!mapReady.value) return
    await geocodeCinemas()
    renderCinemaMarkers()
  }
)

watch(
  () => [sharedLocation.value.lng, sharedLocation.value.lat],
  () => {
    if (!mapReady.value) return
    const had = userPosition.value != null
    syncFromSharedLocation()
    if (!had && userPosition.value && cinemaMarkers.size === 0 && mapInstance) {
      mapInstance.setZoomAndCenter(13, userPosition.value)
    }
  }
)

onMounted(async () => {
  await loadCinemas()
  await loadContextMovie()
  await nextTick()
  initMap().catch(e => {
    console.error('地图初始化失败:', e)
    ElMessage.error('地图加载失败')
  })
})

onBeforeUnmount(() => {
  mapInstance?.destroy()
  mapInstance = null
  cinemaMarkers.clear()
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
  --sidebar: #E7E8E5;
  --sidebar-accent: #CBCCC9;
  --sidebar-fg: #666666;
  --sidebar-accent-fg: #18181b;
  --sidebar-border: #CBCCC9;
  --destructive: #D93C15;
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
  flex-shrink: 0;
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
  flex-shrink: 0;
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
.brand-sub {
  font-size: 12px;
  color: var(--sidebar-fg);
}

.sidebar-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin: 16px;
  background: var(--sidebar-accent);
  border: 1px solid var(--sidebar-border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.sidebar-search:hover { border-color: var(--primary); }
.sidebar-search .search-ico { font-size: 18px; color: var(--sidebar-fg); }
.search-text { flex: 1; font-size: 14px; color: var(--sidebar-fg); }
.kbd {
  padding: 2px 6px;
  border: 1px solid var(--sidebar-border);
  border-radius: 4px;
  background: var(--sidebar);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--sidebar-fg);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
  text-decoration: none;
  color: inherit;
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
.nav-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  background: var(--sidebar-accent);
  color: var(--sidebar-fg);
}
.nav-item.active .nav-badge {
  background: rgba(17, 17, 17, 0.2);
  color: var(--primary-foreground);
}

.sidebar-divider {
  height: 1px;
  background: var(--sidebar-border);
  margin: 4px 0;
}

.quick-access {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px 14px;
}
.qa-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: var(--sidebar-fg);
}
.qa-head .msr { font-size: 16px; }
.qa-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.qa-item:hover { background: rgba(0, 0, 0, 0.04); }
.qa-ico { font-size: 18px; color: var(--primary); }
.qa-label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.qa-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--primary);
}
.qa-tag.hot { color: var(--destructive); }

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
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.guest-avatar { background: var(--sidebar-accent); color: var(--sidebar-fg); }
.guest-avatar .msr { font-size: 20px; }
.user-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; cursor: pointer; transition: opacity 0.15s; }
.user-info:hover { opacity: 0.8; }
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sidebar-accent-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-tier {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--sidebar-fg);
}
.more-btn {
  font-size: 18px;
  color: var(--sidebar-fg);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}
.more-btn:hover { background: var(--sidebar-accent); }
.footer-login {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.footer-login:hover { opacity: 0.88; }

/* ============ MAIN AREA ============ */
.main-area { flex: 1; min-width: 0; display: flex; flex-direction: column; }

/* TOP BAR */
.top-bar {
  display: flex;
  justify-content: space-between;
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
.breadcrumb { display: flex; align-items: center; gap: 8px; }
.bc-item {
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}
.bc-item:hover { color: var(--fg); }
.bc-item.active { color: var(--fg); font-weight: 700; cursor: default; }
.bc-sep { font-family: var(--font-mono); font-size: 12px; color: var(--muted); }
.tb-right { display: flex; align-items: center; gap: 10px; }
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
.tb-pill .msr { font-size: 14px; color: var(--muted); }
.tb-pill .primary { color: var(--primary); }
.tb-pill-caret { font-size: 14px !important; }
.tb-pill-text { font-size: 12px; font-weight: 600; }
.tb-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  width: 280px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.15s;
}
.tb-search:focus-within { border-color: var(--primary); }
.tb-search .msr { font-size: 14px; color: var(--muted); }
.tb-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--fg);
}
.tb-search input::placeholder { color: var(--muted); }
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
  transition: border-color 0.15s;
}
.tb-icon:hover { border-color: var(--primary); }
.tb-icon .msr { font-size: 18px; color: var(--fg); }
.tb-avatar {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

/* CONTENT */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 32px;
  min-height: 0;
}

/* ============ MOVIE INFO BAR ============ */
.movie-info-bar {
  display: flex;
  align-items: stretch;
  gap: 20px;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.mib-poster {
  position: relative;
  width: 100px;
  height: 150px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 8px;
}
.mib-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.85;
}
.mib-poster-text {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.mib-eng {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #FFCC8A;
}
.mib-eng-sub {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 2px;
  color: rgba(255, 204, 138, 0.6);
}
.mib-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
  min-width: 0;
}
.mib-title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.mib-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--fg); }
.mib-en {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--muted);
}
.mib-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.mib-tag {
  padding: 3px 8px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--muted);
}
.mib-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.mib-meta .meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.mib-meta .msr { font-size: 14px; color: var(--primary); }
.mib-meta .meta-num {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--fg);
}
.mib-meta .meta-out {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}
.mib-meta .meta-text {
  font-size: 12px;
  color: var(--muted);
}
.meta-sep {
  width: 1px;
  height: 12px;
  background: var(--border);
}
.mib-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.mib-stat { display: flex; flex-direction: column; gap: 2px; align-items: flex-end; }
.mib-stat .stat-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--muted);
}
.stat-num-row { display: flex; align-items: flex-end; gap: 4px; }
.stat-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--fg);
  line-height: 1;
}
.stat-unit { font-size: 11px; font-weight: 500; color: var(--muted); margin-bottom: 2px; }
.mib-sep { width: 1px; height: 32px; background: var(--border); }
.mib-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.mib-action:hover { border-color: var(--primary); color: var(--primary); }
.mib-action .msr { font-size: 16px; }

/* ============ CINEMA FILTERS ============ */
.cinema-filter-panel {
  margin-bottom: 20px;
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.cf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(90deg, rgba(255, 132, 0, 0.1), transparent 42%);
}
.cf-title-wrap { display: flex; align-items: center; gap: 12px; }
.cf-title-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 20px;
}
.cf-title { margin: 0; font-size: 15px; font-weight: 800; color: var(--fg); }
.cf-subtitle { margin: 3px 0 0; font-size: 11px; color: var(--muted); }
.cf-reset {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 11px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--card);
  color: var(--muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.cf-reset:hover { border-color: var(--primary); color: var(--fg); }
.cf-reset .msr { font-size: 15px; }
.cf-groups { padding: 2px 20px; }
.cf-group {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--border);
}
.cf-group:last-child { border-bottom: 0; }
.cf-label {
  display: flex;
  align-items: center;
  gap: 8px;
  align-self: start;
  min-height: 30px;
  font-size: 12px;
  font-weight: 700;
  color: var(--muted);
}
.cf-label .msr { font-size: 16px; color: var(--primary); }
.cf-options { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.cf-option {
  padding: 7px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--fg);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
  cursor: pointer;
  transition: all 0.15s;
}
.cf-option:hover { background: var(--secondary); border-color: var(--border); }
.cf-option.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
  font-weight: 700;
  box-shadow: 0 3px 10px rgba(255, 132, 0, 0.2);
}
.cf-summary {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 9px 20px;
  border-top: 1px solid var(--border);
  background: var(--secondary);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}
.cf-summary > .msr { font-size: 15px; color: var(--primary); }
.cf-summary strong { color: var(--fg); font-size: 13px; }
.cf-active-list { display: flex; gap: 5px; flex-wrap: wrap; margin-left: 8px; }
.cf-active-tag {
  padding: 3px 7px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--card);
  color: var(--fg);
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 600;
}

/* ============ TWO COL ============ */
.two-col {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 20px;
  align-items: start;
}

/* ============ MAP CARD ============ */
.map-card {
  display: flex;
  flex-direction: column;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  position: sticky;
  top: 88px;
  min-height: 540px;
}
.map-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.map-head-left { display: flex; align-items: center; gap: 8px; }
.map-ico { color: var(--primary); font-size: 16px; }
.map-title { margin: 0; font-size: 13px; font-weight: 700; color: var(--fg); }
.map-count { font-family: var(--font-mono); font-size: 11px; font-weight: 500; color: var(--muted); }
.map-filter {
  width: 28px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.15s;
}
.map-filter:hover { border-color: var(--primary); }
.map-filter .msr { font-size: 14px; color: var(--fg); }

.map-canvas {
  position: relative;
  flex: 1;
  min-height: 380px;
  background: var(--secondary);
  overflow: hidden;
}
.map-current {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  color: var(--fg);
}
.map-current .msr { font-size: 12px; color: var(--primary); }
.map-zoom {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  width: 36px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.zoom-btn {
  height: 32px;
  border: none;
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}
.zoom-btn:hover { background: var(--secondary); }
.zoom-btn:first-child { border-bottom: 1px solid var(--border); }
.zoom-btn .msr { font-size: 16px; color: var(--fg); }

.map-grid { display: none; }
.amap-container {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: var(--secondary);
}
.map-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
  background: var(--secondary);
  z-index: 1;
}
.map-loading .spin {
  font-size: 18px;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.map-legend {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
.legend-row { display: flex; gap: 14px; }
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--fg);
}
.legend-dot.active { background: var(--primary); }
.legend-range {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
}

/* ============ LIST COLUMN ============ */
.list-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.date-pills {
  display: flex;
  gap: 4px;
  justify-content: space-between;
}
.date-pill {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--card);
  cursor: pointer;
  transition: all 0.15s;
}
.date-pill:hover { border-color: var(--primary); }
.date-pill.active {
  background: var(--fg);
  border-color: var(--fg);
}
.dp-week {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}
.dp-day {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}
.date-pill.active .dp-week { color: #fff; }
.date-pill.active .dp-day { color: rgba(255, 255, 255, 0.6); }

.filter-row { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.filter-chip:hover { border-color: var(--primary); }
.filter-chip .msr { font-size: 14px; color: var(--muted); }

/* Cinema list */
.cinema-list { display: flex; flex-direction: column; gap: 12px; }

.cinema-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.2s;
  overflow: hidden;
}
.cinema-card:hover {
  border-color: var(--primary);
}
.cinema-card.expanded {
  border: 2px solid var(--fg);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  cursor: default;
}

.cc-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
}
.cc-rank {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: var(--fg);
  flex-shrink: 0;
}
.cc-rank.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.cc-info { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.cc-title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cc-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--fg);
}
.cc-rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  background: var(--secondary);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--fg);
}
.cc-rating .msr { font-size: 11px; color: var(--primary); }

.cc-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.cc-tag {
  padding: 3px 8px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--muted);
}

.cc-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cc-meta .meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
}
.cc-meta .msr { font-size: 12px; color: var(--muted); }
.cc-meta .meta-item:nth-child(3) {
  font-family: var(--font-mono);
  font-weight: 600;
}
.meta-sep-dot {
  width: 1px;
  height: 10px;
  background: var(--border);
}

.cc-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.cc-price { display: flex; flex-direction: column; gap: 2px; align-items: flex-end; }
.price-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--muted);
}
.price-row { display: flex; align-items: flex-end; gap: 2px; }
.price-cur {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--primary);
}
.price-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--primary);
  line-height: 1;
}
.price-unit {
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  margin-bottom: 2px;
}

.cc-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: var(--fg);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s;
}
.cc-action:hover { opacity: 0.92; transform: translateY(-1px); }
.cc-action .msr { font-size: 14px; }
.cc-action.primary {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
}

/* Showtimes */
.cc-showtimes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px 18px;
  border-top: 1px solid var(--border);
}
.st-th {
  display: grid;
  grid-template-columns: 130px 1.4fr 1fr 130px 80px 110px;
  align-items: center;
  padding: 8px 12px;
  background: var(--secondary);
  border-radius: 8px;
}
.th-col {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
}
.st-col-price { text-align: right; }
.st-col-act { text-align: right; }

.st-loading, .st-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 16px;
  color: var(--muted);
}
.st-loading .msr, .st-empty .msr { font-size: 32px; color: var(--muted); }
.st-loading .spin { animation: spin 1.4s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.st-empty p { margin: 0; font-size: 13px; }
.link { color: var(--primary); cursor: pointer; font-weight: 600; }

.st-rows { display: flex; flex-direction: column; gap: 10px; }
.st-row {
  display: grid;
  grid-template-columns: 130px 1.4fr 1fr 130px 80px 110px;
  align-items: center;
  padding: 12px;
  border-radius: 10px;
  border-bottom: 1px solid var(--border);
  transition: background 0.15s;
}
.st-row:hover { background: var(--bg); }
.st-row.featured {
  background: rgba(255, 132, 0, 0.08);
  border: 1px solid rgba(255, 132, 0, 0.25);
}

.st-col { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.st-col-time .st-time-big {
  font-family: var(--font-mono);
  font-size: 17px;
  font-weight: 700;
  color: var(--fg);
}
.st-col-time .st-time-end {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}
.st-col-hall .st-hall-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st-col-movie {
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.st-movie-poster {
  width: 40px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #1a3a5c 0%, #0a1f3a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.st-movie-poster img { width: 100%; height: 100%; object-fit: cover; }
.st-movie-poster .msr { font-size: 20px; color: rgba(255, 255, 255, 0.6); }
.st-movie-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.st-movie-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st-movie-duration {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}
.st-col-seats .seats-num {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}
.seats-bar {
  width: 80px;
  height: 4px;
  border-radius: 999px;
  background: var(--secondary);
  overflow: hidden;
}
.seats-bar-fill {
  height: 100%;
  background: var(--primary);
  border-radius: 999px;
  transition: width 0.3s;
}
.seats-bar-fill.low { background: var(--destructive); }

.st-col-price {
  flex-direction: row;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 1px;
}
.st-price-cur {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--fg);
}
.st-price-num {
  font-family: var(--font-mono);
  font-size: 17px;
  font-weight: 700;
  color: var(--fg);
  line-height: 1;
}
.st-col-act { flex-direction: row; justify-content: flex-end; }
.st-buy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 700;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.st-buy:hover { border-color: var(--primary); color: var(--primary); }
.st-buy.primary {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.3);
}
.st-buy.primary:hover { opacity: 0.92; color: var(--primary-foreground); }

/* Page footer */
.page-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 4px;
  margin-top: 8px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--muted);
}
.foot-links { display: flex; gap: 16px; }
.foot-links span { cursor: pointer; transition: color 0.15s; }
.foot-links span:hover { color: var(--primary); }

/* ============ RESPONSIVE ============ */
@media (max-width: 1440px) {
  .two-col { grid-template-columns: 340px 1fr; }
  .st-th, .st-row { grid-template-columns: 100px 1.4fr 1fr 110px 70px 100px; }
}
@media (max-width: 1280px) {
  .two-col { grid-template-columns: 1fr; }
  .map-card { position: relative; top: 0; min-height: 320px; max-height: 360px; }
}
@media (max-width: 1024px) {
  .sidebar { display: none; }
  .content { padding: 20px; }
  .top-bar { gap: 12px; padding: 14px 20px; }
  .tb-search { width: 200px; }
}
@media (max-width: 768px) {
  .cf-head { padding: 14px; }
  .cf-groups { padding: 0 14px; }
  .cf-group { grid-template-columns: 1fr; gap: 6px; padding: 12px 0; }
  .cf-label { min-height: auto; }
  .cf-option { padding: 6px 10px; }
  .cf-summary { padding: 9px 14px; align-items: flex-start; flex-wrap: wrap; }
  .cf-active-list { width: 100%; margin-left: 22px; }
  .movie-info-bar { flex-direction: column; align-items: flex-start; }
  .mib-right { width: 100%; justify-content: space-between; }
  .st-th { display: none; }
  .st-row { grid-template-columns: 1fr 1fr; gap: 10px 16px; }
  .st-col-act { grid-column: 1 / -1; }
}
</style>

<!-- AMap marker content is injected outside Vue's scoped style scope -->
<style>
.amap-pin {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #FFFFFF;
  border: 2px solid #111111;
  cursor: pointer;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 12px;
  font-weight: 700;
  color: #111111;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  transition: transform 0.15s, background 0.15s, border-color 0.15s;
}
.amap-pin:hover { transform: scale(1.1); }
.amap-pin.active {
  background: #FF8400;
  border-color: #FF8400;
  color: #111111;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.amap-pin-dot { display: none; }

.me-dot {
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: rgba(33, 150, 243, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: me-pulse 1.6s ease-in-out infinite;
}
.me-dot > span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #2196F3;
  box-shadow: 0 0 0 2px #FFFFFF;
}
@keyframes me-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}
</style>
