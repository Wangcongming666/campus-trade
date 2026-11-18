// 阶段 3 回归 + 截图：真实后端（8080）+ Vite（5173），不重启任何服务
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SHOTS = '/workspace/screens'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'
const TS = new Date().toTimeString().slice(0, 8).replace(/:/g, '')
const results = []
const notes = []
const consoleErrors = []
const cleanup = { offShelf: [], orders: [] }

const redisGet = (key) => execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
async function api(method, path, { token, body, params } = {}) {
  const qs = params ? `?${new URLSearchParams(params)}` : ''
  const res = await fetch(API + path + qs, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json()
}
async function apiLogin(account, password) {
  const cap = await api('GET', '/auth/captcha')
  const code = redisGet(cap.data.captchaKey)
  const res = await api('POST', '/auth/login', { body: { account, password, captchaKey: cap.data.captchaKey, captchaCode: code } })
  if (res.code !== 200) throw new Error(`login ${account}: ${res.msg}`)
  return res.data
}
async function check(name, fn) {
  const t = Date.now()
  try {
    const detail = await fn()
    results.push({ name, ok: true, ms: Date.now() - t, detail: detail || '' })
    console.log(`✅ ${name}${detail ? ' — ' + detail : ''}`)
  } catch (e) {
    results.push({ name, ok: false, ms: Date.now() - t, detail: String(e?.message || e).slice(0, 300) })
    console.log(`❌ ${name} — ${String(e?.message || e).slice(0, 300)}`)
  }
}
async function newPage(browser, session, label, opts = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, ...opts })
  if (session) {
    await ctx.addInitScript((s) => {
      localStorage.setItem('campus_trade_access_token', s.accessToken)
      localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
      localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
      localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
    }, session)
  }
  const page = await ctx.newPage()
  page.on('console', (m) => {
    if (m.type() !== 'error') return
    const text = m.text()
    if (/unload|permissions policy/i.test(text)) return
    consoleErrors.push(`[${label}] ${text.slice(0, 240)}`)
  })
  page.on('pageerror', (e) => consoleErrors.push(`[${label}] pageerror ${e.message.slice(0, 240)}`))
  return page
}
const shot = (page, name, fullPage = true) => page.screenshot({ path: `${SHOTS}/redesign-s3-${name}.png`, fullPage })
async function scrollThrough(page) {
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 400) {
      window.scrollTo(0, y)
      await new Promise((r) => setTimeout(r, 120))
    }
    window.scrollTo(0, 0)
  })
  await page.waitForTimeout(1200)
}

const browser = await chromium.launch()
const s1 = await apiLogin('user1', '123456')
const s2 = await apiLogin('user2', '123456')
const sa = await apiLogin('admin', 'admin123')
const u1Id = s1.userInfo.id
const u2Id = s2.userInfo.id

let guest, u1, u2, ad
let pubTitle = `[联调测试] 阶段3 台灯 ${TS}`
let pubGoodsId = ''
let rejectGoodsId = ''

/* 1. 登录页（UI 登录，验证码只读 Redis） */
await check('登录：UI 登录 user1（图形验证码 + 账号密码）', async () => {
  guest = await newPage(browser, null, 'guest')
  const capResp = guest.waitForResponse((r) => r.url().includes('/auth/captcha') && r.ok())
  await guest.goto(`${BASE}/login`)
  const key = (await (await capResp).json()).data.captchaKey
  await guest.getByPlaceholder('用户名 / 手机号 / 学号').fill('user1')
  await guest.getByPlaceholder('请输入密码').fill('123456')
  await guest.getByPlaceholder('请输入图形验证码').fill(redisGet(key))
  await shot(guest, 'login', false)
  await guest.getByRole('button', { name: '登录', exact: true }).click()
  await guest.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 10000 })
  const token = await guest.evaluate(() => localStorage.getItem('campus_trade_access_token'))
  if (!token) throw new Error('未写入 token')
  return `跳转到 ${new URL(guest.url()).pathname}`
})

