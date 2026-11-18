import {
  AuditResult,
  GoodsStatus,
  Role,
  StudentAuthStatus,
  UserStatus,
} from '@/constants/enums'
import { categories, goodsList, users } from '@/mock/data'
import { orderRecords } from '@/mock/extraState'

function shanghaiDate(msAgo = 0) {
  const date = new Date(Date.now() - msAgo)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type) => parts.find((part) => part.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')}`
}

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
const day = 24 * hour

export const studentVerifies = [
  {
    id: '1980000000000000001',
    userId: '1930000000000000004',
    username: 'chenhao',
    nickname: '陈同学',
    realName: '陈浩',
    studentNo: '2023001008',
    school: '示例大学',
    cardImageUrl: '/mock/g06.jpg',
    status: StudentAuthStatus.PENDING,
    reason: '',
    createTime: shanghaiTime(18 * hour),
  },
  {
    id: '1980000000000000002',
    userId: '1930000000000000002',
    username: 'liming',
    nickname: '李同学',
    realName: '李明',
    studentNo: '2022000888',
    school: '示例大学',
    cardImageUrl: '/mock/g03.jpg',
    status: StudentAuthStatus.APPROVED,
    reason: '',
    createTime: shanghaiTime(20 * day),
  },
  {
    id: '1980000000000000003',
    userId: '1930000000000000005',
    username: 'zhaolei',
    nickname: '赵同学',
    realName: '赵磊',
    studentNo: '2021001888',
    school: '示例大学',
    cardImageUrl: '/mock/g12.jpg',
    status: StudentAuthStatus.REJECTED,
    reason: '学生证照片模糊，无法辨认学号',
    createTime: shanghaiTime(40 * day),
  },
]

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

function topCategoryName(categoryId) {
  const cat = categories.find((item) => item.id === String(categoryId))
  if (!cat) return '未分类'
  if (!cat.parentId) return cat.name
  const parent = categories.find((item) => item.id === cat.parentId)
  return parent?.name || cat.name
}

export function listAuditGoods(query = {}) {
  const status = query.status || GoodsStatus.PENDING_AUDIT
  const list = goodsList
    .filter((item) => item.status === status)
    .slice()
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  return paginate(list, query)
}

export function auditGoodsItem(id, { result, reason } = {}) {
  const item = goodsList.find((row) => row.id === String(id))
  if (!item) {
    const error = new Error('商品不存在')
    error.code = 20001
    throw error
  }
  if (item.status !== GoodsStatus.PENDING_AUDIT) {
    const error = new Error('商品不在待审核状态')
    error.code = 60001
    throw error
  }
  if (result === AuditResult.APPROVE) {
    item.status = GoodsStatus.ON_SHELF
    item.auditReason = undefined
  } else if (result === AuditResult.REJECT) {
    if (!reason || !String(reason).trim()) {
      const error = new Error('驳回时必须填写原因')
      error.code = 400
      throw error
    }
    item.status = GoodsStatus.REJECTED
    item.auditReason = String(reason).trim()
  } else {
    const error = new Error('参数错误')
    error.code = 400
    throw error
  }
  return item
}

export function listAdminGoods(query = {}) {
  let list = goodsList.slice()
  const keyword = String(query.keyword || '').trim()
  if (keyword) {
    list = list.filter((item) => item.title.includes(keyword) || item.seller?.nickname?.includes(keyword))
  }
  if (query.categoryId) {
    const id = String(query.categoryId)
    const ids = new Set([id])
    categories.forEach((item) => {
      if (item.parentId && String(item.parentId) === id) ids.add(item.id)
    })
    list = list.filter((item) => ids.has(String(item.categoryId)))
  }
  if (query.status) list = list.filter((item) => item.status === query.status)
  list.sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  return paginate(list, query)
}

export function forceAdminGoodsStatus(id, status) {
  const item = goodsList.find((row) => row.id === String(id))
  if (!item) {
    const error = new Error('商品不存在')
    error.code = 20001
    throw error
  }
  const allowed = (
    (item.status === GoodsStatus.ON_SHELF && status === GoodsStatus.OFF_SHELF)
    || (item.status === GoodsStatus.OFF_SHELF && status === GoodsStatus.ON_SHELF)
  )
  if (!allowed) {
    const error = new Error('商品当前状态不允许此操作')
    error.code = 20005
    throw error
  }
  item.status = status
  return item
}

export function listAdminUsers(query = {}) {
  let list = users.slice()
  const keyword = String(query.keyword || '').trim()
  if (keyword) {
    list = list.filter((item) =>
      [item.username, item.nickname, item.phone, item.studentNo].some((value) =>
        String(value || '').includes(keyword),
      ),
    )
  }
  if (query.status) list = list.filter((item) => item.status === query.status)
  list.sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  return paginate(
    list.map((item) => ({
      id: item.id,
      username: item.username,
      nickname: item.nickname,
      avatar: item.avatar,
      phone: item.phone || null,
      studentNo: item.studentNo || null,
      school: item.school,
      role: item.role,
      status: item.status,
      creditScore: item.creditScore,
      studentAuthStatus: item.studentAuthStatus,
      createTime: item.createTime,
    })),
    query,
  )
}

