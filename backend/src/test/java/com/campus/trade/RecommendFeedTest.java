package com.campus.trade;

import com.campus.trade.service.RecommendFeedService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET /recommend/home 分页模式：页间不重复、顺序稳定、超范围为空；不传分页参数时保持数组返回（兼容）。 */
class RecommendFeedTest extends BaseIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    private static List<String> ids(JsonNode records) {
        List<String> out = new ArrayList<>();
        records.forEach(r -> out.add(r.get("id").asText()));
        return out;
    }

    @Test
    void guestPagesDoNotOverlap() throws Exception {
        JsonNode p1 = ok(get("/api/v1/recommend/home").param("pageNum", "1").param("pageSize", "4"));
        long total = p1.get("total").asLong();
        assertTrue(total > 4, "seed has more than 4 on-shelf goods, total=" + total);
        assertEquals(1, p1.get("pageNum").asInt());
        assertEquals(4, p1.get("pageSize").asInt());
        assertTrue(p1.get("hasMore").asBoolean());
        assertEquals((total + 3) / 4, p1.get("pages").asLong());
        assertTrue(redis.hasKey(RecommendFeedService.key(null)));

        Set<String> seen = new LinkedHashSet<>(ids(p1.get("records")));
        int pages = (int) p1.get("pages").asLong();
        JsonNode last = p1;
        for (int p = 2; p <= pages; p++) {
            last = ok(get("/api/v1/recommend/home").param("pageNum", String.valueOf(p)).param("pageSize", "4"));
            for (String id : ids(last.get("records"))) {
                assertTrue(seen.add(id), "duplicate across pages: " + id);
            }
            for (JsonNode r : last.get("records")) {
                assertFalse(r.get("reason").asText().isBlank());
                assertTrue(Set.of("CF", "INTEREST", "HOT").contains(r.get("reasonType").asText()));
            }
        }
        assertFalse(last.get("hasMore").asBoolean());
        // 超出范围：空列表
        JsonNode beyond = ok(get("/api/v1/recommend/home").param("pageNum", String.valueOf(pages + 5)).param("pageSize", "4"));
        assertEquals(0, beyond.get("records").size());
        assertEquals(total, beyond.get("total").asLong());
        assertFalse(beyond.get("hasMore").asBoolean());
    }

    @Test
    void userFeedIsStableAndCached() throws Exception {
        String user1 = token("user1", "123456");
        JsonNode p1 = ok(auth(get("/api/v1/recommend/home").param("pageNum", "1").param("pageSize", "3"), user1));
        assertTrue(redis.hasKey(RecommendFeedService.key(USER1_ID)));
        JsonNode p2 = ok(auth(get("/api/v1/recommend/home").param("pageNum", "2").param("pageSize", "3"), user1));
        JsonNode p2again = ok(auth(get("/api/v1/recommend/home").param("pageNum", "2").param("pageSize", "3"), user1));
        assertEquals(ids(p2.get("records")), ids(p2again.get("records")), "page 2 stable from cache");
        Set<String> a = new HashSet<>(ids(p1.get("records")));
        ids(p2.get("records")).forEach(id -> assertFalse(a.contains(id), "page overlap " + id));
        // 大页一次取全，等于各小页拼接（同一份缓存）
        JsonNode all = ok(auth(get("/api/v1/recommend/home").param("pageNum", "1").param("pageSize", "50"), user1));
        List<String> full = ids(all.get("records"));
        List<String> joined = new ArrayList<>(ids(p1.get("records")));
        joined.addAll(ids(p2.get("records")));
        assertEquals(full.subList(0, Math.min(full.size(), joined.size())), joined);
        // 不推荐自己发布的商品，理由齐全
        for (JsonNode r : all.get("records")) {
            assertNotEquals(String.valueOf(USER1_ID), r.get("seller").get("id").asText());
            assertTrue(r.hasNonNull("reasonType"));
        }
    }

    @Test
    void pageSizeIsCappedAndLegacyArrayKept() throws Exception {
        JsonNode capped = ok(get("/api/v1/recommend/home").param("pageNum", "1").param("pageSize", "100"));
        assertEquals(50, capped.get("pageSize").asInt());
        // 只传 pageNum：pageSize 取 size（默认 10）
        assertEquals(10, ok(get("/api/v1/recommend/home").param("pageNum", "1")).get("pageSize").asInt());
        assertEquals(5, ok(get("/api/v1/recommend/home").param("pageNum", "1").param("size", "5")).get("pageSize").asInt());
        // 兼容：不传 pageNum/pageSize 仍返回数组
        JsonNode legacy = ok(get("/api/v1/recommend/home").param("size", "3"));
        assertTrue(legacy.isArray());
        assertTrue(legacy.size() <= 3);
    }

    @Test
    void firstPageMatchesLegacyDefaultHome() throws Exception {
        String user1 = token("user1", "123456");
        JsonNode legacy = ok(auth(get("/api/v1/recommend/home").param("size", "10"), user1));
        JsonNode page = ok(auth(get("/api/v1/recommend/home").param("pageNum", "1").param("pageSize", "10"), user1));
        assertEquals(ids(legacy), ids(page.get("records")), "首屏与不分页的默认首页一致");
        for (int i = 0; i < legacy.size(); i++) {
            assertEquals(legacy.get(i).get("reason").asText(), page.get("records").get(i).get("reason").asText());
            assertEquals(legacy.get(i).get("reasonType").asText(), page.get("records").get(i).get("reasonType").asText());
        }
    }
}
