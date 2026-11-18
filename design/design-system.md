# 校园二手交易智能平台 · 设计规范（Iris Design System v1.0）

> 适用：Vue 3 + Vite + Element Plus 前端（`/workspace/campus-trade/frontend`）
> 文件：`tokens.css`（设计变量）· `element-plus-theme.css`（Element Plus 主题覆盖）· `motion.md`（动效）· `layouts.md`（页面布局）· `prototype/`（高保真原型）· `screenshots/`（效果图）
> 编制：2026-11-18 · 状态：待用户确认，确认后交前端开发

---

## 0. 设计关键词

**年轻 · 轻盈 · 可信 · 有温度**

- **年轻**：鸢尾紫 → 紫罗兰 → 洋红的品牌渐变，搭配珊瑚橙、薄荷绿和柠檬黄做点缀，不再用旧版单一的「校园绿」。
- **轻盈**：大圆角（卡片 20px / 容器 28px）、柔和的多层阴影、玻璃拟态导航、背景网格渐变（mesh）。
- **可信**：学生认证、信誉分、同校标识统一用薄荷绿；安全提示固定放在下单和聊天附近。
- **有温度**：微交互要有反馈：收藏时心形弹跳并迸出粒子，下单成功时撒花，聊天气泡用弹簧动画进入。

> 视觉层级：品牌渐变只给「主行动」（发布、搜索、发送）用；珊瑚橙只给「价格 / 立即下单 / 热门」用；其余元素都用中性色，避免页面花哨。

---

## 1. 色彩

### 1.1 品牌色

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| `--ct-primary` | `#5B5CF0` | `#7B7CFF` | 主按钮、链接、选中态、焦点环 |
| `--ct-primary-hover` | `#7173F5` | `#9596FF` | hover |
| `--ct-primary-active` | `#494AC0` | `#5B5CBC` | 按下 |
| `--ct-primary-soft` | `#EFEFFE` | `#1D1F3D` | 浅色底（选中菜单、标签） |
| `--ct-accent` | `#FF6B4A` | `#FF7A5C` | **价格**、立即下单、热门、未读角标 |
| `--ct-mint` | `#14C9A2` | `#2EE0B8` | 已认证、同校、在线、成功进度 |
| `--ct-sun` | `#FFC53D` | `#FFD166` | **推荐理由**底色、徽章高光 |
| `--ct-sky` | `#38BDF8` | — | 图表辅助色 |

渐变：

```css
--ct-gradient-brand: linear-gradient(135deg, #5b5cf0 0%, #8b5cf6 55%, #c264f0 100%); /* 主按钮/Hero/Logo */
--ct-gradient-hot:   linear-gradient(135deg, #ff6b4a 0%, #ff9a5a 100%);             /* 立即下单 */
--ct-gradient-fresh: linear-gradient(135deg, #14c9a2 0%, #38bdf8 100%);             /* 进度/完成度 */
--ct-gradient-mesh:  三个 radial-gradient 叠加                                       /* 页面顶部氛围背景 */
```

### 1.2 语义色

| 语义 | Light | Dark | Element 变量 |
| --- | --- | --- | --- |
| Success | `#16B07B` | `#2FCB93` | `--el-color-success` |
| Warning | `#F5A524` | `#F7B648` | `--el-color-warning` |
| Danger / Error | `#F04452` | `#FF5C69` | `--el-color-danger` / `--el-color-error` |
| Info | `#8A8FA3` | `#9AA0B8` | `--el-color-info` |

`light-3/5/7/8/9` 和 `dark-2` 按 Element Plus 的算法预先算好（亮色与 `#fff` 混合，暗色与 `#0F1120` 混合），完整列表见 `element-plus-theme.css`。

### 1.3 中性色（偏紫的冷灰，和主色更协调）

`gray-0 #FFFFFF` · `25 #FAFBFE` · `50 #F5F6FB` · `100 #ECEEF6` · `200 #DFE2EE` · `300 #C6CADB` · `400 #9BA1B7` · `500 #6F758C` · `600 #50566C` · `700 #363B50` · `800 #23273B` · `900 #141728`

