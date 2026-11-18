import { chromium } from 'playwright'
import { execFileSync, execFile } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { promisify } from 'node:util'
import path from 'node:path'
const execFileAsync = promisify(execFile)

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SCREEN = '/workspace/screens'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'
const CAT = '3000000000000000103' // 耳机音箱
const passed = [], failed = [], notes = [], shots = [], backendIssues = []

function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}
async function api(method, urlPath, { token, body, raw } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (raw) payload = raw
  else if (body !== undefined) {
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
    body: { account, password, captchaKey: cap.data.captchaKey, captchaCode: redisGet(cap.data.captchaKey) },
  })
  if (res.code !== 200) throw new Error(`login ${account} ${JSON.stringify(res)}`)
  return res.data
}
async function uploadImage(token) {
  const buf = readFileSync(IMAGE)
  const { stdout } = await execFileAsync('curl', [
    '-sS', `${API}/files/upload`,
    '-H', `Authorization: Bearer ${token}`,
    '-F', `file=@${IMAGE};type=image/jpeg`,
  ])
  const res = JSON.parse(stdout)
  if (res.code !== 200) throw new Error(`upload ${stdout}`)
  return res.data
}
async function createGoods(token, title) {
  const file = await uploadImage(token)
  const url = file.url
  const suggest = await api('POST', '/price/suggest', {
    token,
    body: { categoryId: CAT, condition: 'GOOD', originalPrice: 199, title },
  })
  const res = await api('POST', '/goods', {
    token,
    body: {
      title,
      description: '联调测试商品，请勿当真购买。',
      price: suggest.data?.suggestedPrice || 88,
      originalPrice: 199,
      condition: 'GOOD',
      tradeMethod: 'FACE',
      categoryId: CAT,
      coverUrl: url,
      images: [url],
    },
  })
  if (res.code !== 200) throw new Error(`create ${title} ${JSON.stringify(res)}`)
  return { goods: res.data, suggest: suggest.data }
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

const browser = await chromium.launch({ headless: true })
const opts = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }
try {
  const adminSession = await apiLogin('admin', 'admin123')
  const user1Session = await apiLogin('user1', '123456')
  const user2Session = await apiLogin('user2', '123456')

  const guest = await browser.newContext(opts)
  const g = await guest.newPage()
  await check('游客首页含推荐和公告并打开公告详情', async () => {
    await g.goto(`${BASE}/`)
    await g.getByTestId('home-page').waitFor()
    await g.getByTestId('announce-bar').waitFor()
    await shot(g, 'home')
    await g.goto(`${BASE}/announcements/9100000000000000001`)
    await g.getByTestId('announcement-detail').waitFor()
    const text = await g.getByTestId('announcement-detail').innerText()
    if (!text.includes('欢迎使用校园二手交易平台')) throw new Error(text.slice(0, 200))
    await shot(g, 'announcement')
  })
  await check('游客详情页含相似推荐', async () => {
    await g.goto(`${BASE}/search?keyword=${encodeURIComponent('耳机')}`)
    await g.waitForTimeout(600)
    await g.goto(`${BASE}/goods/4000000000000000003`)
    await g.getByTestId('goods-detail').waitFor()
    await g.getByRole('heading', { name: '相似推荐' }).waitFor({ timeout: 8000 })
    await shot(g, 'detail')
  })

  const u1ctx = await browser.newContext(opts)
  await applyAuth(u1ctx, user1Session)
  const u1 = await u1ctx.newPage()

  await check('发布页展示智能定价 sampleCount 并提交审核', async () => {
    const title = `[联调测试] 定价页 ${Date.now().toString().slice(-6)}`
    await u1.goto(`${BASE}/publish`)
    await u1.getByTestId('publish-page').waitFor()
    await u1.locator('.image-uploader input[type=file]').setInputFiles(IMAGE)
    await u1.locator('.image-item').first().waitFor({ timeout: 10000 })
    await u1.getByPlaceholder('简洁描述闲置物品').fill(title)
    await u1.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('联调测试商品，请勿当真购买。')
    await u1.getByPlaceholder('请选择分类').click()
    await u1.locator('.el-cascader-menu').first().getByText('数码电子', { exact: true }).click()
    await u1.locator('.el-cascader-menu').nth(1).getByText('耳机音箱', { exact: true }).click()
    await u1.keyboard.press('Escape')
    await u1.waitForTimeout(250)
    await u1.locator('.el-form-item').filter({ hasText: '成色' }).locator('.el-select__wrapper').click({ force: true })
    await u1.getByRole('option', { name: '轻微使用' }).click({ timeout: 8000 })
    await u1.locator('.el-form-item').filter({ hasText: '交易方式' }).locator('.el-select__wrapper').click({ force: true })
    await u1.getByRole('option', { name: '当面交易' }).click({ timeout: 8000 })
    await u1.getByPlaceholder('购买时的价格').fill('199')
    const suggestResp = u1.waitForResponse((r) => r.url().includes('/price/suggest') && r.ok())
    await u1.getByRole('button', { name: '智能定价' }).click()
    const suggest = await (await suggestResp).json()
    await u1.locator('.suggest-result').waitFor({ timeout: 8000 })
    const box = await u1.locator('.suggest-box').innerText()
    if (!/参考\s+\d+\s+件同类商品/.test(box) && !box.includes('件同类商品')) throw new Error(box)
    notes.push(`UI 智能定价 sampleCount=${suggest.data?.sampleCount}`)
    await shot(u1, 'publish-suggest')
    await u1.getByRole('button', { name: '一键采用建议价' }).click()
    const post = u1.waitForResponse((r) => r.url().includes('/api/v1/goods') && r.request().method() === 'POST')
    await u1.getByRole('button', { name: '提交审核' }).click()
    const body = await (await post).json()
    if (body.code !== 200) throw new Error(JSON.stringify(body))
    await u1.waitForURL('**/user', { timeout: 15000 })
  })

  const ts = Date.now().toString().slice(-6)
  const titleA = `[联调测试] 蓝牙耳机 A ${ts}`
  const titleB = `[联调测试] 蓝牙耳机 B ${ts}`
  const titleC = `[联调测试] 待驳回商品 ${ts}`
  let goodsA, goodsB, goodsC
  await check('准备联调用待审商品（接口创建）', async () => {
    goodsA = (await createGoods(user1Session.accessToken, titleA)).goods
    goodsB = (await createGoods(user1Session.accessToken, titleB)).goods
    goodsC = (await createGoods(user1Session.accessToken, titleC)).goods
    notes.push(`created ${goodsA.id}, ${goodsB.id}, ${goodsC.id}`)
  })

  const adctx = await browser.newContext(opts)
  await applyAuth(adctx, adminSession)
  const ad = await adctx.newPage()

  await check('admin 批量通过与驳回', async () => {
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
    if (selected < 2) throw new Error(`选中 ${selected} / ${count}`)
    await shot(ad, 'admin-audit')
    await ad.getByRole('button', { name: /批量通过/ }).click()
    if (await ad.locator('.el-message-box').isVisible({ timeout: 1500 }).catch(() => false)) await confirmBox(ad)
    await ad.waitForTimeout(1000)
    const after = ad.locator('.el-table__body tr.el-table__row')
    const n = await after.count()
    let found = false
    for (let i = 0; i < n; i++) {
      if ((await after.nth(i).innerText()).includes(titleC)) {
        await after.nth(i).getByRole('button', { name: '驳回' }).click()
        found = true
        break
      }
    }
    if (!found) throw new Error('没有待驳回商品')
    await ad.locator('.el-dialog .el-textarea__inner, .el-dialog textarea').fill('联调测试驳回：信息不全')
    await ad.locator('.el-dialog').getByRole('button', { name: '确认驳回' }).click()
    await ad.waitForTimeout(800)
  })

  await check('卖家看到 auditReason', async () => {
    await u1.goto(`${BASE}/goods/${goodsC.id}`)
    await u1.getByTestId('goods-detail').waitFor()
    const text = await u1.locator('.detail-page').innerText()
    if (!text.includes('联调测试驳回') && !text.includes('信息不全') && !text.includes('审核未通过')) {
      await u1.goto(`${BASE}/user`)
      await u1.waitForTimeout(800)
      const p = await u1.locator('body').innerText()
      if (!p.includes('联调测试驳回') && !p.includes('信息不全') && !p.includes(titleC)) throw new Error(text.slice(0, 300) + ' | ' + p.slice(0, 200))
    }
    await shot(u1, 'audit-reason')
  })

  await check('审核后状态 ON_SHELF / REJECTED', async () => {
    const list = await api('GET', `/admin/goods?keyword=${encodeURIComponent('[联调测试]')}&pageNum=1&pageSize=50`, { token: adminSession.accessToken })
    const recs = list.data?.records || []
    const a = recs.find((i) => i.id === goodsA.id || i.title === titleA)
    const b = recs.find((i) => i.id === goodsB.id || i.title === titleB)
    const c = recs.find((i) => i.id === goodsC.id || i.title === titleC)
    if (a?.status !== 'ON_SHELF') throw new Error(`A ${a?.status}`)
    if (b?.status !== 'ON_SHELF') throw new Error(`B ${b?.status}`)
    if (c?.status !== 'REJECTED') throw new Error(`C ${c?.status}`)
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

  await check('下单到完成并评价，商品变为 SOLD', async () => {
    const created = await buy(u2, goodsA.id, '联调测试下单')
    if (created.code !== 200) throw new Error(JSON.stringify(created))
    const oid = created.data.id
    const after = await api('GET', `/goods/${goodsA.id}`, { token: user2Session.accessToken })
    if (after.data?.status !== 'RESERVED') throw new Error(`下单后 ${after.data?.status}`)
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

  await check('取消订单后商品回到 ON_SHELF', async () => {
    const created = await buy(u2, goodsB.id, '联调测试取消单')
    if (created.code !== 200) throw new Error(JSON.stringify(created))
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    const card = u2.locator('.order-card').filter({ hasText: titleB }).first()
    await card.getByRole('button', { name: '取消订单' }).click()
    await u2.locator('.el-dialog .el-textarea__inner, .el-message-box textarea, .el-textarea__inner').last().fill('联调测试取消')
    await u2.locator('.el-dialog, .el-message-box').getByRole('button', { name: /确认|提交/ }).last().click()
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${created.data.id}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'CANCELLED') throw new Error(`订单 ${order.data?.status}`)
    const g2 = await api('GET', `/admin/goods?keyword=${encodeURIComponent(titleB)}`, { token: adminSession.accessToken })
    const row = (g2.data?.records || []).find((i) => i.id === goodsB.id)
    if (row?.status !== 'ON_SHELF') throw new Error(`商品 ${row?.status}`)
  })

  await check('双方实时聊天：文字、图片、商品卡片', async () => {
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
    await u2.waitForTimeout(1200)
    await u2.locator('.composer .el-textarea__inner').fill('联调测试文字消息 hello')
    await u2.getByRole('button', { name: '发送' }).click()
    await u2.waitForTimeout(1200)
    const noti = await u1.locator('.el-notification').first().isVisible().catch(() => false)
    const badge = await u1.locator('.header-msg .el-badge__content').isVisible().catch(() => false)
    if (!noti && !badge) notes.push('user1 首页未看到通知或未读角标（可能连接稍慢）')
    await shot(u2, 'chat-user2')
    await u1.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1500)
    const t1 = await u1.locator('.message-list').innerText()
    if (!t1.includes('联调测试文字消息')) throw new Error(`user1 未收到文字: ${t1.slice(0, 250)}`)
    await u1.locator('.composer__file').setInputFiles(IMAGE)
    await u1.waitForTimeout(2500)
    await u1.getByTestId('send-goods-card').click()
    await u1.waitForTimeout(1500)
    await shot(u1, 'chat-user1')
    await u2.waitForTimeout(1500)
    if (!(await u2.locator('.message-list .el-image, .bubble.is-type-2').count())) notes.push('user2 聊天区未见图片')
    if (!(await u2.locator('.goods-chip').count())) notes.push('user2 聊天区未见商品卡片')
    const meta = await u1.locator('.bubble-meta').allTextContents()
    if (!meta.some((x) => x.includes('已读'))) notes.push(`已读回执未出现: ${JSON.stringify(meta.slice(-8))}`)
  })

  await guest.close(); await u1ctx.close(); await u2ctx.close(); await adctx.close()
} catch (e) {
  failed.push({ name: '框架异常', error: String(e.stack || e) })
  console.error(e)
} finally {
  await browser.close()
  writeFileSync('/workspace/screens/e2e-report-final.json', JSON.stringify({ passed, failed, notes, backendIssues, shots }, null, 2))
  console.log('\n======= FINAL =======')
  console.log(JSON.stringify({ passed, failed: failed.map((i) => i.name), notes }, null, 2))
  failed.forEach((i) => console.log('FAILED:', i.name, '\n', i.error.split('\n').slice(0, 6).join('\n')))
}
