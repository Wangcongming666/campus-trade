import { ElMessage } from 'element-plus'
import {
  GoodsSort,
  GoodsStatus,
  OrderAction,
  OrderRole,
  OrderStatus,
  Role,
  StudentAuthStatus,
  UserStatus,
  AuditResult,
} from '@/constants/enums'
import {
  CURRENT_USER_ID,
  categories,
  goodsList,
  recommendHomeIds,
  users,
} from '@/mock/data'
import {
  INTEREST_MAX,
  appendChatMessage,
  createAnnouncement,
  createUserReport,
  deleteAnnouncement,
  ensureSession,
  findSessionById,
  getAnnouncementById,
  getMyInterestCategories,
  getOrderById,
  getPublishedAnnouncement,
  handleReport,
  listAdminAnnouncements,
  listAdminReports,
  listChatSessions,
  listOrders,
  listPublishedAnnouncements,
  markChatSessionRead,
  myInterestIds,
  orderRecords,
  pageChatMessages,
  reviewRecords,
  setMyInterestIds,
  toChatSessionVO,
  toOrderVO,
  toReviewVO,
  updateAnnouncement,
  updateAnnouncementStatus,
} from '@/mock/extraState'
import {
  auditGoodsItem,
  auditStudentVerifyItem,
  forceAdminGoodsStatus,
  getAdminStats,
  getPendingAuditCounts,
  listAdminGoods,
  listAdminUsers,
  listAuditGoods,
  listStudentVerifies,
  updateAdminUserStatus,
} from '@/mock/adminState'

/* ---------- 推荐理由（mock）：文案集与后端规范第 9 节一致 ---------- */
function shortTitle(title = '') {
  const t = String(title).split(/\s+/)[0] || '商品'
  return t.length > 10 ? `${t.slice(0, 10)}…` : t
}

function mockHomeReason(item, index, preferred) {
  // 按位置轮换 CF / INTEREST / HOT，保证首屏 4 张就能看到三种样式；命中兴趣分类时用「你关注了」
  if (index % 3 === 1) {
    const interestHit = preferred.has(item.categoryId) || [...preferred].some((id) => collectCategoryIds(id).has(item.categoryId))
    return { reason: `${interestHit ? '你关注了' : '你常逛'}${item.categoryName || '这个分类'}`, reasonType: 'INTEREST' }
  }
  if (index % 3 === 0) {
    const verbs = ['浏览过', '收藏过', '咨询过', '买过']
    // 第 4 条用完整标题，文案较长，用来检查单行省略 + title 全文
    const title = index === 3 ? item.title : shortTitle(item.title)
    return { reason: `你${verbs[(index / 3) % verbs.length]}类似的${title}`.slice(0, 30), reasonType: 'CF' }
  }
  return { reason: index % 2 ? '最新发布' : '本周热门', reasonType: 'HOT' }
}

const DELAY = 280
const captchaStore = new Map()
const favoriteIds = new Set(['1920000000000000001', '1920000000000000005', '1920000000000000007'])
let goodsSeq = 20
let refreshSeq = 1

function wait(data) {
  return new Promise((resolve) => {
    setTimeout(() => resolve(clone(data)), DELAY)
  })
}

function fail(msg, code = 400) {
  ElMessage.error(msg)
  return Promise.reject({ code, msg, data: null })
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function pathOf(config) {
  const raw = config.url || ''
  return raw.split('?')[0].replace(/\/$/, '') || '/'
}

function methodOf(config) {
  return (config.method || 'get').toLowerCase()
}

function queryOf(config) {
  const params = { ...(config.params || {}) }
  const raw = config.url || ''
  const qIndex = raw.indexOf('?')
  if (qIndex >= 0) {
    const search = new URLSearchParams(raw.slice(qIndex + 1))
    search.forEach((value, key) => {
      if (params[key] == null) params[key] = value
    })
  }
  return params
}

function bodyOf(config) {
  const data = config.data
  if (!data) return {}
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    const obj = {}
    data.forEach((value, key) => {
      obj[key] = value
    })
    return obj
  }
  return data
}

