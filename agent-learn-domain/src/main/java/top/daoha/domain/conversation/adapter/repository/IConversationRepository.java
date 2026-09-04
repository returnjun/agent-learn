package top.daoha.domain.conversation.adapter.repository;

import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;

import java.util.List;

/**
 * 会话仓储接口。
 */
public interface IConversationRepository {

    List<ConversationEntity> queryConversationList(String mode);

    ConversationEntity queryConversationById(String conversationId);

    void saveConversation(ConversationEntity conversation);

    boolean renameConversation(String conversationId, String title);

    boolean deleteConversation(String conversationId);

    boolean conversationExists(String conversationId);

    List<ConversationMessageEntity> queryMessageList(String conversationId);

    void saveMessage(ConversationMessageEntity message);
}
