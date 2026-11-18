import request from '@/utils/request'

/**
 * 用户模块（路径已由规范第 10 节确认）
 *   GET/PUT /users/me
 *   PUT  /users/me/password { oldPassword, newPassword }
 *   GET  /users/me 返回 UserMeVO；GET /users/{id} 返回 UserPublicVO（规范第 11 节）
 *   POST /users/me/student-verify { studentNo, realName, school, cardImageUrl }
 *   GET  /users/me/goods（我的发布，可带 status）
 *   GET  /users/me/favorites
 *   GET  /users/{id}（公开主页 + 信誉分）
 *   GET  /users/me/interests → CategoryVO[]
 *   PUT  /users/me/interests { categoryIds: string[] }（整体覆盖，最多 5 个，空数组=清空）
 * 用户 id 为雪花 ID 字符串。
 */

export function getCurrentUser() {
  return request({
    url: '/users/me',
    method: 'get',
  })
}

export function updateUserProfile(data) {
  return request({
    url: '/users/me',
    method: 'put',
    data,
  })
}

export function updatePassword(data) {
  return request({
    url: '/users/me/password',
    method: 'put',
    data,
  })
}

export function verifyStudent(data) {
  return request({
    url: '/users/me/student-verify',
    method: 'post',
    data,
  })
}

export function getMyGoods(params) {
  return request({
    url: '/users/me/goods',
    method: 'get',
    params,
  })
}

export function getMyFavorites(params) {
  return request({
    url: '/users/me/favorites',
    method: 'get',
    params,
  })
}

export function getUserProfile(id) {
  return request({
    url: `/users/${id}`,
    method: 'get',
  })
}

export function getMyInterests() {
  return request({
    url: '/users/me/interests',
    method: 'get',
  })
}

export function saveMyInterests(data) {
  return request({
    url: '/users/me/interests',
    method: 'put',
    data,
  })
}
