"""Keep the uniCloud copy's request timeouts and on-demand behavior across UI syncs."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent/'frontend'
for name in ['movie-ticket-user/src/api/poster.ts','movie-ticket-user/src/api/souvenir.ts']:
    file=root/name
    value=file.read_text(encoding='utf-8').replace('timeout: 30_000','timeout: 110_000').replace('{ silent: true }','{ silent: true, timeout: 110_000 }')
    file.write_text(value,encoding='utf-8')
file=root/'movie-ticket-admin/src/api/marketing.ts'
value=file.read_text(encoding='utf-8').replace('textConfigured: boolean; posterContentRequired','textConfigured: boolean; executionMode?: string; posterContentRequired')
value=value.replace("base + '/campaigns/' + id),","base + '/campaigns/' + id, { timeout: 110_000 }),").replace('{ kind, requestKey, imageCount, posterContent })','{ kind, requestKey, imageCount, posterContent }, { timeout: 110_000 })').replace('{ acknowledgePossibleCharge })','{ acknowledgePossibleCharge }, { timeout: 110_000 })')
if "GENERATING: '模型生成中'" not in value:value=value.replace("SUBMITTING: '生成中'","GENERATING: '模型生成中', SUBMITTING: '提交模型中'")
file.write_text(value,encoding='utf-8')
file=root/'movie-ticket-admin/src/components/MarketingWorkspace.vue'
value=file.read_text(encoding='utf-8').replace("'SUBMITTING','PERSISTING'","'SUBMITTING','GENERATING','PERSISTING'").replace("'SUBMITTING', 'PERSISTING'","'SUBMITTING', 'GENERATING', 'PERSISTING'").replace('生成任务已创建，可离开页面后回来查看','任务已保存；已提交图片会继续生成，排队任务返回页面后继续处理')
if "capabilities?.executionMode" not in value:
    value=value.replace('    <div class="campaign-bar">','    <el-alert v-if="capabilities?.executionMode === \'demand\'" class="notice" :closable="false" type="info" title="按需创作：排队任务在本页继续提交，已提交图片离开后仍会生成。请在24小时内回来查看并保存。" />\n    <div class="campaign-bar">')
file.write_text(value,encoding='utf-8')
file=root/'movie-ticket-admin/src/components/MarketingGeneration.vue'
file.write_text(file.read_text(encoding='utf-8').replace("status === 'SUBMITTING' ?","['SUBMITTING', 'GENERATING'].includes(status) ?"),encoding='utf-8')
file=root/'movie-ticket-user/src/views/PosterStudio.vue'
value=file.read_text(encoding='utf-8').replace('你可以离开，稍后回来继续查看。','已提交的图片会继续生成，请在24小时内回来查看并保存。').replace('AI 创作需等待几分钟','提交成功后可离开，请在24小时内回来保存')
value=value.replace('v-model="prompt" maxlength="1000"','v-model="prompt" :maxlength="promptLimit"').replace('{{ prompt.length }}/1000','{{ prompt.length }}/{{ promptLimit }}')
if 'const promptLimit =' not in value:
    value=value.replace("const imageUrl = ref('')", "const promptLimit = computed(() => photo.value ? 1000 : 700)\nconst imageUrl = ref('')")
value=value.replace('capabilities.remaining <= 0 || (!movieTitle.trim()', 'capabilities.remaining <= 0 || prompt.length > promptLimit || (!movieTitle.trim()')
file.write_text(value,encoding='utf-8')
file=root/'movie-ticket-user/src/components/SouvenirTicketDialog.vue'
file.write_text(file.read_text(encoding='utf-8').replace('可以关闭窗口，稍后从订单回来查看。','已提交图片会继续生成，请在24小时内从订单回来查看并保存。'),encoding='utf-8')
print('On-demand UI notices, progress states and bounded request timeouts prepared.')
