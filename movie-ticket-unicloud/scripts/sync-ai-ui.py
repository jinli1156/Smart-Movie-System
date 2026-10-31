"""Sync the new AI screens while retaining uniCloud's existing API adapters."""
from pathlib import Path
import shutil, re

cloud = Path(__file__).resolve().parent.parent
root = cloud.parent
selections = {
    'movie-ticket-user': [
        'src/api/poster.ts', 'src/api/souvenir.ts', 'src/views/PosterStudio.vue',
        'src/components/SouvenirTicketDialog.vue', 'src/components/MarketingPlacements.vue',
        'src/views/Home.vue', 'src/views/CinemaDetail.vue', 'src/views/OrderDetail.vue', 'src/views/MovieDetail.vue',
        'src/views/OrderList.vue', 'src/views/Assistant.vue', 'src/components/AppSidebar.vue',
        'src/router/index.ts', 'index.html', 'vite.config.ts',
    ],
    'movie-ticket-admin': [
        'src/api/marketing.ts', 'src/api/listing.ts', 'src/components/MarketingWorkspace.vue',
        'src/components/MarketingGeneration.vue', 'src/components/MarketingImage.vue',
        'src/views/ListingWorkspace.vue', 'src/views/MarketingStudio.vue',
        'src/views/MarketingTasks.vue', 'src/views/MarketingReview.vue',
        'src/router/index.ts', 'src/layout/MainLayout.vue', 'src/views/Movie.vue', 'src/views/SmartScheduling.vue', 'index.html', 'vite.config.ts',
    ],
}
for app, names in selections.items():
    source, target = root / app, cloud / 'frontend' / app
    for name in names:
        src, dest = source / name, target / name
        assert src.is_file(), src
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
    # New screens may depend on new small components and composables.
    for folder in ['components', 'composables', 'utils', 'styles']:
        for src in (source / 'src' / folder).rglob('*'):
            if src.is_file():
                dest = target / 'src' / src.relative_to(source / 'src')
                if not dest.exists():
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(src, dest)
    for src in (target / 'src').rglob('*.vue'):
        value = src.read_text(encoding='utf-8')
        value = re.sub(r"@import url\(['\"]https://fonts\.googleapis\.com/[^\n]+\n?", '', value)
        src.write_text(value, encoding='utf-8')
    # All browser requests, including private image fetches, use the cloud API.
    helper = target / 'src/utils/api-url.ts'
    helper.write_text("""export const apiUrl = (url: string) => url.startsWith('/api/') ? (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\\/$/, '') + url.slice(4) : url
export async function encodeFormData(form: FormData) {
  const fields: Record<string, string> = {}, files: { name: string; filename: string; mime: string; base64: string }[] = []
  for (const [name, value] of form.entries()) {
    if (typeof value === 'string') fields[name] = value
    else {
      const encoded = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader(); reader.onerror = () => reject(new Error('文件读取失败'))
        reader.onload = () => resolve(String(reader.result).split(',')[1] || '')
        reader.readAsDataURL(value)
      })
      files.push({ name, filename: value.name || 'blob', mime: value.type, base64: encoded })
    }
  }
  return { fields, files }
}
export async function fetchCloudFile(url: string, options?: RequestInit): Promise<Response> {
  const target = apiUrl(url), response = await fetch(target, options)
  if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return response
  const result = await response.json(), manifest = result.data
  if (result.code !== 200 || !Number.isInteger(manifest?.parts) || manifest.parts < 1 || manifest.parts > 42 || manifest.mime !== 'image/png') throw new Error(result.message || '图片暂不可用')
  const chunks: Blob[] = []
  for (let part = 0; part < manifest.parts; part++) {
    const r = await fetch(target + (target.includes('?') ? '&' : '?') + 'part=' + part, options)
    if (!r.ok || r.headers.get('content-type')?.includes('application/json')) throw new Error('图片分段加载失败，请重新加载')
    chunks.push(await r.blob())
  }
  const blob = new Blob(chunks, { type: manifest.mime })
  if (blob.size !== manifest.size) throw new Error('图片下载不完整，请重新加载')
  return new Response(blob, { headers: { 'Content-Type': manifest.mime } })
}
""", encoding='utf-8')
    for name in ['src/api/poster.ts', 'src/api/souvenir.ts', 'src/components/MarketingPlacements.vue', 'src/components/MarketingImage.vue', 'src/components/MarketingWorkspace.vue']:
        dest = target / name
        if not dest.exists():
            continue
        value = dest.read_text(encoding='utf-8')
        marker = '<script setup lang="ts">'
        value = value.replace(marker, marker + "\nimport { apiUrl } from '@/utils/api-url'", 1) if marker in value else ("import { apiUrl, encodeFormData } from '@/utils/api-url'\n" if name == 'src/api/poster.ts' else "import { apiUrl } from '@/utils/api-url'\n") + value
        if name == 'src/api/poster.ts':
            value = value.replace('create(input: PosterInput, photo: File | null)', 'async create(input: PosterInput, photo: File | null)')
            value = value.replace("('/posters', form,", "('/posters', await encodeFormData(form),")
        value = value.replace('fetch(`/api/posters/${encodeURIComponent(id)}/file`,', 'fetch(apiUrl(`/api/posters/${encodeURIComponent(id)}/file`),')
        value = value.replace('fetch(`/api/souvenirs/${encodeURIComponent(id)}/file`,', 'fetch(apiUrl(`/api/souvenirs/${encodeURIComponent(id)}/file`),')
        value = value.replace("fetch('/api/marketing/placements?' + params,", "fetch(apiUrl('/api/marketing/placements?' + params),")
        value = value.replace('items.value = result.data || []', 'items.value = (result.data || []).map((item: any) => ({ ...item, imageUrl: apiUrl(item.imageUrl) }))')
        value = value.replace('fetch(src,', 'fetch(apiUrl(src),').replace('fetch(asset.previewUrl,', 'fetch(apiUrl(asset.previewUrl),')
        if name != 'src/components/MarketingPlacements.vue':
            value = value.replace('import { apiUrl', 'import { fetchCloudFile, apiUrl')
            value = value.replace('fetch(apiUrl(', 'fetchCloudFile(apiUrl(')
        dest.write_text(value, encoding='utf-8')
    listing_api = target / 'src/api/listing.ts'
    if listing_api.exists():
        value = listing_api.read_text(encoding='utf-8')
        value = "import { encodeFormData } from '@/utils/api-url'\n" + value
        value = value.replace('create: (data: FormData)', 'create: async (data: FormData)').replace("('/listing/import', data,", "('/listing/import', await encodeFormData(data),")
        listing_api.write_text(value, encoding='utf-8')
    request = target / 'src/utils/request.ts'
    value = request.read_text(encoding='utf-8')
    if "response.config.responseType === 'blob'" not in value:
        value = value.replace('const { code, message, data } = response.data', "if (response.config.responseType === 'blob') return response.data\n    const { code, message, data } = response.data")
        request.write_text(value, encoding='utf-8')
    if app.endswith('admin'):
        movie_page = target / 'src/views/Movie.vue'
        value = movie_page.read_text(encoding='utf-8').replace("computed(() => '/api/upload/image')", "computed(() => (import.meta.env.VITE_API_BASE_URL || '/api') + '/upload/image')")
        value = value.replace('5 * 1024 * 1024', '1.4 * 1024 * 1024').replace('超过 5MB', '超过 1.4MB')
        value = value.replace(':action="uploadAction"', ':action="uploadAction" :http-request="cloudPosterUpload"')
        value = value.replace('<script setup lang="ts">', '<script setup lang="ts">\nimport { uploadApi } from \'@/api/upload\'')
        value = value.replace('// 上传相关', "const cloudPosterUpload: UploadProps['httpRequest'] = async options => ({ code: 200, data: await uploadApi.uploadImage(options.file) })\n// 上传相关")
        movie_page.write_text(value, encoding='utf-8')
        upload_api = target / 'src/api/upload.ts'
        value = upload_api.read_text(encoding='utf-8')
        if 'encodeFormData' not in value:
            value = "import { encodeFormData } from '@/utils/api-url'\n" + value
            value = value.replace('uploadImage(file: File)', 'async uploadImage(file: File)').replace("('/upload/image', formData)", "('/upload/image', await encodeFormData(formData))")
            upload_api.write_text(value, encoding='utf-8')
        for page, kind in [('Cinema', 'CINEMA'), ('Hall', 'HALL')]:
            file = target / 'src/views' / (page + '.vue')
            value = file.read_text(encoding='utf-8')
            link = f'/listing?kind={kind}'
            if link not in value:
                label = '影院管理' if page == 'Cinema' else '影厅管理'
                condition = ' v-if="userStore.userInfo?.role === \'ADMIN\'"' if page == 'Cinema' else ''
                value = value.replace(f'<span>{label}</span>', f'<span>{label}</span><router-link{condition} to="{link}"><el-button>AI 辅助上架</el-button></router-link>', 1)
                file.write_text(value, encoding='utf-8')
        router = target / 'src/router/index.ts'
        value = router.read_text(encoding='utf-8').replace('createWebHistory', 'createWebHashHistory')
        router.write_text(value, encoding='utf-8')
        vite = target / 'vite.config.ts'
        value = vite.read_text(encoding='utf-8').replace('export default defineConfig({', "export default defineConfig({\n  base: process.env.NODE_ENV === 'production' ? '/admin/' : '/',", 1)
        vite.write_text(value, encoding='utf-8')
        listing = target / 'src/views/ListingWorkspace.vue'
        value = listing.read_text(encoding='utf-8').replace('5MB', '1.3MB').replace('8MB', '1.3MB').replace('5 * 1024 * 1024', '1.3 * 1024 * 1024').replace('8 * 1024 * 1024', '1.3 * 1024 * 1024')
        value = value.replace("import { computed, onMounted, ref } from 'vue'", "import { computed, onMounted, ref, watch } from 'vue'")
        value = value.replace("onBeforeRouteLeave, useRoute", "onBeforeRouteLeave, onBeforeRouteUpdate, useRoute")
        value = value.replace('onBeforeRouteLeave(canLeave)', '''onBeforeRouteUpdate(async to => {
  if (draft.value && dirty.value && to.query.draft !== draft.value.id) return canLeave()
})
watch(() => route.query.draft, async id => {
  if (typeof id === 'string' && id !== draft.value?.id) await openDraft(id)
  else if (!id && draft.value) { draft.value = undefined; editFields.value = {}; batchItems.value = []; error.value = ''; await loadDrafts() }
})
onBeforeRouteLeave(canLeave)''')
        listing.write_text(value, encoding='utf-8')
    else:
        poster = target / 'src/views/PosterStudio.vue'
        value = poster.read_text(encoding='utf-8').replace('5MB','1.4MB').replace('5 * 1024 * 1024','1.4 * 1024 * 1024')
        poster.write_text(value, encoding='utf-8')
    print('Synced AI features:', app)

# Retain quota-aware polling when rebuilding the cloud copy from local screens.
import runpy
runpy.run_path(str(cloud / 'scripts/limit-ai-polling.py'), run_name='__main__')
runpy.run_path(str(cloud / 'scripts/adapt-demand-ui.py'), run_name='__main__')
runpy.run_path(str(cloud / 'scripts/replace-home-box-office.py'), run_name='__main__')
