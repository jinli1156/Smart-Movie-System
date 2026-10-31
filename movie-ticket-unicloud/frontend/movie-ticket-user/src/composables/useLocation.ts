/**
 * 共享定位状态：恢复缓存位置，协调浏览器定位、IP 定位与地址解析。
 *
 * 定位分三步：浏览器坐标 -> 高德逆地理编码（补城市/区县）-> IP 城市兜底。
 * 只要浏览器给出了坐标，就不应该让界面停在“定位中”；城市解析失败时
 * 降级为“当前位置”，并允许用户点击后重新定位。
 */
import { ref } from 'vue'
import type { CityOption, ProvinceCities } from '@/data/cities'

const STORAGE_KEY = 'user_location_cache'
const MANUAL_KEY = 'user_location_manual'
const LOCATING_LABEL = '正在定位...'
const FAILED_LABEL = '定位失败，请选择城市'
const COORD_ONLY_LABEL = '当前位置'
/** 旧版本会把无城市信息的状态写成这个文案，读取缓存时需识别并丢弃。 */
const LEGACY_PENDING_LABEL = '定位中'

export type LocationStatus = 'locating' | 'ready' | 'fallback' | 'failed'
export type LocationSource = 'html5' | 'ip' | 'sdk' | 'manual' | 'unknown'

export interface ManualCityInput {
  city: string
  lng: number
  lat: number
  label?: string
  province?: string
}

interface LocationState {
  label: string
  status: LocationStatus
  source?: LocationSource
  province?: string
  city?: string
  district?: string
  lng?: number
  lat?: number
}

interface BrowserLocation {
  lng: number
  lat: number
  source: Exclude<LocationSource, 'manual'>
  province?: string
  city?: string
  district?: string
}

const state = ref<LocationState>({ label: LOCATING_LABEL, status: 'locating' })
let inflight: Promise<void> | null = null

/** 用函数读取，避免 TS 把多次判断收窄成不可达分支。 */
const isManualLocation = () => state.value.source === 'manual'

const persist = (next: LocationState) => {
  state.value = next
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {}
}

/** 手动选择的城市优先于自动定位，并持久化到 localStorage 供下次打开使用。 */
const setManualCity = (input: ManualCityInput) => {
  const next: LocationState = {
    label: input.label || input.city,
    status: 'ready',
    source: 'manual',
    province: input.province,
    city: input.city,
    lng: input.lng,
    lat: input.lat
  }
  try {
    localStorage.setItem(MANUAL_KEY, JSON.stringify(next))
  } catch {}
  persist(next)
}

const restoreManual = () => {
  try {
    const raw = localStorage.getItem(MANUAL_KEY)
    if (raw) {
      const cached: LocationState = JSON.parse(raw)
      if (
        cached &&
        typeof cached.city === 'string' &&
        Number.isFinite(cached.lng) &&
        Number.isFinite(cached.lat)
      ) {
        state.value = {
          ...cached,
          label: cached.label || cached.city,
          status: 'ready',
          source: 'manual'
        }
        return true
      }
    }
  } catch {}
  return false
}

/** 清除手动选择，让下一次 ensure 重新走自动定位。 */
const clearManualCity = () => {
  try {
    localStorage.removeItem(MANUAL_KEY)
  } catch {}
  if (state.value.source === 'manual') {
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {}
    state.value = { label: LOCATING_LABEL, status: 'locating' }
  }
}

/** 只有带城市/区县或有效坐标的结果才值得缓存，失败和“定位中”都应允许重试。 */
const isUsableCache = (value: any): value is LocationState => {
  if (!value || typeof value.label !== 'string') return false
  if (value.status === 'locating' || value.status === 'failed') return false
  if (value.label === LOCATING_LABEL || value.label === LEGACY_PENDING_LABEL) return false
  if (value.label.startsWith('定位失败')) return false
  const hasCity = Boolean(value.city || value.district)
  const hasCoord = Number.isFinite(value.lng) && Number.isFinite(value.lat)
  return hasCity || hasCoord
}

