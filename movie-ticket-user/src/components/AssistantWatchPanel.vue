<template>
  <details class="watch-panel">
    <summary><span>本次观影条件</span><small>{{ conditions.length ? `${conditions.length} 项 · 可查看和修改` : '告诉我时间、人数或喜欢的类型' }}</small></summary>
    <div class="watch-panel-body">
      <p class="watch-description">修改一项后，其他条件会保留。价格与座位会重新查询。</p>
      <div v-if="conditions.length" class="condition-list">
        <button v-for="item in conditions" :key="item.key" type="button" :disabled="busy" :aria-label="`移除条件：${item.label}`" @click="remove(item)">{{ item.label }} <span aria-hidden="true">×</span></button>
      </div>
      <div class="scene-actions"><button v-for="scene in scenes" :key="scene.value" type="button" :disabled="busy" @click="emit('change', `这次${scene.label}，请根据本次条件推荐`, { scene: scene.value })">{{ scene.label }}</button></div>
      <div class="preference-actions">
        <button v-if="conditions.length" type="button" :disabled="busy" @click="emit('change', '清空本次观影条件，重新开始', { reset: true, excludedGenres: [], excludedMovieIds: [] })">清空本次条件</button>
        <template v-if="loggedIn">
          <button v-if="hasExclusions" type="button" :disabled="busy || saving" @click="saveCurrent">保存为长期排除偏好</button>
          <button type="button" :disabled="saving" @click="openPreferences">{{ preferencesOpen ? '收起长期排除偏好' : '查看长期排除偏好' }}</button>
        </template>
        <router-link v-else to="/login">登录后可保存长期偏好</router-link>
      </div>
      <section v-if="preferencesOpen" class="saved-preferences" aria-label="长期排除偏好">
        <p>以下排除项会用于新对话；修改它们不会清除当前观影条件。</p>
        <p v-if="saving" role="status">正在同步偏好…</p>
        <div v-else class="condition-list">
          <button v-for="genre in saved.excludedGenres || []" :key="genre" type="button" :aria-label="`移除长期排除：${genre}`" @click="save({ ...saved, excludedGenres: saved.excludedGenres?.filter(value => value !== genre) })">不看{{ genre }} ×</button>
          <button v-if="saved.excludedMovieIds?.length" type="button" @click="save({ ...saved, excludedMovieIds: [] })">已排除 {{ saved.excludedMovieIds.length }} 部影片 · 移除</button>
          <span v-if="!saved.excludedGenres?.length && !saved.excludedMovieIds?.length">暂无长期排除偏好</span>
        </div>
      </section>
      <p v-if="notice" class="preference-notice" role="status">{{ notice }}</p>
    </div>
  </details>
</template>

<script setup lang="ts">
/**
 * 观影条件面板：展示当前条件与候选项，维护长期排除偏好。
 */
