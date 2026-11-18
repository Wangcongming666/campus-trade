package com.campus.trade.recommend.service;

import com.campus.trade.common.Constants;

/**
 * 推荐理由（写入 GoodsVO.reason / reasonType）。文案全部在这里生成，整句不超过 {@link #MAX_LEN} 个字符（按码点计），
 * 标题过长时截断并加"…"。
 * <pre>
 * CF       你{买过|咨询过|收藏过|浏览过}类似的{标题}     ItemCF：种子 = 对该候选得分贡献最大的已交互商品
 *          和你{买过|咨询过|收藏过|浏览过}的{标题}相似   内容相似（新商品）：种子 = 贡献最大的画像商品
 *          和当前商品相似                                相似商品接口（种子为当前商品）
 *          和你兴趣相似的同学也看过                      UserCF（无种子商品；目前线上未使用）
 *          和你看过的商品相似                            种子商品已删除/查不到标题时的兜底
 * INTEREST 你关注了{分类名}                              冷启动命中用户注册时选的兴趣分类（用用户选的那个分类名）
 *          你常逛{分类名}                                冷启动命中用户少量行为所在的分类（非注册选择）
 * HOT      本周热门                                      游客、冷启动/候选不足时的热度补齐
 *          最新发布                                      热度榜为空（模型未生成）时按最新上架补齐
 * </pre>
 * 行为取用户对种子商品权重最高的一种：下单 &gt; 聊天 &gt; 收藏 &gt; 浏览。
 */
public record RecommendReason(String type, String text) {

    public static final int MAX_LEN = 30;
    public static final String CF = "CF";
    public static final String INTEREST = "INTEREST";
    public static final String HOT = "HOT";

    public static final RecommendReason HOT_WEEK = new RecommendReason(HOT, "本周热门");
    public static final RecommendReason LATEST = new RecommendReason(HOT, "最新发布");
    public static final RecommendReason SIMILAR_TO_CURRENT = new RecommendReason(CF, "和当前商品相似");
    public static final RecommendReason SIMILAR_USERS = new RecommendReason(CF, "和你兴趣相似的同学也看过");
    public static final RecommendReason CF_FALLBACK = new RecommendReason(CF, "和你看过的商品相似");
    /** 首页最后一层兜底：只浏览过、仍在售的商品 */
    public static final RecommendReason VIEWED_STILL_ON_SHELF = new RecommendReason(CF, "你最近浏览过，还在售");

    /** ItemCF："你收藏过类似的 iPad…" */
    public static RecommendReason itemCf(Integer behaviorType, String seedTitle) {
        return withTitle(CF, "你" + verb(behaviorType) + "类似的", seedTitle, "");
    }

    /** 内容相似："和你浏览过的高等数学相似" */
    public static RecommendReason content(Integer behaviorType, String seedTitle) {
        return withTitle(CF, "和你" + verb(behaviorType) + "的", seedTitle, "相似");
    }

    /** 冷启动兴趣分类：注册时选择的用"你关注了"，仅由行为推断的用"你常逛"。 */
    public static RecommendReason interest(String categoryName, boolean declared) {
        if (categoryName == null || categoryName.isBlank()) {
            return HOT_WEEK;
        }
        return new RecommendReason(INTEREST, fit(declared ? "你关注了" : "你常逛", categoryName, ""));
    }

    static String verb(Integer behaviorType) {
        if (behaviorType == null) {
            return "浏览过";
        }
        return switch (behaviorType) {
            case Constants.BehaviorType.ORDER -> "买过";
            case Constants.BehaviorType.CHAT -> "咨询过";
            case Constants.BehaviorType.FAVORITE -> "收藏过";
            default -> "浏览过";
        };
    }

    private static RecommendReason withTitle(String type, String prefix, String title, String suffix) {
        if (title == null || title.isBlank()) {
            return CF_FALLBACK;
        }
        return new RecommendReason(type, fit(prefix, title, suffix));
    }

    /** prefix + 截断后的 middle + suffix，整句 ≤ MAX_LEN 个码点。 */
    static String fit(String prefix, String middle, String suffix) {
        String m = middle.strip().replaceAll("\\s+", " ");
        int budget = MAX_LEN - length(prefix) - length(suffix);
        if (length(m) > budget) {
            int keep = Math.max(0, budget - 1);
            m = m.substring(0, m.offsetByCodePoints(0, keep)).stripTrailing() + "…";
        }
        return prefix + m + suffix;
    }

    public static int length(String s) {
        return s.codePointCount(0, s.length());
    }
}
