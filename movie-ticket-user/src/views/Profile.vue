<template>
  <div class="profile-page">
    <!-- 左侧菜单栏 -->
    <AppSidebar :show-quick-access="false" />

    <!-- 个人中心内容区 -->
    <div class="profile-body">
      <!-- 左侧个人信息卡片 -->
      <aside class="profile-sidebar">
      <div class="profile-card">
        <div class="profile-avatar">
          <div class="avatar-circle">{{ avatarChar }}</div>
        </div>
        <h2 class="profile-name">{{ userStore.userInfo?.nickname || '用户' }}</h2>
        <p class="profile-phone">{{ userStore.userInfo?.phone || '未绑定手机' }}</p>

        <div class="profile-level">
          <span class="level-badge">LVL.3</span>
          <span class="level-text">黄金会员</span>
          <span class="level-icon">⭐</span>
        </div>

        <button class="edit-profile-btn" @click="showEditDialog = true">
          <MaterialIcon class="msr" name="edit" />
          <span>编辑资料</span>
        </button>
      </div>

      <!-- 会员权益 -->
      <div class="benefits-card" v-loading="loadingProfile">
        <div class="benefits-header">
          <MaterialIcon class="msr" name="workspace_premium" />
          <span>我的会员权益</span>
        </div>
        <div class="benefits-stats">
          <div class="stat-item">
            <span class="stat-label">优惠券</span>
            <span class="stat-value">{{ availableCouponCount }} 张</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-label">积分</span>
            <span class="stat-value">{{ Number(userStore.userInfo?.points || 0).toLocaleString() }}</span>
          </div>
        </div>
      </div>

      <!-- 快捷功能 -->
      <section class="quick-actions-section">
        <h3 class="section-title">快捷功能</h3>
        <div class="quick-actions-grid">
          <button
            v-for="action in quickActions"
            :key="action.key"
            class="quick-action-card"
            @click="onQuickAction(action)"
          >
            <MaterialIcon class="msr action-icon" :style="{ color: action.color }" :name="action.icon" />
            <span class="action-label">{{ action.label }}</span>
            <span v-if="action.badge" class="action-badge">{{ action.badge }}</span>
          </button>
        </div>
      </section>
      <!-- 我的优惠券 -->
      <section ref="couponSectionRef" class="coupons-section">
        <div class="coupon-section-head">
          <div>
            <div class="coupon-eyebrow">MY COUPONS</div>
            <h3 class="section-title">我的优惠券</h3>
            <p>新账号赠送 1 张满 ¥80 减 ¥6.99 优惠券，领取后 7 天内有效</p>
          </div>
          <div class="coupon-tabs">
            <button
              v-for="tab in couponTabs"
              :key="tab.key"
              :class="{ active: activeCouponTab === tab.key }"
              @click="activeCouponTab = tab.key"
            >
              {{ tab.label }} <span>{{ tab.count }}</span>
            </button>
          </div>
        </div>

        <div v-loading="loadingCoupons" class="coupon-grid">
          <article
            v-for="coupon in displayCoupons"
            :key="coupon.id"
            class="profile-coupon"
            :class="{ unavailable: !coupon.available }"
          >
            <div class="coupon-value">
              <span class="coupon-currency">¥</span>
              <strong>{{ Number(coupon.amount).toFixed(2) }}</strong>
            </div>
            <div class="coupon-detail">
              <div class="coupon-name-row">
                <h4>{{ coupon.name }}</h4>
                <span :class="`coupon-status status-${coupon.status}`">{{ coupon.statusText }}</span>
              </div>
              <p>{{ Number(coupon.minSpend) > 0 ? `满 ¥${coupon.minSpend} 可用` : '无门槛使用' }}</p>
              <span class="coupon-validity">有效期至 {{ formatCouponDate(coupon.expireTime) }}</span>
            </div>
            <button v-if="coupon.available" class="coupon-use" @click="router.push('/movies')">
              去使用 <MaterialIcon class="msr" name="arrow_forward" />
            </button>
            <span v-else class="coupon-watermark">{{ coupon.status === 1 ? 'USED' : 'EXPIRED' }}</span>
          </article>
        </div>
        <el-empty v-if="!loadingCoupons && displayCoupons.length === 0" description="暂无优惠券" />
      </section>

    </aside>

    <!-- 右侧主要内容 -->
    <main class="profile-main">
      <!-- 我的电影票 -->
      <section class="tickets-section">
        <div class="section-header">
          <h3 class="section-title">我的电影票</h3>
          <div class="section-tabs">
            <button
              v-for="tab in ticketTabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTicketTab === tab.key }"
              @click="activeTicketTab = tab.key"
            >
              {{ tab.label }}
              <span v-if="tab.count" class="tab-count">{{ tab.count }}</span>
            </button>
          </div>
          <div class="section-actions">


          </div>
        </div>

        <div class="tickets-grid" v-loading="loadingOrders">
          <div v-for="order in displayOrders" :key="order.id" class="ticket-card">
            <div class="ticket-poster">
              <img v-if="order.moviePoster" v-poster :src="order.moviePoster" :alt="order.movieTitle" />
              <div v-else class="poster-placeholder">
                <MaterialIcon class="msr" name="movie" />
              </div>
              <span class="ticket-status" :class="getStatusClass(order.status)">
                {{ getStatusText(order.status) }}
              </span>
            </div>

            <div class="ticket-info">
              <h4 class="ticket-title">{{ order.movieTitle }}</h4>
              <div class="ticket-meta">
                <div class="meta-row">
                  <MaterialIcon class="msr" name="theaters" />
                  <span>{{ order.cinemaName }}</span>
                </div>
                <div class="meta-row">
                  <MaterialIcon class="msr" name="event_seat" />
                  <span>{{ order.hallName }}</span>
                </div>
                <div class="meta-row">
                  <MaterialIcon class="msr" name="schedule" />
                  <span>{{ formatShowtime(order.showtime) }}</span>
                </div>
                <div class="meta-row">
                  <MaterialIcon class="msr" name="confirmation_number" />
                  <span>{{ order.orderNo }}</span>
                </div>
              </div>

              <div class="ticket-footer">
                <div class="ticket-price">
                  <span class="currency">¥</span>
                  <span class="amount">{{ order.totalPrice }}</span>
                </div>
                <button
                  v-if="order.status === 1 && isMovieEnded(order)"
                  class="ticket-action-btn watched"
                  disabled
                >
                  <MaterialIcon class="msr" name="movie" />
                  <span>已观影</span>
                </button>
                <button
                  v-else-if="order.status === 1 && isTicketPicked(order)"
                  class="ticket-action-btn picked"
                  disabled
                >
                  <MaterialIcon class="msr" name="check_circle" />
                  <span>已取票</span>
                </button>
                <button
                  v-else-if="order.status === 1"
                  class="ticket-action-btn primary"
                  @click="viewTicket(order)"
                >
                  <MaterialIcon class="msr" name="qr_code" />
                  <span>查看取票码</span>
                </button>
                <button
                  v-else-if="order.status === 0"
                  class="ticket-action-btn"
                  @click="goToPay(order)"
                >
                  <span>去支付</span>
                  <MaterialIcon class="msr" name="arrow_forward" />
                </button>
                <button
                  v-else
                  class="ticket-action-btn"
                  @click="viewOrder(order)"
                >
                  <span>查看详情</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="!loadingOrders && displayOrders.length === 0" description="暂无订单" />
      </section>
      <!-- 观影记录 -->
      <section ref="historySectionRef" class="history-section">
        <div class="history-head">
          <div>
            <div class="history-eyebrow">WATCH HISTORY</div>
            <h3 class="section-title">观影记录</h3>
            <p>回顾每一次银幕相遇</p>
          </div>
          <div class="history-stats">
            <div>
              <strong>{{ historyTotal }}</strong>
              <span>累计观影</span>
            </div>
            <i></i>
            <div>
              <strong>¥{{ historySpend.toFixed(0) }}</strong>
              <span>记录内消费</span>
            </div>
          </div>
        </div>

        <div v-loading="loadingHistory" class="history-list">
          <article v-for="record in viewingHistory" :key="record.id" class="history-card">
            <div class="history-poster">
              <img v-if="record.moviePoster" v-poster :src="record.moviePoster" :alt="record.movieTitle" />
              <MaterialIcon v-else class="msr" name="movie" />
              <span class="history-watched"><MaterialIcon class="msr" name="check" /> 已观影</span>
            </div>
            <div class="history-info">
              <div class="history-date">{{ formatHistoryDate(record.showtime) }}</div>
              <h4>{{ record.movieTitle || '未知影片' }}</h4>
              <div class="history-meta">
                <span><MaterialIcon class="msr" name="theaters" />{{ record.cinemaName }}</span>
                <span><MaterialIcon class="msr" name="event_seat" />{{ record.hallName }} · {{ record.seats }}</span>
              </div>
              <div class="history-footer">
                <span class="history-price">实付 ¥{{ Number(record.totalPrice).toFixed(2) }}</span>
                <button @click="router.push({ path: `/order/${record.orderNo}`, query: { souvenir: '1' } })">
                  观影纪念票 <MaterialIcon class="msr" name="auto_awesome" />
                </button>
                <button v-if="record.movieId" @click="router.push(`/movie/${record.movieId}`)">
                  去评价 <MaterialIcon class="msr" name="arrow_forward" />
                </button>
              </div>
            </div>
          </article>
        </div>
        <el-empty
          v-if="!loadingHistory && viewingHistory.length === 0"
          description="还没有观影记录，完成一次观影后会显示在这里"
        />
      </section>

    </main>
    </div>

    <!-- ============ 编辑资料对话框 ============ -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑资料"
      width="600px"
      :close-on-click-modal="false"
      class="edit-profile-dialog consumer-dialog"
      align-center
    >
      <div class="edit-form">
        <div class="form-section">
          <div class="form-section-title">
            <MaterialIcon class="msr" name="person" />
            <span>基本信息</span>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">昵称</span>
              <span class="label-required">*</span>
            </label>
            <el-input
              v-model="editForm.nickname"
              placeholder="请输入昵称"
              maxlength="20"
              show-word-limit
              clearable
            />
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">手机号</span>
            </label>
            <el-input
              v-model="editForm.phone"
              placeholder="请输入手机号"
              maxlength="11"
              disabled
            >
              <template #suffix>
                <span class="input-tip">手机号不可修改</span>
              </template>
            </el-input>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">性别</span>
            </label>
            <el-radio-group v-model="editForm.gender" class="gender-group">
              <el-radio :label="1">
                <MaterialIcon class="msr" name="male" />
                <span>男</span>
              </el-radio>
              <el-radio :label="2">
                <MaterialIcon class="msr" name="female" />
                <span>女</span>
              </el-radio>
              <el-radio :label="0">
                <MaterialIcon class="msr" name="help" />
                <span>保密</span>
              </el-radio>
            </el-radio-group>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">生日</span>
            </label>
            <el-date-picker
              v-model="editForm.birthday"
              type="date"
              placeholder="选择生日"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
          </div>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <MaterialIcon class="msr" name="location_on" />
            <span>地区信息</span>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">所在城市</span>
            </label>
            <el-select
              v-model="editForm.city"
              placeholder="选择城市"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="city in cities"
                :key="city"
                :label="city"
                :value="city"
              />
            </el-select>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">详细地址</span>
            </label>
            <el-input
              v-model="editForm.address"
              type="textarea"
              :rows="3"
              placeholder="请输入详细地址（选填）"
              maxlength="100"
              show-word-limit
            />
          </div>
        </div>

        <div ref="preferenceSectionRef" class="form-section">
          <div class="form-section-title">
            <MaterialIcon class="msr" name="movie" />
            <span>观影偏好</span>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">喜欢的类型</span>
            </label>
            <div class="genre-tags">
              <button
                v-for="genre in genreOptions"
                :key="genre"
                class="genre-tag"
                :class="{ active: editForm.favoriteGenres.includes(genre) }"
                @click="toggleGenre(genre)"
              >
                {{ genre }}
              </button>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">个性签名</span>
            </label>
            <el-input
              v-model="editForm.signature"
              type="textarea"
              :rows="2"
              placeholder="写下你的个性签名..."
              maxlength="50"
              show-word-limit
            />
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <button class="dialog-btn cancel" @click="showEditDialog = false">
            <span>取消</span>
          </button>
          <button class="dialog-btn primary" @click="handleSaveProfile" :disabled="saving">
            <MaterialIcon class="msr" v-if="saving" name="progress_activity" />
            <span>{{ saving ? '保存中...' : '保存' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- ============ 修改密码对话框 ============ -->
    <el-dialog
      v-model="showPasswordDialog"
      title="修改密码"
      width="480px"
      :close-on-click-modal="false"
      class="edit-profile-dialog consumer-dialog"
      align-center
    >
      <div class="edit-form">
        <div class="form-section">
          <div class="form-section-title">
            <MaterialIcon class="msr" name="lock" />
            <span>账号安全</span>
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">原密码</span>
              <span class="label-required">*</span>
            </label>
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
              maxlength="20"
            />
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">新密码</span>
              <span class="label-required">*</span>
            </label>
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="6-20 位，建议字母 + 数字组合"
              show-password
              maxlength="20"
            />
          </div>

          <div class="form-row">
            <label class="form-label">
              <span class="label-text">确认新密码</span>
              <span class="label-required">*</span>
            </label>
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              placeholder="再次输入新密码"
              show-password
              maxlength="20"
            />
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <button class="dialog-btn cancel" @click="showPasswordDialog = false">
            <span>取消</span>
          </button>
          <button class="dialog-btn primary" @click="handleChangePassword" :disabled="changing">
            <MaterialIcon class="msr" v-if="changing" name="progress_activity" />
            <span>{{ changing ? '提交中...' : '确认修改' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 取票码二维码对话框 -->
    <TicketQRCode v-if="showQRCode" v-model="showQRCode" :order="selectedOrder" />
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心：维护资料与密码，展示订单、观影历史、积分及优惠券。
 */
import { ref, computed, defineAsyncComponent, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { useUserStore } from '@/stores/user'
import { orderApi } from '@/api/order'
import { userApi } from '@/api/user'
import { couponApi } from '@/api/coupon'
import type { Coupon, Order } from '@/types'
import AppSidebar from '@/components/AppSidebar.vue'

const TicketQRCode = defineAsyncComponent(() => import('@/components/TicketQRCode.vue'))

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loadingProfile = ref(false)
const loadingOrders = ref(false)
const orders = ref<Order[]>([])
const coupons = ref<Coupon[]>([])
const loadingCoupons = ref(false)
const viewingHistory = ref<Order[]>([])
const loadingHistory = ref(false)
const historyTotal = ref(0)
const activeCouponTab = ref<'available' | 'used' | 'expired'>('available')
const couponSectionRef = ref<HTMLElement | null>(null)
const historySectionRef = ref<HTMLElement | null>(null)
const preferenceSectionRef = ref<HTMLElement | null>(null)
const activeTicketTab = ref<'all' | 'pending' | 'paid' | 'cancelled' | 'refunded'>('all')
const showEditDialog = ref(false)
const saving = ref(false)
const showPasswordDialog = ref(false)
const changing = ref(false)
const showQRCode = ref(false)
const selectedOrder = ref<Order | null>(null)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 编辑表单数据
const editForm = ref({
  nickname: '',
  phone: '',
  gender: 0,
  birthday: '',
  city: '',
  address: '',
  favoriteGenres: [] as string[],
  signature: ''
})

// 城市列表
const cities = [
  '北京', '上海', '广州', '深圳', '杭州', '成都', '重庆', '武汉',
  '西安', '南京', '天津', '苏州', '长沙', '郑州', '东莞', '青岛'
]

// 电影类型选项
const genreOptions = [
  '动作', '喜剧', '爱情', '科幻', '恐怖', '悬疑',
  '剧情', '动画', '战争', '犯罪', '冒险', '奇幻'
]

const ticketTabs = [
  { key: 'all', label: '全部', count: 0 },
  { key: 'pending', label: '待支付', count: 0 },
  { key: 'paid', label: '待观影', count: 0 },
  { key: 'cancelled', label: '已取消', count: 0 },
  { key: 'refunded', label: '已退款', count: 0 }
] as const

const quickActions = computed(() => [
  { key: 'orders', label: '我的订单', icon: 'receipt_long', color: '#FF8400' },
  { key: 'favorites', label: '想看清单', icon: 'favorite', color: '#EC4899' },
  { key: 'history', label: '观影记录', icon: 'history', color: '#8B5CF6' },
  { key: 'coupons', label: '优惠券', icon: 'confirmation_number', color: '#10B981', badge: String(availableCouponCount.value) },
  { key: 'settings', label: '修改密码', icon: 'lock', color: '#6B7280' }
])

const availableCouponCount = computed(() => coupons.value.filter(coupon => coupon.available).length)
const couponTabs = computed(() => [
  { key: 'available' as const, label: '可使用', count: coupons.value.filter(c => c.status === 0).length },
  { key: 'used' as const, label: '已使用', count: coupons.value.filter(c => c.status === 1).length },
  { key: 'expired' as const, label: '已过期', count: coupons.value.filter(c => c.status === 2).length }
])
const displayCoupons = computed(() => coupons.value.filter(coupon => {
  if (activeCouponTab.value === 'available') return coupon.status === 0
  if (activeCouponTab.value === 'used') return coupon.status === 1
  return coupon.status === 2
}))
const historySpend = computed(() => viewingHistory.value.reduce(
  (total, record) => total + Number(record.totalPrice || 0), 0
))

const avatarChar = computed(() => {
  const n = userStore.userInfo?.nickname || '用'
  return n.charAt(0).toUpperCase()
})

const displayOrders = computed(() => {
  if (activeTicketTab.value === 'all') return orders.value
  return orders.value.filter(o => {
    if (activeTicketTab.value === 'pending') return o.status === 0  // 待支付
    if (activeTicketTab.value === 'paid') return o.status === 1     // 待观影（已支付）
    if (activeTicketTab.value === 'cancelled') return o.status === 2 // 已取消
    if (activeTicketTab.value === 'refunded') return o.status === 3  // 已退款
    return true
  })
})

// 初始化编辑表单
const initEditForm = () => {
  const userInfo = userStore.userInfo
  editForm.value = {
    nickname: userInfo?.nickname || '',
    phone: userInfo?.phone || '',
    gender: userInfo?.gender || 0,
    birthday: userInfo?.birthday || '',
    city: userInfo?.city || '',
    address: userInfo?.address || '',
    favoriteGenres: userInfo?.favoriteGenres || [],
    signature: userInfo?.signature || ''
  }
}

const openPreferenceEditor = async () => {
  initEditForm()
  showEditDialog.value = true
  await nextTick()
  const section = preferenceSectionRef.value
  const dialogBody = section?.closest('.el-dialog__body') as HTMLElement | null
  if (section && dialogBody) {
    dialogBody.scrollTo({ top: Math.max(0, section.offsetTop - 20), behavior: 'smooth' })
  }
}

// 切换电影类型
const toggleGenre = (genre: string) => {
  const index = editForm.value.favoriteGenres.indexOf(genre)
  if (index > -1) {
    editForm.value.favoriteGenres.splice(index, 1)
  } else {
    if (editForm.value.favoriteGenres.length < 5) {
      editForm.value.favoriteGenres.push(genre)
    } else {
      ElMessage.warning('最多选择5个类型')
    }
  }
}

// 禁用未来日期
const disabledDate = (time: Date) => {
  return time.getTime() > Date.now()
}

// 保存资料
const handleSaveProfile = async () => {
  if (!editForm.value.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }

  saving.value = true
  try {
    // 调用API更新用户信息
    await userStore.updateUserInfo(editForm.value)
    ElMessage.success('保存成功')
    showEditDialog.value = false
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

/** 按当前状态和页码加载订单列表。 */
const loadOrders = async () => {
  loadingOrders.value = true
  try {
    const res = await orderApi.getMyOrders({ page: 1, size: 10 })
    orders.value = res.records
  } catch (e) {
    console.error('加载订单失败:', e)
  } finally {
    loadingOrders.value = false
  }
}

/** 读取本人优惠券，更新页面可用优惠信息。 */
const loadCoupons = async () => {
  loadingCoupons.value = true
  try {
    coupons.value = await couponApi.getMine()
  } catch (e) {
    console.error('加载优惠券失败:', e)
    coupons.value = []
  } finally {
    loadingCoupons.value = false
  }
}

/** 加载本人已完成的观影记录。 */
const loadViewingHistory = async () => {
  loadingHistory.value = true
  try {
    const result = await orderApi.getViewingHistory({ page: 1, size: 12 })
    viewingHistory.value = result.records || []
    historyTotal.value = result.total || 0
  } catch (e) {
    console.error('加载观影记录失败:', e)
    viewingHistory.value = []
    historyTotal.value = 0
  } finally {
    loadingHistory.value = false
  }
}

const formatCouponDate = (value: string) => {
  const date = new Date(value)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

const formatHistoryDate = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} · ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const getStatusClass = (status: number) => {
  const map: Record<number, string> = {
    0: 'status-pending',
    1: 'status-paid',
    2: 'status-cancelled',
    3: 'status-refunded'
  }
  return map[status] || ''
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = {
    0: '待支付',
    1: '已支付',
    2: '已取消',
    3: '已退款'
  }
  return map[status] || '未知'
}

const formatShowtime = (showtime?: string) => {
  if (!showtime) return '--'
  const d = new Date(showtime)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  return `${month}.${day} ${hour}:${minute}`
}

const isTicketPicked = (order: Order) => {
  return order.isPicked === 1
}

const isMovieEnded = (order: Order) => {
  if (!order.showtime) return false
  const showtimeDate = new Date(order.showtime).getTime()
  const now = new Date().getTime()
  return now > showtimeDate
}

const viewTicket = (order: Order) => {
  selectedOrder.value = order
  showQRCode.value = true
}

const goToPay = (order: Order) => {
  router.push(`/order/pay/${order.orderNo}`)
}

const viewOrder = (order: Order) => {
  router.push(`/order/${order.orderNo}`)
}

const onQuickAction = (action: { key: string; label: string }) => {
  if (action.key === 'orders') {
    router.push('/orders')
  } else if (action.key === 'favorites') {
    router.push('/favorites')
  } else if (action.key === 'coupons') {
    couponSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (action.key === 'history') {
    historySectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (action.key === 'settings') {
    openPasswordDialog()
  }
}

const openPasswordDialog = () => {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  showPasswordDialog.value = true
}

/** 校验旧密码与新密码输入，提交密码修改。 */
const handleChangePassword = async () => {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!newPassword || newPassword.length < 6 || newPassword.length > 20) {
    ElMessage.warning('新密码长度需为 6-20 位')
    return
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (oldPassword === newPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }

  changing.value = true
  try {
    await userApi.changePassword({ oldPassword, newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    showPasswordDialog.value = false
    setTimeout(() => userStore.logout(), 800)
  } catch (e) {
    console.error('修改密码失败:', e)
  } finally {
    changing.value = false
  }
}


onMounted(async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  loadingProfile.value = true
  try {
    const profile = await userApi.getCurrent()
    userStore.setUserInfo(profile)
  } catch (error) {
    console.error('加载用户钱包信息失败:', error)
  } finally {
    loadingProfile.value = false
  }
  initEditForm()
  loadOrders()
  loadCoupons()
  loadViewingHistory()
  if (route.query.edit === 'preferences') await openPreferenceEditor()
})
</script>

<style scoped>

.profile-page {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --fg: #111111;
  --muted: #666666;
  --border: #CBCCC9;
  --secondary: #E7E8E5;
  --radius-m: 16px;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;

  display: flex;
  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
}

.profile-body {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 24px;
  padding: 28px 32px;
}

.msr {
  font-family: 'Material Symbols Rounded';
  font-weight: normal;
  font-style: normal;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
  font-size: 20px;
  user-select: none;
}

/* 左侧边栏 */
.profile-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.profile-avatar {
  margin-bottom: 8px;
}

.avatar-circle {
  width: 80px;
  height: 80px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  box-shadow: 0 8px 24px rgba(255, 132, 0, 0.3);
}

.profile-name {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--fg);
}

.profile-phone {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--muted);
}

.profile-level {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 132, 0, 0.1);
  border: 1px solid rgba(255, 132, 0, 0.3);
  border-radius: 999px;
}

.level-badge {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
}

.level-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.level-icon {
  font-size: 16px;
}

.edit-profile-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
  width: 100%;
  justify-content: center;
}

.edit-profile-btn:hover {
  border-color: var(--primary);
  background: var(--card);
}

.edit-profile-btn .msr {
  font-size: 16px;
}

/* 会员权益卡片 */
.benefits-card {
  background: linear-gradient(135deg, #1F1340 0%, #2D1B4E 100%);
  border-radius: 20px;
  padding: 24px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(168, 85, 247, 0.2);
}

.benefits-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 20px;
}

.benefits-header .msr {
  font-size: 18px;
  color: #A855F7;
}

.benefits-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: rgba(255, 255, 255, 0.1);
}

/* 右侧主要内容 */
.profile-main {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 优惠券 */
.profile-sidebar .coupons-section { width: 100%; min-width: 0; flex-shrink: 0; }
.profile-sidebar .coupon-section-head { flex-direction: column; align-items: flex-start; gap: 14px; padding: 18px; }
.profile-sidebar .coupon-grid { grid-template-columns: 1fr; padding: 14px; }
.profile-sidebar .profile-coupon { grid-template-columns: 76px minmax(0, 1fr); }
.profile-sidebar .profile-coupon::before,
.profile-sidebar .profile-coupon::after { left: 68px; }
.profile-sidebar .coupon-name-row { flex-wrap: wrap; }
.profile-sidebar .coupon-use { grid-column: 2; justify-self: start; margin: 0 0 14px 16px; }

.coupons-section {
  scroll-margin-top: 24px;
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}
.coupon-section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  background: linear-gradient(110deg, rgba(255, 132, 0, 0.14), rgba(255, 132, 0, 0) 54%);
  border-bottom: 1px solid var(--border);
}
.coupon-eyebrow {
  margin-bottom: 5px;
  color: var(--primary);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.6px;
}
.coupon-section-head .section-title { font-size: 21px; }
.coupon-section-head p { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.coupon-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--secondary);
}
.coupon-tabs button {
  padding: 7px 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.coupon-tabs button.active { background: var(--fg); color: #fff; }
.coupon-tabs span { margin-left: 3px; font-family: var(--font-mono); }
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-height: 130px;
  padding: 20px 24px 24px;
}
.profile-coupon {
  position: relative;
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  align-items: center;
  min-height: 112px;
  overflow: hidden;
  border: 1px solid rgba(255, 132, 0, 0.38);
  border-radius: 14px;
  background: #fffaf4;
}
.profile-coupon::before,
.profile-coupon::after {
  content: '';
  position: absolute;
  left: 78px;
  width: 16px;
  height: 16px;
  border: 1px solid rgba(255, 132, 0, 0.38);
  border-radius: 999px;
  background: var(--card);
  transform: translateX(-50%);
}
.profile-coupon::before { top: -9px; }
.profile-coupon::after { bottom: -9px; }
.coupon-value {
  display: flex;
  align-items: baseline;
  justify-content: center;
  align-self: stretch;
  padding-top: 31px;
  border-right: 1px dashed rgba(255, 132, 0, 0.45);
  color: var(--primary);
}
.coupon-value strong { font-family: var(--font-mono); font-size: 34px; line-height: 1; }
.coupon-currency { font-family: var(--font-mono); font-size: 14px; font-weight: 800; }
.coupon-detail { min-width: 0; padding: 16px; }
.coupon-name-row { display: flex; align-items: center; gap: 7px; }
.coupon-detail h4 { margin: 0; overflow: hidden; color: var(--fg); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.coupon-detail p { margin: 8px 0 5px; color: var(--fg); font-size: 11px; font-weight: 600; }
.coupon-validity { color: var(--muted); font-family: var(--font-mono); font-size: 9px; }
.coupon-status { padding: 2px 6px; border-radius: 4px; font-size: 9px; font-weight: 700; white-space: nowrap; }
.coupon-status.status-0 { background: rgba(255, 132, 0, 0.14); color: #b85f00; }
.coupon-status.status-1,
.coupon-status.status-2 { background: var(--secondary); color: var(--muted); }
.coupon-use {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-right: 14px;
  padding: 7px 10px;
  border: 0;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}
.coupon-use .msr { font-size: 13px; }
.profile-coupon.unavailable { border-color: var(--border); background: var(--secondary); opacity: 0.72; }
.profile-coupon.unavailable::before,
.profile-coupon.unavailable::after { border-color: var(--border); }
.profile-coupon.unavailable .coupon-value { border-color: var(--border); color: var(--muted); }
.coupon-watermark { position: absolute; right: 10px; bottom: 8px; color: rgba(17, 17, 17, 0.12); font-family: var(--font-mono); font-size: 16px; font-weight: 800; transform: rotate(-8deg); }

/* 观影记录 */
.history-section {
  scroll-margin-top: 24px;
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(110deg, rgba(139, 92, 246, 0.11), transparent 50%);
}
.history-eyebrow {
  margin-bottom: 5px;
  color: #7C3AED;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.6px;
}
.history-head .section-title { font-size: 21px; }
.history-head p { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
.history-stats { display: flex; align-items: center; gap: 18px; }
.history-stats > div { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.history-stats strong { color: var(--fg); font-family: var(--font-mono); font-size: 20px; }
.history-stats span { color: var(--muted); font-size: 9px; }
.history-stats i { width: 1px; height: 32px; background: var(--border); }
.history-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 12px;
  min-height: 120px;
  padding: 20px 24px 24px;
}
.history-card {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  min-height: 128px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--bg);
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.history-card:hover {
  transform: translateY(-2px);
  border-color: #8B5CF6;
  box-shadow: 0 6px 18px rgba(17, 17, 17, 0.06);
}
.history-poster {
  position: relative;
  min-height: 128px;
  overflow: hidden;
  background: linear-gradient(145deg, #251943, #111111);
  display: flex;
  align-items: center;
  justify-content: center;
}
.history-poster > img { width: 100%; height: 100%; object-fit: cover; }
.history-poster > .msr { color: rgba(255, 255, 255, 0.6); font-size: 30px; }
.history-watched {
  position: absolute;
  left: 7px;
  bottom: 7px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 6px;
  border-radius: 999px;
  background: rgba(17, 17, 17, 0.82);
  color: #fff;
  font-size: 8px;
  font-weight: 700;
  backdrop-filter: blur(4px);
}
.history-watched .msr { font-size: 10px; }
.history-info { display: flex; flex-direction: column; min-width: 0; padding: 14px 16px; }
.history-date { color: #7C3AED; font-family: var(--font-mono); font-size: 9px; font-weight: 700; }
.history-info h4 { margin: 5px 0 9px; overflow: hidden; color: var(--fg); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.history-meta { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.history-meta > span {
  display: flex;
  align-items: center;
  gap: 5px;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-meta .msr { flex-shrink: 0; font-size: 12px; }
.history-footer { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 10px; }
.history-price { color: var(--fg); font-family: var(--font-mono); font-size: 10px; font-weight: 700; }
.history-footer button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 6px 9px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--fg);
  font-family: var(--font-sans);
  font-size: 9px;
  font-weight: 700;
  cursor: pointer;
}
.history-footer button:hover { border-color: #8B5CF6; color: #7C3AED; }
.history-footer button .msr { font-size: 11px; }

.section-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.section-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--fg);
}

.section-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}

.tab-btn.active {
  background: var(--card);
  color: var(--fg);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.tab-count {
  padding: 2px 6px;
  background: rgba(255, 132, 0, 0.15);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--primary);
}

.tab-btn.active .tab-count {
  background: var(--primary);
  color: var(--primary-foreground);
}

.section-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  cursor: pointer;
  transition: border-color 0.15s;
}

.action-btn:hover {
  border-color: var(--primary);
}

.action-btn .msr {
  font-size: 16px;
}

/* 电影票网格 */
.tickets-section {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 24px;
}

.tickets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.ticket-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 14px;
  transition: transform 0.15s, border-color 0.15s;
  cursor: pointer;
}

.ticket-card:hover {
  transform: translateY(-2px);
  border-color: var(--primary);
}

.ticket-poster {
  position: relative;
  width: 80px;
  height: 110px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%);
}

.ticket-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.3);
}

.poster-placeholder .msr {
  font-size: 40px;
}

.ticket-status {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  backdrop-filter: blur(4px);
}

.status-pending {
  background: rgba(251, 191, 36, 0.9);
  color: #78350f;
}

.status-paid {
  background: rgba(34, 197, 94, 0.9);
  color: #14532d;
}

.status-cancelled {
  background: rgba(156, 163, 175, 0.9);
  color: #1f2937;
}

.status-refunded {
  background: rgba(239, 68, 68, 0.9);
  color: #7f1d1d;
}

.ticket-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.ticket-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticket-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.meta-row .msr {
  font-size: 14px;
  color: var(--muted);
}

.ticket-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 8px;
}

.ticket-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.ticket-price .currency {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
}

.ticket-price .amount {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 800;
  color: var(--fg);
}

.ticket-action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 999px;
  background: var(--fg);
  color: var(--card);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}

.ticket-action-btn:hover {
  opacity: 0.88;
}

.ticket-action-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
}

.ticket-action-btn.picked {
  background: #10B981;
  color: #fff;
  cursor: not-allowed;
  opacity: 0.8;
}

.ticket-action-btn.watched {
  background: #6B7280;
  color: #fff;
  cursor: not-allowed;
  opacity: 0.8;
}

.ticket-action-btn .msr {
  font-size: 14px;
}

/* 快捷功能 */
.quick-actions-section {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 20px;
}

.quick-actions-section .section-title {
  font-size: 18px;
  margin-bottom: 14px;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.quick-action-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg);
  cursor: pointer;
  transition: all 0.15s;
}

