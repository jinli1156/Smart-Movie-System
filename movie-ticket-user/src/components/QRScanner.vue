<template>
  <div class="qr-scanner-dialog">
    <el-dialog
      v-model="visible"
      title="扫码取票"
      width="600px"
      :close-on-click-modal="false"
      @close="handleClose"
    >
      <div class="scanner-container">
        <div class="scanner-header">
          <div class="header-icon">
            <MaterialIcon class="msr" name="qr_code_scanner" />
          </div>
          <div class="header-info">
            <h3 class="header-title">请将取票码对准扫描框</h3>
            <p class="header-desc">支持扫描二维码或手动输入取票码</p>
          </div>
        </div>

        <!-- 扫描器区域 -->
        <div class="scanner-wrapper">
          <div v-if="!isScanning && !scanResult && !scanError && !isLoading" class="scanner-placeholder">
            <MaterialIcon class="msr" name="videocam" />
            <p>点击下方按钮开始扫描</p>
          </div>

          <!-- 加载状态 -->
          <div v-if="isLoading" class="scanner-loading">
            <MaterialIcon class="msr loading-icon" name="progress_activity" />
            <p>正在启动摄像头...</p>
          </div>

          <div v-show="isScanning" id="qr-reader" class="qr-reader"></div>

          <!-- 扫描指示器（独立显示，不覆盖视频） -->
          <div v-if="isScanning" class="scanning-indicator">
            <div class="scanning-line"></div>
            <p class="scanning-text">正在扫描中...</p>
          </div>

          <!-- 扫描成功结果 -->
          <div v-if="scanResult" class="scan-result">
            <div class="result-icon success">
              <MaterialIcon class="msr" name="check_circle" />
            </div>
            <h3 class="result-title">扫描成功！</h3>
            <div class="result-info">
              <div class="info-row">
                <span class="info-label">电影</span>
                <span class="info-value">{{ ticketInfo?.movieTitle }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">影院</span>
                <span class="info-value">{{ ticketInfo?.cinemaName }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">影厅</span>
                <span class="info-value">{{ ticketInfo?.hallName }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">座位</span>
                <span class="info-value">{{ ticketInfo?.seats }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">场次</span>
                <span class="info-value">{{ formatShowtime(ticketInfo?.showtime) }}</span>
              </div>
            </div>
            <div class="result-actions">
              <button class="result-btn primary" @click="handleConfirmTicket">
                <MaterialIcon class="msr" name="confirmation_number" />
                <span>确认取票</span>
              </button>
              <button class="result-btn secondary" @click="handleRescan">
                <MaterialIcon class="msr" name="refresh" />
                <span>重新扫描</span>
              </button>
            </div>
          </div>

          <!-- 扫描失败 -->
          <div v-if="scanError" class="scan-result">
            <div class="result-icon error">
              <MaterialIcon class="msr" name="error" />
            </div>
            <h3 class="result-title error">扫描失败</h3>
            <p class="result-message">{{ errorMessage }}</p>
            <button class="result-btn secondary" @click="handleRescan">
              <MaterialIcon class="msr" name="refresh" />
              <span>重新扫描</span>
            </button>
          </div>
        </div>

        <!-- 控制按钮 -->
        <div v-if="!scanResult && !scanError" class="scanner-controls">
          <button
            v-if="!isScanning"
            class="control-btn primary"
            @click="startScanning"
          >
            <MaterialIcon class="msr" name="play_arrow" />
            <span>开始扫描</span>
          </button>
          <button
            v-else
            class="control-btn secondary"
            @click="stopScanning"
          >
            <MaterialIcon class="msr" name="stop" />
            <span>停止扫描</span>
          </button>
          <button class="control-btn secondary" @click="showManualInput = true">
            <MaterialIcon class="msr" name="keyboard" />
            <span>手动输入</span>
          </button>
        </div>

        <!-- 使用提示 -->
        <div class="scanner-tips">
          <div class="tip-title">
            <MaterialIcon class="msr" name="lightbulb" />
            <span>使用提示</span>
          </div>
          <ul class="tip-list">
            <li>请确保光线充足，二维码清晰可见</li>
            <li>将二维码完整置于扫描框内，保持稳定</li>
            <li>二维码与摄像头距离保持在10-30厘米</li>
            <li>如果扫描困难，可尝试调整角度或距离</li>
            <li><strong>屏幕扫描较困难，建议使用手动输入或打印二维码</strong></li>
            <li>如无法扫描，可选择手动输入取票码</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <!-- 手动输入对话框 -->
    <el-dialog
      v-model="showManualInput"
      title="手动输入取票码"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="manual-input-form">
        <el-input
          v-model="manualCode"
          placeholder="请输入8位取票码"
          maxlength="8"
          size="large"
          clearable
        >
          <template #prefix>
            <MaterialIcon class="msr" name="confirmation_number" />
          </template>
        </el-input>
        <p class="input-tip">取票码为8位数字，可在订单详情中查看</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showManualInput = false">取消</el-button>
          <el-button type="primary" @click="handleManualSubmit" :disabled="manualCode.length !== 8">
            确认
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { Html5Qrcode } from 'html5-qrcode'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api/order'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'success': [ticketInfo: any]
}>()

const visible = ref(props.modelValue)
const isScanning = ref(false)
const isLoading = ref(false)
const scanResult = ref(false)
const scanError = ref(false)
const errorMessage = ref('')
const ticketInfo = ref<any>(null)
const showManualInput = ref(false)
const manualCode = ref('')

let html5QrCode: Html5Qrcode | null = null

const startScanning = async () => {
  try {
    isLoading.value = true
    html5QrCode = new Html5Qrcode('qr-reader')

    const config = {
      fps: 10,
      qrbox: function(viewfinderWidth: number, viewfinderHeight: number) {
        const minEdgePercentage = 0.8
        const minEdgeSize = Math.min(viewfinderWidth, viewfinderHeight)
        const qrboxSize = Math.floor(minEdgeSize * minEdgePercentage)
        return {
          width: qrboxSize,
          height: qrboxSize
        }
      },
      aspectRatio: 1.0,
      disableFlip: false,
      // 提高扫描灵敏度
      experimentalFeatures: {
        useBarCodeDetectorIfSupported: true
      }
    }

    // 尝试使用后置摄像头，如果失败则使用任意可用摄像头
    try {
      await html5QrCode.start(
        { facingMode: 'environment' },
        config,
        onScanSuccess,
        onScanFailure
      )
      console.log('使用后置摄像头')
    } catch (err) {
      console.log('后置摄像头不可用，尝试使用前置摄像头')
      try {
        await html5QrCode.start(
          { facingMode: 'user' },
          config,
          onScanSuccess,
          onScanFailure
        )
        console.log('使用前置摄像头')
      } catch (err2) {
        console.log('尝试使用任意可用摄像头')
        await html5QrCode.start(
          { facingMode: { ideal: 'environment' } },
          config,
          onScanSuccess,
          onScanFailure
        )
      }
    }

    isScanning.value = true
    isLoading.value = false
    scanError.value = false
    errorMessage.value = ''
    ElMessage.success('摄像头已启动，请对准二维码')
  } catch (err: any) {
    console.error('启动扫描失败:', err)
    isLoading.value = false
    ElMessage.error('无法启动摄像头，请检查权限设置')
  }
}

const stopScanning = async () => {
  if (html5QrCode) {
    try {
      if (isScanning.value) {
        await html5QrCode.stop()
      }
      // 清理 HTML5Qrcode 实例
      html5QrCode.clear()

      // 手动停止所有媒体流
      const videoElement = document.querySelector('#qr-reader video') as HTMLVideoElement
      if (videoElement && videoElement.srcObject) {
        const stream = videoElement.srcObject as MediaStream
        stream.getTracks().forEach(track => {
          track.stop()
          console.log('停止媒体轨道:', track.kind)
        })
        videoElement.srcObject = null
      }

      isScanning.value = false
      console.log('扫描已停止，摄像头已关闭')
    } catch (err) {
      console.error('停止扫描失败:', err)
      // 即使出错也尝试停止媒体流
      try {
        const videoElement = document.querySelector('#qr-reader video') as HTMLVideoElement
        if (videoElement && videoElement.srcObject) {
          const stream = videoElement.srcObject as MediaStream
          stream.getTracks().forEach(track => track.stop())
          videoElement.srcObject = null
        }
      } catch (e) {
        console.error('强制停止媒体流失败:', e)
      }
      isScanning.value = false
    }
  }
}

const onScanSuccess = async (decodedText: string) => {
  console.log('========== 扫描成功 ==========')
  console.log('原始数据:', decodedText)
  console.log('数据长度:', decodedText.length)
  console.log('============================')

  ElMessage.success('扫描成功！正在验证...')

  // 停止扫描
  await stopScanning()

  try {
    // 解析二维码内容
    const data = JSON.parse(decodedText)
    console.log('解析后的数据:', data)

    if (data.type === 'MOVIE_TICKET') {
      // 验证取票码
      await verifyTicket(data.orderNo, data.ticketCode)
    } else {
      throw new Error('无效的取票码类型: ' + data.type)
    }
  } catch (err: any) {
    console.error('解析二维码失败:', err)
    scanError.value = true
    errorMessage.value = err.message || '无效的二维码格式，请确保扫描的是电影票取票码'
  }
}

const onScanFailure = (error: string) => {
  // 扫描失败是正常的，只在控制台输出调试信息
  if (error && !error.includes('NotFoundException')) {
    console.log('扫描中...', error)
  }
}

const verifyTicket = async (orderNo: string, ticketCode: string) => {
  try {
    // 先验证订单状态，但不立即取票
    const order = await orderApi.getByOrderNo(orderNo)

    if (order.status !== 1) {
      throw new Error('该订单未支付或已取消')
    }

    if (order.isPicked === 1) {
      throw new Error('该订单已取票')
    }

    // 验证成功，显示票务信息，等待用户确认
    ticketInfo.value = {
      orderNo: order.orderNo,
      ticketCode: ticketCode,
      movieTitle: order.movieTitle,
      cinemaName: order.cinemaName,
      hallName: order.hallName,
      seats: order.seats,
      showtime: order.showtime
    }

    scanResult.value = true
    scanError.value = false
  } catch (err: any) {
    console.error('验证失败:', err)
    scanError.value = true
    errorMessage.value = err.message || '验证失败'
  }
}

const handleManualSubmit = async () => {
  if (manualCode.value.length !== 8) {
    ElMessage.warning('请输入8位取票码')
    return
  }

  showManualInput.value = false

  try {
    ElMessage.info('正在验证取票码...')

    // 先通过取票码查询订单信息，但不立即取票
    // 需要先获取所有已支付订单，找到匹配的取票码
    const response = await orderApi.getMyOrders({ page: 1, size: 100, status: 1 })
    const matchedOrder = response.records.find(order => order.ticketCode === manualCode.value)

    if (!matchedOrder) {
      throw new Error('取票码无效或订单不存在')
    }

    if (matchedOrder.isPicked === 1) {
      throw new Error('该订单已取票')
    }

    // 显示取票信息，等待用户确认
    ticketInfo.value = {
      orderNo: matchedOrder.orderNo,
      ticketCode: manualCode.value,
      movieTitle: matchedOrder.movieTitle,
      cinemaName: matchedOrder.cinemaName,
      hallName: matchedOrder.hallName,
      seats: matchedOrder.seats,
      showtime: matchedOrder.showtime
    }

    scanResult.value = true
    scanError.value = false
    manualCode.value = ''

  } catch (err: any) {
    console.error('验证失败:', err)
    scanError.value = true
    errorMessage.value = err.message || '取票码验证失败'
  }
}

const handleConfirmTicket = async () => {
  if (!ticketInfo.value) return

  try {
    // 调用后端API进行取票
    await orderApi.pickTicket(ticketInfo.value.ticketCode)

    ElMessage.success('取票成功！请前往影厅观影')
    emit('success', ticketInfo.value)
    handleClose()
  } catch (err: any) {
    console.error('取票失败:', err)
    ElMessage.error(err.message || '取票失败，请重试')
  }
}

const handleRescan = () => {
  scanResult.value = false
  scanError.value = false
  errorMessage.value = ''
  ticketInfo.value = null
  startScanning()
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

const handleClose = async () => {
  await stopScanning()
  scanResult.value = false
  scanError.value = false
  errorMessage.value = ''
  ticketInfo.value = null
  emit('update:modelValue', false)
}

// 监听 modelValue 变化
watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  if (!val) {
    emit('update:modelValue', false)
  }
})

onBeforeUnmount(async () => {
  await stopScanning()
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

.scanner-container {
  display: flex;
  flex-direction: column;
}

.scanner-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: linear-gradient(135deg, #FFF8F0 0%, #FFF 100%);
  border-bottom: 2px dashed #FFE4CC;
}

.header-icon {
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

.header-icon .msr {
  font-size: 32px;
}

.header-info {
  flex: 1;
}

.header-title {
  font-size: 18px;
  font-weight: 700;
  color: #111;
  margin: 0 0 4px 0;
}

.header-desc {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.scanner-wrapper {
  position: relative;
  min-height: 350px;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scanner-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: #666;
}

.scanner-placeholder .msr {
  font-size: 64px;
  color: #999;
}

.scanner-placeholder p {
  font-size: 14px;
  margin: 0;
}

.scanner-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 20px;
  color: #FF8400;
}

.scanner-loading .loading-icon {
  font-size: 64px;
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.scanner-loading p {
  font-size: 14px;
  margin: 0;
  font-weight: 600;
}

.qr-reader {
  width: 100%;
  position: relative;
  min-height: 350px;
}

.scanning-indicator {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  pointer-events: none;
}

.scanning-line {
  width: 200px;
  height: 3px;
  background: linear-gradient(90deg, transparent, #FF8400, transparent);
  border-radius: 2px;
  animation: scanning 2s ease-in-out infinite;
}

@keyframes scanning {
  0%, 100% {
    opacity: 0.3;
    transform: scaleX(0.5);
  }
  50% {
    opacity: 1;
    transform: scaleX(1);
  }
}

.scanning-text {
  margin: 0;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 20px;
  backdrop-filter: blur(10px);
}

:deep(#qr-reader) {
  width: 100%;
  min-height: 350px;
}

:deep(#qr-reader video) {
  border-radius: 0;
  width: 100% !important;
  height: auto !important;
  display: block;
}

:deep(#qr-reader__dashboard) {
  display: none;
}

:deep(#qr-reader__scan_region) {
  min-height: 350px;
}

.scan-result {
  position: absolute;
  inset: 0;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  gap: 20px;
}

.result-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-icon.success {
  background: linear-gradient(135deg, #22C55E 0%, #16A34A 100%);
  color: #fff;
  box-shadow: 0 8px 24px rgba(34, 197, 94, 0.3);
}

.result-icon.error {
  background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
  color: #fff;
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.3);
}

.result-icon .msr {
  font-size: 48px;
}

.result-title {
  font-size: 24px;
  font-weight: 700;
  color: #111;
  margin: 0;
}

.result-title.error {
  color: #DC2626;
}

.result-message {
  font-size: 14px;
  color: #666;
  margin: 0;
  text-align: center;
}

.result-info {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: #F9FAFB;
  border-radius: 12px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.info-value {
  font-size: 14px;
  color: #111;
  font-weight: 600;
  text-align: right;
}

.result-actions {
  display: flex;
  gap: 12px;
}

.result-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.result-btn.primary {
  background: #FF8400;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.3);
}

.result-btn.primary:hover {
  background: #FF6B00;
  transform: translateY(-1px);
}

.result-btn.secondary {
  background: #F3F4F6;
  color: #111;
  border: 1px solid #E5E7EB;
}

.result-btn.secondary:hover {
  background: #E5E7EB;
}

.result-btn .msr {
  font-size: 18px;
}

.scanner-controls {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.control-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.control-btn.primary {
  background: #FF8400;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.3);
}

.control-btn.primary:hover {
  background: #FF6B00;
  transform: translateY(-1px);
}

.control-btn.secondary {
  background: #F3F4F6;
  color: #111;
  border: 1px solid #E5E7EB;
}

.control-btn.secondary:hover {
  background: #E5E7EB;
}

.control-btn .msr {
  font-size: 20px;
}

.scanner-tips {
  padding: 20px 24px;
  background: #FFF8F0;
}

.tip-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #FF8400;
  margin-bottom: 12px;
}

.tip-title .msr {
  font-size: 18px;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.tip-list li {
  position: relative;
  font-size: 13px;
  color: #666;
  line-height: 1.8;
  padding-left: 12px;
}

.tip-list li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #FF8400;
  font-weight: bold;
}

.manual-input-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-tip {
  font-size: 13px;
  color: #666;
  margin: 0;
}

:deep(.el-input__prefix) {
  display: flex;
  align-items: center;
}

:deep(.el-input__prefix .msr) {
  font-size: 20px;
  color: #FF8400;
}
</style>
