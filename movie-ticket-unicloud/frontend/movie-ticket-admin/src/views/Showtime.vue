<template>
  <div class="showtime-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>排片管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加排片
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-radio-group v-model="searchForm.status" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">可售</el-radio-button>
          <el-radio-button :value="1">已结束</el-radio-button>
          <el-radio-button :value="2">已取消</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="searchForm.movieId"
          placeholder="选择电影"
          clearable
          filterable
          style="width: 200px;"
          @change="handleSearch"
        >
          <el-option
            v-for="m in movieOptions"
            :key="m.id"
            :label="m.title"
            :value="m.id!"
          />
        </el-select>
        <el-select
          v-model="searchForm.cinemaId"
          placeholder="选择影院"
          clearable
          filterable
          style="width: 200px;"
          @change="handleSearch"
        >
          <el-option
            v-for="c in cinemaOptions"
            :key="c.id"
            :label="c.name"
            :value="c.id!"
          />
        </el-select>
        <el-date-picker
          v-model="searchForm.date"
          type="date"
          placeholder="选择日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 180px;"
          @change="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <!-- 排片列表 -->
      <BatchActions :rows="selectedRows" :disabled="loading" :api="showtimeApi" :statuses="[{ label: '批量取消排片', value: 2 }]"
        @clear="batchTable?.clearSelection()" @busy="batchBusy = $event" @completed="handleBatchCompleted" />
      <el-table ref="batchTable" row-key="id" @selection-change="selectedRows = $event" :data="showtimeList" v-loading="loading || batchBusy" style="margin-top: 20px;">
        <el-table-column type="selection" width="48" :selectable="() => !loading && !batchBusy" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="电影" min-width="200">
          <template #default="{ row }">
            <div class="movie-cell">
              <el-image
                v-if="row.moviePoster"
                :src="row.moviePoster"
                lazy
                fit="cover"
                style="width: 40px; height: 56px; border-radius: 2px;"
              />
              <div class="movie-info">
                <div class="movie-title">{{ row.movieTitle }}</div>
                <div class="movie-meta">{{ row.movieDuration }}分钟</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="cinemaName" label="影院" min-width="150" show-overflow-tooltip />
        <el-table-column prop="hallName" label="影厅" width="120" />
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="basePrice" label="票价" width="100">
          <template #default="{ row }">
            <span class="price">¥{{ row.basePrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="座位" width="120">
          <template #default="{ row }">
            <span :class="{ 'low-seat': (row.availableSeats || 0) < 10 }">
              {{ row.availableSeats }} / {{ row.totalSeats }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="success">可售</el-tag>
            <el-tag v-else-if="row.status === 1" type="info">已结束</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              link
              type="warning"
              v-if="row.status !== 2"
              @click="handleCancel(row)"
            >
              <el-icon><Close /></el-icon>
              取消
            </el-button>
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
        @size-change="loadShowtimes"
        @current-change="loadShowtimes"
      />
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="电影" prop="movieId">
          <el-select
            v-model="formData.movieId"
            placeholder="请选择电影"
            filterable
            style="width: 100%;"
            @change="handleMovieChange"
          >
            <el-option
              v-for="m in movieOptions"
              :key="m.id"
              :label="`${m.title}（${m.duration}分钟）`"
              :value="m.id!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="影院" prop="cinemaId">
          <el-select
            v-model="formData.cinemaId"
            placeholder="请选择影院"
            filterable
            style="width: 100%;"
            @change="handleCinemaChange"
          >
            <el-option
              v-for="c in cinemaOptions"
              :key="c.id"
              :label="c.name"
              :value="c.id!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="影厅" prop="hallId">
          <el-select
            v-model="formData.hallId"
            placeholder="请先选择影院"
            filterable
            style="width: 100%;"
            :disabled="!formData.cinemaId"
          >
            <el-option
              v-for="h in hallOptions"
              :key="h.id"
              :label="`${h.name}（${h.totalRows * h.totalCols}座）`"
              :value="h.id!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="formData.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
            @change="handleStartTimeChange"
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="formData.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            选择开始时间和电影后会自动计算
          </div>
        </el-form-item>

        <el-form-item label="基础票价" prop="basePrice">
          <el-input-number
            v-model="formData.basePrice"
            :min="0"
            :max="9999"
            :step="5"
            :precision="2"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import BatchActions from '@/components/BatchActions.vue'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, Close } from '@element-plus/icons-vue'
import { showtimeApi } from '@/api/showtime'
import { movieApi } from '@/api/movie'
import { cinemaApi } from '@/api/cinema'
import { hallApi } from '@/api/hall'
import type { Showtime, Movie, Cinema, Hall } from '@/types'

const batchTable = ref<{ clearSelection: () => void }>()
const selectedRows = ref<Showtime[]>([])
const batchBusy = ref(false)
const handleBatchCompleted = async (deleted: number) => {
  if (deleted > 0 && deleted >= showtimeList.value.length && pagination.page > 1) pagination.page--
  await loadShowtimes()
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const movieOptions = ref<Movie[]>([])
const cinemaOptions = ref<Cinema[]>([])
const hallOptions = ref<Hall[]>([])

const searchForm = reactive({
  movieId: undefined as number | undefined,
  cinemaId: undefined as number | undefined,
  date: '',
  status: 0 as number | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const showtimeList = ref<Showtime[]>([])
const formRef = ref<FormInstance>()

const formData = reactive<Showtime>({
  movieId: 0,
  cinemaId: 0,
  hallId: 0,
  startTime: '',
  endTime: '',
  basePrice: 50
})

const formRules: FormRules = {
  movieId: [{ required: true, message: '请选择电影', trigger: 'change' }],
  cinemaId: [{ required: true, message: '请选择影院', trigger: 'change' }],
  hallId: [{ required: true, message: '请选择影厅', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  basePrice: [{ required: true, message: '请输入基础票价', trigger: 'blur' }]
}

const formatDateTime = (val?: string) => {
  if (!val) return '-'
  return val.replace('T', ' ').slice(0, 16)
}

const loadShowtimes = async () => {
  batchTable.value?.clearSelection()
  selectedRows.value = []
  loading.value = true
  try {
    const res = await showtimeApi.getPage({
      page: pagination.page,
      size: pagination.size,
      movieId: searchForm.movieId,
      cinemaId: searchForm.cinemaId,
      date: searchForm.date || undefined,
      status: searchForm.status
    })
    showtimeList.value = res.records
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('加载排片列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  try {
    const [movies, cinemas] = await Promise.all([
      movieApi.getPage({ page: 1, size: 1000 }),
      cinemaApi.getAll()
    ])
    movieOptions.value = movies.records
    cinemaOptions.value = cinemas
  } catch (error) {
    console.error('加载选项失败:', error)
  }
}

const loadHalls = async (cinemaId: number) => {
  try {
    hallOptions.value = await hallApi.getByCinemaId(cinemaId)
  } catch (error) {
    console.error('加载影厅失败:', error)
    hallOptions.value = []
  }
}

const handleMovieChange = () => {
  recalculateEndTime()
}

const handleCinemaChange = async (cinemaId: number) => {
  formData.hallId = 0
  if (cinemaId) {
    await loadHalls(cinemaId)
  } else {
    hallOptions.value = []
  }
}

const handleStartTimeChange = () => {
  recalculateEndTime()
}

const recalculateEndTime = () => {
  if (!formData.startTime || !formData.movieId) return
  const movie = movieOptions.value.find(m => m.id === formData.movieId)
  if (!movie) return
  const start = new Date(formData.startTime.replace(' ', 'T'))
  const end = new Date(start.getTime() + movie.duration * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  formData.endTime = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())} ${pad(end.getHours())}:${pad(end.getMinutes())}:${pad(end.getSeconds())}`
}

const handleSearch = () => {
  pagination.page = 1
  loadShowtimes()
}

const handleReset = () => {
  searchForm.movieId = undefined
  searchForm.cinemaId = undefined
  searchForm.date = ''
  searchForm.status = 0
  pagination.page = 1
  loadShowtimes()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '添加排片'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: Showtime) => {
  isEdit.value = true
  dialogTitle.value = '编辑排片'
  formData.id = row.id
  formData.movieId = row.movieId
  formData.cinemaId = row.cinemaId
  formData.hallId = row.hallId
  formData.startTime = (row.startTime || '').replace('T', ' ').slice(0, 19)
  formData.endTime = (row.endTime || '').replace('T', ' ').slice(0, 19)
  formData.basePrice = row.basePrice
  if (row.cinemaId) {
    await loadHalls(row.cinemaId)
  }
  dialogVisible.value = true
}

const handleDelete = async (row: Showtime) => {
  try {
    await ElMessageBox.confirm(
      `确定删除排片"${row.movieTitle} - ${formatDateTime(row.startTime)}"吗？`,
      '删除确认',
      { type: 'warning' }
    )
    await showtimeApi.delete(row.id!)
    ElMessage.success('删除成功')
    loadShowtimes()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleCancel = async (row: Showtime) => {
  try {
    await ElMessageBox.confirm('确定取消该排片吗？', '操作确认', { type: 'warning' })
    await showtimeApi.updateStatus(row.id!, 2)
    ElMessage.success('排片已取消')
    loadShowtimes()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await showtimeApi.update(formData.id!, formData)
        ElMessage.success('更新成功')
      } else {
        await showtimeApi.create(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadShowtimes()
    } catch (error) {
      console.error(error)
    } finally {
      submitting.value = false
    }
  })
}

const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    movieId: 0,
    cinemaId: 0,
    hallId: 0,
    startTime: '',
    endTime: '',
    basePrice: 50
  })
  hallOptions.value = []
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadOptions()
  loadShowtimes()
})
</script>

<style scoped>
.showtime-page {
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

.movie-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.movie-info {
  flex: 1;
  min-width: 0;
}

.movie-title {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.movie-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.price {
  color: #f56c6c;
  font-weight: 600;
}

.low-seat {
  color: #e6a23c;
  font-weight: 500;
}
</style>
