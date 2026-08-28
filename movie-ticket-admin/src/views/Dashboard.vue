<template>
  <div class="dashboard-page">
    <h2>数据统计</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card">
          <div class="stat-icon orange">
            <el-icon size="40"><Film /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.movieCount || 0 }}</div>
            <div class="stat-label">电影总数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card">
          <div class="stat-icon green">
            <el-icon size="40"><OfficeBuilding /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.cinemaCount || 0 }}</div>
            <div class="stat-label">影院总数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card">
          <div class="stat-icon amber">
            <el-icon size="40"><Tickets /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.todayOrderCount || 0 }}</div>
            <div class="stat-label">今日订单</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card">
          <div class="stat-icon red">
            <el-icon size="40"><Money /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">¥{{ stats.todayRevenue || 0 }}</div>
            <div class="stat-label">今日收入</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 票房排行榜 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>票房排行榜</span>
          <el-button text @click="loadBoxOffice">刷新</el-button>
        </div>
      </template>

      <el-table :data="boxOfficeData" v-loading="boxOfficeLoading">
        <el-table-column type="index" label="排名" width="80" />
        <el-table-column prop="movieTitle" label="电影" min-width="200" />
        <el-table-column prop="boxOffice" label="票房" width="150">
          <template #default="{ row }">
            <span class="revenue">¥{{ row.boxOffice }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ticketCount" label="售票数" width="120">
          <template #default="{ row }">
            {{ row.ticketCount }}张
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 用户统计 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <span>用户统计</span>
          </template>
          <div class="user-stats">
            <div class="stat-item">
              <span class="label">总用户数：</span>
              <span class="value">{{ stats.userCount || 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="router.push('/cinema')">
              <el-icon><OfficeBuilding /></el-icon>
              影院管理
            </el-button>
            <el-button type="success" @click="router.push('/movie')">
              <el-icon><Film /></el-icon>
              电影管理
            </el-button>
            <el-button type="warning" @click="router.push('/showtime')">
              <el-icon><Clock /></el-icon>
              排片管理
            </el-button>
            <el-button type="info" @click="router.push('/order')">
              <el-icon><Tickets /></el-icon>
              订单管理
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const boxOfficeLoading = ref(false)
const stats = ref<any>({})
const boxOfficeData = ref<any[]>([])

const loadStats = async () => {
  loading.value = true
  try {
    stats.value = await request.get('/statistics/dashboard')
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

const loadBoxOffice = async () => {
  boxOfficeLoading.value = true
  try {
    boxOfficeData.value = await request.get('/statistics/box-office', {
      params: { limit: 10 }
    })
  } catch (error) {
    console.error('加载票房数据失败:', error)
  } finally {
    boxOfficeLoading.value = false
  }
}

onMounted(() => {
  loadStats()
  loadBoxOffice()
})
</script>

<style scoped>
.dashboard-page {
  padding: 0;
}

.dashboard-page h2 {
  margin: 0 0 20px;
  color: var(--admin-text);
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  min-height: 132px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #111;
  margin-right: 16px;
  transition: transform 0.18s ease;
}

.stat-icon :deep(.el-icon) { font-size: 28px !important; }
.stat-icon.orange { background: #ff8400; }
.stat-icon.green { background: #a7d879; }
.stat-icon.amber { background: #ffd36e; }
.stat-icon.red { background: #ff8d81; }
.stat-card:hover .stat-icon { transform: rotate(-3deg) scale(1.03); }

.stat-content {
  flex: 1;
}

.stat-value {
  color: var(--admin-text);
  font-family: 'JetBrains Mono', monospace;
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: var(--admin-muted);
  font-weight: 500;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-stats {
  padding: 20px 0;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 16px;
}

.stat-item .label {
  color: var(--admin-muted);
}

.stat-item .value {
  font-weight: bold;
  color: var(--admin-text);
  font-size: 20px;
}

.revenue {
  color: var(--admin-primary);
  font-family: 'JetBrains Mono', monospace;
  font-weight: 800;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 0;
}

.quick-actions .el-button {
  flex: 1;
  min-width: 120px;
}

@media (max-width: 700px) {
  .dashboard-page h2 { font-size: 22px; }
  .stat-card :deep(.el-card__body) { min-height: 112px; }
}
</style>
