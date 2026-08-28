<template>
  <div class="user-page">
    <el-card>
      <template #header>
        <span>用户管理</span>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="用户名/昵称/手机/邮箱"
          clearable
          style="width: 260px;"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.role"
          placeholder="角色"
          clearable
          style="width: 160px;"
          @change="handleSearch"
        >
          <el-option label="全部" value="" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="影院管理员" value="CINEMA_ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
        <el-select
          v-model="searchForm.status"
          placeholder="状态"
          clearable
          style="width: 140px;"
          @change="handleSearch"
        >
          <el-option label="全部" :value="undefined" />
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
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

      <!-- 用户列表 -->
      <el-table :data="userList" v-loading="loading" style="margin-top: 20px;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="头像" width="80">
          <template #default="{ row }">
            <el-avatar
              v-if="row.avatar"
              :src="row.avatar"
              :size="40"
            />
            <el-avatar v-else :size="40">
              {{ (row.nickname || row.username || '?').charAt(0) }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="danger">管理员</el-tag>
            <el-tag v-else-if="row.role === 'CINEMA_ADMIN'" type="warning">影院管理员</el-tag>
            <el-tag v-else type="info">普通用户</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              <el-icon><Switch /></el-icon>
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="handleResetPassword(row)">
              <el-icon><Key /></el-icon>
              重置密码
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
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑用户"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="用户名">
          <el-input v-model="formData.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="formData.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色" style="width: 100%;">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="影院管理员" value="CINEMA_ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
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

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="重置密码"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="不少于6位"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleResetPasswordSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Edit, Delete, Switch, Key } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import type { User } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const passwordDialogVisible = ref(false)

const searchForm = reactive({
  keyword: '',
  role: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const userList = ref<User[]>([])
const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const currentUserId = ref<number | null>(null)

const formData = reactive<User>({
  username: '',
  nickname: '',
  phone: '',
  email: '',
  role: 'USER',
  status: 1
})

const passwordForm = reactive({
  password: '',
  confirmPassword: ''
})

const formRules: FormRules = {
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const passwordRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码不少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, val, cb) => {
        if (val !== passwordForm.password) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const formatDateTime = (val?: string) => {
  if (!val) return '-'
  return val.replace('T', ' ').slice(0, 19)
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await userApi.getPage({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      role: searchForm.role || undefined,
      status: searchForm.status
    })
    userList.value = res.records
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('加载用户列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadUsers()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.status = undefined
  pagination.page = 1
  loadUsers()
}

const handleEdit = (row: User) => {
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleToggleStatus = async (row: User) => {
  const action = row.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}用户"${row.username}"吗？`, '操作确认', {
      type: 'warning'
    })
    await userApi.updateStatus(row.id!, row.status === 1 ? 0 : 1)
    ElMessage.success(`${action}成功`)
    loadUsers()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`${action}失败`)
    }
  }
}

const handleResetPassword = (row: User) => {
  currentUserId.value = row.id!
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
  passwordDialogVisible.value = true
}

const handleResetPasswordSubmit = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await userApi.resetPassword(currentUserId.value!, passwordForm.password)
      ElMessage.success('密码重置成功')
      passwordDialogVisible.value = false
    } catch (error) {
      console.error(error)
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = async (row: User) => {
  try {
    await ElMessageBox.confirm(
      `确定删除用户"${row.username}"吗？删除后将无法恢复。`,
      '删除确认',
      { type: 'warning' }
    )
    await userApi.delete(row.id!)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await userApi.update(formData.id!, {
        nickname: formData.nickname,
        phone: formData.phone,
        email: formData.email,
        role: formData.role,
        status: formData.status,
        avatar: formData.avatar
      })
      ElMessage.success('更新成功')
      dialogVisible.value = false
      loadUsers()
    } catch (error) {
      console.error(error)
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-page {
  padding: 0;
}

.search-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
