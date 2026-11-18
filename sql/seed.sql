-- =============================================================================
-- 校园二手交易智能平台 · 开发/演示种子数据
-- 先执行 schema.sql（会重建所有表），再执行本脚本：
--   mysql -uroot -p < schema.sql && mysql -uroot -p < seed.sql
-- 账号：admin / admin123（ADMIN）；user1 / 123456（已认证）；user2 / 123456（认证待审核）；
--       user3、user4 / 123456（普通用户，提供协同过滤所需的共同行为）（均为 BCrypt）
-- 推荐演示：user1 登录后首页可同时看到 CF（你…类似的…）、INTEREST（你关注了…）、HOT（本周热门/最新发布）三种理由
-- 图片为占位 URL（picsum.photos）
-- =============================================================================
USE `campus_trade`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 用户
INSERT INTO `sys_user` (`id`, `username`, `password_hash`, `nickname`, `avatar`, `phone`, `student_no`, `school`,
                        `role`, `status`, `credit_score`, `student_auth_status`)
VALUES
  (2000000000000000001, 'admin', '$2a$10$iglsUKCWnAeVBzxWdM8jdeE5b1iP8mG48100woDLtt7PC29.L20Pu', '管理员',
   'https://picsum.photos/seed/avatar-admin/200/200', '13800000000', NULL, NULL, 'ADMIN', 'NORMAL', 100, 'NONE'),
  (2000000000000000002, 'user1', '$2a$10$RF6f90pg8oqmDx1wd40nx.Q3S4.1VA6TL9m4onli95fizEEYv9w8e', '小明同学',
   'https://picsum.photos/seed/avatar-user1/200/200', '13800000001', '2022001001', '示例大学', 'USER', 'NORMAL', 98, 'APPROVED'),
  (2000000000000000003, 'user2', '$2a$10$RF6f90pg8oqmDx1wd40nx.Q3S4.1VA6TL9m4onli95fizEEYv9w8e', '小红学姐',
   'https://picsum.photos/seed/avatar-user2/200/200', '13800000002', NULL, NULL, 'USER', 'NORMAL', 100, 'PENDING'),
  (2000000000000000004, 'user3', '$2a$10$RF6f90pg8oqmDx1wd40nx.Q3S4.1VA6TL9m4onli95fizEEYv9w8e', '阿杰学长',
   'https://picsum.photos/seed/avatar-user3/200/200', '13800000003', NULL, NULL, 'USER', 'NORMAL', 100, 'NONE'),
  (2000000000000000005, 'user4', '$2a$10$RF6f90pg8oqmDx1wd40nx.Q3S4.1VA6TL9m4onli95fizEEYv9w8e', '小雨同学',
   'https://picsum.photos/seed/avatar-user4/200/200', '13800000004', NULL, NULL, 'USER', 'NORMAL', 100, 'NONE');

INSERT INTO `student_auth` (`id`, `user_id`, `student_no`, `real_name`, `school`, `card_image_url`, `status`,
                            `auditor_id`, `audit_time`)
VALUES
  (2100000000000000001, 2000000000000000002, '2022001001', '王小明', '示例大学',
   'https://picsum.photos/seed/card-user1/600/400', 'APPROVED', 2000000000000000001, NOW()),
  (2100000000000000002, 2000000000000000003, '2023002002', '李小红', '示例大学',
   'https://picsum.photos/seed/card-user2/600/400', 'PENDING', NULL, NULL);

-- ---------------------------------------------------------------- 分类（8 个一级 + 子分类 + 1 个停用）
INSERT INTO `category` (`id`, `parent_id`, `name`, `sort`, `status`) VALUES
  (3000000000000000001, NULL, '数码电子', 1, 'ENABLED'),
  (3000000000000000002, NULL, '教材书籍', 2, 'ENABLED'),
  (3000000000000000003, NULL, '生活用品', 3, 'ENABLED'),
  (3000000000000000004, NULL, '服饰鞋包', 4, 'ENABLED'),
  (3000000000000000005, NULL, '运动户外', 5, 'ENABLED'),
  (3000000000000000006, NULL, '美妆个护', 6, 'ENABLED'),
  (3000000000000000007, NULL, '乐器文娱', 7, 'ENABLED'),
  (3000000000000000008, NULL, '交通出行', 8, 'ENABLED'),
  (3000000000000000009, NULL, '其他（已停用）', 99, 'DISABLED');

