package com.campus.trade.ai.clip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CLIP 零样本图片分类配置（前缀 campus.ai.clip）。全部有默认值，不配置也能启动；
 * 模型文件缺失时分类器不可用（isAvailable=false），不影响应用其他功能。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.ai.clip")
public class ClipProperties {
    /** 总开关；false 时不加载模型，isAvailable 恒为 false */
    private boolean enabled = true;
    /** 模型目录（相对路径以后端工作目录为基准，默认指向 scripts/ai/download_model.py 的输出目录） */
    private String modelDir = "../scripts/ai/models/clip-vit-base-patch32";
    /** 图像编码器 ONNX 文件名（相对 modelDir，也可写绝对路径）；可换成 vision_model_quantized.onnx 以节省内存 */
    private String visionModel = "vision_model.onnx";
    /** 分类文本向量 JSON（相对 modelDir，也可写绝对路径），由 scripts/ai/build_text_embeddings.py 生成 */
    private String embeddings = "category_embeddings.json";
    /** true 用提示词集成向量（embedding），false 用单提示词向量（singleEmbedding，仅消融用） */
    private boolean promptEnsemble = true;
    /** softmax 温度（CLIP 的 logit_scale，ViT-B/32 训练得到约 100） */
    private double logitScale = 100.0;
    /** 返回候选数 */
    private int topK = 3;
    /** 是否在应用启动完成后异步预加载（否则在第一次调用时懒加载） */
    private boolean preload = true;
    /** ONNX Runtime 单次推理使用的线程数 */
    private int intraOpThreads = 2;
    /** 同时进行的推理请求上限，超出时排队等待 */
    private int maxConcurrent = 2;
    /** 排队等待推理的最长毫秒数，超时抛异常（由调用方降级为空建议） */
    private long acquireTimeoutMs = 5000;
    /** 加载失败后的重试间隔（秒）：模型补下载后无需重启即可恢复 */
    private long retryIntervalSeconds = 300;
    /** 解码前允许的最大像素数（宽×高），防止解压炸弹 */
    private long maxPixels = 80_000_000L;
    /** 分类名称/启用状态从 category 表刷新的间隔（秒） */
    private long categoryRefreshSeconds = 60;
}