const restore = () => {
  if (restoreManual()) return true
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const cached: LocationState = JSON.parse(raw)
      if (isUsableCache(cached)) {
        state.value = { ...cached, status: cached.status || 'ready' }
        return true
      }
      // 旧缓存可能是“定位中/定位失败”，清掉以便本次重新定位
      sessionStorage.removeItem(STORAGE_KEY)
    }
  } catch {}
  return false
}

/** 高德对直辖市/部分区域可能返回空数组，统一转成可展示的字符串。 */
const normalizeText = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value : undefined

/** 返回展示文案；城市与区县都拿不到时返回 undefined，由调用方决定降级文案。 */
const formatLabel = (city?: unknown, district?: unknown) => {
  const c = normalizeText(city)
  const d = normalizeText(district)
  if (c && d) {
    return d.startsWith(c) ? d : `${c} · ${d}`
  }
  return d || c || undefined
}

/** 给可能永远不回调的 SDK 调用加超时，避免定位流程整体卡住。 */
const withTimeout = <T>(task: Promise<T>, ms: number, fallback: T) =>
  Promise.race<T>([
    task,
    new Promise<T>(resolve => setTimeout(() => resolve(fallback), ms))
  ])

const loadAMap = async () => {
  if (!window._AMapSecurityConfig) {
    window._AMapSecurityConfig = {
      securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
    }
  }
  const { default: AMapLoader } = await import('@amap/amap-jsapi-loader')
  return AMapLoader.load({
    key: import.meta.env.VITE_AMAP_KEY,
    version: '2.0',
    plugins: ['AMap.Geolocation', 'AMap.CitySearch', 'AMap.Geocoder', 'AMap.DistrictSearch']
  })
}

const MUNICIPALITIES = new Set([
  '北京市',
  '上海市',
  '天津市',
  '重庆市',
  '香港特别行政区',
  '澳门特别行政区'
])

/** 把高德行政区树（国家 -> 省 -> 市）拍平成城市选择器需要的分组结构。 */
export const buildProvinceGroups = (country: any): ProvinceCities[] => {
  const groups: ProvinceCities[] = []
  for (const province of country?.districtList || []) {
    const provinceName = normalizeText(province?.name)
    if (!provinceName) continue
    const children = (province?.districtList || []) as any[]
    const cities: CityOption[] = []
    const pushCity = (node: any) => {
      const name = normalizeText(node?.name)
      const center = node?.center
      let lng = Number.NaN
      let lat = Number.NaN
      if (typeof center === 'string') {
        const [lngText, latText] = center.split(',')
        lng = Number(lngText)
        lat = Number(latText)
      } else {
        lng = Number(center?.lng)
        lat = Number(center?.lat)
      }
      if (name && Number.isFinite(lng) && Number.isFinite(lat)) {
        cities.push({ name, lng, lat })
      }
    }
    // 直辖市/特别行政区的省节点本身就是城市；其余省取下一级地级市
    if (MUNICIPALITIES.has(provinceName) || children.length === 0) {
      pushCity(province)
    } else {
      children.forEach(pushCity)
    }
    if (cities.length) {
      groups.push({ province: provinceName, cities })
    }
  }
  return groups
}

let chinaCitiesCache: ProvinceCities[] | null = null
let chinaCitiesInflight: Promise<ProvinceCities[]> | null = null

