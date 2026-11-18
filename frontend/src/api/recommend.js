import request from '@/utils/request'

/**
 * 推荐模块（规范第 9、10 节已确认）
 *   GET /recommend/home?size=              未登录走热度/冷启动
 *   GET /recommend/similar/{goodsId}?size=
 * goodsId 为雪花 ID 字符串。
 */

export function getHomeRecommend(params) {
  return request({
    url: '/recommend/home',
    method: 'get',
    silent: true,
    params,
  })
}

export function getSimilarGoods(goodsId, params) {
  return request({
    url: `/recommend/similar/${goodsId}`,
    method: 'get',
    silent: true,
    params,
  })
}
