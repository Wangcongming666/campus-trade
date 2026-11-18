# 校园二手交易智能平台 · 前端

基于 Vue 3 + Vite 的用户端 / 管理端，对接 Spring Boot 后端（默认端口 `8080`）。

本工程位于仓库的 `frontend/` 目录（完整路径：`campus-trade/frontend/`）。

## 技术栈

- Vue 3（`<script setup>` + JavaScript）
- Vite 8
- Element Plus（中文 locale，图标 `@element-plus/icons-vue`）
- Pinia、Vue Router 4、Axios
- Sass
- ECharts（管理端统计按需引入）
- 即时通讯：默认 STOMP over SockJS（`@stomp/stompjs`、`sockjs-client`）；后端同时提供原生 WebSocket 端点

## 启动步骤

先启动后端（`http://localhost:8080`）。接口文档（Knife4j）：[http://localhost:8080/doc.html](http://localhost:8080/doc.html)。开发登录需要图形验证码，后端将验证码写入 Redis（`captcha:{captchaKey}`）。

在仓库根目录 `campus-trade` 下进入前端目录后安装并启动：

```bash
cd frontend

# 安装依赖
npm install

# 开发环境（固定端口 5173；Vite 把 /api、/ws、/files 代理到 http://localhost:8080）
npm run dev

# 如需局域网访问
npm run dev -- --host

# 生产构建
npm run build

# 预览构建结果（固定端口 4173，同样代理 /api、/ws、/files 到 8080）
npm run preview
```

若当前已经在 `campus-trade/frontend/` 内，可跳过 `cd frontend`，直接执行以上命令。

开发服务：`http://127.0.0.1:5173`（`vite.config.js` 中 `server.port = 5173`，`strictPort: true`）。
预览服务：`http://127.0.0.1:4173`（`preview.port = 4173`，`strictPort: true`，`preview.proxy` 与开发代理一致）。

## mock / 真实模式切换

默认走真实后端，由 `.env` 中的 `VITE_USE_MOCK=false` 控制。真实模式下**完全不进入 mock**：接口 404 时对应区域显示空状态并提示「该接口尚未上线」，不弹全局报错，也不会用假数据回退。

仅在后端未启动、需要演示预览时，可临时打开 mock（页面会明显标注为演示数据）：

```bash
# 仅本次启动
VITE_USE_MOCK=true npm run dev

# 或在 .env 中改为 VITE_USE_MOCK=true（演示结束后请改回 false）
```

参考模板：`.env.example`。

## 目录结构

```
campus-trade/
├── frontend/                 # 本前端工程
│   ├── index.html
│   ├── vite.config.js        # @ 别名；dev(5173)/preview(4173) 代理 /api /ws /files；分包
│   ├── jsconfig.json
│   ├── src/
│   │   ├── api/              # 按模块封装的接口（baseURL = /api/v1）
│   │   │   ├── auth.js
│   │   │   ├── user.js
│   │   │   ├── goods.js
│   │   │   ├── category.js
│   │   │   ├── order.js
│   │   │   ├── chat.js
│   │   │   ├── review.js
│   │   │   ├── file.js
│   │   │   ├── recommend.js
│   │   │   ├── price.js
│   │   │   ├── report.js
│   │   │   ├── announcement.js
│   │   │   └── admin.js      # 一律 /admin/**，需 ADMIN
│   │   ├── components/       # GoodsCard、GoodsGrid、ReportDialog、InterestPicker 等
│   │   ├── composables/      # usePagination、useCaptcha、useChart
│   │   ├── constants/        # enums.js、errorCodes.js
│   │   ├── mock/             # 仅 VITE_USE_MOCK=true 时使用
│   │   ├── layouts/          # UserLayout、AdminLayout
│   │   ├── router/
│   │   ├── stores/           # user（token）、chat（STOMP / 未读）
│   │   ├── styles/           # 校园绿主题 CSS 变量
│   │   ├── utils/            # request.js（Axios）、stomp.js（默认 SockJS）
│   │   ├── views/
│   │   │   ├── Login.vue
│   │   │   ├── Register.vue
│   │   │   ├── user/
│   │   │   └── admin/
│   │   ├── App.vue
│   │   └── main.js
│   └── public/
├── docs/                     # 含《后端接口规范》
└── ...
```

## 页面清单与访问权限

依据规范第 6 节白名单及第 13 节补充：

**游客可访问（无需登录）**

| 路径 | 页面 |
| --- | --- |
| `/` | 首页（推荐、公告滚动条） |
| `/search` | 商品搜索 / 列表 |
| `/goods/:id` | 商品详情（含相似推荐、举报入口） |
| `/announcements/:id` | 公告详情 |
| `/users/:id` | 用户主页（他人主页可举报） |
| `/login`、`/register` | 登录 / 注册 |

游客请求不加 `Authorization` 头。导航栏在游客态显示「登录 / 注册」，登录后显示头像下拉。

**需登录（用户端）**

| 路径 | 页面 |
| --- | --- |
| `/publish` | 发布 / 编辑商品（智能定价） |
| `/chat` | 即时聊天 |
| `/orders` | 订单列表 |
| `/orders/:id` | 订单详情（非参与方 403） |
| `/user` | 个人中心 |

无 token 时跳转 `/login?redirect=`。

**管理端（需登录且 `role === 'ADMIN'`，接口 `/api/v1/admin/**`）**

| 路径 | 页面 |
| --- | --- |
| `/admin`、`/admin/dashboard` | 数据统计（7 / 30 天） |
| `/admin/audit` | 商品审核、学生认证审核 |
| `/admin/users` | 用户管理（封禁 / 解封） |
| `/admin/goods` | 商品管理（仅 ON_SHELF ↔ OFF_SHELF） |
| `/admin/reports` | 举报处理 |
| `/admin/announcements` | 公告管理 |

非参与方访问订单或聊天会话：HTTP 403，页面友好提示并返回。接口 404：区域空状态「该接口尚未上线」。

## 接口对齐版本

已对齐《校园二手交易平台 · 后端接口规范（v1.0 定稿）》（2026-11-18，`docs/后端接口规范.md`）**第 10–13 节**，并对照 `/v3/api-docs`。

- **第 10 节（路径清单）**：认证、用户、商品/收藏/搜索、订单、聊天、评价、文件上传、推荐、智能定价、管理端审核/用户/商品/统计等。
- **第 11 节（VO / 枚举）**：GoodsVO、CategoryVO、UserPublicVO / UserMeVO、ChatSessionVO（主键 `id`）、ChatMessageVO、价格建议 `{suggestedPrice,minPrice,maxPrice,sampleCount,reason}`，商品状态含 `RESERVED`，订单 action 仅 `PAY/CANCEL/CONFIRM/REFUND`。
- **第 12 节（phase 1 补充）**：错误码 10005–10011、20004/20005、70003；游客可看 `GET /users/{id}`；`pageSize` 上限 100；详情可见性；收藏幂等；OpenAPI 中主键以 **string** 序列化。
- **第 13 节（phase 2 补充）**：错误码 30004/30005、40001–40004、50001/50002、60001–60010；非参与方 HTTP 403；`GET /chat/sessions` 返回不分页数组；管理员商品状态仅 ON_SHELF ↔ OFF_SHELF；STOMP 订阅 `/user/queue/errors`；举报 `POST /reports`；公告 `GET /announcements`、`GET /announcements/{id}`；管理端举报处理与公告管理；游客可看评价与公告；封禁立即作废 token，解封后旧 accessToken / refreshToken 仍无效，须重新登录。

其它约定：

- 统一前缀：`/api/v1`（Axios `baseURL` 为 `/api/v1`）。
- 统一返回：`{ code, msg, data }`，成功 `code = 200`，拦截器直接返回 `data`。
- 业务错误：HTTP 200 + 非 200 的业务 `code`，前端提示 `msg`（见 `src/constants/errorCodes.js`）。
- Token 过期：HTTP 401。前端用 `POST /api/v1/auth/refresh` 换新 token 并重放；并发 401 排队，只刷新一次。刷新失败（含解封后旧 refreshToken 失效）清空登录态并跳转 `/login`，不循环刷新。
- 认证头：仅在已登录时携带 `Authorization: Bearer <accessToken>`。
- 分页：`pageNum`（从 1 开始）、`pageSize`，返回 `{ records, total, pageNum, pageSize, pages }`。
- 聊天实时：默认 STOMP over SockJS，端点 `/ws`；CONNECT 头带 `Authorization`；发送 `/app/chat.send`；订阅 `/user/queue/messages`、`/user/queue/read`、`/user/queue/errors`。后端同时提供原生 WebSocket `ws://localhost:8080/ws`（可用 `scripts/native-stomp-check.mjs` 探活，不改变默认 SockJS 实现）。
- 文件上传：`POST /api/v1/files/upload`（字段名 `file`），返回 `{ url, name, size }`。
- 雪花 ID 一律按字符串处理，不要使用 `Number()` 或 `parseInt()`。

## 测试账号说明

本地种子账号（开发环境，密码仅为联调用）：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | 管理端全部功能 |
| `user1` | `123456` | USER | 买家/卖家联调 |
| `user2` | `123456` | USER | 买家/卖家联调 |

开发登录必须填写验证码：先调用 `GET /api/v1/auth/captcha` 得到 `captchaKey`，再从 Redis 只读取出 `captcha:{captchaKey}` 作为 `captchaCode`。不要改后端验证码配置。

注意：若某用户曾被封禁再解封，解封前签发的 accessToken / refreshToken 仍然无效，必须重新登录。不要复用封禁前缓存的 token。

联调产生的测试商品标题以「[联调测试]」为前缀，便于识别。

主题色在 `src/styles/index.scss` 的 CSS 变量中，可按设计稿调整。
