import request from '@/utils/request'

/**
 * 评价模块（路径已由规范第 10 节确认）
 *   POST /reviews { orderId, score(1-5), content }
 *   返回 ReviewVO { id, orderId, fromUser{id,nickname,avatar}, score, content, createTime }
 *   GET  /reviews?userId=
 */

export function createReview(data) {
  return request({
    url: '/reviews',
    method: 'post',
    data,
  })
}

export function getReviewList(params) {
  return request({
    url: '/reviews',
    method: 'get',
    params,
  })
}
