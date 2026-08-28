<template>
  <div class="order-confirm-page">
    <el-page-header @back="router.back()" title="返回" />

    <div class="container" v-loading="loading">
      <h2>确认订单</h2>

      <el-card class="info-card" v-if="showtime">
        <h3>观影信息</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="电影">{{ showtime.movieTitle }}</el-descriptions-item>
          <el-descriptions-item label="影院">{{ showtime.cinemaName }}</el-descriptions-item>
          <el-descriptions-item label="影厅">{{ showtime.hallName }}</el-descriptions-item>
          <el-descriptions-item label="场次">{{ formatDateTime(showtime.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="座位" :span="2">
            <el-tag v-for="seat in seats" :key="seat" style="margin: 0 5px;">{{ seat }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ showtime.basePrice }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ seats.length }}张</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="info-card">
        <h3>备注信息</h3>
        <el-input
          v-model="remark"
          type="textarea"
          :rows="3"
          placeholder="选填，如有特殊需求请在此说明"
          maxlength="200"
          show-word-limit
        />
      </el-card>

      <div class="total-section">
        <div class="total-price">
          <span>总计：</span>
          <span class="price">¥{{ totalPrice }}</span>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          @click="submitOrder"
          style="width: 200px;"
        >
          提交订单
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showtimeApi } from '@/api/showtime'
import { orderApi } from '@/api/order'
import type { Showtime } from '@/types'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const showtime = ref<Showtime | null>(null)
const seats = ref<string[]>([])
const remark = ref('')

const totalPrice = computed(() => {
  if (!showtime.value) return 0
  return (showtime.value.basePrice * seats.value.length).toFixed(2)
})

const loadData = async () => {
  loading.value = true
  try {
    const showtimeId = Number(route.query.showtimeId)
    const seatsStr = route.query.seats as string

    if (!showtimeId || !seatsStr) {
      ElMessage.error('参数错误')
      router.back()
      return
    }

    showtime.value = await showtimeApi.getById(showtimeId)
    seats.value = seatsStr.split(',')
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const formatDateTime = (datetime: string) => {
  const date = new Date(datetime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const submitOrder = async () => {
  if (!showtime.value) return

  submitting.value = true
  try {
    const orderNo = await orderApi.create({
      showtimeId: showtime.value.id,
      seats: seats.value,
      remark: remark.value
    })

    ElMessage.success('订单创建成功')
    router.push(`/order/pay/${orderNo}`)
  } catch (error) {
    console.error('创建订单失败:', error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.order-confirm-page {
  min-height: 100vh;
  background-color: #f5f5f5;
  padding: 20px;
}

.container {
  max-width: 800px;
  margin: 20px auto;
}

.container h2 {
  font-size: 24px;
  margin-bottom: 20px;
  color: #333;
}

.info-card {
  margin-bottom: 20px;
}

.info-card h3 {
  font-size: 18px;
  margin-bottom: 15px;
  color: #333;
}

.total-section {
  background-color: #fff;
  padding: 30px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-price {
  font-size: 20px;
}

.total-price .price {
  font-size: 32px;
  font-weight: bold;
  color: #f56c6c;
  margin-left: 10px;
}
</style>
