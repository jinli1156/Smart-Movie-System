<template>
  <div class="ticket-qrcode-dialog">
    <el-dialog
      v-model="visible"
      :title="title"
      width="480px"
      :close-on-click-modal="false"
      @close="handleClose"
    >
      <div class="qrcode-container">
        <div class="ticket-header">
          <div class="ticket-icon">
            <MaterialIcon class="msr" name="confirmation_number" />
          </div>
          <div class="ticket-info">
            <h3 class="movie-title">{{ order?.movieTitle }}</h3>
            <p class="cinema-name">{{ order?.cinemaName }}</p>
          </div>
        </div>

        <div class="qrcode-wrapper">
          <div class="qrcode-box">
            <canvas ref="qrcodeCanvas"></canvas>
          </div>
          <div class="qrcode-tip">
            <MaterialIcon class="msr" name="info" />
            <span>请在影院自助取票机扫描此二维码取票</span>
          </div>
        </div>

        <div class="ticket-details">
          <div class="detail-row">
            <span class="detail-label">取票码</span>
            <span class="detail-value code">{{ ticketCode }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">订单号</span>
            <span class="detail-value">{{ order?.orderNo }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">影厅</span>
            <span class="detail-value">{{ order?.hallName }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">座位</span>
            <span class="detail-value seats">{{ order?.seats }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">场次时间</span>
            <span class="detail-value">{{ formatShowtime(order?.showtime) }}</span>
          </div>
        </div>

        <div class="qrcode-actions">
          <button class="action-btn secondary" @click="handleDownload">
            <MaterialIcon class="msr" name="download" />
            <span>保存二维码</span>
          </button>
          <button class="action-btn secondary" @click="handleCopyData">
            <MaterialIcon class="msr" name="content_copy" />
            <span>复制数据</span>
          </button>
        </div>

        <div class="qrcode-notice">
          <div class="notice-title">
            <MaterialIcon class="msr" name="tips_and_updates" />
            <span>温馨提示</span>
          </div>
          <ul class="notice-list">
            <li>请提前15分钟到达影院取票</li>
            <li>取票码有效期至场次开始前30分钟</li>
            <li>如遇问题请联系影院工作人员</li>
            <li>请妥善保管取票码，避免截图外泄</li>
          </ul>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import type { Order } from '@/types'

interface Props {
  modelValue: boolean
  order: Order | null
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '电影票取票码'
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(props.modelValue)
const qrcodeCanvas = ref<HTMLCanvasElement | null>(null)
const qrDataString = ref('')

// 从订单中获取取票码
const ticketCode = ref('')

const generateQRCode = async () => {
  if (!props.order || !qrcodeCanvas.value) return

  try {
    // 从订单中获取取票码
    if (!props.order.ticketCode) {
      ElMessage.error('该订单暂无取票码，请联系客服')
      return
    }

    ticketCode.value = props.order.ticketCode

    // 二维码内容：包含订单信息的JSON
    const qrData = JSON.stringify({
      type: 'MOVIE_TICKET',
      orderNo: props.order.orderNo,
      ticketCode: ticketCode.value,
      movieTitle: props.order.movieTitle,
      cinemaName: props.order.cinemaName,
      hallName: props.order.hallName,
      seats: props.order.seats,
      showtime: props.order.showtime,
      timestamp: Date.now()
    })

    // 保存二维码数据用于调试
    qrDataString.value = qrData
    console.log('生成的二维码数据:', qrData)

    // 生成二维码
    await QRCode.toCanvas(qrcodeCanvas.value, qrData, {
      width: 240,
      margin: 2,
      color: {
        dark: '#111111',
        light: '#FFFFFF'
      },
      errorCorrectionLevel: 'H'
    })
  } catch (error) {
    console.error('生成二维码失败:', error)
    ElMessage.error('生成二维码失败')
  }
}

const formatShowtime = (showtime?: string) => {
  if (!showtime) return '--'
  const date = new Date(showtime)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const handleDownload = () => {
  if (!qrcodeCanvas.value) return

  try {
    const url = qrcodeCanvas.value.toDataURL('image/png')
    const link = document.createElement('a')
    link.download = `取票码_${props.order?.orderNo}.png`
    link.href = url
    link.click()
    ElMessage.success('二维码已保存')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}

const handleCopyData = () => {
  if (!qrDataString.value) return

  try {
    navigator.clipboard.writeText(qrDataString.value)
    ElMessage.success('二维码数据已复制到剪贴板')
    console.log('已复制的数据:', qrDataString.value)
  } catch (error) {
    console.error('复制失败:', error)
    ElMessage.error('复制失败')
  }
}

const handleClose = () => {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.order) {
    nextTick(() => {
      generateQRCode()
    })
  }
})

watch(visible, (val) => {
  if (!val) {
    emit('update:modelValue', false)
  }
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0&display=swap');

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

:deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #FFF8F0 0%, #FFF 100%);
}

:deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: #111;
}

:deep(.el-dialog__body) {
  padding: 0;
}

.qrcode-container {
  display: flex;
  flex-direction: column;
}

.ticket-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: linear-gradient(135deg, #FFF8F0 0%, #FFF 100%);
  border-bottom: 2px dashed #FFE4CC;
}

.ticket-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: linear-gradient(135deg, #FF8400 0%, #FF6B00 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.3);
}

.ticket-icon .msr {
  font-size: 32px;
}

.ticket-info {
  flex: 1;
  min-width: 0;
}

.movie-title {
  font-size: 18px;
  font-weight: 700;
  color: #111;
  margin: 0 0 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cinema-name {
  font-size: 14px;
  color: #666;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qrcode-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 24px;
  background: #fff;
}

.qrcode-box {
  padding: 20px;
  background: #fff;
  border: 2px solid #FF8400;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(255, 132, 0, 0.15);
  margin-bottom: 16px;
}

.qrcode-box canvas {
  display: block;
  border-radius: 8px;
}

.qrcode-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #FFF8F0;
  border: 1px solid #FFE4CC;
  border-radius: 8px;
  font-size: 13px;
  color: #FF8400;
  font-weight: 500;
}

.qrcode-tip .msr {
  font-size: 18px;
}

.ticket-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
  background: #F9FAFB;
  border-top: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.detail-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.detail-value {
  font-size: 14px;
  color: #111;
  font-weight: 600;
  text-align: right;
}

.detail-value.code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 20px;
  font-weight: 800;
  color: #FF8400;
  letter-spacing: 2px;
}

.detail-value.seats {
  font-family: 'JetBrains Mono', monospace;
  color: #FF8400;
}

.qrcode-actions {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  background: #fff;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.secondary {
  background: #F3F4F6;
  color: #111;
  border: 1px solid #E5E7EB;
}

.action-btn.secondary:hover {
  background: #E5E7EB;
  transform: translateY(-1px);
}

.action-btn .msr {
  font-size: 18px;
}

.qrcode-notice {
  padding: 20px 24px 24px;
  background: #FFF8F0;
}

.notice-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #FF8400;
  margin-bottom: 12px;
}

.notice-title .msr {
  font-size: 18px;
}

.notice-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.notice-list li {
  position: relative;
  font-size: 13px;
  color: #666;
  line-height: 1.8;
  padding-left: 12px;
}

.notice-list li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #FF8400;
  font-weight: bold;
}
</style>
