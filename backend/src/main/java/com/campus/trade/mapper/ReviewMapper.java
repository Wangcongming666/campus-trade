package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Review;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 某用户收到评价的平均分（无评价返回 null）。 */
    @org.apache.ibatis.annotations.Select("SELECT AVG(score) FROM review WHERE to_user_id = #{userId}")
    java.math.BigDecimal avgScore(@org.apache.ibatis.annotations.Param("userId") Long userId);
}