.quick-action-card:hover {
  transform: translateY(-2px);
  border-color: var(--primary);
  background: var(--card);
}

.action-icon {
  font-size: 24px;
}

.action-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  text-align: center;
}

.action-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 6px;
  background: var(--primary);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: var(--primary-foreground);
}

@media (max-width: 1024px) {
  .profile-body {
    grid-template-columns: 1fr;
  }

  .profile-sidebar {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .profile-card,
  .benefits-card {
    flex: 1;
    min-width: 280px;
  }

  .quick-actions-section {
    flex: 1 1 100%;
  }

  .quick-actions-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }

  .coupon-grid { grid-template-columns: 1fr; }
  .history-list { grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); }
}

@media (max-width: 640px) {
  .profile-sidebar {
    flex-direction: column;
  }

  .tickets-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .coupon-section-head { align-items: flex-start; flex-direction: column; padding: 18px; }
  .coupon-grid { padding: 14px; }
  .profile-coupon { grid-template-columns: 76px minmax(0, 1fr); }
  .profile-coupon::before,
  .profile-coupon::after { left: 68px; }
  .coupon-use { grid-column: 2; justify-self: start; margin: 0 0 14px 16px; }
  .history-head { align-items: flex-start; flex-direction: column; padding: 18px; }
  .history-stats > div { align-items: flex-start; }
  .history-list { grid-template-columns: 1fr; padding: 14px; }
  .history-card { grid-template-columns: 78px minmax(0, 1fr); }
}

