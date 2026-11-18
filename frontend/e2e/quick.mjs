import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
const [,, account, pass, url, out, full, waitSel] = process.argv
const API = 'http://localhost:8080/api/v1'
const j = async (m, p, b) => (await fetch(API + p, { method: m, headers: b ? { 'Content-Type': 'application/json' } : {}, body: b ? JSON.stringify(b) : undefined })).json()
let s = null
if (account !== '-') {
  const cap = await j('GET', '/auth/captcha')
  const code = execFileSync('redis-cli', ['get', `captcha:${cap.data.captchaKey}`], { encoding: 'utf8' }).trim()
  s = (await j('POST', '/auth/login', { account, password: pass, captchaKey: cap.data.captchaKey, captchaCode: code })).data
}
const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } })
if (s) await ctx.addInitScript((s) => { localStorage.setItem('campus_trade_access_token', s.accessToken); localStorage.setItem('campus_trade_refresh_token', s.refreshToken); localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200)); localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo)) }, s)
const page = await ctx.newPage()
const errs = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') errs.push(m.type() + ': ' + m.text().slice(0, 300)) })
page.on('pageerror', (e) => errs.push('pageerror: ' + e.message))
await page.goto('http://127.0.0.1:5173' + url, { waitUntil: 'networkidle' })
if (waitSel) await page.locator(waitSel).first().waitFor({ timeout: 8000 }).catch(() => errs.push('wait timeout ' + waitSel))
await page.waitForTimeout(2000)
if (full === 'full') { await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 400) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 120)) } window.scrollTo(0, 0) }); await page.waitForTimeout(1200) }
await page.screenshot({ path: out, fullPage: full === 'full' })
console.log(errs.filter((e) => !e.includes('unload')).join('\n') || 'no errors')
await b.close()
