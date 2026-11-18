-- =============================================================================
-- 校园二手交易智能平台
-- MySQL 8.0 完整建表脚本
-- =============================================================================
-- 字符集：utf8mb4    排序规则：utf8mb4_0900_ai_ci
-- 主键：全部为雪花 ID（BIGINT，非自增），由应用层 MyBatis-Plus 生成后写入
-- 金额：DECIMAL(10,2)，与接口 BigDecimal、两位小数一致
-- 时间：DATETIME，应用层按 yyyy-MM-dd HH:mm:ss、时区 Asia/Shanghai 处理
-- 约定来源：《后端接口规范》v1.0、《毕业设计总体方案》，以及前端已确认/待 schema 定稿的枚举
-- 本脚本可重复执行（先删表再建模）
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `campus_trade`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `campus_trade`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 按依赖逆序删除，保证可重复执行
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
DROP TABLE IF EXISTS `report`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `pay_order`;
DROP TABLE IF EXISTS `trade_order`;
DROP TABLE IF EXISTS `user_interest`;
DROP TABLE IF EXISTS `user_behavior`;
DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `goods_image`;
DROP TABLE IF EXISTS `goods`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `student_auth`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_role`;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 1. sys_role  角色字典（USER / ADMIN，与规范第 6 节一致）
-- =============================================================================
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL COMMENT '雪花主键',
  `role_code`   VARCHAR(16)  NOT NULL COMMENT '角色编码：USER / ADMIN',
  `role_name`   VARCHAR(32)  NOT NULL COMMENT '角色名称',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色字典';

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `remark`)
VALUES
  (1000000000000000001, 'USER',  '普通用户', '在校学生用户'),
  (1000000000000000002, 'ADMIN', '管理员',   '后台管理与审核');

-- =============================================================================
-- 2. sys_user  用户账号（含管理员，以 role 区分）
--    登录账号可为用户名 / 手机号 / 学号（规范第 10 节）
--    状态：NORMAL / BANNED（对应 PATCH /admin/users/{id}/status）
-- =============================================================================
CREATE TABLE `sys_user` (
  `id`                   BIGINT        NOT NULL COMMENT '雪花主键',
  `username`             VARCHAR(32)   NOT NULL COMMENT '用户名，登录账号之一',
  `password_hash`        VARCHAR(100)  NOT NULL COMMENT '密码哈希（BCrypt）',
  `nickname`             VARCHAR(32)   NOT NULL COMMENT '昵称',
  `avatar`               VARCHAR(512)  DEFAULT NULL COMMENT '头像 URL',
  `phone`                VARCHAR(20)   DEFAULT NULL COMMENT '手机号，登录账号之一',
  `student_no`           VARCHAR(32)   DEFAULT NULL COMMENT '学号，认证通过后回写，可作为登录账号',
  `school`               VARCHAR(100)  DEFAULT NULL COMMENT '学校，认证通过后回写',
  `role`                 VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
  `status`               VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '账号状态：NORMAL / BANNED',
  `credit_score`         INT           NOT NULL DEFAULT 100 COMMENT '信誉分，公开主页展示',
  `student_auth_status`  VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT '认证状态：NONE / PENDING / APPROVED / REJECTED',
  `last_login_time`      DATETIME      DEFAULT NULL COMMENT '最近登录时间',
  `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_role_status` (`role`, `status`),
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role`) REFERENCES `sys_role` (`role_code`),
  CONSTRAINT `chk_user_role` CHECK (`role` IN ('USER', 'ADMIN')),
  CONSTRAINT `chk_user_status` CHECK (`status` IN ('NORMAL', 'BANNED')),
  CONSTRAINT `chk_user_auth_status` CHECK (`student_auth_status` IN ('NONE', 'PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT `chk_user_credit` CHECK (`credit_score` >= 0 AND `credit_score` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

-- =============================================================================
-- 3. student_auth  学生认证
--    提交：POST /users/me/student-verify {studentNo, realName, school, cardImageUrl}
--    审核：PATCH /admin/student-verifies/{id} {result: APPROVE|REJECT, reason?}
-- =============================================================================
CREATE TABLE `student_auth` (
  `id`             BIGINT        NOT NULL COMMENT '雪花主键',
  `user_id`        BIGINT        NOT NULL COMMENT '申请用户 ID',
  `student_no`     VARCHAR(32)   NOT NULL COMMENT '学号',
  `real_name`      VARCHAR(32)   NOT NULL COMMENT '真实姓名',
  `school`         VARCHAR(100)  NOT NULL COMMENT '学校',
  `card_image_url` VARCHAR(512)  NOT NULL COMMENT '学生证或校园卡图片 URL',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
  `reason`         VARCHAR(255)  DEFAULT NULL COMMENT '审核意见，拒绝时填写',
  `auditor_id`     BIGINT        DEFAULT NULL COMMENT '审核人 ID',
  `audit_time`     DATETIME      DEFAULT NULL COMMENT '审核时间',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_student_no` (`student_no`),
  CONSTRAINT `fk_auth_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_auth_auditor` FOREIGN KEY (`auditor_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_auth_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生认证';

-- =============================================================================
-- 4. category  商品分类（支持树：parent_id 为空表示一级；接口提供平铺与树两种查询）
-- =============================================================================
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL COMMENT '雪花主键',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父分类 ID，NULL 表示一级分类',
  `name`        VARCHAR(32)  NOT NULL COMMENT '分类名称',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，升序',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_sort` (`parent_id`, `sort`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_category_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';

-- =============================================================================
-- 5. goods  商品
--    状态：PENDING_AUDIT / ON_SHELF / RESERVED / SOLD / OFF_SHELF / REJECTED
--    成色：NEW / LIKE_NEW / GOOD / FAIR / POOR
--    交易方式：FACE / EXPRESS / EITHER
--    卖家上下架：PATCH /goods/{id}/status
--    后台审核：PATCH /admin/goods/{id}/audit {result: APPROVE|REJECT, reason?}
-- =============================================================================
CREATE TABLE `goods` (
  `id`             BIGINT         NOT NULL COMMENT '雪花主键',
  `seller_id`      BIGINT         NOT NULL COMMENT '卖家用户 ID',
  `category_id`    BIGINT         NOT NULL COMMENT '分类 ID',
  `title`          VARCHAR(120)   NOT NULL COMMENT '标题',
  `description`    TEXT           NOT NULL COMMENT '描述',
  `price`          DECIMAL(10,2)  NOT NULL COMMENT '售价，两位小数',
  `original_price` DECIMAL(10,2)  DEFAULT NULL COMMENT '原价（选填），两位小数',
  `condition`      VARCHAR(16)    NOT NULL COMMENT '成色：NEW / LIKE_NEW / GOOD / FAIR / POOR',
  `trade_method`   VARCHAR(16)    NOT NULL DEFAULT 'FACE' COMMENT '交易方式：FACE / EXPRESS / EITHER',
  `trade_location` VARCHAR(100)   NULL COMMENT '交易地点，如 东区食堂门口',
  `status`         VARCHAR(16)    NOT NULL DEFAULT 'PENDING_AUDIT' COMMENT 'PENDING_AUDIT / ON_SHELF / RESERVED / SOLD / OFF_SHELF / REJECTED',
  `cover_url`      VARCHAR(512)   DEFAULT NULL COMMENT '封面图 URL，通常取第一张商品图',
  `view_count`     INT            NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `favorite_count` INT            NOT NULL DEFAULT 0 COMMENT '收藏次数（与 favorite 表同步维护）',
  `audit_reason`   VARCHAR(255)   DEFAULT NULL COMMENT '审核意见，拒绝时填写',
  `auditor_id`     BIGINT         DEFAULT NULL COMMENT '审核人 ID',
  `audit_time`     DATETIME       DEFAULT NULL COMMENT '审核时间',
  `deleted`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是（对应 DELETE /goods/{id}）',
  `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_time` (`status`, `create_time`),
  KEY `idx_price` (`price`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_goods_seller` FOREIGN KEY (`seller_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_goods_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`),
  CONSTRAINT `fk_goods_auditor` FOREIGN KEY (`auditor_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_goods_condition` CHECK (`condition` IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
  CONSTRAINT `chk_goods_trade_method` CHECK (`trade_method` IN ('FACE', 'EXPRESS', 'EITHER')),
  CONSTRAINT `chk_goods_status` CHECK (`status` IN ('PENDING_AUDIT', 'ON_SHELF', 'RESERVED', 'SOLD', 'OFF_SHELF', 'REJECTED')),
  CONSTRAINT `chk_goods_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_goods_original_price` CHECK (`original_price` IS NULL OR `original_price` >= 0),
  CONSTRAINT `chk_goods_deleted` CHECK (`deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品';

-- =============================================================================
-- 6. goods_image  商品图片（每件最多 9 张；仅 jpg/jpeg/png/webp，单张 ≤ 5MB，由应用层校验）
-- =============================================================================
CREATE TABLE `goods_image` (
  `id`          BIGINT        NOT NULL COMMENT '雪花主键',
  `goods_id`    BIGINT        NOT NULL COMMENT '商品 ID',
  `url`         VARCHAR(512)  NOT NULL COMMENT '图片访问 URL',
  `name`        VARCHAR(128)  DEFAULT NULL COMMENT '原始文件名',
  `size`        BIGINT        DEFAULT NULL COMMENT '文件大小（字节）',
  `sort_no`     TINYINT       NOT NULL DEFAULT 1 COMMENT '排序，从 1 到 9',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_sort` (`goods_id`, `sort_no`),
  CONSTRAINT `fk_image_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_image_sort` CHECK (`sort_no` >= 1 AND `sort_no` <= 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品图片';

-- =============================================================================
-- 7. favorite  当前收藏关系（“是否仍收藏”）
--    收藏/取消：POST|DELETE /goods/{id}/favorite
--    收藏成功时同时向 user_behavior 写入 behavior_type=2
-- =============================================================================
CREATE TABLE `favorite` (
  `id`          BIGINT   NOT NULL COMMENT '雪花主键',
  `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
  `goods_id`    BIGINT   NOT NULL COMMENT '商品 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_favorite_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品收藏';

-- =============================================================================
-- 8. user_behavior  统一行为日志（已确定，替代 browse_history）
--    behavior_type：1 浏览 2 收藏 3 聊天 4 下单；协同过滤权重 1/3/4/5
--    浏览按同一用户同一商品 30 分钟去重（Redis），游客不记录
-- =============================================================================
CREATE TABLE `user_behavior` (
  `id`             BIGINT   NOT NULL COMMENT '雪花主键',
  `user_id`        BIGINT   NOT NULL COMMENT '用户ID',
  `goods_id`       BIGINT   NOT NULL COMMENT '商品ID',
  `behavior_type`  TINYINT  NOT NULL COMMENT '1浏览 2收藏 3聊天 4下单',
  `create_time`    DATETIME NOT NULL COMMENT '行为时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_goods` (`user_id`, `goods_id`),
  KEY `idx_goods_type` (`goods_id`, `behavior_type`),
  CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_behavior_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`),
  CONSTRAINT `chk_behavior_type` CHECK (`behavior_type` IN (1, 2, 3, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户行为日志（推荐用）';

-- =============================================================================
-- 8b. user_interest  用户兴趣分类（冷启动推荐用，总控 2026-11-18 采纳）
--    PUT /users/me/interests {categoryIds:[]}：整体覆盖，最多 5 个，仅限 ENABLED 分类
--    GET /users/me/interests；注册后弹窗选择，可跳过
-- =============================================================================
CREATE TABLE `user_interest` (
  `id`          BIGINT   NOT NULL COMMENT '雪花主键',
  `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
  `category_id` BIGINT   NOT NULL COMMENT '兴趣分类 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '选择时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category` (`user_id`, `category_id`),
  KEY `idx_category_id` (`category_id`),
  CONSTRAINT `fk_interest_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_interest_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户兴趣分类';

-- =============================================================================
-- 9. trade_order  订单
--    状态：PENDING / PAID / COMPLETED / CANCELLED
--    状态变更：PATCH /orders/{id}/status {action, reason?}
--    action：PAY / CANCEL / CONFIRM / REFUND（无 DELIVER，不设 deliver_time；时间字段仅 pay_time、complete_time）
--    流转：PENDING-PAY→PAID-CONFIRM→COMPLETED；PENDING/PAID 经 CANCEL/REFUND→CANCELLED
--    商品联动：下单 ON_SHELF→RESERVED（条件更新 WHERE status='ON_SHELF'），取消/退款→ON_SHELF，完成→SOLD
--    业务约束：不能购买自己的商品；进行中的订单视为商品已被预订（30002 / 30003）
-- =============================================================================
CREATE TABLE `trade_order` (
  `id`            BIGINT         NOT NULL COMMENT '雪花主键',
  `goods_id`      BIGINT         NOT NULL COMMENT '商品 ID',
  `buyer_id`      BIGINT         NOT NULL COMMENT '买家 ID',
  `seller_id`     BIGINT         NOT NULL COMMENT '卖家 ID',
  `price`         DECIMAL(10,2)  NOT NULL COMMENT '下单时的成交/挂牌价格快照',
  `remark`        VARCHAR(255)   DEFAULT NULL COMMENT '买家备注',
  `status`        VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PAID / COMPLETED / CANCELLED',
  `cancel_reason` VARCHAR(255)   DEFAULT NULL COMMENT '取消或退款原因',
  `pay_time`      DATETIME       DEFAULT NULL COMMENT '确认支付或预订时间',
  `complete_time` DATETIME       DEFAULT NULL COMMENT '完成时间（完成后双方可评价）',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_buyer_status` (`buyer_id`, `status`),
  KEY `idx_seller_status` (`seller_id`, `status`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_order_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`),
  CONSTRAINT `fk_order_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_order_seller` FOREIGN KEY (`seller_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_order_status` CHECK (`status` IN ('PENDING', 'PAID', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT `chk_order_not_self` CHECK (`buyer_id` <> `seller_id`),
  CONSTRAINT `chk_order_price` CHECK (`price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易订单';

-- =============================================================================
-- 9.1 pay_order  模拟支付单（每个订单同一时刻最多一张 WAITING 支付单）
-- =============================================================================
CREATE TABLE `pay_order` (
  `id`          BIGINT         NOT NULL COMMENT '雪花主键',
  `pay_no`      VARCHAR(32)    NOT NULL COMMENT '支付单号（对外暴露）',
  `order_id`    BIGINT         NOT NULL COMMENT '订单 ID',
  `user_id`     BIGINT         NOT NULL COMMENT '付款人（买家）ID',
  `amount`      DECIMAL(10,2)  NOT NULL COMMENT '支付金额（订单价格快照）',
  `status`      VARCHAR(16)    NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING 待支付 / PAID 已支付 / CLOSED 已关闭',
  `expire_time` DATETIME       NOT NULL COMMENT '过期时间（= 下单时间 + 超时时长，默认 15 分钟）',
  `pay_time`    DATETIME       DEFAULT NULL COMMENT '支付时间',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  -- 生成列：仅 WAITING 且未删除时为 1，否则 NULL；配合唯一索引保证同一订单最多一张 WAITING 支付单
  `waiting_flag` TINYINT GENERATED ALWAYS AS (IF(`status` = 'WAITING' AND `deleted` = 0, 1, NULL)) STORED
                 COMMENT '生成列：WAITING 唯一性约束用，应用层不读写',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_order_waiting` (`order_id`, `waiting_flag`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_expire` (`status`, `expire_time`),
  CONSTRAINT `fk_pay_order` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`),
  CONSTRAINT `fk_pay_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_pay_status` CHECK (`status` IN ('WAITING', 'PAID', 'CLOSED')),
  CONSTRAINT `chk_pay_amount` CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟支付单';

-- =============================================================================
-- 10. review  评价（订单完成后双方各评价一次；50001 已评价，50002 订单未完成不可评价）
-- =============================================================================
CREATE TABLE `review` (
  `id`           BIGINT        NOT NULL COMMENT '雪花主键',
  `order_id`     BIGINT        NOT NULL COMMENT '订单 ID',
  `from_user_id` BIGINT        NOT NULL COMMENT '评价人 ID',
  `to_user_id`   BIGINT        NOT NULL COMMENT '被评价人 ID',
  `score`        TINYINT       NOT NULL COMMENT '评分，1–5 分',
  `content`      VARCHAR(500)  DEFAULT NULL COMMENT '评价内容',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_from` (`order_id`, `from_user_id`),
  KEY `idx_to_user` (`to_user_id`),
  CONSTRAINT `fk_review_order` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`),
  CONSTRAINT `fk_review_from` FOREIGN KEY (`from_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_review_to` FOREIGN KEY (`to_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_review_score` CHECK (`score` >= 1 AND `score` <= 5),
  CONSTRAINT `chk_review_not_self` CHECK (`from_user_id` <> `to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单评价';

-- =============================================================================
-- 11. chat_session  聊天会话
--    POST /chat/sessions {goodsId, toUserId}：已存在则返回原会话
--    用生成列保证同一商品、同一对用户只有一条会话
-- =============================================================================
CREATE TABLE `chat_session` (
  `id`               BIGINT        NOT NULL COMMENT '雪花主键',
  `goods_id`         BIGINT        NOT NULL COMMENT '关联商品 ID',
  `initiator_id`     BIGINT        NOT NULL COMMENT '发起人 ID',
  `peer_id`          BIGINT        NOT NULL COMMENT '对方用户 ID',
  `user_low_id`      BIGINT        GENERATED ALWAYS AS (LEAST(`initiator_id`, `peer_id`)) STORED COMMENT '较小用户 ID，用于唯一约束',
  `user_high_id`     BIGINT        GENERATED ALWAYS AS (GREATEST(`initiator_id`, `peer_id`)) STORED COMMENT '较大用户 ID，用于唯一约束',
  `last_content`     VARCHAR(255)  DEFAULT NULL COMMENT '最近一条消息摘要',
  `last_time`        DATETIME      DEFAULT NULL COMMENT '最近消息时间',
  `initiator_unread` INT           NOT NULL DEFAULT 0 COMMENT '发起人未读数',
  `peer_unread`      INT           NOT NULL DEFAULT 0 COMMENT '对方未读数',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_pair` (`goods_id`, `user_low_id`, `user_high_id`),
  KEY `idx_initiator_id` (`initiator_id`),
  KEY `idx_peer_id` (`peer_id`),
  CONSTRAINT `fk_session_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`),
  CONSTRAINT `fk_session_initiator` FOREIGN KEY (`initiator_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_session_peer` FOREIGN KEY (`peer_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_session_not_self` CHECK (`initiator_id` <> `peer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话';

-- =============================================================================
-- 12. chat_message  聊天消息
--    type：1 文本 2 图片 3 商品卡片（规范第 7 节，STOMP 消息体亦使用该取值）
-- =============================================================================
CREATE TABLE `chat_message` (
  `id`           BIGINT        NOT NULL COMMENT '雪花主键',
  `session_id`   BIGINT        NOT NULL COMMENT '会话 ID',
  `from_user_id` BIGINT        NOT NULL COMMENT '发送人 ID',
  `to_user_id`   BIGINT        NOT NULL COMMENT '接收人 ID',
  `goods_id`     BIGINT        DEFAULT NULL COMMENT '关联商品 ID（商品卡片或会话商品）',
  `content`      TEXT          NOT NULL COMMENT '文本内容、图片 URL 或商品卡片载荷',
  `type`         TINYINT       NOT NULL DEFAULT 1 COMMENT '1 文本 2 图片 3 商品卡片',
  `read_flag`    TINYINT       NOT NULL DEFAULT 0 COMMENT '0 未读 1 已读',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_time` (`session_id`, `create_time`),
  KEY `idx_to_user_read` (`to_user_id`, `read_flag`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`),
  CONSTRAINT `fk_message_from` FOREIGN KEY (`from_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_message_to` FOREIGN KEY (`to_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_message_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`),
  CONSTRAINT `chk_message_type` CHECK (`type` IN (1, 2, 3)),
  CONSTRAINT `chk_message_read` CHECK (`read_flag` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息';

-- =============================================================================
-- 13. report  举报与审核
--    用户可举报商品或用户；管理员审核后回写结果（审核结果取值与规范一致：APPROVE / REJECT）
-- =============================================================================
CREATE TABLE `report` (
  `id`          BIGINT        NOT NULL COMMENT '雪花主键',
  `reporter_id` BIGINT        NOT NULL COMMENT '举报人 ID',
  `target_type` VARCHAR(16)   NOT NULL COMMENT '被举报对象类型：GOODS / USER',
  `target_id`   BIGINT        NOT NULL COMMENT '被举报对象 ID',
  `reason`      VARCHAR(64)   NOT NULL COMMENT '举报原因（简要分类或标签）',
  `detail`      VARCHAR(500)  DEFAULT NULL COMMENT '补充说明',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
  `result`      VARCHAR(16)   DEFAULT NULL COMMENT '审核结果：APPROVE / REJECT，待审时为空',
  `remark`      VARCHAR(255)  DEFAULT NULL COMMENT '处理说明',
  `auditor_id`  BIGINT        DEFAULT NULL COMMENT '审核人 ID',
  `audit_time`  DATETIME      DEFAULT NULL COMMENT '审核时间',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_status` (`status`),
  KEY `idx_reporter_id` (`reporter_id`),
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_report_auditor` FOREIGN KEY (`auditor_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_report_target_type` CHECK (`target_type` IN ('GOODS', 'USER')),
  CONSTRAINT `chk_report_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT `chk_report_result` CHECK (`result` IS NULL OR `result` IN ('APPROVE', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报与审核';

-- =============================================================================
-- 14. announcement  公告（后台发布，前台展示）
-- =============================================================================
CREATE TABLE `announcement` (
  `id`           BIGINT        NOT NULL COMMENT '雪花主键',
  `title`        VARCHAR(120)  NOT NULL COMMENT '标题',
  `content`      TEXT          NOT NULL COMMENT '正文',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / OFF_SHELF',
  `publisher_id` BIGINT        NOT NULL COMMENT '发布人（管理员）ID',
  `publish_time` DATETIME      DEFAULT NULL COMMENT '实际发布时间',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_publish` (`status`, `publish_time`),
  CONSTRAINT `fk_announcement_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_announcement_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'OFF_SHELF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台公告';
