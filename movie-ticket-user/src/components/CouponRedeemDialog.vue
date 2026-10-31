<template>
  <el-dialog
    v-model="visible"
    width="440px"
    title="兑换优惠券"
    append-to-body
    destroy-on-close
    @closed="reset"
  >
    <div class="redeem-content">
      <div class="redeem-icon">
        <MaterialIcon class="msr" name="redeem" />
      </div>
      <div class="redeem-copy">
        <strong>输入兑换码领取优惠券</strong>
        <span>字母不区分大小写，每个兑换码每个账号限领一次</span>
      </div>
      <el-input
        v-model="code"
        class="redeem-input"
        maxlength="32"
        placeholder="例如：JINLI699"
        clearable
        @keyup.enter="submit"
      />
      <p class="redeem-hint">演示兑换码：JINLI699</p>
    </div>
    <template #footer>
      <button class="dialog-btn ghost" :disabled="submitting" @click="visible = false">取消</button>
      <button class="dialog-btn primary" :disabled="submitting || !code.trim()" @click="submit">
        {{ submitting ? '兑换中…' : '立即兑换' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 优惠券兑换弹窗：校验兑换码输入，提交领取并同步成功状态。
 */
import { computed, ref } from 'vue'
import { couponApi } from '@/api/coupon'
import type { Coupon } from '@/types'
import { ElMessage } from 'element-plus/es/components/message/index'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'redeemed', coupon: Coupon): void
}>()

const code = ref('')
const submitting = ref(false)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const reset = () => {
  code.value = ''
  submitting.value = false
}

/** 校验兑换码并申请领取优惠券，成功后通知父页面刷新。 */
const submit = async () => {
  const normalized = code.value.trim().toUpperCase()
  if (!normalized || submitting.value) return

  submitting.value = true
  try {
    const coupon = await couponApi.redeem(normalized)
    emit('redeemed', coupon)
    ElMessage.success(`兑换成功：${coupon.name}`)
    visible.value = false
  } catch (error) {
    console.error('兑换优惠券失败:', error)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.redeem-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 4px 2px;
}

.redeem-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  color: #111;
  background: #ff8400;
  box-shadow: 0 10px 24px rgba(255, 132, 0, 0.24);
}

.redeem-icon .msr { font-size: 30px; }
.redeem-copy { display: flex; flex-direction: column; gap: 6px; margin: 16px 0 20px; text-align: center; }
.redeem-copy strong { font-size: 18px; color: #111; }
.redeem-copy span { font-size: 13px; color: #777; }
.redeem-input { width: 100%; }
.redeem-hint { align-self: flex-start; margin: 8px 0 0; font-size: 12px; color: #999; }
.dialog-btn { min-width: 92px; height: 38px; border-radius: 10px; font-weight: 700; cursor: pointer; }
.dialog-btn:disabled { cursor: not-allowed; opacity: 0.55; }
.dialog-btn.ghost { border: 1px solid #d8d8d8; color: #555; background: #fff; }
.dialog-btn.primary { border: 1px solid #ff8400; color: #111; background: #ff8400; }
</style>
