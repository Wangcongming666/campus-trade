/**
 * 时间格式约定（已确认）：yyyy-MM-dd HH:mm:ss，时区 Asia/Shanghai。
 * 金额为数字，保留 2 位小数。
 */

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000

export function formatPrice(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toFixed(2)
}

export function parseShanghaiTime(value) {
  if (!value) return null
  if (value instanceof Date) return value
  const text = String(value).trim()
  const matched = text.match(
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/,
  )
  if (matched) {
    const [, y, mo, d, h, mi, s] = matched
    return new Date(Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h) - 8, Number(mi), Number(s)))
  }
  const date = new Date(text)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 相对时间（以 Asia/Shanghai 墙钟为准） */
export function formatRelativeTime(value) {
  const date = parseShanghaiTime(value)
  if (!date) return ''
  const now = Date.now()
  const diff = now - date.getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day === 1) return '昨天'
  if (day < 7) return `${day} 天前`
  const shanghai = new Date(date.getTime() + SHANGHAI_OFFSET_MS)
  const y = shanghai.getUTCFullYear()
  const m = String(shanghai.getUTCMonth() + 1).padStart(2, '0')
  const d = String(shanghai.getUTCDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 展示用日期时间，Asia/Shanghai，精确到分钟 */
export function formatDateTime(value) {
  const date = parseShanghaiTime(value)
  if (!date) return ''
  const shanghai = new Date(date.getTime() + SHANGHAI_OFFSET_MS)
  const y = shanghai.getUTCFullYear()
  const m = String(shanghai.getUTCMonth() + 1).padStart(2, '0')
  const d = String(shanghai.getUTCDate()).padStart(2, '0')
  const h = String(shanghai.getUTCHours()).padStart(2, '0')
  const mi = String(shanghai.getUTCMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${mi}`
}

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
