import request from '@/utils/request'

/**
 * 价格建议（规范第 11 节已确认）
 *   POST /price/suggest
 *   请求 { categoryId, condition, originalPrice?, title? }
 *   返回 { suggestedPrice, minPrice, maxPrice, sampleCount, reason }
 *   金额为数字，保留 2 位小数。
 */
export function suggestPrice(data) {
  return request({
    url: '/price/suggest',
    method: 'post',
    data,
  })
}
