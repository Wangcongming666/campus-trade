# 动效与交互清单（Motion Spec）

> 原则：**快进慢出、有反馈、不打扰。** 进入用 expo-out（先快后慢），离开用 ease-in 且时长更短，需要「俏皮感」的地方（收藏、气泡、滑块）用 back-out 回弹。单个动效不超过 600ms（装饰性的循环动画除外）。
> 所有效果都能在 `prototype/` 里直接体验，代码片段可以直接放进 Vue SFC 使用。

## 0. 动效 Token（已写入 tokens.css）

| Token | 值 | 用于 |
| --- | --- | --- |
| `--ct-ease-standard` | `cubic-bezier(.2, 0, 0, 1)` | 颜色、背景等一般过渡 |
| `--ct-ease-out` | `cubic-bezier(.16, 1, .3, 1)` | **进入**、上浮、展开（expo-out） |
| `--ct-ease-in` | `cubic-bezier(.4, 0, 1, 1)` | **离开**、收起 |
| `--ct-ease-spring` | `cubic-bezier(.34, 1.56, .64, 1)` | 回弹：心形、气泡、分段滑块、徽章 |
| `--ct-ease-emphasized` | `cubic-bezier(.2, .8, .2, 1)` | 公告滚动、撒花轨迹 |
| `--ct-dur-instant / fast / base / slow / slower` | 100 / 160 / 240 / 360 / 560ms | — |

## 一览表

| # | 动效 | 触发 | 时长 | 缓动 | 实现 |
| --- | --- | --- | --- | --- | --- |
| 1 | 页面切换 | 路由变化 | 出 200ms / 进 560ms | in `(.4,0,1,1)` / out `(.16,1,.3,1)` | 纯 CSS `<Transition>` |
| 2 | 商品卡片 hover | 鼠标悬停 | 卡片 360ms、插图 560ms、快速查看 240ms | `(.16,1,.3,1)` | 纯 CSS |
| 3 | 滚动出现 | 元素进入视口 12% | 560ms，依次延迟 60ms（最多 8 个） | `(.16,1,.3,1)` | `@vueuse/motion`（或 `v-reveal` 指令） |
| 4 | 骨架屏 | 请求中（超过 150ms 才显示） | 扫光 1.4s 循环，切换到内容 240ms | linear / `(.2,0,0,1)` | 纯 CSS + `el-skeleton` |
| 5 | 收藏 | 点击心形 | 心跳 500ms、光环 500ms、粒子 600ms | `(.34,1.56,.64,1)` / `(.16,1,.3,1)` | CSS 关键帧（GSAP 版可选） |
| 6 | 下单按钮 | 点击「立即下单」 | 按压 160ms → loading → 打勾 450ms → 撒花 1.1–1.6s | spring / `(.2,.8,.2,1)` | CSS + GSAP |
| 7 | 聊天气泡进入 | 收到 / 发出消息 | 420ms | `(.34,1.56,.64,1)` | `<TransitionGroup>` 纯 CSS |
| 8 | 推荐轮播 | 自动播放 4.5s / 箭头 / 拖拽 | 翻页 700ms，卡片依次延迟 50ms | `(.16,1,.3,1)` | CSS transform + GSAP |
| 9 | 暗色切换 | 点击太阳 / 月亮按钮 | 圆形扩散 560ms，图标翻转 560ms | `(.16,1,.3,1)` / spring | View Transitions API |
| 10 | 其他微交互 | 见 §10 | — | — | CSS |

---

## 1. 页面切换（router-view transition）

- **效果**：旧页面上移 8px，同时淡出、模糊 4px（200ms）；新页面从下方 16px 处上浮，同时淡入、去模糊（560ms）。
- **触发**：每次路由切换；管理后台子路由用同一套动画，但位移减半。
- **注意**：`mode="out-in"`，配合路由的 `scrollBehavior` 在离开动画结束后再回到顶部。