### 1.4 语义化表面（组件只引用这一层）

| Token | Light | Dark | 说明 |
| --- | --- | --- | --- |
| `--ct-bg` | `#F5F6FB` | `#0B0D18` | 页面底色 |
| `--ct-surface` | `#FFFFFF` | `#14172A` | 卡片 |
| `--ct-surface-2` | `#FAFBFE` | `#191C32` | 卡片里的次级块 |
| `--ct-surface-sunken` | `#EFF1F8` | `#0F1222` | 输入框、凹陷区 |
| `--ct-glass` | `rgba(255,255,255,.68)` | `rgba(20,23,42,.62)` | 玻璃导航、Toast（配合 `backdrop-filter: blur(20px) saturate(1.8)`） |
| `--ct-border` / `-strong` | `#E6E8F2` / `#D3D7E6` | `#252A44` / `#343A5A` | 分割线 / 输入框 hover |
| `--ct-text` / `-2` / `-3` | `#141728` / `#50566C` / `#8A8FA3` | `#ECEEFA` / `#A9AEC8` / `#6F7593` | 主 / 次 / 辅助文字 |
| `--ct-price` | = accent | = accent | 价格专用 |

**对比度**：主文字与背景 ≥ 15:1，次级文字 ≥ 7:1，辅助文字只用于时间、计数等非关键信息（≥ 3:1）。

### 1.5 暗色模式

- 切换方式：`<html class="dark">`，和 Element Plus 官方暗色方案一致；同时兼容 `<html data-theme="dark">`。
- 先引入 `element-plus/theme-chalk/dark/css-vars.css`，再引入本主题，本主题会覆盖其中的主色、表面和边框。
- 暗色不是简单反色：主色提亮一档（`#7B7CFF`）；阴影改为「黑色深投影 + 1px 主色描边光」；商品插画/图片加 `filter: brightness(.9)`，避免刺眼。
- 切换动画：用 View Transitions API，从按钮位置向外做圆形扩散，560ms，`cubic-bezier(.16,1,.3,1)`（代码见 `motion.md` §9）。

---

## 2. 字体

```css
--ct-font-sans: "Inter", "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Hiragino Sans GB",
  "Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC",
  system-ui, -apple-system, "Segoe UI", sans-serif;
--ct-font-num: "Inter", "DIN Alternate", "SF Pro Display", "Roboto", var(--ct-font-sans); /* 价格、统计数字 */
```

- 拉丁字母和数字优先用 Inter（可选：`npm i @fontsource-variable/inter`，在 main.js 里 `import '@fontsource-variable/inter'`；不装时自动回退到系统字体）。
- 数字统一加 `.num` 类：`font-variant-numeric: tabular-nums; letter-spacing: -0.02em`，保证价格和统计数字对齐。

### 字号阶梯

| Token | 桌面 | 移动 (<768) | 行高 | 字重 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `--ct-fs-display` | 44px | 32px | 1.15 | 800 | 首页 Hero 标题 |
| `--ct-fs-h1` | 32px | 26px | 1.2 | 800 | 页面标题（发布闲置） |
| `--ct-fs-h2` | 24px | 20px | 1.2 | 800 | 区块标题（猜你喜欢） |
| `--ct-fs-h3` | 20px | 18px | 1.35 | 800 | 详情页商品名 |
| `--ct-fs-h4` | 17px | 16px | 1.4 | 700/800 | 表单分组、面板标题 |
| `--ct-fs-body-lg` | 16px | 16px | 1.6 | 400 | 引导文案 |
| `--ct-fs-body` | 14px | 14px | 1.6 | 400/600 | 正文基准 |
| `--ct-fs-caption` | 12px | 12px | 1.5 | 400/600 | 时间、辅助说明 |
| `--ct-fs-micro` | 11px | 11px | 1.4 | 700 | 徽章 |
| `--ct-fs-price-lg` | 36px | 30px | 1 | 800 | 详情页价格 |
| `--ct-fs-price` | 22px | 18px | 1 | 800 | 卡片价格 |

