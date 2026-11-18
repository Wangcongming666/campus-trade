# 校园二手交易智能平台 · 后端（Phase 1 + 2）

Spring Boot 3.2 · JDK 17 · Spring Security 6 + JWT(jjwt 0.12) · MyBatis-Plus 3.5.7 · MySQL 8 · Redis · Knife4j · MinIO（可降级本地）

接口规范：`../docs/后端接口规范.md`；建表：`../sql/schema.sql`；种子数据：`../sql/seed.sql`。

## 运行

```bash
# 1. 启动 MySQL / Redis（本机开发环境）
bash ../scripts/start-services.sh

# 2. 建库建表 + 种子数据（schema.sql 会重建所有表）
mysql -h127.0.0.1 -uroot -proot123 < ../sql/schema.sql
mysql -h127.0.0.1 -uroot -proot123 < ../sql/seed.sql

# 3. 打包运行（默认 dev profile，端口 8080）
mvn -q -DskipTests package
java -jar target/campus-trade-backend-1.0.0.jar
# 或：mvn spring-boot:run
```

- 接口文档：http://localhost:8080/doc.html （OpenAPI JSON：/v3/api-docs）
- 种子账号：`admin / admin123`（ADMIN）、`user1 / 123456`（已学生认证，学号 2022001001）、`user2 / 123456`（认证待审核）
- 种子数据还包括：四种状态的订单（PENDING/PAID/COMPLETED/CANCELLED，对应商品 RESERVED/RESERVED/SOLD/ON_SHELF）、1 条评价、1 个聊天会话（4 条消息，user1 未读 1）、1 件 REJECTED 商品、1 条待处理举报、已发布 + 草稿公告各 1
- WebSocket（STOMP）：`ws://localhost:8080/ws`（原生）或 SockJS `http://localhost:8080/ws`；CONNECT 头 `Authorization: Bearer <accessToken>`；`SEND /app/chat.send {toUserId, goodsId, content, type}`；订阅 `/user/queue/messages`、`/user/queue/read`、`/user/queue/errors`
- 上传文件默认落在 `./uploads`，通过 `/files/**` 访问；`STORAGE_TYPE=minio` 切到 MinIO（不可用时自动降级本地）

常用环境变量：`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`JWT_SECRET`、`STORAGE_TYPE`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。

## 测试

```bash
mvn test
```

集成测试（SpringBootTest + MockMvc；聊天另有 RANDOM_PORT + WebSocketStompClient 端到端测试）使用独立库 `campus_trade_test`（自动创建）和 Redis db 1：
启动时读取 `../sql/schema.sql`、`../sql/seed.sql`，把库名替换为 `campus_trade_test` 后执行（不修改原文件），并清空 Redis db 1。
测试 profile 中 `captcha.enabled=false` 跳过验证码，`AuthFlowTest` 单独验证开启时的校验逻辑。需要本机 MySQL/Redis 已启动。

## 约定要点

- 统一返回 `{code,msg,data}`，成功 `code=200`；未登录/Token 失效 HTTP 401 + `code 401`；无权限 HTTP 403 + `code 403`；其他错误 HTTP 200 + 业务码
- 分页 `pageNum`（默认 1）/`pageSize`（默认 10，最大 100，超出截断）→ `{records,total,pageNum,pageSize,pages}`
- 主键（`Long`）JSON 输出为字符串；时间 `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai）
- `Authorization: Bearer <accessToken>`；accessToken 2h；refreshToken 7d 存 Redis（`auth:refresh:{sha256}`），刷新时轮换；退出时 accessToken 的 jti 进黑名单 `auth:blacklist:{jti}`
- 游客白名单：`/api/v1/auth/{captcha,register,login,refresh}`、`GET /goods`、`GET /goods/search`、`GET /goods/{id}`、`GET /categories/**`、`GET /recommend/**`、`GET /users/{id}`、`/doc.html`、swagger、`/files/**`、`/ws/**`；`/api/v1/admin/**` 需 ADMIN

## 代码结构（com.campus.trade）

| 包 | 内容 |
|---|---|
| `common` | `Result`、`PageResult`、`PageQuery`、`BizException`、`ErrorCode`、`Constants`（枚举取值）、`GlobalExceptionHandler` |
| `config` | Jackson（Long→String、时间格式）、MyBatis-Plus（分页、自动填充）、OpenAPI、WebMvc（/files/**、CORS）、异步线程池、存储配置 |
| `security` | `JwtUtil`、`JwtAuthFilter`、`SecurityConfig`、`SecurityUtils`、401/403 JSON 处理器、`RedisKeys`、`StompAuthChannelInterceptor`、`StompPrincipal` |
| `entity` / `mapper` | 表实体与 Mapper（`goods.deleted` 逻辑删除；`condition` 为保留字已加反引号） |
| `service` | Auth / Captcha / User / UserInterest / Category / Goods / Favorite / File / `UserBehaviorService`；Order / Review / Chat / Admin / Report / Announcement |
| `storage` | `LocalFileStorage`、`MinioFileStorage` |
| `controller` / `dto` / `vo` | 接口层；`vo.GoodsVO` 等 |

### 推荐 / 价格模块可复用

- `UserBehaviorService.recordView/recordFavorite/recordChat/recordOrder`（type 1–4），均 `@Async`，游客（null）自动跳过；聊天与下单已接入
- `GoodsMapper.casStatus(from, to)` / `TradeOrderMapper.casStatus(fromStatuses, to)`：订单与商品状态的条件更新（OrderService 已使用）
- `GoodsService.toListVOs(List<Goods>, viewerId)`：批量组装列表 GoodsVO（推荐结果可直接复用）
- `UserInterestService.categoryIdsOf(userId)`：冷启动兴趣分类
