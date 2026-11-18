<template>
  <div class="chat-page" data-testid="chat-page">
    <div class="chat-box" :class="{ 'has-side': activeSession && goodsInfo, 'has-active': activeSession }">
      <!-- 会话栏 -->
      <aside class="session-pane">
        <div class="session-pane__head">
          <h2>消息</h2>
          <span v-if="chatStore.unreadTotal" class="ct-badge ct-badge--hot num">{{ chatStore.unreadTotal }} 未读</span>
        </div>
        <label class="session-search">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" type="search" placeholder="搜索联系人或商品" />
        </label>
        <div class="session-filter">
          <button type="button" class="ct-chip is-sm" :class="{ 'is-active': sessionFilter === 'all' }" @click="sessionFilter = 'all'">全部</button>
          <button type="button" class="ct-chip is-sm" :class="{ 'is-active': sessionFilter === 'unread' }" @click="sessionFilter = 'unread'">未读</button>
        </div>
        <el-scrollbar class="session-scroll">
          <template v-if="!chatStore.started">
            <div v-for="n in 6" :key="n" class="session-sk">
              <div class="ct-sk session-sk__av" />
              <div class="session-sk__lines">
                <div class="ct-sk" style="width: 50%" />
                <div class="ct-sk" style="width: 80%" />
              </div>
            </div>
          </template>
          <button
            v-for="item in filteredSessions"
            :key="item.id"
            class="session-item"
            :class="{ 'is-active': String(item.id) === String(chatStore.currentSessionId) }"
            :data-session-id="String(item.id)"
            type="button"
            @click="openSession(item.id)"
          >
            <el-avatar :size="44" :src="item.peer?.avatar">
              {{ (item.peer?.nickname || '同').slice(0, 1) }}
            </el-avatar>
            <div class="session-item__body">
              <div class="session-item__row">
                <span class="session-item__name">{{ item.peer?.nickname || '同学' }}</span>
                <span class="session-item__time">{{ formatRelativeTime(item.lastTime) }}</span>
              </div>
              <div class="session-item__row">
                <p class="session-item__preview">{{ item.lastContent || '暂无消息' }}</p>
                <span v-if="item.unreadCount" class="session-item__unread num">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</span>
              </div>
              <p v-if="item.goodsTitle" class="session-item__goods">{{ item.goodsTitle }}</p>
            </div>
            <img v-if="item.goodsCover" class="session-item__thumb" :src="item.goodsCover" alt="" />
          </button>
          <p v-if="chatStore.started && chatStore.sessions.length && !filteredSessions.length" class="session-none">没有符合条件的会话</p>
          <div v-if="chatStore.started && !chatStore.sessions.length" class="session-none">
            {{ chatStore.offline ? API_OFFLINE_TEXT : '还没有会话' }}
          </div>
        </el-scrollbar>
      </aside>

      <!-- 主会话区 -->
      <section class="chat-main">
        <Transition name="conn">
          <div v-if="connState !== 'ok'" class="conn-bar" :class="`is-${connState}`">
            {{ connState === 'lost' ? '连接已断开，正在重连…' : '已重新连接' }}
          </div>
        </Transition>

        <template v-if="activeSession">
          <header class="chat-main__head">
            <el-avatar :size="42" :src="activeSession.peer?.avatar">{{ (activeSession.peer?.nickname || '同').slice(0, 1) }}</el-avatar>
            <div class="chat-main__who">
              <strong>{{ activeSession.peer?.nickname || '同学' }}</strong>
              <p v-if="activeSession.goodsTitle">关于「{{ activeSession.goodsTitle }}」</p>
            </div>
            <router-link v-if="activeSession.peer?.id" class="ct-icon-btn" :to="`/users/${activeSession.peer.id}`" title="查看主页">
              <el-icon><User /></el-icon>
            </router-link>
          </header>

          <div v-if="activeSession.goodsId" class="pin-goods">
            <img v-if="goodsInfo?.coverUrl || activeSession.goodsCover" :src="goodsInfo?.coverUrl || activeSession.goodsCover" alt="" />
            <div class="pin-goods__body">
              <p>{{ goodsInfo?.title || activeSession.goodsTitle }}</p>
              <b v-if="goodsInfo" class="num">¥{{ formatPrice(goodsInfo.price) }}</b>
            </div>
            <span v-if="goodsStatusText" class="ct-badge" :class="goodsInfo?.status === GoodsStatus.ON_SHELF ? 'ct-badge--mint' : goodsInfo?.status === GoodsStatus.RESERVED ? 'ct-badge--sun' : 'ct-badge--gray'">{{ goodsStatusText }}</span>
            <router-link class="pin-goods__go" :to="`/goods/${activeSession.goodsId}`">查看商品</router-link>
          </div>

          <div ref="listRef" class="message-list" @scroll="onScroll">
            <div v-if="loadingOlder" class="message-list__more"><span class="ct-spinner" />正在加载更早的消息…</div>
            <div v-else-if="!hasOlder && messages.length" class="message-list__more">没有更早的消息了</div>

            <div v-if="loading && !messages.length" class="msg-sk">
              <div class="ct-sk msg-sk__b" />
              <div class="ct-sk msg-sk__b is-mine" />
              <div class="ct-sk msg-sk__b" />
            </div>

            <TransitionGroup :name="bulk ? 'bubble-none' : 'bubble'" tag="div" class="message-flow">
              <div
                v-for="row in timeline"
                :key="row.key"
                :class="row.kind === 'day' ? 'day-pill' : ['bubble-row', { 'is-mine': isMine(row.item) }]"
              >
                <span v-if="row.kind === 'day'">{{ row.label }}</span>
                <template v-else>
                  <el-avatar :size="32" :src="avatarOf(row.item)">
                    {{ nicknameOf(row.item).slice(0, 1) }}
                  </el-avatar>
                  <div class="bubble-wrap">
                    <div class="bubble-line">
                      <button
                        v-if="isMine(row.item) && row.item.failed"
                        class="retry"
                        type="button"
                        title="发送失败，点击重试"
                        aria-label="发送失败，点击重试"
                        @click="retrySend(row.item)"
                      >
                        !
                      </button>
                      <div
                        class="bubble"
                        :class="[`is-type-${row.item.type}`, { 'is-mine': isMine(row.item), 'is-failed': row.item.failed, 'is-sending': row.item.sending }]"
                      >
                        <template v-if="row.item.type === ChatMessageType.IMAGE">
                          <el-image :src="row.item.content" fit="cover" :preview-src-list="[row.item.content]" preview-teleported />
                        </template>
                        <template v-else-if="row.item.type === ChatMessageType.GOODS_CARD">
                          <button
                            v-if="parseGoodsCard(row.item.content)"
                            class="goods-chip"
                            type="button"
                            @click="goGoods(parseGoodsCard(row.item.content).goodsId)"
                          >
                            <img :src="parseGoodsCard(row.item.content).coverUrl" alt="" />
                            <div>
                              <p>{{ parseGoodsCard(row.item.content).title }}</p>
                              <strong class="num">¥{{ formatPrice(parseGoodsCard(row.item.content).price) }}</strong>
                            </div>
                          </button>
                          <span v-else>{{ row.item.content }}</span>
                        </template>
                        <span v-else>{{ row.item.content }}</span>
                      </div>
                    </div>
                    <div class="bubble-meta">
                      <span class="num">{{ timeOf(row.item) }}</span>
                      <span v-if="isMine(row.item) && row.item.sending">发送中</span>
                      <span v-else-if="isMine(row.item) && row.item.failed" class="is-err">发送失败，点击 ! 重试</span>
                      <span v-else-if="isMine(row.item)" :class="{ 'is-read': row.item.readFlag }">
                        {{ row.item.readFlag ? '已读' : '未读' }}
                      </span>
                    </div>
                  </div>
                </template>
              </div>
            </TransitionGroup>
          </div>

          <div class="quick">
            <button v-for="text in QUICK_REPLIES" :key="text" type="button" class="ct-chip is-sm" @click="useQuickReply(text)">{{ text }}</button>
          </div>

          <footer class="composer">
            <input ref="fileRef" class="composer__file" type="file" accept="image/*" @change="onPickImage" />
            <div class="composer__box">
              <button type="button" class="composer__icon" title="发送图片" @click="fileRef?.click()">
                <el-icon :size="20"><Picture /></el-icon>
              </button>
              <button
                type="button"
                class="composer__icon"
                data-testid="send-goods-card"
                title="发送商品卡片"
                @click="sendGoodsCard"
              >
                <el-icon :size="20"><Goods /></el-icon>
              </button>
              <span class="composer__sep" />
              <el-input
                v-model="draft"
                class="composer__input"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                placeholder="输入消息，Enter 发送，Shift + Enter 换行"
                @keydown.enter.exact.prevent="sendText"
              />
              <el-button
                class="composer__send"
                type="primary"
                circle
                :loading="sending"
                :disabled="!draft.trim()"
                aria-label="发送"
                @click="sendText"
              >
                <el-icon v-if="!sending"><Promotion /></el-icon>
              </el-button>
            </div>
          </footer>
        </template>

        <div v-else class="chat-empty">
          <div class="chat-empty__art" aria-hidden="true">
            <span class="b1" />
            <span class="b2" />
          </div>
          <template v-if="chatStore.started && !chatStore.sessions.length">
            <p>{{ chatStore.offline ? API_OFFLINE_TEXT : '还没有消息，去看看感兴趣的闲置吧' }}</p>
            <el-button type="primary" round @click="$router.push('/')">去逛逛</el-button>
          </template>
          <p v-else>选择一个会话开始聊天</p>
        </div>
      </section>

      <!-- 商品侧栏 -->
      <aside v-if="activeSession && goodsInfo" class="goods-side">
        <router-link :to="`/goods/${goodsInfo.id}`" class="goods-side__img">
          <img :src="goodsInfo.coverUrl || goodsInfo.images?.[0]" :alt="goodsInfo.title" />
        </router-link>
        <h3>{{ goodsInfo.title }}</h3>
        <div class="goods-side__price">
          <b class="num"><small>¥</small>{{ formatPrice(goodsInfo.price) }}</b>
          <s v-if="goodsInfo.originalPrice" class="num">¥{{ formatPrice(goodsInfo.originalPrice) }}</s>
        </div>
        <dl class="goods-side__kv">
          <div><dt>状态</dt><dd>{{ goodsStatusText }}</dd></div>
          <div><dt>成色</dt><dd>{{ enumLabel(GOODS_CONDITION_LABELS, goodsInfo.condition, '—') }}</dd></div>
          <div><dt>交易方式</dt><dd>{{ enumLabel(TRADE_METHOD_LABELS, goodsInfo.tradeMethod, '—') }}</dd></div>
          <div><dt>卖家信誉</dt><dd class="num">{{ goodsInfo.seller?.creditScore ?? '—' }}</dd></div>
        </dl>
        <router-link :to="`/goods/${goodsInfo.id}`" class="el-button el-button--large is-round ct-btn-ghost goods-side__btn">查看商品详情</router-link>
        <div class="goods-side__tip">
          <el-icon><Warning /></el-icon>
          <span>不要脱离平台私下转账，当面验货后再确认收货。</span>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useIntervalFn } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getChatMessages } from '@/api/chat'