```vue
<!-- layouts/UserLayout.vue -->
<main class="user-main">
  <router-view v-slot="{ Component, route }">
    <Transition name="page" mode="out-in">
      <component :is="Component" :key="route.path" />
    </Transition>
  </router-view>
</main>

<style>
.page-enter-active { transition: opacity .56s var(--ct-ease-out), transform .56s var(--ct-ease-out), filter .56s var(--ct-ease-out); }
.page-leave-active { transition: opacity .2s var(--ct-ease-in), transform .2s var(--ct-ease-in), filter .2s var(--ct-ease-in); }
.page-enter-from  { opacity: 0; transform: translateY(16px); filter: blur(4px); }
.page-leave-to    { opacity: 0; transform: translateY(-8px); filter: blur(4px); }
</style>
```

```js
// router/index.js：等离开动画结束后再滚动
scrollBehavior(to, from, saved) {
  return new Promise((r) => setTimeout(() => r(saved || { top: 0 }), 200))
}
```

## 2. 商品卡片 hover

| 元素 | 变化 | 时长 / 缓动 |
| --- | --- | --- |
| 卡片 | `translateY(-6px)`，阴影 `shadow-1 → shadow-3`，边框变透明 | 360ms `(.16,1,.3,1)` |
| 封面插图 / 图片 | `scale(1.08) rotate(-3deg)`（实拍图只用 `scale(1.06)`） | 560ms `(.16,1,.3,1)` |
| 「快速查看」条 | 从下方 12px 处滑入并淡入；同时左下角的地点徽章淡出 | 240ms `(.16,1,.3,1)` |
| 收藏按钮 | hover 时 `scale(1.1)`，变为珊瑚色 | 160ms |

```css
.goods-card { transition: transform .36s var(--ct-ease-out), box-shadow .36s var(--ct-ease-out), border-color .36s; }
.goods-card:hover { transform: translateY(-6px); box-shadow: var(--ct-shadow-3); border-color: transparent; }
.goods-card__cover img { transition: transform .56s var(--ct-ease-out); }
.goods-card:hover .goods-card__cover img { transform: scale(1.06); }
.goods-card__quick { opacity: 0; transform: translateY(12px); transition: all .24s var(--ct-ease-out); }
.goods-card:hover .goods-card__quick { opacity: 1; transform: none; }
@media (hover: none) { .goods-card:hover { transform: none; } } /* 触屏设备不做悬停效果 */
```

Hero 区的浮动卡片额外加了 **3D 倾斜**：根据鼠标位置设置 `--rx/--ry`（最大 10–12°），过渡 400ms `(.16,1,.3,1)`，同时有 6s 的 `floatY` 上下漂浮循环。

## 3. 滚动出现（Scroll Reveal）

- **效果**：元素从下方 24px 处上浮，同时淡入；同一组列表依次延迟 60ms，最多延迟到第 8 个（避免长列表等太久）。
- **触发**：元素进入视口 12%，`rootMargin: 0 0 -40px 0`，**只触发一次**。
- **方案 A（推荐，@vueuse/motion）**：

```vue
<GoodsCard
  v-for="(g, i) in list" :key="g.id" :goods="g"
  v-motion
  :initial="{ opacity: 0, y: 24 }"
  :visible-once="{ opacity: 1, y: 0, transition: { duration: 560, delay: (i % 8) * 60, ease: [0.16, 1, 0.3, 1] } }"
/>
```

- **方案 B（零依赖指令）**：`src/directives/reveal.js`

```js
const io = new IntersectionObserver((entries) => entries.forEach((e) => {
  if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target) }
}), { threshold: 0.12, rootMargin: '0px 0px -40px 0px' })

export default {
  mounted(el, { value = 0 }) { el.setAttribute('data-reveal', ''); el.style.setProperty('--i', value % 8); io.observe(el) },
  unmounted(el) { io.unobserve(el) },
}
// main.js: app.directive('reveal', reveal)   用法：<div v-reveal="index">
```

```css
[data-reveal] { opacity: 0; transform: translateY(24px);
  transition: opacity .56s var(--ct-ease-out), transform .56s var(--ct-ease-out); transition-delay: calc(var(--i, 0) * 60ms); }
[data-reveal].is-in { opacity: 1; transform: none; }
```

