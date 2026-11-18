package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /** 新消息：更新摘要/时间，并给接收方未读数 +1（接收方为发起人则 initiator_unread，否则 peer_unread）。 */
    @Update("UPDATE chat_session SET last_content = #{lastContent}, last_time = #{lastTime}, "
            + "initiator_unread = initiator_unread + IF(initiator_id = #{toUserId}, 1, 0), "
            + "peer_unread = peer_unread + IF(peer_id = #{toUserId}, 1, 0), update_time = NOW() "
            + "WHERE id = #{id}")
    int onNewMessage(@Param("id") Long id, @Param("toUserId") Long toUserId,
                     @Param("lastContent") String lastContent, @Param("lastTime") LocalDateTime lastTime);

    /** 清零当前用户在该会话的未读数。 */
    @Update("UPDATE chat_session SET initiator_unread = IF(initiator_id = #{userId}, 0, initiator_unread), "
            + "peer_unread = IF(peer_id = #{userId}, 0, peer_unread) WHERE id = #{id}")
    int clearUnread(@Param("id") Long id, @Param("userId") Long userId);
}
