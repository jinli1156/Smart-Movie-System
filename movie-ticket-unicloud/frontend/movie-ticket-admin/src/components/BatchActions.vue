<template>
  <div class="batch-actions" aria-label="批量操作">
    <span aria-live="polite">已选 <strong>{{ rows.length }}</strong> 项 <small>（仅当前页）</small></span>
    <el-button size="small" :disabled="disabled || busy || confirming || !rows.length" @click="$emit('clear')">取消选择</el-button>
    <el-button v-for="action in statuses" :key="action.value" size="small" :disabled="disabled || busy || confirming || !rows.length" @click="run(action.label, action.value)">{{ action.label }}</el-button>
    <el-button v-if="allowDelete !== false" size="small" type="danger" plain :disabled="disabled || busy || confirming || !rows.length" @click="run('批量删除')">批量删除</el-button>
    <span v-if="busy" role="status">正在处理，请稍候…</span>
  </div>
  <el-alert v-if="result" class="batch-result" :title="result" :type="hasFailures ? 'warning' : 'success'" show-icon @close="result = ''" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'

interface Row { id?: number; status?: number; redeemedCount?: number }
const props = defineProps<{
  rows: Row[]
  disabled: boolean
  statuses: { label: string; value: number }[]
  api: { delete: (id: number) => Promise<unknown>; updateStatus: (id: number, status: number) => Promise<unknown> }
  protectRedeemed?: boolean
  allowDelete?: boolean
}>()
const emit = defineEmits<{ clear: []; busy: [value: boolean]; completed: [deleted: number] }>()
const busy = ref(false)
const confirming = ref(false)
const result = ref('')
const hasFailures = ref(false)

async function run(label: string, status?: number) {
  if (status === undefined && props.allowDelete === false) return
  if (busy.value || confirming.value || props.disabled || !props.rows.length) return
  const selected = props.rows.map(row => ({ ...row }))
  const eligible = selected.filter(row => row.id != null && (status === undefined
    ? !(props.protectRedeemed && (row.redeemedCount || 0) > 0)
    : row.status !== status))
  const skipped = selected.length - eligible.length
  const skipReason = status === undefined ? '已有兑换记录的兑换码保留历史数据，请改用停用。' : '目标状态相同的记录已跳过。'
  if (!eligible.length) {
    result.value = `没有可操作的记录，跳过 ${skipped} 项。${skipReason}`
    hasFailures.value = true
    return
  }
  confirming.value = true
  try {
    try {
      await ElMessageBox.confirm(
        `已选 ${selected.length} 项，将${label.replace('批量', '')} ${eligible.length} 项。${skipped ? `跳过 ${skipped} 项，${skipReason}` : ''}${status === undefined ? '删除后不可恢复。' : ''}`,
        `${label}确认`,
        { type: 'warning', confirmButtonText: '确定执行', cancelButtonText: '取消' }
      )
    } catch { return }
    confirming.value = false
    busy.value = true
    emit('busy', true)
    let succeeded = 0
    const failed: number[] = []
    // Keep request volume bounded and account for each independent result.
    for (const row of eligible) {
      try {
        if (status === undefined) await props.api.delete(row.id!)
        else await props.api.updateStatus(row.id!, status)
        succeeded++
      } catch { failed.push(row.id!) }
    }
    hasFailures.value = failed.length > 0 || skipped > 0
    result.value = `${label}完成：成功 ${succeeded} 项，失败 ${failed.length} 项，跳过 ${skipped} 项。${failed.length ? `失败 ID：${failed.join('、')}，请检查原因后重新选择重试。` : ''}${skipped ? skipReason : ''}`
    emit('clear')
    emit('completed', status === undefined ? succeeded : 0)
  } finally {
    confirming.value = false
    if (busy.value) {
      busy.value = false
      emit('busy', false)
    }
  }
}
</script>

<style scoped>
.batch-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin:16px 0 10px;font-size:13px;color:#606266}
.batch-actions strong{color:#e89024}
.batch-actions > span:first-child{min-width:120px;white-space:nowrap}
.batch-actions strong{display:inline-block;min-width:3ch;text-align:center;font-variant-numeric:tabular-nums}
.batch-actions :deep(.el-button){transition:none!important}
.batch-actions small{color:#909399}
.batch-actions .el-button+.el-button{margin-left:0}
.batch-result{margin-bottom:12px}
</style>
