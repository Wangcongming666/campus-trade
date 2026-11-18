package com.campus.trade.common;

import lombok.Getter;

/**
 * 错误码（规范第 5 节分段）。
 */
@Getter
public enum ErrorCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    TOKEN_EXPIRED(401, "登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统异常"),

    // 10000–10999 用户与认证
    USERNAME_EXISTS(10001, "用户名已存在"),
    BAD_CREDENTIALS(10002, "账号或密码错误"),
    STUDENT_NOT_VERIFIED(10003, "学生认证未通过"),
    ACCOUNT_BANNED(10004, "账号已封禁"),
    PHONE_EXISTS(10005, "手机号已被使用"),
    CAPTCHA_INVALID(10006, "验证码错误或已过期"),
    OLD_PASSWORD_WRONG(10007, "原密码错误"),
    STUDENT_ALREADY_VERIFIED(10008, "已通过学生认证"),
    STUDENT_VERIFY_PENDING(10009, "学生认证审核中，请勿重复提交"),
    STUDENT_NO_EXISTS(10010, "学号已被使用"),
    USER_NOT_FOUND(10011, "用户不存在"),
    REFRESH_TOKEN_INVALID(10012, "刷新令牌无效"),

    // 20000–20999 商品与分类
    GOODS_NOT_FOUND(20001, "商品不存在"),
    GOODS_OFF_SHELF(20002, "商品已下架"),
    GOODS_NOT_OWNER(20003, "非本人商品"),
    CATEGORY_NOT_FOUND(20004, "分类不存在或已停用"),
    GOODS_STATUS_NOT_ALLOWED(20005, "商品当前状态不允许此操作"),

    // 30000–30999 订单（phase 2）
    ORDER_STATUS_NOT_ALLOWED(30001, "订单状态不允许此操作"),
    ORDER_SELF_GOODS(30002, "不能购买自己的商品"),
    GOODS_RESERVED(30003, "商品已被预订"),
    ORDER_NOT_FOUND(30004, "订单不存在"),
    ORDER_ACTION_FORBIDDEN(30005, "当前角色不能对该订单执行此操作"),

    // 40000–40999 聊天消息
    CHAT_SELF(40001, "不能和自己聊天"),
    CHAT_NOT_SELLER(40002, "会话双方须包含该商品的卖家"),
    CHAT_SESSION_NOT_FOUND(40003, "会话不存在"),
    CHAT_MESSAGE_INVALID(40004, "消息内容无效"),

    // 50000–50999 评价与信誉
    REVIEW_EXISTS(50001, "已评价"),
    REVIEW_ORDER_NOT_COMPLETED(50002, "订单未完成不可评价"),

    // 60000–60999 后台管理与审核
    GOODS_NOT_PENDING_AUDIT(60001, "商品不在待审核状态"),
    ADMIN_SELF_OPERATION(60002, "不能修改自己的账号状态"),
    ADMIN_TARGET_IS_ADMIN(60003, "不能封禁管理员"),
    VERIFY_ALREADY_HANDLED(60004, "认证申请已处理"),
    REPORT_SELF(60005, "不能举报自己或自己的商品"),
    REPORT_DUPLICATE(60006, "已举报，请等待处理"),
    REPORT_ALREADY_HANDLED(60007, "举报已处理"),
    ANNOUNCEMENT_NOT_FOUND(60008, "公告不存在"),
    VERIFY_NOT_FOUND(60009, "认证申请不存在"),
    REPORT_NOT_FOUND(60010, "举报不存在"),

    // 70000–70999 文件上传
    FILE_TYPE_NOT_SUPPORTED(70001, "文件类型不支持，仅支持 jpg/jpeg/png/webp"),
    FILE_TOO_LARGE(70002, "文件过大，单张不超过 5MB"),
    FILE_UPLOAD_FAILED(70003, "文件上传失败"),

    // 80000–80999 推荐与价格建议
    PRICE_NO_DATA(80001, "该分类暂无价格数据，请填写原价后再试"),

    // 90000–90999 AI 图片识别
    AI_IMAGE_REQUIRED(90001, "请上传图片文件或提供本站图片地址 imageUrl"),
    AI_IMAGE_NOT_FOUND(90002, "图片不存在或已被删除"),

    // 91000–91999 模拟支付
    PAY_ORDER_NOT_FOUND(91001, "支付单不存在"),
    PAY_ORDER_EXPIRED(91002, "支付已超时，订单已自动取消"),
    PAY_ORDER_CLOSED(91003, "支付单已关闭");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
