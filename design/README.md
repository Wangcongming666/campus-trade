# 设计交付物 · 校园二手交易智能平台（Iris Design System v1.0）

| 文件 | 说明 |
| --- | --- |
| `design-system.md` | 设计规范：色彩（亮/暗）、字体、圆角、阴影、间距、断点、组件规范，以及 **main.js 引入方式**和旧变量迁移 |
| `tokens.css` | 可直接引入的 CSS 变量（`:root` 亮色 + `html.dark` / `[data-theme=dark]` 暗色 + reduced-motion） |
| `element-plus-theme.css` | Element Plus 主题覆盖（`--el-color-*` 全梯度、表面、圆角、阴影，以及按钮/输入框/卡片/弹窗/表格等外观） |
| `motion.md` | 动效与交互清单：时长、cubic-bezier、触发条件、实现方式、Vue 代码片段、依赖、降级 |
| `layouts.md` | 六个核心页面的栅格、模块、关键组件、响应式、空状态和加载态 |
| `prototype/` | 高保真 HTML 原型，直接双击 `prototype/index.html` 打开（右下角可以切换 6 个页面，右上角 ☀/☾ 切换暗色） |
| `screenshots/` | 1440 宽效果图（6 个页面 + 4 张暗色） |

原型说明：纯 HTML/CSS/原生 JS，不需要构建、不依赖 CDN，可以离线打开；商品图用矢量插画加渐变代替；页面上的人名、价格、统计数字都是**示例数据**。URL 参数 `?theme=dark` 强制暗色，`?shot=1` 关闭入场动画（截图用）。