INSERT INTO `category` (`id`, `parent_id`, `name`, `sort`, `status`) VALUES
  (3000000000000000101, 3000000000000000001, '手机', 1, 'ENABLED'),
  (3000000000000000102, 3000000000000000001, '电脑平板', 2, 'ENABLED'),
  (3000000000000000103, 3000000000000000001, '耳机音箱', 3, 'ENABLED'),
  (3000000000000000201, 3000000000000000002, '专业教材', 1, 'ENABLED'),
  (3000000000000000202, 3000000000000000002, '考研考证', 2, 'ENABLED'),
  (3000000000000000203, 3000000000000000002, '课外读物', 3, 'ENABLED'),
  (3000000000000000301, 3000000000000000003, '宿舍电器', 1, 'ENABLED'),
  (3000000000000000302, 3000000000000000003, '收纳日用', 2, 'ENABLED'),
  (3000000000000000801, 3000000000000000008, '自行车', 1, 'ENABLED'),
  (3000000000000000802, 3000000000000000008, '电动车', 2, 'ENABLED');

-- ---------------------------------------------------------------- 商品（10 件 ON_SHELF + 待审/下架/已预订×2/已售/驳回各示例）
INSERT INTO `goods` (`id`, `seller_id`, `category_id`, `title`, `description`, `price`, `original_price`, `condition`,
                     `trade_method`, `status`, `cover_url`, `view_count`, `favorite_count`, `create_time`)
