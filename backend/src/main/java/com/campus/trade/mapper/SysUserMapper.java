package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按日统计新注册用户，后台统计用。返回 d(yyyy-MM-dd) / c。 */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, COUNT(*) AS c FROM sys_user "
            + "WHERE create_time >= #{since} GROUP BY d")
    java.util.List<java.util.Map<String, Object>> countByDay(@Param("since") java.time.LocalDateTime since);
}
