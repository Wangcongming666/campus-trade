package com.campus.trade.price.service;

import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.Category;
import com.campus.trade.price.config.PriceProperties;
import com.campus.trade.price.core.PriceQuery;
import com.campus.trade.price.core.PriceSample;
import com.campus.trade.price.core.PriceSuggester;
import com.campus.trade.price.core.PriceSuggestion;
import com.campus.trade.price.core.WeightedStats;
import com.campus.trade.price.dto.PriceSuggestDTO;
import com.campus.trade.price.mapper.PriceSampleMapper;
import com.campus.trade.price.vo.PriceSuggestVO;
import com.campus.trade.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 价格建议：取同分类（含子分类）的已售/在售样本，交给纯 Java 的 {@link PriceSuggester} 计算，结果保留两位小数。
 * 无原价且无样本时以分类整体价格中位数兜底（本分类为空再看父分类）。
 */
@Service
@RequiredArgsConstructor
public class PriceSuggestService {

    /** 80000–80999 推荐与价格建议（规范第 5 节错误码分段）；ErrorCode 枚举属 common 包，此处不改动它。 */

    private final PriceSampleMapper sampleMapper;
    private final CategoryService categoryService;
    private final PriceProperties props;

    public PriceSuggestVO suggest(PriceSuggestDTO dto) {
        Category cat = categoryService.getEnabled(dto.getCategoryId());
        if (cat == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Set<Long> catIds = categoryService.selfAndDescendantIds(cat.getId());
        List<PriceSample> samples = new ArrayList<>();
        for (PriceSampleRow r : sampleMapper.selectSamples(catIds, props.getSampleLimit())) {
            if (r.getPrice() == null || r.getPrice().signum() <= 0) {
                continue;
            }
            samples.add(new PriceSample(r.getGoodsId(), r.getPrice().doubleValue(), r.getCondition(),
                    r.getOriginalPrice() == null ? null : r.getOriginalPrice().doubleValue(), r.getTitle(),
                    Constants.GoodsStatus.SOLD.equals(r.getStatus())));
        }
        Double orig = dto.getOriginalPrice() == null ? null : dto.getOriginalPrice().doubleValue();
        Double catMedian = samples.isEmpty() && orig == null ? categoryMedian(cat) : null;

        PriceSuggestion s = new PriceSuggester(props.toConfig())
                .suggest(new PriceQuery(dto.getCondition(), orig, dto.getTitle()), samples, catMedian);
        if (s.basis() == PriceSuggestion.Basis.NONE) {
            throw new BizException(ErrorCode.PRICE_NO_DATA);
        }
        BigDecimal p = money(s.suggested());
        BigDecimal lo = money(s.min());
        BigDecimal hi = money(s.max());
        if (dto.getOriginalPrice() != null) { // 取整后再保证不超过原价
            BigDecimal o = dto.getOriginalPrice().setScale(2, RoundingMode.DOWN);
            p = p.min(o);
            hi = hi.min(o);
        }
        lo = lo.min(p);
        hi = hi.max(p);
        return new PriceSuggestVO(p, lo, hi, s.sampleCount(), s.reason());
    }

    /** 本分类（含子分类）全部价格的中位数；为空则看父分类；都没有返回 null。 */
    private Double categoryMedian(Category cat) {
        List<Long> chain = new ArrayList<>();
        chain.add(cat.getId());
        if (cat.getParentId() != null) {
            chain.add(cat.getParentId());
        }
        for (Long c : chain) {
            List<Double> prices = sampleMapper.selectCategoryPrices(categoryService.selfAndDescendantIds(c), props.getMedianLimit())
                    .stream().filter(Objects::nonNull).filter(b -> b.signum() > 0).map(BigDecimal::doubleValue).toList();
            if (!prices.isEmpty()) {
                return WeightedStats.median(prices);
            }
        }
        return null;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(Math.max(0, v)).setScale(2, RoundingMode.HALF_UP);
    }
}
