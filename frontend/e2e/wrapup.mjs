import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'
const passed = [], failed = [], notes = []

function redisGet(key){return execFileSync('redis-cli',['get',`captcha:${key}`],{encoding:'utf8'}).trim()}
async function api(method, urlPath, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${urlPath}`, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined })
  return { http: res.status, ...(await res.json().catch(() => ({}))) }
}
async function apiLogin(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const res = await api('POST', '/auth/login', { body: { account, password, captchaKey: cap.data.captchaKey, captchaCode: redisGet(cap.data.captchaKey) } })
  if (res.code !== 200) throw new Error(JSON.stringify(res))
  return res.data
}
async function check(name, fn) {
  try { await fn(); passed.push(name); console.log('PASS', name) }
  catch (e) { failed.push({ name, error: String(e.stack || e) }); console.error('FAIL', name, e) }
}
async function applyAuth(ctx, s) {
  await ctx.addInitScript((session) => {
    localStorage.setItem('campus_trade_access_token', session.accessToken)
    localStorage.setItem('campus_trade_refresh_token', session.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(session.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(session.userInfo))
  }, s)
}

const browser = await chromium.launch({ headless: true })
const opts = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }
try {
  const adminSession = await apiLogin('admin', 'admin123')
  const user1Session = await apiLogin('user1', '123456')
  const user2Session = await apiLogin('user2', '123456')

  await check('公告详情截图', async () => {
    const ctx = await browser.newContext(opts)
    const p = await ctx.newPage()
    await p.goto(`${BASE}/announcements/9100000000000000001`)
    await p.locator('.announce-article').waitFor({ timeout: 10000 })
    const text = await p.locator('.announce-article').innerText()
    if (!text.includes('欢迎使用校园二手交易平台')) throw new Error(text.slice(0, 200))
    await p.screenshot({ path: '/workspace/screens/e2e-announcement.png' })
    await ctx.close()
  })

  // find reserved 联调测试 B order and cancel
  await check('取消联调订单后商品回 ON_SHELF（页面确定 + 校验）', async () => {
    const list = await api('GET', '/orders?role=buyer&status=PENDING&pageNum=1&pageSize=20', { token: user2Session.accessToken })
    const recs = list.data?.records || []
    const order = recs.find((i) => String(i.goodsTitle || '').includes('[联调测试] 蓝牙耳机 B'))
    if (!order) {
      // maybe already cancelled or not created
      const all = await api('GET', '/orders?role=buyer&pageNum=1&pageSize=50', { token: user2Session.accessToken })
      const hit = (all.data?.records || []).find((i) => String(i.goodsTitle || '').includes('[联调测试] 蓝牙耳机 B'))
      if (hit?.status === 'CANCELLED') {
        notes.push('取消单已是 CANCELLED')
      } else throw new Error(`找不到待取消订单 ${JSON.stringify(recs.map(r=>[r.status,r.goodsTitle]))}`)
    } else {
      const ctx = await browser.newContext(opts)
      await applyAuth(ctx, user2Session)
      const p = await ctx.newPage()
      await p.goto(`${BASE}/orders`)
      await p.getByTestId('orders-page').waitFor()
      const card = p.locator('.order-card').filter({ hasText: order.goodsTitle }).first()
      await card.getByRole('button', { name: '取消订单' }).click()
      await p.getByPlaceholder('请填写原因（选填）').fill('联调测试取消')
      await p.locator('.el-dialog').getByRole('button', { name: '确定' }).click()
      await p.waitForTimeout(1000)
      const after = await api('GET', `/orders/${order.id}`, { token: user2Session.accessToken })
      if (after.data?.status !== 'CANCELLED') throw new Error(`订单 ${after.data?.status}`)
      const goods = await api('GET', `/admin/goods?keyword=${encodeURIComponent(order.goodsTitle)}`, { token: adminSession.accessToken })
      const row = (goods.data?.records || []).find((i) => i.id === order.goodsId)
      if (row?.status !== 'ON_SHELF') throw new Error(`商品 ${row?.status}`)
      await p.screenshot({ path: '/workspace/screens/e2e-orders-cancel.png' })
      await ctx.close()
    }
  })

  await check('双方实时聊天文字/图片/商品卡片', async () => {
    const goodsList = await api('GET', `/admin/goods?keyword=${encodeURIComponent('[联调测试] 蓝牙耳机 B')}&pageNum=1&pageSize=10`, { token: adminSession.accessToken })
    const goods = (goodsList.data?.records || []).find((i) => i.title.includes('蓝牙耳机 B'))
    if (!goods) throw new Error('找不到联调商品 B')
    const sessionRes = await api('POST', '/chat/sessions', {
      token: user2Session.accessToken,
      body: { goodsId: goods.id, toUserId: user1Session.userInfo.id },
    })
    if (sessionRes.code !== 200) throw new Error(JSON.stringify(sessionRes))
    const sessionId = sessionRes.data.id

    const c1 = await browser.newContext(opts)
    const c2 = await browser.newContext(opts)
    await applyAuth(c1, user1Session)
    await applyAuth(c2, user2Session)
    const u1 = await c1.newPage()
    const u2 = await c2.newPage()
    await u1.goto(`${BASE}/`)
    await u1.getByTestId('home-page').waitFor()
    await u1.waitForTimeout(1500)
    await u2.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(1200)
    await u2.locator('.composer .el-textarea__inner').fill('联调测试文字消息 hello')
    await u2.getByRole('button', { name: '发送', exact: true }).click()
    await u2.waitForTimeout(1500)
    const noti = await u1.locator('.el-notification').first().isVisible().catch(() => false)
    const badge = await u1.locator('.header-msg .el-badge__content').isVisible().catch(() => false)
    if (!noti && !badge) notes.push('user1 首页未看到通知或未读角标')
    await u2.screenshot({ path: '/workspace/screens/e2e-chat-user2.png' })

    await u1.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1500)
    const t1 = await u1.locator('.message-list').innerText()
    if (!t1.includes('联调测试文字消息')) throw new Error(`user1 未收到文字: ${t1.slice(0, 250)}`)
    await u1.locator('.composer__file').setInputFiles(IMAGE)
    await u1.waitForTimeout(2500)
    await u1.getByTestId('send-goods-card').click()
    await u1.waitForTimeout(1500)
    await u1.screenshot({ path: '/workspace/screens/e2e-chat-user1.png' })
    await u2.waitForTimeout(1500)
    if (!(await u2.locator('.message-list .el-image, .bubble.is-type-2').count())) notes.push('user2 未见图片')
    if (!(await u2.locator('.goods-chip').count())) notes.push('user2 未见商品卡片')
    const meta = await u1.locator('.bubble-meta').allTextContents()
    if (!meta.some((x) => x.includes('已读'))) notes.push(`已读回执未出现: ${JSON.stringify(meta.slice(-8))}`)
    else notes.push('已读回执已出现')
    await c1.close(); await c2.close()
  })
} catch (e) {
  failed.push({ name: '框架', error: String(e.stack || e) })
  console.error(e)
} finally {
  await browser.close()
  writeFileSync('/workspace/screens/e2e-report-wrapup.json', JSON.stringify({ passed, failed, notes }, null, 2))
  console.log('\n======= WRAPUP =======')
  console.log(JSON.stringify({ passed, failed: failed.map(i=>i.name), notes }, null, 2))
  failed.forEach((i) => console.log('FAILED', i.name, i.error.split('\n').slice(0,8).join('\n')))
}