字重：400 正文 / 500 辅助强调 / 600 标签和按钮 / 700 小标题 / 800 标题和价格。中文长段落（商品描述）行高用 `--ct-lh-loose: 1.75`。

---

## 3. 圆角

| Token | 值 | 用途 |
| --- | --- | --- |
| `--ct-radius-xs` | 6px | 徽章、kbd |
| `--ct-radius-sm` | 10px | 输入框、小按钮、骨架块 |
| `--ct-radius-md` | 14px | 按钮、下拉菜单、图标按钮 |
| `--ct-radius-lg` | 20px | **商品卡片**、面板、上传格 |
| `--ct-radius-xl` | 28px | Hero、大容器、弹窗、聊天窗口 |
| `--ct-radius-pill` | 999px | 搜索框、Chip、分段控件、Toast |

嵌套规则：内层圆角 = 外层圆角 − 内边距（比如 28px 容器里 8px 内边距的元素用 20px）。

## 4. 阴影层级

| Token | 场景 |
| --- | --- |
| `--ct-shadow-1` | 静止卡片（几乎看不出，主要靠 1px 边框） |
| `--ct-shadow-2` | 浮起元素：分段控件滑块、选中会话、吸顶头部滚动后 |
| `--ct-shadow-3` | **hover 卡片**、Hero 搜索框、下拉、聊天窗口 |
| `--ct-shadow-4` | 弹窗、抽屉、Popover |
| `--ct-shadow-glow` | 主按钮 / Logo 的紫色发光 |
| `--ct-shadow-glow-hot` | 立即下单的珊瑚色发光 |
| `--ct-ring` | 焦点环 `0 0 0 4px rgba(主色,.18)`，所有可聚焦元素统一使用 |

阴影统一带一点紫色（`rgba(39,40,120,…)`），不用纯黑，画面更通透。

## 5. 间距（4 / 8 基准）

`--ct-space-1 4` · `2 8` · `3 12` · `4 16` · `5 20` · `6 24` · `8 32` · `10 40` · `12 48` · `16 64` · `20 80`

- 组件内边距：按钮 0 20px，卡片 24px，商品卡正文 14px 16px 16px。
- 栅格间距 `--ct-gutter: 24px`（移动端 12px）；区块之间纵向间距 56px（`.section`）。
- 内容最大宽度 `--ct-container: 1280px`，左右留白 24px（旧版是 1200px，加宽后首页可以一行放 4 张卡片，不拥挤）。

## 6. 断点

| 名称 | 最小宽度 | 布局 |
| --- | --- | --- |
| `sm` | 640px | 手机横屏 |
| `md` | 768px | 平板竖屏：商品 2 → 3 列，隐藏顶部搜索框，改用搜索图标 |
| `lg` | 1024px | 小屏笔记本：详情页双栏，聊天页三栏改成两栏（隐藏右侧商品栏） |
| `xl` | 1280px | 标准桌面：商品 4 列 |
| `2xl` | 1536px | 宽屏：容器保持 1280px，居中 |

SCSS mixin（建议放在 `src/styles/_breakpoints.scss`）：

```scss
$bp: (sm: 640px, md: 768px, lg: 1024px, xl: 1280px, 2xl: 1536px);
@mixin up($k) { @media (min-width: map-get($bp, $k)) { @content; } }
@mixin down($k) { @media (max-width: map-get($bp, $k) - 0.02px) { @content; } }
```

---

## 7. 核心组件规范（与原型中的类名对应）

