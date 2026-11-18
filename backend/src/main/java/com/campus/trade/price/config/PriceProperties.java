package com.campus.trade.price.config;

import com.campus.trade.price.core.ConditionFactors;
import com.campus.trade.price.core.PriceSuggestConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 价格建议参数，前缀 campus.price，全部有默认值（不配置即可运行；需要调整时在 application.yml 追加）。
 */
@Data
@ConfigurationProperties(prefix = "campus.price")
public class PriceProperties {

    /** 成色系数先验 f₀(c)：同一商品在成色 c 下的价格 ≈ 原价 × f₀(c) */
    private Map<String, Double> conditionFactors = new LinkedHashMap<>(ConditionFactors.defaults().asMap());
    /** 是否用同类带原价样本校准成色系数（向先验收缩） */
    private boolean estimateFactors = true;
    /** 成色系数收缩的先验强度 m（相当于 m 个虚拟样本） */
    private double factorPrior = 10;
    /** 已成交样本权重 */
    private double soldWeight = 1.0;
    /** 在售挂牌样本权重 */
    private double listedWeight = 0.4;
    /** 标题相似加权 γ：w ← w·(1+γ·cos) */
    private double titleGamma = 2.0;
    /** IQR 去异常倍数 */
    private double iqrK = 1.5;
    /** 样本少于该值时主要依赖原价折算 */
    private int minSamples = 5;
    /** 样本数平滑常数 κ：λ_n = n/(n+κ) */
    private double blendKappa = 5;
    /** 原价折算价的默认区间半宽 */
    private double anchorBand = 0.15;
    /** 每次最多读取的同类样本数（按更新时间倒序） */
    private int sampleLimit = 2000;
    /** 分类整体中位数兜底时最多读取的价格数 */
    private int medianLimit = 5000;

    public PriceSuggestConfig toConfig() {
        Map<String, Double> f = new LinkedHashMap<>(ConditionFactors.defaults().asMap());
        if (conditionFactors != null) {
            f.putAll(conditionFactors);
        }
        return new PriceSuggestConfig(new ConditionFactors(f), soldWeight, listedWeight, titleGamma, iqrK, minSamples,
                blendKappa, anchorBand, estimateFactors, factorPrior, true, true, true, true, true);
    }
}
