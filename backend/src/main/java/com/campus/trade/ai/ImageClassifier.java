package com.campus.trade.ai;

/**
 * 图片自动分类（商品发布时根据图片推荐分类）。实现类注册为 Spring Bean，
 * 由 {@link ClassifyService} 按配置 campus.ai.classify.provider（clip | mock | baidu，默认 clip）与 {@link #name()} 匹配选择。
 * <p>约定：返回结果中的分类应是数据库里存在且启用的子分类；ClassifyService 会再次校验并补全名称/父分类，
 * 不存在的分类会被丢弃。实现不可用（{@link #isAvailable()} 为 false）或抛异常时，接口返回 source=NONE 的空结果。</p>
 */
public interface ImageClassifier {

    /** 实现名称，与 campus.ai.classify.provider 对应，如 clip / mock / baidu。 */
    String name();

    /** 是否可用（模型已加载、密钥已配置等）。 */
    boolean isAvailable();

    /**
     * @param imageBytes 已通过类型（jpg/png/webp）与大小（≤5MB）校验的图片字节
     * @param filename   原始文件名（可能为 null），mock 实现据此匹配关键词
     */
    ClassifyResult classify(byte[] imageBytes, String filename);
}
