<template>
  <section v-if="analysis" class="ai-analysis" aria-label="排片分析">
    <div class="ai-heading">
      <strong>{{ analysis.source === 'model' ? '大模型排片分析' : '历史数据排片分析' }}</strong>
      <el-tag :type="analysis.source === 'model' ? 'success' : 'warning'" size="small">
        {{ analysis.source === 'model' ? analysis.model : analysis.source === 'algorithm' ? '可解释历史模型' : analysis.status === 'unconfigured' ? '模型未配置' : '模型暂不可用' }}
      </el-tag>
    </div>
    <p class="ai-summary">{{ analysis.summary }}</p>
    <ol v-if="analysis.actions.length" class="ai-actions">
      <li v-for="item in analysis.actions" :key="item.evidenceId">
        <strong>{{ item.movieTitle }}<span v-if="item.hallName"> · {{ item.hallName }}</span></strong>
        <p>{{ item.advice }}</p>
      </li>
    </ol>
    <ul v-if="analysis.risks.length" class="ai-risks"><li v-for="risk in analysis.risks" :key="risk">{{ risk }}</li></ul>
    <div class="ai-basis">
      <span>数据库历史样本 {{ analysis.dataBasis.historicalSamples }} 场</span>
      <span v-if="analysis.dataBasis.historicalFrom">{{ analysis.dataBasis.historicalFrom.slice(0, 10) }} 至 {{ analysis.dataBasis.historicalTo?.slice(0, 10) }}</span>
      <span v-if="analysis.dataBasis.coldStart">暂无历史样本，预测为冷启动估计</span>
      <span>生成于 {{ new Date(analysis.generatedAt).toLocaleString('zh-CN', { hour12: false }) }}</span>
    </div>
    <details><summary>查看数据与预测口径</summary><p>{{ analysis.dataBasis.occupancyBasis }}</p><p>{{ analysis.dataBasis.predictionBasis }}</p></details>
  </section>
</template>

<script setup lang="ts">
import type { SchedulingAiAnalysis } from '@/api/scheduling'
defineProps<{ analysis?: SchedulingAiAnalysis }>()
</script>

<style scoped>
.ai-analysis{min-width:0;padding:20px;border:1px solid rgba(50,168,121,.25);border-radius:14px;background:var(--admin-card);overflow-wrap:anywhere}.ai-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.ai-heading strong{font-size:16px}.ai-summary{margin:14px 0;line-height:1.8;font-size:14px;white-space:pre-line}.ai-actions{padding-left:22px;margin:12px 0}.ai-actions li{padding:5px 0}.ai-actions strong{font-size:13px}.ai-actions p{margin:4px 0;line-height:1.7;font-size:13px}.ai-risks{padding-left:20px;color:#a46623;font-size:12px;line-height:1.8}.ai-basis{display:flex;flex-wrap:wrap;gap:8px 18px;border-top:1px solid rgba(160,167,160,.2);padding-top:12px;color:var(--admin-muted);font-size:11px}details{margin-top:10px;font-size:11px;color:var(--admin-muted)}summary{cursor:pointer}details p{line-height:1.6}
</style>
