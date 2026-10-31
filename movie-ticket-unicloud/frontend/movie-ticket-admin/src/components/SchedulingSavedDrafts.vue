<template>
  <el-drawer :model-value="modelValue" class="saved-drafts-drawer" title="暂存排片草案" size="min(600px, 100vw)" @update:model-value="emit('update:modelValue', $event)">
    <template #header="{ titleId }">
      <div class="drafts-heading">
        <span class="drafts-eyebrow">排片工作台 / 草案管理</span>
        <h2 :id="titleId">暂存排片草案</h2>
        <p>把未完成的安排，留到下一次继续。</p>
      </div>
    </template>
    <div class="drafts-content">
      <div class="drafts-notice"><Document aria-hidden="true" /><p>恢复后可继续调整，<strong>重新检查后再应用到影院。</strong></p></div>
      <div class="drafts-toolbar">
        <div class="drafts-filters" role="group" aria-label="筛选暂存草案">
          <button v-for="tab in tabs" :key="tab.value" type="button" :class="{ selected: filter === tab.value }" :aria-pressed="filter === tab.value" @click="filter = tab.value">{{ tab.label }}<span>{{ tab.count }}</span></button>
        </div>
        <button class="drafts-refresh" type="button" :disabled="busy || loading" aria-label="刷新草案列表" @click="emit('refresh')"><Refresh :class="{ spinning: loading }" aria-hidden="true" /><span>{{ loading ? '刷新中' : '刷新' }}</span></button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div v-if="loading && !items.length" class="drafts-loading" role="status"><Refresh class="spinning" aria-hidden="true" />正在读取暂存草案…</div>
      <template v-else-if="filteredItems.length">
        <p class="drafts-list-caption">{{ filter === 'all' ? '可恢复草案优先，按最近暂存排序' : filter === 'available' ? '选择一份草案，接着上次的安排' : '日期超出可排片范围，可删除不再需要的记录' }}</p>
        <div class="drafts-list">
          <article v-for="item in filteredItems" :key="item.id" class="saved-draft-card" :class="{ unavailable: isUnavailable(item.targetDate), current: currentId === item.id }">
            <div class="saved-draft-topline">
              <div class="saved-draft-date"><Calendar aria-hidden="true" /><div><span>排片日期</span><time :datetime="item.targetDate">{{ item.targetDate }}</time></div></div>
              <span class="saved-draft-badge" :class="{ muted: isUnavailable(item.targetDate), editing: currentId === item.id }"><i v-if="!isUnavailable(item.targetDate)" />{{ isUnavailable(item.targetDate) ? unavailableLabel(item.targetDate) : currentId === item.id ? '编辑中' : '可恢复' }}</span>
            </div>
            <h3>{{ item.cinemaName || '已移除影院' }}</h3>
            <p class="saved-draft-time"><Clock aria-hidden="true" /><span>暂存于 <time :datetime="item.savedAt">{{ item.savedAt.replace('T', ' ').slice(0, 19) }}</time></span></p>
            <div class="saved-draft-footer">
              <span class="saved-draft-hint">{{ isUnavailable(item.targetDate) ? '日期已超出可排片范围' : currentId === item.id ? '当前正在编辑这份草案' : '恢复后继续编辑' }}</span>
              <div class="saved-draft-actions">
                <button class="draft-delete" type="button" :disabled="busy" aria-label="删除暂存" @click="emit('delete', item)"><Delete aria-hidden="true" /><span>删除</span></button>
                <button v-if="!isUnavailable(item.targetDate)" class="draft-restore" type="button" :disabled="busy" @click="emit('restore', item)">恢复草案<ArrowRight aria-hidden="true" /></button>
              </div>
            </div>
          </article>
        </div>
      </template>
      <div v-else-if="!error" class="drafts-empty"><div><FolderOpened aria-hidden="true" /></div><h3>{{ items.length ? '这个分类下暂无草案' : '还没有暂存草案' }}</h3><p>{{ items.length ? '切换分类，查看其他暂存记录。' : '在时间轴中完成调整后，点击“暂存草案”即可保留。' }}</p></div>
    </div>
    <template #footer><div class="drafts-privacy"><Lock aria-hidden="true" /><span>仅当前账号可见 · 暂存不会发布场次</span></div></template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, Calendar, Clock, Delete, Document, FolderOpened, Lock, Refresh } from '@element-plus/icons-vue'
import type { SavedSchedulingSummary } from '@/api/scheduling'