## 4. 骨架屏

- **形状**：和真实商品卡片一模一样（1:1 封面 + 两行标题 + 价格条），切换时不会跳动。
- **动画**：从左到右的高光扫过，1.4s linear 无限循环；暗色下高光透明度只有 6%。
- **时机**：请求开始 **150ms 后**还没返回才显示骨架（避免闪一下）；骨架一旦出现至少保留 300ms；内容替换时淡入 240ms。
- **无限滚动**：底部固定放一行（4 张）骨架，加上「正在为你加载更多闲置…」的 spinner 文案；没有更多时显示「已经到底啦」。

```vue
<el-skeleton :loading="showSkeleton" animated :throttle="{ leading: 150, trailing: 300, initVal: false }">
  <template #template>
    <div class="sk-card">
      <el-skeleton-item variant="image" class="sk-img" />
      <div class="sk-body"><el-skeleton-item variant="text" /><el-skeleton-item variant="text" style="width:60%" /><el-skeleton-item variant="h3" style="width:40%" /></div>
    </div>
  </template>
  <template #default><GoodsGrid :list="list" /></template>
</el-skeleton>
```

```css
/* 不用 el-skeleton 时的纯 CSS 版 */
.sk { position: relative; overflow: hidden; background: var(--ct-gray-100); border-radius: 10px; }
.sk::after { content: ""; position: absolute; inset: 0; transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.6), transparent); animation: shimmer 1.4s linear infinite; }
@keyframes shimmer { to { transform: translateX(100%); } }
```

> `el-skeleton` 的 `throttle` 对象写法需要较新的 Element Plus（当前 2.14 支持）；如遇不支持，改为 `:throttle="150"` 并自行保证最短展示 300ms。

## 5. 收藏微交互（心形弹跳 + 粒子）

- **时序**：点击 → 心形先缩到 0.7，再弹到 1.35，最后回到 1（500ms spring），同时填充为 `#FF3D6E` → 光环从 0.4 放大到 1.6 并淡出（500ms）→ 10 个彩色粒子沿圆周飞出 22–34px 并缩小消失（600ms）→ 计数 +1 → Toast「已加入收藏，降价会第一时间提醒你」。
- **取消收藏**：只把填充色退回去，不放粒子。
- **数据**：乐观更新，接口失败时回滚，并用 `ElMessage.error` 提示；这里会写入 `user_behavior`（type=2，由后端完成）。
- **实现**：纯 CSS 关键帧 + 少量 JS 生成粒子；想要更有弹性可以换成 GSAP 版。

```vue
<!-- components/LikeButton.vue -->
<template>
  <button class="heart-btn" :class="{ 'is-on': liked }" @click.stop.prevent="toggle" aria-label="收藏">
    <span ref="host" class="heart-host">
      <svg class="i" viewBox="0 0 24 24"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>
    </span>
  </button>
</template>
<script setup>
import { ref } from 'vue'
import { usePreferredReducedMotion } from '@vueuse/core'
const props = defineProps({ modelValue: Boolean, onToggle: Function })
const emit = defineEmits(['update:modelValue'])
const liked = ref(props.modelValue), host = ref()
const reduced = usePreferredReducedMotion()
const COLORS = ['#ff3d6e', '#ff6b4a', '#ffc53d', '#8b5cf6', '#14c9a2']

async function toggle() {
  liked.value = !liked.value; emit('update:modelValue', liked.value)
  if (liked.value && reduced.value !== 'reduce') burst()
  try { await props.onToggle?.(liked.value) } catch { liked.value = !liked.value; emit('update:modelValue', liked.value) }
}
function burst() {
  const ring = document.createElement('span'); ring.className = 'heart-ring'; host.value.appendChild(ring); setTimeout(() => ring.remove(), 520)
  for (let i = 0; i < 10; i++) {
    const p = document.createElement('span'), a = (Math.PI * 2 * i) / 10, d = 22 + Math.random() * 12
    p.className = 'particle'; p.style.cssText = `background:${COLORS[i % 5]};--dx:${Math.cos(a) * d}px;--dy:${Math.sin(a) * d}px`
    host.value.appendChild(p); setTimeout(() => p.remove(), 650)
  }
}
</script>
<style scoped>
.heart-host { position: relative; display: grid; place-items: center; }
.heart-btn.is-on { color: #ff3d6e; }
.heart-btn.is-on svg { fill: #ff3d6e; animation: heartBeat .5s var(--ct-ease-spring); }
@keyframes heartBeat { 0% { transform: scale(1) } 25% { transform: scale(.7) } 60% { transform: scale(1.35) } 100% { transform: scale(1) } }
:deep(.heart-ring) { position: absolute; inset: -8px; border-radius: 50%; border: 2px solid #ff3d6e; animation: ring .5s var(--ct-ease-out) forwards; }
@keyframes ring { from { transform: scale(.4); opacity: 1 } to { transform: scale(1.6); opacity: 0 } }
:deep(.particle) { position: absolute; left: 50%; top: 50%; width: 6px; height: 6px; margin: -3px; border-radius: 50%; animation: particle .6s var(--ct-ease-out) forwards; }
@keyframes particle { to { transform: translate(var(--dx), var(--dy)) scale(0); opacity: 0 } }
</style>
```

