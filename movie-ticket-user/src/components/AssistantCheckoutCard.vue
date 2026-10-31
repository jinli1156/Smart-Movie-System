<template>
  <section class="checkout-card" aria-label="订单确认卡">
    <header><strong>确认这场电影</strong><span>尚未下单</span></header>
    <div class="checkout-film">
      <img v-poster :src="draft.moviePoster || '/poster-placeholder.svg'" :alt="draft.movieTitle + '海报'" />
      <div>
        <h3>{{ draft.movieTitle }}</h3>
        <p>{{ draft.cinemaName }} · {{ draft.hallName }}</p>
        <p>{{ draft.startTime.replace('T', ' ').slice(0, 16) }} 至 {{ draft.endTime.slice(11, 16) }}</p>
        <p class="checkout-seats">{{ draft.people }} 人 · {{ draft.seats.join(' · ') }}</p>
      </div>
    </div>
    <p class="checkout-total">预计实付 ¥{{ money(draft.bestPlan.totalPrice) }}</p>
    <p>原价 ¥{{ money(draft.originalPrice) }} · 券抵 ¥{{ money(draft.bestPlan.couponDiscount) }} · {{ draft.bestPlan.pointsUsed }} 积分抵 ¥{{ money(draft.bestPlan.pointsDiscount) }}</p>
    <p class="checkout-notice">{{ draft.notice }}</p>
    <p v-if="error" role="alert" class="checkout-error">{{ error }}</p>
    <div class="checkout-actions">
      <button type="button" :disabled="disabled || busy" @click="$emit('confirm', draft)">{{ busy ? '正在创建订单…' : '确认下单并去支付' }}</button>
      <button v-if="error" type="button" :disabled="disabled || busy" @click="$emit('refresh', draft)">重新准备订单</button>
      <router-link :to="`/showtime/${draft.showtimeId}/seats?people=${draft.people}&assistantSeats=${encodeURIComponent(draft.seats.join(','))}&pricingPoints=${draft.usePoints}&pricingCoupons=${draft.useCoupons}`">自己调整座位</router-link>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * 订单确认卡：展示场次、座位和优惠试算，向父页面发出确认操作。
 */
import type { CheckoutDraft } from '@/api/assistant'
defineProps<{ draft: CheckoutDraft; busy: boolean; disabled: boolean; error?: string }>()
defineEmits<{ confirm: [draft: CheckoutDraft]; refresh: [draft: CheckoutDraft] }>()
const money = (value: number) => Number(value).toFixed(2)
</script>

<style scoped>
.checkout-card{border:1px solid #e4bc7b;border-radius:14px;background:#fffaf1;padding:18px;margin-top:12px;overflow-wrap:anywhere}
.checkout-film{display:grid;grid-template-columns:88px minmax(0,1fr);gap:14px;align-items:start;margin:14px 0}.checkout-film img{display:block;width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:8px;background:#edf0e8}.checkout-film h3{margin-top:0}
header{display:flex;justify-content:space-between;gap:12px}header span{font-size:12px;color:#7c694e}h3{font-size:18px;margin:12px 0 6px}p{font-size:14px;line-height:1.7;margin:5px 0;color:#5f634f}.checkout-seats{font-weight:600}.checkout-total{font-size:22px;color:#9a5710;font-weight:700}.checkout-notice{font-size:12px}.checkout-error{color:#a83621}.checkout-actions{display:flex;gap:16px;align-items:center;flex-wrap:wrap;margin-top:14px}.checkout-actions button{font:inherit;background:#ff920f;border:0;border-radius:8px;padding:10px 14px;cursor:pointer;font-weight:600}.checkout-actions button:disabled{opacity:.5;cursor:wait}.checkout-actions a{font-size:13px;color:#815921;text-decoration:none}button:focus-visible,a:focus-visible{outline:2px solid #bd6f13;outline-offset:3px}
@media(max-width:600px){.checkout-card{padding:13px}.checkout-actions button{width:100%}.checkout-film{grid-template-columns:72px minmax(0,1fr);gap:10px}}
</style>
