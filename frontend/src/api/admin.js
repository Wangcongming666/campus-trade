import request from '@/utils/request'

/**
 * 管理端模块（路径已由规范第 10 节确认，需 ADMIN）
 *   GET   /admin/goods/audits
 *   PATCH /admin/goods/{id}/audit          { result: APPROVE|REJECT, reason? }
 *   GET   /admin/goods
 *   PATCH /admin/goods/{id}/status         { status }
 *   GET   /admin/users
 *   PATCH /admin/users/{id}/status         { status }
 *   GET   /admin/student-verifies
 *   PATCH /admin/student-verifies/{id}     { result, reason? }
 *   GET   /admin/stats
 *   GET/PATCH /admin/reports
 *   GET/POST/PUT/PATCH/DELETE /admin/announcements
 * 所有 id 为雪花 ID 字符串。status / result 取值见 enums.js。
 */

export function getAuditGoods(params) {
  return request({
    url: '/admin/goods/audits',
    method: 'get',
    params,
  })
}

export function auditGoods(id, data) {
  return request({
    url: `/admin/goods/${id}/audit`,
    method: 'patch',
    data,
  })
}

export function getAdminGoods(params) {
  return request({
    url: '/admin/goods',
    method: 'get',
    params,
  })
}

export function updateAdminGoodsStatus(id, data) {
  return request({
    url: `/admin/goods/${id}/status`,
    method: 'patch',
    data,
  })
}

export function getAdminUsers(params) {
  return request({
    url: '/admin/users',
    method: 'get',
    params,
  })
}

export function updateAdminUserStatus(id, data) {
  return request({
    url: `/admin/users/${id}/status`,
    method: 'patch',
    data,
  })
}

export function getStudentVerifies(params) {
  return request({
    url: '/admin/student-verifies',
    method: 'get',
    params,
  })
}

export function auditStudentVerify(id, data) {
  return request({
    url: `/admin/student-verifies/${id}`,
    method: 'patch',
    data,
  })
}

export function getAdminStats(params) {
  return request({
    url: '/admin/stats',
    method: 'get',
    params,
  })
}

export function getAdminReports(params) {
  return request({
    url: '/admin/reports',
    method: 'get',
    params,
  })
}

export function handleAdminReport(id, data) {
  return request({
    url: `/admin/reports/${id}`,
    method: 'patch',
    data,
  })
}

export function getAdminAnnouncements(params) {
  return request({
    url: '/admin/announcements',
    method: 'get',
    params,
  })
}

export function getAdminAnnouncementDetail(id) {
  return request({
    url: `/admin/announcements/${id}`,
    method: 'get',
  })
}

export function createAdminAnnouncement(data) {
  return request({
    url: '/admin/announcements',
    method: 'post',
    data,
  })
}

export function updateAdminAnnouncement(id, data) {
  return request({
    url: `/admin/announcements/${id}`,
    method: 'put',
    data,
  })
}

export function updateAdminAnnouncementStatus(id, data) {
  return request({
    url: `/admin/announcements/${id}/status`,
    method: 'patch',
    data,
  })
}

export function deleteAdminAnnouncement(id) {
  return request({
    url: `/admin/announcements/${id}`,
    method: 'delete',
  })
}