const props = defineProps<{
  modelValue: boolean
  items: SavedSchedulingSummary[]
  currentId: string
  loading: boolean
  busy: boolean
  error: string
  isUnavailable: (date: string) => boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  restore: [item: SavedSchedulingSummary]
  delete: [item: SavedSchedulingSummary]
}>()
type Filter = 'all' | 'available' | 'unavailable'
const filter = ref<Filter>('all')
const availableCount = computed(() => props.items.filter(item => !props.isUnavailable(item.targetDate)).length)
const tabs = computed<{ value: Filter; label: string; count: number }[]>(() => [
  { value: 'all', label: '全部', count: props.items.length },
  { value: 'available', label: '可恢复', count: availableCount.value },
  { value: 'unavailable', label: '不可恢复', count: props.items.length - availableCount.value }
])
const filteredItems = computed(() => props.items.filter(item => filter.value === 'all' || (filter.value === 'available' ? !props.isUnavailable(item.targetDate) : props.isUnavailable(item.targetDate)))
  .slice().sort((a, b) => Number(props.isUnavailable(a.targetDate)) - Number(props.isUnavailable(b.targetDate)) || b.savedAt.localeCompare(a.savedAt)))
function unavailableLabel(date: string) {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  return new Date(`${date}T00:00:00`) < today ? '已过期' : '超出范围'
}
watch(() => props.modelValue, open => { if (open) filter.value = 'all' })
</script>