VALUES
  (4000000000000000001, 2000000000000000002, 3000000000000000101, 'iPhone 13 128G 蓝色',
   '自用一年半，电池健康 88%，无磕碰，配原装充电线。', 2299.00, 5999.00, 'LIKE_NEW', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods1-1/600/600', 56, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (4000000000000000002, 2000000000000000002, 3000000000000000102, 'iPad Air 4 64G + 笔',
   '记笔记神器，送二代笔和保护壳，屏幕完好。', 2100.00, 4799.00, 'GOOD', 'EITHER', 'ON_SHELF',
   'https://picsum.photos/seed/goods2-1/600/600', 88, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (4000000000000000003, 2000000000000000003, 3000000000000000103, '索尼 WH-1000XM4 降噪耳机',
   '降噪效果很好，耳罩有轻微使用痕迹，盒子配件齐全。', 899.00, 2299.00, 'GOOD', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods3-1/600/600', 34, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (4000000000000000004, 2000000000000000003, 3000000000000000201, '高等数学 同济第七版 上下册',
   '有少量笔记，不影响阅读，期末复习很有用。', 25.00, 78.00, 'FAIR', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods4-1/600/600', 120, 0, DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (4000000000000000005, 2000000000000000002, 3000000000000000202, '考研英语 真题黄皮书 2026',
   '全新未拆封，买重了。', 45.00, 89.00, 'NEW', 'EITHER', 'ON_SHELF',
   'https://picsum.photos/seed/goods5-1/600/600', 12, 0, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (4000000000000000006, 2000000000000000003, 3000000000000000301, '小米 迷你电饭煲 1.6L',
   '宿舍可用，功率小不跳闸，九成新。', 79.00, 199.00, 'LIKE_NEW', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods6-1/600/600', 23, 0, DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (4000000000000000007, 2000000000000000002, 3000000000000000004, '优衣库 羽绒服 L 码 黑色',
   '穿过一个冬天，干洗过，保暖性好。', 150.00, 499.00, 'GOOD', 'EXPRESS', 'ON_SHELF',
   'https://picsum.photos/seed/goods7-1/600/600', 9, 0, DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (4000000000000000008, 2000000000000000003, 3000000000000000005, '李宁 羽毛球拍 一对 + 球',
   '社团活动用过几次，送一筒球。', 120.00, 360.00, 'GOOD', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods8-1/600/600', 17, 0, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (4000000000000000009, 2000000000000000002, 3000000000000000007, '雅马哈 F310 民谣吉他',
   '入门款，音准正常，送琴包和变调夹。', 380.00, 799.00, 'GOOD', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods9-1/600/600', 41, 0, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (4000000000000000010, 2000000000000000003, 3000000000000000801, '捷安特 山地自行车 26 寸',
   '毕业出，刹车和变速都正常，送车锁。', 450.00, 1580.00, 'FAIR', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods10-1/600/600', 66, 0, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (4000000000000000011, 2000000000000000002, 3000000000000000203, '三体 全集 三册',
   '待审核示例商品。', 39.00, 93.00, 'LIKE_NEW', 'FACE', 'PENDING_AUDIT',
   'https://picsum.photos/seed/goods11-1/600/600', 0, 0, NOW()),
  (4000000000000000012, 2000000000000000003, 3000000000000000302, '宿舍收纳箱 3 个',
   '已下架示例商品。', 30.00, 60.00, 'GOOD', 'FACE', 'OFF_SHELF',
   'https://picsum.photos/seed/goods12-1/600/600', 5, 0, DATE_SUB(NOW(), INTERVAL 11 DAY)),
  -- 订单关联商品：13 已预订（待付款订单）、14 已预订（已付款订单）、15 已售出（已完成订单）
  (4000000000000000013, 2000000000000000003, 3000000000000000101, '小米手环 7 NFC 版',
   '用了半年，表带换过新的。', 99.00, 249.00, 'GOOD', 'FACE', 'RESERVED',
   'https://picsum.photos/seed/goods13-1/600/600', 30, 0, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (4000000000000000014, 2000000000000000002, 3000000000000000102, '机械键盘 87 键 青轴',
   '打字手感清脆，键帽无打油。', 129.00, 299.00, 'LIKE_NEW', 'EITHER', 'RESERVED',
   'https://picsum.photos/seed/goods14-1/600/600', 21, 0, DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (4000000000000000015, 2000000000000000003, 3000000000000000301, '护眼 LED 台灯 三档调光',
   '宿舍学习用，灯光柔和。', 35.00, 129.00, 'GOOD', 'FACE', 'SOLD',
   'https://picsum.photos/seed/goods15-1/600/600', 44, 0, DATE_SUB(NOW(), INTERVAL 6 DAY)),
  -- 推荐演示商品（user2 在售）：17/19 与索尼耳机有共同交互 → user1 的 CF；18 属教材书籍 → user1 的 INTEREST
  (4000000000000000017, 2000000000000000003, 3000000000000000103, 'AirPods Pro 二代 主动降噪',
   '苹果原装，续航正常，充电盒有细微划痕。', 699.00, 1899.00, 'GOOD', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods17-1/600/600', 27, 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (4000000000000000018, 2000000000000000003, 3000000000000000201, '线性代数 同济第六版',
   '几乎没有笔记，附课后习题答案。', 15.00, 36.00, 'LIKE_NEW', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods18-1/600/600', 8, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (4000000000000000019, 2000000000000000003, 3000000000000000103, 'JBL Flip 5 蓝牙音箱',
   '防水，低音足，宿舍外放够用。', 299.00, 799.00, 'GOOD', 'FACE', 'ON_SHELF',
   'https://picsum.photos/seed/goods19-1/600/600', 15, 0, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 审核驳回示例（带 audit_reason）
INSERT INTO `goods` (`id`, `seller_id`, `category_id`, `title`, `description`, `price`, `original_price`, `condition`,
                     `trade_method`, `status`, `cover_url`, `audit_reason`, `auditor_id`, `audit_time`, `create_time`)
VALUES
  (4000000000000000016, 2000000000000000002, 3000000000000000203, '四六级答案（内部资料）',
   '驳回示例商品。', 10.00, NULL, 'NEW', 'EXPRESS', 'REJECTED',
   'https://picsum.photos/seed/goods16-1/600/600', '涉嫌违规考试资料，禁止发布', 2000000000000000001,
   DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ---------------------------------------------------------------- 商品图片（sort_no 从 1 开始，第一张即封面）
INSERT INTO `goods_image` (`id`, `goods_id`, `url`, `name`, `sort_no`) VALUES
  (5000000000000000101, 4000000000000000001, 'https://picsum.photos/seed/goods1-1/600/600', 'goods1-1.jpg', 1),
  (5000000000000000102, 4000000000000000001, 'https://picsum.photos/seed/goods1-2/600/600', 'goods1-2.jpg', 2),
  (5000000000000000103, 4000000000000000001, 'https://picsum.photos/seed/goods1-3/600/600', 'goods1-3.jpg', 3),
  (5000000000000000201, 4000000000000000002, 'https://picsum.photos/seed/goods2-1/600/600', 'goods2-1.jpg', 1),
  (5000000000000000202, 4000000000000000002, 'https://picsum.photos/seed/goods2-2/600/600', 'goods2-2.jpg', 2),
  (5000000000000000301, 4000000000000000003, 'https://picsum.photos/seed/goods3-1/600/600', 'goods3-1.jpg', 1),
  (5000000000000000302, 4000000000000000003, 'https://picsum.photos/seed/goods3-2/600/600', 'goods3-2.jpg', 2),
  (5000000000000000401, 4000000000000000004, 'https://picsum.photos/seed/goods4-1/600/600', 'goods4-1.jpg', 1),
  (5000000000000000501, 4000000000000000005, 'https://picsum.photos/seed/goods5-1/600/600', 'goods5-1.jpg', 1),
  (5000000000000000601, 4000000000000000006, 'https://picsum.photos/seed/goods6-1/600/600', 'goods6-1.jpg', 1),
  (5000000000000000602, 4000000000000000006, 'https://picsum.photos/seed/goods6-2/600/600', 'goods6-2.jpg', 2),
  (5000000000000000701, 4000000000000000007, 'https://picsum.photos/seed/goods7-1/600/600', 'goods7-1.jpg', 1),
  (5000000000000000801, 4000000000000000008, 'https://picsum.photos/seed/goods8-1/600/600', 'goods8-1.jpg', 1),
  (5000000000000000901, 4000000000000000009, 'https://picsum.photos/seed/goods9-1/600/600', 'goods9-1.jpg', 1),
  (5000000000000000902, 4000000000000000009, 'https://picsum.photos/seed/goods9-2/600/600', 'goods9-2.jpg', 2),
  (5000000000000001001, 4000000000000000010, 'https://picsum.photos/seed/goods10-1/600/600', 'goods10-1.jpg', 1),
  (5000000000000001101, 4000000000000000011, 'https://picsum.photos/seed/goods11-1/600/600', 'goods11-1.jpg', 1),
  (5000000000000001201, 4000000000000000012, 'https://picsum.photos/seed/goods12-1/600/600', 'goods12-1.jpg', 1),
  (5000000000000001301, 4000000000000000013, 'https://picsum.photos/seed/goods13-1/600/600', 'goods13-1.jpg', 1),
  (5000000000000001401, 4000000000000000014, 'https://picsum.photos/seed/goods14-1/600/600', 'goods14-1.jpg', 1),
  (5000000000000001501, 4000000000000000015, 'https://picsum.photos/seed/goods15-1/600/600', 'goods15-1.jpg', 1),
  (5000000000000001601, 4000000000000000016, 'https://picsum.photos/seed/goods16-1/600/600', 'goods16-1.jpg', 1),
  (5000000000000001701, 4000000000000000017, 'https://picsum.photos/seed/goods17-1/600/600', 'goods17-1.jpg', 1),
  (5000000000000001801, 4000000000000000018, 'https://picsum.photos/seed/goods18-1/600/600', 'goods18-1.jpg', 1),
  (5000000000000001901, 4000000000000000019, 'https://picsum.photos/seed/goods19-1/600/600', 'goods19-1.jpg', 1);

-- ---------------------------------------------------------------- 交易地点（goods.trade_location，选填）
UPDATE `goods` SET `trade_location` = '东区食堂门口' WHERE `id` IN (4000000000000000001, 4000000000000000006, 4000000000000000013);
UPDATE `goods` SET `trade_location` = '图书馆一楼大厅' WHERE `id` IN (4000000000000000002, 4000000000000000004, 4000000000000000018);
UPDATE `goods` SET `trade_location` = '二食堂门口' WHERE `id` IN (4000000000000000003, 4000000000000000017, 4000000000000000019);
UPDATE `goods` SET `trade_location` = '西区宿舍 6 栋楼下' WHERE `id` IN (4000000000000000005, 4000000000000000007, 4000000000000000014);
UPDATE `goods` SET `trade_location` = '体育馆南门' WHERE `id` IN (4000000000000000008, 4000000000000000010);
UPDATE `goods` SET `trade_location` = '音乐楼 102 门口' WHERE `id` = 4000000000000000009;
UPDATE `goods` SET `trade_location` = '北门快递站' WHERE `id` IN (4000000000000000011, 4000000000000000012, 4000000000000000015);

-- ---------------------------------------------------------------- 兴趣分类示例
INSERT INTO `user_interest` (`id`, `user_id`, `category_id`) VALUES
  (6000000000000000001, 2000000000000000002, 3000000000000000001),
  (6000000000000000002, 2000000000000000002, 3000000000000000002);

-- ---------------------------------------------------------------- 订单（四种状态各一）
-- 待付款订单 7000000000000000001 以导入时刻为下单时间：15 分钟内未支付会被定时任务自动取消（演示超时关单）
INSERT INTO `trade_order` (`id`, `goods_id`, `buyer_id`, `seller_id`, `price`, `remark`, `status`, `cancel_reason`,
                           `pay_time`, `complete_time`, `create_time`)
VALUES
  (7000000000000000001, 4000000000000000013, 2000000000000000002, 2000000000000000003, 99.00, '今晚图书馆门口交易',
   'PENDING', NULL, NULL, NULL, NOW()),
  (7000000000000000002, 4000000000000000014, 2000000000000000003, 2000000000000000002, 129.00, NULL,
   'PAID', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (7000000000000000003, 4000000000000000015, 2000000000000000002, 2000000000000000003, 35.00, NULL,
   'COMPLETED', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (7000000000000000004, 4000000000000000006, 2000000000000000002, 2000000000000000003, 79.00, NULL,
   'CANCELLED', '买家时间冲突，取消订单', NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ---------------------------------------------------------------- 模拟支付单（待付款订单对应一张 WAITING 支付单，有效期至下单后 15 分钟）
INSERT INTO `pay_order` (`id`, `pay_no`, `order_id`, `user_id`, `amount`, `status`, `expire_time`, `pay_time`, `create_time`)
VALUES
  (7200000000000000001, 'P202611180000000001', 7000000000000000001, 2000000000000000002, 99.00, 'WAITING',
   DATE_ADD(NOW(), INTERVAL 15 MINUTE), NULL, NOW()),
  (7200000000000000002, 'P202611180000000002', 7000000000000000002, 2000000000000000003, 129.00, 'PAID',
   DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 15 MINUTE), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---------------------------------------------------------------- 评价（已完成订单，买家 user1 评卖家 user2，5 分 → 信誉分 100）
INSERT INTO `review` (`id`, `order_id`, `from_user_id`, `to_user_id`, `score`, `content`, `create_time`) VALUES
  (7100000000000000001, 7000000000000000003, 2000000000000000002, 2000000000000000003, 5, '台灯很好用，学姐人很nice',
   DATE_SUB(NOW(), INTERVAL 4 DAY));

-- ---------------------------------------------------------------- 聊天（user1 咨询 user2 的耳机，最后一条 user2 发出，user1 未读 1）
INSERT INTO `chat_session` (`id`, `goods_id`, `initiator_id`, `peer_id`, `last_content`, `last_time`,
                            `initiator_unread`, `peer_unread`, `create_time`)
VALUES
  (8000000000000000001, 4000000000000000003, 2000000000000000002, 2000000000000000003, '可以，明天下午二食堂见',
   DATE_SUB(NOW(), INTERVAL 30 MINUTE), 1, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR));

INSERT INTO `chat_message` (`id`, `session_id`, `from_user_id`, `to_user_id`, `goods_id`, `content`, `type`, `read_flag`,
                            `create_time`)
VALUES
  (8100000000000000001, 8000000000000000001, 2000000000000000002, 2000000000000000003, 4000000000000000003,
   '学姐你好，耳机还在吗？', 1, 1, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
  (8100000000000000002, 8000000000000000001, 2000000000000000003, 2000000000000000002, 4000000000000000003,
   '在的，降噪正常', 1, 1, DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
  (8100000000000000003, 8000000000000000001, 2000000000000000002, 2000000000000000003, 4000000000000000003,
   '850 可以吗？可以的话当面看看', 1, 1, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
  (8100000000000000004, 8000000000000000001, 2000000000000000003, 2000000000000000002, 4000000000000000003,
   '可以，明天下午二食堂见', 1, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE));

-- ---------------------------------------------------------------- 行为日志（与上面的订单/聊天一致）
INSERT INTO `user_behavior` (`id`, `user_id`, `goods_id`, `behavior_type`, `create_time`) VALUES
  (6100000000000000001, 2000000000000000002, 4000000000000000003, 1, DATE_SUB(NOW(), INTERVAL 61 MINUTE)),
  (6100000000000000002, 2000000000000000002, 4000000000000000003, 3, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
  (6100000000000000003, 2000000000000000002, 4000000000000000013, 4, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (6100000000000000004, 2000000000000000003, 4000000000000000014, 4, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6100000000000000005, 2000000000000000002, 4000000000000000015, 4, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (6100000000000000006, 2000000000000000002, 4000000000000000006, 4, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  -- user3、user4 同时交互索尼耳机(3)与 AirPods(17)/JBL(19)，形成物品相似度；user4 浏览羽毛球拍(8) 贡献热度
  (6100000000000000007, 2000000000000000004, 4000000000000000003, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (6100000000000000008, 2000000000000000004, 4000000000000000003, 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (6100000000000000009, 2000000000000000004, 4000000000000000017, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6100000000000000010, 2000000000000000004, 4000000000000000017, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6100000000000000011, 2000000000000000004, 4000000000000000019, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6100000000000000012, 2000000000000000005, 4000000000000000003, 1, DATE_SUB(NOW(), INTERVAL 30 HOUR)),
  (6100000000000000013, 2000000000000000005, 4000000000000000017, 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
  (6100000000000000014, 2000000000000000005, 4000000000000000017, 2, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
  (6100000000000000015, 2000000000000000005, 4000000000000000019, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
  (6100000000000000016, 2000000000000000005, 4000000000000000008, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR));

-- ---------------------------------------------------------------- 收藏（与上面 behavior_type=2 一致）
INSERT INTO `favorite` (`id`, `user_id`, `goods_id`, `create_time`) VALUES
  (6200000000000000001, 2000000000000000004, 4000000000000000003, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (6200000000000000002, 2000000000000000004, 4000000000000000017, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (6200000000000000003, 2000000000000000005, 4000000000000000017, DATE_SUB(NOW(), INTERVAL 20 HOUR));

-- ---------------------------------------------------------------- 举报（待处理一条）
INSERT INTO `report` (`id`, `reporter_id`, `target_type`, `target_id`, `reason`, `detail`, `status`) VALUES
  (9000000000000000001, 2000000000000000002, 'GOODS', 4000000000000000010, '描述不符', '图片与实物颜色不一致', 'PENDING');

-- ---------------------------------------------------------------- 公告（已发布 + 草稿）
INSERT INTO `announcement` (`id`, `title`, `content`, `status`, `publisher_id`, `publish_time`) VALUES
  (9100000000000000001, '欢迎使用校园二手交易平台', '请优先选择校内当面交易，注意保护个人信息与财产安全。',
   'PUBLISHED', 2000000000000000001, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (9100000000000000002, '毕业季跳蚤市场（草稿）', '时间地点待定。', 'DRAFT', 2000000000000000001, NULL);
