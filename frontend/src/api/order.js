import request from '@/utils/request'

/**
 * 订单模块（路径已由规范第 10 节确认）
 *   POST  /orders                    { goodsId, remark? }
 *   GET   /orders?role=buyer|seller&status=
 *   GET   /orders/{id}
 *   PATCH /orders/{id}/status        { action, reason? }
 * action 取值见 src/constants/enums.js（PAY/CANCEL/CONFIRM/REFUND）。
 * 订单 VO：{ id, goodsId, goodsTitle, goodsCover, buyer, seller, price, remark, status, cancelReason, payTime, completeTime, createTime, reviewed }
 * 取消原因字段为 cancelReason；对方由 buyer/seller 按当前角色推算。仅 price 为下单快照。
 * PATCH /orders/{id}/status 的请求体仍为 { action, reason? }。
 * 所有 id 为雪花 ID 字符串。
 */

export function createOrder(data) {
  return request({
    url: '/orders',
    method: 'post',
    data,
  })
}

export function getOrderList(params) {
  return request({
    url: '/orders',
    method: 'get',
    params,
  })
}

export function getOrderDetail(id, config = {}) {
  return request({
    url: `/orders/${id}`,
    method: 'get',
    ...config,
  })
}

export function updateOrderStatus(id, data) {
  return request({
    url: `/orders/${id}/status`,
    method: 'patch',
    data,
  })
}
