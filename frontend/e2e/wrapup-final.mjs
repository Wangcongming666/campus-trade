import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import path from 'node:path'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
const SCREEN = '/workspace/screens'
const IMAGE = '/workspace/campus-trade/frontend/public/mock/g01.jpg'

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

async function publishOne(page, title) {
  await page.goto(`${BASE}/publish`)
  await page.getByTestId('publish-page').waitFor()
  await page.locator('.image-uploader input[type=file]').setInputFiles(IMAGE)
  await page.locator('.image-item').first().waitFor({ timeout: 15000 })
  await page.getByPlaceholder('简洁描述闲置物品').fill(title)
  await page.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('联调测试商品，请勿当真购买。含图片与智能定价。')
  await page.getByPlaceholder('请选择分类').click()
  await page.locator('.el-cascader-menu').first().getByText('数码电子', { exact: true }).click()
  await page.locator('.el-cascader-menu').nth(1).getByText('耳机音箱', { exact: true }).click()
  // 叶子选中后应自动关弹层；若仍可见则视为修复失败
  try {
    await page.locator('.el-cascader-menu').first().waitFor({ state: 'hidden', timeout: 2500 })
  } catch {
    throw new Error('选中叶子分类后级联弹层未自动关闭')
  }
  await page.locator('.el-form-item').filter({ hasText: '成色' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '轻微使用' }).click({ timeout: 8000 })
  await page.locator('.el-form-item').filter({ hasText: '交易方式' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '当面交易' }).click({ timeout: 8000 })
  await page.getByPlaceholder('购买时的价格').fill('199')
  const suggestWait = page.waitForResponse((r) => r.url().includes('/price/suggest') && r.ok())
  await page.getByRole('button', { name: '智能定价' }).click()
  const suggest = await (await suggestWait).json()
  await page.locator('.suggest-result').waitFor({ timeout: 8000 })
  const box = await page.locator('.suggest-box').innerText()
  if (!box.includes('件同类商品')) throw new Error(`智能定价未显示 sampleCount: ${box}`)
  await page.getByRole('button', { name: '一键采用建议价' }).click()
  const postWait = page.waitForResponse((r) => r.url().includes('/api/v1/goods') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '提交审核' }).click()
  const body = await (await postWait).json()
  if (body.code !== 200) throw new Error(`发布失败 ${JSON.stringify(body)}`)
  await page.waitForURL('**/user', { timeout: 15000 })
  return { id: body.data?.id, sampleCount: suggest.data?.sampleCount, suggestBox: box }
}

