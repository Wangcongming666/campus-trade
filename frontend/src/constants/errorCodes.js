/**
 * 业务错误码（规范第 5、12 节）。
 * 通用 HTTP 码与分段说明见规范；此处列出前端需要分支处理的码。
 */
export const BizCode = {
  SUCCESS: 200,
  USERNAME_EXISTS: 10001,
  BAD_CREDENTIALS: 10002,
  STUDENT_NOT_VERIFIED: 10003,
  ACCOUNT_BANNED: 10004,
  PHONE_EXISTS: 10005,
  CAPTCHA_INVALID: 10006,
  OLD_PASSWORD_WRONG: 10007,
  STUDENT_ALREADY_VERIFIED: 10008,
  STUDENT_VERIFY_PENDING: 10009,
  STUDENT_NO_EXISTS: 10010,
  USER_NOT_FOUND: 10011,
  GOODS_NOT_FOUND: 20001,
  GOODS_OFF_SHELF: 20002,
  GOODS_NOT_OWNER: 20003,
  CATEGORY_NOT_FOUND: 20004,
  GOODS_STATUS_NOT_ALLOWED: 20005,
  FILE_TYPE_NOT_SUPPORTED: 70001,
  FILE_TOO_LARGE: 70002,
  FILE_UPLOAD_FAILED: 70003,
  ORDER_STATUS_NOT_ALLOWED: 30001,
  CANNOT_BUY_OWN_GOODS: 30002,
  GOODS_RESERVED: 30003,
  ORDER_NOT_FOUND: 30004,
  ORDER_ACTION_ROLE_DENIED: 30005,
  CANNOT_CHAT_SELF: 40001,
  CHAT_MUST_INCLUDE_SELLER: 40002,
  SESSION_NOT_FOUND: 40003,
  MESSAGE_CONTENT_INVALID: 40004,
  ALREADY_REVIEWED: 50001,
  ORDER_NOT_COMPLETED: 50002,
  GOODS_NOT_PENDING_AUDIT: 60001,
  CANNOT_CHANGE_OWN_STATUS: 60002,
  CANNOT_BAN_ADMIN: 60003,
  STUDENT_VERIFY_HANDLED: 60004,
  CANNOT_REPORT_SELF: 60005,
  REPORT_ALREADY_PENDING: 60006,
  REPORT_ALREADY_HANDLED: 60007,
  ANNOUNCEMENT_NOT_FOUND: 60008,
  STUDENT_VERIFY_NOT_FOUND: 60009,
  REPORT_NOT_FOUND: 60010,
  PRICE_NO_SAMPLE: 80001,
}

export const BIZ_CODE_TEXT = {
  [BizCode.ORDER_NOT_FOUND]: '订单不存在',
  [BizCode.ORDER_ACTION_ROLE_DENIED]: '当前角色不能对该订单执行此操作',
  [BizCode.CANNOT_CHAT_SELF]: '不能和自己聊天',
  [BizCode.CHAT_MUST_INCLUDE_SELLER]: '会话双方须包含该商品的卖家',
  [BizCode.SESSION_NOT_FOUND]: '会话不存在',
  [BizCode.MESSAGE_CONTENT_INVALID]: '消息内容无效',
  [BizCode.GOODS_NOT_PENDING_AUDIT]: '商品不在待审核状态',
  [BizCode.CANNOT_CHANGE_OWN_STATUS]: '不能修改自己的账号状态',
  [BizCode.CANNOT_BAN_ADMIN]: '不能封禁管理员',
  [BizCode.STUDENT_VERIFY_HANDLED]: '认证申请已处理',
  [BizCode.CANNOT_REPORT_SELF]: '不能举报自己或自己的商品',
  [BizCode.REPORT_ALREADY_PENDING]: '已举报，请等待处理',
  [BizCode.REPORT_ALREADY_HANDLED]: '举报已处理',
  [BizCode.ANNOUNCEMENT_NOT_FOUND]: '公告不存在',
  [BizCode.STUDENT_VERIFY_NOT_FOUND]: '认证申请不存在',
  [BizCode.REPORT_NOT_FOUND]: '举报不存在',
}

/** 验证码类错误：自动刷新图形验证码 */
export const CAPTCHA_ERROR_CODES = [BizCode.CAPTCHA_INVALID]

export const CAPTCHA_INVALID_EVENT = 'campus-trade:captcha-invalid'

export function isCaptchaError(code) {
  return CAPTCHA_ERROR_CODES.includes(Number(code))
}

/** 接口未实现（HTTP/业务码 404）时，页面展示空状态，不弹全局错误 */
export const API_OFFLINE_TEXT = '该接口尚未上线'

export function isApiOffline(error) {
  if (!error) return false
  const status = error.response?.status
  const code = error.code ?? error.response?.data?.code
  return status === 404 || Number(code) === 404
}

export const FORBIDDEN_TEXT = '无权查看该内容'

export function isForbidden(error) {
  if (!error) return false
  const status = error.response?.status
  const code = error.code ?? error.response?.data?.code
  return status === 403 || Number(code) === 403
}

export function bizErrorText(error, fallback = '请求失败') {
  if (!error) return fallback
  const code = Number(error.code ?? error.response?.data?.code)
  return error.msg || error.response?.data?.msg || BIZ_CODE_TEXT[code] || fallback
}