| 组件 | 原型类名 | 规格要点 | Element Plus 落地 |
| --- | --- | --- | --- |
| 玻璃导航 | `.app-header` | 高 68px，sticky，`--ct-glass` + blur 20px；滚动超过 8px 后加 `shadow-2` | 改写 `UserLayout.vue` 头部，el-input 外加 `.ct-search` |
| 主按钮 | `.btn-primary` | 品牌渐变 + glow，hover 上浮 2px，按下 scale .96，点击涟漪 | `el-button type="primary"`，主题文件已覆盖 |
| 下单按钮 | `.btn-hot` | 珊瑚渐变，带 loading → 成功打勾 → 撒花的状态机 | `el-button class="ct-btn-hot"` |
| 商品卡片 | `.goods-card` | 圆角 20，封面 1:1，左上成色徽章，右上玻璃收藏按钮，左下地点，hover 显示「快速查看」 | 重写 `GoodsCard.vue` |
| 推荐理由 | `.reason` | 柠檬黄底，带 sparkle 图标，12px/600，文案与推荐接口返回的 reason 一一对应 | 新增 `RecommendReason.vue` |
| Chip | `.chip` | 32px 胶囊；选中时深色反白 | `el-check-tag` 或自定义 |
| 分段控件 | `.seg` | 胶囊底槽 + 白色滑块，滑块移动用弹簧曲线 | `el-segmented`（2.7+ 可用）或自定义 |
| 徽章 | `.badge-*` | 22px 胶囊，primary / mint / hot / sun / gray / glass 六种 | `el-tag`（已改为胶囊无边框） |
| 输入框 | `.input` | 默认无边框凹陷底，hover 出现边框，focus 变白底 + 主色边 + ring | 主题文件已覆盖 `.el-input__wrapper` |
| Toast | `.toast` | 玻璃胶囊，从上方弹入 | `ElMessage`（主题文件已改为玻璃风格） |
| 骨架屏 | `.sk-card` | 与商品卡尺寸一致，shimmer 扫光 1.4s | `el-skeleton` + 自定义 template |

---

## 8. 在 main.js 中引入

1）把 `design/tokens.css`、`design/element-plus-theme.css` 复制到 `frontend/src/styles/`。

2）`main.js` 中的引入**顺序**（替换现有的样式 import 部分）：

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { MotionPlugin } from '@vueuse/motion'          // 动效（见 motion.md）

import 'element-plus/dist/index.css'                    // 1. Element 基础样式
import 'element-plus/theme-chalk/dark/css-vars.css'     // 2. Element 官方暗色变量（html.dark 生效）
import './styles/tokens.css'                            // 3. 设计变量（亮/暗）
import './styles/element-plus-theme.css'                // 4. Element 主题覆盖
import './styles/index.scss'                            // 5. 全局样式（见下方迁移说明）

import App from './App.vue'
import router from './router'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) app.component(key, component)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(MotionPlugin)
app.mount('#app')
```

3）暗色切换（推荐用 `@vueuse/core` 的 `useDark`，它默认就是给 html 加 `dark` 类）：

```js
// src/composables/useTheme.js
import { useDark, useToggle } from '@vueuse/core'
export const isDark = useDark({ storageKey: 'ct-theme', valueDark: 'dark', valueLight: '' })
export const toggleDark = useToggle(isDark)
```

为了避免首屏先白后黑的闪烁，在 `index.html` 的 `<head>` 里内联：

```html
<script>
  (function () {
    var t = localStorage.getItem('ct-theme');
    if (t === 'dark' || (!t && matchMedia('(prefers-color-scheme: dark)').matches)) document.documentElement.classList.add('dark');
  })();