<style scoped>
:global(.saved-drafts-drawer.el-drawer){background:#f5f6f2;color:#303b34;box-shadow:-12px 0 48px #26372b1a}
:global(.saved-drafts-drawer .el-drawer__header){flex-shrink:0;margin:0;padding:30px 28px 24px;border-bottom:1px solid #e0e5dc;background:#fbfcf9;align-items:flex-start}
:global(.saved-drafts-drawer .el-drawer__close-btn){margin-top:0;padding:7px;border-radius:9px;color:#778177;transition:background .15s}
:global(.saved-drafts-drawer .el-drawer__close-btn:hover){background:#e9ede5;color:#354332}
:global(.saved-drafts-drawer .el-drawer__body){padding:0;overflow-x:hidden}
:global(.saved-drafts-drawer .el-drawer__footer){padding:18px 28px;border-top:1px solid #e0e5dc;background:#fbfcf9}
.drafts-heading{min-width:0}.drafts-eyebrow{font-size:11px;font-weight:600;letter-spacing:.06em;color:#7a8777}.drafts-heading h2{margin:10px 0 8px;font-size:23px;font-weight:700;line-height:1.4;letter-spacing:-.6px;color:#303b34}.drafts-heading>p{font-size:13px;line-height:1.7;color:#7b857a}
.drafts-content{padding:22px 28px 28px;min-width:0}.drafts-notice{display:flex;align-items:flex-start;gap:10px;border:1px solid #e2e8dc;border-radius:12px;padding:13px 14px;background:#edf1e8;color:#707e67}.drafts-notice>svg{width:17px;height:17px;flex-shrink:0;margin-top:2px}.drafts-notice p{margin:0;font-size:12px;line-height:1.8}.drafts-notice strong{font-weight:500;color:#52664c}
.drafts-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:24px 0 16px}.drafts-filters{display:flex;gap:3px;padding:4px;background:#e9ede5;border-radius:10px;min-width:0}.drafts-filters button{display:flex;align-items:center;gap:6px;padding:8px 10px;border:0;border-radius:7px;background:transparent;font-size:12px;white-space:nowrap;color:#7d8877;cursor:pointer}.drafts-filters button.selected{background:#fcfdf9;color:#34492e;box-shadow:0 1px 4px #36472b12;font-weight:600}.drafts-filters button span{font-size:11px;min-width:14px;text-align:center;color:#8a9583}.drafts-filters button.selected span{color:#58734e}
.drafts-refresh{display:flex;align-items:center;gap:6px;padding:8px 0 8px 8px;border:0;background:transparent;color:#75836b;font-size:12px;white-space:nowrap;cursor:pointer}.drafts-refresh>svg{width:15px;height:15px}.drafts-refresh:disabled{opacity:.5;cursor:wait}.drafts-list-caption{margin:0 0 13px;font-size:11px;line-height:1.7;color:#90998a}.drafts-list{display:flex;flex-direction:column;gap:14px}
.saved-draft-card{min-width:0;padding:20px;border:1px solid #dce3d5;border-radius:14px;background:#fcfdf9;box-shadow:0 2px 5px #354b251f}.saved-draft-card.current{border-color:#d8b47f;box-shadow:0 0 0 1px #eddbbd55}.saved-draft-topline{display:flex;justify-content:space-between;align-items:center;gap:12px}.saved-draft-date{display:flex;align-items:center;gap:11px;min-width:0}.saved-draft-date>svg{width:37px;height:37px;padding:9px;border:1px solid #e4e9dc;border-radius:10px;background:#f0f3eb;color:#738b65;flex-shrink:0}.saved-draft-date>div>span{display:block;font-size:11px;color:#8b9782;line-height:1.5;margin-bottom:3px}.saved-draft-date time{font:600 19px/1.3 'Geist',Consolas,monospace;letter-spacing:-.5px;color:#3b4c34;white-space:nowrap}
.saved-draft-badge{display:inline-flex;align-items:center;gap:5px;flex-shrink:0;padding:5px 9px;border-radius:6px;background:#edf3e7;font-size:11px;font-weight:600;color:#637d52;white-space:nowrap}.saved-draft-badge i{width:4px;height:4px;border-radius:50%;background:currentColor}.saved-draft-badge.editing{background:#fbefdb;color:#a8752e}.saved-draft-badge.muted{background:#eaece6;color:#949b8d}
.saved-draft-card h3{margin:18px 0 8px;font-size:15px;font-weight:600;line-height:1.6;overflow-wrap:anywhere;color:#46513e}.saved-draft-time{display:flex;align-items:center;gap:6px;margin:0;color:#7b886f;font-size:12px;line-height:1.7;flex-wrap:wrap}.saved-draft-time>svg{width:13px;height:13px;flex-shrink:0}.saved-draft-time time{font-variant-numeric:tabular-nums}
.saved-draft-footer{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-top:17px;padding-top:15px;border-top:1px solid #e9ede3}.saved-draft-hint{font-size:11px;color:#7d896f;line-height:1.6}.saved-draft-actions{display:flex;align-items:center;gap:12px;margin-left:auto}.saved-draft-actions button{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:36px;font-size:12px;cursor:pointer}.saved-draft-actions svg{width:13px;height:13px}.draft-delete{padding:6px 0;border:0;background:transparent;color:#929b87}.draft-delete:hover{color:#ad5e4d}.draft-restore{padding:8px 12px;border:1px solid #e6c38e;border-radius:8px;background:#f8e9cf;color:#94672b;font-weight:600}.draft-restore:hover{border-color:#cea362;background:#f4e0bb}.saved-draft-actions button:disabled{opacity:.5;cursor:not-allowed}.saved-draft-card.unavailable{background:#f0f2ec;border-color:#e1e5da;box-shadow:none}.unavailable .saved-draft-date time,.unavailable h3{color:#7c8770}.unavailable .saved-draft-date>svg{background:#e9ede2;border-color:#e1e6d8;color:#929f83}
.drafts-privacy{display:flex;align-items:center;justify-content:center;gap:7px;color:#949e89;font-size:11px;line-height:1.7}.drafts-privacy>svg{width:13px;height:13px}.drafts-empty,.drafts-loading{text-align:center;padding:64px 12px;color:#98a18e}.drafts-empty>div{display:grid;place-items:center;width:64px;height:64px;border-radius:18px;background:#e9eee1;margin:0 auto 20px}.drafts-empty svg{width:28px;height:28px;color:#91a27c}.drafts-empty h3{font-size:15px;color:#6f7e60;font-weight:500;margin:0 0 8px}.drafts-empty p{font-size:12px;line-height:1.8;margin:0}.drafts-loading>svg{display:block;width:24px;height:24px;margin:0 auto 12px}.spinning{animation:draft-spin 1s linear infinite}
.drafts-content button:focus-visible{outline:2px solid #c18d45;outline-offset:3px}@keyframes draft-spin{to{transform:rotate(360deg)}}
@media(max-width:480px){:global(.saved-drafts-drawer .el-drawer__header){padding:24px 20px 20px}:global(.saved-drafts-drawer .el-drawer__footer){padding:16px 20px}.drafts-content{padding:18px 16px 24px}.drafts-heading h2{font-size:21px}.drafts-toolbar{gap:8px}.drafts-filters button{padding:8px;gap:4px;font-size:11px}.drafts-refresh{padding-left:0}.saved-draft-card{padding:17px 16px}.saved-draft-date time{font-size:18px}.saved-draft-date>svg{width:33px;height:33px;padding:8px}.saved-draft-date{gap:8px}.saved-draft-topline{gap:8px}.saved-draft-footer{gap:10px}.saved-draft-hint{flex-basis:100%}}
@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
</style>
