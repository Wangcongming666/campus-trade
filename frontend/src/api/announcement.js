import request from '@/utils/request'

/**
 * 前台公告（游客可看，仅 PUBLISHED）
 *   GET /announcements（按发布时间倒序分页）
 *   GET /announcements/{id}
 */
export function getAnnouncements(params) {
  return request({
    url: '/announcements',
    method: 'get',
    params,
  })
}

export function getAnnouncementDetail(id, config = {}) {
  return request({
    url: `/announcements/${id}`,
    method: 'get',
    ...config,
  })
}
