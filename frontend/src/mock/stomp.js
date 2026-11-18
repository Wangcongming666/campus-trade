import { ChatMessageType } from '@/constants/enums'
import { CURRENT_USER_ID } from '@/mock/data'
import { appendChatMessage, ensureSession, toChatSessionVO } from '@/mock/extraState'

let hooks = { onMessage: null, onRead: null, onError: null }
let connected = false
const pendingTimers = []

export function connectMockStomp(nextHooks = {}) {
  if (nextHooks.onMessage) hooks.onMessage = nextHooks.onMessage
  if (nextHooks.onRead) hooks.onRead = nextHooks.onRead
  if (nextHooks.onError) hooks.onError = nextHooks.onError
  connected = true
  return { connected: true, active: true }
}

export function sendMockChat(payload = {}) {
  if (!connected) {
    throw new Error('聊天连接尚未建立')
  }
  const toUserId = String(payload.toUserId || '')
  const goodsId = String(payload.goodsId || '')
  const type = payload.type || ChatMessageType.TEXT
  const content = payload.content || ''
  if (!toUserId || !content) {
    throw new Error('消息不完整')
  }
  const session = ensureSession(toUserId, goodsId)
  appendChatMessage({
    sessionId: session.id,
    fromUserId: CURRENT_USER_ID,
    toUserId,
    goodsId,
    content,
    type,
    readFlag: 1,
  })

  const timer = setTimeout(() => {
    const reply = appendChatMessage({
      sessionId: session.id,
      fromUserId: toUserId,
      toUserId: CURRENT_USER_ID,
      goodsId,
      content: '（演示）收到了，这块我们可以再细聊～',
      type: ChatMessageType.TEXT,
      readFlag: 0,
    })
    hooks.onMessage?.(reply)
  }, 2200)
  pendingTimers.push(timer)
  return toChatSessionVO(session)
}

export function disconnectMockStomp() {
  pendingTimers.forEach((timer) => clearTimeout(timer))
  pendingTimers.length = 0
  connected = false
  hooks = { onMessage: null, onRead: null, onError: null }
}

export function isMockStompConnected() {
  return connected
}