GSAP 版的粒子（替换 `burst` 里的循环）：

```js
import gsap from 'gsap'
gsap.fromTo(p, { x: 0, y: 0, scale: 1, opacity: 1 },
  { x: Math.cos(a) * d, y: Math.sin(a) * d, scale: 0, opacity: 0, duration: 0.6, ease: 'expo.out', onComplete: () => p.remove() })
```

## 6. 下单按钮微交互

状态机：`idle → pressing → loading → success | error → idle`

| 状态 | 视觉 | 时长 / 缓动 |
| --- | --- | --- |
| hover | 上浮 2px，亮度 +5%，珊瑚色发光 | 160ms `(.16,1,.3,1)` |
| pressing | `scale(.96)` + 从点击位置扩散涟漪（2.6 倍，600ms） | 160ms |
| loading | 锁定宽度，文字换成 spinner +「正在预订…」，按钮 disabled | spinner 700ms linear 循环 |
| success | 背景过渡到绿色渐变，对勾描边绘制（stroke-dashoffset 24→0）+ 从按钮位置撒花 36 片 + Toast「预订成功，快和卖家约个面交时间吧」 | 对勾 450ms `(.16,1,.3,1)`，撒花 1.1–1.6s `(.2,.8,.2,1)` |
| error | 水平抖动 ±6px 共 3 次，文字变为「已被预订」或接口返回的 msg | 400ms |
| 结束 | 成功 1.2s 后跳转到订单详情 `/orders/:id` | — |

```vue
<el-button class="ct-btn-hot order-btn" size="large" :loading="state === 'loading'" :class="`is-${state}`" @click="order">
  <template v-if="state === 'success'"><svg class="check" viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg>已预订</template>
  <template v-else-if="state === 'loading'">正在预订…</template>
  <template v-else>立即下单</template>
</el-button>

<script setup>
import { ref } from 'vue'
import gsap from 'gsap'
const state = ref('idle')
async function order(e) {
  if (state.value !== 'idle') return
  state.value = 'loading'
  try {
    const res = await createOrder({ goodsId: props.goods.id })
    state.value = 'success'; confetti(e.currentTarget)
    setTimeout(() => router.push(`/orders/${res.id}`), 1200)
  } catch (err) {
    state.value = 'error'
    gsap.fromTo(e.currentTarget, { x: 0 }, { x: 6, duration: 0.067, repeat: 5, yoyo: true, ease: 'sine.inOut', clearProps: 'x' })
    setTimeout(() => (state.value = 'idle'), 1600)
  }
}
function confetti(btn) {
  const r = btn.getBoundingClientRect(), colors = ['#ff3d6e', '#ff6b4a', '#ffc53d', '#8b5cf6', '#14c9a2']
  for (let i = 0; i < 36; i++) {
    const c = Object.assign(document.createElement('span'), { className: 'confetti' })
    c.style.cssText = `left:${r.left + r.width / 2}px;top:${r.top}px;background:${colors[i % 5]}`
    document.body.appendChild(c)
    const a = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.1, v = 160 + Math.random() * 200
    gsap.to(c, { x: Math.cos(a) * v, y: Math.sin(a) * v + 260, rotation: Math.random() * 720, opacity: 0,
      duration: 1.1 + Math.random() * 0.5, ease: 'power2.out', onComplete: () => c.remove() })
  }
}
</script>
<style>
.confetti { position: fixed; width: 8px; height: 12px; border-radius: 2px; z-index: 4000; pointer-events: none; }
.order-btn.is-success { background-image: linear-gradient(135deg, #16b07b, #14c9a2) !important; }
.order-btn .check { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 3; stroke-dasharray: 24; stroke-dashoffset: 24; animation: draw .45s var(--ct-ease-out) forwards; margin-right: 6px; }
@keyframes draw { to { stroke-dashoffset: 0 } }
</style>
```

