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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 推荐模块集成测试（MySQL campus_trade_test + Redis db1）。
 * 在"交通出行"一级分类（下有自行车/电动车两个二级分类）下造一组受控商品和行为，跑一次离线任务后验证接口。
 * 测试结束删除本类插入的商品与行为。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecommendIntegrationTest extends BaseIntegrationTest {

    static final long CAT_TRAFFIC = 3000000000000000008L;
    static final long CAT_BIKE = 3000000000000000801L;
    static final long CAT_EBIKE = 3000000000000000802L;

    @Autowired
    RecommendOfflineService offline;
    @Autowired
    StringRedisTemplate redis;

    String sellerToken;
    long sellerId;
    /** r[0] 最热；r[0] 与 r[1] 被同一批用户共同交互（ItemCF 邻居） */
    long[] r = new long[8];
    long newGoods;
    long offShelf;
    String targetToken;
    final List<Long> createdBehaviorUsers = new ArrayList<>();

    @BeforeAll
    void setUp() throws Exception {
        sellerToken = newUserToken();
        sellerId = userIdOf(sellerToken);
        String[] titles = {"捷安特 山地车 ATX 830", "美利达 山地车 勇士", "捷安特 公路车 SCR", "折叠车 大行 P8",
                "雅迪 电动车 48V", "小牛 电动车 U+", "爱玛 电动车 续航60km", "凤凰 自行车 复古"};
        for (int i = 0; i < r.length; i++) {
            r[i] = insertGoods(sellerId, i >= 4 && i <= 6 ? CAT_EBIKE : CAT_BIKE, titles[i], "ON_SHELF");
        }
        offShelf = insertGoods(sellerId, CAT_BIKE, "捷安特 山地车 已下架", "OFF_SHELF");

        // 6 个"共同兴趣"用户：都交互 r0 与 r1（收藏/聊天），r0 额外更多浏览 → r0 最热、r0~r1 相似
        for (int u = 0; u < 6; u++) {
            long uid = userIdOf(newUserToken());
            createdBehaviorUsers.add(uid);
            behavior(uid, r[0], 1);
            behavior(uid, r[0], 1);
            behavior(uid, r[0], 2);
            behavior(uid, r[1], 1);
            behavior(uid, r[1], 3);
            behavior(uid, offShelf, 2); // 已下架商品不能出现在推荐结果中
        }
        // 另 2 个用户交互 r3、r4（与 r0 无关）
        for (int u = 0; u < 2; u++) {
            long uid = userIdOf(newUserToken());
            createdBehaviorUsers.add(uid);
            behavior(uid, r[3], 1);
            behavior(uid, r[4], 1);
        }
        // 目标用户：交互 r0, r2, r5, r6, r7（5 个商品 → 非冷启动）
        targetToken = newUserToken();
        long target = userIdOf(targetToken);
        createdBehaviorUsers.add(target);
        for (int i : new int[]{0, 2, 5, 6, 7}) {
            behavior(target, r[i], 1);
        }
        behavior(target, r[0], 2);
        // 新商品（无任何行为），标题与 r0 高度相似
        newGoods = insertGoods(sellerId, CAT_BIKE, "捷安特 山地车 ATX 860 九成新", "ON_SHELF");
        rebuild();
    }

    @AfterAll
    void cleanUp() {
        List<Long> goods = new ArrayList<>();
        for (long id : r) {
            goods.add(id);
        }
        goods.add(newGoods);
        goods.add(offShelf);
        for (Long id : goods) {
            jdbc.update("DELETE FROM user_behavior WHERE goods_id=?", id);
            jdbc.update("DELETE FROM favorite WHERE goods_id=?", id);
            jdbc.update("DELETE FROM goods WHERE id=?", id);
        }
    }

    private long insertGoods(long seller, long cat, String title, String status) {
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO goods(id,seller_id,category_id,title,description,price,original_price,`condition`,"
                        + "trade_method,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, seller, cat, title, "推荐测试 " + title, 500, 1000, "GOOD", "FACE", status);
        return id;
    }

    private void behavior(long uid, long goodsId, int type) {
        jdbc.update("INSERT INTO user_behavior(id,user_id,goods_id,behavior_type,create_time) VALUES(?,?,?,?,NOW())",
                IdWorker.getId(), uid, goodsId, type);
    }

    /** 启动时可能有一次离线任务持有锁，重试直到本次真正执行。 */
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

    private static void assertCards(JsonNode list) {
        for (JsonNode g : list) {
            assertEquals("ON_SHELF", g.get("status").asText(), g.toString());
            assertTrue(g.get("id").isTextual(), "雪花 ID 应序列化为字符串");
            assertTrue(g.get("seller").get("id").isTextual());
            assertNotNull(g.get("title"));
            assertNull(g.get("description"), "列表 VO 不含 description");
            // 推荐理由：两个字段非空、类型合法、文案 ≤ 30 字
            assertTrue(g.hasNonNull("reason") && !g.get("reason").asText().isBlank(), g.toString());
            assertTrue(g.hasNonNull("reasonType"), g.toString());
            assertTrue(Set.of("CF", "INTEREST", "HOT").contains(g.get("reasonType").asText()), g.toString());
            String reason = g.get("reason").asText();
            assertTrue(reason.codePointCount(0, reason.length()) <= 30, "reason 超过 30 字: " + reason);
        }
    }

    private static JsonNode byId(JsonNode list, long id) {
        for (JsonNode g : list) {
            if (Long.parseLong(g.get("id").asText()) == id) {
                return g;
            }
        }
        return fail("not found " + id + " in " + list);
    }

    @Test
    void modelWrittenToRedisDb1() {
        String ver = redis.opsForValue().get("rec:ver");
        assertNotNull(ver);
        Set<String> nb = redis.opsForZSet().reverseRange("rec:" + ver + ":sim:" + r[0], 0, -1);
        assertNotNull(nb);
        assertTrue(nb.contains(String.valueOf(r[1])), "r0 的邻居应包含 r1: " + nb);
        Long ttl = redis.getExpire("rec:" + ver + ":hot");
        assertTrue(ttl != null && ttl > 0, "版本 key 应带 TTL");
    }

    @Test
    void guestGetsHotOnShelfOnly() throws Exception {
        JsonNode d = ok(get("/api/v1/recommend/home").param("size", "5"));
        assertTrue(d.isArray() && d.size() > 0 && d.size() <= 5);
        assertCards(d);
        List<Long> ids = ids(d);
        assertEquals(r[0], ids.get(0), "r0 行为最多，应排在游客热门第一");
        // HOT 场景：游客全部为热度理由
        for (JsonNode g : d) {
            assertEquals("HOT", g.get("reasonType").asText());
            assertEquals("本周热门", g.get("reason").asText());
        }
        assertFalse(ids.contains(offShelf));
        // 默认 size=10
        assertTrue(ok(get("/api/v1/recommend/home")).size() <= 10);
    }

    @Test
    void personalizedHomeUsesItemCfAndFiltersSeen() throws Exception {
        JsonNode d = ok(auth(get("/api/v1/recommend/home").param("size", "10"), targetToken));
        assertCards(d);
        List<Long> ids = ids(d);
        assertTrue(ids.contains(r[1]), "r1 是 r0 的 ItemCF 邻居，应被推荐: " + ids);
        // CF 场景：r1 由种子 r0 推出，目标用户对 r0 最高的行为是收藏
        JsonNode r1 = byId(d, r[1]);
        assertEquals("CF", r1.get("reasonType").asText());
        assertEquals("你收藏过类似的捷安特 山地车 ATX 830", r1.get("reason").asText());
        for (int i : new int[]{0, 2, 5, 6, 7}) {
            assertFalse(ids.contains(r[i]), "已交互商品不应再推荐");
        }
        assertFalse(ids.contains(offShelf));
        assertEquals(ids.size(), Set.copyOf(ids).size(), "结果不应重复");
        // favorited 字段：目标用户收藏 r0（行为表），但 favorite 表无记录 → false；字段必须存在
        assertTrue(d.get(0).has("favorited"));
    }

    @Test
    void ownGoodsNeverRecommended() throws Exception {
        for (String path : List.of("/api/v1/recommend/home", "/api/v1/recommend/similar/" + r[2])) {
            JsonNode d = ok(auth(get(path).param("size", "20"), sellerToken));
            for (JsonNode g : d) {
                assertNotEquals(String.valueOf(sellerId), g.get("seller").get("id").asText(), path);
            }
        }
    }

    @Test
    void coldUserWithParentCategoryInterest() throws Exception {
        String token = newUserToken();
        // 注册兴趣只选一级分类"交通出行"，商品在二级分类上：热度榜按祖先分类展开后应能命中
        ok(json(auth(put("/api/v1/users/me/interests"), token), Map.of("categoryIds", List.of(String.valueOf(CAT_TRAFFIC)))));
        // 再直接写一条停用分类兴趣（接口会拒绝，模拟历史脏数据）：推荐侧应忽略而不是报错
        long uid = userIdOf(token);
        jdbc.update("INSERT INTO user_interest(id,user_id,category_id) VALUES(?,?,?)", IdWorker.getId(), uid, CAT_DISABLED);
        JsonNode d = ok(auth(get("/api/v1/recommend/home").param("size", "4"), token));
        assertCards(d);
        assertTrue(d.size() > 0);
        long first = Long.parseLong(d.get(0).get("categoryId").asText());
        assertTrue(first == CAT_BIKE || first == CAT_EBIKE, "冷启动首条应来自兴趣分类子树，实际 " + first);
        assertEquals(r[0], Long.parseLong(d.get(0).get("id").asText()));
        // INTEREST 场景：二级分类商品命中一级兴趣，用用户选的一级分类名
        assertEquals("INTEREST", d.get(0).get("reasonType").asText());
        assertEquals("你关注了交通出行", d.get(0).get("reason").asText());
    }

    @Test
    void similarUsesItemCfThenContent() throws Exception {
        JsonNode d = ok(get("/api/v1/recommend/similar/" + r[0]).param("size", "5"));
        assertCards(d);
        List<Long> ids = ids(d);
        assertEquals(r[1], ids.get(0), "ItemCF 最相似的是 r1: " + ids);
        assertFalse(ids.contains(r[0]), "不推荐自身");
        assertEquals("CF", d.get(0).get("reasonType").asText());
        assertEquals("和当前商品相似", d.get(0).get("reason").asText());

        // 新商品没有行为：靠同一级分类内标题 TF-IDF 相似补齐，最像的是 r0
        JsonNode n = ok(get("/api/v1/recommend/similar/" + newGoods).param("size", "3"));
        assertEquals(r[0], ids(n).get(0), n.toString());
    }

    @Test
    void notFoundAndValidation() throws Exception {
        assertEquals(20001, perform(get("/api/v1/recommend/similar/1"), 200).get("code").asInt());
        assertEquals(400, perform(get("/api/v1/recommend/home").param("size", "0"), 200).get("code").asInt());
        assertEquals(400, perform(get("/api/v1/recommend/home").param("size", "51"), 200).get("code").asInt());
        assertEquals(400, perform(get("/api/v1/recommend/similar/" + r[0]).param("size", "999"), 200).get("code").asInt());
    }
}
