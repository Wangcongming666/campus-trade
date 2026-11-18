import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { API_OFFLINE_TEXT, BizCode, CAPTCHA_INVALID_EVENT, isCaptchaError } from '@/constants/errorCodes'
import { USE_MOCK } from '@/utils/format'

/**
 * 后端统一返回格式（已由《后端接口规范》v1.0 确认）：
 * { code, msg, data }，code === 200 时拦截器返回 data。
 * 业务错误：HTTP 200 + 非 200 业务 code，提示 msg。
 * Token 过期：HTTP 401，POST /auth/refresh 换票后重放；并发排队只刷新一次。
 * 无权限：HTTP 403。
 * 认证：仅在已登录时加 Authorization: Bearer <accessToken>，游客不加该头。
 */

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

instance.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    // 无 token 时不加 Authorization 头，保证游客可访问白名单接口
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

let isRefreshing = false
const pendingQueue = []

function enqueuePending(cb) {
  pendingQueue.push(cb)
}

function flushPending(error, token) {
  pendingQueue.forEach((cb) => cb(error, token))
  pendingQueue.length = 0
}

function isRefreshRequest(config) {
  const url = config?.url || ''
  return url.includes('/auth/refresh')
}

async function redirectToLogin() {
  const userStore = useUserStore()
  userStore.resetAuth()
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({
      path: '/login',
      query: { redirect: current.fullPath },
    })
  }
}

async function handleHttp401(error) {
  const originalConfig = error.config || {}
  const userStore = useUserStore()

  if (isRefreshRequest(originalConfig) || originalConfig._retry) {
    await redirectToLogin()
    return Promise.reject(error)
  }

  if (!userStore.refreshToken) {
    await redirectToLogin()
    return Promise.reject(error)
  }

  originalConfig._retry = true

  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      enqueuePending((err, token) => {
        if (err || !token) {
          reject(err || error)
          return
        }
        originalConfig.headers = originalConfig.headers || {}
        originalConfig.headers.Authorization = `Bearer ${token}`
        resolve(instance(originalConfig))
      })
    })
  }

  isRefreshing = true
  try {
    const res = await axios.post('/api/v1/auth/refresh', {
      refreshToken: userStore.refreshToken,
    })
    const body = res.data || {}
    if (body.code !== 200 || !body.data?.accessToken) {
      throw new Error(body.msg || '刷新登录态失败')
    }
    // data: { accessToken, refreshToken, expiresIn }，refreshToken 已轮换，必须整包写回
    userStore.setTokens(body.data)
    const newToken = body.data.accessToken
    flushPending(null, newToken)
    originalConfig.headers = originalConfig.headers || {}
    originalConfig.headers.Authorization = `Bearer ${newToken}`
    return instance(originalConfig)
  } catch (refreshError) {
    flushPending(refreshError, null)
    ElMessage.error('登录已过期，请重新登录')
    await redirectToLogin()
    return Promise.reject(refreshError)
  } finally {
    isRefreshing = false
  }
}

function notifyBizError(res = {}) {
  if (isCaptchaError(res.code)) {
    window.dispatchEvent(new CustomEvent(CAPTCHA_INVALID_EVENT, { detail: res }))
  }
  ElMessage.error(res.msg || '请求失败')
}

instance.interceptors.response.use(

  (response) => {
    const res = response.data || {}
    if (res.code === BizCode.SUCCESS || res.code === 200) {
      return res.data
    }
    if (Number(res.code) === 404) {
      return Promise.reject({ ...res, code: 404, msg: API_OFFLINE_TEXT, offline: true })
    }
    if (!response.config?.silent) {
      notifyBizError(res)
    }
    return Promise.reject(res)
  },
  async (error) => {
    const status = error.response?.status
    const body = error.response?.data || {}
    const msg = body.msg

    if (status === 401) {
      return handleHttp401(error)
    }

    if (status === 404 || Number(body.code) === 404) {
      return Promise.reject(Object.assign(error, { code: 404, msg: API_OFFLINE_TEXT, offline: true }))
    }

    if (error.config?.silent) {
      return Promise.reject(Object.assign(error, { code: body.code, msg: body.msg }))
    }

    if (status === 403) {
      ElMessage.error(msg || '无权查看该内容')
      return Promise.reject(Object.assign(error, { code: body.code || 403, msg: msg || '无权查看该内容' }))
    }

    if (body.code && body.code !== 200) {
      notifyBizError(body)
      return Promise.reject(Object.assign(error, { code: body.code, msg: body.msg }))
    }

    ElMessage.error(msg || error.message || '网络异常')
    return Promise.reject(error)
  },
)

/** 统一出口：仅当 VITE_USE_MOCK=true 时走本地演示数据；真实模式绝不进入 mock。 */
export default function request(config) {
  if (USE_MOCK) {
    return import('@/mock').then(({ handleMock }) => handleMock(config))
  }
  return instance(config)
}
