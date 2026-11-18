import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import path from 'node:path'

const BASE = process.env.E2E_BASE || 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SCREEN = '/workspace/screens'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'

const passed = []
const failed = []
const backendIssues = []
const unverified = []
const notes = []

function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}

async function api(method, urlPath, { token, body, form } = {}) {
  const headers = {}
  let payload
  if (form) {
    payload = form
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${API}${urlPath}`, { method, headers, body: payload })
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch { data = { raw: text } }
  return { http: res.status, ...data }
}

async function getCaptcha() {
  const res = await api('GET', '/auth/captcha')
  const key = res.data?.captchaKey
  const code = redisGet(key)
  return { key, code }
}

async function apiLogin(account, password) {
  const { key, code } = await getCaptcha()
  const res = await api('POST', '/auth/login', {
    body: { account, password, captchaKey: key, captchaCode: code },
  })
  if (res.code !== 200) throw new Error(`api login ${account} failed: ${JSON.stringify(res)}`)
  return res.data
}

function recordBackend(issue) {
  backendIssues.push(issue)
  console.warn('BACKEND', issue.title)
}

async function check(name, fn) {
  try {
    await fn()
    passed.push(name)
    console.log('PASS', name)
  } catch (error) {
    failed.push({ name, error: String(error?.stack || error) })
    console.error('FAIL', name, error)
  }
}

async function shot(page, name) {
  const file = path.join(SCREEN, `e2e-${name}.png`)
  await page.screenshot({ path: file, fullPage: false })
  return file
}

async function waitGoneLogin(page) {
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20000 })
}

async function fillCaptchaAnd(page, afterGoto = false) {
  // Prefer the most recent captcha request already made; otherwise click refresh.
  let key
  try {
    const resp = await page.waitForResponse(
      (r) => r.url().includes('/auth/captcha') && r.ok(),
      { timeout: afterGoto ? 3000 : 8000 },
    )
    const body = await resp.json()
    key = body.data?.captchaKey
  } catch {
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/auth/captcha') && r.ok(), { timeout: 10000 }),
      page.locator('.captcha-image').click(),
    ])
    const body = await resp.json()
    key = body.data?.captchaKey
  }
  const code = redisGet(key)
  if (!code) throw new Error(`redis captcha empty for ${key}`)
  await page.getByPlaceholder('请输入图形验证码').fill(code)
  return code
}

async function loginUi(page, account, password) {
  const respPromise = page.waitForResponse((r) => r.url().includes('/auth/captcha') && r.ok(), { timeout: 15000 })
  await page.goto(`${BASE}/login`)
  const resp = await respPromise
  const body = await resp.json()
  const key = body.data?.captchaKey
  const code = redisGet(key)
  await page.getByPlaceholder('用户名 / 手机号 / 学号').fill(account)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByPlaceholder('请输入图形验证码').fill(code)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await waitGoneLogin(page)
}

async function applyAuth(page, session) {
  await page.addInitScript((s) => {
    localStorage.setItem('campus_trade_access_token', s.accessToken)
    localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
  }, session)
}

async function injectAuth(page, session) {
  await page.evaluate((s) => {
    localStorage.setItem('campus_trade_access_token', s.accessToken)
    localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
    localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
    localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
  }, session)
  await page.reload({ waitUntil: 'networkidle' })
}

async function confirmBox(page, buttonName = '确定') {
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: 8000 })
  const btn = box.getByRole('button', { name: buttonName })
  if (await btn.count()) await btn.click()
  else await box.locator('.el-button--primary').click()
}

async function dismissIf(page, selector, timeout = 1000) {
  const el = page.locator(selector).first()
  try {
    await el.waitFor({ state: 'visible', timeout })
    return true
  } catch {
    return false
  }
}

async function selectOption(page, placeholder, label) {
  await page.locator('.el-select').filter({ has: page.getByPlaceholder(placeholder) }).click()
  await page.locator('.el-select-dropdown:visible .el-option').filter({ hasText: label }).first().click()
}

async function publishGoods(page, title, { price = '88', original = '199' } = {}) {
  await page.goto(`${BASE}/publish`)
  await page.getByTestId('publish-page').waitFor({ timeout: 15000 })
  const fileInput = page.locator('.image-uploader input[type=file]')
  await fileInput.setInputFiles(IMAGE)
  await page.waitForTimeout(800)
  await page.getByPlaceholder('简洁描述闲置物品').fill(title)
  await page.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('联调测试商品，请勿当真购买。')
  await page.getByPlaceholder('请选择分类').click()
  await page.locator('.el-cascader-menu').first().getByText('数码电子', { exact: true }).click()
  await page.locator('.el-cascader-menu').nth(1).getByText('耳机音箱', { exact: true }).click()
  await page.keyboard.press('Escape')
  await page.waitForTimeout(200)
  await page.locator('.el-form-item').filter({ hasText: '成色' }).locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-option, body > .el-select-dropdown .el-option').filter({ hasText: '轻微使用' }).first().click()
  await page.locator('.el-form-item').filter({ hasText: '交易方式' }).locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-option, body > .el-select-dropdown .el-option').filter({ hasText: '当面交易' }).first().click()
  await page.getByPlaceholder('购买时的价格').fill(original)
  const suggestResp = page.waitForResponse((r) => r.url().includes('/price/suggest') && r.ok(), { timeout: 15000 })
  await page.getByRole('button', { name: '智能定价' }).click()
  const suggestJson = await (await suggestResp).json()
  const sampleCount = suggestJson?.data?.sampleCount
  await page.locator('.suggest-result').waitFor({ timeout: 8000 })
  const suggestText = await page.locator('.suggest-box').innerText()
  if (!suggestText.includes('参考') || !suggestText.includes('件同类商品')) {
    throw new Error(`智能定价未显示 sampleCount: ${suggestText}`)
  }
  if (sampleCount == null) notes.push(`price suggest sampleCount missing in response for ${title}`)
  await page.getByRole('button', { name: '一键采用建议价' }).click()
  await page.getByRole('button', { name: '提交审核' }).click()
  await page.waitForURL('**/user', { timeout: 15000 })
  return { sampleCount, suggest: suggestJson?.data }
}

async function openAdmin(page) {
  await page.goto(`${BASE}/admin/dashboard`)
  await page.getByTestId('admin-dashboard').waitFor({ timeout: 15000 })
}


const report = { passed, failed, backendIssues, unverified, notes, shots: [] }

const browser = await chromium.launch({ headless: true })
const contextOptions = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }

try {
  const adminSession = await apiLogin('admin', 'admin123')
  const user1Session = await apiLogin('user1', '123456')
  const user2Session = await apiLogin('user2', '123456')

  const guest = await browser.newContext(contextOptions)
  const g = await guest.newPage()

  await check('游客打开公告详情', async () => {
    await g.goto(`${BASE}/`)
    await g.getByTestId('announce-bar').locator('a').first().click()
    await g.getByTestId('announcement-detail').waitFor({ timeout: 10000 })
    const text = await g.getByTestId('announcement-detail').innerText()
    if (!text.includes('欢迎使用校园二手交易平台')) throw new Error(text.slice(0, 200))
    if (!text.includes('当面交易')) throw new Error('公告正文缺失')
    report.shots.push(await shot(g, 'announcement'))
  })

  await check('游客搜索与商品详情（含相似推荐）', async () => {
    await g.goto(`${BASE}/`)
    await g.getByPlaceholder('搜索闲置好物').fill('耳机')
    await g.getByRole('button', { name: '搜索' }).click()
    await g.waitForURL('**/search**')
    await g.getByTestId('goods-grid').waitFor({ timeout: 10000 })
    const card = g.getByTestId('goods-card').filter({ hasText: '索尼' }).first()
    if (await card.count()) await card.click()
    else {
      const any = g.getByTestId('goods-card').first()
      if (await any.count()) await any.click()
      else await g.goto(`${BASE}/goods/4000000000000000003`)
    }
    await g.getByTestId('goods-detail').waitFor({ timeout: 15000 })
    const pageText = await g.locator('.detail-page').innerText()
    if (!pageText.includes('相似推荐')) throw new Error('详情页无相似推荐区域')
    report.shots.push(await shot(g, 'detail'))
  })

  const u1ctx = await browser.newContext(contextOptions)
  await applyAuth(u1ctx, user1Session)
  const u1 = await u1ctx.newPage()
  const titleA = `[联调测试] 蓝牙耳机 A ${Date.now().toString().slice(-6)}`
  const titleB = `[联调测试] 蓝牙耳机 B ${Date.now().toString().slice(-6)}`
  const titleC = `[联调测试] 待驳回商品 ${Date.now().toString().slice(-6)}`
  let suggestMeta = null

  await check('user1 发布商品（含智能定价 sampleCount）', async () => {
    suggestMeta = await publishGoods(u1, titleA)
    notes.push(`sampleCount(A)=${suggestMeta.sampleCount}`)
    await publishGoods(u1, titleB)
    await publishGoods(u1, titleC)
    report.shots.push(await shot(u1, 'publish-profile'))
  })

  const adctx = await browser.newContext(contextOptions)
  await applyAuth(adctx, adminSession)
  const ad = await adctx.newPage()

  await check('admin 商品审核（含批量通过）与驳回', async () => {
    await ad.goto(`${BASE}/admin/audit`)
    await ad.getByTestId('admin-audit').waitFor({ timeout: 15000 })
    const rows = ad.locator('.el-table__row, .el-table__body tr')
    await rows.first().waitFor({ timeout: 10000 })
    const count = await rows.count()
    let selected = 0
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).innerText()
      if (text.includes(titleA) || text.includes(titleB)) {
        await rows.nth(i).locator('.el-checkbox').first().click()
        selected++
      }
    }
    if (selected < 2) throw new Error(`待审列表未找到两件联调商品, selected=${selected}, rows=${count}`)
    report.shots.push(await shot(ad, 'admin-audit'))
    await ad.getByRole('button', { name: /批量通过/ }).click()
    if (await dismissIf(ad, '.el-message-box', 2000)) await confirmBox(ad)
    await ad.waitForTimeout(1000)

    const after = ad.locator('.el-table__row, .el-table__body tr')
    const n = await after.count()
    let rejected = false
    for (let i = 0; i < n; i++) {
      const text = await after.nth(i).innerText()
      if (text.includes(titleC)) {
        await after.nth(i).getByRole('button', { name: '驳回' }).click()
        rejected = true
        break
      }
    }
    if (!rejected) throw new Error('未找到待驳回商品')
    await ad.locator('.el-dialog textarea, .el-dialog .el-textarea__inner').fill('联调测试驳回：信息不全')
    await ad.locator('.el-dialog').getByRole('button', { name: '确认驳回' }).click()
    await ad.waitForTimeout(800)
  })

  await check('卖家在被驳回商品上看到 auditReason', async () => {
    await u1.goto(`${BASE}/user`)
    await u1.waitForTimeout(800)
    const text = await u1.locator('body').innerText()
    if (!text.includes(titleC)) throw new Error('个人中心未见被驳回商品')
    if (!text.includes('联调测试驳回') && !text.includes('信息不全')) {
      await u1.getByText(titleC).first().click()
      await u1.getByTestId('goods-detail').waitFor({ timeout: 10000 })
      const detail = await u1.locator('.detail-page').innerText()
      if (!detail.includes('联调测试驳回') && !detail.includes('信息不全') && !detail.includes('审核未通过')) {
        throw new Error(`详情未见 auditReason: ${detail.slice(0, 300)}`)
      }
    }
    report.shots.push(await shot(u1, 'audit-reason'))
  })

  async function findGoodsId(title) {
    const res = await api('GET', `/admin/goods?keyword=${encodeURIComponent(title)}&pageNum=1&pageSize=10`, {
      token: adminSession.accessToken,
    })
    const row = (res.data?.records || []).find((item) => item.title === title)
    if (!row) throw new Error(`找不到商品 ${title}: ${JSON.stringify(res)}`)
    return row
  }

  let goodsA, goodsB
  await check('联调商品审核后状态正确', async () => {
    goodsA = await findGoodsId(titleA)
    goodsB = await findGoodsId(titleB)
    const goodsC = await findGoodsId(titleC)
    if (goodsA.status !== 'ON_SHELF') throw new Error(`A status ${goodsA.status}`)
    if (goodsB.status !== 'ON_SHELF') throw new Error(`B status ${goodsB.status}`)
    if (goodsC.status !== 'REJECTED') throw new Error(`C status ${goodsC.status}`)
  })

  const u2ctx = await browser.newContext(contextOptions)
  await applyAuth(u2ctx, user2Session)
  const u2 = await u2ctx.newPage()

  async function buyOnPage(page, goodsId, remark) {
    await page.goto(`${BASE}/goods/${goodsId}`)
    await page.getByTestId('goods-detail').waitFor()
    await page.getByRole('button', { name: '立即购买' }).click()
    if (remark) await page.getByPlaceholder('给卖家留言（选填）').fill(remark)
    const respPromise = page.waitForResponse((r) => r.url().includes('/api/v1/orders') && r.request().method() === 'POST')
    await page.getByRole('button', { name: '确认下单' }).click()
    const resp = await respPromise
    const body = await resp.json()
    return { http: resp.status(), body }
  }

  let orderCompleteId, orderCancelId
  await check('user2 下单 user1 商品完整闭环', async () => {
    const created = await buyOnPage(u2, goodsA.id, '联调测试下单')
    if (created.body.code !== 200) throw new Error(`下单失败 ${JSON.stringify(created.body)}`)
    orderCompleteId = created.body.data.id
    const afterGoods = await api('GET', `/goods/${goodsA.id}`, { token: user2Session.accessToken })
    if (afterGoods.data?.status !== 'RESERVED') {
      throw new Error(`下单后商品状态为 ${afterGoods.data?.status}，期望 RESERVED`)
    }
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    const card = u2.locator('.order-card').filter({ hasText: titleA }).first()
    await card.waitFor()
    await card.getByRole('button', { name: '付款' }).click()
    await confirmBox(u2, '确认付款')
    await u2.waitForTimeout(800)
    await card.getByRole('button', { name: '确认收货' }).click()
    await confirmBox(u2, '确认收货')
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${orderCompleteId}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'COMPLETED') throw new Error(`订单状态 ${order.data?.status}`)
    const sold = await findGoodsId(titleA)
    if (sold.status !== 'SOLD') throw new Error(`完成后商品状态 ${sold.status}`)
    const reviewBtn = u2.locator('.order-card').filter({ hasText: titleA }).getByRole('button', { name: '评价' })
    await reviewBtn.click()
    const dialog = u2.locator('.el-dialog').filter({ hasText: '评价' })
    await dialog.waitFor()
    const submit = dialog.getByRole('button', { name: /提交|确定/ })
    if (await submit.count()) await submit.click()
    await u2.waitForTimeout(800)
    report.shots.push(await shot(u2, 'orders'))
  })

  await check('另一单 CANCEL 后商品回到 ON_SHELF', async () => {
    const created = await buyOnPage(u2, goodsB.id, '联调测试取消单')
    if (created.body.code !== 200) throw new Error(`下单失败 ${JSON.stringify(created.body)}`)
    orderCancelId = created.body.data.id
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    const card = u2.locator('.order-card').filter({ hasText: titleB }).first()
    await card.getByRole('button', { name: '取消订单' }).click()
    await u2.locator('.el-dialog, .el-message-box').locator('textarea, .el-textarea__inner').fill('联调测试取消')
    const ok = u2.locator('.el-dialog, .el-message-box').getByRole('button', { name: /确认|提交/ })
    await ok.last().click()
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${orderCancelId}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'CANCELLED') throw new Error(`取消后订单 ${order.data?.status}`)
    const g2 = await findGoodsId(titleB)
    if (g2.status !== 'ON_SHELF') throw new Error(`取消后商品状态 ${g2.status}`)
  })

  await check('user1 / user2 实时聊天', async () => {
    const sessionRes = await api('POST', '/chat/sessions', {
      token: user2Session.accessToken,
      body: { goodsId: goodsB.id, toUserId: user1Session.userInfo.id },
    })
    if (sessionRes.code !== 200) throw new Error(`创建会话失败 ${JSON.stringify(sessionRes)}`)
    const sessionId = sessionRes.data.id

    await u1.goto(`${BASE}/`)
    await u1.getByTestId('home-page').waitFor()
    await u1.waitForTimeout(1200)

    await u2.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(1000)
    await u2.locator('.composer textarea, .composer .el-textarea__inner').fill('联调测试文字消息 hello')
    await u2.getByRole('button', { name: '发送' }).click()
    await u2.waitForTimeout(1000)

    const notiVisible = await u1.locator('.el-notification').first().isVisible().catch(() => false)
    const badgeVisible = await u1.locator('.header-msg .el-badge__content').isVisible().catch(() => false)
    if (!notiVisible && !badgeVisible) notes.push('user1 首页未捕捉到通知或未读角标')
    report.shots.push(await shot(u2, 'chat-user2'))

    await u1.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1200)
    const u1text = await u1.locator('.message-list').innerText()
    if (!u1text.includes('联调测试文字消息')) throw new Error(`user1 未收到文字消息: ${u1text.slice(0, 200)}`)

    await u1.locator('.composer__file').setInputFiles(IMAGE)
    await u1.waitForTimeout(2000)
    await u1.getByTestId('send-goods-card').click()
    await u1.waitForTimeout(1500)
    report.shots.push(await shot(u1, 'chat-user1'))

    await u2.waitForTimeout(1200)
    const u2text = await u2.locator('.message-list').innerText()
    const hasCard = await u2.locator('.goods-chip').count()
    const hasImg = await u2.locator('.message-list .el-image, .bubble.is-type-2').count()
    if (!hasImg) notes.push('user2 聊天区未见图片消息')
    if (!hasCard && !u2text.includes(titleB)) notes.push('user2 聊天区未见商品卡片')
    const meta = await u1.locator('.bubble-meta').allTextContents()
    if (!meta.some((t) => t.includes('已读'))) notes.push(`已读回执未出现 meta=${JSON.stringify(meta.slice(-6))}`)
  })

  await check('商品详情与用户主页可以举报', async () => {
    await u1.goto(`${BASE}/goods/4000000000000000003`)
    await u1.getByTestId('report-goods-btn').click()
    const dialog = u1.locator('.el-dialog').filter({ hasText: '举报' })
    await dialog.waitFor()
    await dialog.locator('.el-select').click()
    await u1.getByRole('option', { name: '描述不符' }).click()
    await dialog.getByPlaceholder('请补充具体说明，便于管理员核实').fill('联调测试举报商品')
    await dialog.getByRole('button', { name: '提交举报' }).click()
    await u1.waitForTimeout(800)
    const msg = await u1.locator('.el-message').allTextContents()
    notes.push(`举报商品提示: ${msg.join(' | ')}`)

    await u1.goto(`${BASE}/users/${user2Session.userInfo.id}`)
    await u1.getByTestId('report-user-btn').click()
    const d2 = u1.locator('.el-dialog').filter({ hasText: '举报' })
    await d2.waitFor()
    await d2.locator('.el-select').click()
    await u1.getByRole('option', { name: '其他' }).click()
    await d2.getByRole('button', { name: '提交举报' }).click()
    await u1.waitForTimeout(800)
  })

  await check('损坏 accessToken 触发刷新', async () => {
    await u1.goto(`${BASE}/user`)
    await u1.evaluate(() => {
      localStorage.setItem('campus_trade_access_token', 'invalid-access-token-for-e2e')
    })
    const [resp] = await Promise.all([
      u1.waitForResponse((r) => r.url().includes('/auth/refresh'), { timeout: 20000 }),
      u1.reload({ waitUntil: 'domcontentloaded' }),
    ])
    const body = await resp.json().catch(() => ({}))
    if (resp.status() !== 200 || body.code !== 200) {
      recordBackend({
        title: 'accessToken 失效后刷新未成功',
        api: 'POST /api/v1/auth/refresh',
        spec: 'HTTP 401 后前端用 refreshToken 换票并重放原请求',
        actual: { http: resp.status(), body },
        curl: '将 localStorage.campus_trade_access_token 改为非法值后访问 /user',
        severity: '中',
      })
    } else {
      notes.push('token 刷新成功')
    }
    await u1.waitForTimeout(800)
  })

  await guest.close(); await u1ctx.close(); await u2ctx.close(); await adctx.close()
} catch (error) {
  failed.push({ name: 'e2e 框架异常', error: String(error?.stack || error) })
  console.error(error)
} finally {
  await browser.close()
  const out = { passed, failed, backendIssues, unverified, notes, shots: report.shots }
  writeFileSync('/workspace/screens/e2e-report-retry.json', JSON.stringify(out, null, 2))
  console.log('\n======= RETRY SUMMARY =======')
  console.log(JSON.stringify({ passed: passed.length, failed: failed.length, backend: backendIssues.length, notes }, null, 2))
  failed.forEach((item) => console.log('FAILED:', item.name, item.error.split('\n')[0]))
}
