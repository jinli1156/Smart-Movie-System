<template>
  <section v-if="tasks.length || submitting" class="generation-panel" aria-label="海报生成进度">
    <div class="generation-heading" role="status" aria-live="polite">
      <span class="activity-dot" aria-hidden="true"></span>
      <div><h2>海报正在生成</h2><p>{{ tasks.length ? tasks.length + ' 张海报处理中，完成后将自动出现在素材中。' : '正在提交生成任务…' }}</p></div>
      <span class="background-note">可以离开页面，任务会继续处理</span>
    </div>
    <div class="generation-grid">
      <article v-for="task in tasks" :key="task.id" class="generation-card" :aria-label="movieTitle(task.movieId) + ' · ' + stageLabel(task.status)">
        <div class="canvas-preview" aria-hidden="true">
          <div class="preview-glow"></div><div class="preview-orbit"></div>
          <svg class="preview-icon" viewBox="0 0 48 48" fill="none"><rect x="8" y="10" width="32" height="28" rx="5" /><circle cx="19" cy="20" r="3" /><path d="m10 34 10-9 7 6 6-5 7 8M37 5v8m-4-4h8" /></svg>
          <div class="shimmer"></div>
        </div>
        <div class="card-body">
          <strong>{{ movieTitle(task.movieId) }}</strong><small>{{ task.style }} · {{ task.ratio }}</small>
          <p class="stage-label" role="status">{{ stageLabel(task.status) }}<span class="loading-dots" aria-hidden="true"><i></i><i></i><i></i></span></p>
          <ol class="generation-steps" aria-label="生成步骤"><li v-for="(step, index) in steps" :key="step" :class="{ current: stageIndex(task.status) === index, done: stageIndex(task.status) > index }" :aria-current="stageIndex(task.status) === index ? 'step' : undefined"><span>{{ stageIndex(task.status) > index ? '✓' : index + 1 }}</span>{{ step }}</li></ol>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MarketingMovie, MarketingTask } from '@/api/marketing'
const props = defineProps<{ tasks: MarketingTask[]; movies: MarketingMovie[]; submitting: boolean }>()
const steps = ['构思画面', '生成图片', '保存素材']
const movieTitle = (id: number) => props.movies.find(movie => movie.id === id)?.title || '影片'
const stageIndex = (status: string) => status === 'QUEUED' ? -1 : status === 'PERSISTING' ? 2 : ['SUBMITTING', 'GENERATING'].includes(status) ? 1 : 0
const stageLabel = (status: string) => status === 'QUEUED' ? '等待处理' : status === 'PERSISTING' ? '正在保存素材' : ['SUBMITTING', 'GENERATING'].includes(status) ? '正在生成图片' : '正在构思画面'
</script>

<style scoped>
.generation-panel{margin-bottom:22px;padding:24px;border:1px solid #ebdfd0;border-radius:14px;background:linear-gradient(120deg,#fffbf6,#fff)}
.generation-heading{display:flex;align-items:center;gap:12px}.generation-heading h2{margin:0;font-size:18px;color:#67452d}.generation-heading p{margin:6px 0 0;font-size:13px;line-height:1.6;color:#7c7167}.background-note{margin-left:auto;font-size:12px;color:#8d8177}
.activity-dot{width:9px;height:9px;border-radius:50%;background:#bc7844;box-shadow:0 0 0 5px #bc784419;animation:breath 2s ease-in-out infinite}
.generation-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px;max-height:560px;overflow:auto}.generation-grid:has(.generation-card){margin-top:20px}
.generation-card{display:flex;overflow:hidden;background:#fff;border:1px solid #eee5dc;border-radius:12px}.canvas-preview{position:relative;display:grid;place-items:center;width:100px;min-height:154px;flex-shrink:0;overflow:hidden;background:linear-gradient(155deg,#eadbc7,#f8efe3 50%,#dce3ed)}
.preview-glow{position:absolute;width:120px;height:120px;border-radius:50%;background:radial-gradient(circle,#fffaf0 0,#eac69d99 45%,transparent 70%);animation:breath 3s ease-in-out infinite}.preview-orbit{position:absolute;width:66px;height:66px;border:1px dashed #b7895c80;border-radius:50%;animation:orbit 16s linear infinite}.preview-icon{position:relative;width:46px;height:46px;stroke:#9e744f;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}
.shimmer{position:absolute;inset:0;transform:translateX(-100%);background:linear-gradient(100deg,transparent,#fff9,transparent);animation:shimmer 2.6s ease-in-out infinite}.card-body{padding:18px 14px;min-width:0;flex:1}.card-body strong{display:block;font-size:14px;color:#384358;overflow-wrap:anywhere}.card-body small{display:block;margin-top:5px;font-size:11px;color:#9097a3}.stage-label{display:flex;align-items:center;gap:5px;font-size:13px;color:#a36635;margin:14px 0}
.loading-dots{display:inline-flex;gap:3px}.loading-dots i{width:3px;height:3px;border-radius:50%;background:currentColor;animation:breath 1.2s ease-in-out infinite}.loading-dots i:nth-child(2){animation-delay:.2s}.loading-dots i:nth-child(3){animation-delay:.4s}
.generation-steps{display:flex;flex-wrap:wrap;justify-content:flex-start;gap:6px;list-style:none;margin:0;padding:0}.generation-steps li{display:flex;align-items:center;gap:4px;font-size:10px;color:#9ba3ae;white-space:nowrap}.generation-steps li>span{display:grid;place-items:center;width:15px;height:15px;border:1px solid #e0e4e9;border-radius:50%;font-size:9px}.generation-steps .current{color:#a36635}.generation-steps .current>span{background:#fbf1e4;border-color:#d5ae83}.generation-steps .done{color:#6b8c70}.generation-steps .done>span{background:#edf5ee;border-color:#c8ddcc}
@keyframes shimmer{0%,15%{transform:translateX(-100%)}75%,100%{transform:translateX(100%)}}@keyframes breath{0%,100%{opacity:.45}50%{opacity:1}}@keyframes orbit{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.generation-panel *{animation:none!important}.shimmer{display:none}}
@media(max-width:760px){.generation-panel{padding:18px}.generation-heading{flex-wrap:wrap}.background-note{margin-left:21px}.generation-grid{grid-template-columns:1fr}.generation-card{flex-direction:column}.canvas-preview{width:100%;min-height:100px}.generation-steps{flex-wrap:wrap;justify-content:flex-start;gap:8px}}
</style>