import { getGoodsDetail } from '@/api/goods'
import { uploadFile } from '@/api/file'
import { ChatMessageType, GOODS_CONDITION_LABELS, GOODS_STATUS_LABELS, GoodsStatus, TRADE_METHOD_LABELS, enumLabel } from '@/constants/enums'
import { API_OFFLINE_TEXT, FORBIDDEN_TEXT, bizErrorText, isApiOffline, isForbidden } from '@/constants/errorCodes'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatPrice, formatRelativeTime } from '@/utils/format'
import { getStompClient, sendChat } from '@/utils/stomp'

const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()

const messages = ref([])
const pageNum = ref(1)
const pages = ref(1)
const hasOlder = computed(() => pageNum.value < pages.value)
const loadingOlder = ref(false)
const loading = ref(false)
const sending = ref(false)
const draft = ref('')
const listRef = ref()
const fileRef = ref()
const pendingScroll = ref(true)
// 批量加载（切换会话 / 加载更早消息）时不播放气泡入场动画，只有新消息才播放
const bulk = ref(true)
const keyword = ref('')
const sessionFilter = ref('all')
const goodsInfo = ref(null)
const goodsCache = new Map()
const QUICK_REPLIES = ['还在吗？', '可以小刀吗？', '什么时候方便面交？', '能再发几张实拍图吗？']