function paginate(list, params) {
  const pageNum = Math.max(1, Number(params.pageNum) || 1)
  const pageSize = Math.min(100, Math.max(1, Number(params.pageSize) || 10))
  const total = list.length
  const pages = Math.max(1, Math.ceil(total / pageSize) || 1)
  const start = (pageNum - 1) * pageSize
  return {
    records: list.slice(start, start + pageSize),
    total,
    pageNum,
    pageSize,
    pages,
  }
}

/** 列表接口不返回 description、images；auditReason 仅本人/管理员可见 */
function toListGoods(item, { includeAuditReason = false } = {}) {
  const { description, images, auditReason, ...rest } = withFavoriteFlag(item)
  if (includeAuditReason && auditReason) {
    return { ...rest, auditReason }
  }
  return rest
}

function withFavoriteFlag(item) {
  return {
    ...item,
    favorited: favoriteIds.has(item.id),
    favoriteCount: item.favoriteCount + (favoriteIds.has(item.id) ? 1 : 0),
  }
}

function collectCategoryIds(categoryId) {
  const id = String(categoryId)
  const ids = new Set([id])
  categories.forEach((item) => {
    if (item.parentId && String(item.parentId) === id) {
      collectCategoryIds(item.id).forEach((child) => ids.add(child))
    }
  })
  return ids
}

function buildCategoryTree() {
  const map = new Map()
  categories.forEach((item) => {
    map.set(item.id, { ...item, children: [] })
  })
  const roots = []
  map.forEach((item) => {
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId).children.push(item)
    } else {
      roots.push(item)
    }
  })
  const prune = (nodes) =>
    nodes.map((node) => ({
      ...node,
      children: node.children.length ? prune(node.children) : undefined,
    }))
  return prune(roots)
}

function filterGoods(params = {}) {
  let list = goodsList.filter((item) => item.status === GoodsStatus.ON_SHELF)
  if (params.categoryId) {
    const ids = collectCategoryIds(params.categoryId)
    list = list.filter((item) => ids.has(String(item.categoryId)))
  }
  if (params.condition) {
    list = list.filter((item) => item.condition === params.condition)
  }
  if (params.minPrice != null && params.minPrice !== '') {
    list = list.filter((item) => item.price >= Number(params.minPrice))
  }
  if (params.maxPrice != null && params.maxPrice !== '') {
    list = list.filter((item) => item.price <= Number(params.maxPrice))
  }
  if (params.keyword) {
    const keyword = String(params.keyword).trim()
    list = list.filter(
      (item) => item.title.includes(keyword) || item.description.includes(keyword),
    )
  }
  const sort = params.sort || GoodsSort.LATEST
  const sorted = list.slice()
  if (sort === GoodsSort.PRICE_ASC) {
    sorted.sort((a, b) => a.price - b.price)
  } else if (sort === GoodsSort.PRICE_DESC) {
    sorted.sort((a, b) => b.price - a.price)
  } else if (sort === GoodsSort.HOT) {
    sorted.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
  } else {
    sorted.sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  }
  return sorted
}

function toUserMe(user) {
  return {
    id: user.id,
    username: user.username,
    nickname: user.nickname,
    avatar: user.avatar,
    phone: user.phone || null,
    studentNo: user.studentNo || null,
    school: user.school,
    role: user.role,
    status: user.status,
    creditScore: user.creditScore,
    studentAuthStatus: user.studentAuthStatus || StudentAuthStatus.NONE,
    createTime: user.createTime,
  }
}

function toUserPublic(user) {
  return {
    id: user.id,
    nickname: user.nickname,
    avatar: user.avatar,
    creditScore: user.creditScore,
    school: user.school,
    studentVerified: user.studentAuthStatus === StudentAuthStatus.APPROVED,
    createTime: user.createTime,
  }
}

let loginUserId = CURRENT_USER_ID

function currentUser() {
  return users.find((item) => item.id === loginUserId) || users.find((item) => item.id === CURRENT_USER_ID)
}


function toChatSession(session) {
  const goods = goodsList.find((item) => item.id === session.goodsId)
  const peer = users.find((item) => item.id === session.peerId) || users[1]
  return {
    id: session.id,
    goodsId: session.goodsId,
    goodsTitle: goods?.title || '',
    goodsCover: goods?.coverUrl || '',
    peer: {
      id: peer.id,
      nickname: peer.nickname,
      avatar: peer.avatar,
    },
    lastContent: session.lastContent || '',
    lastTime: session.lastTime || goods?.createTime || '',
    unreadCount: session.unreadCount || 0,
  }
}