/* 2. 首页浏览 */
u1 = await newPage(browser, s1, 'user1')
await check('首页：Hero/分类/猜你喜欢/最新发布渲染 + 排序 + 筛选 + 头部搜索', async () => {
  await u1.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await u1.getByTestId('home-page').waitFor()
  await u1.locator('[data-testid="goods-card"]').first().waitFor({ timeout: 10000 })
  const cards = await u1.locator('[data-testid="goods-card"]').count()
  const latest = u1.locator('section', { hasText: '最新发布' }).last()
  await latest.locator('.seg__item', { hasText: '价格↑' }).click()
  await u1.waitForResponse((r) => r.url().includes('/goods?') && r.url().includes('price_asc'))
  await u1.waitForTimeout(800)
  const prices = await latest.locator('.goods-card__now').evaluateAll((els) => els.slice(0, 4).map((e) => Number(e.textContent.replace(/[^\d.]/g, ''))))
  for (let i = 1; i < prices.length; i++) if (prices[i] < prices[i - 1]) throw new Error(`价格升序错误 ${prices}`)
  await latest.locator('.ct-chip', { hasText: '¥50 以下' }).click()
  await u1.waitForResponse((r) => r.url().includes('maxPrice=50'))
  await u1.waitForTimeout(800)
  const filtered = await latest.locator('.goods-card__now').evaluateAll((els) => els.map((e) => Number(e.textContent.replace(/[^\d.]/g, ''))))
  if (filtered.some((p) => p > 50)) throw new Error(`¥50 以下筛选错误 ${filtered}`)
  await latest.locator('.ct-chip', { hasText: '全部' }).click()
  await latest.locator('.seg__item', { hasText: '最新' }).click()
  await u1.waitForTimeout(800)
  const search = u1.getByTestId('header-search')
  await search.click()
  await search.locator('input').fill('耳机')
  await search.locator('input').press('Enter')
  await u1.waitForURL(/\/search\?keyword=/)
  await u1.getByTestId('search-page').waitFor()
  await u1.waitForTimeout(1500)
  await shot(u1, 'search')
  return `首页卡片 ${cards} 张；价格↑ ${prices.join(',')}；¥50 以下 ${filtered.length} 件`
})

await check('首页：卡片收藏 / 取消收藏（心形按钮）', async () => {
  await u1.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  const card = u1.locator('section', { hasText: '最新发布' }).last().locator('[data-testid="goods-card"]', { has: u1.locator('[data-testid="like-btn"]') }).first()
  await card.waitFor()
  const href = await card.getAttribute('href')
  const gid = href.split('/').pop()
  const before = (await api('GET', `/goods/${gid}`, { token: s1.accessToken })).data.favorited
  await card.locator('[data-testid="like-btn"]').click()
  await u1.waitForTimeout(1200)
  const mid = (await api('GET', `/goods/${gid}`, { token: s1.accessToken })).data.favorited
  if (mid === before) throw new Error('收藏状态没有变化')
  await card.locator('[data-testid="like-btn"]').click()
  await u1.waitForTimeout(1200)
  const after = (await api('GET', `/goods/${gid}`, { token: s1.accessToken })).data.favorited
  if (after !== before) throw new Error('取消后未恢复')
  return `goods ${gid}: ${before} → ${mid} → ${after}`
})

await check('首页截图（滚动触发入场动效后整页）', async () => {
  await u1.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await scrollThrough(u1)
  await shot(u1, 'home')
})

/* 3. 商品详情 */
await check('商品详情：图集切换 / 收藏大按钮 / 智能定价参考 / 卖家评价 / 相似推荐', async () => {
  await u1.goto(`${BASE}/goods/4000000000000000003`, { waitUntil: 'networkidle' })
  await u1.getByTestId('goods-detail').waitFor()
  await u1.locator('.info-card .title').waitFor()
  const title = await u1.locator('.info-card .title').innerText()
  const thumbs = await u1.locator('.thumbs__item').count()
  if (thumbs > 1) {
    await u1.locator('.thumbs__item').nth(1).click()
    await u1.waitForTimeout(600)
    if (!(await u1.locator('.stage__count').innerText()).startsWith('2')) throw new Error('图集未切换')
    await u1.locator('.thumbs__item').nth(0).click()
  }
  for (const name of ['联系卖家', '立即购买']) if (!(await u1.getByRole('button', { name }).isVisible())) throw new Error(`缺少按钮 ${name}`)
  if (!(await u1.getByTestId('report-goods-btn').isVisible())) throw new Error('缺少举报')
  const like = u1.locator('.actions [data-testid="like-btn"]')
  const before = (await api('GET', '/goods/4000000000000000003', { token: s1.accessToken })).data.favorited
  await like.click()
  await u1.waitForTimeout(1000)
  const mid = (await api('GET', '/goods/4000000000000000003', { token: s1.accessToken })).data.favorited
  await like.click()
  await u1.waitForTimeout(1000)
  const after = (await api('GET', '/goods/4000000000000000003', { token: s1.accessToken })).data.favorited
  if (mid === before || after !== before) throw new Error(`收藏切换异常 ${before}/${mid}/${after}`)
  const ai = await u1.locator('.ai-price').isVisible()
  await u1.getByRole('tab', { name: /卖家评价/ }).click()
  await u1.waitForTimeout(400)
  const reviews = await u1.locator('.desc-card .review').count()
  await u1.getByRole('tab', { name: '商品描述' }).click()
  await scrollThrough(u1)
  const similar = await u1.locator('.ct-section [data-testid="goods-card"]').count()
  await shot(u1, 'detail')
  return `「${title}」缩略图 ${thumbs}；收藏 ${before}→${mid}→${after}；定价参考 ${ai ? '显示' : '隐藏'}；评价 ${reviews}；相似 ${similar}`
})

