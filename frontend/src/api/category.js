import request from '@/utils/request'

/**
 * 分类模块（路径已由规范第 10 节确认）
 *   GET /categories       平铺
 *   GET /categories/tree
 * 游客可访问。
 */

export function getCategoryList(params) {
  return request({
    url: '/categories',
    method: 'get',
    params,
  })
}

export function getCategoryTree() {
  return request({
    url: '/categories/tree',
    method: 'get',
  })
}
