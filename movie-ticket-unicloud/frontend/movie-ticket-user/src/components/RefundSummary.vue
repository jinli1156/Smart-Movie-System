<template>
  <div class="refund-summary">
    <p class="refund-intro">确认退票后，座位将立即释放。</p>
    <div class="refund-amount-card">
      <div class="refund-amount-heading">
        <span>预计退款金额</span>
        <span class="refund-policy">{{ feeRate > 0 ? `含 ${feeRate}% 手续费` : '免手续费' }}</span>
      </div>
      <div class="refund-amount"><span>¥</span>{{ refundAmount.toFixed(2) }}</div>
      <div class="refund-breakdown">
        <div><span>订单金额</span><strong>¥{{ totalPrice.toFixed(2) }}</strong></div>
        <div><span>退票手续费{{ feeRate > 0 ? `（${feeRate}%）` : '' }}</span><strong>{{ fee > 0 ? '-' : '' }}¥{{ fee.toFixed(2) }}</strong></div>
      </div>
    </div>
    <div class="refund-arrival">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
      <div><strong>原路退回支付账户</strong><p>预计 1–7 个工作日到账</p></div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ totalPrice: number; refundAmount: number; fee: number; feeRate: number }>()
</script>

<style scoped>
.refund-intro { margin: 0 0 22px; color: #74766f; font-size: 14px; line-height: 1.7; }
.refund-amount-card { padding: 22px; border: 1px solid #e6e7e1; border-radius: 16px; background: #f6f7f3; }
.refund-amount-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #62665c; font-size: 13px; }
.refund-policy { padding: 4px 8px; border-radius: 6px; background: #e9ede2; color: #536345; font-size: 11px; white-space: nowrap; }
.refund-amount { display: flex; align-items: baseline; gap: 5px; margin: 10px 0 20px; color: #22271f; font-size: 42px; font-weight: 700; line-height: 1.2; letter-spacing: -1.5px; font-variant-numeric: tabular-nums; }
.refund-amount > span { font-size: 23px; font-weight: 500; letter-spacing: 0; }
.refund-breakdown { display: grid; gap: 12px; padding-top: 17px; border-top: 1px solid #dfe2d8; font-size: 13px; }
.refund-breakdown > div { display: flex; justify-content: space-between; gap: 12px; color: #74766f; }
.refund-breakdown strong { color: #363b31; font-weight: 500; font-variant-numeric: tabular-nums; white-space: nowrap; }
.refund-arrival { display: flex; gap: 11px; align-items: flex-start; padding: 20px 2px 4px; }
.refund-arrival svg { width: 19px; height: 19px; flex-shrink: 0; color: #808776; margin-top: 1px; }
.refund-arrival strong { color: #4f5648; font-size: 13px; font-weight: 500; }
.refund-arrival p { margin: 5px 0 0; color: #81857a; font-size: 12px; }
</style>

<style>
/* Message boxes are teleported to body, outside either order page. */
.el-message-box.refund-confirm-dialog { width: 440px; max-width: calc(100vw - 32px); padding: 28px; border: 1px solid #e6e7e1; border-radius: 22px; box-shadow: 0 24px 80px #18201026; }
.refund-confirm-dialog .el-message-box__header { padding: 0 30px 10px 0; }
.refund-confirm-dialog .el-message-box__title { color: #252a20; font-size: 21px; font-weight: 650; line-height: 1.4; }
.refund-confirm-dialog .el-message-box__headerbtn { top: 25px; right: 23px; width: 28px; height: 28px; border-radius: 50%; }
.refund-confirm-dialog .el-message-box__headerbtn:hover { background: #f2f3ef; }
.refund-confirm-dialog .el-message-box__content { padding: 0; }
.refund-confirm-dialog .el-message-box__message { width: 100%; }
.refund-confirm-dialog .el-message-box__btns { display: flex; gap: 12px; padding: 22px 0 0; }
.refund-confirm-dialog .el-message-box__btns .el-button { flex: 1; height: 44px; margin: 0; border-radius: 10px; font-size: 14px; font-weight: 600; --el-button-bg-color: #fff; --el-button-border-color: #dedfd8; --el-button-text-color: #52584b; --el-button-hover-bg-color: #f4f5f0; --el-button-hover-border-color: #bfc4b5; --el-button-hover-text-color: #252a20; }
.refund-confirm-dialog .el-message-box__btns .el-button--primary { --el-button-bg-color: #ff8400; --el-button-border-color: #ff8400; --el-button-text-color: #20170b; --el-button-hover-bg-color: #ed7a00; --el-button-hover-border-color: #ed7a00; --el-button-hover-text-color: #20170b; --el-button-active-bg-color: #db7000; --el-button-active-border-color: #db7000; }
.refund-confirm-dialog .el-button:focus-visible, .refund-confirm-dialog .el-message-box__headerbtn:focus-visible { outline: 2px solid #b65b00; outline-offset: 3px; }
@media (max-width: 480px) { .el-message-box.refund-confirm-dialog { padding: 22px; border-radius: 18px; } .refund-confirm-dialog .el-message-box__headerbtn { top: 19px; right: 17px; } }
</style>
