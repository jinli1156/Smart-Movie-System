<template>
  <div class="cinema-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>影院管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增影院
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="影院名称或地址" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="影院名称" min-width="150" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column prop="phone" label="电话" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '营业中' : '已停业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
            <el-button type="primary" size="small" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停业' : '营业' }}
            </el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
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
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="影院名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入影院名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="影院地址" prop="address">
          <el-input
            v-model="formData.address"
            placeholder="请输入影院地址"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="经纬度">
          <div class="lnglat-row">
            <el-input-number
              v-model="formData.lng"
              :precision="7"
              :min="-180"
              :max="180"
              :controls="false"
              placeholder="经度"
              class="lnglat-input"
            />
            <el-input-number
              v-model="formData.lat"
              :precision="7"
              :min="-90"
              :max="90"
              :controls="false"
              placeholder="纬度"
              class="lnglat-input"
            />
            <el-button link type="primary" :loading="geocoding" @click="autoGeocode">
              <el-icon><Aim /></el-icon>
              自动定位
            </el-button>
            <el-button link type="info" @click="openCoordPicker">
              <el-icon><Location /></el-icon>
              去拾取
            </el-button>
          </div>
          <div class="form-tip">
            点"自动定位"根据地址自动查坐标；若不准确可点"去拾取"手动选择，或直接输入经纬度
          </div>
        </el-form-item>

        <el-form-item label="联系电话" prop="phone">
          <el-input
            v-model="formData.phone"
            placeholder="请输入联系电话"
            maxlength="20"
          />
        </el-form-item>

        <el-form-item label="影院简介" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入影院简介"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="影院图片" prop="images">
          <div class="upload-area">
            <div class="image-preview-list">
              <div
                v-for="(url, index) in imageList"
                :key="index"
                class="image-preview-item"
              >
                <img :src="url" alt="影院图片" class="preview-img" />
                <div class="preview-overlay">
                  <el-icon class="preview-action" @click="handlePreview(url)"><ZoomIn /></el-icon>
                  <el-icon class="preview-action delete" @click="removeImage(index)"><Delete /></el-icon>
                </div>
              </div>
            </div>
            <el-upload
              v-if="imageList.length < 5"
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="handleFileSelected"
            >
              <div class="upload-trigger" :class="{ 'is-uploading': uploading }">
                <el-icon v-if="uploading" class="upload-icon is-loading"><Loading /></el-icon>
                <el-icon v-else class="upload-icon"><Plus /></el-icon>
                <span class="upload-text">{{ uploading ? '上传中...' : '上传图片' }}</span>
              </div>
            </el-upload>
          </div>
          <div class="form-tip">最多上传5张图片，支持 jpg / png / gif / webp，单张不超过 5MB</div>
        </el-form-item>

        <el-form-item label="营业状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">营业中</el-radio>
            <el-radio :label="0">已停业</el-radio>
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

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="50%" append-to-body>
      <div class="preview-dialog">
        <img :src="previewImageUrl" alt="预览" class="preview-dialog-img" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { Plus, ZoomIn, Delete, Loading, Location, Aim } from '@element-plus/icons-vue'
import { cinemaApi } from '@/api/cinema'
import { uploadApi } from '@/api/upload'
import type { Cinema } from '@/types'
import AMapLoader from '@amap/amap-jsapi-loader'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<Cinema[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增影院')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const previewVisible = ref(false)
const previewImageUrl = ref('')
const uploading = ref(false)
const geocoding = ref(false)

const searchForm = reactive({
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formData = reactive<Cinema>({
  id: undefined,
  name: '',
  address: '',
  lng: undefined,
  lat: undefined,
  phone: '',
  description: '',
  images: '',
  status: 1
})

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入影院名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  address: [
    { required: true, message: '请输入影院地址', trigger: 'blur' },
    { min: 5, max: 200, message: '长度在 5 到 200 个字符', trigger: 'blur' }
  ],
  phone: [
    {
      pattern: /^[\d\-\s+()]+$/,
      message: '请输入有效的电话号码',
      trigger: 'blur'
    }
  ],
  status: [
    { required: true, message: '请选择营业状态', trigger: 'change' }
  ]
}

const IMAGE_BASE = '/api/uploads/'

// 后端上传接口已返回完整路径（/api/uploads/xxx.jpg），仅对历史的纯文件名补全前缀
const resolveImageUrl = (ref: string) => {
  const f = ref.trim()
  if (f.startsWith('http') || f.startsWith('/')) return f
  return IMAGE_BASE + f
}

const imageList = computed(() => {
  if (!formData.images) return []
  return formData.images.split(',').filter(s => s.trim()).map(resolveImageUrl)
})

const handleFileSelected = async (uploadFile: UploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过5MB')
    return
  }

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    return
  }

  uploading.value = true
  try {
    const res = await uploadApi.uploadImage(file)
    const currentImages = formData.images ? formData.images.split(',').filter(s => s.trim()) : []
    currentImages.push(res.url)
    formData.images = currentImages.join(',')
    ElMessage.success('图片上传成功')
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('图片上传失败')
  } finally {
    uploading.value = false
  }
}