/* ============ 编辑资料对话框样式 ============ */
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--primary);
  font-size: 16px;
  font-weight: 700;
  color: var(--fg);
}

.form-section-title .msr {
  font-size: 20px;
  color: var(--primary);
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.label-text {
  color: var(--fg);
}

.label-required {
  color: #EF4444;
  font-weight: 700;
}

.input-tip {
  font-size: 12px;
  color: var(--muted);
}

/* Element Plus 组件样式覆盖 */
:deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--border) inset;
  transition: box-shadow 0.15s;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--primary) inset;
}

:deep(.el-textarea__inner) {
  border-radius: 10px;
  border-color: var(--border);
  font-family: var(--font-sans);
}

:deep(.el-textarea__inner:hover) {
  border-color: var(--primary);
}

:deep(.el-textarea__inner:focus) {
  border-color: var(--primary);
}

:deep(.el-select .el-input__wrapper) {
  border-radius: 10px;
}

:deep(.el-date-editor) {
  width: 100%;
}

:deep(.el-date-editor .el-input__wrapper) {
  border-radius: 10px;
}

.gender-group {
  display: flex;
  gap: 16px;
}

:deep(.gender-group .el-radio) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  margin-right: 0;
  transition: all 0.15s;
}

:deep(.gender-group .el-radio:hover) {
  border-color: var(--primary);
  background: var(--card);
}

:deep(.gender-group .el-radio.is-checked) {
  border-color: var(--primary);
  background: rgba(255, 132, 0, 0.1);
}

:deep(.gender-group .el-radio__input) {
  display: none;
}

:deep(.gender-group .el-radio__label) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--fg);
}

:deep(.gender-group .el-radio__label .msr) {
  font-size: 18px;
  color: var(--muted);
}

:deep(.gender-group .el-radio.is-checked .el-radio__label .msr) {
  color: var(--primary);
}

.genre-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.genre-tag {
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}

.genre-tag:hover {
  border-color: var(--primary);
  background: var(--card);
}

.genre-tag.active {
  border-color: var(--primary);
  background: var(--primary);
  color: var(--primary-foreground);
  font-weight: 600;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.dialog-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border: none;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.dialog-btn.cancel {
  background: var(--secondary);
  color: var(--fg);
  border: 1px solid var(--border);
}

.dialog-btn.cancel:hover {
  background: var(--border);
}

.dialog-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
}

.dialog-btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35);
}

.dialog-btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.dialog-btn .msr {
  font-size: 16px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
