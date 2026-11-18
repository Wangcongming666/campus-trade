import request from '@/utils/request'

/**
 * 认证模块（路径与请求体已由规范第 10 节确认）
 *   GET  /auth/captcha  → { captchaKey, imageBase64 }
 *   POST /auth/register { username, password, phone?, captchaKey, captchaCode }
 *   POST /auth/login    { account, password, captchaKey, captchaCode }
 *     account 可为用户名 / 手机号 / 学号
 *   POST /auth/refresh  { refreshToken }
 *   POST /auth/logout
 * 登录成功 data：{ accessToken, refreshToken, expiresIn, userInfo }
 */

export function getCaptcha() {
  return request({
    url: '/auth/captcha',
    method: 'get',
  })
}

export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data,
  })
}

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  })
}

export function refreshToken(data) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    data,
  })
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post',
  })
}