/** 拉取全国地级市及中心点；结果缓存，供城市选择弹窗复用。 */
const loadChinaCities = async (): Promise<ProvinceCities[]> => {
  if (chinaCitiesCache) return chinaCitiesCache
  if (chinaCitiesInflight) return chinaCitiesInflight
  chinaCitiesInflight = (async () => {
    const AMap = await loadAMap()
    if (!AMap.DistrictSearch) throw new Error('高德行政区查询插件未加载')
    return new Promise<ProvinceCities[]>((resolve, reject) => {
      let settled = false
      const timer = setTimeout(() => {
        if (settled) return
        settled = true
        reject(new Error('全国城市加载超时'))
      }, 12000)
      const finish = (task: () => void) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        task()
      }
      try {
        const search = new AMap.DistrictSearch({
          level: 'country',
          subdistrict: 2,
          extensions: 'base'
        })
        const run = (keyword: string, retry: boolean) => {
          search.search(keyword, (status: string, result: any) => {
            if (retry && (status !== 'complete' || !result?.districtList?.length)) {
              // 个别环境对“中国/全国”关键字支持不同，失败后换关键字再试一次
              run('全国', false)
              return
            }
            finish(() => {
              if (status !== 'complete' || !result?.districtList?.length) {
                reject(new Error(result?.info || status || '全国城市加载失败'))
                return
              }
              resolve(buildProvinceGroups(result.districtList[0]))
            })
          })
        }
        run('中国', true)
      } catch (e) {
        finish(() => reject(e instanceof Error ? e : new Error(String(e))))
      }
    })
  })()
    .then(groups => {
      if (groups.length) chinaCitiesCache = groups
      chinaCitiesInflight = null
      return groups
    })
    .catch(e => {
      chinaCitiesInflight = null
      throw e
    })
  return chinaCitiesInflight
}

/** 将经纬度转换为可展示的地址与城市信息。 */
const reverseGeocode = (AMap: any, lng: number, lat: number) =>
  new Promise<Partial<LocationState>>(resolve => {
    try {
      const geocoder = new AMap.Geocoder({ city: '全国', extensions: 'base' })
      geocoder.getAddress([lng, lat], (status: string, result: any) => {
        if (status === 'complete' && result?.regeocode?.addressComponent) {
          const a = result.regeocode.addressComponent
          resolve({
            province: normalizeText(a.province),
            city: normalizeText(a.city) || normalizeText(a.province),
            district: normalizeText(a.district)
          })
        } else {
          // 常见原因：Key 域名白名单不匹配 / 安全密钥错误 / 网络不可达
          console.warn('[定位] 逆地理编码失败:', result?.info || status, result?.infocode || '')
          resolve({})
        }
      })
    } catch {
      resolve({})
    }
  })

/** 调用浏览器定位能力获取当前位置。 */
const browserLocate = (AMap: any) =>
  new Promise<BrowserLocation | null>(resolve => {
    let settled = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const finish = (value: BrowserLocation | null) => {
      if (settled) return
      settled = true
      if (timer) clearTimeout(timer)
      resolve(value)
    }
    // 高德 getCurrentPosition 自身有 timeout，这里再兜一层，防止回调不触发
    timer = setTimeout(() => finish(null), 9000)

    try {
      const geo = new AMap.Geolocation({
        // PC 端高德默认优先“精确 IP 定位”；代理/VPN 会把定位带到出口城市（如日本）。
        // 这里强制优先浏览器定位，IP 定位只作为兜底。
        enableHighAccuracy: true,
        GeoLocationFirst: true,
        timeout: 6000,
        maximumAge: 0,
        showButton: false,
        showMarker: false,
        showCircle: false,
        panToLocation: false
      })
      geo.getCurrentPosition(async (status: string, result: any) => {
        if (status !== 'complete' || !result?.position) {
          finish(null)
          return
        }
        const lng = Number(result.position.lng)
        const lat = Number(result.position.lat)
        if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
          finish(null)
          return
        }
        const source = (result.location_type || 'unknown') as BrowserLocation['source']
        console.info(`[定位] 来源=${source} 坐标=${lng},${lat}`)
        const component = result.addressComponent || {}
        // 高德定位成功时已自带逆地理编码结果，只有缺失时才再查一次
        let meta: Partial<LocationState> = {
          province: normalizeText(component.province),
          city: normalizeText(component.city) || normalizeText(component.province),
          district: normalizeText(component.district)
        }
        if (!meta.city && !meta.district) {
          meta = await withTimeout(reverseGeocode(AMap, lng, lat), 4000, {})
        }
        finish({
          lng,
          lat,
          source,
          province: normalizeText(meta.province),
          city: normalizeText(meta.city),
          district: normalizeText(meta.district)
        })
      })
    } catch (e) {
      console.warn('高德浏览器定位不可用:', e)
      finish(null)
    }
  })

