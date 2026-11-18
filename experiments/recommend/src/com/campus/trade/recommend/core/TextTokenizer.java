package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量中文标题分词：中文连续片段切成字二元组（bigram），英文/数字按整词，去掉常见无信息词。
 * 不依赖 jieba/HanLP 等第三方库；如后端已引入 HanLP，可替换本类实现。
 */
public final class TextTokenizer {

    private static final Set<String> STOP = Set.of(
            "全新", "九成", "成新", "八成", "自用", "闲置", "二手", "转让", "便宜", "包邮", "低价", "急出",
            "毕业", "业出", "价出", "出售");

    private TextTokenizer() {
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String s = text.toLowerCase(Locale.ROOT);
        StringBuilder cjk = new StringBuilder();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                flushWord(word, tokens);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjk, tokens);
                word.append(ch);
            } else {
                flushWord(word, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushWord(word, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushWord(StringBuilder sb, List<String> out) {
        if (sb.length() > 0) {
            out.add(sb.toString());
            sb.setLength(0);
        }
    }

    private static void flushCjk(StringBuilder sb, List<String> out) {
        if (sb.length() == 1) {
            out.add(sb.toString());
        } else {
            for (int i = 0; i + 1 < sb.length(); i++) {
                String bg = sb.substring(i, i + 2);
                if (!STOP.contains(bg)) {
                    out.add(bg);
                }
            }
        }
        sb.setLength(0);
    }
}
