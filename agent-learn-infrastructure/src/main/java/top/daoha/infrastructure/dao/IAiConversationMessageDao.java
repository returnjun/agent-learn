package top.daoha.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.daoha.infrastructure.dao.po.AiConversationMessage;

import java.util.List;

/**
 * 会话消息表 DAO。
 */
@Mapper
public interface IAiConversationMessageDao {

    List<AiConversationMessage> queryByConversationId(@Param("conversationId") String conversationId);

    int insertOrUpdate(AiConversationMessage message);
}
