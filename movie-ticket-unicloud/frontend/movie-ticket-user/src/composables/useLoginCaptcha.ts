import { onBeforeUnmount, onMounted, ref } from 'vue'
import { authApi } from '@/api/auth'

export function useLoginCaptcha() {
  const captchaId = ref('')
  const captchaImage = ref('')
  const captcha = ref('')
  const captchaLoading = ref(false)
  const captchaError = ref('')
  let generation = 0
  const refreshCaptcha = async () => {
    const current = ++generation
    captcha.value = ''
    captchaId.value = ''
    captchaImage.value = ''
    captchaError.value = ''
    captchaLoading.value = true
    try {
      const result = await authApi.getCaptcha()
      if (current !== generation) return
      if (!result.captchaId || !result.image?.startsWith('data:image/png;base64,')) throw new Error('验证码不可用')
      captchaId.value = result.captchaId
      captchaImage.value = result.image
    } catch {
      if (current === generation) captchaError.value = '验证码加载失败，请点击图片重试'
    } finally {
      if (current === generation) captchaLoading.value = false
    }
  }
  onMounted(refreshCaptcha)
  onBeforeUnmount(() => { generation++ })
  return { captchaId, captchaImage, captcha, captchaLoading, captchaError, refreshCaptcha }
}