const filteredSessions = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return chatStore.sessions.filter((item) => {
    if (sessionFilter.value === 'unread' && !item.unreadCount) return false
    if (!kw) return true
    return [item.peer?.nickname, item.goodsTitle, item.lastContent].some((text) => String(text || '').toLowerCase().includes(kw))
  })
})

// 消息流 + 日期分隔胶囊
const timeline = computed(() => {
  const rows = []
  let lastDay = ''
  for (const item of messages.value) {
    const day = String(item.createTime || '').slice(0, 10)
    if (day && day !== lastDay) {
      rows.push({ kind: 'day', key: `day-${day}`, label: dayLabel(day) })
      lastDay = day
    }
    rows.push({ kind: 'msg', key: item._key || item.id, item })
  }
  return rows
})

function dayLabel(day) {
  const today = nowShanghai().slice(0, 10)
  if (day === today) return '今天'
  const y = new Date(`${today}T00:00:00`)
  y.setDate(y.getDate() - 1)
  const pad = (n) => String(n).padStart(2, '0')
  if (day === `${y.getFullYear()}-${pad(y.getMonth() + 1)}-${pad(y.getDate())}`) return '昨天'
  return day.replace(/^(\d{4})-(\d{2})-(\d{2})$/, '$2 月 $3 日')
}

