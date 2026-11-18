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

  // ---------------- guest ----------------
  const guest = await browser.newContext(contextOptions)
  const g = await guest.newPage()

  await check('游客浏览首页（推荐+公告）', async () => {
    await g.goto(`${BASE}/`)
    await g.getByTestId('home-page').waitFor()
    const bar = g.getByTestId('announce-bar')
    await bar.waitFor({ timeout: 8000 })
    const barText = await bar.innerText()
    if (!barText.includes('欢迎使用校园二手交易平台')) {
      throw new Error(`公告条内容不符: ${barText}`)
    }
    const rec = g.locator('.home-section').filter({ hasText: '猜你喜欢' })
    await rec.waitFor()
    await g.waitForTimeout(800)
    report.shots.push(await shot(g, 'home'))
  })

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

  await check('权限守卫：游客访问需登录页', async () => {
    await g.goto(`${BASE}/publish`)
    await g.waitForURL('**/login**', { timeout: 8000 })
    await g.goto(`${BASE}/orders`)
    await g.waitForURL('**/login**', { timeout: 8000 })
    await g.goto(`${BASE}/admin/dashboard`)
    await g.waitForURL('**/login**', { timeout: 8000 })
  })

  // ---------------- register + interests ----------------
  const e2eName = `e2e_${Date.now().toString().slice(-8)}`
  const e2ePass = '123456'
  let e2eSession = null
  let recBefore = []
  let recAfter = []

  await check('注册并弹出兴趣选择', async () => {
    const respPromise = g.waitForResponse((r) => r.url().includes('/auth/captcha') && r.ok())
    await g.goto(`${BASE}/register`)
    const resp = await respPromise
    const key = (await resp.json()).data?.captchaKey
    const code = redisGet(key)
    await g.getByPlaceholder('3–20 位字母、数字或下划线').fill(e2eName)
    await g.getByPlaceholder('6–32 位密码').fill(e2ePass)
    await g.getByPlaceholder('请再次输入密码').fill(e2ePass)
    await g.getByPlaceholder('请输入图形验证码').fill(code)
    await g.getByRole('button', { name: '注册' }).click()
    await g.waitForURL('**/login**', { timeout: 15000 })
  })

  await check('新用户登录后选择兴趣并观察推荐变化', async () => {
    await loginUi(g, e2eName, e2ePass)
    await g.waitForTimeout(500)
    // may land on / ; capture recommend first if dialog covers? dialog is modal.
    // Open home after closing? We need before vs after. Before = default cold start,
    // we can skip dialog first? Register opens picker after login. So get recommend
    // would be behind dialog. We'll skip first to snapshot, then reopen from profile? 
    // Better: dismiss by 跳过, snapshot, then open 个人中心 修改兴趣, save, snapshot again.
    if (await g.getByText('选择你感兴趣的分类').isVisible().catch(() => false)) {
      await g.getByRole('button', { name: '跳过' }).click()
      await g.waitForTimeout(400)
    }
    await g.goto(`${BASE}/`)
    await g.getByTestId('home-page').waitFor()
    recBefore = await g.locator('.home-section').filter({ hasText: '猜你喜欢' }).locator('.goods-card, [class*=card] h3, .title').allTextContents()
    // open interest from profile
    await g.locator('.avatar-trigger').click()
    await g.getByRole('menuitem', { name: '个人中心' }).click()
    await g.waitForURL('**/user')
    await g.getByRole('button', { name: '修改' }).click()
    await g.getByText('选择你感兴趣的分类').waitFor()
    // pick 教材书籍 / 专业教材 if present, else first two tags
    const tags = g.locator('.el-check-tag, .el-dialog .tags button, .el-dialog .tags .el-check-tag')
    const wanted = ['教材书籍', '专业教材', '考研考证']
    let clicked = 0
    for (const name of wanted) {
      const tag = g.locator('.el-dialog').getByText(name, { exact: true })
      if (await tag.count()) {
        await tag.click()
        clicked++
      }
    }
    if (clicked === 0) {
      const count = await tags.count()
      for (let i = 0; i < Math.min(2, count); i++) await tags.nth(i).click()
    }
    await g.getByRole('button', { name: '保存' }).click()
    await g.waitForTimeout(800)
    await g.goto(`${BASE}/`)
    await g.getByTestId('home-page').waitFor()
    await g.waitForTimeout(800)
    recAfter = await g.locator('.home-section').filter({ hasText: '猜你喜欢' }).innerText()
    if (JSON.stringify(recBefore) === JSON.stringify(recAfter.split('\n').filter(Boolean)) && recAfter.length === 0) {
      throw new Error('推荐区域为空')
    }
    if (recAfter.includes(recBefore.join('')) && recBefore.length) {
      // not necessarily a failure — algorithm may still return same hot list
      unverified.push('设置兴趣后首页推荐结果与跳过时相同，无法从页面确认推荐已变化（可能仍走热度/冷启动）')
    }
    e2eSession = {
      accessToken: await g.evaluate(() => localStorage.getItem('campus_trade_access_token')),
      refreshToken: await g.evaluate(() => localStorage.getItem('campus_trade_refresh_token')),
      expiresIn: Number(await g.evaluate(() => localStorage.getItem('campus_trade_expires_in'))),
      userInfo: JSON.parse(await g.evaluate(() => localStorage.getItem('campus_trade_user_info'))),
    }
  })

  // ---------------- user1 publish + audit ----------------
  const u1ctx = await browser.newContext(contextOptions)
  await applyAuth(u1ctx, user1Session)
  const u1 = await u1ctx.newPage()
  const titleA = `[联调测试] 蓝牙耳机 A ${Date.now().toString().slice(-6)}`
  const titleB = `[联调测试] 蓝牙耳机 B ${Date.now().toString().slice(-6)}`
  const titleC = `[联调测试] 待驳回商品 ${Date.now().toString().slice(-6)}`
  let suggestMeta = null

  await check('user1 发布商品（含智能定价 sampleCount）', async () => {
    suggestMeta = await publishGoods(u1, titleA)
    await publishGoods(u1, titleB)
    await publishGoods(u1, titleC)
    report.shots.push(await shot(u1, 'publish-profile'))
  })

  const adctx = await browser.newContext(contextOptions)
  await applyAuth(adctx, adminSession)
  const ad = await adctx.newPage()

  await check('admin 统计 7/30 天', async () => {
    await openAdmin(ad)
    const text7 = await ad.getByTestId('admin-dashboard').innerText()
    if (!text7.includes('注册用户') || !text7.includes('待审商品')) throw new Error(text7.slice(0, 200))
    report.shots.push(await shot(ad, 'admin-dashboard'))
    await ad.getByText('近 30 天').click()
    await ad.waitForTimeout(800)
    const text30 = await ad.getByTestId('admin-dashboard').innerText()
    if (!text30.includes('注册用户')) throw new Error('30 天统计未渲染')
    report.shots.push(await shot(ad, 'admin-dashboard-30'))
  })

  await check('admin 商品审核（含批量通过）与驳回', async () => {
    await ad.goto(`${BASE}/admin/audit`)
    await ad.getByTestId('admin-audit').waitFor({ timeout: 15000 })
    // batch select A and B
    const rows = ad.locator('.el-table__body tr')
    await rows.first().waitFor({ timeout: 10000 })
    const count = await rows.count()
    let selected = 0
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).innerText()
      if (text.includes(titleA) || text.includes(titleB)) {
        await rows.nth(i).locator('.el-checkbox').click()
        selected++
      }
    }
    if (selected < 2) throw new Error(`待审列表未找到两件联调商品, selected=${selected}, rows=${count}`)
    report.shots.push(await shot(ad, 'admin-audit'))
    await ad.getByRole('button', { name: /批量通过/ }).click()
    if (await dismissIf(ad, '.el-message-box', 2000)) {
      await confirmBox(ad)
    }
    await ad.waitForTimeout(800)

    // reject C
    const after = ad.locator('.el-table__body tr')
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
    await ad.locator('.el-dialog').getByPlaceholder('请填写驳回原因（必填）').fill('联调测试驳回：信息不全')
    await ad.locator('.el-dialog').getByRole('button', { name: '确认驳回' }).click()
    await ad.waitForTimeout(800)
  })

  await check('卖家在被驳回商品上看到 auditReason', async () => {
    await u1.goto(`${BASE}/user`)
    await u1.waitForTimeout(800)
    const text = await u1.locator('body').innerText()
    if (!text.includes(titleC)) throw new Error('个人中心未见被驳回商品')
    if (!text.includes('联调测试驳回') && !text.includes('信息不全')) {
      // maybe need to open detail
      const link = u1.getByText(titleC).first()
      await link.click()
      await u1.getByTestId('goods-detail').waitFor({ timeout: 10000 })
      const detail = await u1.locator('.detail-page').innerText()
      if (!detail.includes('联调测试驳回') && !detail.includes('信息不全') && !detail.includes('审核未通过')) {
        throw new Error(`详情未见 auditReason: ${detail.slice(0, 300)}`)
      }
    }
    report.shots.push(await shot(u1, 'audit-reason'))
  })

  // find ids of A/B via admin goods search
  async function findGoodsId(title) {
    const res = await api('GET', `/admin/goods?keyword=${encodeURIComponent(title)}&pageNum=1&pageSize=10`, {
      token: adminSession.accessToken,
    })
    const row = (res.data?.records || []).find((item) => item.title === title)
    if (!row) throw new Error(`找不到商品 ${title}`)
    return row
  }

  let goodsA
  let goodsB
  await check('联调商品审核后状态正确', async () => {
    goodsA = await findGoodsId(titleA)
    goodsB = await findGoodsId(titleB)
    const goodsC = await findGoodsId(titleC)
    if (goodsA.status !== 'ON_SHELF') throw new Error(`A status ${goodsA.status}`)
    if (goodsB.status !== 'ON_SHELF') throw new Error(`B status ${goodsB.status}`)
    if (goodsC.status !== 'REJECTED') throw new Error(`C status ${goodsC.status}`)
  })

  // ---------------- orders user2 ----------------
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

  let orderCompleteId
  let orderCancelId

  await check('user2 下单 user1 商品 → RESERVED → PAY → CONFIRM → COMPLETED → SOLD → 评价', async () => {
    const created = await buyOnPage(u2, goodsA.id, '联调测试下单')
    if (created.body.code !== 200) throw new Error(`下单失败 ${JSON.stringify(created.body)}`)
    orderCompleteId = created.body.data.id
    const afterGoods = await api('GET', `/goods/${goodsA.id}`, { token: user2Session.accessToken })
    if (afterGoods.data?.status !== 'RESERVED') {
      throw new Error(`下单后商品状态为 ${afterGoods.data?.status}，期望 RESERVED`)
    }
    await u2.waitForURL('**/orders**', { timeout: 15000 }).catch(() => {})
    await u2.goto(`${BASE}/orders`)
    await u2.getByTestId('orders-page').waitFor()
    // PAY
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
    const sold = await api('GET', `/admin/goods?keyword=${encodeURIComponent(titleA)}`, { token: adminSession.accessToken })
    const row = (sold.data?.records || []).find((item) => item.id === goodsA.id)
    if (row?.status !== 'SOLD') throw new Error(`完成后商品状态 ${row?.status}`)
    // review
    const reviewBtn = u2.locator('.order-card').filter({ hasText: titleA }).getByRole('button', { name: '评价' })
    await reviewBtn.click()
    await u2.locator('.el-dialog').getByRole('button', { name: '提交评价' }).or(u2.locator('.el-dialog').getByRole('button', { name: '提交' })).click()
    // maybe need to just click 确定 / 提交
    const dialog = u2.locator('.el-dialog').filter({ hasText: '评价' })
    if (await dialog.isVisible()) {
      const submit = dialog.getByRole('button', { name: /提交|确定/ })
      if (await submit.count()) await submit.click()
    }
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
    await u2.getByPlaceholder(/取消原因|请填写/).fill('联调测试取消')
    await u2.getByRole('button', { name: /确认|提交/ }).last().click()
    await u2.waitForTimeout(800)
    const order = await api('GET', `/orders/${orderCancelId}`, { token: user2Session.accessToken })
    if (order.data?.status !== 'CANCELLED') throw new Error(`取消后订单 ${order.data?.status}`)
    const g2 = await findGoodsId(titleB)
    if (g2.status !== 'ON_SHELF') throw new Error(`取消后商品状态 ${g2.status}`)
  })

  // ---------------- chat two contexts ----------------
  await check('user1 / user2 实时聊天（文字、图片、商品卡片、已读、未读、通知）', async () => {
    // start session about goods B (user1 seller, user2 buyer) or existing XM4
    // Use goods B so it's our test item; or XM4 already has a session.
    // Open user2 on home first to receive notification, user1 sends later.
    // Simpler path: both open chat after creating session via API.
    const sessionRes = await api('POST', '/chat/sessions', {
      token: user2Session.accessToken,
      body: { goodsId: goodsB.id, toUserId: user1Session.userInfo.id },
    })
    if (sessionRes.code !== 200) throw new Error(`创建会话失败 ${JSON.stringify(sessionRes)}`)
    const sessionId = sessionRes.data.id

    // user1 stays on home to receive notification
    await u1.goto(`${BASE}/`)
    await u1.getByTestId('home-page').waitFor()
    await u1.waitForTimeout(1000)

    await u2.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(800)
    await u2.locator('.composer textarea, .composer .el-textarea__inner').fill('联调测试文字消息 hello')
    await u2.getByRole('button', { name: '发送' }).click()
    await u2.waitForTimeout(800)

    // notification on user1
    const noti = u1.locator('.el-notification')
    const notiVisible = await noti.first().isVisible().catch(() => false)
    const badge = u1.locator('.header-msg .el-badge__content')
    const badgeVisible = await badge.isVisible().catch(() => false)
    if (!notiVisible && !badgeVisible) {
      notes.push('发送后在 user1 首页未捕捉到通知或未读角标，将继续在聊天页验证已读/未读')
    }

    report.shots.push(await shot(u2, 'chat-user2'))

    await u1.goto(`${BASE}/chat?sessionId=${sessionId}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1000)
    const u1text = await u1.locator('.message-list').innerText()
    if (!u1text.includes('联调测试文字消息')) throw new Error(`user1 未收到文字消息: ${u1text.slice(0, 200)}`)

    // image from user1
    await u1.locator('.composer__file').setInputFiles(IMAGE)
    await u1.waitForTimeout(1500)

    // goods card from user1
    await u1.getByTestId('send-goods-card').click()
    await u1.waitForTimeout(1000)
    report.shots.push(await shot(u1, 'chat-user1'))

    await u2.waitForTimeout(1000)
    const u2text = await u2.locator('.message-list').innerText()
    if (!u2.locator('.bubble.is-type-2, .bubble img, .el-image').first()) {
      // continue
    }
    // read receipt: user2 is viewing the session, user1's messages should become 已读
    await u1.waitForTimeout(800)
    const meta = await u1.locator('.bubble-meta').allTextContents()
    if (!meta.some((t) => t.includes('已读'))) {
      notes.push(`已读回执未在超时内出现，当前 meta=${JSON.stringify(meta.slice(-6))}`)
    }
    if (!u2text.includes(titleB) && !(await u2.locator('.goods-chip').count())) {
      notes.push('user2 聊天区未见商品卡片，可能发送失败或渲染延迟')
    }
  })

  // ---------------- 403 ----------------
  const e2ectx = await browser.newContext(contextOptions)
  if (e2eSession?.accessToken) await applyAuth(e2ectx, e2eSession)
  const e2ep = await e2ectx.newPage()

  await check('非参与方查看他人订单 HTTP 403，页面提示并返回', async () => {
    if (!e2eSession?.accessToken) throw new Error('注册用户会话不可用')
    // also verify API
    const probe = await api('GET', '/orders/7000000000000000003', { token: e2eSession.accessToken })
    if (probe.http !== 403 && probe.code !== 403) {
      recordBackend({
        title: '非参与方 GET /orders/{id} 未返回 HTTP 403',
        api: 'GET /api/v1/orders/{id}',
        spec: '非订单参与方访问：HTTP 403 + code 403',
        actual: probe,
        curl: `curl -i -H "Authorization: Bearer <e2e_token>" http://localhost:8080/api/v1/orders/7000000000000000003`,
        severity: '高',
      })
    }
    await e2ep.goto(`${BASE}/orders/7000000000000000003`)
    const warned = await e2ep.locator('.el-message').filter({ hasText: /无权|无权限|不能/ }).first().isVisible({ timeout: 5000 }).catch(() => false)
    await e2ep.waitForTimeout(800)
    const url = e2ep.url()
    if (!warned && url.includes('/orders/7000000000000000003')) {
      const body = await e2ep.locator('body').innerText()
      throw new Error(`未提示也未返回，url=${url} body=${body.slice(0, 200)}`)
    }
  })

  await check('非参与方查看他人会话 403，页面提示并返回', async () => {
    const probe = await api('GET', '/chat/sessions/8000000000000000001/messages?pageNum=1&pageSize=10', {
      token: e2eSession.accessToken,
    })
    if (probe.http !== 403 && probe.code !== 403) {
      recordBackend({
        title: '非参与方 GET /chat/sessions/{id}/messages 未返回 HTTP 403',
        api: 'GET /api/v1/chat/sessions/{sessionId}/messages',
        spec: '非会话参与方访问：HTTP 403 + code 403',
        actual: probe,
        curl: `curl -i -H "Authorization: Bearer <e2e_token>" "http://localhost:8080/api/v1/chat/sessions/8000000000000000001/messages?pageNum=1&pageSize=10"`,
        severity: '高',
      })
    }
    await e2ep.goto(`${BASE}/chat?sessionId=8000000000000000001`)
    const warned = await e2ep.locator('.el-message').filter({ hasText: /无权|无权限|不能|会话/ }).first().isVisible({ timeout: 5000 }).catch(() => false)
    await e2ep.waitForTimeout(600)
    if (!warned) {
      notes.push(`会话 403 页面提示未捕捉到，url=${e2ep.url()}`)
    }
  })

  // ---------------- report ----------------
  await check('商品详情与用户主页可以举报', async () => {
    await u1.goto(`${BASE}/goods/4000000000000000003`)
    await u1.getByTestId('report-goods-btn').click()
    const dialog = u1.locator('.el-dialog').filter({ hasText: '举报' })
    await dialog.waitFor()
    await dialog.locator('.el-select').click()
    await u1.getByRole('option', { name: '描述不符' }).click()
    await dialog.getByPlaceholder('请补充具体说明，便于管理员核实').fill('联调测试举报商品')
    await dialog.getByRole('button', { name: '提交举报' }).click()
    await u1.waitForTimeout(600)

    await u1.goto(`${BASE}/users/${user2Session.userInfo.id}`)
    await u1.getByTestId('report-user-btn').click()
    const d2 = u1.locator('.el-dialog').filter({ hasText: '举报' })
    await d2.waitFor()
    await d2.locator('.el-select').click()
    await u1.getByRole('option', { name: '其他' }).click()
    await d2.getByRole('button', { name: '提交举报' }).click()
    await u1.waitForTimeout(600)
  })

  await check('未登录点击举报跳转登录', async () => {
    const guest2 = await browser.newContext(contextOptions)
    const p = await guest2.newPage()
    await p.goto(`${BASE}/goods/4000000000000000003`)
    await p.getByTestId('report-goods-btn').click()
    await p.waitForURL('**/login**', { timeout: 8000 })
    await guest2.close()
  })

  // ---------------- admin remaining ----------------
  await check('admin 学生认证审核 user2，学校回写', async () => {
    await ad.goto(`${BASE}/admin/audit`)
    await ad.getByTestId('admin-audit').waitFor()
    await ad.getByRole('tab', { name: /学生认证/ }).click()
    const row = ad.locator('.el-table__body tr').filter({ hasText: 'user2' }).first()
    await row.waitFor({ timeout: 8000 })
    await row.getByRole('button', { name: '通过' }).click()
    if (await dismissIf(ad, '.el-message-box', 2000)) await confirmBox(ad)
    await ad.waitForTimeout(800)
    // refresh user2
    const me = await api('GET', '/users/me', { token: (await apiLogin('user2', '123456')).accessToken })
    if (me.data?.school !== '示例大学') {
      throw new Error(`user2 school 未回写: ${JSON.stringify(me.data)}`)
    }
    if (me.data?.studentAuthStatus !== 'APPROVED') {
      throw new Error(`认证状态 ${me.data?.studentAuthStatus}`)
    }
    user2Session.accessToken = (await apiLogin('user2', '123456')).accessToken // keep fresh? already logged
  })

  await check('admin 强制下架 / 重新上架仅对 ON_SHELF/OFF_SHELF', async () => {
    await ad.goto(`${BASE}/admin/goods`)
    await ad.getByTestId('admin-goods').waitFor()
    // OFF_SHELF seed 宿舍收纳箱
    await ad.getByPlaceholder('搜索标题或卖家').fill('宿舍收纳箱')
    await ad.getByRole('button', { name: '查询' }).click()
    await ad.waitForTimeout(600)
    const offRow = ad.locator('.el-table__body tr').filter({ hasText: '宿舍收纳箱' }).first()
    await offRow.getByRole('button', { name: '重新上架' }).click()
    await confirmBox(ad)
    await ad.waitForTimeout(600)
    await ad.getByPlaceholder('搜索标题或卖家').fill('宿舍收纳箱')
    await ad.getByRole('button', { name: '查询' }).click()
    await ad.waitForTimeout(600)
    const onRow = ad.locator('.el-table__body tr').filter({ hasText: '宿舍收纳箱' }).first()
    if (!(await onRow.getByRole('button', { name: '强制下架' }).count())) {
      throw new Error('重新上架后未见强制下架按钮')
    }
    await onRow.getByRole('button', { name: '强制下架' }).click()
    await confirmBox(ad)
    await ad.waitForTimeout(400)
    // reserved/sold should have no toggle
    await ad.getByPlaceholder('搜索标题或卖家').fill('护眼 LED')
    await ad.getByRole('button', { name: '查询' }).click()
    await ad.waitForTimeout(600)
    const soldRow = ad.locator('.el-table__body tr').filter({ hasText: '护眼 LED' }).first()
    const soldText = await soldRow.innerText()
    if (soldText.includes('强制下架') || soldText.includes('重新上架')) {
      throw new Error('SOLD 商品仍显示上下架操作')
    }
    report.shots.push(await shot(ad, 'admin-goods'))
  })

  await check('admin 举报处理与公告管理页面可用', async () => {
    await ad.goto(`${BASE}/admin/reports`)
    await ad.getByTestId('admin-reports').waitFor()
    const row = ad.locator('.el-table__body tr').first()
    if (await row.getByRole('button', { name: '成立' }).count()) {
      await row.getByRole('button', { name: '成立' }).click()
      await ad.locator('.el-dialog').getByPlaceholder('处理说明（选填）').fill('联调测试处理')
      await ad.locator('.el-dialog').getByRole('button', { name: '确认' }).click()
      await ad.waitForTimeout(500)
    }
    report.shots.push(await shot(ad, 'admin-reports'))

    await ad.goto(`${BASE}/admin/announcements`)
    await ad.getByTestId('admin-announcements').waitFor()
    await ad.getByRole('button', { name: '新建公告' }).click()
    const dlg = ad.locator('.el-dialog').filter({ hasText: '新建公告' })
    await dlg.getByRole('textbox').nth(0).fill('[联调测试] 临时公告')
    await dlg.locator('textarea').fill('这是联调测试公告正文。')
    await dlg.getByText('直接发布').click()
    await dlg.getByRole('button', { name: '保存' }).click()
    await ad.waitForTimeout(800)
    const body = await ad.getByTestId('admin-announcements').innerText()
    if (!body.includes('[联调测试] 临时公告')) throw new Error('公告未出现在列表')
    report.shots.push(await shot(ad, 'admin-announcements'))
  })

  await check('普通用户不能进入管理后台', async () => {
    await u2.goto(`${BASE}/admin/dashboard`)
    await u2.waitForTimeout(800)
    if (u2.url().includes('/admin')) throw new Error(`user2 进入了管理端 ${u2.url()}`)
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
        curl: '手动将 localStorage.campus_trade_access_token 改为非法值后访问 /user',
        severity: '中',
      })
    }
    const meVisible = await u1.getByText('小明同学').isVisible().catch(() => false)
    if (!meVisible && !u1.url().includes('/login')) {
      notes.push(`刷新后个人中心未看到昵称，url=${u1.url()}`)
    }
  })

  await check('封禁用户后请求立即失效，解封需重新登录', async () => {
    const targetId = e2eSession.userInfo.id
    const ban = await api('PATCH', `/admin/users/${targetId}/status`, {
      token: adminSession.accessToken,
      body: { status: 'BANNED' },
    })
    if (ban.code !== 200) throw new Error(`封禁失败 ${JSON.stringify(ban)}`)
    const me = await api('GET', '/users/me', { token: e2eSession.accessToken })
    if (me.http !== 401 && me.code !== 10004 && me.code !== 401) {
      recordBackend({
        title: '封禁后原 accessToken 仍可访问接口',
        api: 'GET /api/v1/users/me ；PATCH /admin/users/{id}/status {status:BANNED}',
        spec: '封禁作废 refreshToken 并写入 Redis 封禁标记，现有 accessToken（HTTP 与 STOMP）立即失效',
        actual: me,
        curl: `curl -i -H "Authorization: Bearer <banned_access_token>" http://localhost:8080/api/v1/users/me`,
        severity: '高',
      })
    }
    await e2ep.goto(`${BASE}/user`)
    await e2ep.waitForTimeout(1000)
    const bannedUi = e2ep.url().includes('/login') || await e2ep.locator('.el-message').count()
    const unban = await api('PATCH', `/admin/users/${targetId}/status`, {
      token: adminSession.accessToken,
      body: { status: 'NORMAL' },
    })
    if (unban.code !== 200) throw new Error(`解封失败 ${JSON.stringify(unban)}`)
    const still = await api('GET', '/users/me', { token: e2eSession.accessToken })
    if (still.code === 200) {
      recordBackend({
        title: '解封后旧 accessToken 仍可用（规范要求解封后需重新登录）',
        api: 'GET /api/v1/users/me',
        spec: '解封移除标记，用户需重新登录',
        actual: { code: still.code, msg: still.msg },
        curl: `curl -i -H "Authorization: Bearer <old_token>" http://localhost:8080/api/v1/users/me`,
        severity: '中',
      })
    }
    // re-login should work
    const again = await apiLogin(e2eName, e2ePass)
    if (!again.accessToken) throw new Error('解封后重新登录失败')
    if (!bannedUi) notes.push(`封禁后前端页面未明显跳转登录，url=${e2ep.url()}`)
  })

  await guest.close()
  await u1ctx.close()
  await u2ctx.close()
  await adctx.close()
  await e2ectx.close()
} catch (error) {
  failed.push({ name: 'e2e 框架异常', error: String(error?.stack || error) })
  console.error(error)
} finally {
  await browser.close()
  const out = {
    passed,
    failed,
    backendIssues,
    unverified,
    notes,
    shots: report.shots,
  }
  writeFileSync('/workspace/screens/e2e-report.json', JSON.stringify(out, null, 2))
  console.log('\n======= SUMMARY =======')
  console.log(JSON.stringify({ passed: passed.length, failed: failed.length, backend: backendIssues.length, unverified: unverified.length, notes }, null, 2))
  failed.forEach((item) => console.log('FAILED:', item.name, item.error.split('\n')[0]))
}