export function updateAdminUserStatus(id, status, operatorId) {
  const user = users.find((item) => item.id === String(id))
  if (!user) {
    const error = new Error('用户不存在')
    error.code = 10011
    throw error
  }
  if (operatorId && String(user.id) === String(operatorId)) {
    const error = new Error('不能修改自己的账号状态')
    error.code = 60002
    throw error
  }
  if (user.role === Role.ADMIN) {
    const error = new Error('不能封禁管理员')
    error.code = 60003
    throw error
  }
  if (status !== UserStatus.NORMAL && status !== UserStatus.BANNED) {
    const error = new Error('参数错误')
    error.code = 400
    throw error
  }
  user.status = status
  return user
}

export function listStudentVerifies(query = {}) {
  let list = studentVerifies.slice()
  if (query.status) list = list.filter((item) => item.status === query.status)
  list.sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  return paginate(list, query)
}

export function auditStudentVerifyItem(id, { result, reason } = {}) {
  const item = studentVerifies.find((row) => row.id === String(id))
  if (!item) {
    const error = new Error('认证申请不存在')
    error.code = 60009
    throw error
  }
  if (item.status !== StudentAuthStatus.PENDING) {
    const error = new Error('认证申请已处理')
    error.code = 60004
    throw error
  }
  const user = users.find((row) => row.id === item.userId)
  if (result === AuditResult.APPROVE) {
    item.status = StudentAuthStatus.APPROVED
    item.reason = ''
    if (user) {
      user.studentAuthStatus = StudentAuthStatus.APPROVED
      user.studentNo = item.studentNo
      user.school = item.school
    }
  } else if (result === AuditResult.REJECT) {
    if (!reason || !String(reason).trim()) {
      const error = new Error('驳回时必须填写原因')
      error.code = 400
      throw error
    }
    item.status = StudentAuthStatus.REJECTED
    item.reason = String(reason).trim()
    if (user) user.studentAuthStatus = StudentAuthStatus.REJECTED
  } else {
    const error = new Error('参数错误')
    error.code = 400
    throw error
  }
  return item
}

/**
 * GET /admin/stats 字段假设：
 * days: 7 | 30
 * userCount, goodsCount, orderCount, gmv,
 * pendingGoodsAudit, pendingStudentVerify,
 * trend: [{ date, newUsers, newGoods, newOrders }],
 * categoryShares: [{ categoryId, categoryName, count }],
 * hotGoods: [{ id, title, coverUrl, price, viewCount, favoriteCount }]  Top10
 */
export function getAdminStats({ days = 7 } = {}) {
  const range = Number(days) === 30 ? 30 : 7
  const completed = orderRecords.filter((item) => item.status === 'COMPLETED')
  const gmv = completed.reduce((sum, item) => sum + Number(item.price || 0), 0)
  const pendingGoodsAudit = goodsList.filter((item) => item.status === GoodsStatus.PENDING_AUDIT).length
  const pendingStudentVerify = studentVerifies.filter((item) => item.status === StudentAuthStatus.PENDING).length

  const trend = []
  for (let i = range - 1; i >= 0; i -= 1) {
    trend.push({
      date: shanghaiDate(i * day),
      newUsers: 1 + ((range - i) % 3),
      newGoods: 1 + ((range - i + 1) % 4),
      newOrders: ((range - i) % 5),
    })
  }

  const shareMap = new Map()
  goodsList.forEach((item) => {
    const name = topCategoryName(item.categoryId)
    const current = shareMap.get(name) || { categoryId: item.categoryId, categoryName: name, count: 0 }
    current.count += 1
    shareMap.set(name, current)
  })

  const hotGoods = goodsList
    .slice()
    .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
    .slice(0, 10)
    .map((item) => ({
      id: item.id,
      title: item.title,
      coverUrl: item.coverUrl,
      price: item.price,
      viewCount: item.viewCount,
      favoriteCount: item.favoriteCount,
    }))

  return {
    userCount: users.length,
    goodsCount: goodsList.length,
    orderCount: orderRecords.length,
    gmv: Number(gmv.toFixed(2)),
    pendingGoodsAudit,
    pendingStudentVerify,
    trend,
    categoryShares: [...shareMap.values()],
    hotGoods,
  }
}

export function getPendingAuditCounts() {
  return {
    pendingGoodsAudit: goodsList.filter((item) => item.status === GoodsStatus.PENDING_AUDIT).length,
    pendingStudentVerify: studentVerifies.filter((item) => item.status === StudentAuthStatus.PENDING).length,
  }
}
