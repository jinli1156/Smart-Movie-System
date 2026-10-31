<template>
  <el-dialog
    :model-value="modelValue"
    width="560px"
    class="city-picker-dialog"
    append-to-body
    @update:model-value="onVisibleChange"
  >
    <template #header>
      <div class="cp-head">
        <span class="cp-head-icon">
          <MaterialIcon class="msr" name="apartment" />
        </span>
        <div>
          <h2 class="cp-title">选择城市</h2>
          <p class="cp-subtitle">全国城市均可选择；未开通影院的城市，影院列表会为空</p>
        </div>
      </div>
    </template>

    <div class="cp-current">
      <span class="cp-current-dot" :class="{ ip: location.source === 'ip' }"></span>
      <div class="cp-current-body">
        <div class="cp-current-label">{{ location.label }}</div>
        <div class="cp-current-sub">{{ sourceText }}</div>
      </div>
      <button class="cp-relocate" type="button" :disabled="locating" @click="relocate">
        <MaterialIcon class="msr" :class="{ spin: locating }" name="my_location" />
        <span>{{ locating ? '定位中…' : '重新定位' }}</span>
      </button>
    </div>

    <div class="cp-search">
      <MaterialIcon class="msr" name="search" />
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索全国城市，如：北京、成都、桂林"
        @keyup.enter="chooseFirstMatch"
      />
    </div>

    <div class="cp-cities">
      <div v-if="loadingCities" class="cp-loading">
        <MaterialIcon class="msr spin" name="progress_activity" />
        <span>正在加载全国城市…</span>
      </div>
      <template v-else>
        <p v-if="cityLoadError" class="cp-error">
          {{ cityLoadError }}，已回退到常用城市
          <button type="button" class="cp-retry" @click="ensureCitiesLoaded">重试</button>
        </p>
        <section v-for="group in filteredGroups" :key="group.province" class="cp-province">
          <h3 class="cp-province-name">{{ group.province }}</h3>
          <div class="cp-grid">
            <button
              v-for="city in group.cities"
              :key="city.name"
              class="cp-city"
              :class="{ active: isActive(city) }"
              type="button"
              @click="choose(city)"
            >
              <span>{{ city.name }}</span>
              <MaterialIcon v-if="isActive(city)" class="msr cp-city-check" name="check_circle" />
            </button>
          </div>
        </section>
        <p v-if="!filteredGroups.length" class="cp-empty">没有匹配的城市，换个关键词试试</p>
      </template>
    </div>

    <p class="cp-tip">
      如果开启了代理/VPN 被定位到海外，或浏览器不允许定位，直接选择「南宁市」即可按南宁市中心计算距离。选择结果保存在本机，下次打开仍然生效。
    </p>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 城市选择弹窗：全国地级市通过高德行政区查询动态加载，支持搜索与手动切换。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import MaterialIcon from '@/components/MaterialIcon.vue'
import { useLocation } from '@/composables/useLocation'
import { CITY_OPTIONS, type CityOption, type ProvinceCities } from '@/data/cities'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const { location, ensure, setManualCity, loadChinaCities } = useLocation()
const locating = ref(false)
const keyword = ref('')
const groups = ref<ProvinceCities[]>([])
const loadingCities = ref(false)
const cityLoadError = ref('')

const sourceText = computed(() => {
  const current = location.value
  if (locating.value || current.status === 'locating') return '正在获取位置…'
  if (current.status === 'failed') return '定位失败，可手动选择城市'
  if (current.source === 'manual') return '手动选择'
  if (current.source === 'ip') return 'IP 定位，可能受代理/VPN 影响'
  if (current.source === 'html5') return '浏览器定位'
  if (current.source === 'sdk') return '设备定位'
  return '未知来源'
})

const isActive = (city: CityOption) => location.value.city === city.name

const fallbackGroups: ProvinceCities[] = [{ province: '常用城市', cities: CITY_OPTIONS }]

const sourceGroups = computed(() => {
  if (groups.value.length) return groups.value
  if (cityLoadError.value) return fallbackGroups
  return []
})

const filteredGroups = computed(() => {
  const query = keyword.value.trim()
  if (!query) return sourceGroups.value
  return sourceGroups.value
    .map(group => ({
      province: group.province,
      cities: group.cities.filter(city =>
        city.name.includes(query) || group.province.includes(query)
      )
    }))
    .filter(group => group.cities.length)
})

const ensureCitiesLoaded = async () => {
  if (loadingCities.value || groups.value.length) return
  loadingCities.value = true
  cityLoadError.value = ''
  try {
    groups.value = await loadChinaCities()
    if (!groups.value.length) cityLoadError.value = '未获取到全国城市'
  } catch (e) {
    console.error('全国城市加载失败:', e)
    cityLoadError.value = '全国城市加载失败'
  } finally {
    loadingCities.value = false
  }
}

watch(() => props.modelValue, open => {
  if (!open) return
  keyword.value = ''
  ensureCitiesLoaded()
})

const onVisibleChange = (value: boolean) => emit('update:modelValue', value)