/** 通过 IP 定位获取城市级位置，作为定位补充。 */
const ipLocate = (AMap: any) => {
  const task = new Promise<LocationState | null>(resolve => {
    try {
      const citySearch = new AMap.CitySearch()
      citySearch.getLocalCity((status: string, result: any) => {
        if (status === 'complete' && result?.city) {
          const c = result.bounds?.getCenter?.()
          resolve({
            label: formatLabel(result.city, undefined) || COORD_ONLY_LABEL,
            status: 'fallback',
            source: 'ip',
            province: normalizeText(result.province),
            city: result.city,
            lng: c?.lng,
            lat: c?.lat
          })
        } else {
          console.warn('[定位] IP 城市定位失败:', result?.info || status, result?.infocode || '')
          resolve(null)
        }
      })
    } catch {
      resolve(null)
    }
  })
  return withTimeout(task, 5000, null)
}

const fail = (reason: string) => {
  console.error('定位失败:', reason)
  const previous = state.value
  // 已有城市信息时保留旧值，不要因为一次失败把界面清空
  if (previous.city || previous.district) return
  persist({ label: FAILED_LABEL, status: 'failed' })
}

/** 提供共享位置及主动定位方法，供首页和影院地图复用。 */
export const useLocation = () => {
  const ensure = async (force = false) => {
    if (force) {
      clearManualCity()
      try {
        sessionStorage.removeItem(STORAGE_KEY)
      } catch {}
    }
    if (!force && restore()) return
    if (inflight) return inflight
    if (!state.value.city && !state.value.district && !state.value.lng) {
      state.value = { label: LOCATING_LABEL, status: 'locating' }
    }
    inflight = (async () => {
      try {
        const AMap = await loadAMap()
        // 自动定位期间用户可能已手动选了城市，任何异步阶段回来都要让位
        if (isManualLocation()) return
        const fromGeo = await browserLocate(AMap)
        if (isManualLocation()) return
        if (fromGeo) {
          if (fromGeo.city || fromGeo.district) {
            if (fromGeo.source === 'ip') {
              console.warn('[定位] 当前为 IP 定位；若开着代理/VPN，城市会是出口 IP 的归属地')
            }
            persist({
              label: formatLabel(fromGeo.city, fromGeo.district) || COORD_ONLY_LABEL,
              status: fromGeo.source === 'ip' ? 'fallback' : 'ready',
              source: fromGeo.source,
              province: fromGeo.province,
              city: fromGeo.city,
              district: fromGeo.district,
              lng: fromGeo.lng,
              lat: fromGeo.lat
            })
            return
          }
          // 坐标成功但城市解析失败：用 IP 城市补齐展示名，绝不停在“定位中”
          const fromIp = await ipLocate(AMap)
          if (isManualLocation()) return
          persist({
            label: formatLabel(fromGeo.city || fromIp?.city, fromGeo.district || fromIp?.district) ||
              COORD_ONLY_LABEL,
            status: 'fallback',
            source: fromGeo.source,
            province: fromGeo.province || fromIp?.province,
            city: fromGeo.city || fromIp?.city,
            district: fromGeo.district || fromIp?.district,
            lng: fromGeo.lng,
            lat: fromGeo.lat
          })
          return
        }
        const fromIp = await ipLocate(AMap)
        if (isManualLocation()) return
        if (fromIp) {
          persist(fromIp)
          return
        }
        fail('浏览器定位与 IP 定位均无结果')
      } catch (e) {
        fail(e instanceof Error ? e.message : String(e))
      } finally {
        inflight = null
      }
    })()
    return inflight
  }
  return { location: state, ensure, setManualCity, clearManualCity, loadChinaCities }
}
