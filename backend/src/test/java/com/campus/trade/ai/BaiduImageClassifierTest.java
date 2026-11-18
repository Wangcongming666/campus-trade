package com.campus.trade.ai;

import com.campus.trade.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 百度识别结果 → 现有子分类的映射（不发网络请求）。 */
class BaiduImageClassifierTest extends BaseIntegrationTest {

    @Autowired
    CategoryResolver resolver;

    @Test
    void mapsBaiduKeywordsToExistingCategories() throws Exception {
        BaiduImageClassifier c = new BaiduImageClassifier(resolver, new ObjectMapper(), "", "");
        assertFalse(c.isAvailable());
        String json = "[{\"keyword\":\"蓝牙耳机\",\"root\":\"商品-数码产品\",\"score\":0.83},"
                + "{\"keyword\":\"智能手机\",\"root\":\"商品-数码产品\",\"score\":0.41},"
                + "{\"keyword\":\"桌子\",\"root\":\"商品-家具\",\"score\":0.30}]";
        ClassifyResult r = c.fromKeywords(new ObjectMapper().readTree(json));
        assertEquals("BAIDU", r.source());
        assertEquals(3000000000000000103L, r.categoryId());
        assertEquals("耳机音箱", r.categoryName());
        assertEquals(0.83, r.confidence(), 1e-9);
        assertEquals(2, r.candidates().size());
        assertEquals("手机", r.candidates().get(1).categoryName());
        assertEquals("NONE", c.fromKeywords(new ObjectMapper().readTree("[{\"keyword\":\"天空\",\"score\":0.9}]")).source());
    }
}
