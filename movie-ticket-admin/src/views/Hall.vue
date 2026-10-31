<template>
  <div class="hall-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>影厅管理</span>
          <router-link to="/listing?kind=HALL"><el-button>AI 辅助上架</el-button></router-link>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增影厅
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="影院">
          <el-select v-model="searchForm.cinemaId" placeholder="全部影院" clearable style="width: 200px">
            <el-option v-for="c in cinemaList" :key="c.id" :label="c.name" :value="c.id!" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <BatchActions :rows="selectedRows" :disabled="loading" :api="hallApi" :statuses="[{ label: '批量启用', value: 1 }, { label: '批量停用', value: 0 }]"
        @clear="batchTable?.clearSelection()" @busy="batchBusy = $event" @completed="handleBatchCompleted" />
      <ManagementTable ref="batchTable" row-key="id" @selection-change="selectedRows = $event" :data="tableData" border stripe v-loading="loading || batchBusy">
        <el-table-column type="selection" width="48" :selectable="() => !loading && !batchBusy" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="cinemaName" label="所属影院" min-width="150" />
        <el-table-column prop="name" label="影厅名称" min-width="120" />
        <el-table-column label="座位" width="140">
          <template #default="{ row }">
            {{ row.totalRows }} 行 × {{ row.totalCols }} 列
            <span class="seats-total">（{{ row.totalRows * row.totalCols }}座）</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
            <el-button type="primary" size="small" link @click="handlePreviewLayout(row)">预览</el-button>
            <el-button type="primary" size="small" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </ManagementTable>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        :pager-count="5"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end;"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      :close-on-click-modal="false"
      class="hall-dialog"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="所属影院" prop="cinemaId">
          <el-select v-model="formData.cinemaId" placeholder="请选择影院" style="width: 100%">
            <el-option v-for="c in cinemaList" :key="c.id" :label="c.name" :value="c.id!" />
          </el-select>
        </el-form-item>

        <el-form-item label="影厅名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入影厅名称，如 1号厅、IMAX厅"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="座位行数" prop="totalRows">
              <el-input-number
                v-model="formData.totalRows"
                :min="1"
                :max="50"
                :controls="true"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="座位列数" prop="totalCols">
              <el-input-number
                v-model="formData.totalCols"
                :min="1"
                :max="50"
                :controls="true"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="添加过道">
          <el-select v-model="aisleAfterCols" multiple clearable placeholder="选择过道位置" style="width: 100%" aria-label="添加过道">
            <el-option label="最左侧" :value="0" />
            <el-option v-for="col in Math.max(0, formData.totalCols - 1)" :key="col" :label="`第 ${col} 列后`" :value="col" />
            <el-option label="最右侧" :value="formData.totalCols" />
          </el-select>
          <div class="form-tip">可在最左侧、最右侧或座位列之间添加多条过道，清空表示无过道。过道不占座位编号，智能推荐不会跨过道连座。</div>
        </el-form-item>

        <el-form-item label="座位布局">
          <div class="seat-editor-wrapper">
            <div class="seat-toolbar">
              <span class="seat-legend">
                <span class="legend-item"><span class="seat-icon available"></span> 可用</span>
                <span class="legend-item"><span class="seat-icon disabled"></span> 不可用</span>
              </span>
              <el-button size="small" @click="resetSeatLayout">重置为全部可用</el-button>
            </div>
            <div class="screen-label">银 幕</div>
            <div class="seat-grid" v-if="formData.totalRows > 0 && formData.totalCols > 0">
              <div v-for="(row, ri) in seatGrid" :key="ri" class="seat-row">
                <span class="row-label">{{ ri + 1 }}</span>
                <span v-if="aisleAfterCols.includes(0)" class="hall-aisle" title="最左侧过道">过道</span>
                <template v-for="(cell, ci) in row" :key="ci">
                <span
                  class="seat-cell"
                  :class="{ disabled: cell === 0 }"
                  @click="toggleSeat(ri, ci)"
                />
                <span v-if="aisleAfterCols.includes(ci + 1)" class="hall-aisle" :title="ci + 1 === formData.totalCols ? '最右侧过道' : `第 ${ci + 1} 列后过道`">过道</span>
                </template>
              </div>
            </div>
            <el-empty v-else description="请先设置行数和列数" :image-size="60" />
          </div>
        </el-form-item>

        <el-form-item label="座位布局" prop="seatLayout" class="json-field">
          <el-input
            v-model="formData.seatLayout"
            type="textarea"
            :rows="3"
            readonly
            placeholder="点击上方座位自动生"
          />
          <div class="form-tip">点击座位格子可切换启用/停用状态，布局会自动生成</div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
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

    <!-- 座位预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      title="座位布局预览"
      width="620px"
      class="hall-dialog"
    >
      <div class="preview-hall-name">{{ previewHallName }}</div>
      <div class="preview-info">{{ previewRows }} 行 × {{ previewCols }} 列</div>
      <div class="screen-label">银 幕</div>
      <div class="seat-grid preview-grid" v-if="previewRows > 0 && previewCols > 0">
        <div v-for="(row, ri) in previewGrid" :key="ri" class="seat-row">
          <span class="row-label">{{ ri + 1 }}</span>
          <span v-if="previewAisles.includes(0)" class="hall-aisle" title="最左侧过道">过道</span>
          <template v-for="(cell, ci) in row" :key="ci">
          <span
            class="seat-cell"
            :class="{ disabled: cell === 0 }"
          />
          <span v-if="previewAisles.includes(ci + 1)" class="hall-aisle" :title="ci + 1 === previewCols ? '最右侧过道' : `第 ${ci + 1} 列后过道`">过道</span>
          </template>
        </div>
      </div>
      <div class="preview-legend">
        <span class="legend-item"><span class="seat-icon available"></span> 可用座位</span>
        <span class="legend-item"><span class="seat-icon disabled"></span> 不可用座位</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 影厅管理：维护影厅资料，编辑有效座位矩阵及过道，预览可售布局。
 */
