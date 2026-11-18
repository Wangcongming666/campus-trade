// 推荐理由自测：mock（4173 预览，三种 reasonType）+ 真实后端（5173，只读浏览）
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
const API = 'http://localhost:8080/api/v1'
const OUT = '/workspace/screens'
const log = (...a) => console.log(...a)
const j = async (m, p, b) => (await fetch(API + p, { method: m, headers: b ? { 'Content-Type': 'application/json' } : {}, body: b ? JSON.stringify(b) : undefined })).json()
const browser = await chromium.launch()
const errs = []
async function ctxFor(session, theme = 'light') {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, colorScheme: theme })
  await ctx.addInitScript(([s]) => {
    if (s) {
      localStorage.setItem('campus_trade_access_token', s.accessToken)
      localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
      localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200))
      localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
    }
  }, [session])
  const page = await ctx.newPage()
  page.on('console', (m) => { if (m.type() === 'error' && !/unload/.test(m.text())) errs.push(m.text().slice(0, 200)) })
  page.on('pageerror', (e) => errs.push('pageerror ' + e.message))
  return page
}
const readReasons = (page, scope) => page.locator(`${scope} [data-testid="goods-card"]`).evaluateAll((cards) => cards.map((c) => {
  const r = c.querySelector('[data-testid="recommend-reason"]')
  const t = r?.querySelector('.ct-reason__text')
  const cs = r ? getComputedStyle(r) : null
  return {
    title: c.querySelector('.goods-card__title')?.textContent.trim(),
    type: r?.dataset.type || null,
    text: t?.textContent.trim() || null,
    titleAttr: r?.getAttribute('title') || null,
    ellipsis: t ? t.scrollWidth > t.clientWidth : false,
    color: cs?.color, bg: cs?.backgroundColor,
    cardH: Math.round(c.getBoundingClientRect().height),
  }
}))
async function toggle(page) { await page.getByTestId('theme-toggle').click(); await page.waitForTimeout(900) }
const shot = (loc, name) => loc.screenshot({ path: `${OUT}/reason-${name}.png` })

/* ---- mock ---- */
{
  const p = await ctxFor(null)
  await p.goto('http://127.0.0.1:4173/', { waitUntil: 'networkidle' })
  const sec = p.getByTestId('recommend-section')
  await sec.locator('[data-testid="recommend-reason"]').first().waitFor()
  await sec.scrollIntoViewIfNeeded(); await p.waitForTimeout(1200)
  log('MOCK home', JSON.stringify(await readReasons(p, '[data-testid="recommend-section"]'), null, 0))
  await shot(sec, 'mock-home-light'); await toggle(p); await shot(sec, 'mock-home-dark')
  await p.goto('http://127.0.0.1:4173/goods/1920000000000000001', { waitUntil: 'networkidle' })
  const sim = p.locator('section', { hasText: '看了这件的同学还在看' }).last()
  await sim.locator('[data-testid="recommend-reason"]').first().waitFor()
  await sim.scrollIntoViewIfNeeded(); await p.waitForTimeout(1200)
  log('MOCK similar', JSON.stringify(await readReasons(p, 'section:has-text("看了这件的同学还在看")')))
  await shot(sim, 'mock-detail-dark'); await toggle(p); await shot(sim, 'mock-detail-light')
  await p.context().close()
}

/* ---- real ---- */
const cap = await j('GET', '/auth/captcha')
const code = execFileSync('redis-cli', ['get', `captcha:${cap.data.captchaKey}`], { encoding: 'utf8' }).trim()
const s1 = (await j('POST', '/auth/login', { account: 'user1', password: '123456', captchaKey: cap.data.captchaKey, captchaCode: code })).data
{
  const p = await ctxFor(s1)
  await p.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle' })
  const sec = p.getByTestId('recommend-section')
  await sec.locator('[data-testid="recommend-reason"]').first().waitFor()
  await sec.scrollIntoViewIfNeeded(); await p.waitForTimeout(1200)
  const home = await readReasons(p, '[data-testid="recommend-section"]')
  log('REAL home', JSON.stringify(home))
  await shot(sec, 'real-home-light'); await toggle(p); await shot(sec, 'real-home-dark')
  if (process.argv.includes('--no-real-detail')) { await p.context().close() } else {
  // 详情页只打开一次（会记一次浏览），在同一页内切主题截两张
  const gid = '4000000000000000004'
  await p.goto(`http://127.0.0.1:5173/goods/${gid}`, { waitUntil: 'networkidle' })
  const sim = p.locator('section', { hasText: '看了这件的同学还在看' }).last()
  await sim.locator('[data-testid="goods-card"]').first().waitFor({ timeout: 8000 }).catch(() => {})
  await sim.scrollIntoViewIfNeeded(); await p.waitForTimeout(1200)
  log('REAL similar', JSON.stringify(await readReasons(p, 'section:has-text("看了这件的同学还在看")')))
  await shot(sim, 'real-detail-dark'); await toggle(p); await shot(sim, 'real-detail-light')
  await p.evaluate(() => localStorage.removeItem('ct-theme'))
  await p.context().close()
  }
}
/* ---- reason 缺失 / 超长：拦截真实 /recommend/home 响应（只改浏览器里看到的数据） ---- */
{
  const p = await ctxFor(s1)
  await p.route('**/api/v1/recommend/home**', async (route) => {
    const res = await route.fetch(); const body = await res.json()
    if (Array.isArray(body.data) && body.data.length >= 2) {
      delete body.data[0].reason; delete body.data[0].reasonType
      body.data[1].reason = '你收藏过类似的高等数学同济第七版上下册配套习题全解'; body.data[1].reasonType = 'CF'
    }
    await route.fulfill({ response: res, json: body })
  })
  await p.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle' })
  const sec = p.getByTestId('recommend-section')
  await sec.locator('[data-testid="goods-card"]').first().waitFor()
  await sec.scrollIntoViewIfNeeded(); await p.waitForTimeout(1200)
  log('EDGE home', JSON.stringify(await readReasons(p, '[data-testid="recommend-section"]')))
  await shot(sec, 'edge-missing-long-light')
  await p.context().close()
}
log('console errors:', errs.length ? errs.join('\n') : 'none')
await browser.close()