function generateCaptcha() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 4; i += 1) {
    code += chars[Math.floor(Math.random() * chars.length)]
  }
  const key = `cap_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
  captchaStore.set(key, code.toLowerCase())
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40">
    <rect width="120" height="40" rx="6" fill="#e8f8f2"/>
    <text x="60" y="27" text-anchor="middle" font-size="22" font-family="monospace" font-weight="700" fill="#148a64" letter-spacing="6">${code}</text>
  </svg>`
  const imageBase64 = `data:image/svg+xml;base64,${btoa(svg)}`
  return { captchaKey: key, imageBase64 }
}

function assertCaptcha(key, code) {
  if (!key || !code) return false
  const saved = captchaStore.get(key)
  captchaStore.delete(key)
  return saved && saved === String(code).trim().toLowerCase()
}

function readUploadFile(file) {
  if (!file) return Promise.resolve(null)
  if (typeof FileReader === 'undefined') {
    return Promise.resolve({ url: '/mock/g01.jpg', name: file.name || 'image.jpg', size: file.size || 0 })
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve({ url: reader.result, name: file.name, size: file.size })
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsDataURL(file)
  })
}

function match(method, path, pattern) {
  if (method !== pattern.method) return null
  const keys = []
  const regex = new RegExp(
    `^${pattern.path.replace(/\{(\w+)\}/g, (_, key) => {
      keys.push(key)
      return '([^/]+)'
    })}$`,
  )
  const matched = path.match(regex)
  if (!matched) return null
  const params = {}
  keys.forEach((key, index) => {
    params[key] = matched[index + 1]
  })
  return params
}

function categoryNameById(id) {
  return categories.find((item) => item.id === String(id))?.name || ''
}

function sellerOfCurrent() {
  const user = currentUser()
  return {
    id: user.id,
    nickname: user.nickname,
    avatar: user.avatar,
    creditScore: user.creditScore,
    school: user.school,
  }
}

