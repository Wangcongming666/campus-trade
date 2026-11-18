/**
 * 业务枚举集中定义（规范第 11 节已定稿），全项目只引用本文件常量，不要散落字面量。
 */

function freeze(obj) {
  return Object.freeze(obj)
}

/** 用户角色 */
export const Role = freeze({
  USER: 'USER',
  ADMIN: 'ADMIN',
})

export const ROLE_LABELS = freeze({
  [Role.USER]: '普通用户',
  [Role.ADMIN]: '管理员',
})

/** 商品状态。卖家 PATCH /goods/{id}/status 只允许 ON_SHELF ↔ OFF_SHELF。 */
export const GoodsStatus = freeze({
  PENDING_AUDIT: 'PENDING_AUDIT',
  ON_SHELF: 'ON_SHELF',
  RESERVED: 'RESERVED',
  OFF_SHELF: 'OFF_SHELF',
  SOLD: 'SOLD',
  REJECTED: 'REJECTED',
})

export const GOODS_STATUS_LABELS = freeze({
  [GoodsStatus.PENDING_AUDIT]: '待审核',
  [GoodsStatus.ON_SHELF]: '已上架',
  [GoodsStatus.RESERVED]: '已预订',
  [GoodsStatus.OFF_SHELF]: '已下架',
  [GoodsStatus.SOLD]: '已售出',
  [GoodsStatus.REJECTED]: '审核未通过',
})

export const GOODS_STATUS_TAG_TYPES = freeze({
  [GoodsStatus.PENDING_AUDIT]: 'warning',
  [GoodsStatus.ON_SHELF]: 'success',
  [GoodsStatus.RESERVED]: 'warning',
  [GoodsStatus.OFF_SHELF]: 'info',
  [GoodsStatus.SOLD]: 'danger',
  [GoodsStatus.REJECTED]: 'danger',
})

/** 商品成色 */
export const GoodsCondition = freeze({
  NEW: 'NEW',
  LIKE_NEW: 'LIKE_NEW',
  GOOD: 'GOOD',
  FAIR: 'FAIR',
  POOR: 'POOR',
})

export const GOODS_CONDITION_LABELS = freeze({
  [GoodsCondition.NEW]: '全新',
  [GoodsCondition.LIKE_NEW]: '几乎全新',
  [GoodsCondition.GOOD]: '轻微使用',
  [GoodsCondition.FAIR]: '明显使用痕迹',
  [GoodsCondition.POOR]: '有明显瑕疵',
})

export const GOODS_CONDITION_TAG_TYPES = freeze({
  [GoodsCondition.NEW]: 'success',
  [GoodsCondition.LIKE_NEW]: 'success',
  [GoodsCondition.GOOD]: 'primary',
  [GoodsCondition.FAIR]: 'warning',
  [GoodsCondition.POOR]: 'info',
})

/** 交易方式 */
export const TradeMethod = freeze({
  FACE: 'FACE',
  EXPRESS: 'EXPRESS',
  EITHER: 'EITHER',
})

export const TRADE_METHOD_LABELS = freeze({
  [TradeMethod.FACE]: '当面交易',
  [TradeMethod.EXPRESS]: '快递邮寄',
  [TradeMethod.EITHER]: '当面或邮寄均可',
})

/**
 * 商品列表排序（GET /goods?sort=）
 * latest 默认，create_time 倒序；hot 按 view_count 倒序。
 */
export const GoodsSort = freeze({
  LATEST: 'latest',
  PRICE_ASC: 'price_asc',
  PRICE_DESC: 'price_desc',
  HOT: 'hot',
})

export const GOODS_SORT_LABELS = freeze({
  [GoodsSort.LATEST]: '最新发布',
  [GoodsSort.PRICE_ASC]: '价格从低到高',
  [GoodsSort.PRICE_DESC]: '价格从高到低',
  [GoodsSort.HOT]: '最热',
})

/** 订单状态 */
export const OrderStatus = freeze({
  PENDING: 'PENDING',
  PAID: 'PAID',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
})

export const ORDER_STATUS_LABELS = freeze({
  [OrderStatus.PENDING]: '待付款',
  [OrderStatus.PAID]: '待确认收货',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
})

export const ORDER_STATUS_TAG_TYPES = freeze({
  [OrderStatus.PENDING]: 'warning',
  [OrderStatus.PAID]: 'primary',
  [OrderStatus.COMPLETED]: 'success',
  [OrderStatus.CANCELLED]: 'info',
})

/**
 * 订单 action（已定稿，无 DELIVER）：
 * PENDING -PAY(买家)→ PAID -CONFIRM(买家当面确认收货)→ COMPLETED
 * PENDING 或 PAID -CANCEL/REFUND(买卖双方)→ CANCELLED
 */
export const OrderAction = freeze({
  PAY: 'PAY',
  CANCEL: 'CANCEL',
  CONFIRM: 'CONFIRM',
  REFUND: 'REFUND',
})

export const ORDER_ACTION_LABELS = freeze({
  [OrderAction.PAY]: '付款',
  [OrderAction.CANCEL]: '取消订单',
  [OrderAction.CONFIRM]: '确认收货',
  [OrderAction.REFUND]: '退款',
})

/** 订单列表角色 */
export const OrderRole = freeze({
  BUYER: 'buyer',
  SELLER: 'seller',
})

