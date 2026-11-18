import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
const API = 'http://localhost:8080/api/v1'
const j = async (m, p, b) => (await fetch(API + p, { method: m, headers: b ? { 'Content-Type': 'application/json' } : {}, body: b ? JSON.stringify(b) : undefined })).json()
const cap = await j('GET', '/auth/captcha')
const code = execFileSync('redis-cli', ['get', `captcha:${cap.data.captchaKey}`], { encoding: 'utf8' }).trim()
const s = (await j('POST', '/auth/login', { account: 'user1', password: '123456', captchaKey: cap.data.captchaKey, captchaCode: code })).data
const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } })
await ctx.addInitScript((s) => { localStorage.setItem('campus_trade_access_token', s.accessToken); localStorage.setItem('campus_trade_refresh_token', s.refreshToken); localStorage.setItem('campus_trade_expires_in', String(s.expiresIn || 7200)); localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo)); localStorage.removeItem('campus_trade_publish_draft') }, s)
const page = await ctx.newPage()
const errs = []
page.on('pageerror', (e) => errs.push(e.message))
await page.goto('http://127.0.0.1:5173/publish', { waitUntil: 'networkidle' })
await page.locator('.dropzone input[type=file]').setInputFiles('/workspace/campus-trade/frontend/public/mock/g05.jpg')
await page.locator('.img-cell img').first().waitFor()
await page.getByPlaceholder(/简洁描述闲置物品/).fill('[联调测试] 宿舍护眼台灯（仅截图，不提交）')
await page.locator('.chip-col .chips').first().locator('.ct-chip', { hasText: '生活用品' }).click()
await page.locator('.chips--sub .ct-chip').first().click()
await page.locator('.ct-chip', { hasText: '几乎全新' }).click()
await page.getByPlaceholder('补充成色、入手渠道、交易说明等').fill('九成新，三档调光，毕业带不走，宿舍楼下面交。')
await page.locator('.trade-opt', { hasText: '当面交易' }).click()
await page.getByPlaceholder('入手价，买家会看到省了多少').fill('100')
await page.locator('.ai-card__adopt').waitFor()
await page.locator('.ai-card__adopt').click()
await page.waitForTimeout(1200)
// 整页截图时把固定在视口底部的操作栏放到文档底部，避免出现在页面中间
await page.addStyleTag({ content: '.pub-bar{position:absolute!important;bottom:0!important}.publish-page{position:relative}' })
await page.evaluate(() => window.scrollTo(0, 0))
await page.waitForTimeout(300)
await page.screenshot({ path: '/workspace/screens/redesign-s3-publish.png', fullPage: true })
console.log(errs.length ? errs.join('\n') : 'no errors')
await b.close()