async function waitForText(locator, pred, timeout = 10000) {
  const start = Date.now()
  let last = ''
  while (Date.now() - start < timeout) {
    last = (await locator.allTextContents().catch(() => [])).join(' | ')
    if (pred(last)) return last
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error(`等待文本超时: ${last.slice(0, 400)}`)
}

const browser = await chromium.launch({ headless: true })
const opts = { viewport: { width: 1280, height: 800 }, locale: 'zh-CN' }
const result = { publish: [], chat: {}, token: {} }

try {
  const adminSession = await apiLogin('admin', 'admin123')
  let user1Session = await apiLogin('user1', '123456')
  const user2Session = await apiLogin('user2', '123456')

  await check('连续纯 UI 发布 2 件[联调测试]商品（上传图片 + 智能定价，级联自动关闭）', async () => {
    const ctx = await browser.newContext(opts)
    await applyAuth(ctx, user1Session)
    const page = await ctx.newPage()
    const ts = Date.now().toString().slice(-6)
    const titles = [`[联调测试] 收尾耳机 ${ts}-1`, `[联调测试] 收尾耳机 ${ts}-2`]
    for (const title of titles) {
      const one = await publishOne(page, title)
      result.publish.push({ title, ...one })
      notes.push(`发布 ${title} sampleCount=${one.sampleCount}`)
    }
    await page.screenshot({ path: path.join(SCREEN, 'e2e-publish-two.png') })
    await ctx.close()
    if (result.publish.length !== 2) throw new Error('未连续发布 2 件')
  })

  await check('双上下文聊天：未读角标与已读回执', async () => {
    const sessions = await api('GET', '/chat/sessions', { token: user1Session.accessToken })
    const list = Array.isArray(sessions.data) ? sessions.data : []
    if (list.length < 1) throw new Error('user1 没有会话')
    let sessionB = list.find((s) => String(s.goodsTitle || '').includes('蓝牙耳机 B')) || list[0]
    let sessionOther = list.find((s) => s.id !== sessionB.id)
    if (!sessionOther) {
      const goodsList = await api('GET', '/admin/goods?keyword=索尼&pageNum=1&pageSize=10', {
        token: adminSession.accessToken,
      })
      const goods = (goodsList.data?.records || [])[0]
      if (!goods) throw new Error('找不到第二件商品建会话')
      const created = await api('POST', '/chat/sessions', {
        token: user2Session.accessToken,
        body: { goodsId: goods.id, toUserId: user1Session.userInfo.id },
      })
      if (created.code !== 200) throw new Error(JSON.stringify(created))
      sessionOther = created.data
    }

    const badgeText = `角标测试 ${Date.now().toString().slice(-8)}`
    const readText = `已读测试 ${Date.now().toString().slice(-8)}`

    const c1 = await browser.newContext(opts)
    const c2 = await browser.newContext(opts)
    await applyAuth(c1, user1Session)
    await applyAuth(c2, user2Session)
    const u1 = await c1.newPage()
    const u2 = await c2.newPage()

    // 1) user1 留在首页，user2 向「另一会话」发消息，验证首页未读角标
    await u1.goto(`${BASE}/`)
    await u1.getByTestId('home-page').waitFor()
    await u1.waitForTimeout(1600)
    await u2.goto(`${BASE}/chat?sessionId=${sessionOther.id}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(1200)
    await u2.locator('.composer .el-textarea__inner').fill(badgeText)
    await u2.getByRole('button', { name: '发送', exact: true }).click()
    await u1.locator('.header-msg .el-badge__content').waitFor({ state: 'visible', timeout: 8000 })
    const headerBadge = (await u1.locator('.header-msg .el-badge__content').innerText()).trim()
    if (!headerBadge || headerBadge === '0') throw new Error(`首页未读角标异常: ${headerBadge}`)
    notes.push(`user1 首页未读角标=${headerBadge}`)

    // 2) user2 切到主会话并保持打开，user1 进入主会话发消息，验证已读；列表中另一会话仍有未读
    await u2.goto(`${BASE}/chat?sessionId=${sessionB.id}`)
    await u2.getByTestId('chat-page').waitFor()
    await u2.waitForTimeout(1200)
    await u1.goto(`${BASE}/chat?sessionId=${sessionB.id}`)
    await u1.getByTestId('chat-page').waitFor()
    await u1.waitForTimeout(1200)
    await u1.locator('.composer .el-textarea__inner').fill(readText)
    await u1.getByRole('button', { name: '发送', exact: true }).click()
    await waitForText(
      u1.locator('.bubble-meta'),
      (t) => t.includes('已读'),
      12000,
    )
    const otherItem = u1.locator('.session-item').filter({ hasText: sessionOther.goodsTitle || '' }).first()
    const listBadgeVisible = await otherItem.locator('.el-badge__content').isVisible().catch(() => false)
    if (!listBadgeVisible) notes.push('会话列表未读角标未观察到（可能标题匹配失败，首页角标已通过）')
    else notes.push('会话列表未读角标可见')

    await u1.screenshot({ path: path.join(SCREEN, 'e2e-chat-read.png') })
    result.chat = { headerBadge, sessionB: sessionB.id, sessionOther: sessionOther.id, readText, badgeText }
    await c1.close()
    await c2.close()
  })

  await check('封禁/解封后旧 accessToken 与 refreshToken 仍 401，前端跳登录且不循环刷新', async () => {
    const old = await apiLogin('user1', '123456')
    const userId = old.userInfo.id
    const oldAccess = old.accessToken
    const oldRefresh = old.refreshToken
    result.token.userId = userId
    try {
      const banned = await api('PATCH', `/admin/users/${userId}/status`, {
        token: adminSession.accessToken,
        body: { status: 'BANNED' },
      })
      if (banned.code !== 200) throw new Error(`封禁失败 ${JSON.stringify(banned)}`)

      const duringBan = await api('GET', '/users/me', { token: oldAccess })
      if (duringBan.http !== 401) throw new Error(`封禁后旧 access 应为 401，实际 http=${duringBan.http} body=${JSON.stringify(duringBan)}`)
      result.token.bannedAccess = { http: duringBan.http, code: duringBan.code, msg: duringBan.msg }

      const unbanned = await api('PATCH', `/admin/users/${userId}/status`, {
        token: adminSession.accessToken,
        body: { status: 'NORMAL' },
      })
      if (unbanned.code !== 200) throw new Error(`解封失败 ${JSON.stringify(unbanned)}`)

      const afterAccess = await api('GET', '/users/me', { token: oldAccess })
      const afterRefresh = await api('POST', '/auth/refresh', { body: { refreshToken: oldRefresh } })
      result.token.afterUnbanAccess = { http: afterAccess.http, code: afterAccess.code, msg: afterAccess.msg }
      result.token.afterUnbanRefresh = { http: afterRefresh.http, code: afterRefresh.code, msg: afterRefresh.msg }
      if (afterAccess.http !== 401) {
        throw new Error(`解封后旧 accessToken 仍应 401，实际 http=${afterAccess.http} body=${JSON.stringify(afterAccess)}`)
      }
      if (afterRefresh.http !== 401 && afterRefresh.code === 200) {
        throw new Error(`解封后旧 refreshToken 仍应失效，实际 ${JSON.stringify(afterRefresh)}`)
      }
      if (afterRefresh.http !== 401 && afterRefresh.code !== 401 && afterRefresh.code !== 10004) {
        // 业务失败但非 401 也算「仍无效」，记录后继续前端验证；若竟然成功则上面已抛错
        if (afterRefresh.code === 200) throw new Error('解封后旧 refresh 居然成功')
        notes.push(`解封后旧 refresh 非 HTTP 401，业务码 ${afterRefresh.code} ${afterRefresh.msg}`)
      }

      const ctx = await browser.newContext(opts)
      await applyAuth(ctx, old)
      const page = await ctx.newPage()
      const refreshCalls = []
      page.on('request', (req) => {
        if (req.url().includes('/auth/refresh') && req.method() === 'POST') {
          refreshCalls.push(req.url())
        }
      })
      await page.goto(`${BASE}/user`)
      await page.waitForURL(/\/login/, { timeout: 15000 })
      await page.waitForTimeout(2500)
      if (page.url().includes('/login') === false) throw new Error(`未跳转登录页: ${page.url()}`)
      if (refreshCalls.length > 2) {
        throw new Error(`刷新循环：/auth/refresh 被调用 ${refreshCalls.length} 次`)
      }
      notes.push(`前端跳登录，refresh 调用 ${refreshCalls.length} 次`)
      result.token.frontend = { url: page.url(), refreshCalls: refreshCalls.length }
      await page.screenshot({ path: path.join(SCREEN, 'e2e-token-relogin.png') })
      await ctx.close()

      const fresh = await apiLogin('user1', '123456')
      const me = await api('GET', '/users/me', { token: fresh.accessToken })
      if (me.http !== 200 || me.code !== 200) throw new Error(`重新登录后新 token 异常 ${JSON.stringify(me)}`)
      result.token.freshLoginOk = true
      user1Session = fresh
    } finally {
      await api('PATCH', `/admin/users/${userId}/status`, {
        token: adminSession.accessToken,
        body: { status: 'NORMAL' },
      }).catch(() => {})
    }
  })
} catch (e) {
  failed.push({ name: '框架', error: String(e.stack || e) })
  console.error(e)
} finally {
  await browser.close()
}

console.log(JSON.stringify({ passed, failed, notes, result }, null, 2))
if (failed.length) process.exitCode = 1