import BatchActions from '@/components/BatchActions.vue'
import { useListPageState } from '@/composables/usePageState'
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { hallApi } from '@/api/hall'
import { cinemaApi } from '@/api/cinema'
import type { Hall, Cinema } from '@/types'

const batchTable = ref<{ clearSelection: () => void }>()
const selectedRows = ref<Hall[]>([])
const batchBusy = ref(false)
/** 接收批量操作结果，清理选择并刷新当前列表。 */
const handleBatchCompleted = async (deleted: number) => {
  if (deleted > 0 && deleted >= tableData.value.length && pagination.page > 1) pagination.page--
  await loadData()
}

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<Hall[]>([])
const cinemaList = ref<Cinema[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增影厅')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 预览
const previewVisible = ref(false)
const previewHallName = ref('')
const previewRows = ref(0)
const previewCols = ref(0)
const previewGrid = ref<number[][]>([])
const previewAisles = ref<number[]>([])
const aisleAfterCols = ref<number[]>([])

const searchForm = reactive({
  cinemaId: undefined as number | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})
useListPageState('hall', searchForm, pagination)

const formData = reactive<Hall>({
  id: undefined,
  cinemaId: undefined as unknown as number,
  name: '',
  totalRows: 10,
  totalCols: 10,
  seatLayout: '',
  status: 1
})

const formRules: FormRules = {
  cinemaId: [
    { required: true, message: '请选择所属影院', trigger: 'change' }
  ],
  name: [
    { required: true, message: '请输入影厅名称', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ],
  totalRows: [
    { required: true, message: '请输入座位行数', trigger: 'change' }
  ],
  totalCols: [
    { required: true, message: '请输入座位列数', trigger: 'change' }
  ],
  seatLayout: [
    { required: true, message: '请设置座位布局', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

// 座位网格状态（二维数组，1=可用，0=不可用）
const seatGrid = ref<number[][]>([])

// 从 seatLayout JSON 解析网格
const parseSeatLayout = (layout: string): number[][] => {
  try {
    const parsed = JSON.parse(layout)
    if (Array.isArray(parsed)) return parsed
    if (Array.isArray(parsed?.seats)) return parsed.seats
  } catch {}
  return []
}

// 生成 seatLayout JSON
const generateSeatLayout = (grid: number[][]) => {
  formData.seatLayout = JSON.stringify({ seats: grid, aisleAfterCols: [...aisleAfterCols.value].sort((a, b) => a - b) })
}

const parseAisles = (layout: string, cols: number): number[] => {
  try {
    const parsed = JSON.parse(layout)
    if (!Array.isArray(parsed) && Array.isArray(parsed?.aisleAfterCols)) return parsed.aisleAfterCols
  } catch {}
  return cols > 1 ? [Math.floor(cols / 2)] : []
}

watch(aisleAfterCols, () => generateSeatLayout(seatGrid.value), { deep: true })

// 初始化/重置座位网格
const initSeatGrid = () => {
  const rows = formData.totalRows || 0
  const cols = formData.totalCols || 0
  if (rows <= 0 || cols <= 0) {
    seatGrid.value = []
    formData.seatLayout = ''
    return
  }
  // 如果已有布局且尺寸匹配，保留
  const existing = parseSeatLayout(formData.seatLayout)
  if (existing.length === rows && existing[0]?.length === cols) {
    seatGrid.value = existing
  } else {
    seatGrid.value = Array.from({ length: rows }, () => Array(cols).fill(1))
    generateSeatLayout(seatGrid.value)
  }
}

const resetSeatLayout = () => {
  const rows = formData.totalRows || 0
  const cols = formData.totalCols || 0
  if (rows <= 0 || cols <= 0) return
  seatGrid.value = Array.from({ length: rows }, () => Array(cols).fill(1))
  generateSeatLayout(seatGrid.value)
}

/** 切换座位选择或可售状态，并执行当前页面的座位限制检查。 */
const toggleSeat = (row: number, col: number) => {
  seatGrid.value[row][col] = seatGrid.value[row][col] === 1 ? 0 : 1
  generateSeatLayout(seatGrid.value)
}

// 监听行列数变化，重建网格
watch(
  () => [formData.totalRows, formData.totalCols],
  (_, previous) => {
    if (dialogVisible.value) {
      // 调整列数时，最右侧过道随边缘移动；打开另一影厅则沿用该厅配置。
      const existing = parseSeatLayout(formData.seatLayout)
      const resized = existing[0]?.length !== formData.totalCols
      aisleAfterCols.value = [...new Set(aisleAfterCols.value
        .map(col => resized && col === previous[1] ? formData.totalCols : col)
        .filter(col => col >= 0 && col <= formData.totalCols))]
      initSeatGrid()
    }
  }
)

/** 加载当前页面所需数据，并同步加载与失败状态。 */
const loadData = async () => {
  batchTable.value?.clearSelection()
  selectedRows.value = []
  loading.value = true
  try {
    const res = await hallApi.getPage({
      page: pagination.page,
      size: pagination.size,
      cinemaId: searchForm.cinemaId
    })
    tableData.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

/** 加载影院列表，供影院选择或地图展示。 */
const loadCinemas = async () => {
  try {
    cinemaList.value = await cinemaApi.getAll()
  } catch (error) {
    console.error('加载影院列表失败:', error)
  }
}

/** 应用筛选条件并从第一页重新查询。 */
const handleSearch = () => {
  pagination.page = 1
  loadData()
}

/** 清空筛选条件并重新加载列表。 */
const handleReset = () => {
  searchForm.cinemaId = undefined
  handleSearch()
}

const resetFormData = () => {
  formData.id = undefined
  formData.cinemaId = undefined as unknown as number
  formData.name = ''
  formData.totalRows = 10
  formData.totalCols = 10
  formData.seatLayout = ''
  formData.status = 1
}

/** 初始化新增表单并打开编辑入口。 */
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增影厅'
  resetFormData()
  aisleAfterCols.value = []
  initSeatGrid()
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 将选中记录填入编辑表单。 */
const handleEdit = (row: Hall) => {
  isEdit.value = true
  dialogTitle.value = '编辑影厅'
  Object.assign(formData, {
    id: row.id,
    cinemaId: row.cinemaId,
    name: row.name,
    totalRows: row.totalRows,
    totalCols: row.totalCols,
    seatLayout: row.seatLayout,
    status: row.status
  })
  aisleAfterCols.value = parseAisles(row.seatLayout, row.totalCols)
  initSeatGrid()
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 打开影厅座位预览，核对实际可售布局。 */
const handlePreviewLayout = (row: Hall) => {
  previewHallName.value = `${row.cinemaName} - ${row.name}`
  previewRows.value = row.totalRows
  previewCols.value = row.totalCols
  previewGrid.value = parseSeatLayout(row.seatLayout)
  if (!previewGrid.value.length) previewGrid.value = Array.from({ length: row.totalRows }, () => Array(row.totalCols).fill(1))
  previewAisles.value = parseAisles(row.seatLayout, row.totalCols)
  previewVisible.value = true
}

/** 校验当前表单并提交保存，成功后更新页面数据。 */
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善表单信息')
    return
  }

  submitting.value = true
  try {
    const submitData: Hall = {
      cinemaId: formData.cinemaId,
      name: formData.name,
      totalRows: formData.totalRows,
      totalCols: formData.totalCols,
      seatLayout: formData.seatLayout,
      status: formData.status
    }

    if (isEdit.value && formData.id) {
      await hallApi.update(formData.id, submitData)
      ElMessage.success('编辑成功')
    } else {
      await hallApi.create(submitData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(isEdit.value ? '编辑失败' : '新增失败')
  } finally {
    submitting.value = false
  }
}

/** 提交启用或停用操作，并同步最新状态。 */
const handleToggleStatus = async (row: Hall) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(`确定要${action}影厅"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await hallApi.updateStatus(row.id!, newStatus)
    ElMessage.success('状态更新成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('状态更新失败:', error)
      ElMessage.error('状态更新失败')
    }
  }
}

/** 确认后提交删除请求，成功后刷新列表。 */
const handleDelete = async (row: Hall) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除影厅"${row.name}"吗？此操作不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await hallApi.delete(row.id!)
    ElMessage.success('删除成功')

    if (tableData.value.length === 1 && pagination.page > 1) {
      pagination.page--
    }
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadData()
  loadCinemas()
})
</script>

<style scoped>
.hall-page {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.seats-total {
  font-size: 12px;
  color: #909399;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table .el-button) {
  margin: 0 4px;
}

.json-field {
  margin-top: -8px;
}
</style>

<!-- 对话框内的座位布局样式（append-to-body 需要全局样式） -->
<style>
.hall-dialog .seat-grid {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  overflow: auto;
  padding: 8px 0;
}

.hall-dialog .seat-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.hall-dialog .row-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.hall-dialog .seat-cell {
  display: inline-block;
  width: 24px;
  flex-shrink: 0;
  height: 24px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--admin-primary);
  border: 1px solid #d96f00;
}

.hall-dialog .hall-aisle {
  width: 28px;
  flex-shrink: 0;
  color: #8a8f98;
  font-size: 10px;
  text-align: center;
  border-inline: 1px dashed #a5a9af;
}

.hall-dialog .seat-cell:hover {
  transform: scale(1.15);
  box-shadow: 0 2px 8px rgba(255, 132, 0, 0.38);
}

.hall-dialog .seat-cell.disabled {
  background: #dcdfe6;
  border: 1px solid #c0c4cc;
}

.hall-dialog .seat-cell.disabled:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.hall-dialog .preview-grid {
  cursor: default;
}

.hall-dialog .preview-grid .seat-cell {
  cursor: default;
}

.hall-dialog .preview-grid .seat-cell:hover {
  transform: none;
  box-shadow: none;
}

.hall-dialog .screen-label {
  text-align: center;
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
  padding: 6px 0;
  border-bottom: 3px solid #e4e7ed;
  letter-spacing: 8px;
}

.hall-dialog .el-dialog__body {
  padding: 20px 20px 10px;
}

.hall-dialog .el-form-item__label {
  font-weight: 500;
}

.hall-dialog .seat-editor-wrapper {
  width: 100%;
  border: 0;
  border-radius: 16px;
  padding: 16px;
  background: var(--admin-card);
  box-shadow:
    inset 5px 5px 10px var(--admin-shadow-dark),
    inset -5px -5px 10px var(--admin-shadow-light);
}

.hall-dialog .seat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.hall-dialog .seat-legend {
  display: flex;
  gap: 16px;
}

.hall-dialog .legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}

.hall-dialog .seat-icon {
  display: inline-block;
  width: 16px;
  height: 16px;
  border-radius: 3px;
}

.hall-dialog .seat-icon.available {
  background: var(--admin-primary);
  border: 1px solid #d96f00;
}

.hall-dialog .seat-icon.disabled {
  background: #dcdfe6;
  border: 1px solid #c0c4cc;
}

.hall-dialog .preview-hall-name {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 4px;
}

.hall-dialog .preview-info {
  font-size: 14px;
  color: #909399;
  text-align: center;
  margin-bottom: 16px;
}

.hall-dialog .preview-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}
</style>
