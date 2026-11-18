import request from '@/utils/request'

/**
 * 举报：POST /reports { targetType: GOODS|USER, targetId, reason(≤64), detail?(≤500) } → ReportVO
 */
export function createReport(data) {
  return request({
    url: '/reports',
    method: 'post',
    data,
  })
}