> 不想引入 GSAP 时，撒花可以换成 `canvas-confetti`（约 6KB），或者像原型那样用 Web Animations API（`el.animate`）实现，原型 `assets/proto.js` 里的 `ctConfetti` 可以直接搬过去用。

## 7. 聊天气泡进入

- **效果**：新消息从下方 12px 处出现，同时从 0.85 缩放到 1 并淡入，420ms spring；`transform-origin` 对方消息设在左下、我的消息设在右下，看起来像从头像里「吐」出来的。
- **我发出的消息**：先显示「送达」，收到 `/user/queue/read` 已读回执后，文字在 240ms 内淡变成主色「已读」。
- **对方正在输入**：三个点依次上下跳动，1.2s 循环，每个点错开 150ms。
- **滚动**：新消息到达时，只有当前已在底部 80px 以内才 `scrollTo({ behavior: 'smooth' })`；否则在输入框上方显示「↓ 2 条新消息」胶囊按钮。
- **历史消息**：顶部加载更早消息时**不做**入场动画（避免整屏闪烁），并保持滚动位置不跳。

```vue
<TransitionGroup name="bubble" tag="div" class="message-list">
  <div v-for="m in messages" :key="m.id" class="bubble-row" :class="{ 'is-mine': isMine(m) }">…</div>
</TransitionGroup>

<style>
.bubble-enter-active { transition: opacity .42s var(--ct-ease-out), transform .42s var(--ct-ease-spring); }
.bubble-enter-from { opacity: 0; transform: translateY(12px) scale(.85); }
.bubble-row { transform-origin: bottom left; }
.bubble-row.is-mine { transform-origin: bottom right; }
.typing i { animation: typing 1.2s infinite ease-in-out; }
.typing i:nth-child(2) { animation-delay: .15s } .typing i:nth-child(3) { animation-delay: .3s }
@keyframes typing { 0%, 60%, 100% { transform: translateY(0); opacity: .4 } 30% { transform: translateY(-5px); opacity: 1 } }
</style>
```

发送按钮：hover `scale(1.06) rotate(-8deg)`，按下 `scale(.9)`，160ms spring。快捷回复 Chip 点击后直接发送。

## 8. 推荐轮播（猜你喜欢）

- **布局**：桌面一屏 4 张，平板 3 张，手机 1.2 张（露出下一张的边，提示可以滑动）。
- **翻页**：track `translateX` 700ms `(.16,1,.3,1)`；翻页后新的一屏卡片用 GSAP 依次淡入上浮（每张延迟 50ms，360ms）。
- **自动播放**：4.5s 一次；鼠标悬停、窗口不可见（`document.visibilityState`）或减少动态效果时暂停。
- **指示器**：当前页的圆点变宽（8 → 32px，360ms），里面的进度条在 4.5s 内线性填满，和自动播放节奏同步。
- **拖拽**：触屏用 Pointer Events 拖动，松手时位移超过 60px 或速度大于 0.4px/ms 就翻页。
- **推荐理由**：每张卡片都带 `.reason` 黄色条（对应接口的 reason 字段）。