export async function handleMock(config) {
  const path = pathOf(config)
  const method = methodOf(config)
  const query = queryOf(config)
  const body = bodyOf(config)

  try {
    if (match(method, path, { method: 'get', path: '/auth/captcha' })) {
      return wait(generateCaptcha())
    }
    if (match(method, path, { method: 'post', path: '/auth/login' })) {
      if (!assertCaptcha(body.captchaKey, body.captchaCode)) {
        return fail('验证码不正确')
      }
      if (!body.account || !body.password) {
        return fail('请输入账号和密码')
      }
      const account = String(body.account).trim()
      const user =
        users.find((item) => item.username === account || item.phone === account || item.studentNo === account)
        || currentUser()
      loginUserId = user.id
      return wait({
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        expiresIn: 7200,
        userInfo: toUserMe(user),
      })
    }
    if (match(method, path, { method: 'post', path: '/auth/register' })) {
      if (!assertCaptcha(body.captchaKey, body.captchaCode)) {
        return fail('验证码不正确')
      }
      if (!body.username || !body.password) {
        return fail('请完善注册信息')
      }
      return wait(true)
    }
    if (match(method, path, { method: 'post', path: '/auth/logout' })) {
      return wait(true)
    }
    if (match(method, path, { method: 'post', path: '/auth/refresh' })) {
      refreshSeq += 1
      return wait({
        accessToken: `mock-access-token-${refreshSeq}`,
        refreshToken: `mock-refresh-token-${refreshSeq}`,
        expiresIn: 7200,
      })
    }

    if (match(method, path, { method: 'get', path: '/categories' })) {
      return wait(categories)
    }
    if (match(method, path, { method: 'get', path: '/categories/tree' })) {
      return wait(buildCategoryTree())
    }
    if (match(method, path, { method: 'get', path: '/recommend/home' })) {
      const size = Number(query.size) || 8
      const preferred = new Set(myInterestIds || [])
      const pool = recommendHomeIds
        .map((id) => goodsList.find((item) => item.id === id && item.status === GoodsStatus.ON_SHELF))
        .filter(Boolean)
      pool.sort((a, b) => {
        const aHit = preferred.has(a.categoryId) || [...preferred].some((id) => collectCategoryIds(id).has(a.categoryId))
        const bHit = preferred.has(b.categoryId) || [...preferred].some((id) => collectCategoryIds(id).has(b.categoryId))
        return Number(bHit) - Number(aHit)
      })
      // 推荐理由：与后端一致，每项带 reason（≤30 字）和 reasonType（CF / INTEREST / HOT），三种来源都覆盖
      return wait(pool.slice(0, size).map((item, index) => ({ ...toListGoods(item), ...mockHomeReason(item, index, preferred) })))
    }
    const similar = match(method, path, { method: 'get', path: '/recommend/similar/{goodsId}' })
    if (similar) {
      const current = goodsList.find((item) => item.id === String(similar.goodsId))
      const size = Number(query.size) || 8
      let list = goodsList.filter(
        (item) =>
          item.id !== String(similar.goodsId) &&
          item.status === GoodsStatus.ON_SHELF &&
          (!current || item.categoryId === current.categoryId || item.categoryName === current.categoryName),
      )
      if (!list.length) {
        list = goodsList.filter((item) => item.id !== String(similar.goodsId) && item.status === GoodsStatus.ON_SHELF)
      }
      return wait(
        list.slice(0, size).map((item, index) => ({
          ...toListGoods(item),
          ...(current && item.categoryId === current.categoryId && index < 2
            ? { reason: '和当前商品相似', reasonType: 'CF' }
            : index % 2
              ? { reason: '本周热门', reasonType: 'HOT' }
              : { reason: `和你看过的${shortTitle(current?.title)}相似`, reasonType: 'CF' }),
        })),
      )
    }
    if (match(method, path, { method: 'get', path: '/goods/search' }) || match(method, path, { method: 'get', path: '/goods' })) {
      return wait(paginate(filterGoods(query).map((item) => toListGoods(item)), query))
    }
    const goodsDetail = match(method, path, { method: 'get', path: '/goods/{id}' })
    if (goodsDetail) {
      const item = goodsList.find((row) => row.id === String(goodsDetail.id))
      if (!item) return fail('商品不存在', 20001)
      item.viewCount = (item.viewCount || 0) + 1
      const detail = withFavoriteFlag({ ...item, viewCount: item.viewCount })
      const isOwnerOrAdmin = item.seller?.id === CURRENT_USER_ID
      if (!isOwnerOrAdmin) delete detail.auditReason
      return wait(detail)
    }
    const addFav = match(method, path, { method: 'post', path: '/goods/{id}/favorite' })
    if (addFav) {
      favoriteIds.add(String(addFav.id))
      return wait(true)
    }
    const removeFav = match(method, path, { method: 'delete', path: '/goods/{id}/favorite' })
    if (removeFav) {
      favoriteIds.delete(String(removeFav.id))
      return wait(true)
    }
    const statusPatch = match(method, path, { method: 'patch', path: '/goods/{id}/status' })
    if (statusPatch) {
      const item = goodsList.find((row) => row.id === String(statusPatch.id))
      if (!item) return fail('商品不存在', 20001)
      const allowed =
        (item.status === GoodsStatus.ON_SHELF && body.status === GoodsStatus.OFF_SHELF) ||
        (item.status === GoodsStatus.OFF_SHELF && body.status === GoodsStatus.ON_SHELF)
      if (!allowed) return fail('当前状态不允许此操作')
      item.status = body.status
      return wait(true)
    }
    if (match(method, path, { method: 'post', path: '/goods' })) {
      goodsSeq += 1
      const id = `1920000000000000${String(goodsSeq).padStart(3, '0')}`
      const images = Array.isArray(body.images) ? body.images.map(String) : []
      const created = {
        id,
        title: body.title,
        description: body.description || '',
        price: Number(body.price),
        originalPrice: body.originalPrice != null ? Number(body.originalPrice) : null,
        condition: body.condition,
        tradeMethod: body.tradeMethod,
        status: GoodsStatus.PENDING_AUDIT,
        categoryId: String(body.categoryId),
        categoryName: categoryNameById(body.categoryId),
        coverUrl: body.coverUrl || images[0] || '',
        images,
        viewCount: 0,
        favoriteCount: 0,
        seller: sellerOfCurrent(),
        createTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      }
      goodsList.unshift(created)
      return wait(created)
    }
    const updateGoods = match(method, path, { method: 'put', path: '/goods/{id}' })
    if (updateGoods) {
      const item = goodsList.find((row) => row.id === String(updateGoods.id))
      if (!item) return fail('商品不存在', 20001)
      const images = Array.isArray(body.images) ? body.images.map(String) : item.images
      Object.assign(item, {
        title: body.title ?? item.title,
        description: body.description ?? item.description,
        price: body.price != null ? Number(body.price) : item.price,
        originalPrice: body.originalPrice != null ? Number(body.originalPrice) : item.originalPrice,
        condition: body.condition ?? item.condition,
        tradeMethod: body.tradeMethod ?? item.tradeMethod,
        categoryId: body.categoryId != null ? String(body.categoryId) : item.categoryId,
        categoryName: body.categoryId != null ? categoryNameById(body.categoryId) : item.categoryName,
        images,
        coverUrl: body.coverUrl || images[0] || item.coverUrl,
        status: GoodsStatus.PENDING_AUDIT,
        auditReason: undefined,
      })
      return wait(item)
    }

    if (match(method, path, { method: 'get', path: '/users/me/interests' })) {
      return wait(getMyInterestCategories())
    }
    if (match(method, path, { method: 'put', path: '/users/me/interests' })) {
      try {
        const ids = Array.isArray(body.categoryIds) ? body.categoryIds : []
        if (ids.length > INTEREST_MAX) return fail(`最多选择 ${INTEREST_MAX} 个兴趣分类`)
        return wait(setMyInterestIds(ids))
      } catch (error) {
        return fail(error.message || '保存兴趣失败')
      }
    }
    if (match(method, path, { method: 'get', path: '/users/me' })) {
      return wait(toUserMe(currentUser()))
    }
    if (match(method, path, { method: 'put', path: '/users/me' })) {
      const user = currentUser()
      if (body.nickname != null) user.nickname = body.nickname
      if (body.avatar != null) user.avatar = body.avatar
      if (body.school != null) user.school = body.school
      if (body.phone != null) user.phone = body.phone
      return wait(toUserMe(user))
    }
    if (match(method, path, { method: 'put', path: '/users/me/password' })) {
      if (!body.oldPassword || !body.newPassword) return fail('请填写原密码和新密码')
      return wait(true)
    }
    if (match(method, path, { method: 'post', path: '/users/me/student-verify' })) {
      const user = currentUser()
      if (!body.studentNo || !body.realName || !body.school || !body.cardImageUrl) {
        return fail('请完整填写学生认证信息')
      }
      user.studentNo = body.studentNo
      user.school = body.school
      user.studentAuthStatus = StudentAuthStatus.PENDING
      return wait(true)
    }
    if (match(method, path, { method: 'get', path: '/users/me/goods' })) {
      let list = goodsList.filter((item) => item.seller.id === CURRENT_USER_ID)
      if (query.status) list = list.filter((item) => item.status === query.status)
      return wait(paginate(list.map((item) => toListGoods(item, { includeAuditReason: true })), query))
    }
    if (match(method, path, { method: 'get', path: '/users/me/favorites' })) {
      const list = goodsList.filter((item) => favoriteIds.has(item.id)).map((item) => toListGoods(item))
      return wait(paginate(list, query))
    }
    const userHome = match(method, path, { method: 'get', path: '/users/{id}' })
    if (userHome) {
      const user = users.find((item) => item.id === String(userHome.id))
      if (!user) return fail('用户不存在', 404)
      return wait(toUserPublic(user))
    }

    if (match(method, path, { method: 'get', path: '/orders' })) {
      const role = query.role === OrderRole.SELLER ? OrderRole.SELLER : OrderRole.BUYER
      const list = listOrders({ role, status: query.status })
      return wait(paginate(list, query))
    }
    const orderDetail = match(method, path, { method: 'get', path: '/orders/{id}' })
    if (orderDetail) {
      const raw = orderRecords.find((item) => item.id === String(orderDetail.id))
      if (!raw) return fail('订单不存在', 30004)
      const me = currentUser()
      if (!me) return fail('无权查看该订单', 403)
      if (me.role !== Role.ADMIN && me.id !== raw.buyerId && me.id !== raw.sellerId) {
        return fail('无权查看该订单', 403)
      }
      return wait(toOrderVO(raw))
    }
    if (match(method, path, { method: 'post', path: '/orders' })) {
      if (!body.goodsId) return fail('缺少商品 ID')
      const item = goodsList.find((row) => row.id === String(body.goodsId))
      if (!item) return fail('商品不存在', 20001)
      if (item.status !== GoodsStatus.ON_SHELF) return fail('商品已被预订', 30003)
      item.status = GoodsStatus.RESERVED
      const created = {
        id: `1940000000000000${String(orderRecords.length + 8).padStart(3, '0')}`,
        goodsId: String(body.goodsId),
        goodsTitle: item.title,
        goodsCover: item.coverUrl,
        price: item.price,
        remark: body.remark || '',
        cancelReason: '',
        payTime: null,
        completeTime: null,
        reviewed: false,
        buyerId: CURRENT_USER_ID,
        sellerId: item.seller.id,
        status: OrderStatus.PENDING,
        createTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      }
      orderRecords.unshift(created)
      return wait(toOrderVO(created))
    }
    const orderStatus = match(method, path, { method: 'patch', path: '/orders/{id}/status' })
    if (orderStatus) {
      const order = orderRecords.find((item) => item.id === String(orderStatus.id))
      if (!order) return fail('订单不存在', 30004)
      const me = currentUser()
      if (!me) return fail('无权查看该内容', 403)
      if (me.role !== Role.ADMIN && me.id !== order.buyerId && me.id !== order.sellerId) {
        return fail('无权查看该订单', 403)
      }
      if (me.id === order.sellerId && (body.action === OrderAction.PAY || body.action === OrderAction.CONFIRM)) {
        return fail('当前角色不能对该订单执行此操作', 30005)
      }
      const goods = goodsList.find((item) => item.id === order.goodsId)
      const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
      if (body.action === OrderAction.PAY && order.status === OrderStatus.PENDING) {
        order.status = OrderStatus.PAID
        order.payTime = now
      } else if (body.action === OrderAction.CONFIRM && order.status === OrderStatus.PAID) {
        order.status = OrderStatus.COMPLETED
        order.completeTime = now
        if (goods) goods.status = GoodsStatus.SOLD
      } else if (
        (body.action === OrderAction.CANCEL || body.action === OrderAction.REFUND) &&
        (order.status === OrderStatus.PENDING || order.status === OrderStatus.PAID)
      ) {
        order.status = OrderStatus.CANCELLED
        order.cancelReason = body.reason || order.cancelReason
        if (goods) goods.status = GoodsStatus.ON_SHELF
      } else {
        return fail('订单状态不允许此操作', 30001)
      }
      return wait(toOrderVO(order))
    }

    if (match(method, path, { method: 'post', path: '/reviews' })) {
      const order = orderRecords.find((item) => item.id === String(body.orderId))
      if (!order) return fail('订单不存在')
      if (order.status !== OrderStatus.COMPLETED) return fail('订单未完成不可评价', 50002)
      if (order.reviewed) return fail('已评价', 50001)
      const score = Number(body.score)
      if (!score || score < 1 || score > 5) return fail('评分需为 1–5 分')
      order.reviewed = true
      const review = {
        id: `1970000000000000${String(reviewRecords.length + 2).padStart(3, '0')}`,
        orderId: order.id,
        fromUserId: CURRENT_USER_ID,
        score,
        content: body.content || '',
        createTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      }
      reviewRecords.push(review)
      return wait(toReviewVO(review))
    }
    if (match(method, path, { method: 'get', path: '/reviews' })) {
      return wait(paginate(reviewRecords.map(toReviewVO), query))
    }

    if (match(method, path, { method: 'post', path: '/chat/sessions' })) {
      const goodsId = String(body.goodsId || '')
      const toUserId = String(body.toUserId || '')
      if (!goodsId || !toUserId) return fail('缺少会话参数')
      const me = currentUser()
      if (me && String(me.id) === toUserId) return fail('不能和自己聊天', 40001)
      const goods = goodsList.find((item) => item.id === goodsId)
      const sellerId = String(goods?.seller?.id || '')
      if (sellerId && me && String(me.id) !== sellerId && toUserId !== sellerId) {
        return fail('会话双方须包含该商品的卖家', 40002)
      }
      const session = ensureSession(toUserId, goodsId)
      return wait(toChatSessionVO(session))
    }
    if (match(method, path, { method: 'get', path: '/chat/sessions' })) {
      return wait(listChatSessions())
    }
    const messages = match(method, path, { method: 'get', path: '/chat/sessions/{sessionId}/messages' })
    if (messages) {
      const session = findSessionById(messages.sessionId)
      if (!session) return fail('会话不存在', 40003)
      const me = currentUser()
      if (me && me.role !== Role.ADMIN && me.id !== session.peerId && me.id !== CURRENT_USER_ID) {
        return fail('无权查看该会话', 403)
      }
      // mock 会话以当前登录用户视角存储 peerId，非参与方无法命中自己的会话列表
      if (me && me.role !== Role.ADMIN && me.id !== CURRENT_USER_ID && String(me.id) !== String(session.peerId)) {
        return fail('无权查看该会话', 403)
      }
      return wait(pageChatMessages(messages.sessionId, query))
    }
    const readSession = match(method, path, { method: 'patch', path: '/chat/sessions/{sessionId}/read' })
    if (readSession) {
      return wait(markChatSessionRead(readSession.sessionId))
    }

    if (match(method, path, { method: 'post', path: '/files/upload' })) {
      const file = body.file
      const uploaded = await readUploadFile(file)
      if (!uploaded) return fail('请选择文件')
      return wait(uploaded)
    }

    if (match(method, path, { method: 'post', path: '/price/suggest' })) {
      if (!body.categoryId || !body.condition) return fail('请先选择分类和成色')
      const similar = goodsList.filter(
        (item) => String(item.categoryId) === String(body.categoryId) || collectCategoryIds(body.categoryId).has(String(item.categoryId)),
      )
      const prices = similar.map((item) => item.price)
      const origin = Number(body.originalPrice) || 0
      const base = prices.length
        ? prices.reduce((sum, price) => sum + price, 0) / prices.length
        : origin
          ? origin * 0.45
          : 99
      const suggestedPrice = Number(base.toFixed(2))
      const minPrice = Number((suggestedPrice * 0.8).toFixed(2))
      const maxPrice = Number((suggestedPrice * 1.2).toFixed(2))
      return wait({
        suggestedPrice,
        minPrice,
        maxPrice,
        sampleCount: Math.max(prices.length, 3),
        reason: prices.length
          ? '根据同类在售商品近期成交与挂牌价格综合估算。'
          : '同类样本较少，建议价主要参考成色和原价进行估算。',
      })
    }



    if (match(method, path, { method: 'post', path: '/reports' })) {
      const me = currentUser()
      if (!me) return fail('请先登录', 401)
      if (!body.targetType || !body.targetId || !body.reason) return fail('请完整填写举报信息')
      try {
        return wait(createUserReport({
          reporterId: me.id,
          targetType: body.targetType,
          targetId: body.targetId,
          reason: body.reason,
          detail: body.detail,
        }))
      } catch (error) {
        return fail(error.message || '举报失败', error.code || 400)
      }
    }
    if (match(method, path, { method: 'get', path: '/announcements' })) {
      return wait(paginate(listPublishedAnnouncements(), query))
    }
    const announcementDetail = match(method, path, { method: 'get', path: '/announcements/{id}' })
    if (announcementDetail) {
      const item = getPublishedAnnouncement(announcementDetail.id)
      if (!item) return fail('公告不存在', 60008)
      return wait(item)
    }

    if (match(method, path, { method: 'get', path: '/admin/reports' })) {
      return wait(paginate(listAdminReports(query), query))
    }
    const adminReport = match(method, path, { method: 'patch', path: '/admin/reports/{id}' })
    if (adminReport) {
      try {
        return wait(handleReport(adminReport.id, { ...body, auditorId: currentUser()?.id }))
      } catch (error) {
        return fail(error.message || '处理失败', error.code || 400)
      }
    }
    if (match(method, path, { method: 'get', path: '/admin/announcements' })) {
      return wait(paginate(listAdminAnnouncements(query), query))
    }
    if (match(method, path, { method: 'post', path: '/admin/announcements' })) {
      if (!body.title || !body.content) return fail('请填写标题和正文')
      return wait(createAnnouncement({
        title: body.title,
        content: body.content,
        status: body.status,
        publisherId: currentUser()?.id,
      }))
    }
    const adminAnnouncement = match(method, path, { method: 'get', path: '/admin/announcements/{id}' })
    if (adminAnnouncement && method === 'get') {
      const item = getAnnouncementById(adminAnnouncement.id)
      if (!item) return fail('公告不存在', 60008)
      return wait(item)
    }
    const adminAnnouncementPut = match(method, path, { method: 'put', path: '/admin/announcements/{id}' })
    if (adminAnnouncementPut) {
      const item = updateAnnouncement(adminAnnouncementPut.id, body)
      if (!item) return fail('公告不存在', 60008)
      return wait(item)
    }
    const adminAnnouncementStatus = match(method, path, { method: 'patch', path: '/admin/announcements/{id}/status' })
    if (adminAnnouncementStatus) {
      const item = updateAnnouncementStatus(adminAnnouncementStatus.id, body.status)
      if (!item) return fail('公告不存在', 60008)
      return wait(item)
    }
    const adminAnnouncementDelete = match(method, path, { method: 'delete', path: '/admin/announcements/{id}' })
    if (adminAnnouncementDelete) {
      const ok = deleteAnnouncement(adminAnnouncementDelete.id)
      if (!ok) return fail('公告不存在', 60008)
      return wait(null)
    }

    if (match(method, path, { method: 'get', path: '/admin/stats' })) {
      return wait(getAdminStats({ days: query.days }))
    }
    if (match(method, path, { method: 'get', path: '/admin/goods/audits' })) {
      return wait(listAuditGoods(query))
    }
    const goodsAudit = match(method, path, { method: 'patch', path: '/admin/goods/{id}/audit' })
    if (goodsAudit) {
      try {
        return wait(auditGoodsItem(goodsAudit.id, body))
      } catch (error) {
        return fail(error.message || '审核失败', error.code || 400)
      }
    }
    if (match(method, path, { method: 'get', path: '/admin/goods' })) {
      return wait(listAdminGoods(query))
    }
    const adminGoodsStatus = match(method, path, { method: 'patch', path: '/admin/goods/{id}/status' })
    if (adminGoodsStatus) {
      try {
        return wait(forceAdminGoodsStatus(adminGoodsStatus.id, body.status))
      } catch (error) {
        return fail(error.message || '操作失败', error.code || 400)
      }
    }
    if (match(method, path, { method: 'get', path: '/admin/users' })) {
      return wait(listAdminUsers(query))
    }
    const adminUserStatus = match(method, path, { method: 'patch', path: '/admin/users/{id}/status' })
    if (adminUserStatus) {
      try {
        return wait(toUserMe(updateAdminUserStatus(adminUserStatus.id, body.status, currentUser()?.id)))
      } catch (error) {
        return fail(error.message || '操作失败', error.code || 400)
      }
    }
    if (match(method, path, { method: 'get', path: '/admin/student-verifies' })) {
      return wait(listStudentVerifies(query))
    }
    const studentAudit = match(method, path, { method: 'patch', path: '/admin/student-verifies/{id}' })
    if (studentAudit) {
      try {
        return wait(auditStudentVerifyItem(studentAudit.id, body))
      } catch (error) {
        return fail(error.message || '审核失败', error.code || 400)
      }
    }

    console.warn('[mock] unhandled', method, path)
    return fail(`演示数据未覆盖该接口：${method.toUpperCase()} ${path}`, 404)
  } catch (error) {
    return fail(error.message || '演示数据异常')
  }
}