/* 4. 发布 */
await check('发布：上传图片 + 两级分类 Chip + 成色/交易方式 + 智能定价 + 提交审核', async () => {
  await u1.goto(`${BASE}/publish`, { waitUntil: 'networkidle' })
  await u1.getByTestId('publish-page').waitFor()
  await u1.evaluate(() => localStorage.removeItem('campus_trade_publish_draft'))
  await u1.locator('.dropzone input[type=file]').setInputFiles(IMAGE)
  await u1.locator('.img-cell img').first().waitFor({ timeout: 10000 })
  await u1.getByPlaceholder(/简洁描述闲置物品/).fill(pubTitle)
  const parent = u1.locator('.chip-col .chips').first().locator('.ct-chip', { hasText: '生活用品' })
  await parent.click()
  const sub = u1.locator('.chips--sub .ct-chip')
  if (await sub.count()) await sub.first().click()
  await u1.locator('.ct-chip', { hasText: '几乎全新' }).click()
  await u1.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('联调测试商品，阶段 3 回归用，九成新，宿舍楼下面交。')
  await u1.locator('.trade-opt', { hasText: '当面交易' }).click()
  await u1.getByPlaceholder('入手价，买家会看到省了多少').fill('100')
  await u1.locator('.ai-card__adopt').waitFor({ timeout: 8000 })
  await u1.locator('.ai-card__adopt').click()
  await u1.waitForTimeout(900)
  const price = await u1.locator('.price-input input').inputValue()
  if (!(Number(price) > 0)) throw new Error(`采用建议价失败 ${price}`)
  const done = await u1.locator('.done-hd b').innerText()
  await u1.evaluate(() => window.scrollTo(0, 0))
  await u1.waitForTimeout(400)
  await shot(u1, 'publish')
  await u1.getByTestId('publish-submit').click()
  await u1.waitForURL(/\/user/, { timeout: 15000 })
  await u1.getByTestId('profile-page').waitFor()
  const mine = await api('GET', '/users/me/goods', { token: s1.accessToken, params: { pageNum: 1, pageSize: 50 } })
  const hit = (mine.data.records || []).find((g) => g.title === pubTitle)
  if (!hit) throw new Error('我的发布里没有找到新商品')
  pubGoodsId = String(hit.id)
  cleanup.offShelf.push(pubGoodsId)
  if (hit.status !== 'PENDING_AUDIT') throw new Error(`状态 ${hit.status}`)
  return `goodsId ${pubGoodsId}，建议价 ¥${price}，完整度 ${done}，状态 ${hit.status}`
})

await check('个人中心：审核中筛选 / 收藏 tab / 评价 tab / 设置 tab（不改密码）', async () => {
  await u1.waitForTimeout(1500)
  const pending = await u1.locator('.my-card', { hasText: pubTitle }).count()
  if (!pending) throw new Error('「审核中」筛选里没有新商品')
  await u1.locator('.seg__item', { hasText: '全部' }).click()
  await u1.waitForTimeout(600)
  const all = await u1.locator('.my-card').count()
  await u1.evaluate(() => window.scrollTo(0, 0))
  await shot(u1, 'profile')
  await u1.locator('.side-nav__item', { hasText: '我的收藏' }).click()
  await u1.waitForURL(/tab=favorites/)
  await u1.waitForTimeout(800)
  await u1.locator('.side-nav__item', { hasText: '收到的评价' }).click()
  await u1.waitForURL(/tab=reviews/)
  await u1.locator('.side-nav__item', { hasText: '账号设置' }).click()
  await u1.waitForURL(/tab=settings/)
  await u1.waitForTimeout(600)
  await shot(u1, 'profile-settings', false)
  return `我的商品 ${all} 件`
})

