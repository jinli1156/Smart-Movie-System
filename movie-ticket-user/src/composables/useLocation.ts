import { ref } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

const STORAGE_KEY = 'user_location_cache'

interface LocationState {
  label: string
  province?: string
  city?: string
  district?: string
  lng?: number
  lat?: number
}

const state = ref<LocationState>({ label: '正在定位...' })
let inflight: Promise<void> | null = null

const persist = (next: LocationState) => {
  state.value = next
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {}
}

const restore = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const cached: LocationState = JSON.parse(raw)
      if (cached?.label && cached.label !== '正在定位...') {
        state.value = cached
        return true
      }
    }
  } catch {}
  return false
}

const formatLabel = (city?: string, district?: string) => {
  if (!city && !district) return '定位中'
  if (city && district && !district.startsWith(city)) {
    return `${city} · ${district}`
  }
  return district || city || '定位中'
}

const loadAMap = () => {
  if (!window._AMapSecurityConfig) {
    window._AMapSecurityConfig = {
      securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
    }
  }
  return AMapLoader.load({
    key: import.meta.env.VITE_AMAP_KEY,
    version: '2.0',
    plugins: ['AMap.Geolocation', 'AMap.CitySearch', 'AMap.Geocoder']
  })
}

const reverseGeocode = (AMap: any, lng: number, lat: number) =>
  new Promise<Partial<LocationState>>(resolve => {
    const geocoder = new AMap.Geocoder({ city: '全国', extensions: 'base' })
    geocoder.getAddress([lng, lat], (status: string, result: any) => {
      if (status === 'complete' && result?.regeocode?.addressComponent) {
        const a = result.regeocode.addressComponent
        resolve({
          province: a.province || undefined,
          city: a.city || a.province || undefined,
          district: a.district || undefined
        })
      } else {
        resolve({})
      }
    })
  })

const browserLocate = (AMap: any) =>
  new Promise<LocationState | null>(resolve => {
    const geo = new AMap.Geolocation({
      enableHighAccuracy: false,
      timeout: 6000,
      maximumAge: 0
    })
    geo.getCurrentPosition(async (status: string, result: any) => {
      if (status === 'complete' && result?.position) {
        const { lng, lat } = result.position
        const meta = await reverseGeocode(AMap, lng, lat)
        const city = meta.city || result.addressComponent?.city
        const district = meta.district || result.addressComponent?.district
        resolve({
          label: formatLabel(city, district),
          province: meta.province || result.addressComponent?.province,
          city,
          district,
          lng,
          lat
        })
      } else {
        resolve(null)
      }
    })
  })

const ipLocate = (AMap: any) =>
  new Promise<LocationState | null>(resolve => {
    const citySearch = new AMap.CitySearch()
    citySearch.getLocalCity((status: string, result: any) => {
      if (status === 'complete' && result?.city) {
        const c = result.bounds?.getCenter?.()
        resolve({
          label: formatLabel(result.city, undefined),
          province: result.province,
          city: result.city,
          lng: c?.lng,
          lat: c?.lat
        })
      } else {
        resolve(null)
      }
    })
  })

export const useLocation = () => {
  const ensure = async (force = false) => {
    if (!force && restore()) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        const AMap = await loadAMap()
        const fromGeo = await browserLocate(AMap)
        if (fromGeo) {
          persist(fromGeo)
          return
        }
        const fromIp = await ipLocate(AMap)
        if (fromIp) {
          persist(fromIp)
          return
        }
        persist({ label: '定位失败' })
      } catch (e) {
        console.error('定位失败:', e)
        persist({ label: '定位失败' })
      } finally {
        inflight = null
      }
    })()
    return inflight
  }
  return { location: state, ensure }
}