```vue
<div class="rec-vp" @pointerenter="pause" @pointerleave="play">
  <div ref="track" class="rec-track" :style="{ transform: `translateX(${-page * pageWidth}px)` }">
    <GoodsCard v-for="g in list" :key="g.id" :goods="g" show-reason />
  </div>
</div>
<div class="dots"><i v-for="n in pages" :key="n" :class="{ on: n - 1 === page }" @click="go(n - 1)" /></div>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useIntervalFn, useElementSize, usePreferredReducedMotion, useDocumentVisibility } from '@vueuse/core'
import gsap from 'gsap'
const track = ref(), page = ref(0), perPage = 4
const { width } = useElementSize(track)
const pageWidth = computed(() => width.value + 20)          // 20 = gap
const pages = computed(() => Math.ceil(props.list.length / perPage))
const reduced = usePreferredReducedMotion(), visible = useDocumentVisibility()
const { pause, resume: play } = useIntervalFn(() => go(page.value + 1), 4500)
watch([reduced, visible], ([r, v]) => (r === 'reduce' || v !== 'visible' ? pause() : play()), { immediate: true })
function go(i) {
  page.value = (i + pages.value) % pages.value
  nextTick(() => {
    const cards = [...track.value.children].slice(page.value * perPage, page.value * perPage + perPage)
    gsap.fromTo(cards, { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: 0.36, stagger: 0.05, ease: 'expo.out', delay: 0.15 })
  })
}
</script>
<style>
.rec-track { display: flex; gap: 20px; transition: transform .7s var(--ct-ease-out); }
.rec-track > * { flex: 0 0 calc((100% - 60px) / 4); }
.dots i { width: 8px; height: 8px; border-radius: 99px; background: var(--ct-border-strong); transition: width .36s var(--ct-ease-out); position: relative; overflow: hidden; }
.dots i.on { width: 32px; background: var(--ct-primary-soft-2); }
.dots i.on::after { content: ""; position: absolute; inset: 0; background: var(--ct-primary); transform-origin: left; animation: prog 4.5s linear forwards; }
@keyframes prog { from { transform: scaleX(0) } }
</style>
```

> 也可以继续用 `el-carousel`（`:interval="4500"`、`indicator-position="outside"`），主题文件已经把指示器改成「当前项变长」的样式；但 el-carousel 每页只能放一个 item，需要自己按 4 个一组切片。

## 9. 暗色切换

```js
import { isDark } from '@/composables/useTheme'
function toggleTheme(e) {
  const x = e.clientX, y = e.clientY
  const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y))
  if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) { isDark.value = !isDark.value; return }
  document.startViewTransition(() => { isDark.value = !isDark.value }).ready.then(() => {
    document.documentElement.animate({ clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
      { duration: 560, easing: 'cubic-bezier(.16,1,.3,1)', pseudoElement: '::view-transition-new(root)' })
  })
}
```

```css
::view-transition-old(root), ::view-transition-new(root) { animation: none; mix-blend-mode: normal; }
/* 图标：太阳向上转出，月亮从下方转入，560ms spring */
```

## 10. 其他微交互（原型中已全部实现）