function timeOf(item) {
  return String(item.createTime || '').slice(11, 16)
}

// 连接状态：只读 STOMP 客户端的 connected，不改动连接逻辑
const connState = ref('ok')
let connHideTimer = null
useIntervalFn(() => {
  if (!chatStore.started) return
  const connected = Boolean(getStompClient()?.connected)
  if (!connected && connState.value !== 'lost') {
    clearTimeout(connHideTimer)
    connState.value = 'lost'
  } else if (connected && connState.value === 'lost') {
    connState.value = 'back'
    connHideTimer = setTimeout(() => {
      connState.value = 'ok'
    }, 1500)
  }
}, 2000)

// 设计答复 #6：同一商品在整个页面生命周期内只请求一次（缓存 Promise，并发切换也不会重复请求）
function fetchGoodsCached(goodsId) {
  const key = String(goodsId)
  if (!goodsCache.has(key)) {
    const task = getGoodsDetail(key).catch((error) => {
      goodsCache.delete(key)
      throw error
    })
    goodsCache.set(key, task)
  }
  return goodsCache.get(key)
}

async function loadGoodsInfo(goodsId) {
  if (!goodsId) {
    goodsInfo.value = null
    return
  }
  const key = String(goodsId)
  goodsInfo.value = null
  try {
    const data = await fetchGoodsCached(key)
    if (String(activeSession.value?.goodsId) === key) goodsInfo.value = data
  } catch {
    goodsInfo.value = null
  }
}

const goodsStatusText = computed(() => {
  const status = goodsInfo.value?.status
  if (!status) return ''
  return status === GoodsStatus.ON_SHELF ? '在售' : enumLabel(GOODS_STATUS_LABELS, status)
})

function useQuickReply(text) {
  draft.value = text
}

const activeSession = computed(() => chatStore.currentSession)

function isMine(message) {
  return String(message.fromUserId) === String(userStore.userInfo?.id)
}

function nicknameOf(message) {
  if (isMine(message)) return userStore.userInfo?.nickname || '我'
  return activeSession.value?.peer?.nickname || '同学'
}

function avatarOf(message) {
  if (isMine(message)) return userStore.userInfo?.avatar
  return activeSession.value?.peer?.avatar
}

function parseGoodsCard(content) {
  if (!content) return null
  try {
    const data = typeof content === 'string' ? JSON.parse(content) : content
    if (data && data.goodsId) return data
  } catch {
    return null
  }
  return null
}

function goGoods(id) {
  if (id) router.push(`/goods/${id}`)
}

