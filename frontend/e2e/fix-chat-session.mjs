import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import path from 'node:path'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SCREEN = '/workspace/screens'
const REPRO_GOODS = '4000000000000000004'

const passed = []
const failed = []
const notes = []

function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}

async function api(method, urlPath, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`${API}${urlPath}`, { method, headers, body: payload })
  const data = await res.json().catch(() => ({}))
  return { http: res.status, ...data }
}

async function apiLogin(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const res = await api('POST', '/auth/login', {
    body: {
      account,
      password,
      captchaKey: cap.data.captchaKey,
      captchaCode: redisGet(cap.data.captchaKey),
    },
  })
  if (res.code !== 200) throw new Error(`login ${account} ${JSON.stringify(res)}`)
  return res.data
}

async function check(name, fn) {
  try {
    await fn()
    passed.push(name)
    console.log('PASS', name)
  } catch (e) {
    failed.push({ name, error: String(e.stack || e) })
    console.error('FAIL', name, e)
  }
}

async function applyAuth(ctx, session) {
  await ctx.addInitScript((s) => {
    localStorage.setItem('campus_trade_access_token', s.accessToken)
    localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
  }, session)
}

async function assertSelected(page, sessionId, label) {
  await page.getByTestId('chat-page').waitFor({ timeout: 10000 })
  const empty = page.locator('.chat-empty')
  // 选中后主区不应再显示「选择一个会话开始聊天」
  await page.waitForFunction((id) => {
    const emptyEl = document.querySelector('.chat-empty')
    const emptyShown = emptyEl && emptyEl.offsetParent !== null
    const item = document.querySelector(`.session-item[data-session-id="${id}"]`)
    const active = item && item.classList.contains('is-active')
    const urlOk = new URL(location.href).searchParams.get('sessionId') === id
    return !emptyShown && active && urlOk
  }, sessionId, { timeout: 10000 }).catch(async () => {
    const url = page.url()
    const emptyText = await empty.isVisible().then(async (v) => (v ? empty.innerText() : ''))
    const active = await page.locator('.session-item.is-active').getAttribute('data-session-id').catch(() => null)
    throw new Error(`${label}: 未立即选中会话 ${sessionId} url=${url} empty=${emptyText} active=${active}`)
  })
}

async function routerPushSession(page, sessionId) {
  const ok = await page.evaluate(async (id) => {
    const app = document.querySelector('#app')
    const vue = app && app.__vue_app__
    const router = vue && vue.config.globalProperties.$router
    if (!router) return false
    await router.push({ path: '/chat', query: { sessionId: id } })
    return true
  }, sessionId)
  if (!ok) throw new Error('页面内无法访问 Vue Router，无法切换 sessionId')
}

const browser = await chromium.launch({ headless: true })
const opts = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }

