<template>
  <div class="timeline-interaction">
    <p class="timeline-help">{{ readOnly ? '原排片仅供对照，请切换到排片草案后拖动调整。' : '拖到另一场电影上交换位置，拖到空白处调整时间或影厅 · 每 5 分钟吸附 · Esc 取消；也可聚焦后用方向键微调' }}</p>
    <div ref="scrollElement" class="timeline-scroll" :class="{ dragging: drag?.active }" tabindex="0" aria-label="影厅排片时间轴">
      <div class="timeline" :style="{ width: `${axisWidth + 116}px` }">
        <div class="time-ruler"><div class="hall-corner">影厅 / 时间</div><div class="ruler-track" :style="{ width: `${axisWidth}px` }"><span v-for="tick in ticks" :key="tick" :style="{ left: `${tick - axisStart}px` }">{{ hourLabel(tick) }}</span></div></div>
        <div v-for="hall in halls" :key="hall.id" class="hall-lane" :class="{ 'drop-target': drag?.active && drag.overHall && drag.candidate.hallId === hall.id }">
          <div class="hall-label"><strong>{{ hall.name }}</strong><small>{{ items.filter(s => s.hallId === hall.id).length }} 场放映{{ hall.status !== 1 ? ' · 已停用' : '' }}</small></div>
          <div class="lane-track" :data-hall-id="hall.id" :style="{ width: `${axisWidth}px`, height: `${Math.max(1, lanes.get(hall.id!)?.count || 0) * 108}px` }">
            <div class="golden-band" :style="{ left: `${1080 - axisStart}px`, width: '240px' }" /><span v-for="tick in ticks" :key="tick" class="grid-line" :style="{ left: `${tick - axisStart}px` }" />
            <p v-if="!items.some(s => s.hallId === hall.id)" class="empty-lane">拖入一场电影，或生成草案、添加场次</p>
            <div v-for="item in lanes.get(hall.id!)?.items" :key="item.index" class="showtime-slot" :class="{ 'drag-source': drag?.active && (drag.index === item.index || swapPreview?.targetIndex === item.index) }" :style="{ left: `${minute(item.row.startTime) - axisStart}px`, top: `${item.level * 108 + 12}px`, width: `${minute(item.row.endTime) - minute(item.row.startTime)}px` }">
              <button class="showtime-block" :data-showtime-index="item.index" :class="{ locked: item.row.locked, movable: canMove(item.row), conflict: conflicts.includes(item.index), 'swap-target': drag?.active && drag.swapIndex === item.index }" :style="{ '--film-color': filmColor(item.row.movieId) }" :title="`${item.row.movieTitle} ${time(item.row.startTime)}–${time(item.row.endTime)}${item.row.locked ? ` · ${item.row.lockReason || '已锁定'}` : canMove(item.row) ? ' · 拖到其他场次上交换，点击编辑' : ''}`" :aria-label="`${item.row.movieTitle} ${time(item.row.startTime)} ${item.row.locked ? '已锁定' : '调整场次'}`" :disabled="busy" @pointerdown="startDrag($event, item.index)" @click="clickItem($event, item.row)" @keydown="moveWithKeyboard($event, item.index)" @dragstart.prevent>
                <span class="slot-time">{{ time(item.row.startTime) }}–{{ time(item.row.endTime) }}</span><strong>{{ item.row.movieTitle }}</strong><span class="slot-meta">¥{{ item.row.basePrice }}<span>{{ item.row.locked ? '锁定' : labels[item.index] || '已排' }}</span></span>
              </button>
              <span class="cleaning-block" :style="{ width: `${cleaningMinutes}px` }" :title="`${cleaningMinutes} 分钟清场`" />
            </div>
            <div v-for="preview in previews.filter(item => item.row.hallId === hall.id)" :key="`preview-${preview.index}`" class="showtime-slot drag-preview" :class="{ invalid: !!dropIssue, swapping: !!swapPreview }" :style="{ left: `${minute(preview.row.startTime) - axisStart}px`, top: `${preview.level * 108 + 12}px`, width: `${minute(preview.row.endTime) - minute(preview.row.startTime)}px` }" aria-hidden="true">
              <div class="preview-block"><span class="slot-time">{{ time(preview.row.startTime) }}–{{ time(preview.row.endTime) }}</span><strong>{{ preview.row.movieTitle }}</strong><span>{{ dropIssue ? swapPreview ? '无法交换' : '无法放置' : swapPreview ? '松开交换位置' : '松开调整' }}</span></div><span class="cleaning-block" :style="{ width: `${cleaningMinutes}px` }" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="timeline-feedback" :class="{ invalid: drag?.active ? !!dropIssue : feedbackInvalid, swapping: drag?.active && !!swapPreview && !dropIssue }" role="status" aria-live="polite" aria-atomic="true">{{ dragFeedback }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { DraftShowtime } from '@/api/scheduling'
import type { Hall } from '@/types'

const props = defineProps<{
  items: DraftShowtime[]
  halls: Hall[]
  targetDate: string
  cleaningMinutes: number
  busy: boolean
  readOnly: boolean
  labels: string[]
}>()
const emit = defineEmits<{
  edit: [row: DraftShowtime]
  move: [index: number, row: DraftShowtime]
  swap: [changes: { index: number; row: DraftShowtime }[]]
}>()
type SwapPlan = { sourceIndex: number; targetIndex: number; source: DraftShowtime; target: DraftShowtime }
type DragState = {
  index: number
  row: DraftShowtime
  candidate: DraftShowtime
  pointerId: number
  element: HTMLElement
  originX: number
  originY: number
  clientX: number
  clientY: number
  offsetX: number
  axisStart: number
  axisEnd: number
  active: boolean
  overHall: boolean
  previewLevel: number
  swapIndex?: number
}
const scrollElement = ref<HTMLElement>()
const drag = shallowRef<DragState>()
const feedback = ref(''), feedbackInvalid = ref(false)
let animationFrame = 0, previousFrameTime = 0, suppressClickUntil = 0
const pad = (n: number) => String(n).padStart(2, '0')
const minute = (value: string) => (new Date(value).getTime() - new Date(`${props.targetDate}T00:00:00`).getTime()) / 60000
const timestamp = (value: number) => {
  const date = new Date(new Date(`${props.targetDate}T00:00:00`).getTime() + value * 60000)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
// Freeze the ruler while dragging so moving near either boundary cannot shift the pointer's time mapping.
const axisStart = computed(() => drag.value?.axisStart ?? Math.floor(Math.min(600, ...props.items.map(s => minute(s.startTime))) / 60) * 60)
const axisEnd = computed(() => drag.value?.axisEnd ?? Math.ceil(Math.max(1560, ...props.items.map(s => minute(s.endTime) + props.cleaningMinutes)) / 60) * 60)
const axisWidth = computed(() => axisEnd.value - axisStart.value)
const ticks = computed(() => Array.from({ length: Math.floor(axisWidth.value / 120) + 1 }, (_, i) => axisStart.value + i * 120))
const hourLabel = (m: number) => `${m >= 1440 ? '次日 ' : ''}${pad(Math.floor(m / 60) % 24)}:00`
const time = (value: string) => `${value.slice(0, 10) > props.targetDate ? '次日 ' : ''}${value.slice(11, 16)}`
const filmColor = (id: number) => ['#527c69', '#a07645', '#657dab', '#9c687c', '#64898f', '#8a7b9f'][id % 6]
const canMove = (row: DraftShowtime) => !props.busy && !props.readOnly && !row.locked
const lanes = computed(() => new Map(props.halls.map(hall => {
  const ends: number[] = []
  const items = props.items.map((row, index) => ({ row, index })).filter(s => s.row.hallId === hall.id).sort((a, b) => minute(a.row.startTime) - minute(b.row.startTime)).map(item => {
    let level = ends.findIndex(end => end <= minute(item.row.startTime))
    if (level < 0) level = ends.length
    ends[level] = minute(item.row.endTime) + props.cleaningMinutes
    return { ...item, level }
  })
  return [hall.id!, { items, count: ends.length }]
})))
function conflictingItems(row: DraftShowtime, index: number, items = props.items) {
  return items.flatMap((other, otherIndex) => otherIndex !== index && other.hallId === row.hallId && minute(row.startTime) < minute(other.endTime) + props.cleaningMinutes && minute(other.startTime) < minute(row.endTime) + props.cleaningMinutes ? [otherIndex] : [])
}
function issueFor(row: DraftShowtime, index: number, items = props.items) {
  if (props.halls.find(h => h.id === row.hallId)?.status !== 1) return '该影厅已停用，请选择可用影厅'
  if (row.startTime.slice(0, 10) !== props.targetDate) return '开场时间须在所选日期内，可跨日散场'
  if (new Date(row.startTime).getTime() <= Date.now()) return '不能调整到已过去的开场时间'
  const conflict = items[conflictingItems(row, index, items)[0]]
  if (conflict) return `与「${conflict.movieTitle}」${time(conflict.startTime)}–${time(conflict.endTime)} 重叠或清场不足，需预留 ${props.cleaningMinutes} 分钟`
  return ''
}
function shifted(row: DraftShowtime, start: number, hall: Hall): DraftShowtime {
  return { ...row, hallId: hall.id!, hallName: hall.name, startTime: timestamp(start), endTime: timestamp(start + minute(row.endTime) - minute(row.startTime)), predictedRate: undefined, confidence: undefined }
}
/** Exchange both slots together; adjacent films retain their clearing gap and each film's duration. */
function buildSwap(sourceIndex: number, targetIndex: number): SwapPlan | undefined {
  const source = props.items[sourceIndex], target = props.items[targetIndex]
  if (!source || !target || sourceIndex === targetIndex) return
  const sourceHall = props.halls.find(hall => hall.id === source.hallId), targetHall = props.halls.find(hall => hall.id === target.hallId)
  if (!sourceHall || !targetHall) return
  const plan: SwapPlan = { sourceIndex, targetIndex, source: shifted(source, minute(target.startTime), targetHall), target: shifted(target, minute(source.startTime), sourceHall) }
  if (source.hallId === target.hallId) {
    const ordered = props.items.map((row, index) => ({ row, index })).filter(item => item.row.hallId === source.hallId).sort((a, b) => minute(a.row.startTime) - minute(b.row.startTime))
    const sourcePosition = ordered.findIndex(item => item.index === sourceIndex), targetPosition = ordered.findIndex(item => item.index === targetIndex)
    if (Math.abs(sourcePosition - targetPosition) === 1) {
      const earlier = ordered[Math.min(sourcePosition, targetPosition)], later = ordered[Math.max(sourcePosition, targetPosition)]
      const gap = Math.max(props.cleaningMinutes, minute(later.row.startTime) - minute(earlier.row.endTime))
      const first = shifted(later.row, minute(earlier.row.startTime), sourceHall)
      const second = shifted(earlier.row, minute(first.endTime) + gap, sourceHall)
      plan.source = sourceIndex === later.index ? first : second
      plan.target = targetIndex === later.index ? first : second
    }
  }
  return plan
}
const swapPreview = computed(() => drag.value?.active && drag.value.overHall && drag.value.swapIndex != null ? buildSwap(drag.value.index, drag.value.swapIndex) : undefined)
function swappedItems(plan: SwapPlan) {
  const items = [...props.items]
  items[plan.sourceIndex] = plan.source; items[plan.targetIndex] = plan.target
  return items
}
const conflicts = computed(() => {
  const state = drag.value, plan = swapPreview.value
  if (!state?.active) return []
  if (!plan) return conflictingItems(state.candidate, state.index)
  const items = swappedItems(plan)
  return [...new Set([...conflictingItems(plan.source, plan.sourceIndex, items), ...conflictingItems(plan.target, plan.targetIndex, items)])]
})
const dropIssue = computed(() => {
  const state = drag.value, plan = swapPreview.value
  if (!state?.overHall) return '请在目标影厅的时间轴内松开'
  if (!plan) return issueFor(state.candidate, state.index)
  if (props.items[plan.targetIndex].locked) return `「${plan.target.movieTitle}」已锁定，不能交换场次`
  const items = swappedItems(plan)
  const issue = issueFor(plan.source, plan.sourceIndex, items) || issueFor(plan.target, plan.targetIndex, items)
  return issue ? `交换后${issue}` : ''
})
const previews = computed(() => {
  const state = drag.value, plan = swapPreview.value
  if (!state?.active) return []
  if (!plan) return [{ index: state.index, row: state.candidate, level: state.previewLevel }]
  const levelOf = (index: number) => lanes.value.get(props.items[index].hallId)?.items.find(item => item.index === index)?.level || 0
  return [{ index: plan.sourceIndex, row: plan.source, level: levelOf(plan.targetIndex) }, { index: plan.targetIndex, row: plan.target, level: levelOf(plan.sourceIndex) }]
})
const dragFeedback = computed(() => {
  const state = drag.value, plan = swapPreview.value
  if (!state?.active) return feedback.value || '调整后需重新检查并比较，再确认应用排片。'
  if (dropIssue.value) return dropIssue.value
  if (plan) return `松开交换位置：「${plan.source.movieTitle}」→ ${plan.source.hallName} ${time(plan.source.startTime)}–${time(plan.source.endTime)}；「${plan.target.movieTitle}」→ ${plan.target.hallName} ${time(plan.target.startTime)}–${time(plan.target.endTime)}`
  return `${state.candidate.hallName} · ${time(state.candidate.startTime)}–${time(state.candidate.endTime)} · 松开保存到草案`
})
function startDrag(event: PointerEvent, index: number) {
  suppressClickUntil = 0
  const row = props.items[index]
  if (!canMove(row) || !event.isPrimary || event.button !== 0 || drag.value) return
  const element = event.currentTarget as HTMLElement
  drag.value = { index, row, candidate: row, pointerId: event.pointerId, element, originX: event.clientX, originY: event.clientY, clientX: event.clientX, clientY: event.clientY, offsetX: event.clientX - element.getBoundingClientRect().left, axisStart: axisStart.value, axisEnd: axisEnd.value, active: false, overHall: true, previewLevel: lanes.value.get(row.hallId)?.items.find(item => item.index === index)?.level || 0 }
  element.setPointerCapture(event.pointerId)
  element.addEventListener('lostpointercapture', cancelDrag)
  window.addEventListener('pointermove', pointerMove, { passive: false })
  window.addEventListener('pointerup', pointerUp)
  window.addEventListener('pointercancel', cancelDrag)
  window.addEventListener('keydown', escapeDrag)
}
function updateCandidate() {
  const state = drag.value, scroll = scrollElement.value
  if (!state || !scroll) return
  const bounds = scroll.getBoundingClientRect()
  const track = Array.from(scroll.querySelectorAll<HTMLElement>('.lane-track')).find(element => {
    const rect = element.getBoundingClientRect()
    return state.clientY >= rect.top && state.clientY < rect.bottom
  })
  const hall = props.halls.find(h => h.id === Number(track?.dataset.hallId))
  const overHall = !!track && state.clientX >= bounds.left + 116 && state.clientX < bounds.right
  if (!track || !hall) { drag.value = { ...state, overHall: false, swapIndex: undefined }; return }
  const start = Math.round((state.axisStart + state.clientX - track.getBoundingClientRect().left - state.offsetX) / 5) * 5
  // Pointer capture keeps events on the source, so find the actual destination by its visible rectangle.
  const targetBlock = overHall ? Array.from(track.querySelectorAll<HTMLElement>('.showtime-block')).find(element => {
    const rect = element.getBoundingClientRect()
    return Number(element.dataset.showtimeIndex) !== state.index && state.clientX >= rect.left && state.clientX < rect.right && state.clientY >= rect.top && state.clientY < rect.bottom
  }) : undefined
  const swapIndex = targetBlock ? Number(targetBlock.dataset.showtimeIndex) : undefined
  const plan = swapIndex != null ? buildSwap(state.index, swapIndex) : undefined
  drag.value = { ...state, candidate: plan?.source || shifted(state.row, start, hall), overHall, swapIndex: plan?.targetIndex, previewLevel: hall.id === state.row.hallId ? lanes.value.get(hall.id!)?.items.find(item => item.index === state.index)?.level || 0 : 0 }
}
function pointerMove(event: PointerEvent) {
  const state = drag.value
  if (!state || event.pointerId !== state.pointerId) return
  const active = state.active || Math.hypot(event.clientX - state.originX, event.clientY - state.originY) >= 5
  drag.value = { ...state, clientX: event.clientX, clientY: event.clientY, active }
  if (!active) return
  event.preventDefault()
  updateCandidate()
  if (!animationFrame) { previousFrameTime = 0; animationFrame = requestAnimationFrame(autoScroll) }
}
function autoScroll(now: number) {
  const state = drag.value, scroll = scrollElement.value
  if (!state?.active || !scroll) { animationFrame = 0; return }
  const rect = scroll.getBoundingClientRect()
  const elapsed = previousFrameTime ? Math.min(now - previousFrameTime, 32) / 16 : 1
  previousFrameTime = now
  if (state.clientY >= rect.top && state.clientY <= rect.bottom) {
    const right = Math.max(0, Math.min(1, (state.clientX - rect.right + 48) / 48))
    const left = Math.max(0, Math.min(1, (rect.left + 164 - state.clientX) / 48))
    scroll.scrollLeft += (right - left) * 12 * elapsed
  }
  updateCandidate()
  animationFrame = requestAnimationFrame(autoScroll)
}
function finishDrag() {
  const state = drag.value
  drag.value = undefined
  cancelAnimationFrame(animationFrame); animationFrame = 0
  window.removeEventListener('pointermove', pointerMove)
  window.removeEventListener('pointerup', pointerUp)
  window.removeEventListener('pointercancel', cancelDrag)
  window.removeEventListener('keydown', escapeDrag)
  if (state) {
    state.element.removeEventListener('lostpointercapture', cancelDrag)
    if (state.element.hasPointerCapture(state.pointerId)) state.element.releasePointerCapture(state.pointerId)
    if (state.active) suppressClickUntil = performance.now() + 350
  }
}
function commit(index: number, row: DraftShowtime) {
  const original = props.items[index]
  if (original.hallId === row.hallId && original.startTime === row.startTime) return
  feedbackInvalid.value = false
  feedback.value = `已将「${row.movieTitle}」调整至 ${row.hallName} ${time(row.startTime)}–${time(row.endTime)}，请重新检查草案。`
  emit('move', index, row)
}
function commitSwap(plan: SwapPlan) {
  feedbackInvalid.value = false
  feedback.value = `已交换「${plan.source.movieTitle}」与「${plan.target.movieTitle}」的位置，请重新检查草案。`
  emit('swap', [{ index: plan.sourceIndex, row: plan.source }, { index: plan.targetIndex, row: plan.target }])
}
function pointerUp(event: PointerEvent) {
  if (!drag.value || event.pointerId !== drag.value.pointerId) return
  if (drag.value.active) {
    drag.value = { ...drag.value, clientX: event.clientX, clientY: event.clientY }
    updateCandidate()
  }
  const state = drag.value!, issue = dropIssue.value, plan = swapPreview.value
  finishDrag()
  if (!state.active) return
  if (issue) { feedbackInvalid.value = true; feedback.value = `${issue}；已保留原安排。`; return }
  if (plan) commitSwap(plan)
  else commit(state.index, state.candidate)
}
function cancelDrag() {
  if (drag.value?.active) { feedback.value = '已取消拖动，保留原安排。'; feedbackInvalid.value = false }
  finishDrag()
}
function escapeDrag(event: KeyboardEvent) { if (event.key === 'Escape') { event.preventDefault(); cancelDrag() } }
function clickItem(event: MouseEvent, row: DraftShowtime) {
  if (performance.now() < suppressClickUntil) { event.preventDefault(); return }
  emit('edit', row)
}
function moveWithKeyboard(event: KeyboardEvent, index: number) {
  const row = props.items[index]
  if (!canMove(row) || drag.value || event.altKey || event.ctrlKey || event.metaKey || !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
  event.preventDefault()
  const halls = props.halls.filter(h => h.status === 1)
  const hallIndex = halls.findIndex(h => h.id === row.hallId)
  const vertical = event.key === 'ArrowUp' || event.key === 'ArrowDown'
  const hall = vertical ? halls[hallIndex + (event.key === 'ArrowUp' ? -1 : 1)] : props.halls.find(h => h.id === row.hallId)
  if (!hall) return
  const start = vertical ? minute(row.startTime) : Math.round(minute(row.startTime) / 5) * 5 + (event.key === 'ArrowLeft' ? -5 : 5)
  const candidate = shifted(row, start, hall), issue = issueFor(candidate, index)
  if (issue) { feedbackInvalid.value = true; feedback.value = `${issue}；已保留原安排。`; return }
  commit(index, candidate)
}
watch(() => [props.items, props.targetDate, props.busy, props.readOnly, props.cleaningMinutes], () => { if (drag.value) cancelDrag() })
watch(() => [props.targetDate, props.readOnly], () => { feedback.value = ''; feedbackInvalid.value = false })
onBeforeUnmount(finishDrag)
</script>

<style scoped>
.timeline-help{margin:0;padding:0 22px 12px;color:#728075;font-size:11px;line-height:1.7}
.timeline-scroll{overflow-x:auto;border-top:1px solid #d5dbd1;background:#f3f5ef;scrollbar-color:#b6c2af #ecf0e6}
.timeline{min-width:100%}.time-ruler{height:46px;display:flex;border-bottom:1px solid #d5dbd1}
.hall-corner,.hall-label{width:116px;flex-shrink:0;box-sizing:border-box;padding:16px;position:sticky;left:0;z-index:5;background:#edf1e7;border-right:1px solid #d5dbd1}
.hall-corner{font-size:10px;color:#7f8b76}.ruler-track{position:relative;flex-shrink:0}.ruler-track>span{position:absolute;top:17px;padding-left:7px;font:10px monospace;color:#839079}
.hall-lane{display:flex;border-bottom:1px solid #d5dbd1}.hall-label{display:flex;flex-direction:column;justify-content:center}.hall-label strong{font-size:12px;overflow-wrap:anywhere}.hall-label small{font-size:10px;color:#8a957f;margin-top:6px}
.lane-track{position:relative;flex-shrink:0;overflow:hidden;min-height:108px}.golden-band{position:absolute;top:0;bottom:0;background:rgba(210,179,105,.09)}.grid-line{position:absolute;top:0;bottom:0;border-left:1px solid #e0e5d9}.empty-lane{font-size:11px;color:#9aa58f;padding:30px 24px}
.showtime-slot{position:absolute;height:82px;display:flex}.showtime-block{width:100%;height:82px;flex-shrink:0;z-index:2;text-align:left;border:1px solid color-mix(in srgb,var(--film-color) 40%,transparent);border-left:3px solid var(--film-color);border-radius:6px;background:color-mix(in srgb,var(--film-color) 13%,#f6f8f0);color:#354833;padding:10px 8px;cursor:pointer;display:flex;flex-direction:column;gap:7px;overflow:hidden;transition:filter .15s}
.showtime-block:hover{filter:brightness(.96)}.showtime-block:focus-visible{outline:2px solid #dd8a3f;outline-offset:2px}.showtime-block.locked{background:#e5e8df;border-color:#b6c0ad;border-left-color:#99a68e;color:#75826b}.showtime-block strong,.preview-block strong{font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;width:100%}
.slot-time{font:10px monospace;white-space:nowrap}.slot-meta{display:flex;justify-content:space-between;width:100%;font-size:9px;color:#7c8871}.cleaning-block{height:82px;position:absolute;left:100%;border-radius:0 5px 5px 0;background:repeating-linear-gradient(135deg,#dce3d2,#dce3d2 2px,#edf1e7 2px,#edf1e7 6px);opacity:.8}
.showtime-block.movable{cursor:grab;touch-action:none;user-select:none}.dragging{cursor:grabbing;user-select:none}.dragging .showtime-block{cursor:grabbing}.drag-source{opacity:.3}.drop-target .lane-track{background-color:rgba(82,124,105,.06)}.drop-target .hall-label{background:#dfeadd}
.showtime-block.conflict{outline:2px solid #bd604b;outline-offset:-2px}.drag-preview{z-index:4;pointer-events:none}.preview-block{box-sizing:border-box;width:100%;border:2px solid #527c69;border-radius:6px;background:#e2eee5;box-shadow:0 4px 12px #273b3226;padding:9px 7px;display:flex;flex-direction:column;gap:7px;overflow:hidden;color:#354833;font-size:10px}.drag-preview.invalid .preview-block{border-color:#bd604b;background:#f8e7df;color:#934832}
.showtime-block.swap-target{outline:2px solid #b58b49;outline-offset:2px}.drag-preview.swapping:not(.invalid) .preview-block{border-color:#ae8646;background:#f7eddb;color:#796037}.timeline-feedback.swapping{color:#94703b}
.timeline-feedback{min-height:18px;padding:10px 22px 0;color:#527c69;font-size:11px;line-height:1.7;overflow-wrap:anywhere}.timeline-feedback.invalid{color:#a14e39}
@media(prefers-reduced-motion:reduce){.showtime-block{transition:none}}
</style>
