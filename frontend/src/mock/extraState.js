import { ChatMessageType, OrderStatus } from '@/constants/enums'
import { CURRENT_USER_ID, categories, goodsList, users } from '@/mock/data'

function shanghaiTime(msAgo = 0) {
  const date = new Date(Date.now() - msAgo)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type) => parts.find((part) => part.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}:${get('second')}`
}

const hour = 60 * 60 * 1000
const minute = 60 * 1000

export const INTEREST_MAX = 5
export let myInterestIds = ['1910000000000000001', '1910000000000000002']

export function getMyInterestCategories() {
  return myInterestIds
    .map((id) => categories.find((item) => item.id === id))
    .filter(Boolean)
    .map((item) => ({ id: item.id, parentId: item.parentId ?? null, name: item.name, sort: item.sort }))
}

export function setMyInterestIds(ids) {
  const unique = []
  for (const id of ids || []) {
    const text = String(id)
    if (!unique.includes(text)) unique.push(text)
  }
  if (unique.length > INTEREST_MAX) {
    const error = new Error(`最多选择 ${INTEREST_MAX} 个兴趣分类`)
    error.code = 400
    throw error
  }
  myInterestIds = unique
  return getMyInterestCategories()
}

function userOf(id) {
  return users.find((item) => item.id === id)
}

function goodsOf(id) {
  return goodsList.find((item) => item.id === id)
}

function peerVO(id) {
  const user = userOf(id)
  return { id: user.id, nickname: user.nickname, avatar: user.avatar }
}

export const chatSessions = [
  {
    id: '1950000000000000001',
    goodsId: '1920000000000000001',
    peerId: '1930000000000000002',
    lastContent: '方便今天下午当面验机吗？',
    lastTime: shanghaiTime(8 * minute),
    unreadCount: 2,
  },
  {
    id: '1950000000000000002',
    goodsId: '1920000000000000002',
    peerId: '1930000000000000003',
    lastContent: '[商品]',
    lastTime: shanghaiTime(3 * hour),
    unreadCount: 0,
  },
  {
    id: '1950000000000000003',
    goodsId: '1920000000000000010',
    peerId: '1930000000000000004',
    lastContent: '[图片]',
    lastTime: shanghaiTime(26 * hour),
    unreadCount: 1,
  },
]

/** 每条会话的消息按时间从旧到新 */
export const messagesBySession = {
  '1950000000000000001': [],
  '1950000000000000002': [],
  '1950000000000000003': [],
}

function msg({ id, sessionId, from, to, goodsId, content, type, hoursAgo, minutesAgo = 0, readFlag = 1 }) {
  return {
    id,
    sessionId,
    fromUserId: from,
    toUserId: to,
    goodsId,
    content,
    type,
    readFlag,
    createTime: shanghaiTime(hoursAgo * hour + minutesAgo * minute),
  }
}

const me = CURRENT_USER_ID
const li = '1930000000000000002'
const wang = '1930000000000000003'
const chen = '1930000000000000004'
const mac = '1920000000000000001'
const book = '1920000000000000002'
const camera = '1920000000000000010'

messagesBySession['1950000000000000001'] = [
  msg({ id: '1960000000000000001', sessionId: '1950000000000000001', from: me, to: li, goodsId: mac, content: '你好，这台还在吗？成色看起来不错。', type: ChatMessageType.TEXT, hoursAgo: 6 }),
  msg({ id: '1960000000000000002', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '在的，下午都可以当面看。', type: ChatMessageType.TEXT, hoursAgo: 5, minutesAgo: 40 }),
  msg({ id: '1960000000000000003', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '/mock/g01.jpg', type: ChatMessageType.IMAGE, hoursAgo: 5, minutesAgo: 35 }),
  msg({
    id: '1960000000000000004',
    sessionId: '1950000000000000001',
    from: me,
    to: li,
    goodsId: mac,
    content: JSON.stringify({ goodsId: mac, title: '【演示】MacBook Air 2020 13 寸 酷睿 i5 16G+256G', coverUrl: '/mock/g01.jpg', price: 3299 }),
    type: ChatMessageType.GOODS_CARD,
    hoursAgo: 5,
    minutesAgo: 20,
  }),
  msg({ id: '1960000000000000005', sessionId: '1950000000000000001', from: me, to: li, goodsId: mac, content: '电池健康还够用吗？能拆机看一下吗？', type: ChatMessageType.TEXT, hoursAgo: 4 }),
  msg({ id: '1960000000000000006', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '大约 87%，可以当面给你看系统信息。', type: ChatMessageType.TEXT, hoursAgo: 3, minutesAgo: 50 }),
  msg({ id: '1960000000000000007', sessionId: '1950000000000000001', from: me, to: li, goodsId: mac, content: '好的，那我再看看配置。', type: ChatMessageType.TEXT, hoursAgo: 3 }),
  msg({ id: '1960000000000000008', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '/mock/g08.jpg', type: ChatMessageType.IMAGE, hoursAgo: 2, minutesAgo: 40 }),
  msg({ id: '1960000000000000009', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '充电器原装的，一起给你。', type: ChatMessageType.TEXT, hoursAgo: 2 }),
  msg({ id: '1960000000000000010', sessionId: '1950000000000000001', from: me, to: li, goodsId: mac, content: '价格还能再商量一点吗？', type: ChatMessageType.TEXT, hoursAgo: 1, minutesAgo: 20 }),
  msg({ id: '1960000000000000011', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '最低 3200，当面交易。', type: ChatMessageType.TEXT, hoursAgo: 1 }),
  msg({ id: '1960000000000000012', sessionId: '1950000000000000001', from: me, to: li, goodsId: mac, content: '行，我争取今天过去。', type: ChatMessageType.TEXT, hoursAgo: 0, minutesAgo: 40 }),
  msg({ id: '1960000000000000013', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '图书馆北门方便，我五点后都在。', type: ChatMessageType.TEXT, hoursAgo: 0, minutesAgo: 22, readFlag: 0 }),
  msg({ id: '1960000000000000014', sessionId: '1950000000000000001', from: li, to: me, goodsId: mac, content: '方便今天下午当面验机吗？', type: ChatMessageType.TEXT, hoursAgo: 0, minutesAgo: 8, readFlag: 0 }),
]

messagesBySession['1950000000000000002'] = [
  msg({ id: '1960000000000000021', sessionId: '1950000000000000002', from: me, to: wang, goodsId: book, content: '这套书还有笔记吗？', type: ChatMessageType.TEXT, hoursAgo: 8 }),
  msg({ id: '1960000000000000022', sessionId: '1950000000000000002', from: wang, to: me, goodsId: book, content: '几乎没有笔记，内页干净。', type: ChatMessageType.TEXT, hoursAgo: 7 }),
  msg({
    id: '1960000000000000023',
    sessionId: '1950000000000000002',
    from: wang,
    to: me,
    goodsId: book,
    content: JSON.stringify({ goodsId: book, title: '【演示】考研数学复习全书 + 660 题，几乎全新', coverUrl: '/mock/g02.jpg', price: 28 }),
    type: ChatMessageType.GOODS_CARD,
    hoursAgo: 3,
  }),
]

messagesBySession['1950000000000000003'] = [
  msg({ id: '1960000000000000031', sessionId: '1950000000000000003', from: chen, to: me, goodsId: camera, content: '相机快门数我待会拍给你。', type: ChatMessageType.TEXT, hoursAgo: 30 }),
  msg({ id: '1960000000000000032', sessionId: '1950000000000000003', from: chen, to: me, goodsId: camera, content: '/mock/g10.jpg', type: ChatMessageType.IMAGE, hoursAgo: 26, readFlag: 0 }),
]

let messageSeq = 100
let sessionSeq = 4

export function toChatSessionVO(session) {
  const goods = goodsOf(session.goodsId)
  const peer = peerVO(session.peerId)
  return {
    id: session.id,
    goodsId: session.goodsId,
    goodsTitle: goods?.title || '',
    goodsCover: goods?.coverUrl || '',
    peer,
    lastContent: session.lastContent || '',
    lastTime: session.lastTime || '',
    unreadCount: session.unreadCount || 0,
  }
}

export function listChatSessions() {
  return chatSessions
    .slice()
    .sort((a, b) => (a.lastTime < b.lastTime ? 1 : -1))
    .map(toChatSessionVO)
}

export function findSessionById(id) {
  return chatSessions.find((item) => item.id === String(id))
}

export function findSessionByPeerGoods(peerId, goodsId) {
  return chatSessions.find((item) => item.peerId === String(peerId) && item.goodsId === String(goodsId))
}

export function ensureSession(peerId, goodsId) {
  let session = findSessionByPeerGoods(peerId, goodsId)
  if (!session) {
    session = {
      id: `1950000000000000${String(sessionSeq).padStart(3, '0')}`,
      goodsId: String(goodsId),
      peerId: String(peerId),
      lastContent: '',
      lastTime: shanghaiTime(0),
      unreadCount: 0,
    }
    sessionSeq += 1
    chatSessions.unshift(session)
    messagesBySession[session.id] = []
  }
  return session
}

export function previewOfMessage(message) {
  if (message.type === ChatMessageType.IMAGE) return '[图片]'
  if (message.type === ChatMessageType.GOODS_CARD) return '[商品]'
  return message.content
}

export function appendChatMessage({ sessionId, fromUserId, toUserId, goodsId, content, type, readFlag = 1 }) {
  messageSeq += 1
  const message = {
    id: `1960000000000000${String(messageSeq).padStart(3, '0')}`,
    sessionId: String(sessionId),
    fromUserId: String(fromUserId),
    toUserId: String(toUserId),
    goodsId: String(goodsId || ''),
    content,
    type,
    readFlag,
    createTime: shanghaiTime(0),
  }
  if (!messagesBySession[message.sessionId]) messagesBySession[message.sessionId] = []
  messagesBySession[message.sessionId].push(message)
  const session = findSessionById(message.sessionId)
  if (session) {
    session.lastContent = previewOfMessage(message)
    session.lastTime = message.createTime
    if (fromUserId !== CURRENT_USER_ID) {
      session.unreadCount = (session.unreadCount || 0) + 1
    }
  }
  return message
}

/** pageNum=1 表示最新一页，向上翻页加载更早的消息；页内按时间正序 */
export function pageChatMessages(sessionId, params = {}) {
  const all = messagesBySession[String(sessionId)] || []
  const pageNum = Math.max(1, Number(params.pageNum) || 1)
  const pageSize = Math.min(100, Math.max(1, Number(params.pageSize) || 10))
  const total = all.length
  const pages = Math.max(1, Math.ceil(total / pageSize) || 1)
  const end = total - (pageNum - 1) * pageSize
  const start = Math.max(0, end - pageSize)
  const records = end <= 0 ? [] : all.slice(start, Math.max(start, end))
  return { records, total, pageNum, pageSize, pages }
}

export function markChatSessionRead(sessionId) {
  const session = findSessionById(sessionId)
  if (session) session.unreadCount = 0
  const list = messagesBySession[String(sessionId)] || []
  list.forEach((item) => {
    if (item.toUserId === CURRENT_USER_ID) item.readFlag = 1
  })
  return true
}

export const orderRecords = [
  {
    id: '1940000000000000001',
    goodsId: '1920000000000000005',
    goodsTitle: '【演示】头戴式降噪耳机，带收纳盒',
    goodsCover: '/mock/g05.jpg',
    price: 199,
    status: OrderStatus.PENDING,
    remark: '希望尽量带上收纳盒',
    cancelReason: '',
    payTime: null,
    completeTime: null,
    reviewed: false,
    buyerId: CURRENT_USER_ID,
    sellerId: '1930000000000000002',
    createTime: shanghaiTime(2 * hour),
  },
  {
    id: '1940000000000000002',
    goodsId: '1920000000000000007',
    goodsTitle: '【演示】Air Force 1 白色运动鞋 42 码',
    goodsCover: '/mock/g07.jpg',
    price: 268,
    status: OrderStatus.PAID,
    remark: '',
    cancelReason: '',
    payTime: shanghaiTime(20 * hour),
    completeTime: null,
    reviewed: false,
    buyerId: CURRENT_USER_ID,
    sellerId: '1930000000000000004',
    createTime: shanghaiTime(30 * hour),
  },
  {
    id: '1940000000000000003',
    goodsId: '1920000000000000002',
    goodsTitle: '【演示】考研数学复习全书 + 660 题，几乎全新',
    goodsCover: '/mock/g02.jpg',
    price: 28,
    status: OrderStatus.COMPLETED,
    remark: '',
    cancelReason: '',
    payTime: shanghaiTime(4.5 * 24 * hour),
    completeTime: shanghaiTime(4 * 24 * hour),
    reviewed: false,
    buyerId: CURRENT_USER_ID,
    sellerId: '1930000000000000003',
    createTime: shanghaiTime(5 * 24 * hour),
  },
  {
    id: '1940000000000000004',
    goodsId: '1920000000000000008',
    goodsTitle: '【演示】87 键机械键盘 红轴，键帽可换',
    goodsCover: '/mock/g08.jpg',
    price: 159,
    status: OrderStatus.CANCELLED,
    remark: '',
    cancelReason: '计划有变，暂时不买了',
    payTime: null,
    completeTime: null,
    reviewed: false,
    buyerId: CURRENT_USER_ID,
    sellerId: '1930000000000000002',
    createTime: shanghaiTime(8 * 24 * hour),
  },
  {
    id: '1940000000000000005',
    goodsId: '1920000000000000006',
    goodsTitle: '【演示】24 寸登机行李箱，可扩容',
    goodsCover: '/mock/g06.jpg',
    price: 120,
    status: OrderStatus.PENDING,
    remark: '周末方便自提',
    cancelReason: '',
    payTime: null,
    completeTime: null,
    reviewed: false,
    buyerId: '1930000000000000003',
    sellerId: CURRENT_USER_ID,
    createTime: shanghaiTime(5 * hour),
  },
  {
    id: '1940000000000000006',
    goodsId: '1920000000000000006',
    goodsTitle: '【演示】24 寸登机行李箱，可扩容',
    goodsCover: '/mock/g06.jpg',
    price: 120,
    status: OrderStatus.PAID,
    remark: '',
    cancelReason: '',
    payTime: shanghaiTime(18 * hour),
    completeTime: null,
    reviewed: false,
    buyerId: '1930000000000000004',
    sellerId: CURRENT_USER_ID,
    createTime: shanghaiTime(40 * hour),
  },
  {
    id: '1940000000000000007',
    goodsId: '1920000000000000012',
    goodsTitle: '【演示】卡坦岛桌游，配件齐全少玩',
    goodsCover: '/mock/g12.jpg',
    price: 88,
    status: OrderStatus.COMPLETED,
    remark: '',
    cancelReason: '',
    payTime: shanghaiTime(11 * 24 * hour),
    completeTime: shanghaiTime(10 * 24 * hour),
    reviewed: true,
    buyerId: '1930000000000000002',
    sellerId: CURRENT_USER_ID,
    createTime: shanghaiTime(12 * 24 * hour),
  },
]

export const reviewRecords = [
  {
    id: '1970000000000000001',
    orderId: '1940000000000000007',
    fromUserId: '1930000000000000002',
    score: 5,
    content: '卖家沟通及时，东西和描述一致。',
    createTime: shanghaiTime(10 * 24 * hour),
  },
]

export function toReviewVO(review) {
  const user = userOf(review.fromUserId) || userOf(CURRENT_USER_ID)
  return {
    id: review.id,
    orderId: review.orderId,
    fromUser: user
      ? { id: user.id, nickname: user.nickname, avatar: user.avatar }
      : { id: review.fromUserId, nickname: '同学', avatar: '' },
    score: review.score,
    content: review.content,
    createTime: review.createTime,
  }
}

export function toOrderVO(order) {
  return {
    id: order.id,
    goodsId: order.goodsId,
    goodsTitle: order.goodsTitle,
    goodsCover: order.goodsCover,
    buyer: peerVO(order.buyerId),
    seller: peerVO(order.sellerId),
    price: order.price,
    remark: order.remark || '',
    status: order.status,
    cancelReason: order.cancelReason || '',
    payTime: order.payTime || null,
    completeTime: order.completeTime || null,
    createTime: order.createTime,
    reviewed: Boolean(order.reviewed),
  }
}

export function listOrders({ role, status }) {
  const mine = orderRecords.filter((item) => (role === 'seller' ? item.sellerId : item.buyerId) === CURRENT_USER_ID)
  const filtered = status ? mine.filter((item) => item.status === status) : mine
  return filtered
    .slice()
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map((item) => toOrderVO(item))
}

export function getOrderById(id) {
  const order = orderRecords.find((item) => item.id === String(id))
  if (!order) return null
  return toOrderVO(order)
}


function nextMockId(prefix, seq) {
  return `${prefix}${String(seq).padStart(3, '0')}`
}

let announcementSeq = 3
let reportSeq = 2

export const announcements = [
  {
    id: '9100000000000000001',
    title: '欢迎使用校园二手交易平台',
    content: '请优先选择校内当面交易，注意保护个人信息与财产安全。',
    status: 'PUBLISHED',
    publisherId: '1930000000000000009',
    publishTime: shanghaiTime(24 * hour),
    createTime: shanghaiTime(26 * hour),
    updateTime: shanghaiTime(24 * hour),
  },
  {
    id: '9100000000000000002',
    title: '毕业季跳蚤市场（草稿）',
    content: '时间地点待定。',
    status: 'DRAFT',
    publisherId: '1930000000000000009',
    publishTime: null,
    createTime: shanghaiTime(20 * hour),
    updateTime: shanghaiTime(20 * hour),
  },
]

export const reports = [
  {
    id: '9000000000000000001',
    reporterId: '1930000000000000002',
    targetType: 'GOODS',
    targetId: '1920000000000000010',
    targetName: '',
    reason: '描述不符',
    detail: '图片与实物颜色不一致',
    status: 'PENDING',
    result: null,
    remark: '',
    auditorId: null,
    auditTime: null,
    createTime: shanghaiTime(12 * hour),
  },
]

function reportTargetName(item) {
  if (item.targetName) return item.targetName
  if (item.targetType === 'USER') {
    const user = users.find((row) => row.id === String(item.targetId))
    return user?.nickname || item.targetId
  }
  const goods = goodsList.find((row) => row.id === String(item.targetId))
  return goods?.title || item.targetId
}

function reporterVO(id) {
  const user = users.find((row) => row.id === String(id))
  return user
    ? { id: user.id, nickname: user.nickname, avatar: user.avatar }
    : { id, nickname: '同学', avatar: '' }
}

export function toReportVO(item) {
  return {
    id: item.id,
    reporter: reporterVO(item.reporterId),
    targetType: item.targetType,
    targetId: item.targetId,
    targetName: reportTargetName(item),
    reason: item.reason,
    detail: item.detail || '',
    status: item.status,
    result: item.result,
    remark: item.remark || '',
    auditorId: item.auditorId,
    auditTime: item.auditTime,
    createTime: item.createTime,
  }
}

export function listPublishedAnnouncements() {
  return announcements
    .filter((item) => item.status === 'PUBLISHED')
    .slice()
    .sort((a, b) => String(b.publishTime || '') > String(a.publishTime || '') ? 1 : -1)
}

export function getPublishedAnnouncement(id) {
  const item = announcements.find((row) => row.id === String(id))
  if (!item || item.status !== 'PUBLISHED') return null
  return item
}

export function listAdminAnnouncements(query = {}) {
  let list = announcements.slice()
  if (query.status) list = list.filter((item) => item.status === query.status)
  list.sort((a, b) => String(b.updateTime || b.createTime || '') > String(a.updateTime || a.createTime || '') ? 1 : -1)
  return list
}

export function getAnnouncementById(id) {
  return announcements.find((item) => item.id === String(id)) || null
}

export function createAnnouncement({ title, content, status, publisherId }) {
  const now = shanghaiTime(0)
  const item = {
    id: nextMockId('9100000000000000', announcementSeq),
    title,
    content,
    status: status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    publisherId,
    publishTime: status === 'PUBLISHED' ? now : null,
    createTime: now,
    updateTime: now,
  }
  announcementSeq += 1
  announcements.unshift(item)
  return item
}

export function updateAnnouncement(id, { title, content }) {
  const item = getAnnouncementById(id)
  if (!item) return null
  item.title = title
  item.content = content
  item.updateTime = shanghaiTime(0)
  return item
}

export function updateAnnouncementStatus(id, status) {
  const item = getAnnouncementById(id)
  if (!item) return null
  item.status = status
  if (status === 'PUBLISHED' && !item.publishTime) item.publishTime = shanghaiTime(0)
  item.updateTime = shanghaiTime(0)
  return item
}

export function deleteAnnouncement(id) {
  const index = announcements.findIndex((item) => item.id === String(id))
  if (index < 0) return false
  announcements.splice(index, 1)
  return true
}

export function listAdminReports(query = {}) {
  let list = reports.slice()
  if (query.status) list = list.filter((item) => item.status === query.status)
  list.sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  return list.map(toReportVO)
}

export function createUserReport({ reporterId, targetType, targetId, reason, detail }) {
  if (String(reporterId) === String(targetId) && targetType === 'USER') {
    const error = new Error('不能举报自己或自己的商品')
    error.code = 60005
    throw error
  }
  if (targetType === 'GOODS') {
    const goods = goodsList.find((row) => row.id === String(targetId))
    if (goods?.seller?.id && String(goods.seller.id) === String(reporterId)) {
      const error = new Error('不能举报自己或自己的商品')
      error.code = 60005
      throw error
    }
  }
  const pending = reports.find(
    (item) =>
      item.reporterId === String(reporterId)
      && item.targetType === targetType
      && item.targetId === String(targetId)
      && item.status === 'PENDING',
  )
  if (pending) {
    const error = new Error('已举报，请等待处理')
    error.code = 60006
    throw error
  }
  const now = shanghaiTime(0)
  const item = {
    id: nextMockId('9000000000000000', reportSeq),
    reporterId: String(reporterId),
    targetType,
    targetId: String(targetId),
    targetName: '',
    reason,
    detail: detail || '',
    status: 'PENDING',
    result: null,
    remark: '',
    auditorId: null,
    auditTime: null,
    createTime: now,
  }
  reportSeq += 1
  reports.unshift(item)
  return toReportVO(item)
}

export function handleReport(id, { result, remark, auditorId }) {
  const item = reports.find((row) => row.id === String(id))
  if (!item) {
    const error = new Error('举报不存在')
    error.code = 60010
    throw error
  }
  if (item.status !== 'PENDING') {
    const error = new Error('举报已处理')
    error.code = 60007
    throw error
  }
  item.result = result
  item.status = result === 'APPROVE' ? 'APPROVED' : 'REJECTED'
  item.remark = remark || ''
  item.auditorId = auditorId || null
  item.auditTime = shanghaiTime(0)
  return toReportVO(item)
}
