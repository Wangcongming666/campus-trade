package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class FavoriteTest extends BaseIntegrationTest {

    private int favCount(long goodsId) {
        return jdbc.queryForObject("SELECT favorite_count FROM goods WHERE id=?", Integer.class, goodsId);
    }

    @Test
    void addRemoveFavoriteMaintainsCountAndBehavior() throws Exception {
        long goodsId = 4000000000000000008L;
        String token = newUserToken();
        long uid = userIdOf(token);
        int before = favCount(goodsId);

        JsonNode r = ok(auth(post("/api/v1/goods/" + goodsId + "/favorite"), token));
        assertTrue(r.get("favorited").asBoolean());
        assertEquals(before + 1, r.get("favoriteCount").asInt());
        // 幂等：重复收藏不重复计数
        ok(auth(post("/api/v1/goods/" + goodsId + "/favorite"), token));
        assertEquals(before + 1, favCount(goodsId));
        await(() -> behaviorCount(uid, goodsId, 2) == 1, "favorite behavior");
        Thread.sleep(300);
        assertEquals(1, behaviorCount(uid, goodsId, 2));

        assertTrue(ok(auth(get("/api/v1/goods/" + goodsId), token)).get("favorited").asBoolean());
        JsonNode list = ok(auth(get("/api/v1/goods").param("pageSize", "100"), token));
        boolean found = false;
        for (JsonNode g : list.get("records")) {
            if (g.get("id").asText().equals(String.valueOf(goodsId))) {
                assertTrue(g.get("favorited").asBoolean());
                found = true;
            }
        }
        assertTrue(found);

        JsonNode favs = ok(auth(get("/api/v1/users/me/favorites"), token));
        assertEquals(1, favs.get("total").asLong());
        assertEquals(String.valueOf(goodsId), favs.get("records").get(0).get("id").asText());

        JsonNode rm = ok(auth(delete("/api/v1/goods/" + goodsId + "/favorite"), token));
        assertFalse(rm.get("favorited").asBoolean());
        assertEquals(before, favCount(goodsId));
        ok(auth(delete("/api/v1/goods/" + goodsId + "/favorite"), token));
        assertEquals(before, favCount(goodsId), "取消收藏幂等");
        assertEquals(0, ok(auth(get("/api/v1/users/me/favorites"), token)).get("total").asLong());
        assertFalse(ok(auth(get("/api/v1/goods/" + goodsId), token)).get("favorited").asBoolean());
    }

    @Test
    void cannotFavoriteHiddenGoods() throws Exception {
        String token = newUserToken();
        assertEquals(20002, perform(auth(post("/api/v1/goods/" + GOODS_OFF + "/favorite"), token), 200).get("code").asInt());
        assertEquals(20001, perform(auth(post("/api/v1/goods/" + GOODS_PENDING + "/favorite"), token), 200).get("code").asInt());
        assertEquals(20001, perform(auth(post("/api/v1/goods/1/favorite"), token), 200).get("code").asInt());
    }
}
