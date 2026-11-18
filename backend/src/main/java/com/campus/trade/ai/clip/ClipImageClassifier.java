package com.campus.trade.ai.clip;

import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.ai.ImageClassifier;
import com.campus.trade.entity.Category;
import com.campus.trade.mapper.CategoryMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 基于 CLIP ViT-B/32 的零样本商品图片分类（离线、无需任何 API 密钥）。
 * <p>
 * 流程：图片解码与预处理（{@link ImagePreprocessor}）→ ONNX Runtime 运行图像编码器（{@link ClipVisionEncoder}）
 * → 与预先算好的分类文本向量做余弦相似度 → softmax(×100) → Top3（{@link ZeroShotScorer}）。
 * 文本向量由 scripts/ai/build_text_embeddings.py 离线生成，Java 端不做分词。
 * <p>
 * 降级：模型/向量文件缺失、ONNX Runtime 本地库加载失败等情况下 {@link #isAvailable()} 返回 false，
 * 不抛到启动流程，应用照常启动；失败后每隔 retry-interval-seconds 重试一次，补齐模型文件后无需重启。
 * <p>
 * 线程安全：模型单例，双重检查懒加载（provider=clip 时启动后异步预加载）；推理并发由信号量限流。
 */
@Slf4j
@Component
public class ClipImageClassifier implements ImageClassifier {

    public static final String NAME = "clip";
    public static final String SOURCE = "CLIP";

    private final ClipProperties props;
    private final ObjectProvider<CategoryMapper> categoryMapper;
    private final String provider;
    private final ImagePreprocessor preprocessor;
    private final Semaphore permits;

    private final Object loadLock = new Object();
    private volatile Model model;
    private volatile long lastFailureAt;
    private volatile String lastError;

    private volatile CategoryView categoryView;

    /** 已加载的模型与向量（不可变）。 */
    private record Model(ClipVisionEncoder encoder, CategoryEmbeddings embeddings) {
    }

    /** category 表快照：启用的叶子分类 id 及最新名称。 */
    private record CategoryView(long loadedAt, Set<Long> enabledLeafIds, Map<Long, String> names) {
    }

    public ClipImageClassifier(ClipProperties props, ObjectProvider<CategoryMapper> categoryMapper,
                               @Value("${campus.ai.classify.provider:clip}") String provider) {
        this.props = props;
        this.categoryMapper = categoryMapper;
        this.provider = provider;
        this.preprocessor = new ImagePreprocessor(props.getMaxPixels());
        this.permits = new Semaphore(Math.max(1, props.getMaxConcurrent()), true);
    }

    @Override
    public String name() {
        return NAME;
    }

    /** 启动完成后异步预加载（仅当当前实现被选中且开启 preload），不阻塞启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadOnStartup() {
        if (!props.isEnabled() || !props.isPreload() || !NAME.equalsIgnoreCase(provider)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                ensureLoaded();
            } catch (Throwable e) {
                log.warn("[ai-clip] 预加载异常: {}", e.toString());
            }
        }, "clip-preload");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled() && ensureLoaded() != null;
    }

    @Override
    public ClassifyResult classify(byte[] imageBytes, String filename) {
        long start = System.nanoTime();
        Model m = props.isEnabled() ? ensureLoaded() : null;
        if (m == null) {
            throw new IllegalStateException("CLIP 模型不可用: " + (props.isEnabled() ? lastError : "已关闭"));
        }
        float[] pixels = preprocessor.preprocess(imageBytes);
        float[] emb;
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(props.getAcquireTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("图片识别繁忙，请稍后再试");
            }
            emb = m.encoder().encode(pixels);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片识别被中断", e);
        } catch (ai.onnxruntime.OrtException e) {
            throw new IllegalStateException("图片识别推理失败: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
        CategoryView view = categoryView();
        List<CategoryEmbeddings.Entry> pool = new ArrayList<>();
        for (CategoryEmbeddings.Entry e : m.embeddings().entries()) {
            if (view == null || view.enabledLeafIds().contains(e.categoryId())) {
                pool.add(e);
            }
        }
        List<ZeroShotScorer.Scored> top = ZeroShotScorer.score(emb, pool, props.getLogitScale(), props.getTopK());
        List<ClassifyResult.Candidate> candidates = new ArrayList<>(top.size());
        for (ZeroShotScorer.Scored s : top) {
            CategoryEmbeddings.Entry e = s.category();
            String name = nameOf(view, e.categoryId(), e.name());
            // 与 CategoryResolver 约定一致：无子分类的一级分类，parentCategory 填自身
            Long parentId = e.parentId() == null ? e.categoryId() : e.parentId();
            String parentName = e.parentId() == null ? name : nameOf(view, e.parentId(), e.parentName());
            candidates.add(new ClassifyResult.Candidate(e.categoryId(), name, parentId, parentName,
                    round4(s.probability())));
        }
        long costMs = (System.nanoTime() - start) / 1_000_000;
        if (log.isDebugEnabled()) {
            log.debug("[ai-clip] file={} top={} cost={}ms", filename,
                    candidates.isEmpty() ? "-" : candidates.get(0).categoryName() + ":" + candidates.get(0).confidence(),
                    costMs);
        }
        if (candidates.isEmpty()) {
            return ClassifyResult.none();
        }
        return ClassifyResult.of(candidates, SOURCE);
    }

    /** 当前加载状态说明（供日志/排查）。 */
    public String status() {
        if (!props.isEnabled()) {
            return "disabled";
        }
        return model != null ? "ready" : (lastError == null ? "not-loaded" : "failed: " + lastError);
    }

    private static double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }

    private static String nameOf(CategoryView view, Long id, String fallback) {
        if (view == null || id == null) {
            return fallback;
        }
        return view.names().getOrDefault(id, fallback);
    }

    /** 双重检查懒加载；失败返回 null 并在重试间隔内直接返回 null。 */
    Model ensureLoaded() {
        Model m = model;
        if (m != null) {
            return m;
        }
        if (lastFailureAt > 0
                && System.currentTimeMillis() - lastFailureAt < props.getRetryIntervalSeconds() * 1000L) {
            return null;
        }
        synchronized (loadLock) {
            if (model != null) {
                return model;
            }
            if (lastFailureAt > 0
                    && System.currentTimeMillis() - lastFailureAt < props.getRetryIntervalSeconds() * 1000L) {
                return null;
            }
            long t0 = System.currentTimeMillis();
            ClipVisionEncoder encoder = null;
            try {
                Path dir = Path.of(props.getModelDir());
                Path vision = dir.resolve(props.getVisionModel());
                Path embFile = dir.resolve(props.getEmbeddings());
                if (!Files.isRegularFile(vision)) {
                    throw new IllegalStateException("模型文件不存在: " + vision.toAbsolutePath().normalize()
                            + "（请运行 scripts/ai/download_model.sh）");
                }
                if (!Files.isRegularFile(embFile)) {
                    throw new IllegalStateException("分类向量文件不存在: " + embFile.toAbsolutePath().normalize());
                }
                CategoryEmbeddings emb;
                try (InputStream in = Files.newInputStream(embFile)) {
                    emb = CategoryEmbeddings.load(in, props.isPromptEnsemble());
                }
                encoder = new ClipVisionEncoder(vision, props.getIntraOpThreads());
                // 预热并校验输出维度与文本向量一致
                float[] probe = encoder.encode(new float[3 * ImagePreprocessor.SIZE * ImagePreprocessor.SIZE]);
                if (probe.length != emb.dim()) {
                    throw new IllegalStateException("图像向量维度 " + probe.length + " 与文本向量维度 " + emb.dim() + " 不一致");
                }
                model = new Model(encoder, emb);
                lastError = null;
                lastFailureAt = 0;
                log.info("[ai-clip] 模型加载完成：{}，{} 个分类，promptEnsemble={}，耗时 {}ms",
                        vision.toAbsolutePath().normalize(), emb.entries().size(), props.isPromptEnsemble(),
                        System.currentTimeMillis() - t0);
                warnMissingCategories(emb);
                return model;
            } catch (Throwable e) {
                // 包括 UnsatisfiedLinkError（ONNX Runtime 本地库不可用）
                if (encoder != null) {
                    try {
                        encoder.close();
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                lastFailureAt = System.currentTimeMillis();
                log.warn("[ai-clip] 模型不可用，图片分类建议降级为空：{}", lastError);
                return null;
            }
        }
    }

    /** category 表快照，按 category-refresh-seconds 缓存；数据库不可用时返回 null（用向量文件里的名称、不过滤）。 */
    private CategoryView categoryView() {
        CategoryView v = categoryView;
        long now = System.currentTimeMillis();
        if (v != null && now - v.loadedAt() < props.getCategoryRefreshSeconds() * 1000L) {
            return v;
        }
        CategoryMapper mapper = categoryMapper.getIfAvailable();
        if (mapper == null) {
            return v;
        }
        try {
            List<Category> all = mapper.selectList(null);
            Map<Long, Category> byId = new HashMap<>();
            Set<Long> hasEnabledChild = new HashSet<>();
            for (Category c : all) {
                byId.put(c.getId(), c);
            }
            for (Category c : all) {
                if (c.getParentId() != null && isEnabled(c)) {
                    hasEnabledChild.add(c.getParentId());
                }
            }
            Set<Long> leaves = new HashSet<>();
            Map<Long, String> names = new HashMap<>();
            for (Category c : all) {
                names.put(c.getId(), c.getName());
                Category parent = c.getParentId() == null ? null : byId.get(c.getParentId());
                boolean enabled = isEnabled(c) && (parent == null || isEnabled(parent));
                if (enabled && !hasEnabledChild.contains(c.getId())) {
                    leaves.add(c.getId());
                }
            }
            CategoryView nv = new CategoryView(now, Set.copyOf(leaves), Map.copyOf(names));
            categoryView = nv;
            return nv;
        } catch (Exception e) {
            log.warn("[ai-clip] 读取分类表失败，沿用上次结果: {}", e.getMessage());
            return v;
        }
    }

    private static boolean isEnabled(Category c) {
        return c.getStatus() == null || "ENABLED".equalsIgnoreCase(c.getStatus());
    }

    private void warnMissingCategories(CategoryEmbeddings emb) {
        CategoryView v = categoryView();
        if (v == null) {
            return;
        }
        Set<Long> have = new HashSet<>();
        emb.entries().forEach(e -> have.add(e.categoryId()));
        for (Long id : v.enabledLeafIds()) {
            if (!have.contains(id)) {
                log.warn("[ai-clip] 分类 {}({}) 没有文本向量，不会被识别；请在 scripts/ai/category_prompts.json 补充提示词并重新生成",
                        id, v.names().get(id));
            }
        }
    }

    @PreDestroy
    public void close() {
        Model m = model;
        model = null;
        if (m != null) {
            try {
                m.encoder().close();
            } catch (Exception e) {
                log.debug("[ai-clip] 关闭模型异常: {}", e.getMessage());
            }
        }
    }
}
