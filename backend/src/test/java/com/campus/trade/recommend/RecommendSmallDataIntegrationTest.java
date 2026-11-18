package com.campus.trade.recommend;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.trade.BaseIntegrationTest;
import com.campus.trade.recommend.service.RecommendOfflineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 复现"演示账号首页只返回 3 条最新发布"的小数据量场景（MySQL campus_trade_test + Redis db1）：
 * 新建一个一级分类（下挂 3 个二级分类），商品都挂在二级分类上；冷启动用户注册兴趣只选一级分类，
 * 且只对两件商品有行为（浏览+聊天、下单）——这两件恰好是该分类热度榜的全部内容，被"已交互"过滤后热度榜为空。
 * 修复后：兴趣通道从在售商品按分类子树取候选（INTEREST），有种子商品时给出内容相似 CF，并补满 size。
 * 另验证首页最后一层兜底：只浏览过、仍在售的商品排在最后（CF「你最近浏览过，还在售」）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecommendSmallDataIntegrationTest extends BaseIntegrationTest {

    @Autowired
    RecommendOfflineService offline;

    long catRoot;
    final long[] leaf = new long[3];
    long seller;
    /** x: 用户浏览+聊天；y: 用户下单；z: 与 x 标题相似、无行为；g: 兴趣分类下无行为的新商品；own: 用户自己发布 */
    long x, y, z, g, own;
    String coldToken;
    long coldUser;

    /** 兜底场景用户：v1/v2 只浏览（v2 更近），fav 浏览后收藏，off 只浏览但已下架 */
    long v1, v2, fav, off;
    String viewerToken;
    long viewer;

    final List<Long> goods = new ArrayList<>();

    @BeforeAll
    void setUp() throws Exception {
        catRoot = insertCategory(null, "影音乐器测试");
        leaf[0] = insertCategory(catRoot, "唱机测试");
        leaf[1] = insertCategory(catRoot, "耳机测试");
        leaf[2] = insertCategory(catRoot, "音箱测试");
        seller = userIdOf(newUserToken());
        x = insertGoods(seller, leaf[0], "铁三角 黑胶唱机 LP60X", "ON_SHELF");
        y = insertGoods(seller, leaf[1], "森海塞尔 HD600", "ON_SHELF");
        z = insertGoods(seller, leaf[0], "铁三角 黑胶唱机 LP60XBT 九成新", "ON_SHELF");
        g = insertGoods(seller, leaf[2], "漫步者 R1700BT", "ON_SHELF");

        coldToken = newUserToken();
        coldUser = userIdOf(coldToken);
        own = insertGoods(coldUser, leaf[0], "铁三角 黑胶唱机 LP60X 自用转让", "ON_SHELF");
        ok(json(auth(put("/api/v1/users/me/interests"), coldToken),
                Map.of("categoryIds", List.of(String.valueOf(catRoot)))));
        behavior(coldUser, x, 1, 0);
        behavior(coldUser, x, 3, 0);
        behavior(coldUser, y, 4, 0);

        viewerToken = newUserToken();
        viewer = userIdOf(viewerToken);
        v1 = insertGoods(seller, CAT_PHONE, "兜底测试 只浏览 A", "ON_SHELF");
        v2 = insertGoods(seller, CAT_PHONE, "兜底测试 只浏览 B", "ON_SHELF");
        fav = insertGoods(seller, CAT_PHONE, "兜底测试 浏览后收藏", "ON_SHELF");
        off = insertGoods(seller, CAT_PHONE, "兜底测试 已下架", "OFF_SHELF");
        behavior(viewer, v1, 1, 120);
        behavior(viewer, v2, 1, 10);
        behavior(viewer, fav, 1, 60);
        behavior(viewer, fav, 2, 50);
        behavior(viewer, off, 1, 5);
        rebuild();
    }

    @AfterAll
    void cleanUp() {
        for (Long id : goods) {
            jdbc.update("DELETE FROM user_behavior WHERE goods_id=?", id);
            jdbc.update("DELETE FROM favorite WHERE goods_id=?", id);
            jdbc.update("DELETE FROM goods WHERE id=?", id);
        }
        jdbc.update("DELETE FROM user_interest WHERE category_id=?", catRoot);
        for (long c : leaf) {
            jdbc.update("DELETE FROM category WHERE id=?", c);
        }
        jdbc.update("DELETE FROM category WHERE id=?", catRoot);
    }

    private long insertCategory(Long parent, String name) {
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO category(id,parent_id,name,sort,status) VALUES(?,?,?,?,?)", id, parent, name, 99, "ENABLED");
        return id;
    }

    private long insertGoods(long sellerId, long cat, String title, String status) {
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO goods(id,seller_id,category_id,title,description,price,original_price,`condition`,"
                        + "trade_method,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, sellerId, cat, title, "小数据推荐测试 " + title, 300, 800, "GOOD", "FACE", status);
        goods.add(id);
        return id;
    }

    private void behavior(long uid, long goodsId, int type, int minutesAgo) {
        jdbc.update("INSERT INTO user_behavior(id,user_id,goods_id,behavior_type,create_time) "
                        + "VALUES(?,?,?,?,DATE_SUB(NOW(), INTERVAL ? MINUTE))",
                IdWorker.getId(), uid, goodsId, type, minutesAgo);
    }

    private void rebuild() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (offline.rebuild()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("offline rebuild did not run");
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(Long.parseLong(n.get("id").asText())));
        return out;
    }

    private static void assertReasons(JsonNode list) {
        for (JsonNode n : list) {
            assertEquals("ON_SHELF", n.get("status").asText(), n.toString());
            assertTrue(Set.of("CF", "INTEREST", "HOT").contains(n.get("reasonType").asText()), n.toString());
            String r = n.get("reason").asText();
            assertTrue(!r.isBlank() && r.codePointCount(0, r.length()) <= 30, r);
        }
    }

    @Test
    void coldUserWithFewBehaviorsGetsCfInterestAndFullPage() throws Exception {
        JsonNode d = ok(auth(get("/api/v1/recommend/home").param("size", "6"), coldToken));
        assertReasons(d);
        List<Long> ids = ids(d);
        assertEquals(6, ids.size(), "在售商品足够时应补满 size: " + d);
        assertEquals(ids.size(), Set.copyOf(ids).size());
        // CF：x 没有 ItemCF 可用邻居（唯一共现商品 y 已交互），由内容相似给出 z，种子为聊天过的 x
        assertEquals(z, ids.get(0), d.toString());
        assertEquals("CF", d.get(0).get("reasonType").asText());
        assertEquals("和你咨询过的铁三角 黑胶唱机 LP60X相似", d.get(0).get("reason").asText());
        // INTEREST：一级兴趣，商品挂在二级分类，且没有任何行为（不在热度榜）
        assertEquals(g, ids.get(1), d.toString());
        assertEquals("INTEREST", d.get(1).get("reasonType").asText());
        assertEquals("你关注了影音乐器测试", d.get(1).get("reason").asText());
        // 已交互、本人发布的不推荐
        assertFalse(ids.contains(x));
        assertFalse(ids.contains(y));
        assertFalse(ids.contains(own));
    }

    @Test
    void viewedOnlyGoodsFillTheTailWhenEverythingElseIsUsedUp() throws Exception {
        Integer eligible = jdbc.queryForObject("SELECT COUNT(*) FROM goods WHERE status='ON_SHELF' AND deleted=0 "
                + "AND seller_id<>? AND id NOT IN (SELECT goods_id FROM user_behavior WHERE user_id=?)",
                Integer.class, viewer, viewer);
        assertNotNull(eligible);
        assumeTrue(eligible + 2 <= 50, "测试库在售商品过多，无法用尽其他通道");
        // 正常页：不足时才用兜底，size 够小时不出现只浏览过的商品
        List<Long> small = ids(ok(auth(get("/api/v1/recommend/home").param("size", "2"), viewerToken)));
        assertFalse(small.contains(v1) || small.contains(v2), small.toString());

        JsonNode d = ok(auth(get("/api/v1/recommend/home").param("size", "50"), viewerToken));
        assertReasons(d);
        List<Long> ids = ids(d);
        assertEquals(eligible + 2, ids.size(), d.toString());
        // 只浏览过的排在最后，按最近浏览倒序（v2 比 v1 近）
        assertEquals(v2, ids.get(ids.size() - 2));
        assertEquals(v1, ids.get(ids.size() - 1));
        for (JsonNode n : List.of(d.get(ids.size() - 2), d.get(ids.size() - 1))) {
            assertEquals("CF", n.get("reasonType").asText());
            assertEquals("你最近浏览过，还在售", n.get("reason").asText());
        }
        for (int i = 0; i < ids.size() - 2; i++) {
            assertNotEquals("你最近浏览过，还在售", d.get(i).get("reason").asText());
        }
        // 收藏过的永不重新推荐；已下架的不推荐
        assertFalse(ids.contains(fav));
        assertFalse(ids.contains(off));
    }
}
