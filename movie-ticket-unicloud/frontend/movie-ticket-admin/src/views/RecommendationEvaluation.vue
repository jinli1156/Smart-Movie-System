<template>
  <div class="evaluation-page">
    <el-card>
      <template #header>
        <div class="heading"><div><h2>推荐算法评估</h2><p>对比云端规则推荐、评分基线与消融实验，评估协议以本次接口结果为准。</p></div></div>
      </template>
      <div class="controls">
        <label for="evaluation-k">推荐数量 K</label>
        <el-select id="evaluation-k" v-model="k" :disabled="loading" aria-label="推荐数量 K" style="width: 100px">
          <el-option v-for="value in [3, 5, 6, 10]" :key="value" :value="value" :label="String(value)" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="run">运行评估</el-button>
        <el-button :disabled="!result || loading" @click="download">导出实验结果</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div v-loading="loading" class="results" aria-live="polite">
        <template v-if="result">
          <div class="summary">
            <div><span>本次 K 值</span><strong>{{ result.k }}</strong></div>
            <div><span>有效用户</span><strong>{{ result.evaluatedUsers }}</strong></div>
            <div><span>跳过用户</span><strong>{{ result.skippedUsers }}</strong></div>
            <div><span>生成时间</span><strong class="timestamp">{{ result.generatedAt?.replace('T', ' ') }}</strong></div>
          </div>
          <el-alert v-if="!result.evaluatedUsers" title="有效样本不足：当前结果不能用于判断算法优劣。请准备包含历史行为和对应可售场次的数据后重新评估。" type="warning" :closable="false" show-icon />
          <el-alert v-if="result.k !== k" title="下方保留的是上一次结果，点击运行评估以应用新的 K 值。" type="info" :closable="false" />
          <el-table :data="rows" stripe style="width: 100%" aria-label="推荐实验对照表">
            <el-table-column prop="name" label="实验方案" min-width="160" fixed />
            <el-table-column v-for="metric in metrics" :key="metric.key" :label="metric.label" min-width="125">
              <template #default="{ row }">{{ result.evaluatedUsers ? format(row[metric.key]) : '—' }}</template>
            </el-table-column>
            <el-table-column label="Recall 相对完整模型差值" min-width="190">
              <template #default="{ row }">{{ result.evaluatedUsers && row.key !== 'HYBRID' ? format(row.recallChangeVsFull) : '—' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="实验说明" min-width="260" />
          </el-table>
          <div class="protocol"><h3>评估协议</h3><p>{{ result.protocol }}</p><p>{{ result.candidatePolicy }}</p>
            <p>离线指标反映当前数据集上的排序表现，不代表线上点击率或购票转化率。论文实验请同时保存数据快照、K 值、有效用户数与跳过用户数。</p>
          </div>
        </template>
        <el-empty v-else-if="!loading" description="选择 K 值并运行评估，查看真实接口结果" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { evaluateRecommendations, type Evaluation } from '@/api/recommend'
const k = ref(6)
const loading = ref(false)
const result = ref<Evaluation | null>(null)
const error = ref('')
const metrics = [
  { key: 'precisionAtK', label: 'Precision@K' }, { key: 'recallAtK', label: 'Recall@K' },
  { key: 'ndcgAtK', label: 'NDCG@K' }, { key: 'catalogCoverage', label: '目录覆盖率' },
  { key: 'diversity', label: '列表多样性' }
]
const rows = computed(() => result.value ? [
  { ...result.value, key: 'HYBRID', name: '当前云端规则推荐', description: '类型偏好、评分与行为信号的加权排序；与 Spring Boot 混合算法不同' },
  ...(result.value.baselines || []), ...(result.value.ablations || [])
] : [])
const format = (value: unknown) => Number.isFinite(Number(value)) ? Number(value).toFixed(4) : '—'
async function run() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  result.value = null
  try { result.value = await evaluateRecommendations(k.value) }
  catch { error.value = '评估失败，请检查服务和数据后重试。' }
  finally { loading.value = false }
}
function download() {
  if (!result.value) return
  const url = URL.createObjectURL(new Blob([JSON.stringify(result.value, null, 2)], { type: 'application/json;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `recommendation-evaluation-k${result.value.k}.json`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<style scoped>
.heading h2{margin:0;font-size:22px}.heading p,.protocol p{color:var(--admin-muted);line-height:1.8}
.controls{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:24px}.results{min-height:220px}
.summary{display:grid;grid-template-columns:repeat(3,1fr) 2fr;gap:16px;margin:24px 0}
.summary>div{padding:18px;border-radius:12px;background:var(--admin-bg)}.summary span{display:block;color:var(--admin-muted);font-size:13px}
.summary strong{display:block;margin-top:10px;font-size:26px}.summary .timestamp{font-size:15px}.protocol{margin-top:24px}.el-alert{margin:12px 0}
@media(max-width:800px){.summary{grid-template-columns:1fr 1fr}.controls{align-items:flex-start}}
</style>