const choose = (city: CityOption) => {
  setManualCity({ city: city.name, lng: city.lng, lat: city.lat })
  ElMessage.success(`已切换到${city.name}`)
  emit('update:modelValue', false)
}

const chooseFirstMatch = () => {
  const first = filteredGroups.value[0]?.cities[0]
  if (first) choose(first)
}

const relocate = async () => {
  if (locating.value) return
  locating.value = true
  try {
    await ensure(true)
    const current = location.value
    if (current.source === 'manual') return
    if (current.status === 'failed') {
      ElMessage.warning('定位失败，请检查浏览器定位权限，或直接选择城市')
      return
    }
    if (current.source === 'ip') {
      ElMessage.warning('当前为 IP 定位，若结果不准请直接选择城市，或关闭代理/VPN 后重试')
      return
    }
    ElMessage.success(`已定位到${current.label}`)
    emit('update:modelValue', false)
  } catch (e) {
    console.error('重新定位失败:', e)
    ElMessage.error('定位失败，请稍后重试')
  } finally {
    locating.value = false
  }
}
</script>

<style scoped>
.cp-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cp-head-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #ff8400;
  color: #111;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cp-head-icon .msr { font-size: 22px; }
.cp-title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #111;
}
.cp-subtitle {
  margin: 3px 0 0;
  font-size: 12px;
  color: #777;
}

.cp-current {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #f7f8f5;
  border: 1px solid #e3e4e1;
  border-radius: 14px;
}
.cp-current-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ff8400;
  box-shadow: 0 0 0 4px rgba(255, 132, 0, 0.18);
  flex-shrink: 0;
}
.cp-current-dot.ip {
  background: #d93c15;
  box-shadow: 0 0 0 4px rgba(217, 60, 21, 0.15);
}
.cp-current-body {
  flex: 1;
  min-width: 0;
}
.cp-current-label {
  font-size: 15px;
  font-weight: 700;
  color: #111;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-current-sub {
  margin-top: 2px;
  font-size: 11px;
  color: #777;
}
.cp-relocate {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 12px;
  border: 1px solid #ff8400;
  border-radius: 999px;
  background: #fff;
  color: #b85e00;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
}
.cp-relocate:hover:not(:disabled) { background: #fff4e6; }
.cp-relocate:disabled { opacity: 0.6; cursor: wait; }
.cp-relocate .msr { font-size: 16px; }
.cp-relocate .msr.spin { animation: cp-spin 1s linear infinite; }
@keyframes cp-spin {
  to { transform: rotate(360deg); }
}

.cp-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 0 12px;
  height: 42px;
  background: #f7f8f5;
  border: 1px solid #e3e4e1;
  border-radius: 10px;
  transition: border-color 0.15s, background 0.15s;
}
.cp-search:focus-within {
  border-color: #ff8400;
  background: #fff;
}
.cp-search .msr {
  font-size: 17px;
  color: #888;
}
.cp-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: #111;
}
.cp-search input::placeholder { color: #9a9c97; }

.cp-cities {
  max-height: 360px;
  overflow-y: auto;
  margin-top: 14px;
  padding-right: 4px;
}
.cp-cities::-webkit-scrollbar { width: 6px; }
.cp-cities::-webkit-scrollbar-thumb {
  background: #d9dad6;
  border-radius: 999px;
}
.cp-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 30px 0;
  font-size: 12px;
  color: #888;
}
.cp-loading .msr { font-size: 18px; }
.cp-loading .msr.spin { animation: cp-spin 1s linear infinite; }
.cp-error {
  margin: 12px 0 0;
  font-size: 11px;
  color: #d93c15;
  line-height: 1.6;
}
.cp-retry {
  margin-left: 8px;
  padding: 2px 8px;
  border: 1px solid #d93c15;
  border-radius: 999px;
  background: #fff;
  color: #d93c15;
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}
.cp-province + .cp-province { margin-top: 14px; }
.cp-province-name {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 0 0 8px;
  padding: 4px 0;
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  color: #888;
}
.cp-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.cp-city {
  min-width: 88px;
  height: 36px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid #e3e4e1;
  border-radius: 10px;
  background: #fff;
  color: #111;
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.cp-city:hover {
  border-color: #ff8400;
  color: #b85e00;
}
.cp-city.active {
  border-color: #ff8400;
  background: #fff4e6;
  color: #b85e00;
  font-weight: 800;
}
.cp-city-check { font-size: 14px; color: #ff8400; }
.cp-empty {
  margin: 22px 0;
  text-align: center;
  font-size: 12px;
  color: #888;
}

.cp-tip {
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px dashed #e3e4e1;
  font-size: 11px;
  line-height: 1.7;
  color: #888;
}
</style>

<style>
.city-picker-dialog {
  max-width: calc(100vw - 32px);
  border-radius: 18px;
  font-family: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
.city-picker-dialog .el-dialog__header {
  padding: 20px 24px 0;
  margin-right: 0;
}
.city-picker-dialog .el-dialog__body {
  padding: 18px 24px 22px;
}
.city-picker-dialog .el-dialog__headerbtn {
  top: 12px;
  right: 12px;
}
</style>
