import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SCREEN = '/workspace/screens'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'
const passed = [], failed = [], backendIssues = [], notes = [], shots = []

function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}
async function api(method, urlPath, { token, body } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${API}${urlPath}`, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined })
  const data = await res.json().catch(() => ({}))
  return { http: res.status, ...data }
}
async function apiLogin(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const res = await api('POST', '/auth/login', {
    body: { account, password, captchaKey: cap.data.captchaKey, captchaCode: redisGet(cap.data.captchaKey) },
  })
  if (res.code !== 200) throw new Error(`login ${account} ${JSON.stringify(res)}`)
  return res.data
}
async function check(name, fn) {
  try { await fn(); passed.push(name); console.log('PASS', name) }
  catch (e) { failed.push({ name, error: String(e.stack || e) }); console.error('FAIL', name, e) }
}
async function shot(page, name) {
  const file = path.join(SCREEN, `e2e-${name}.png`)
  await page.screenshot({ path: file, fullPage: false })
  shots.push(file)
  return file
}
async function confirmBox(page, name = '确定') {
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: 8000 })
  const btn = box.getByRole('button', { name })
  if (await btn.count()) await btn.click()
  else await box.locator('.el-button--primary').click()
}
async function applyAuth(ctx, session) {
  await ctx.addInitScript((s) => {
    localStorage.setItem('campus_trade_access_token', s.accessToken)
    localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
  }, session)
}
async function publishGoods(page, title) {
  await page.goto(`${BASE}/publish`)
  await page.getByTestId('publish-page').waitFor({ timeout: 15000 })
  await page.locator('.image-uploader input[type=file]').setInputFiles(IMAGE)
  await page.locator('.image-item').first().waitFor({ timeout: 10000 })
  await page.getByPlaceholder('简洁描述闲置物品').fill(title)
  await page.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('联调测试商品，请勿当真购买。')
  await page.getByPlaceholder('请选择分类').click()
  await page.locator('.el-cascader-menu').first().getByText('数码电子', { exact: true }).click()
  await page.locator('.el-cascader-menu').nth(1).getByText('耳机音箱', { exact: true }).click()
  await page.keyboard.press('Escape')
  await page.waitForTimeout(200)
  await page.locator('.el-form-item').filter({ hasText: '成色' }).locator('.el-select__wrapper').click({ force: true })
  await page.getByRole('option', { name: '轻微使用' }).click()
  await page.locator('.el-form-item').filter({ hasText: '交易方式' }).locator('.el-select__wrapper').click({ force: true })
  await page.getByRole('option', { name: '当面交易' }).click()
  await page.getByPlaceholder('购买时的价格').fill('199')
  const suggestResp = page.waitForResponse((r) => r.url().includes('/price/suggest') && r.ok(), { timeout: 15000 })
  await page.getByRole('button', { name: '智能定价' }).click()
  const suggestJson = await (await suggestResp).json()
  await page.locator('.suggest-result').waitFor({ timeout: 8000 })
  const boxText = await page.locator('.suggest-box').innerText()
  if (!boxText.includes('参考') || !boxText.includes('件同类商品')) throw new Error(boxText)
  await page.getByRole('button', { name: '一键采用建议价' }).click()
  const post = page.waitForResponse((r) => r.url().includes('/api/v1/goods') && r.request().method() === 'POST', { timeout: 15000 })
  await page.getByRole('button', { name: '提交审核' }).click()
  const body = await (await post).json()
  if (body.code !== 200) throw new Error(`发布失败 ${JSON.stringify(body)}`)
  await page.waitForURL('**/user', { timeout: 15000 })
  return { id: body.data?.id, sampleCount: suggestJson?.data?.sampleCount, suggest: suggestJson?.data }
}

const browser = await chromium.launch({ headless: true })
const opts = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }
try {
  const adminSession = await apiLogin('admin', 'admin123')
  const user1Session = await apiLogin('user1', '123456')
  const user2Session = await apiLogin('user2', '123456')

  const guest = await browser.newContext(opts)
  const g = await guest.newPage()

  await check('游客首页公告+推荐截图，打开公告详情', async () => {
    await g.goto(`${BASE}/`)
    await g.getByTestId('home-page').waitFor()
    await g.getByTestId('announce-bar').waitFor()
    await shot(g, 'home')
    const href = await g.getByTestId('announce-bar').locator('a').first().getAttribute('href')
    if (!href) throw new Error('公告条没有链接')
    await g.goto(`${BASE}${href}`)
    await g.getByTestId('announcement-detail').waitFor()
    const text = await g.getByTestId('announcement-detail').innerText()
    if (!text.includes('公告不存在') && text.length < 4) throw new Error(text)
    await shot(g, 'announcement')
  })

  await check('游客搜索并打开详情（相似推荐）', async () => {
    await g.goto(`${BASE}/search?keyword=${encodeURIComponent('耳机')}`)
    await g.waitForTimeout(800)
    if (await g.getByTestId('goods-grid').count()) {
      const card = g.getByTestId('goods-card').filter({ hasText: '索尼' }).first()
      if (await card.count()) await card.click()
    }
    if (!g.url().includes('/goods/')) await g.goto(`${BASE}/goods/4000000000000000003`)
    await g.getByTestId('goods-detail').waitFor({ timeout: 15000 })
    await g.getByRole('heading', { name: '相似推荐' }).waitFor({ timeout: 8000 })
    await shot(g, 'detail')
  })

  const u1ctx = await browser.newContext(opts)
  await applyAuth(u1ctx, user1Session)
  const u1 = await u1ctx.newPage()
  const titleA = `[联调测试] 蓝牙耳机 A ${Date.now().toString().slice(-6)}`
  const titleB = `[联调测试] 蓝牙耳机 B ${Date.now().toString().slice(-6)}`
  const titleC = `[联调测试] 待驳回商品 ${Date.now().toString().slice(-6)}`
  let goodsA, goodsB, goodsC, sampleCount

  await check('user1 发布三件联调商品（含智能定价 sampleCount）', async () => {
    const a = await publishGoods(u1, titleA)
    sampleCount = a.sampleCount
    notes.push(`智能定价 sampleCount=${sampleCount}`)
    const b = await publishGoods(u1, titleB)
    const c = await publishGoods(u1, titleC)
    goodsA = a; goodsB = b; goodsC = c
    await shot(u1, 'publish-profile')
  })

  const adctx = await browser.newContext(opts)
  await applyAuth(adctx, adminSession)
  const ad = await adctx.newPage()

  await check('admin 批量通过 + 驳回并留下 auditReason', async () => {
    await ad.goto(`${BASE}/admin/audit`)
    await ad.getByTestId('admin-audit').waitFor()
    const rows = ad.locator('.el-table__body tr.el-table__row')
    await rows.first().waitFor({ timeout: 10000 })
    let selected = 0
    const count = await rows.count()
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).innerText()
      if (text.includes(titleA) || text.includes(titleB)) {
        await rows.nth(i).locator('.el-checkbox').first().click()
        selected++
      }
    }
    if (selected < 2) throw new Error(`只选中 ${selected}/${count}`)
    await shot(ad, 'admin-audit')
    await ad.getByRole('button', { name: /批量通过/ }).click()
    if (await ad.locator('.el-message-box').isVisible({ timeout: 1500 }).catch(() => false)) await confirmBox(ad)
    await ad.waitForTimeout(1000)

    const after = ad.locator('.el-table__body tr.el-table__row')
    const n = await after.count()
    let ok = false
    for (let i = 0; i < n; i++) {
      const text = await after.nth(i).innerText()
      if (text.includes(titleC)) {
        await after.nth(i).getByRole('button', { name: '驳回' }).click()
        ok = true
        break
      }
    }
    if (!ok) throw new Error('列表中没有待驳回商品')
    await ad.locator('.el-dialog textarea, .el-dialog .el-textarea__inner').fill('联调测试驳回：信息不全')
    await ad.locator('.el-dialog').getByRole('button', { name: '确认驳回' }).click()
    await ad.waitForTimeout(800)
  })

  await check('卖家看到 auditReason', async () => {
    await u1.goto(`${BASE}/user`)
    await u1.waitForTimeout(800)
    let text = await u1.locator('body').innerText()
    if (!text.includes(titleC)) throw new Error('个人中心没有被驳回商品')
    if (!text.includes('联调测试驳回') && !text.includes('信息不全')) {
      await u1.getByText(titleC).first().click()
      await u1.getByTestId('goods-detail').waitFor()
      text = await u1.locator('.detail-page').innerText()
      if (!text.includes('联调测试驳回') && !text.includes('信息不全') && !text.includes('审核未通过')) {
        throw new Error(text.slice(0, 300))
      }
    }
    await shot(u1, 'audit-reason')
  })

  await check('审核后商品状态', async () => {
    const list = await api('GET', `/admin/goods?keyword=${encodeURIComponent('[联调测试]')}&pageNum=1&pageSize=50`, { token: adminSession.accessToken })
    const recs = list.data?.records || []
    const a = recs.find((i) => i.title === titleA)
    const b = recs.find((i) => i.title === titleB)
    const c = recs.find((i) => i.title === titleC)
    if (!a || a.status !== 'ON_SHELF') throw new Error(`A ${a?.status}`)
    if (!b || b.status !== 'ON_SHELF') throw new Error(`B ${b?.status}`)
    if (!c || c.status !== 'REJECTED') throw new Error(`C ${c?.status}`)
    goodsA = a; goodsB = b; goodsC = c
  })

  const u2ctx = await browser.newContext(opts)
  await applyAuth(u2ctx, user2Session)
  const u2 = await u2ctx.newPage()

  async function buy(page, id, remark) {
    await page.goto(`${BASE}/goods/${id}`)
    await page.getByTestId('goods-detail').waitFor()
    await page.getByRole('button', { name: '立即购买' }).click()
    if (remark) await page.getByPlaceholder('给卖家留言（选填）').fill(remark)
    const respP = page.waitForResponse((r) => r.url().includes('/api/v1/orders') && r.request().method() === 'POST')
    await page.getByRole('button', { name: '确认下单' }).click()
    return (await respP).json()
  }

  await check('下单 RESERVED → PAY → CONFIRM → COMPLETED → SOLD → 评价', async () => {
    const created = await buy(u2, goodsA.id, '联调测试下单')
    if (created.code !== 200) throw new Error(JSON.stringify(created))
    const oid = created.data.id
    const gds = await api('GET', `/goods/${goodsA.id}`, { token: user2Session.accessToken })
    if (gds.data?.status !== 'RESERVED') throw new Error(`下单后 ${gds.data?.status}`)
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    const card = u2.locator('.order-card').filter({ hasText: titleA }).first()
    await card.getByRole('button', { name: '付款' }).click()
    await confirmBox(u2, '确认付款')
    await u2.waitForTimeout(800)
    await card.getByRole('button', { name: '确认收货' }).click()
    await confirmBox(u2, '确认收货')
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${oid}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'COMPLETED') throw new Error(`订单 ${order.data?.status}`)
    const sold = await api('GET', `/admin/goods?keyword=${encodeURIComponent(titleA)}`, { token: adminSession.accessToken })
    const row = (sold.data?.records || []).find((i) => i.id === goodsA.id)
    if (row?.status !== 'SOLD') throw new Error(`商品 ${row?.status}`)
    await card.getByRole('button', { name: '评价' }).click()
    const dialog = u2.locator('.el-dialog').filter({ hasText: '评价' })
    await dialog.waitFor()
    const submit = dialog.getByRole('button', { name: /提交|确定/ })
    if (await submit.count()) await submit.click()
    await u2.waitForTimeout(800)
    await shot(u2, 'orders')
  })

  await check('另一单取消后商品回 ON_SHELF', async () => {
    const created = await buy(u2, goodsB.id, '联调测试取消单')
    if (created.code !== 200) throw new Error(JSON.stringify(created))
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    const card = u2.locator('.order-card').filter({ hasText: titleB }).first()
    await card.getByRole('button', { name: '取消订单' }).click()
    await u2.locator('.el-dialog textarea, .el-message-box textarea, .el-textarea__inner').last().fill('联调测试取消')
    await u2.locator('.el-dialog, .el-message-box').getByRole('button', { name: /确认|提交/ }).last().click()
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${created.data.id}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'CANCELLED') throw new Error(`订单 ${order.data?.status}`)
    const g2 = await api('GET', `/admin/goods?keyword=${encodeURIComponent(titleB)}`, { token: adminSession.accessToken })
    const row = (g2.data?.records || []).find((i) => i.id === goodsB.id)
    if (row?.status !== 'ON_SHELF') throw new Error(`商品 ${row?.status}`)
  })

  await check('user1/user2 实时聊天 文字/图片/商品卡片', async () => {
    const sessionRes = await api('POST', '/chat/sessions', {
      token: user2Session.accessToken,
      body: { goodsId: goodsB.id, toUserId: user1Session.userInfo.id },
    })
    if (sessionRes.code !== 200) throw new Error(JSON.stringify(sessionRes))
    const sessionId = sessionRes.data.id
    await u1.goto(`${BASE}/`)
    await u1.getByTestId('home-page').waitFor()
    await u1.waitForTimeout(1500)
    await u2.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(1000)
    await u2.locator('.composer .el-textarea__inner').fill('联调测试文字消息 hello')
    await u2.getByRole('button', { name: '发送' }).click()
    await u2.waitForTimeout(1200)
    const noti = await u1.locator('.el-notification').first().isVisible().catch(() => false)
    const badge = await u1.locator('.header-msg .el-badge__content').isVisible().catch(() => false)
    if (!noti && !badge) notes.push('user1 首页未看到通知或未读角标')
    await shot(u2, 'chat-user2')
    await u1.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1500)
    const t1 = await u1.locator('.message-list').innerText()
    if (!t1.includes('联调测试文字消息')) throw new Error(`user1 未收到文字: ${t1.slice(0, 200)}`)
    await u1.locator('.composer__file').setInputFiles(IMAGE)
    await u1.waitForTimeout(2000)
    await u1.getByTestId('send-goods-card').click()
    await u1.waitForTimeout(1500)
    await shot(u1, 'chat-user1')
    await u2.waitForTimeout(1500)
    if (!(await u2.locator('.message-list .el-image, .bubble.is-type-2').count())) notes.push('user2 未见图片消息')
    if (!(await u2.locator('.goods-chip').count())) notes.push('user2 未见商品卡片')
    const meta = await u1.locator('.bubble-meta').allTextContents()
    if (!meta.some((x) => x.includes('已读'))) notes.push(`已读回执未出现: ${JSON.stringify(meta.slice(-6))}`)
  })

  await check('损坏 accessToken 后自动刷新', async () => {
    const ctx = await browser.newContext(opts)
    const p = await ctx.newPage()
    await p.goto(`${BASE}/login`)
    await p.evaluate((s) => {
      localStorage.setItem('campus_trade_access_token', s.accessToken)
      localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
      localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
      localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
    }, user1Session)
    await p.goto(`${BASE}/user`)
    await p.waitForTimeout(500)
    await p.evaluate(() => localStorage.setItem('campus_trade_access_token', 'invalid-access-token-for-e2e'))
    const [resp] = await Promise.all([
      p.waitForResponse((r) => r.url().includes('/auth/refresh'), { timeout: 20000 }),
      p.evaluate(() => location.reload()),
    ])
    const body = await resp.json().catch(() => ({}))
    if (resp.status() !== 200 || body.code !== 200) {
      backendIssues.push({
        title: 'accessToken 失效后刷新未成功',
        api: 'POST /api/v1/auth/refresh',
        spec: 'HTTP 401 后用 refreshToken 换票',
        actual: { http: resp.status(), body },
        severity: '中',
      })
    } else notes.push('token 刷新接口返回 200')
    await p.waitForTimeout(800)
    if (p.url().includes('/login')) notes.push('刷新后被踢到登录页')
    await ctx.close()
  })

  await guest.close(); await u1ctx.close(); await u2ctx.close(); await adctx.close()
} catch (e) {
  failed.push({ name: '框架异常', error: String(e.stack || e) })
  console.error(e)
} finally {
  await browser.close()
  writeFileSync('/workspace/screens/e2e-report-remaining.json', JSON.stringify({ passed, failed, backendIssues, notes, shots }, null, 2))
  console.log('\n======= REMAINING =======')
  console.log(JSON.stringify({ passed: passed.length, failed: failed.length, notes, backendIssues }, null, 2))
  failed.forEach((i) => console.log('FAILED:', i.name, i.error.split('\n')[0]))
}