try {
  const user1 = await apiLogin('user1', '123456')
  const myId = String(user1.userInfo.id)
  const sessionsRes = await api('GET', '/chat/sessions', { token: user1.accessToken })
  const sessions = Array.isArray(sessionsRes.data) ? sessionsRes.data : []
  if (!sessions.length) throw new Error('user1 没有任何已有会话，无法测已存在会话跳转')
  const existing = sessions[0]
  const existingIds = new Set(sessions.map((s) => String(s.goodsId)))
  notes.push(`已有会话 ${sessions.length} 条，选用 ${existing.id} / ${existing.goodsTitle}`)

  const repro = await api('GET', `/goods/${REPRO_GOODS}`)
  const reproSeller = String(repro.data?.seller?.id || '')
  if (reproSeller === myId) throw new Error('复现商品卖家是 user1，换账号')
  notes.push(`复现商品卖家 ${repro.data?.seller?.nickname} ${reproSeller}，测试账号 user1`)

  // 找一件 user1 尚未聊过、且卖家不是自己的商品，用于「新建会话」
  const goodsPages = await Promise.all([
    api('GET', '/goods?pageNum=1&pageSize=50&sort=latest'),
    api('GET', `/goods/search?keyword=${encodeURIComponent('[联调测试]')}&pageNum=1&pageSize=50`),
  ])
  const goodsList = goodsPages.flatMap((r) => r.data?.records || r.data || []).filter((g) => g && g.id)
  const candidate = goodsList.find((g) => {
    const sellerId = String(g.seller?.id || g.sellerId || '')
    return sellerId && sellerId !== myId && !existingIds.has(String(g.id))
  })

  const ctx = await browser.newContext(opts)
  await applyAuth(ctx, user1)
  const page = await ctx.newPage()

  await check('已存在会话：从首页缓存列表后跳 /chat?sessionId 立即选中', async () => {
    await page.goto(`${BASE}/`)
    await page.getByTestId('home-page').waitFor()
    await page.waitForTimeout(800)
    await page.goto(`${BASE}/chat?sessionId=${existing.id}`)
    await assertSelected(page, String(existing.id), '已存在会话')
  })

  await check('复现路径：联系卖家（已存在会话也应立即选中） /goods/4000000000000000004', async () => {
    await page.goto(`${BASE}/`)
    await page.getByTestId('home-page').waitFor()
    await page.waitForTimeout(600)
    await page.goto(`${BASE}/goods/${REPRO_GOODS}`)
    await page.getByTestId('goods-detail').waitFor()
    const sessionWait = page.waitForResponse((r) => r.url().includes('/chat/sessions') && r.request().method() === 'POST')
    await page.getByRole('button', { name: '联系卖家' }).click()
    const body = await (await sessionWait).json()
    if (body.code !== 200 || !body.data?.id) throw new Error(`创建会话失败 ${JSON.stringify(body)}`)
    const sessionId = String(body.data.id)
    await page.waitForURL(new RegExp(`/chat\\?sessionId=${sessionId}`), { timeout: 8000 })
    await assertSelected(page, sessionId, '复现商品联系卖家')
    notes.push(`复现商品会话 ${sessionId}（可能是已存在会话）`)
  })

  await check('新建会话：联系卖家后立即出现在列表并选中', async () => {
    if (!candidate) {
      notes.push('没有找到 user1 未聊过的他人商品，跳过纯新建（复现路径已覆盖联系卖家跳转）')
      return
    }
    notes.push(`新建会话商品 ${candidate.id} ${candidate.title}`)
    await page.goto(`${BASE}/`)
    await page.getByTestId('home-page').waitFor()
    await page.waitForTimeout(600)
    await page.goto(`${BASE}/goods/${candidate.id}`)
    await page.getByTestId('goods-detail').waitFor()
    const sessionWait = page.waitForResponse((r) => r.url().includes('/chat/sessions') && r.request().method() === 'POST')
    await page.getByRole('button', { name: '联系卖家' }).click()
    const body = await (await sessionWait).json()
    if (body.code !== 200 || !body.data?.id) throw new Error(`新建会话失败 ${JSON.stringify(body)}`)
    const sessionId = String(body.data.id)
    await page.waitForURL(new RegExp(`/chat\\?sessionId=${sessionId}`), { timeout: 8000 })
    await assertSelected(page, sessionId, '新建会话')
    const inList = await page.locator(`.session-item[data-session-id="${sessionId}"]`).count()
    if (!inList) throw new Error('新建会话未出现在左侧列表')
    await page.screenshot({ path: path.join(SCREEN, 'fix-chat-session.png') })
    notes.push(`新建会话 ${sessionId}`)
  })

  await check('在 /chat 页内切换 sessionId（组件复用）', async () => {
    const latest = await api('GET', '/chat/sessions', { token: user1.accessToken })
    const list = Array.isArray(latest.data) ? latest.data : []
    if (list.length < 2) throw new Error('会话不足 2 条，无法测页内切换')
    const a = String(list[0].id)
    const b = String(list[1].id)
    await page.goto(`${BASE}/chat?sessionId=${a}`)
    await assertSelected(page, a, '切换前')
    await routerPushSession(page, b)
    await assertSelected(page, b, '页内切换')
    // 若新建用例未截到图，这里补一张
    const shot = path.join(SCREEN, 'fix-chat-session.png')
    await page.screenshot({ path: shot })
    notes.push(`页内切换 ${a} -> ${b}`)
  })

  await ctx.close()
} catch (e) {
  failed.push({ name: '框架', error: String(e.stack || e) })
  console.error(e)
} finally {
  await browser.close()
}

console.log(JSON.stringify({ passed, failed, notes }, null, 2))
if (failed.length) process.exitCode = 1
