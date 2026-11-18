package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trade.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    @Update("UPDATE goods SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrViewCount(@Param("id") Long id);

    @Update("UPDATE goods SET favorite_count = favorite_count + #{delta} "
            + "WHERE id = #{id} AND favorite_count + #{delta} >= 0")
    int incrFavoriteCount(@Param("id") Long id, @Param("delta") int delta);

    /** 条件状态切换（乐观并发控制）：仅当当前状态为 fromStatus 时更新，返回影响行数。phase 2 下单 ON_SHELF→RESERVED 复用。 */
    @Update("UPDATE goods SET status = #{toStatus}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{fromStatus} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    /** 按 ID 批量查询，包含已逻辑删除的商品（订单/会话展示历史快照用）。 */
    @Select({"<script>SELECT * FROM goods WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    java.util.List<Goods> selectByIdsIncludingDeleted(@Param("ids") java.util.Collection<Long> ids);

    /** 按日统计新增商品（不含已删除），用于后台统计。 */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, COUNT(*) AS c FROM goods "
            + "WHERE deleted = 0 AND create_time >= #{since} GROUP BY d")
    java.util.List<java.util.Map<String, Object>> countByDay(@Param("since") java.time.LocalDateTime since);

    /** 未删除商品按一级分类计数（子分类归入其父分类）。返回 cid / c。 */
    @Select("SELECT COALESCE(c.parent_id, c.id) AS cid, COUNT(*) AS c FROM goods g "
            + "JOIN category c ON g.category_id = c.id WHERE g.deleted = 0 GROUP BY cid")
    java.util.List<java.util.Map<String, Object>> countByTopCategory();

    /** 我的收藏（按收藏时间倒序，排除已删除商品）。 */
    @Select("SELECT g.* FROM favorite f JOIN goods g ON f.goods_id = g.id "
            + "WHERE f.user_id = #{userId} AND g.deleted = 0 ORDER BY f.create_time DESC, f.id DESC")
    IPage<Goods> selectFavoritePage(IPage<Goods> page, @Param("userId") Long userId);
}
