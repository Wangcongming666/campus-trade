package com.campus.trade.common;

/**
 * 业务枚举取值（与 schema.sql CHECK 约束一致）。
 */
public final class Constants {
    private Constants() {
    }

    public static final class GoodsStatus {
        public static final String PENDING_AUDIT = "PENDING_AUDIT";
        public static final String ON_SHELF = "ON_SHELF";
        public static final String RESERVED = "RESERVED";
        public static final String OFF_SHELF = "OFF_SHELF";
        public static final String SOLD = "SOLD";
        public static final String REJECTED = "REJECTED";
        private GoodsStatus() {
        }
    }

    public static final class Role {
        public static final String USER = "USER";
        public static final String ADMIN = "ADMIN";
        private Role() {
        }
    }

    public static final class UserStatus {
        public static final String NORMAL = "NORMAL";
        public static final String BANNED = "BANNED";
        private UserStatus() {
        }
    }

    public static final class AuthStatus {
        public static final String NONE = "NONE";
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        private AuthStatus() {
        }
    }

    public static final class CategoryStatus {
        public static final String ENABLED = "ENABLED";
        private CategoryStatus() {
        }
    }

    /** user_behavior.behavior_type：1 浏览 2 收藏 3 聊天 4 下单 */
    public static final class BehaviorType {
        public static final int VIEW = 1;
        public static final int FAVORITE = 2;
        public static final int CHAT = 3;
        public static final int ORDER = 4;
        private BehaviorType() {
        }
    }

    public static final class OrderStatus {
        public static final String PENDING = "PENDING";
        public static final String PAID = "PAID";
        public static final String COMPLETED = "COMPLETED";
        public static final String CANCELLED = "CANCELLED";
        private OrderStatus() {
        }
    }

    /** 订单动作（无 DELIVER） */
    public static final class OrderAction {
        public static final String PAY = "PAY";
        public static final String CONFIRM = "CONFIRM";
        public static final String CANCEL = "CANCEL";
        public static final String REFUND = "REFUND";
        private OrderAction() {
        }
    }

    public static final class AuditResult {
        public static final String APPROVE = "APPROVE";
        public static final String REJECT = "REJECT";
        private AuditResult() {
        }
    }

    public static final class ReportStatus {
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        private ReportStatus() {
        }
    }

    public static final class AnnouncementStatus {
        public static final String DRAFT = "DRAFT";
        public static final String PUBLISHED = "PUBLISHED";
        public static final String OFF_SHELF = "OFF_SHELF";
        private AnnouncementStatus() {
        }
    }

    public static final String CONDITION_REGEX = "NEW|LIKE_NEW|GOOD|FAIR|POOR";
    public static final String TRADE_METHOD_REGEX = "FACE|EXPRESS|EITHER";
}
