// 阶段 4：亮色 / 暗色各截主要页面 -> /workspace/screens/redesign-s4-<theme>-<name>.png
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
const API = 'http://localhost:8080/api/v1'
const BASE = 'http://127.0.0.1:5173'
const OUT = '/workspace/screens'
const only = process.argv[2] // 可选：light | dark
const filter = process.argv[3] ? process.argv[3].split(',') : null
const j = async (m, p, b) => (await fetch(API + p, { method: m, headers: b ? { 'Content-Type': 'application/json' } : {}, body: b ? JSON.stringify(b) : undefined })).json()
async function login(account, password) {
  const cap = await j('GET', '/auth/captcha')
  const code = execFileSync('redis-cli', ['get', `captcha:${cap.data.captchaKey}`], { encoding: 'utf8' }).trim()
  return (await j('POST', '/auth/login', { account, password, captchaKey: cap.data.captchaKey, captchaCode: code })).data
}
const GOODS = '4000000000000000001'
const ORDER = '7000000000000000001'
const ANN = '9100000000000000001'
const pages = [
  ['home', null, '/', true],
  ['home-user', 'user1', '/', true],
  ['search', null, '/search?keyword=耳机', true],
  ['detail', 'user1', `/goods/${GOODS}`, true],
  ['publish', 'user1', '/publish', true],
  ['chat', 'user1', '/chat', false],
  ['orders', 'user1', '/orders', true],
  ['order-detail', 'user1', `/orders/${ORDER}`, true],
  ['profile', 'user1', '/user', true],
  ['profile-settings', 'user1', '/user?tab=settings', true],
  ['announcement', null, `/announcements/${ANN}`, true],
  ['login', null, '/login', false],
  ['register', null, '/register', false],
  ['admin', 'admin', '/admin/dashboard', true],
  ['admin-audit', 'admin', '/admin/audit', true],
  ['admin-users', 'admin', '/admin/users', true],
  ['admin-goods', 'admin', '/admin/goods', true],
  ['admin-reports', 'admin', '/admin/reports', true],
  ['admin-announcements', 'admin', '/admin/announcements', true],
]
const sessions = { user1: await login('user1', '123456'), admin: await login('admin', 'admin123') }
const browser = await chromium.launch()
const report = []
for (const theme of only ? [only] : ['light', 'dark']) {
  for (const [name, acct, url, full] of pages) {
    if (filter && !filter.includes(name)) continue
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, colorScheme: theme })
    const s = acct ? sessions[acct] : null
    await ctx.addInitScript(([s, theme]) => {
      localStorage.setItem('ct-theme', theme)
      if (s) {
        localStorage.setItem('campus_trade_access_token', s.accessToken)
        localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
        localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
        localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
      }
    }, [s, theme])
    const page = await ctx.newPage()
    const errs = []
    page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('unload')) errs.push(m.text().slice(0, 200)) })
    page.on('pageerror', (e) => errs.push('pageerror: ' + e.message))
    await page.goto(BASE + url, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1800)
    if (name === 'chat') {
      const first = page.locator('.session-item').first()
      if (await first.count()) { await first.click(); await page.waitForTimeout(1500) }
    }
    if (full) {
      await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 400) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 100)) } window.scrollTo(0, 0) })
      await page.waitForTimeout(1000)
      await page.addStyleTag({ content: '.pub-bar{position:absolute!important;bottom:0!important}' })
    }
    const dark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
    const file = `${OUT}/redesign-s4-${theme}-${name}.png`
    await page.screenshot({ path: file, fullPage: full })
    report.push(`${theme.padEnd(5)} ${name.padEnd(20)} html.dark=${dark} ${errs.length ? 'ERR ' + errs.join(' | ') : 'ok'}`)
    await ctx.close()
  }
}
console.log(report.join('\n'))
await browser.close()
