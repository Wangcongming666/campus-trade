import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { getChatSessions, markSessionRead } from '@/api/chat'
import { ChatMessageType } from '@/constants/enums'
import { connectStomp, disconnectStomp } from '@/utils/stomp'
import { isApiOffline } from '@/constants/errorCodes'
import { useUserStore } from '@/stores/user'

function previewOf(message) {
  if (message.type === ChatMessageType.IMAGE) return '[图片]'
  if (message.type === ChatMessageType.GOODS_CARD) return '[商品]'
  return message.content || ''
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref([])
  const currentSessionId = ref('')
  const started = ref(false)
  const offline = ref(false)
  const incomingMessage = ref(null)
  const incomingError = ref(null)

  const unreadTotal = computed(() =>
    sessions.value.reduce((sum, item) => sum + (Number(item.unreadCount) || 0), 0),
  )

  const currentSession = computed(
    () => sessions.value.find((item) => String(item.id) === String(currentSessionId.value)) || null,
  )

  function upsertSession(session) {
    if (!session || session.id == null || session.id === '') return
    const normalized = { ...session, id: String(session.id) }
    const index = sessions.value.findIndex((item) => String(item.id) === normalized.id)
    if (index >= 0) {
      const current = sessions.value[index]
      sessions.value.splice(index, 1)
      sessions.value.unshift({ ...current, ...normalized })
    } else {
      sessions.value.unshift(normalized)
    }
  }

  function upsertSessionFromMessage(message) {
    const sessionId = String(message.sessionId || '')
    if (!sessionId) return
    const index = sessions.value.findIndex((item) => String(item.id) === sessionId)
    const patch = {
      lastContent: previewOf(message),
      lastTime: message.createTime,
    }
    if (index >= 0) {
      const current = sessions.value[index]
      const isCurrent = currentSessionId.value === sessionId
      const mine = message.fromUserId === useUserStore().userInfo?.id
      sessions.value[index] = {
        ...current,
        ...patch,
        unreadCount: mine || isCurrent ? (isCurrent ? 0 : current.unreadCount || 0) : (current.unreadCount || 0) + 1,
      }
      const [hit] = sessions.value.splice(index, 1)
      sessions.value.unshift(hit)
    } else {
      loadSessions()
    }
  }

  async function loadSessions() {
    try {
      const data = await getChatSessions(undefined, { silent: true })
      const list = Array.isArray(data) ? data : data?.records || []
      sessions.value = list.map((item) => {
        const normalized = { ...item, id: String(item.id) }
        return normalized.id === String(currentSessionId.value)
          ? { ...normalized, unreadCount: 0 }
          : normalized
      })
      offline.value = false
    } catch (error) {
      sessions.value = []
      offline.value = isApiOffline(error)
      throw error
    }
  }

  function handleIncoming(message) {
    if (!message) return
    incomingMessage.value = message
    upsertSessionFromMessage(message)
    const mine = message.fromUserId === useUserStore().userInfo?.id
    const sessionId = String(message.sessionId || '')
    if (!mine && sessionId && sessionId === currentSessionId.value) {
      // 当前正在看的会话也要告知后端已读，否则对方收不到已读回执
      markSessionRead(sessionId).catch(() => {})
    }
    if (!mine && sessionId && sessionId !== currentSessionId.value) {
      const session = sessions.value.find((item) => item.id === sessionId)
      ElNotification({
        title: session?.peer?.nickname || '新消息',
        message: previewOf(message),
        duration: 4000,
      })
    }
  }

  function handleRead(payload) {
    const sessionId = String(payload?.sessionId || '')
    if (!sessionId) return
    incomingMessage.value = { ...payload, _kind: 'read' }
  }

  function handleError(payload) {
    const msg = payload?.msg || '消息发送失败'
    ElMessage.error(msg)
    incomingError.value = { ...(payload || {}), msg, _ts: Date.now() }
  }

  async function startRealtime() {
    if (!useUserStore().token) return
    try {
      await loadSessions()
    } catch {
      sessions.value = []
    }
    try {
      await connectStomp({ onMessage: handleIncoming, onRead: handleRead, onError: handleError })
    } catch {
      // 聊天服务未就绪时不阻断页面（例如后端尚未提供 STOMP）
    }
    started.value = true
  }

  async function stopRealtime() {
    started.value = false
    offline.value = false
    currentSessionId.value = ''
    sessions.value = []
    await disconnectStomp()
  }

  async function selectSession(sessionId) {
    currentSessionId.value = sessionId ? String(sessionId) : ''
    if (!currentSessionId.value) return
    const session = sessions.value.find((item) => String(item.id) === String(currentSessionId.value))
    if (session && session.unreadCount) {
      session.unreadCount = 0
      try {
        await markSessionRead(currentSessionId.value)
      } catch {
        // 已读失败不打断阅读
      }
    }
  }

  function clearCurrentSession() {
    currentSessionId.value = ''
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    unreadTotal,
    incomingMessage,
    incomingError,
    started,
    offline,
    loadSessions,
    upsertSession,
    startRealtime,
    stopRealtime,
    selectSession,
    clearCurrentSession,
  }
})
