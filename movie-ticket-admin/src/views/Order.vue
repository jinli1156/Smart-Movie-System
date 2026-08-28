<template>
  <div class="order-page">
    <el-card>
      <template #header>
        <span>订单管理</span>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.orderNo"
          placeholder="订单号"
          clearable
          style="width: 240px;"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.status"
          placeholder="订单状态"
          clearable
          style="width: 160px;"
          @change="handleSearch"
        >
          <el-option label="全部" :value="undefined" />
          <el-option label="待支付" :value="0" />
          <el-option label="已支付" :value="1" />
          <el-option label="已取消" :value="2" />
          <el-option label="已退款" :value="3" />
          <el-option label="已完成" :value="4" />
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

      <!-- 订单列表 -->
      <el-table :data="orderList" v-loading="loading" style="margin-top: 20px;">
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="电影" min-width="200">
          <template #default="{ row }">
            <div class="movie-cell">
              <el-image
                v-if="row.moviePoster"
                :src="row.moviePoster"
                fit="cover"
                style="width: 40px; height: 56px; border-radius: 2px;"
              />
              <div class="movie-info">
                <div class="movie-title">{{ row.movieTitle }}</div>
                <div class="movie-meta">{{ row.cinemaName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="hallName" label="影厅" width="100" />
        <el-table-column label="放映时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.showtime) }}
          </template>
        </el-table-column>
        <el-table-column label="座位" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.seats }}
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="用户" width="120" show-overflow-tooltip />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <span class="price">¥{{ row.totalPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning">待支付</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">已支付</el-tag>
            <el-tag v-else-if="row.status === 2" type="info">已取消</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger">已退款</el-tag>
            <el-tag v-else-if="row.status === 4">已完成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button
              link
              type="warning"
              v-if="row.status === 0"
              @click="handleCancel(row)"
            >
              <el-icon><Close /></el-icon>
              取消
            </el-button>
            <el-button
              link
              type="danger"
              v-if="row.status === 1"
              @click="handleRefund(row)"
            >
              <el-icon><RefreshRight /></el-icon>
              退票
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
        @size-change="loadOrders"
        @current-change="loadOrders"
      />
    </el-card>

    <!-- 订单详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="订单详情" width="700px">
      <el-descriptions :column="2" border v-if="currentOrder">
        <el-descriptions-item label="订单号" :span="2">
          {{ currentOrder.orderNo }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="currentOrder.status === 0" type="warning">待支付</el-tag>
          <el-tag v-else-if="currentOrder.status === 1" type="success">已支付</el-tag>
          <el-tag v-else-if="currentOrder.status === 2" type="info">已取消</el-tag>
          <el-tag v-else-if="currentOrder.status === 3" type="danger">已退款</el-tag>
          <el-tag v-else-if="currentOrder.status === 4">已完成</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span class="price">¥{{ currentOrder.totalPrice }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="电影">{{ currentOrder.movieTitle }}</el-descriptions-item>
        <el-descriptions-item label="影院">{{ currentOrder.cinemaName }}</el-descriptions-item>
        <el-descriptions-item label="影厅">{{ currentOrder.hallName }}</el-descriptions-item>
        <el-descriptions-item label="放映时间">
          {{ formatDateTime(currentOrder.showtime) }}
        </el-descriptions-item>
        <el-descriptions-item label="座位" :span="2">{{ currentOrder.seats }}</el-descriptions-item>
        <el-descriptions-item label="座位数">{{ currentOrder.seatCount }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentOrder.userName }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">
          {{ formatDateTime(currentOrder.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="支付时间">
          {{ currentOrder.payTime ? formatDateTime(currentOrder.payTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ currentOrder.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Close, RefreshRight } from '@element-plus/icons-vue'
import { orderApi } from '@/api/order'
import type { Order } from '@/types'

const loading = ref(false)
const viewDialogVisible = ref(false)
const currentOrder = ref<Order | null>(null)

const searchForm = reactive({
  orderNo: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const orderList = ref<Order[]>([])

const formatDateTime = (val?: string) => {
  if (!val) return '-'
  return val.replace('T', ' ').slice(0, 19)
}

const loadOrders = async () => {
  loading.value = true
  try {
    const res = await orderApi.getPage({
      page: pagination.page,
      size: pagination.size,
      orderNo: searchForm.orderNo || undefined,
      status: searchForm.status
    })
    orderList.value = res.records
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('加载订单列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadOrders()
}

const handleReset = () => {
  searchForm.orderNo = ''
  searchForm.status = undefined
  pagination.page = 1
  loadOrders()
}

const handleView = async (row: Order) => {
  try {
    currentOrder.value = await orderApi.getByOrderNo(row.orderNo)
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载订单详情失败')
  }
}

const handleCancel = async (row: Order) => {
  try {
    await ElMessageBox.confirm(
      `确定取消订单"${row.orderNo}"吗？`,
      '操作确认',
      { type: 'warning' }
    )
    await orderApi.cancel(row.orderNo)
    ElMessage.success('订单已取消')
    loadOrders()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

const handleRefund = async (row: Order) => {
  try {
    await ElMessageBox.confirm(
      `确定对订单"${row.orderNo}"进行退票吗？退款金额 ¥${row.totalPrice}`,
      '退票确认',
      { type: 'warning', confirmButtonText: '确认退票' }
    )
    await orderApi.refund(row.orderNo)
    ElMessage.success('退票成功')
    loadOrders()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('退票失败')
    }
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-page {
  padding: 0;
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
</style>
