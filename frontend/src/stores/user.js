import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getCurrentUser, getMyInterests, saveMyInterests } from '@/api/user'
import { disconnectStomp } from '@/utils/stomp'
import { Role } from '@/constants/enums'

const TOKEN_KEY = 'campus_trade_access_token'
const REFRESH_TOKEN_KEY = 'campus_trade_refresh_token'
const USER_INFO_KEY = 'campus_trade_user_info'
const EXPIRES_IN_KEY = 'campus_trade_expires_in'

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const expiresIn = ref(Number(localStorage.getItem(EXPIRES_IN_KEY)) || 0)
  const userInfo = ref(readJson(USER_INFO_KEY, null))
  const interests = ref([])
  const interestsVersion = ref(0)
  const interestPickerVisible = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed(() => userInfo.value?.role || Role.USER)
  const isAdmin = computed(() => role.value === Role.ADMIN)

  function persistTokens({ accessToken, refreshToken: nextRefresh, expiresIn: nextExpires } = {}) {
    if (accessToken != null) {
      token.value = accessToken
      localStorage.setItem(TOKEN_KEY, accessToken)
    }
    // 刷新接口会轮换 refreshToken，必须替换保存
    if (nextRefresh != null) {
      refreshToken.value = nextRefresh
      localStorage.setItem(REFRESH_TOKEN_KEY, nextRefresh)
    }
    if (nextExpires != null) {
      expiresIn.value = nextExpires
      localStorage.setItem(EXPIRES_IN_KEY, String(nextExpires))
    }
  }

  function persistUserInfo(info) {
    userInfo.value = info || null
    if (info) {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
    } else {
      localStorage.removeItem(USER_INFO_KEY)
    }
  }

  function setTokens(payload = {}) {
    persistTokens(payload)
    if (payload.userInfo) {
      persistUserInfo(payload.userInfo)
    }
  }

  function resetAuth() {
    token.value = ''
    refreshToken.value = ''
    expiresIn.value = 0
    userInfo.value = null
    interests.value = []
    interestPickerVisible.value = false
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(EXPIRES_IN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
    disconnectStomp()
  }

  /**
   * @param {{ account: string, password: string, captchaKey: string, captchaCode: string }} payload
   */
  async function login(payload) {
    const data = await loginApi(payload)
    setTokens(data)
    if (!data?.userInfo) {
      await fetchUserInfo()
    }
    return data
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } catch {
      // 退出接口失败也要清理本地登录态
    } finally {
      resetAuth()
    }
  }

  async function fetchUserInfo() {
    const data = await getCurrentUser()
    persistUserInfo(data)
    return data
  }

  async function loadInterests() {
    const data = await getMyInterests()
    interests.value = Array.isArray(data) ? data : []
    return interests.value
  }

  async function saveInterests(categoryIds) {
    const data = await saveMyInterests({ categoryIds })
    interests.value = Array.isArray(data) ? data : []
    interestsVersion.value += 1
    return interests.value
  }

  function openInterestPicker() {
    interestPickerVisible.value = true
  }

  function closeInterestPicker() {
    interestPickerVisible.value = false
  }

  return {
    token,
    refreshToken,
    expiresIn,
    userInfo,
    isLoggedIn,
    role,
    isAdmin,
    setTokens,
    resetAuth,
    login,
    logout,
    fetchUserInfo,
    interests,
    interestsVersion,
    interestPickerVisible,
    loadInterests,
    saveInterests,
    openInterestPicker,
    closeInterestPicker,
  }
})