/* 5. 后台审核 */
ad = await newPage(browser, sa, 'admin')
await check('后台：控制台 KPI / 图表 / 待审核表 + 范围切换', async () => {
  await ad.goto(`${BASE}/admin/dashboard`, { waitUntil: 'networkidle' })
  await ad.getByTestId('admin-dashboard').waitFor()
  await ad.waitForTimeout(2200)
  const kpi = await ad.locator('.kpi').count()
  const badge = await ad.locator('.admin-nav__badge').innerText().catch(() => '')
  const pend = await ad.locator('.pending-table .el-table__row').count()
  await shot(ad, 'admin')
  const resp = ad.waitForResponse((r) => r.url().includes('/admin/stats') && r.url().includes('days=30'))
  await ad.locator('#admin-topbar-extra .seg__item', { hasText: '近 30 天' }).click()
  await resp
  await ad.waitForTimeout(1200)
  const size = await ad.locator('.el-button').first().evaluate((el) => el.className)
  return `KPI ${kpi}；侧栏待审徽章 ${badge}；待审表 ${pend} 行；30 天切换 OK；按钮尺寸类 ${/--small/.test(size) ? 'small' : size}`
})

await check('后台审核：UI 通过一件 + UI 驳回一件（填原因）', async () => {
  const body = {
    title: `[联调测试] 阶段3 驳回用 ${TS}`,
    description: '联调测试商品，用于验证后台驳回流程。',
    categoryId: (await api('GET', `/goods/${pubGoodsId}`, { token: s1.accessToken })).data.categoryId,
    condition: 'GOOD',
    tradeMethod: 'FACE',
    price: 9.9,
    images: ['/mock/g02.jpg'],
    coverUrl: '/mock/g02.jpg',
  }
  const created = await api('POST', '/goods', { token: s1.accessToken, body })
  if (created.code !== 200) throw new Error(`创建驳回用商品失败 ${created.msg}`)
  rejectGoodsId = String(created.data?.id || created.data)
  cleanup.offShelf.push(rejectGoodsId)
  await ad.goto(`${BASE}/admin/audit`, { waitUntil: 'networkidle' })
  await ad.getByTestId('admin-audit').waitFor()
  await ad.waitForTimeout(1200)
  await shot(ad, 'admin-audit', false)
  const row = ad.locator('.el-table__row', { hasText: pubTitle })
  await row.getByRole('button', { name: '通过' }).click()
  await ad.waitForTimeout(1500)
  const st1 = (await api('GET', `/goods/${pubGoodsId}`, { token: s1.accessToken })).data.status
  if (st1 !== 'ON_SHELF') throw new Error(`通过后状态 ${st1}`)
  const row2 = ad.locator('.el-table__row', { hasText: '阶段3 驳回用' })
  await row2.getByRole('button', { name: '驳回' }).click()
  await ad.locator('.el-dialog textarea').fill('联调测试：图片与描述不符，驳回验证')
  await ad.locator('.el-dialog').getByRole('button', { name: '确认驳回' }).click()
  await ad.waitForTimeout(1500)
  const st2 = (await api('GET', `/goods/${rejectGoodsId}`, { token: s1.accessToken })).data
  if (st2.status !== 'REJECTED') throw new Error(`驳回后状态 ${st2.status}`)
  return `通过 ${pubGoodsId} → ${st1}；驳回 ${rejectGoodsId} → ${st2.status}（${st2.auditReason || ''}）`
})

/* 6. 下单（user2 买 user1 的测试商品，随后取消） */
u2 = await newPage(browser, s2, 'user2')
await check('下单：详情页立即购买 → 确认下单状态机 → 跳转订单页；随后 API 取消订单', async () => {
  await u2.goto(`${BASE}/goods/${pubGoodsId}`, { waitUntil: 'networkidle' })
  await u2.getByRole('button', { name: '立即购买' }).click()
  await u2.locator('.el-dialog textarea').fill('联调测试下单，稍后取消')
  await u2.getByTestId('confirm-buy').click()
  await u2.locator('.order-btn.is-success').waitFor({ timeout: 8000 })
  await u2.waitForTimeout(250)
  await shot(u2, 'order-success', false)
  await u2.waitForURL(/\/orders/, { timeout: 8000 })
  await u2.getByTestId('orders-page').waitFor()
  await u2.waitForTimeout(1200)
  await shot(u2, 'orders')
  const list = await api('GET', '/orders', { token: s2.accessToken, params: { role: 'buyer', pageNum: 1, pageSize: 20 } })
  const order = (list.data.records || []).find((o) => String(o.goodsId) === pubGoodsId && o.status === 'PENDING')
  if (!order) throw new Error('没有找到新订单')
  const goodsAfter = (await api('GET', `/goods/${pubGoodsId}`, { token: s2.accessToken })).data.status
  const cancel = await api('PATCH', `/orders/${order.id}/status`, { token: s2.accessToken, body: { action: 'CANCEL', reason: '联调测试取消' } })
  if (cancel.code !== 200) throw new Error(`取消失败 ${cancel.msg}`)
  const goodsBack = (await api('GET', `/goods/${pubGoodsId}`, { token: s2.accessToken })).data.status
  return `订单 ${order.id}，下单后商品 ${goodsAfter}，取消后 ${goodsBack}`
})

