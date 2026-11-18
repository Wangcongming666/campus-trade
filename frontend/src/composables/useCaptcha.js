import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getCaptcha } from '@/api/auth'
import { CAPTCHA_INVALID_EVENT } from '@/constants/errorCodes'

export function useCaptcha() {
  const captchaKey = ref('')
  const captchaImage = ref('')
  const loading = ref(false)

  async function refresh() {
    loading.value = true
    try {
      const data = await getCaptcha()
      captchaKey.value = data?.captchaKey || ''
      const raw = data?.imageBase64 || ''
      captchaImage.value = raw.startsWith('data:') ? raw : `data:image/png;base64,${raw}`
    } finally {
      loading.value = false
    }
  }

  function onCaptchaInvalid() {
    refresh()
  }

  onMounted(() => {
    refresh()
    window.addEventListener(CAPTCHA_INVALID_EVENT, onCaptchaInvalid)
  })

  onBeforeUnmount(() => {
    window.removeEventListener(CAPTCHA_INVALID_EVENT, onCaptchaInvalid)
  })

  return { captchaKey, captchaImage, loading, refresh }
}
