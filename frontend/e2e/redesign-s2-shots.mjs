import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const OUT = '/workspace/screens'
const redisGet = (k) => execFileSync('redis-cli', ['get', `captcha:${k}`], { encoding: 'utf8' }).trim()
async function api(method, p, body) {
  const r = await fetch(`${API}${p}`, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined })
  return r.json()
}
async function login(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const res = await api('POST', '/auth/login', { account, password, captchaKey: cap.data.captchaKey, captchaCode: redisGet(cap.data.captchaKey) })
  if (res.code !== 200) throw new Error(JSON.stringify(res))
  return res.data
}
const errors = []
async function ctxFor(browser, session) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  if (session) await ctx.addInitScript((s) => {
    localStorage.setItem('campus_trade_access_token', s.accessToken)
    localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
  }, session)
  return ctx
}
async function shot(ctx, url, name, waitSel, action) {
  const page = await ctx.newPage()
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`${name}: ${m.text()}`) })
  page.on('pageerror', (e) => errors.push(`${name} pageerror: ${e.message}`))
  await page.goto(BASE + url, { waitUntil: 'networkidle' })
  if (waitSel) await page.locator(waitSel).first().waitFor({ timeout: 10000 }).catch(() => errors.push(`${name}: wait ${waitSel} timeout`))
  if (action) await action(page)
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${OUT}/redesign-s2-${name}.png`, fullPage: false })
  const info = await page.evaluate(() => ({
    primary: getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim(),
    alias: getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim(),
    bodyBg: getComputedStyle(document.body).backgroundColor,
    font: getComputedStyle(document.body).fontFamily.slice(0, 30),
  }))
  console.log(name, JSON.stringify(info))
  await page.close()
}
const browser = await chromium.launch()
const u1 = await login('user1', '123456')
const ad = await login('admin', 'admin123')
const c1 = await ctxFor(browser, u1)
await shot(c1, '/', 'home', '.goods-card')
await shot(c1, '/goods/4000000000000000006', 'detail', '.detail-price__now')
await shot(c1, '/publish', 'publish', '[data-testid=publish-page]')
await shot(c1, '/chat', 'chat', '.session-item', async (p) => { await p.locator('.session-item').nth(1).click(); await p.locator('.bubble').first().waitFor({ timeout: 8000 }).catch(() => {}) })
await shot(c1, '/user', 'profile', '.profile-card')
await shot(c1, '/search', 'search', '.goods-card')
await shot(c1, '/orders', 'orders', '.order-card')
const c0 = await ctxFor(browser, null)
await shot(c0, '/login', 'login', 'form')
const ca = await ctxFor(browser, ad)
await shot(ca, '/admin/dashboard', 'admin', 'canvas')
await shot(ca, '/admin/audit', 'admin-audit', '.el-table')
await browser.close()
console.log('ERRORS', JSON.stringify(errors, null, 1))