/* 7. 聊天收发 */
await check('聊天：详情页联系卖家 → user1 发文字/商品卡 → user2 实时收到并回复 → 已读回执', async () => {
  await u1.goto(`${BASE}/goods/4000000000000000006`, { waitUntil: 'networkidle' })
  await u1.getByRole('button', { name: '联系卖家' }).click()
  await u1.waitForURL(/\/chat\?sessionId=/)
  const sessionId = new URL(u1.url()).searchParams.get('sessionId')
  await u1.getByTestId('chat-page').waitFor()
  await u2.goto(`${BASE}/chat?sessionId=${sessionId}`, { waitUntil: 'networkidle' })
  await u2.getByTestId('chat-page').waitFor()
  await u1.waitForTimeout(1500)
  const text1 = `[联调测试] 阶段3 你好，电饭煲还在吗？${TS}`
  await u1.locator('.composer .el-textarea__inner').fill(text1)
  await u1.getByRole('button', { name: '发送', exact: true }).click()
  await u2.locator('.message-list', { hasText: text1 }).waitFor({ timeout: 10000 })
  const text2 = `[联调测试] 阶段3 在的，今晚可以面交 ${TS}`
  await u2.locator('.composer .el-textarea__inner').fill(text2)
  await u2.getByRole('button', { name: '发送', exact: true }).click()
  await u1.locator('.message-list', { hasText: text2 }).waitFor({ timeout: 10000 })
  await u1.getByTestId('send-goods-card').click()
  await u2.locator('.message-list .goods-chip').last().waitFor({ timeout: 10000 })
  await u1.waitForTimeout(2000)
  const read = await u1.locator('.bubble-row.is-mine .bubble-meta .is-read').count()
  const bubbles = await u1.locator('.bubble-row').count()
  await shot(u1, 'chat', false)
  await shot(u2, 'chat-user2', false)
  return `session ${sessionId}；user1 消息流 ${bubbles} 条；已读回执 ${read} 条`
})

/* 8. 其他页面 + reduced-motion */
await check('reduced-motion：首页/详情在 prefers-reduced-motion: reduce 下正常渲染', async () => {
  const rm = await newPage(browser, s1, 'reduced', { reducedMotion: 'reduce' })
  await rm.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await rm.locator('[data-testid="goods-card"]').first().waitFor()
  const hidden = await rm.evaluate(() => [...document.querySelectorAll('[data-reveal]')].filter((el) => getComputedStyle(el).opacity === '0').length)
  if (hidden) throw new Error(`${hidden} 个 reveal 元素仍是透明`)
  await rm.waitForTimeout(800)
  await shot(rm, 'home-reduced')
  await rm.goto(`${BASE}/goods/4000000000000000003`, { waitUntil: 'networkidle' })
  await rm.getByTestId('goods-detail').waitFor()
  await rm.close()
  return 'reveal 元素全部可见'
})

/* 清理：测试商品下架（卖家本人操作），不删数据 */
await check('清理：[联调测试] 商品由卖家下架', async () => {
  const out = []
  for (const id of cleanup.offShelf) {
    const g = (await api('GET', `/goods/${id}`, { token: s1.accessToken })).data
    if (g?.status === 'ON_SHELF') {
      const r = await api('PATCH', `/goods/${id}/status`, { token: s1.accessToken, body: { status: 'OFF_SHELF' } })
      out.push(`${id}:${r.code === 200 ? 'OFF_SHELF' : r.msg}`)
    } else out.push(`${id}:${g?.status}`)
  }
  return out.join('，')
})

await browser.close()
console.log('\n==== SUMMARY ====')
console.log(`${results.filter((r) => r.ok).length}/${results.length} passed`)
console.log('console errors:', consoleErrors.length ? '\n' + [...new Set(consoleErrors)].join('\n') : 'none')
if (notes.length) console.log('notes:', notes.join('\n'))
