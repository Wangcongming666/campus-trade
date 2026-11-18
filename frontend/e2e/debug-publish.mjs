import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'

const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
function redisGet(key) {
  return execFileSync('redis-cli', ['get', `captcha:${key}`], { encoding: 'utf8' }).trim()
}
async function apiLogin(account, password) {
  const cap = await (await fetch(`${API}/auth/captcha`)).json()
  const key = cap.data.captchaKey
  const code = redisGet(key)
  const res = await (await fetch(`${API}/auth/login`, {
    method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({account,password,captchaKey:key,captchaCode:code})
  })).json()
  return res.data
}
const session = await apiLogin('user1','123456')
const browser = await chromium.launch({ headless:true })
const ctx = await browser.newContext({ viewport:{width:1280,height:800}, locale:'zh-CN' })
await ctx.addInitScript((s)=>{
  localStorage.setItem('campus_trade_access_token', s.accessToken)
  localStorage.setItem('campus_trade_refresh_token', s.refreshToken)
  localStorage.setItem('campus_trade_expires_in', String(s.expiresIn||7200))
  localStorage.setItem('campus_trade_user_info', JSON.stringify(s.userInfo))
}, session)
const page = await ctx.newPage()
page.on('console', m => console.log('CONSOLE', m.type(), m.text()))
page.on('pageerror', e => console.log('PAGEERROR', e))
await page.goto(`${BASE}/publish`)
await page.getByTestId('publish-page').waitFor()
await page.locator('.image-uploader input[type=file]').setInputFiles('/workspace/campus-trade/frontend/public/mock/g01.jpg')
await page.waitForTimeout(1000)
await page.screenshot({path:'/workspace/screens/debug-publish-1.png'})
console.log('after upload images', await page.locator('.image-item').count())
await page.getByPlaceholder('简洁描述闲置物品').fill('[联调测试] debug')
await page.getByPlaceholder('请选择分类').click()
await page.waitForTimeout(300)
console.log('cascader menus', await page.locator('.el-cascader-menu').count())
console.log('menu0', await page.locator('.el-cascader-menu').nth(0).innerText().catch(e=>e.message))
await page.locator('.el-cascader-menu').first().getByText('数码电子', { exact:true }).click()
await page.waitForTimeout(300)
console.log('menu1', await page.locator('.el-cascader-menu').nth(1).innerText().catch(e=>e.message))
await page.locator('.el-cascader-menu').nth(1).getByText('耳机音箱', { exact:true }).click()
await page.waitForTimeout(300)
await page.screenshot({path:'/workspace/screens/debug-publish-2.png'})
// try various condition selectors
const locators = [
  "getByPlaceholder('请选择成色')",
  "locator('.el-form-item').filter({hasText:'成色'}).locator('.el-select')",
  "locator('.el-select').nth(0)",
]
const item = page.locator('.el-form-item').filter({ hasText: '成色' }).locator('.el-select')
console.log('condition select count', await item.count())
console.log('html snippet', await page.locator('.el-form-item').filter({hasText:'成色'}).innerHTML().then(s=>s.slice(0,400)).catch(e=>e.message))
await item.click()
await page.waitForTimeout(200)
console.log('dropdown', await page.locator('.el-select-dropdown').count(), await page.locator('.el-popper:visible').count())
await page.locator('.el-select-dropdown:visible .el-option, .el-select-dropdown .el-option').filter({hasText:'轻微使用'}).first().click()
await page.screenshot({path:'/workspace/screens/debug-publish-3.png'})
console.log('OK condition selected')
await browser.close()
