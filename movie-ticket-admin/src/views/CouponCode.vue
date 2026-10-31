<template>
  <div class="coupon-code-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <strong>兑换码管理</strong>
            <p>创建活动兑换码，控制发放数量与有效期，并追踪用户兑换情况。</p>
          </div>
          <div class="header-actions">
            <el-button @click="openRedemptions()">
              <el-icon><List /></el-icon>
              全部兑换记录
            </el-button>
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增兑换码
            </el-button>
          </div>
        </div>
      </template>

      <div class="summary-grid">
        <div class="summary-item">
          <span>兑换码总数</span>
          <strong>{{ pagination.total }}</strong>
        </div>
        <div class="summary-item">
          <span>本页启用中</span>
          <strong>{{ enabledCount }}</strong>
        </div>
        <div class="summary-item">
          <span>本页已兑换</span>
          <strong>{{ redeemedCount }}</strong>
        </div>
        <div class="summary-item">
          <span>本页剩余名额</span>
          <strong>{{ remainingCount }}</strong>
        </div>
      </div>

      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="兑换码或优惠券名称"
          clearable
          style="width: 260px"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="searchForm.status"
          placeholder="启用状态"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option label="全部" :value="undefined" />
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <BatchActions :rows="selectedRows" :disabled="loading" :api="couponAdminApi" :statuses="[{ label: '批量启用', value: 1 }, { label: '批量停用', value: 0 }]" protect-redeemed
        @clear="batchTable?.clearSelection()" @busy="batchBusy = $event" @completed="handleBatchCompleted" />
      <ManagementTable ref="batchTable" row-key="id" @selection-change="selectedRows = $event" :data="tableData" v-loading="loading || batchBusy" border stripe>
        <el-table-column type="selection" width="48" :selectable="() => !loading && !batchBusy" />
        <el-table-column label="兑换码" min-width="155">
          <template #default="{ row }">
            <button class="code-value" type="button" title="点击复制" @click="copyCode(row.code)">
              {{ row.code }}
              <el-icon><CopyDocument /></el-icon>
            </button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="优惠券名称" min-width="210" show-overflow-tooltip />
        <el-table-column label="优惠规则" min-width="150">
          <template #default="{ row }">
            <strong class="amount">¥{{ money(row.amount) }}</strong>
            <span class="threshold">满 ¥{{ money(row.minSpend) }} 可用</span>
          </template>
        </el-table-column>
        <el-table-column label="兑换进度" min-width="180">
          <template #default="{ row }">
            <div class="progress-copy">
              <span>{{ row.redeemedCount || 0 }} / {{ row.maxRedemptions }}</span>
              <span>{{ progress(row) }}%</span>
            </div>
            <el-progress
              :percentage="progress(row)"
              :stroke-width="7"
              :show-text="false"
              :status="progress(row) >= 100 ? 'success' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column label="领取后有效期" width="130">
          <template #default="{ row }">{{ row.validDays }} 天</template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="205">
          <template #default="{ row }">
            <div class="date-range">
              <span>{{ formatDateTime(row.validFrom) }}</span>
              <span>至 {{ formatDateTime(row.expireTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row).type">{{ statusMeta(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openRedemptions(row)">记录</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
        class="pagination"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑兑换码' : '新增兑换码'"
      width="650px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="112px">
        <el-form-item label="兑换码" prop="code">
          <el-input
            v-model="formData.code"
            placeholder="如 SUMMER2026"
            maxlength="32"
            show-word-limit
            class="code-input"
          />
          <div class="form-tip">仅支持字母、数字和短横线，保存后统一转换为大写。</div>
        </el-form-item>
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="formData.name" placeholder="如 暑期专享满80减10元券" maxlength="100" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="优惠金额" prop="amount">
            <el-input-number v-model="formData.amount" :min="0.01" :precision="2" :step="1" />
          </el-form-item>
          <el-form-item label="使用门槛" prop="minSpend">
            <el-input-number v-model="formData.minSpend" :min="0" :precision="2" :step="10" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="有效天数" prop="validDays">
            <el-input-number v-model="formData.validDays" :min="1" :max="3650" />
          </el-form-item>
          <el-form-item label="发放上限" prop="maxRedemptions">
            <el-input-number
              v-model="formData.maxRedemptions"
              :min="Math.max(1, formData.redeemedCount || 0)"
              :max="10000000"
            />
          </el-form-item>
        </div>
        <el-form-item label="活动时间" prop="validFrom">
          <el-date-picker
            v-model="activityRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="截止时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="recordsVisible" :title="recordsTitle" size="76%">
      <div class="records-toolbar">
        <el-input
          v-model="recordsSearch.keyword"
          placeholder="用户名、昵称或兑换码"
          clearable
          style="width: 280px"
          @clear="handleRecordsSearch"
          @keyup.enter="handleRecordsSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleRecordsSearch">查询</el-button>
        <el-button @click="resetRecordsSearch">重置</el-button>
      </div>
      <ManagementTable :data="redemptionData" v-loading="recordsLoading" border stripe>
        <el-table-column prop="code" label="兑换码" min-width="135" />
        <el-table-column prop="couponName" label="优惠券" min-width="190" show-overflow-tooltip />
        <el-table-column label="兑换用户" min-width="160">
          <template #default="{ row }">
            <strong>{{ row.nickname || row.username }}</strong>
            <span class="username">@{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="券状态" width="100">
          <template #default="{ row }">
            <el-tag :type="couponStatusMeta(row.couponStatus).type">
              {{ couponStatusMeta(row.couponStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="兑换时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.redeemTime) }}</template>
        </el-table-column>
        <el-table-column label="使用时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.useTime) }}</template>
        </el-table-column>
      </ManagementTable>
      <el-pagination
        v-model:current-page="recordsPagination.page"
        v-model:page-size="recordsPagination.size"
        :total="recordsPagination.total"
        :page-sizes="[10, 20, 50]"
        :pager-count="5"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @size-change="loadRedemptions"
        @current-change="loadRedemptions"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
/**
 * 兑换码管理：维护发放规则及状态，查看领取记录与发放进度。
 */
import BatchActions from '@/components/BatchActions.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useListPageState } from '@/composables/usePageState'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import { CopyDocument, List, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { couponAdminApi } from '@/api/coupon'
import type { CouponCode, CouponRedemption } from '@/types'

type TagType = TagProps['type']

const batchTable = ref<{ clearSelection: () => void }>()
const selectedRows = ref<CouponCode[]>([])
const batchBusy = ref(false)
/** 接收批量操作结果，清理选择并刷新当前列表。 */
const handleBatchCompleted = async (deleted: number) => {
  if (deleted > 0 && deleted >= tableData.value.length && pagination.page > 1) pagination.page--
  await loadData()
}

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<CouponCode[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const activityRange = ref<string[]>([])

const recordsVisible = ref(false)
const recordsLoading = ref(false)
const redemptionData = ref<CouponRedemption[]>([])
const selectedCode = ref<CouponCode>()

const searchForm = reactive<{ keyword: string; status?: number }>({ keyword: '', status: undefined })
const pagination = reactive({ page: 1, size: 10, total: 0 })
useListPageState('coupon-code', searchForm, pagination)
const recordsSearch = reactive({ keyword: '' })
const recordsPagination = reactive({ page: 1, size: 10, total: 0 })

const emptyForm = (): CouponCode => ({
  code: '',
  name: '',
  amount: 6.99,
  minSpend: 80,
  validDays: 7,
  maxRedemptions: 100,
  redeemedCount: 0,
  validFrom: dateValue(new Date()),
  expireTime: dateValue(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)),
  status: 1
})

const formData = reactive<CouponCode>(emptyForm())

const formRules: FormRules = {
  code: [
    { required: true, message: '请输入兑换码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9-]+$/, message: '只能包含字母、数字和短横线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入优惠金额', trigger: 'change' }],
  minSpend: [{ required: true, message: '请输入使用门槛', trigger: 'change' }],
  validDays: [{ required: true, message: '请输入有效天数', trigger: 'change' }],
  maxRedemptions: [{ required: true, message: '请输入发放上限', trigger: 'change' }],
  validFrom: [{ required: true, message: '请选择活动时间', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const enabledCount = computed(() => tableData.value.filter(item => statusMeta(item).text === '兑换中').length)
const redeemedCount = computed(() => tableData.value.reduce((sum, item) => sum + (item.redeemedCount || 0), 0))
const remainingCount = computed(() => tableData.value.reduce(
  (sum, item) => sum + Math.max(0, item.maxRedemptions - (item.redeemedCount || 0)), 0
))
const recordsTitle = computed(() => selectedCode.value
  ? `${selectedCode.value.code} · 兑换记录`
  : '全部兑换记录')

function dateValue(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const money = (value?: number) => Number(value || 0).toFixed(2)

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

const progress = (row: CouponCode) => {
  if (!row.maxRedemptions) return 0
  return Math.min(100, Math.round(((row.redeemedCount || 0) / row.maxRedemptions) * 100))
}

const statusMeta = (row: CouponCode): { text: string; type: TagType } => {
  const now = Date.now()
  if (row.status === 0) return { text: '已停用', type: 'info' }
  if (new Date(row.expireTime).getTime() < now) return { text: '已过期', type: 'danger' }
  if (new Date(row.validFrom).getTime() > now) return { text: '待生效', type: 'warning' }
  if ((row.redeemedCount || 0) >= row.maxRedemptions) return { text: '已兑完', type: 'info' }
  return { text: '兑换中', type: 'success' }
}

const couponStatusMeta = (status: number): { text: string; type: TagType } => {
  if (status === 1) return { text: '已使用', type: 'success' }
  if (status === 2) return { text: '已过期', type: 'info' }
  return { text: '未使用', type: 'warning' }
}

/** 加载当前页面所需数据，并同步加载与失败状态。 */
const loadData = async () => {
  batchTable.value?.clearSelection()
  selectedRows.value = []
  loading.value = true
  try {
    const result = await couponAdminApi.getPage({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status
    })
    tableData.value = result.records
    pagination.total = result.total
  } catch (error) {
    console.error('加载兑换码失败:', error)
  } finally {
    loading.value = false
  }
}

/** 应用筛选条件并从第一页重新查询。 */
const handleSearch = () => {
  pagination.page = 1
  loadData()
}

/** 清空筛选条件并重新加载列表。 */
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = undefined
  handleSearch()
}

/** 初始化新增表单并打开编辑入口。 */
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, emptyForm())
  activityRange.value = [formData.validFrom, formData.expireTime]
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 将选中记录填入编辑表单。 */
const handleEdit = (row: CouponCode) => {
  isEdit.value = true
  Object.assign(formData, row)
  activityRange.value = [row.validFrom, row.expireTime]
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 校验当前表单并提交保存，成功后更新页面数据。 */
const handleSubmit = async () => {
  if (!activityRange.value || activityRange.value.length !== 2) {
    ElMessage.warning('请选择完整的活动时间')
    return
  }
  formData.validFrom = activityRange.value[0]
  formData.expireTime = activityRange.value[1]
  formData.code = formData.code.trim().toUpperCase()

  try {
    await formRef.value?.validate()
  } catch {
    ElMessage.warning('请完善表单信息')
    return
  }

  if (new Date(formData.validFrom) >= new Date(formData.expireTime)) {
    ElMessage.warning('截止时间必须晚于生效时间')
    return
  }
  if (formData.minSpend > 0 && formData.amount > formData.minSpend) {
    ElMessage.warning('优惠金额不能大于使用门槛')
    return
  }

  submitting.value = true
  try {
    if (isEdit.value && formData.id) {
      await couponAdminApi.update(formData.id, formData)
      ElMessage.success('兑换码更新成功')
    } else {
      await couponAdminApi.create(formData)
      ElMessage.success('兑换码创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('保存兑换码失败:', error)
  } finally {
    submitting.value = false
  }
}

/** 提交启用或停用操作，并同步最新状态。 */
const handleToggleStatus = async (row: CouponCode) => {
  const status = row.status === 1 ? 0 : 1
  const action = status === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${action}兑换码“${row.code}”吗？`, '操作确认', {
      type: 'warning'
    })
    await couponAdminApi.updateStatus(row.id!, status)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') console.error(`${action}兑换码失败:`, error)
  }
}

/** 确认后提交删除请求，成功后刷新列表。 */
const handleDelete = async (row: CouponCode) => {
  if ((row.redeemedCount || 0) > 0) {
    ElMessage.warning('该兑换码已有兑换记录，请停用后保留历史数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除兑换码“${row.code}”吗？`, '删除确认', {
      type: 'error',
      confirmButtonText: '确定删除'
    })
    await couponAdminApi.delete(row.id!)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.page > 1) pagination.page--
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') console.error('删除兑换码失败:', error)
  }
}

const copyCode = async (code: string) => {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success(`已复制兑换码 ${code}`)
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

/** 打开选中兑换码的领取记录。 */
const openRedemptions = (row?: CouponCode) => {
  selectedCode.value = row
  recordsSearch.keyword = ''
  recordsPagination.page = 1
  recordsVisible.value = true
  loadRedemptions()
}

/** 按筛选条件分页查询兑换记录。 */
const loadRedemptions = async () => {
  recordsLoading.value = true
  try {
    const result = await couponAdminApi.getRedemptions({
      page: recordsPagination.page,
      size: recordsPagination.size,
      couponCodeId: selectedCode.value?.id,
      keyword: recordsSearch.keyword || undefined
    })
    redemptionData.value = result.records
    recordsPagination.total = result.total
  } catch (error) {
    console.error('加载兑换记录失败:', error)
  } finally {
    recordsLoading.value = false
  }
}

const handleRecordsSearch = () => {
  recordsPagination.page = 1
  loadRedemptions()
}

const resetRecordsSearch = () => {
  recordsSearch.keyword = ''
  handleRecordsSearch()
}

onMounted(loadData)
</script>

<style scoped>
.coupon-code-page {
  padding: 0;
}

.card-header,
.header-actions,
.search-bar,
.records-toolbar,
.progress-copy {
  display: flex;
  align-items: center;
}

.card-header {
  justify-content: space-between;
  gap: 20px;
}

.card-header strong {
  font-size: 17px;
}

.card-header p {
  margin: 5px 0 0;
  color: var(--admin-muted);
  font-size: 12px;
}

.header-actions,
.search-bar,
.records-toolbar {
  gap: 10px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 15px 18px;
  border-radius: 12px;
  background: var(--admin-card);
  box-shadow:
    inset 2px 2px 5px var(--admin-shadow-dark),
    inset -2px -2px 5px var(--admin-shadow-light);
}

.summary-item span,
.threshold,
.date-range span,
.username {
  color: var(--admin-muted);
  font-size: 12px;
}

.summary-item strong {
  color: var(--admin-text);
  font-family: 'JetBrains Mono', monospace;
  font-size: 24px;
}

.search-bar {
  margin-bottom: 20px;
}

.code-value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border: 0;
  border-radius: 6px;
  color: #a95600;
  background: #fff4e6;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 700;
  cursor: pointer;
}

.amount,
.threshold,
.date-range,
.username {
  display: block;
}

.amount {
  color: #c56600;
  font-size: 16px;
}

.threshold,
.date-range span + span,
.username {
  margin-top: 3px;
}

.progress-copy {
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 14px;
}

.form-row :deep(.el-input-number) {
  width: 100%;
}

.form-tip {
  margin-top: 5px;
  color: var(--admin-muted);
  font-size: 12px;
}

.code-input :deep(input) {
  text-transform: uppercase;
}

.records-toolbar {
  margin-bottom: 18px;
}

@media (max-width: 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid,
  .form-row {
    grid-template-columns: 1fr;
  }

  .search-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