| 交互 | 规格 | 实现 |
| --- | --- | --- |
| 按钮涟漪 | 从点击点扩散到 2.6 倍并淡出，600ms `(.16,1,.3,1)` | CSS + 10 行 JS |
| 吸顶头部 | 滚动超过 8px 后出现 `shadow-2`，240ms | CSS + scroll 监听（`useWindowScroll`） |
| 搜索框聚焦 | 宽度 440 → 500px、白底、主色边框和焦点环，240ms | 纯 CSS `:focus-within` |
| 分段控件 | 白色滑块移动到选中项（宽度跟着变），360ms spring | CSS + 测量 offsetLeft |
| 分类图标 | hover 时卡片上浮 6px 并放大到 1.02，图标旋转 -8° 并放大，360ms spring | 纯 CSS |
| 数字滚动 | 统计数字从 0 增长到目标值，1.2s easeOutQuart，进入视口时触发一次 | `useTransition`（@vueuse/core） |
| 公告滚动 | 每条停留约 2.5s，上移 24px，`(.2,.8,.2,1)` | 纯 CSS keyframes |
| 详情图集切换 | 旧图淡出缩到 0.97（160ms），新图从 1.03 淡入（360ms） | Web Animations / `<Transition>` |
| 定价区间标记 | 标记点从最左侧弹到当前价格的位置，900ms spring，延迟 300ms | 纯 CSS |
| 信誉分圆环 | stroke-dashoffset 从满到目标值，1.4–1.6s `(.16,1,.3,1)` | SVG + CSS |
| 上传图片 | 拖进上传区时边框变主色并出现浅色底；上传中显示百分比遮罩和进度条；拖动排序时被拖的图 `scale(1.05)` 并加 shadow-3 | `el-upload` + `vuedraggable`（可选） |
| 发布页实时预览 | 输入标题、价格或选择成色时，右侧卡片实时更新 | Vue 响应式 |
| AI 定价卡片 | 流光渐变描边 6s 循环；柱状图从底部长出，800ms | 纯 CSS |
| 后台折线图 | 折线绘制 1.8s，面积淡入 1.2s | ECharts `animationDuration: 1800, animationEasing: 'cubicOut'` |
| 审核通过 / 驳回 | 该行右移 40px 并淡出（450ms ease-in），然后从列表移除并弹出 Toast | `<TransitionGroup>` |
| Toast | 从上方 16px 处弹入并从 0.9 放大，450ms spring；2.2s 后上移淡出，250ms | `ElMessage` + 主题覆盖 |

---

## 11. 需要新增的 npm 依赖

```bash
cd frontend
npm i @vueuse/core@^13 @vueuse/motion@^3 gsap@^3.15
# 可选
npm i @fontsource-variable/inter       # 数字/拉丁字体，统一价格观感
npm i canvas-confetti                  # 不想用 GSAP 撒花时的替代
npm i vuedraggable@next                # 发布页图片拖拽排序
```

| 包 | 用途 | 体积（gzip 约） |
| --- | --- | --- |
| `@vueuse/core` | `useDark`、`usePreferredReducedMotion`、`useIntervalFn`、`useTransition`、`useElementSize` | 按需 tree-shake |
| `@vueuse/motion` | `v-motion` 滚动出现、列表依次进入 | ~10KB |
| `gsap` | 粒子、撒花、轮播卡片依次进入、错误抖动 | ~25KB（核心） |

> `@vueuse/motion@3` 内部依赖 `@vueuse/core@^13`，所以项目里的 `@vueuse/core` 也用 13.x，避免装两份。GSAP 3.13 起已完全免费（含插件），商用和毕设都可以用。

## 12. prefers-reduced-motion 降级

1. `tokens.css` 已在 `@media (prefers-reduced-motion: reduce)` 下把所有 `--ct-dur-*` 设为 0。
2. 全局兜底（放在 `index.scss`）：

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; scroll-behavior: auto !important; }
  [data-reveal] { opacity: 1 !important; transform: none !important; }
}
```

3. JS 动效统一判断一次：

```js
import { usePreferredReducedMotion } from '@vueuse/core'
const reduced = usePreferredReducedMotion()   // 'reduce' | 'no-preference'
// 'reduce' 时：不生成粒子/撒花、轮播不自动播放、暗色切换不做圆形扩散、数字直接显示终值
gsap.matchMedia().add('(prefers-reduced-motion: reduce)', () => gsap.globalTimeline.timeScale(100))
```

4. `@vueuse/motion`：在 `reduced === 'reduce'` 时不绑定 `v-motion`，或者把 `initial` 设成和终态一样。
5. **保留**的反馈：颜色变化、收藏的填充色、按钮的 loading 状态。这些不属于「动态」，但能保证功能反馈不丢。
