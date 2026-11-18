import request from '@/utils/request'

/**
 * 聊天 HTTP 接口（路径已由规范第 10 节确认）
 *   POST  /chat/sessions                    { goodsId, toUserId }（已存在则返回原会话）
 *   返回 ChatSessionVO，主键字段为 id（不要使用 sessionId）
 *   GET   /chat/sessions                 → ChatSessionVO[]（不分页，按最近消息倒序）
 *   GET   /chat/sessions/{sessionId}/messages
 *   PATCH /chat/sessions/{sessionId}/read
 * 实时消息走 STOMP，见 src/utils/stomp.js。
 * sessionId / userId / goodsId 均为雪花 ID 字符串。
 */

export function createChatSession(data) {
  return request({
    url: '/chat/sessions',
    method: 'post',
    data,
  })
}

export function getChatSessions(params, config = {}) {
  return request({
    url: '/chat/sessions',
    method: 'get',
    params,
    ...config,
  })
}

export function getChatMessages(sessionId, params, config = {}) {
  return request({
    url: `/chat/sessions/${sessionId}/messages`,
    method: 'get',
    params,
    ...config,
  })
}

export function markSessionRead(sessionId) {
  return request({
    url: `/chat/sessions/${sessionId}/read`,
    method: 'patch',
  })
}