</script>
```

4）**旧变量迁移**：现有 `styles/index.scss` 和各组件用的是 `--color-primary`、`--color-text` 等变量。建议在 `index.scss` 里把它们改成别名，旧组件不用改也能先换上新皮肤，之后再逐个重构：

```scss
:root, html.dark {
  --color-primary: var(--ct-primary);
  --color-primary-light: var(--ct-primary-hover);
  --color-primary-dark: var(--ct-primary-active);
  --color-primary-bg: var(--ct-primary-soft);
  --color-text: var(--ct-text);
  --color-text-secondary: var(--ct-text-2);
  --color-border: var(--ct-border);
  --color-bg: var(--ct-bg);
  --color-surface: var(--ct-surface);
  --header-height: var(--ct-header-h);
}
body { font-family: var(--ct-font-sans); background: var(--ct-bg); color: var(--ct-text); }
```

同时删掉 `index.scss` 里原来手写的 `--el-color-primary*`（现在由主题文件统一管理），并把 `GoodsCard.vue` 中写死的 `#e85d4c`、`rgba(26,168,122,…)` 换成 `var(--ct-price)`、`var(--ct-mint)`。

5）ECharts（管理后台）配色建议：

```js
const palette = ['#5B5CF0', '#14C9A2', '#FF6B4A', '#FFC53D', '#EC4899', '#38BDF8']
// 折线：smooth: true, lineStyle.width: 3, areaStyle 用主色 28% → 0% 的纵向渐变
// 坐标轴/分割线：取 getComputedStyle(document.documentElement) 中的 --ct-border、--ct-text-3，切换暗色时 chart.setOption 刷新
```

---

## 9. 文案与图标

- 图标：继续用 `@element-plus/icons-vue`（已安装）；原型用的是 Lucide 风格的 2px 描边图标，想更统一可以换成 `lucide-vue-next`（可选依赖）。
- 价格写法统一为「¥」+ 千分位，小数为 .00 时省略。原价显示在现价后面，加删除线。
- 推荐理由文案必须和后端返回一致（方案 5.5 节）：「根据你选择的兴趣分类」「与你最近浏览的商品同属一类且价格接近」「与当前商品成色和价格相近」「同校最近热门」。
- 原型里的数字、人名、商品都是**示例数据**；论文截图请使用真实系统画面（方案第 10 章要求）。

## 补充约定（2026-11-18，前端实现答疑）
- 状态徽章：已预订用 `badge-sun`，已售出用 `badge-gray`；`badge-hot` 只用于热门、降价等正向强调。
- 按钮层级：立即购买用 `ct-btn-hot`；收藏用圆形心形图标按钮；联系卖家用描边按钮（ghost）；举报用 `el-button link type="info"`。
- 危险操作（删除、封禁等）用 Element 原生红色 danger，不再套珊瑚渐变；主题里已删掉 `.el-button--danger.is-round` 的渐变规则。
- 后台菜单待办数：选中项上用白底珊瑚字，未选中项上用珊瑚底白字。
- 折线图：只有主色那条加渐变面积，其余线不加面积，对比周期的线用虚线。
- 聊天气泡：对方气泡用 `--ct-surface` 底、1px `--ct-border` 描边，暗色模式同样处理；自己的气泡用品牌渐变，不加描边。
- 顶栏搜索：用纯胶囊搜索框，不用 el-input 的 append 写法，按回车提交。
- 尺寸：管理端用 `el-config-provider size="small"`，用户端控件高 38。
- 数字字体：引入 `@fontsource-variable/inter`，字体栈以 "Inter Variable" 开头。
- Logo 文字定为「校园二手」。
- 接口没有数据的模块不做假数据：直接去掉，或换成有真实数据的模块，栅格重新排满，不留空位。首页的分段切换保留「推荐」和「最新」。
- 推荐卡片没有 reason 字段时，不显示理由条，卡片高度收紧。
- 后台 KPI 下方写「近 7 天 +N」，用薄荷绿小字，不加箭头。
- 智能定价样本少于 5 时：显示 badge-gray「同类样本较少」和参考价，隐藏区间条和分布图。
- 暗色模式：图表在主题切换时重新设置 option（轴线和网格线用 --ct-border，文字用 --ct-text-2，tooltip 底色用 --ct-surface-2）；商品图加 brightness(.92)，hover 时恢复；顶栏的切换按钮放在头像左边；账号设置页提供「浅色 / 深色 / 跟随系统」切换，存 localStorage，默认跟随系统；不使用纯黑底。
