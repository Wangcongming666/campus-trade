package com.campus.trade.ai;

import java.util.*;

/**
 * 关键词 → 子分类名称映射（mock 按文件名匹配，baidu 按识别出的物体关键词匹配）。
 * 只给出分类名称，由调用方映射到数据库里存在且启用的分类。
 */
public final class CategoryKeywordMatcher {

    /** 子分类（或一级分类）名称 → 关键词（小写匹配） */
    private static final Map<String, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("手机", List.of("iphone", "phone", "huawei", "xiaomi", "redmi", "oppo", "vivo", "手机", "手环", "watch"));
        RULES.put("电脑平板", List.of("ipad", "tablet", "laptop", "notebook", "macbook", "computer", "keyboard",
                "mouse", "monitor", "电脑", "平板", "笔记本", "键盘", "鼠标", "显示器"));
        RULES.put("耳机音箱", List.of("airpods", "headphone", "headset", "earphone", "earbud", "speaker", "sony",
                "jbl", "耳机", "音箱", "音响"));
        RULES.put("考研考证", List.of("kaoyan", "exam", "cet", "考研", "考证", "真题", "四六级"));
        RULES.put("专业教材", List.of("textbook", "math", "calculus", "教材", "课本", "高数", "高等数学", "线性代数"));
        RULES.put("课外读物", List.of("novel", "book", "magazine", "小说", "书", "读物", "杂志"));
        RULES.put("宿舍电器", List.of("cooker", "kettle", "lamp", "fan", "hairdryer", "电饭煲", "台灯", "风扇",
                "吹风机", "电热水壶", "电器"));
        RULES.put("收纳日用", List.of("box", "storage", "hanger", "cup", "收纳", "衣架", "水杯", "日用"));
        RULES.put("电动车", List.of("ebike", "e-bike", "scooter", "电动车", "电瓶车", "滑板车"));
        RULES.put("自行车", List.of("bike", "bicycle", "cycling", "自行车", "单车", "山地车"));
        RULES.put("服饰鞋包", List.of("jacket", "coat", "shirt", "shoe", "sneaker", "bag", "dress", "衣", "鞋",
                "包", "羽绒服", "外套"));
        RULES.put("运动户外", List.of("racket", "badminton", "basketball", "football", "tennis", "yoga", "球拍",
                "羽毛球", "篮球", "足球", "运动", "瑜伽"));
        RULES.put("乐器文娱", List.of("guitar", "piano", "ukulele", "violin", "吉他", "钢琴", "尤克里里", "乐器"));
        RULES.put("美妆个护", List.of("lipstick", "makeup", "perfume", "cosmetic", "口红", "化妆", "香水", "护肤"));
    }

    private CategoryKeywordMatcher() {
    }

    /** 返回命中的分类名称及命中关键词数（按首次出现顺序）。 */
    public static LinkedHashMap<String, Integer> match(String text) {
        LinkedHashMap<String, Integer> hits = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        String t = text.toLowerCase(Locale.ROOT);
        RULES.forEach((cat, words) -> {
            int n = 0;
            for (String w : words) {
                if (t.contains(w)) {
                    n++;
                }
            }
            if (n > 0) {
                hits.put(cat, n);
            }
        });
        return hits;
    }

    /** 识别不出时的低置信度默认候选（常见二手品类）。 */
    public static List<String> fallbackNames() {
        return List.of("专业教材", "宿舍电器", "电脑平板");
    }
}
