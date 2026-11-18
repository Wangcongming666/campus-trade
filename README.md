# 基于 Spring Boot 的校园二手交易智能平台

```
campus-trade/
├── backend/   Spring Boot 3 后端（JDK 17，端口 8080）
├── frontend/  Vue 3 + Vite 前端（Node 18+，端口 5173）
├── sql/       schema.sql 建表（15 张表）、seed.sql 演示数据
├── docs/      项目方案.md、后端接口规范.md
└── scripts/   start-services.sh（开发机启动 MySQL/Redis 用）
```

## 一、环境
JDK 17、Maven 3.8+、MySQL 8.0、Redis 6+、Node.js 18+。Mac 可用 Homebrew：
`brew install openjdk@17 maven mysql redis node`，然后 `brew services start mysql redis`。

## 二、初始化数据库
```bash
mysql -uroot -p < sql/schema.sql            # 创建 campus_trade 库和表
mysql -uroot -p campus_trade < sql/seed.sql # 导入演示数据
```

## 三、启动后端
默认连接 localhost:3306，账号 root / root123，Redis localhost:6379 无密码。密码不同时用环境变量覆盖：
```bash
cd backend
DB_USERNAME=root DB_PASSWORD=你的密码 mvn spring-boot:run
```
启动后接口文档：http://localhost:8080/doc.html 。上传的图片默认保存在 backend/uploads。
运行单元测试：`mvn test`（会自动创建测试库 campus_trade_test，需要 MySQL 与 Redis 已启动）。

## 四、启动前端
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 ，/api、/ws、/files 自动代理到 8080
```
构建预览：`npm run build && npm run preview`（http://localhost:4173）。管理端地址 /admin。

## 五、演示账号
| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 管理员 |
| user1 | 123456 | 普通用户（已学生认证） |
| user2 | 123456 | 普通用户（学生认证待审核） |
| user3、user4 | 123456 | 普通用户（提供推荐演示所需的共同浏览/收藏行为） |

登录需要输入图形验证码。user1 登录后首页可同时看到三种推荐理由：CF（如「你咨询过类似的索尼 WH-1000XM4 降噪耳机」）、INTEREST（如「你关注了教材书籍」）、HOT（「本周热门」「最新发布」）。

## 六、功能与测试情况
用户注册登录与学生认证、商品发布浏览与审核、搜索分类、收藏、订单（待支付→已支付→已完成，可取消/退款）、评价与信誉分、实时聊天（STOMP WebSocket）、智能推荐（协同过滤 + 冷启动）、价格建议、举报、公告、管理端统计/审核/封禁/上下架。
后端 72 个自动化测试全部通过；前后端已完成两轮端到端联调。
