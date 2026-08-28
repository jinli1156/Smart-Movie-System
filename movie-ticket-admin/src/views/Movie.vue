<template>
  <div class="movie-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>电影管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加电影
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索电影名称、导演、演员"
          clearable
          style="width: 300px; margin-right: 10px;"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.status"
          placeholder="状态"
          clearable
          style="width: 150px; margin-right: 10px;"
          @change="handleSearch"
        >
          <el-option label="全部" :value="undefined" />
          <el-option label="即将上映" :value="0" />
          <el-option label="正在热映" :value="1" />
          <el-option label="已下架" :value="2" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <!-- 电影列表 -->
      <el-table :data="movieList" v-loading="loading" style="margin-top: 20px;">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="海报" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.poster"
              :src="row.poster"
              :preview-src-list="[row.poster]"
              fit="cover"
              style="width: 60px; height: 80px; border-radius: 4px;"
            />
            <span v-else style="color: #999;">暂无海报</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="电影名称" min-width="150" />
        <el-table-column prop="englishTitle" label="英文名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="director" label="导演" width="120" show-overflow-tooltip />
        <el-table-column prop="actors" label="主演" min-width="150" show-overflow-tooltip />
        <el-table-column prop="genre" label="类型" width="120" />
        <el-table-column prop="duration" label="时长" width="100">
          <template #default="{ row }">
            {{ row.duration }}分钟
          </template>
        </el-table-column>
        <el-table-column prop="releaseDate" label="上映日期" width="120" />
        <el-table-column prop="rating" label="评分" width="100">
          <template #default="{ row }">
            <el-rate
              v-if="row.rating"
              :model-value="row.rating / 2"
              disabled
              show-score
              text-color="#ff9900"
            />
            <span v-else style="color: #999;">暂无评分</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info">即将上映</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">正在热映</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger">已下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-dropdown @command="(cmd) => handleStatusChange(row, cmd)">
              <el-button link type="warning">
                <el-icon><Switch /></el-icon>
                状态
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="0" :disabled="row.status === 0">即将上映</el-dropdown-item>
                  <el-dropdown-item :command="1" :disabled="row.status === 1">正在热映</el-dropdown-item>
                  <el-dropdown-item :command="2" :disabled="row.status === 2">已下架</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end;"
        @size-change="loadMovies"
        @current-change="loadMovies"
      />
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电影名称" prop="title">
              <el-input v-model="formData.title" placeholder="请输入电影名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名称" prop="englishTitle">
              <el-input v-model="formData.englishTitle" placeholder="请输入英文名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="导演" prop="director">
              <el-input v-model="formData.director" placeholder="请输入导演" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主演" prop="actors">
              <el-input v-model="formData.actors" placeholder="请输入主演，多个用逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="类型" prop="genre">
              <el-input v-model="formData.genre" placeholder="如：动作/科幻" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="国家" prop="country">
              <el-input v-model="formData.country" placeholder="请输入国家" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="语言" prop="language">
              <el-input v-model="formData.language" placeholder="请输入语言" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="时长" prop="duration">
              <el-input-number
                v-model="formData.duration"
                :min="1"
                :max="500"
                placeholder="分钟"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上映日期" prop="releaseDate">
              <el-date-picker
                v-model="formData.releaseDate"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="评分" prop="rating">
              <el-input-number
                v-model="formData.rating"
                :min="0"
                :max="10"
                :step="0.1"
                :precision="1"
                placeholder="0-10分"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="海报" prop="poster">
          <div style="display: flex; gap: 10px; align-items: flex-start; width: 100%;">
            <el-upload
              class="poster-uploader"
              :action="uploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforePosterUpload"
              :on-success="handlePosterSuccess"
              :on-error="handleUploadError"
              :disabled="uploading"
              accept="image/*"
            >
              <div class="poster-upload-area">
                <el-image
                  v-if="formData.poster"
                  :src="formData.poster"
                  fit="cover"
                  style="width: 120px; height: 160px; border-radius: 4px; cursor: pointer;"
                >
                  <template #error>
                    <div class="image-error">
                      <el-icon><Picture /></el-icon>
                      <span>加载失败</span>
                    </div>
                  </template>
                </el-image>
                <div v-else class="poster-uploader-icon">
                  <el-icon v-if="!uploading"><Plus /></el-icon>
                  <el-icon v-else class="is-loading"><Loading /></el-icon>
                  <div class="upload-text">{{ uploading ? '上传中...' : '点击上传' }}</div>
                </div>
              </div>
            </el-upload>
            <div style="flex: 1;">
              <el-input
                v-model="formData.poster"
                placeholder="或直接输入海报图片URL"
                clearable
                :disabled="uploading"
              >
                <template #append>
                  <el-button
                    v-if="formData.poster"
                    @click="formData.poster = ''"
                    :icon="Delete"
                  >
                    清除
                  </el-button>
                </template>
              </el-input>
              <div style="margin-top: 8px; font-size: 12px; color: #999;">
                支持上传图片或输入URL，建议尺寸：600x800，格式：jpg/png/webp，大小不超过5MB
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="预告片URL" prop="trailer">
          <el-input v-model="formData.trailer" placeholder="请输入预告片视频URL" />
        </el-form-item>

        <el-form-item label="简介" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入电影简介"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="0">即将上映</el-radio>
            <el-radio :value="1">正在热映</el-radio>
            <el-radio :value="2">已下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="电影详情"
      width="800px"
    >
      <el-descriptions :column="2" border v-if="currentMovie">
        <el-descriptions-item label="电影ID">{{ currentMovie.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="currentMovie.status === 0" type="info">即将上映</el-tag>
          <el-tag v-else-if="currentMovie.status === 1" type="success">正在热映</el-tag>
          <el-tag v-else-if="currentMovie.status === 2" type="danger">已下架</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="电影名称">{{ currentMovie.title }}</el-descriptions-item>
        <el-descriptions-item label="英文名称">{{ currentMovie.englishTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="导演">{{ currentMovie.director || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主演">{{ currentMovie.actors || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentMovie.genre || '-' }}</el-descriptions-item>
        <el-descriptions-item label="国家">{{ currentMovie.country || '-' }}</el-descriptions-item>
        <el-descriptions-item label="语言">{{ currentMovie.language || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ currentMovie.duration }}分钟</el-descriptions-item>
        <el-descriptions-item label="上映日期">{{ currentMovie.releaseDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评分">
          <el-rate
            v-if="currentMovie.rating"
            :model-value="currentMovie.rating / 2"
            disabled
            show-score
            text-color="#ff9900"
          />
          <span v-else>暂无评分</span>
        </el-descriptions-item>
        <el-descriptions-item label="海报" :span="2">
          <el-image
            v-if="currentMovie.poster"
            :src="currentMovie.poster"
            :preview-src-list="[currentMovie.poster]"
            fit="cover"
            style="width: 150px; height: 200px; border-radius: 4px;"
          />
          <span v-else>暂无海报</span>
        </el-descriptions-item>
        <el-descriptions-item label="预告片" :span="2">
          <el-link v-if="currentMovie.trailer" :href="currentMovie.trailer" target="_blank" type="primary">
            查看预告片
          </el-link>
          <span v-else>暂无预告片</span>
        </el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">
          {{ currentMovie.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentMovie.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ currentMovie.updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, View, Edit, Switch, Delete, Picture, Loading } from '@element-plus/icons-vue'
import type { UploadProps } from 'element-plus'
import { movieApi } from '@/api/movie'
import { uploadApi } from '@/api/upload'
import { useUserStore } from '@/stores/user'
import type { Movie } from '@/types'

const userStore = useUserStore()

const loading = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const currentMovie = ref<Movie | null>(null)

const searchForm = reactive({
  keyword: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const movieList = ref<Movie[]>([])
const formRef = ref<FormInstance>()

const formData = reactive<Movie>({
  title: '',
  englishTitle: '',
  director: '',
  actors: '',
  genre: '',
  country: '',
  language: '',
  duration: 90,
  releaseDate: '',
  poster: '',
  trailer: '',
  description: '',
  rating: undefined,
  status: 1
})

const formRules: FormRules = {
  title: [
    { required: true, message: '请输入电影名称', trigger: 'blur' }
  ],
  duration: [
    { required: true, message: '请输入电影时长', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

const loadMovies = async () => {
  loading.value = true
  try {
    const res = await movieApi.getPage({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status
    })
    movieList.value = res.records
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('加载电影列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadMovies()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = undefined
  pagination.page = 1
  loadMovies()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加电影'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Movie) => {
  isEdit.value = true
  dialogTitle.value = '编辑电影'
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleView = (row: Movie) => {
  currentMovie.value = row
  viewDialogVisible.value = true
}

const handleDelete = async (row: Movie) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除电影"${row.title}"吗？删除后将无法恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await movieApi.delete(row.id!)
    ElMessage.success('删除成功')
    loadMovies()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

const handleStatusChange = async (row: Movie, status: number) => {
  try {
    await movieApi.updateStatus(row.id!, status)
    ElMessage.success('状态更新成功')
    loadMovies()
  } catch (error) {
    ElMessage.error('状态更新失败')
    console.error(error)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (isEdit.value) {
        await movieApi.update(formData.id!, formData)
        ElMessage.success('更新成功')
      } else {
        await movieApi.create(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadMovies()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      console.error(error)
    } finally {
      submitting.value = false
    }
  })
}

const resetForm = () => {
  Object.assign(formData, {
    title: '',
    englishTitle: '',
    director: '',
    actors: '',
    genre: '',
    country: '',
    language: '',
    duration: 90,
    releaseDate: '',
    poster: '',
    trailer: '',
    description: '',
    rating: undefined,
    status: 1
  })
  formRef.value?.clearValidate()
}

// 上传相关
const uploadAction = computed(() => '/api/upload/image')
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${userStore.token}`
}))

const beforePosterUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const isImage = rawFile.type.startsWith('image/')
  const isLt5M = rawFile.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!')
    return false
  }
  uploading.value = true
  return true
}

const handlePosterSuccess: UploadProps['onSuccess'] = (response) => {
  uploading.value = false
  if (response.code === 200) {
    formData.poster = response.data.url
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadError: UploadProps['onError'] = () => {
  uploading.value = false
  ElMessage.error('上传失败，请重试')
}

onMounted(() => {
  loadMovies()
})
</script>

<style scoped>
.movie-page {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.poster-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}

.poster-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.poster-upload-area {
  width: 120px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.poster-uploader-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
}

.poster-uploader-icon .el-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.poster-uploader-icon .upload-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
}

.image-error .el-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.image-error span {
  font-size: 12px;
}
</style>