const removeImage = (index: number) => {
  const list = (formData.images || '').split(',').filter(s => s.trim())
  list.splice(index, 1)
  formData.images = list.join(',')
}

const handlePreview = (url: string) => {
  previewImageUrl.value = url
  previewVisible.value = true
}

const openCoordPicker = () => {
  window.open('https://lbs.amap.com/tools/picker', '_blank', 'noopener')
}

let AMapNS: any = null

const autoGeocode = async () => {
  if (!formData.address) {
    ElMessage.warning('请先输入影院地址')
    return
  }
  geocoding.value = true
  try {
    if (!AMapNS) {
      ;(window as any)._AMapSecurityConfig = {
        securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
      }
      AMapNS = await AMapLoader.load({
        key: import.meta.env.VITE_AMAP_KEY,
        version: '2.0',
        plugins: ['AMap.Geocoder']
      })
    }
    const geocoder = new AMapNS.Geocoder({ city: '全国' })
    const result = await new Promise<any>((resolve, reject) => {
      geocoder.getLocation(formData.address, (status: string, res: any) => {
        if (status === 'complete' && res?.geocodes?.length) resolve(res)
        else reject(new Error(status))
      })
    })
    const loc = result.geocodes[0].location
    formData.lng = loc.lng
    formData.lat = loc.lat
    ElMessage.success(`定位成功: ${loc.lng.toFixed(6)}, ${loc.lat.toFixed(6)}`)
  } catch (e: any) {
    console.error('自动定位失败:', e)
    ElMessage.error('自动定位失败，请手动输入或使用"去拾取"')
  } finally {
    geocoding.value = false
  }
}

const resetFormData = () => {
  formData.id = undefined
  formData.name = ''
  formData.address = ''
  formData.lng = undefined
  formData.lat = undefined
  formData.phone = ''
  formData.description = ''
  formData.images = ''
  formData.status = 1
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await cinemaApi.getPage({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword
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

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  handleSearch()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增影院'
  resetFormData()
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const handleEdit = (row: Cinema) => {
  isEdit.value = true
  dialogTitle.value = '编辑影院'
  Object.assign(formData, row)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

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
    if (isEdit.value && formData.id) {
      await cinemaApi.update(formData.id, formData)
      ElMessage.success('编辑成功')
    } else {
      await cinemaApi.create(formData)
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

const handleToggleStatus = async (row: Cinema) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '营业' : '停业'

  try {
    await ElMessageBox.confirm(`确定要将该影院设置为${action}状态吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await cinemaApi.updateStatus(row.id!, newStatus)
    ElMessage.success('状态更新成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('状态更新失败:', error)
      ElMessage.error('状态更新失败')
    }
  }
}

const handleDelete = async (row: Cinema) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除影院"${row.name}"吗？此操作不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await cinemaApi.delete(row.id!)
    ElMessage.success('删除成功')

    // 如果当前页删完了，回到上一页
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
})
</script>

<style scoped>
.cinema-page {
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

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.lnglat-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.lnglat-input {
  flex: 1;
  min-width: 0;
}

:deep(.lnglat-input .el-input__inner) {
  text-align: left;
}

:deep(.el-dialog__body) {
  padding: 20px 20px 10px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}

:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table .el-button) {
  margin: 0 4px;
}

/* 图片上传组件样式 */
.upload-area {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
}

.image-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.image-preview-item {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 0;
  box-shadow:
    4px 4px 8px var(--admin-shadow-dark),
    -4px -4px 8px var(--admin-shadow-light);
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  opacity: 0;
  transition: opacity 0.2s;
}

.image-preview-item:hover .preview-overlay {
  opacity: 1;
}

.preview-action {
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: background 0.15s;
}

.preview-action:hover {
  background: rgba(255, 255, 255, 0.2);
}

.preview-action.delete:hover {
  color: #f56c6c;
}

:deep(.el-upload) {
  display: block;
}

.upload-trigger {
  width: 120px;
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.3s ease-in-out;
  background: var(--admin-card);
  box-shadow:
    inset 4px 4px 8px var(--admin-shadow-dark),
    inset -4px -4px 8px var(--admin-shadow-light);
}

.upload-trigger:hover {
  background: var(--admin-primary-soft);
  box-shadow:
    inset 2px 2px 4px var(--admin-shadow-dark),
    inset -2px -2px 4px var(--admin-shadow-light);
}

.upload-trigger.is-uploading {
  pointer-events: none;
  opacity: 0.6;
}

.upload-icon {
  font-size: 28px;
  color: #909399;
}

.upload-icon.is-loading {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.upload-trigger:hover .upload-icon {
  color: var(--admin-primary);
}

.upload-text {
  font-size: 13px;
  color: #909399;
}

.preview-dialog {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-dialog-img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 8px;
}
</style>