function nowShanghai() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date())
  const get = (type) => parts.find((part) => part.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}:${get('second')}`
}

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadMessages({ prepend = false } = {}) {
  const sessionId = chatStore.currentSessionId
  if (!sessionId) {
    messages.value = []
    return
  }
  if (prepend) {
    if (loadingOlder.value || !hasOlder.value) return
    loadingOlder.value = true
  } else {
    loading.value = true
    pageNum.value = 1
  }
  const targetPage = prepend ? pageNum.value + 1 : 1
  const el = listRef.value
  const prevHeight = el ? el.scrollHeight : 0
  try {
    const data = await getChatMessages(sessionId, { pageNum: targetPage, pageSize: PAGE_SIZE }, { silent: true })
    const list = data?.records || []
    pages.value = data?.pages || 1
    pageNum.value = data?.pageNum || targetPage
    bulk.value = true
    if (prepend) {
      messages.value = [...list, ...messages.value]
      await nextTick()
      if (el) el.scrollTop = el.scrollHeight - prevHeight
    } else {
      messages.value = list
      if (pendingScroll.value) await scrollToBottom()
    }
  } catch (error) {
    if (!prepend && isForbidden(error)) {
      messages.value = []
      ElMessage.warning(bizErrorText(error, FORBIDDEN_TEXT))
      chatStore.clearCurrentSession()
      if (window.history.state?.back) router.back()
      else router.replace({ path: '/chat' })
      return
    }
    if (!prepend && isApiOffline(error)) {
      messages.value = []
    }
  } finally {
    loading.value = false
    loadingOlder.value = false
    await nextTick()
    requestAnimationFrame(() => {
      bulk.value = false
    })
  }
}

function onScroll() {
  const el = listRef.value
  if (!el || el.scrollTop > 24) return
  loadMessages({ prepend: true })
}

async function openSession(sessionId) {
  const id = String(sessionId || '')
  if (!id) return
  if (route.query.sessionId !== id) {
    await router.replace({ path: '/chat', query: { sessionId: id } })
  }
  await chatStore.selectSession(id)
}

let syncSeq = 0

function hasSession(sessionId) {
  const id = String(sessionId || '')
  return chatStore.sessions.some((item) => String(item.id) === id)
}

async function syncFromQuery() {
  const seq = ++syncSeq
  if (!chatStore.started) {
    await chatStore.startRealtime()
    if (seq !== syncSeq) return
  }
  const sessionId = route.query.sessionId ? String(route.query.sessionId) : ''
  if (!sessionId) {
    if (chatStore.sessions[0]) {
      await openSession(chatStore.sessions[0].id)
    } else {
      chatStore.clearCurrentSession()
      messages.value = []
    }
    return
  }
  if (!hasSession(sessionId)) {
    try {
      await chatStore.loadSessions()
    } catch {
      // 列表刷新失败时沿用已有缓存，随后再判断是否找得到
    }
    if (seq !== syncSeq) return
  }
  if (!hasSession(sessionId)) {
    ElMessage.warning('未找到该会话，可能已被删除或无权查看')
    chatStore.clearCurrentSession()
    messages.value = []
    return
  }
  await chatStore.selectSession(sessionId)
}

async function sendPayload(payload, existed) {
  const session = activeSession.value
  if (!session) return
  const local = existed || {
    id: `local-${Date.now()}`,
    sessionId: session.id,
    fromUserId: userStore.userInfo?.id,
    toUserId: session.peer?.id,
    goodsId: session.goodsId,
    content: payload.content,
    type: payload.type,
    readFlag: 0,
    createTime: nowShanghai(),
    sending: true,
    failed: false,
  }
  local._key = local._key || local.id
  local.sending = true
  local.failed = false
  local._payload = payload
  if (!existed) {
    messages.value.push(local)
    await scrollToBottom()
  }
  sending.value = true
  if (local._ackTimer) clearTimeout(local._ackTimer)
  try {
    await sendChat({
      toUserId: session.peer.id,
      goodsId: session.goodsId,
      content: payload.content,
      type: payload.type,
    })
    // 保持 sending，等待 /user/queue/messages 回推或 /user/queue/errors
    local._ackTimer = setTimeout(() => {
      if (local.sending) {
        local.sending = false
        local.failed = true
        ElMessage.error('消息发送超时，请重试')
      }
    }, 15000)
    await chatStore.loadSessions()
  } catch (error) {
    local.sending = false
    local.failed = true
    ElMessage.error(error?.message || '发送失败，请重试')
  } finally {
    sending.value = false
  }
}

async function sendGoodsCard() {
  const session = activeSession.value
  if (!session?.goodsId || sending.value) return
  let card = {
    goodsId: session.goodsId,
    title: session.goodsTitle || '商品',
    coverUrl: session.goodsCover || '',
    price: session.goodsPrice ?? 0,
  }
  try {
    const goods = await fetchGoodsCached(session.goodsId)
    if (goods) {
      card = {
        goodsId: String(goods.id || session.goodsId),
        title: goods.title || card.title,
        coverUrl: goods.coverUrl || card.coverUrl,
        price: goods.price,
      }
    }
  } catch {
    // 商品详情不可用时仍按会话信息发送卡片
  }
  await sendPayload({ content: JSON.stringify(card), type: ChatMessageType.GOODS_CARD })
}

async function sendText() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  draft.value = ''
  await sendPayload({ content, type: ChatMessageType.TEXT })
}

async function onPickImage(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  sending.value = true
  try {
    const uploaded = await uploadFile(file)
    const url = uploaded?.url
    if (!url) throw new Error('图片上传失败')
    await sendPayload({ content: url, type: ChatMessageType.IMAGE })
  } catch (error) {
    ElMessage.error(error?.message || '图片发送失败')
  } finally {
    sending.value = false
  }
}

function retrySend(item) {
  if (!item?._payload) return
  sendPayload(item._payload, item)
}

watch(
  () => chatStore.currentSessionId,
  async (id, prev) => {
    if (!id || id === prev) return
    pendingScroll.value = true
    loadGoodsInfo(chatStore.currentSession?.goodsId)
    await loadMessages()
  },
  { immediate: true },
)

watch(
  () => chatStore.incomingMessage,
  async (message) => {
    if (!message) return
    if (message._kind === 'read') {
      const sessionId = String(message.sessionId || '')
      const readerId = String(message.readerId || '')
      const myId = String(userStore.userInfo?.id || '')
      // 只有 readerId 是对方时，才把我发出的消息标已读
      if (sessionId && sessionId === chatStore.currentSessionId && readerId && readerId !== myId) {
        messages.value = messages.value.map((item) =>
          isMine(item) ? { ...item, readFlag: 1 } : item,
        )
      }
      return
    }
    if (String(message.sessionId) !== String(chatStore.currentSessionId)) return
    if (messages.value.some((item) => item.id === message.id)) return
    if (isMine(message)) {
      const pending = messages.value.find((item) => item.sending || item.id.startsWith('local-'))
      if (pending && pending.content === message.content) {
        if (pending._ackTimer) clearTimeout(pending._ackTimer)
        Object.assign(pending, message, { sending: false, failed: false, _ackTimer: null })
        return
      }
    }
    messages.value.push(message)
    await scrollToBottom()
    if (!isMine(message)) {
      await chatStore.selectSession(chatStore.currentSessionId)
    }
  },
)

watch(
  () => chatStore.incomingError,
  (error) => {
    if (!error) return
    messages.value.forEach((item) => {
      if (!item.sending) return
      if (item._ackTimer) clearTimeout(item._ackTimer)
      item.sending = false
      item.failed = true
    })
  },
)

watch(
  () => (route.query.sessionId ? String(route.query.sessionId) : ''),
  () => {
    syncFromQuery()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  chatStore.clearCurrentSession()
})
</script>
<style scoped lang="scss">
.chat-page {
  padding: 20px 0 20px;
}

.chat-box {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  height: calc(100vh - var(--ct-header-h) - 40px);
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-3);

  &.has-side {
    grid-template-columns: 330px minmax(0, 1fr) 300px;
  }
}

/* ---------- 会话栏 ---------- */
.session-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--ct-border);
  background: var(--ct-surface-2);
}

.session-pane__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 22px 20px 14px;

  h2 {
    margin: 0;
    font-size: 22px;
    font-weight: 800;
  }
}

.session-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  margin: 0 16px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--ct-radius-pill);
  background: var(--ct-surface-sunken);
  color: var(--ct-text-3);
  transition:
    border-color var(--ct-dur-base),
    background var(--ct-dur-base),
    box-shadow var(--ct-dur-base);

  &:focus-within {
    border-color: var(--ct-primary);
    background: var(--ct-surface);
    box-shadow: var(--ct-ring);
  }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--ct-text);
    font: inherit;
    font-size: 13px;
  }
}

.session-filter {
  display: flex;
  gap: 8px;
  padding: 12px 16px 8px;
}

.session-scroll {
  flex: 1;
  min-height: 0;

  :deep(.el-scrollbar__view) {
    padding: 4px 10px 12px;
  }
}

.session-sk {
  display: flex;
  gap: 12px;
  padding: 12px;
}

.session-sk__av {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 50%;
}

.session-sk__lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;

  .ct-sk {
    height: 12px;
    border-radius: 6px;
  }
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 0;
  border-radius: 18px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--ct-dur-base),
    box-shadow var(--ct-dur-base);

  &::before {
    content: '';
    position: absolute;
    top: 14px;
    bottom: 14px;
    left: 0;
    width: 4px;
    border-radius: 4px;
    background: var(--ct-gradient-brand);
    opacity: 0;
    transform: scaleY(0.4);
    transition:
      opacity var(--ct-dur-base),
      transform var(--ct-dur-base) var(--ct-ease-spring);
  }

  &:hover {
    background: var(--ct-surface-sunken);
  }

  &.is-active {
    background: var(--ct-surface);
    box-shadow: var(--ct-shadow-1);

    &::before {
      opacity: 1;
      transform: none;
    }
  }

  .el-avatar {
    flex: none;
  }
}

.session-item__body {
  flex: 1;
  min-width: 0;
}

.session-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-item__name {
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__time {
  flex: none;
  color: var(--ct-text-3);
  font-size: 12px;
}

.session-item__preview,
.session-item__goods {
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--ct-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__goods {
  color: var(--ct-text-3);
  font-size: 12px;
}

.session-item__unread {
  flex: none;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--ct-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  animation: ct-badge-pop 0.4s var(--ct-ease-spring);
}

.session-item__thumb {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  object-fit: cover;
}

.session-none {
  margin: 0;
  padding: 32px 12px;
  color: var(--ct-text-3);
  font-size: 13px;
  text-align: center;
}

/* ---------- 主会话区 ---------- */
.chat-main {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.conn-bar {
  position: absolute;
  top: 72px;
  right: 0;
  left: 0;
  z-index: 3;
  padding: 6px;
  font-size: 12px;
  font-weight: 600;
  text-align: center;

  &.is-lost {
    background: var(--ct-sun-soft);
    color: var(--ct-sun-ink);
  }

  &.is-back {
    background: var(--ct-mint-soft);
    color: var(--ct-mint);
  }
}

.conn-enter-active,
.conn-leave-active {
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base);
}

.conn-enter-from,
.conn-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}

.chat-main__head {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 72px;
  padding: 0 22px;
  border-bottom: 1px solid var(--ct-border);
}

.chat-main__who {
  flex: 1;
  min-width: 0;

  strong {
    font-size: 16px;
  }

  p {
    margin: 2px 0 0;
    overflow: hidden;
    color: var(--ct-text-3);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.pin-goods {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 18px 0;
  padding: 10px 14px 10px 10px;
  border: 1px solid var(--ct-border);
  border-radius: 18px;
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);

  img {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    object-fit: cover;
  }

  b {
    color: var(--ct-price);
    font-size: 15px;
  }
}

.pin-goods__body {
  flex: 1;
  min-width: 0;

  p {
    margin: 0;
    overflow: hidden;
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.pin-goods__go {
  color: var(--ct-primary);
  font-size: 13px;
  font-weight: 600;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 24px 8px;
  background-image: radial-gradient(var(--ct-border) 1px, transparent 1px);
  background-size: 18px 18px;
  overscroll-behavior: contain;
}

.message-list__more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 0 12px;
  color: var(--ct-text-3);
  font-size: 12px;

  .ct-spinner {
    width: 14px;
    height: 14px;
    border-color: var(--ct-primary-soft-2);
    border-top-color: var(--ct-primary);
  }
}

.msg-sk {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 12px;
}

.msg-sk__b {
  width: 42%;
  height: 44px;
  border-radius: 20px 20px 20px 6px;

  &.is-mine {
    align-self: flex-end;
    width: 34%;
    border-radius: 20px 20px 6px 20px;
  }
}

.day-pill {
  display: flex;
  justify-content: center;
  margin: 10px 0 14px;

  span {
    padding: 3px 12px;
    border-radius: var(--ct-radius-pill);
    background: var(--ct-surface-sunken);
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 14px;

  &.is-mine {
    flex-direction: row-reverse;
  }

  .el-avatar {
    flex: none;
  }
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 64%;

  .is-mine & {
    align-items: flex-end;
  }
}

.bubble-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 设计答复 #6：对方气泡 surface 底 + 1px border + shadow-1，圆角 20 20 20 6；我的气泡品牌渐变无描边 */
.bubble {
  padding: 11px 15px;
  border: 1px solid var(--ct-border);
  border-radius: 20px 20px 20px 6px;
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);
  color: var(--ct-text);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  transition: opacity var(--ct-dur-base);

  &.is-mine {
    border-color: transparent;
    border-radius: 20px 20px 6px 20px;
    background: var(--ct-gradient-brand);
    box-shadow: var(--ct-shadow-glow);
    color: #fff;
  }

  &.is-type-2,
  &.is-type-3 {
    padding: 6px;
  }

  &.is-sending {
    opacity: 0.85;
  }

  &.is-failed {
    opacity: 0.7;
  }

  :deep(.el-image) {
    display: block;
    width: 180px;
    height: 180px;
    overflow: hidden;
    border-radius: 14px;
  }
}

.goods-chip {
  display: flex;
  gap: 10px;
  width: 240px;
  padding: 4px;
  border: 0;
  border-radius: 14px;
  background: var(--ct-surface-2);
  color: inherit;
  text-align: left;
  cursor: pointer;

  img {
    flex-shrink: 0;
    width: 64px;
    height: 64px;
    border-radius: 10px;
    object-fit: cover;
  }

  p {
    margin: 2px 0 6px;
    color: var(--ct-text);
    font-size: 13px;
    font-weight: 600;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  strong {
    color: var(--ct-price);
  }
}

.bubble.is-mine .goods-chip {
  background: rgba(255, 255, 255, 0.16);

  p,
  strong {
    color: #fff;
  }
}

.bubble-meta {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  color: var(--ct-text-3);
  font-size: 11px;

  .is-read {
    color: var(--ct-mint);
  }

  .is-err {
    color: var(--el-color-danger);
  }
}

.retry {
  display: grid;
  flex: none;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--el-color-danger);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

/* 气泡入场：我的从右下、对方从左下弹入，420ms spring */
.bubble-enter-active {
  transition:
    opacity 0.42s var(--ct-ease-out),
    transform 0.42s var(--ct-ease-spring);
}

.bubble-enter-from {
  opacity: 0;
  transform: translate(-12px, 10px) scale(0.92);
  transform-origin: left bottom;

  &.is-mine {
    transform: translate(12px, 10px) scale(0.92);
    transform-origin: right bottom;
  }
}

.quick {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 10px 22px 0;
  scrollbar-width: none;

  .ct-chip {
    flex: none;
  }
}

.composer {
  padding: 10px 18px 16px;
}

.composer__file {
  display: none;
}

.composer__box {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 6px 6px 6px 10px;
  border: 1px solid var(--ct-border);
  border-radius: 24px;
  background: var(--ct-surface-2);
  transition:
    border-color var(--ct-dur-base),
    box-shadow var(--ct-dur-base);

  &:focus-within {
    border-color: var(--ct-primary);
    box-shadow: var(--ct-ring);
  }
}

.composer__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--ct-text-2);
  cursor: pointer;
  transition:
    background var(--ct-dur-fast),
    color var(--ct-dur-fast);

  &:hover {
    background: var(--ct-primary-soft);
    color: var(--ct-primary);
  }
}

.composer__sep {
  align-self: center;
  width: 1px;
  height: 20px;
  margin: 0 6px;
  background: var(--ct-border-strong);
}

.composer__input {
  flex: 1;

  :deep(.el-textarea__inner) {
    padding: 8px 4px;
    border: 0;
    background: transparent;
    box-shadow: none !important;
    resize: none;
  }
}

.composer__send.el-button {
  flex: none;
  width: 40px;
  height: 40px;
  border: 0;
  background: var(--ct-gradient-brand);
  box-shadow: var(--ct-shadow-glow);
  font-size: 17px;
  transition:
    transform var(--ct-dur-fast) var(--ct-ease-spring),
    opacity var(--ct-dur-fast);

  &:not(.is-disabled):hover {
    transform: scale(1.06);
  }

  &:not(.is-disabled):active {
    transform: scale(0.94);
  }
}

.chat-empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--ct-text-3);
  background-image: radial-gradient(var(--ct-border) 1px, transparent 1px);
  background-size: 18px 18px;

  p {
    margin: 0;
  }
}

.chat-empty__art {
  position: relative;
  width: 120px;
  height: 90px;

  span {
    position: absolute;
    display: block;
    border-radius: 22px 22px 22px 6px;
  }

  .b1 {
    top: 0;
    left: 0;
    width: 84px;
    height: 48px;
    background: var(--ct-gradient-brand);
    animation: ct-float-y 4s ease-in-out infinite;
  }

  .b2 {
    right: 0;
    bottom: 0;
    width: 70px;
    height: 40px;
    border: 1px solid var(--ct-border);
    border-radius: 22px 22px 6px 22px;
    background: var(--ct-surface);
    box-shadow: var(--ct-shadow-2);
    animation: ct-float-y 4s ease-in-out -2s infinite;
  }
}

/* ---------- 商品侧栏 ---------- */
.goods-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  border-left: 1px solid var(--ct-border);

  h3 {
    margin: 4px 0 0;
    font-size: 16px;
    font-weight: 800;
    line-height: 1.4;
  }
}

.goods-side__img {
  display: block;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 20px;
  background: var(--ct-surface-sunken);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.6s var(--ct-ease-out);
  }

  &:hover img {
    transform: scale(1.05);
  }
}

.goods-side__price {
  display: flex;
  align-items: baseline;
  gap: 8px;

  b {
    color: var(--ct-price);
    font-size: 24px;
    font-weight: 800;
  }

  small {
    font-size: 15px;
  }

  s {
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.goods-side__kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 0;

  div {
    padding: 10px 12px;
    border-radius: 14px;
    background: var(--ct-surface-2);
  }

  dt {
    color: var(--ct-text-3);
    font-size: 11px;
  }

  dd {
    margin: 2px 0 0;
    font-size: 13px;
    font-weight: 600;
  }
}

.goods-side__btn {
  width: 100%;
  margin: 0;
}

.goods-side__tip {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  background: var(--ct-sun-soft);
  color: var(--ct-text-2);
  font-size: 12px;
  line-height: 1.6;

  .el-icon {
    flex: none;
    margin-top: 3px;
    color: var(--ct-sun-ink);
  }
}

@media (max-width: 1279px) {
  .chat-box.has-side {
    grid-template-columns: 300px minmax(0, 1fr);
  }

  .goods-side {
    display: none;
  }
}

@media (max-width: 768px) {
  .chat-page {
    padding: 0;
  }

  .chat-box,
  .chat-box.has-side {
    grid-template-columns: 1fr;
    height: calc(100vh - var(--ct-header-h));
    border-radius: 0;
  }

  .chat-box.has-active .session-pane,
  .chat-box:not(.has-active) .chat-main {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bubble-enter-active {
    transition: opacity var(--ct-dur-fast);
  }

  .bubble-enter-from,
  .bubble-enter-from.is-mine {
    transform: none;
  }

  .chat-empty__art span,
  .session-item__unread {
    animation: none;
  }
}
</style>
