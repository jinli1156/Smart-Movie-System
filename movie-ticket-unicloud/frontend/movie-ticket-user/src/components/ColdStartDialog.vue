<template>
  <el-dialog
    :model-value="modelValue"
    title="先选三部你喜欢的电影"
    width="720px"
    :close-on-click-modal="false"
    :show-close="false"
    class="cold-start-dialog"
  >
    <p class="cold-intro">这会成为你的初始观影画像，之后浏览、想看、购票和评价都会持续优化推荐。</p>
    <div class="cold-grid">
      <button
        v-for="movie in movies"
        :key="movie.id"
        class="cold-movie"
        :class="{ selected: selectedIds.includes(movie.id) }"
        @click="toggleMovie(movie.id)"
      >
        <span class="cold-poster">
          <img v-if="movie.poster" v-poster :src="movie.poster" :alt="movie.title" />
          <MaterialIcon v-else class="msr" name="movie" />
          <MaterialIcon v-if="selectedIds.includes(movie.id)" class="check" name="check_circle" />
        </span>
        <strong>{{ movie.title }}</strong>
        <small>{{ movie.genre || '剧情' }}</small>
      </button>
    </div>
    <div class="cold-genres">
      <span>也可以补充喜欢的类型（最多 5 个）</span>
      <div>
        <button v-for="genre in genres" :key="genre" :class="{ active: selectedGenres.includes(genre) }" @click="toggleGenre(genre)">
          {{ genre }}
        </button>
      </div>
    </div>
    <template #footer>
      <div class="cold-footer">
        <span>已选择 {{ selectedIds.length }} / 至少 3 部</span>
        <button class="cold-submit" :disabled="selectedIds.length < 3 || submitting" @click="submit">
          {{ submitting ? '生成画像中…' : '生成我的推荐' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import type { Movie } from '@/types'
import { recommendApi } from '@/api/recommend'

const props = defineProps<{ modelValue: boolean; movies: Movie[] }>()
const emit = defineEmits<{ complete: []; 'update:modelValue': [value: boolean] }>()
const selectedIds = ref<number[]>([])
const selectedGenres = ref<string[]>([])
const submitting = ref(false)
const genres = ['动作', '喜剧', '爱情', '科幻', '悬疑', '剧情', '动画', '犯罪', '冒险', '奇幻']

watch(() => props.modelValue, value => {
  if (value) {
    selectedIds.value = []
    selectedGenres.value = []
  }
})

const toggleMovie = (id: number) => {
  const index = selectedIds.value.indexOf(id)
  if (index >= 0) selectedIds.value.splice(index, 1)
  else if (selectedIds.value.length < 10) selectedIds.value.push(id)
}

const toggleGenre = (genre: string) => {
  const index = selectedGenres.value.indexOf(genre)
  if (index >= 0) selectedGenres.value.splice(index, 1)
  else if (selectedGenres.value.length < 5) selectedGenres.value.push(genre)
}

const submit = async () => {
  if (selectedIds.value.length < 3) return
  submitting.value = true
  try {
    await recommendApi.initialize({ movieIds: selectedIds.value, favoriteGenres: selectedGenres.value })
    ElMessage.success('观影画像已生成')
    emit('update:modelValue', false)
    emit('complete')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.cold-intro { margin: 0 0 18px; color: #666; font-size: 13px; line-height: 1.6; }
.cold-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; max-height: 390px; overflow-y: auto; }
.cold-movie { display: flex; flex-direction: column; gap: 6px; padding: 8px; border: 1px solid #ddd; border-radius: 12px; background: #fff; text-align: left; cursor: pointer; }
.cold-movie.selected { border-color: #a855f7; box-shadow: 0 0 0 2px rgba(168, 85, 247, .15); }
.cold-poster { position: relative; display: flex; width: 100%; aspect-ratio: 2/3; align-items: center; justify-content: center; overflow: hidden; border-radius: 8px; background: #19191f; color: #aaa; }
.cold-poster img { width: 100%; height: 100%; object-fit: cover; }
.cold-poster .check { position: absolute; top: 6px; right: 6px; color: #a855f7; background: #fff; border-radius: 50%; }
.cold-movie strong { overflow: hidden; color: #111; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.cold-movie small { color: #777; font-size: 10px; }
.cold-genres { margin-top: 18px; }
.cold-genres > span { color: #666; font-size: 12px; }
.cold-genres > div { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.cold-genres button { padding: 5px 10px; border: 1px solid #ddd; border-radius: 999px; background: #fff; color: #666; cursor: pointer; }
.cold-genres button.active { border-color: #a855f7; background: rgba(168, 85, 247, .1); color: #9333ea; }
.cold-footer { display: flex; align-items: center; justify-content: space-between; color: #666; font-size: 12px; }
.cold-submit { padding: 10px 20px; border: 0; border-radius: 9px; background: #a855f7; color: #fff; font-weight: 700; cursor: pointer; }
.cold-submit:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 720px) { .cold-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