import { computed, ref } from 'vue'
import { assistantApi, type MovieExclusions, type WatchPlan } from '@/api/assistant'
const props = defineProps<{ plan: WatchPlan; busy: boolean; loggedIn: boolean }>()
const emit = defineEmits<{ change: [message: string, patch: Record<string, unknown>] }>()
const scenes = [{ value: 'RELAXED', label: '想看轻松的' }, { value: 'DATE', label: '和伴侣约会' }, { value: 'FAMILY', label: '和家人一起看' }]
const labels: Record<string, string> = { date: '日期', people: '人数', genre: '题材', minRating: '评分至少', maxDuration: '时长不超过', startAfter: '开场不早于', startBefore: '开场早于', endBefore: '结束不晚于', maxPrice: '单张基础票价上限', maxTotal: '优惠后总预算', cinemaKeyword: '影院范围', hallKeyword: '影厅', naturalLanguage: '座位偏好' }
const sceneNames: Record<string, string> = { RELAXED: '轻松观影', DATE: '约会', FAMILY: '家人同行', FRIENDS: '朋友聚会', SOLO: '独自观影' }
const seatNames: Record<string, string> = { CENTER: '中间位置', QUIET: '安静靠后', EASY_ACCESS: '方便进出', COUPLE: '双人位置', ACCESSIBLE: '方便通行' }
const conditions = computed(() => Object.entries(props.plan.criteria || {}).flatMap(([key, value]) => {
  if (value == null || key === 'limit' || key === 'priceExclusive' || key === 'showtimeId') return []
  let label = ''
  if (labels[key]) label = `${labels[key]}：${value}${['maxPrice', 'maxTotal'].includes(key) ? '元' : key === 'people' ? '人' : key === 'maxDuration' ? '分钟' : ''}`
  if (key === 'maxPrice' && props.plan.criteria.priceExclusive) label = `单张基础票价低于：${value}元`
  if (key === 'movieId') label = `影片：${props.plan.options.find(option => option.movieId === value || option.kind === 'movie' && option.id === value)?.title || '已选定影片'}`
  if (key === 'cinemaId') label = '已限定影院'
  if (key === 'usePoints') label = value ? '允许使用积分' : '不使用积分'
  if (key === 'useCoupons') label = value ? '允许使用优惠券' : '不使用优惠券'
  if (key === 'requireTogether') label = value ? '需要连座' : '可接受分散座位'
  if (key === 'excludedGenres' && value.length) label = `排除：${value.join('、')}`
  if (key === 'excludedMovieIds' && value.length) label = `已排除 ${value.length} 部影片`
  if (key === 'scene') label = `场景：${sceneNames[value] || value}`
  if (key === 'preference' && !props.plan.criteria.naturalLanguage) label = `座位偏好：${seatNames[value] || value}`
  return label ? [{ key, label }] : []
}))
const remove = (item: { key: string; label: string }) => {
  const patch: Record<string, unknown> = { [item.key]: item.key.startsWith('excluded') ? [] : null }
  if (item.key === 'movieId') patch.showtimeId = null
  if (item.key === 'maxPrice') patch.priceExclusive = null
  if (item.key === 'naturalLanguage') patch.preference = null
  emit('change', `移除“${item.label}”，请按剩余条件继续推荐`, patch)
}
const saved = ref<MovieExclusions>({})
const saving = ref(false)
const preferencesOpen = ref(false)
const notice = ref('')
const hasExclusions = computed(() => props.plan.criteria.excludedGenres?.length || props.plan.criteria.excludedMovieIds?.length)
const openPreferences = async () => {
  preferencesOpen.value = !preferencesOpen.value
  if (!preferencesOpen.value) return
  saving.value = true; notice.value = ''
  try { saved.value = await assistantApi.preferences() }
  catch { notice.value = '偏好读取失败，请重试。' }
  finally { saving.value = false }
}
/** 校验并保存长期排除偏好。 */
const save = async (value: MovieExclusions) => {
  saving.value = true; notice.value = ''
  try { saved.value = await assistantApi.savePreferences(value); preferencesOpen.value = true; notice.value = '长期排除偏好已保存，将用于新对话。' }
  catch { notice.value = '保存失败，长期偏好未确认更新，请重试。' }
  finally { saving.value = false }
}
/** 将当前观影条件中的排除项保存为长期偏好。 */
const saveCurrent = () => save({ excludedGenres: props.plan.criteria.excludedGenres || [], excludedMovieIds: props.plan.criteria.excludedMovieIds || [] })
</script>

<style scoped>
.watch-panel{flex:0 0 auto;border-bottom:1px solid #e8eae2;background:#fcfcf9;color:#424a3d;font-size:13px;max-height:32%;overflow:auto}
summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;cursor:pointer;list-style:revert}
summary span{font-weight:600}summary small{font-size:12px;color:#697460}.watch-panel-body{padding:0 22px 14px}.watch-description,.saved-preferences p{line-height:1.7;margin:0 0 10px;color:#697460}
.condition-list,.scene-actions,.preference-actions{display:flex;flex-wrap:wrap;gap:7px;margin-top:9px}.condition-list button,.scene-actions button,.preference-actions button{font:inherit;border:1px solid #dee4d6;border-radius:8px;background:#fff;padding:6px 9px;cursor:pointer;text-align:left;overflow-wrap:anywhere;max-width:100%}.condition-list button{background:#f2f5ec}.condition-list span{margin-left:4px}.preference-actions button,.preference-actions a{color:#995b15;font-size:12px;text-decoration:none}.saved-preferences{border-top:1px solid #e3e7dc;margin-top:12px;padding-top:12px}.preference-notice{margin:10px 0 0;line-height:1.6}button:disabled{opacity:.5;cursor:wait}button:focus-visible,summary:focus-visible{outline:2px solid #dc850f;outline-offset:2px}
@media(max-width:600px){summary{padding:9px 12px;align-items:flex-start}summary small{max-width:55%;text-align:right}.watch-panel-body{padding:0 12px 12px}}
</style>