export const ORDER_ROLE_LABELS = freeze({
  [OrderRole.BUYER]: '我买到的',
  [OrderRole.SELLER]: '我卖出的',
})

/**
 * 按订单状态 + 当前用户角色，返回可用 action 列表（供订单页使用）。
 * @param {string} status OrderStatus
 * @param {string} role OrderRole
 * @returns {string[]}
 */
export function getAvailableOrderActions(status, role) {
  const actions = []
  if (status === OrderStatus.PENDING) {
    if (role === OrderRole.BUYER) actions.push(OrderAction.PAY)
    actions.push(OrderAction.CANCEL, OrderAction.REFUND)
    return actions
  }
  if (status === OrderStatus.PAID) {
    if (role === OrderRole.BUYER) actions.push(OrderAction.CONFIRM)
    actions.push(OrderAction.CANCEL, OrderAction.REFUND)
    return actions
  }
  return actions
}

/** 审核结果 */
export const AuditResult = freeze({
  APPROVE: 'APPROVE',
  REJECT: 'REJECT',
})

export const AUDIT_RESULT_LABELS = freeze({
  [AuditResult.APPROVE]: '通过',
  [AuditResult.REJECT]: '拒绝',
})

export const AUDIT_RESULT_TAG_TYPES = freeze({
  [AuditResult.APPROVE]: 'success',
  [AuditResult.REJECT]: 'danger',
})

/** 聊天消息类型：1 文本 2 图片 3 商品卡片 */
export const ChatMessageType = freeze({
  TEXT: 1,
  IMAGE: 2,
  GOODS_CARD: 3,
})

export const CHAT_MESSAGE_TYPE_LABELS = freeze({
  [ChatMessageType.TEXT]: '文本',
  [ChatMessageType.IMAGE]: '图片',
  [ChatMessageType.GOODS_CARD]: '商品卡片',
})

/** 用户账号状态 */
export const UserStatus = freeze({
  NORMAL: 'NORMAL',
  BANNED: 'BANNED',
})

export const USER_STATUS_LABELS = freeze({
  [UserStatus.NORMAL]: '正常',
  [UserStatus.BANNED]: '已封禁',
})

export const USER_STATUS_TAG_TYPES = freeze({
  [UserStatus.NORMAL]: 'success',
  [UserStatus.BANNED]: 'danger',
})

/** 学生认证状态 */
export const StudentAuthStatus = freeze({
  NONE: 'NONE',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
})

export const STUDENT_AUTH_STATUS_LABELS = freeze({
  [StudentAuthStatus.NONE]: '未认证',
  [StudentAuthStatus.PENDING]: '认证审核中',
  [StudentAuthStatus.APPROVED]: '已认证',
  [StudentAuthStatus.REJECTED]: '认证未通过',
})

export const STUDENT_AUTH_STATUS_TAG_TYPES = freeze({
  [StudentAuthStatus.NONE]: 'info',
  [StudentAuthStatus.PENDING]: 'warning',
  [StudentAuthStatus.APPROVED]: 'success',
  [StudentAuthStatus.REJECTED]: 'danger',
})

/** 举报对象 */
export const ReportTargetType = freeze({
  GOODS: 'GOODS',
  USER: 'USER',
})

export const REPORT_TARGET_TYPE_LABELS = freeze({
  [ReportTargetType.GOODS]: '商品',
  [ReportTargetType.USER]: '用户',
})

/** 举报处理状态（列表过滤 / 展示） */
export const ReportStatus = freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
})

export const REPORT_STATUS_LABELS = freeze({
  [ReportStatus.PENDING]: '待处理',
  [ReportStatus.APPROVED]: '已成立',
  [ReportStatus.REJECTED]: '已驳回',
})

export const REPORT_STATUS_TAG_TYPES = freeze({
  [ReportStatus.PENDING]: 'warning',
  [ReportStatus.APPROVED]: 'success',
  [ReportStatus.REJECTED]: 'info',
})

/** 前端举报原因选项（接口 reason 为 ≤64 的字符串，无强制枚举） */
export const REPORT_REASON_OPTIONS = Object.freeze([
  '虚假信息',
  '违禁物品',
  '诈骗欺诈',
  '侵权盗用',
  '辱骂骚扰',
  '描述不符',
  '其他',
])

/** 公告状态 */
export const AnnouncementStatus = freeze({
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  OFF_SHELF: 'OFF_SHELF',
})

export const ANNOUNCEMENT_STATUS_LABELS = freeze({
  [AnnouncementStatus.DRAFT]: '草稿',
  [AnnouncementStatus.PUBLISHED]: '已发布',
  [AnnouncementStatus.OFF_SHELF]: '已下架',
})

export const ANNOUNCEMENT_STATUS_TAG_TYPES = freeze({
  [AnnouncementStatus.DRAFT]: 'info',
  [AnnouncementStatus.PUBLISHED]: 'success',
  [AnnouncementStatus.OFF_SHELF]: 'warning',
})

export function enumLabel(labels, value, fallback = '未知') {
  if (value == null || value === '') return fallback
  return labels[value] ?? fallback
}

export function enumTagType(types, value, fallback = 'info') {
  return types[value] ?? fallback
}

export function enumOptions(labels) {
  return Object.entries(labels).map(([value, label]) => ({ value, label }))
}
