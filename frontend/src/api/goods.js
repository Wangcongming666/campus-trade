import request from '@/utils/request'

/**
 * 商品模块（路径已由规范第 10 节确认）
 *   GET    /goods                 参数 categoryId/minPrice/maxPrice/condition/sort(latest|price_asc|price_desc|hot)
 *   列表不返回 description、images；详情为完整 GoodsVO（含 images、auditReason）
 *   GET    /goods/search?keyword=
 *   GET    /goods/{id}
 *   POST   /goods
 *   PUT    /goods/{id}
 *   PATCH  /goods/{id}/status     { status }（卖家上/下架，status 取值见 enums.js，待定）
 *   DELETE /goods/{id}
 *   POST   /goods/{id}/favorite
 *   DELETE /goods/{id}/favorite
 * 所有 id 为雪花 ID 字符串。
 */

export function getGoodsList(params) {
  return request({
    url: '/goods',
    method: 'get',
    params,
  })
}

export function searchGoods(params) {
  return request({
    url: '/goods/search',
    method: 'get',
    params,
  })
}

export function getGoodsDetail(id) {
  return request({
    url: `/goods/${id}`,
    method: 'get',
  })
}

export function publishGoods(data) {
  return request({
    url: '/goods',
    method: 'post',
    data,
  })
}

export function updateGoods(id, data) {
  return request({
    url: `/goods/${id}`,
    method: 'put',
    data,
  })
}

export function updateGoodsStatus(id, data) {
  return request({
    url: `/goods/${id}/status`,
    method: 'patch',
    data,
  })
}

export function deleteGoods(id) {
  return request({
    url: `/goods/${id}`,
    method: 'delete',
  })
}

export function addFavorite(id) {
  return request({
    url: `/goods/${id}/favorite`,
    method: 'post',
  })
}

export function removeFavorite(id) {
  return request({
    url: `/goods/${id}/favorite`,
    method: 'delete',
  })
}
