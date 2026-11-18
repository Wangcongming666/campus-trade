package com.campus.trade.recommend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 推荐理由文案（纯 Java，不依赖 Spring/数据库）。 */
class RecommendReasonTest {

    @Test
    void behaviorVerbPriorityAndTemplates() {
        assertEquals("你收藏过类似的iPad Air 4", RecommendReason.itemCf(2, "iPad Air 4").text());
        assertEquals("你买过类似的iPad Air 4", RecommendReason.itemCf(4, "iPad Air 4").text());
        assertEquals("你咨询过类似的iPad Air 4", RecommendReason.itemCf(3, "iPad Air 4").text());
        assertEquals("和你浏览过的高等数学相似", RecommendReason.content(1, "高等数学").text());
        assertEquals("和你浏览过的高等数学相似", RecommendReason.content(null, "  高等数学 ").text());
        assertEquals("CF", RecommendReason.itemCf(1, "x").type());
        assertEquals("你关注了数码电子", RecommendReason.interest("数码电子", true).text());
        assertEquals("你常逛手机", RecommendReason.interest("手机", false).text());
        assertEquals("INTEREST", RecommendReason.interest("手机", true).type());
        assertEquals("HOT", RecommendReason.interest(null, true).type());
        assertEquals(RecommendReason.CF_FALLBACK, RecommendReason.itemCf(2, null));
        assertEquals("CF", RecommendReason.VIEWED_STILL_ON_SHELF.type());
        assertEquals("你最近浏览过，还在售", RecommendReason.VIEWED_STILL_ON_SHELF.text());
    }

    @Test
    void longTitleIsTruncatedTo30() {
        String title = "全新未拆封 Apple iPad Pro 12.9 英寸 M2 芯片 256G 深空灰 带妙控键盘和笔";
        String a = RecommendReason.itemCf(2, title).text();
        String b = RecommendReason.content(4, title).text();
        assertEquals(30, RecommendReason.length(a), a);
        assertEquals(30, RecommendReason.length(b), b);
        assertTrue(a.startsWith("你收藏过类似的全新未拆封") && a.endsWith("…"), a);
        assertTrue(b.startsWith("和你买过的全新未拆封") && b.endsWith("…相似"), b);
        // 中文/emoji 按码点计数
        String c = RecommendReason.itemCf(1, "😀".repeat(40)).text();
        assertEquals(30, RecommendReason.length(c));
        String cat = RecommendReason.interest("很".repeat(40), true).text();
        assertEquals(30, RecommendReason.length(cat));
    }
}
