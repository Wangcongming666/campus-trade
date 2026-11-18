import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useUserStore } from '@/stores/user'
import { USE_MOCK } from '@/utils/format'

/**
 * STOMP over SockJS 封装（约定来自《后端接口规范》第 7 节）：
 * - 端点：/ws（SockJS 兼容）
 * - CONNECT 帧头携带 Authorization: Bearer <accessToken>
 * - 发送：/app/chat.send，消息体 { toUserId, goodsId, content, type }
 *   type：1 文本 2 图片 3 商品卡片
 * - 接收：订阅 /user/queue/messages
 * - 已读回执：订阅 /user/queue/read
 * - 发送失败：订阅 /user/queue/errors，载荷 { code, msg, data:null }
 *
 * mock 模式下走 src/mock/stomp.js 假适配器，发送后约 2 秒自动回一条演示消息。
 * 所有 id 均为雪花 ID 字符串，不要转成 Number。
 */

const WS_ENDPOINT = import.meta.env.VITE_WS_URL || '/ws'

let client = null
let messageCallback = null
let readCallback = null
let errorCallback = null

function buildClient() {
  const stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: () => {
      const userStore = useUserStore()
      stompClient.connectHeaders = {
        Authorization: `Bearer ${userStore.token}`,
      }
    },
    onConnect: () => {
      stompClient.subscribe('/user/queue/messages', (frame) => {
        const payload = safeParse(frame.body)
        messageCallback?.(payload)
      })
      stompClient.subscribe('/user/queue/read', (frame) => {
        const payload = safeParse(frame.body)
        readCallback?.(payload)
      })
      stompClient.subscribe('/user/queue/errors', (frame) => {
        const payload = safeParse(frame.body)
        errorCallback?.(payload)
      })
    },
    onStompError: (frame) => {
      console.error('[STOMP] broker error:', frame.headers['message'], frame.body)
    },
    onWebSocketClose: () => {
      console.warn('[STOMP] socket closed')
    },
  })
  return stompClient
}

function safeParse(body) {
  if (!body) return body
  try {
    return JSON.parse(body)
  } catch {
    return body
  }
}

export async function connectStomp(hooks = {}) {
  if (hooks.onMessage) messageCallback = hooks.onMessage
  if (hooks.onRead) readCallback = hooks.onRead
  if (hooks.onError) errorCallback = hooks.onError

  if (USE_MOCK) {
    const { connectMockStomp } = await import('@/mock/stomp')
    return connectMockStomp({ onMessage: messageCallback, onRead: readCallback, onError: errorCallback })
  }

  if (client?.active) {
    return client
  }

  client = buildClient()
  client.activate()
  return client
}

/**
 * 发送聊天消息。返回 Promise，便于处理发送失败与重发。
 * @param {{ toUserId: string, goodsId?: string, content: string, type: number }} payload
 */
export async function sendChat(payload) {
  if (USE_MOCK) {
    const { sendMockChat } = await import('@/mock/stomp')
    return sendMockChat(payload)
  }
  if (!client?.connected) {
    throw new Error('聊天连接尚未建立')
  }
  client.publish({
    destination: '/app/chat.send',
    body: JSON.stringify(payload),
  })
}

export async function disconnectStomp() {
  if (USE_MOCK) {
    const { disconnectMockStomp } = await import('@/mock/stomp')
    disconnectMockStomp()
    messageCallback = null
    readCallback = null
    errorCallback = null
    return
  }
  if (client) {
    client.deactivate()
    client = null
  }
  messageCallback = null
  readCallback = null
  errorCallback = null
}

export function getStompClient() {
  return client
}
