import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
const BASE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api/v1'
function redisGet(key){return execFileSync('redis-cli',['get',`captcha:${key}`],{encoding:'utf8'}).trim()}
async function apiLogin(account,password){
  const cap=await(await fetch(`${API}/auth/captcha`)).json()
  const res=await(await fetch(`${API}/auth/login`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,password,captchaKey:cap.data.captchaKey,captchaCode:redisGet(cap.data.captchaKey)})})).json()
  return res.data
}
const session=await apiLogin('user1','123456')
const browser=await chromium.launch({headless:true})
const ctx=await browser.newContext({viewport:{width:1280,height:800},locale:'zh-CN'})
await ctx.addInitScript((s)=>{
  localStorage.setItem('campus_trade_access_token',s.accessToken)
  localStorage.setItem('campus_trade_refresh_token',s.refreshToken)
  localStorage.setItem('campus_trade_expires_in',String(s.expiresIn||7200))
  localStorage.setItem('campus_trade_user_info',JSON.stringify(s.userInfo))
},session)
const page=await ctx.newPage()
await page.goto(`${BASE}/publish`)
await page.getByTestId('publish-page').waitFor()
await page.locator('.image-uploader input[type=file]').setInputFiles('/workspace/campus-trade/frontend/public/mock/g01.jpg')
await page.waitForTimeout(800)
await page.getByPlaceholder('简洁描述闲置物品').fill('[联调测试] debug2')
await page.getByPlaceholder('请选择分类').click()
await page.locator('.el-cascader-menu').first().getByText('数码电子',{exact:true}).click()
await page.locator('.el-cascader-menu').nth(1).getByText('耳机音箱',{exact:true}).click()
await page.keyboard.press('Escape')
await page.waitForTimeout(300)
await page.locator('.el-form-item').filter({hasText:'成色'}).locator('.el-select__wrapper').click({force:true})
await page.getByRole('option',{name:'轻微使用'}).click()
await page.locator('.el-form-item').filter({hasText:'交易方式'}).locator('.el-select__wrapper').click({force:true})
await page.getByRole('option',{name:'当面交易'}).click()
await page.getByPlaceholder('购买时的价格').fill('199')
await page.getByRole('button',{name:'智能定价'}).click()
await page.locator('.suggest-result').waitFor({timeout:8000})
console.log(await page.locator('.suggest-box').innerText())
await page.getByRole('button',{name:'一键采用建议价'}).click()
await page.getByRole('button',{name:'提交审核'}).click()
await page.waitForURL('**/user',{timeout:15000})
console.log('PUBLISH OK', page.url())
await page.screenshot({path:'/workspace/screens/debug-publish-ok.png'})
await browser.close()
