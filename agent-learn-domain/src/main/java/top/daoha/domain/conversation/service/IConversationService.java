package top.daoha.domain.conversation.service;

import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;

import java.util.List;

/**
 * 会话领域服务。
 */
public interface IConversationService {

    List<ConversationEntity> queryConversationList(String mode);

    ConversationEntity createConversation(ConversationEntity conversation);

    boolean renameConversation(String conversationId, String title);

    boolean deleteConversation(String conversationId);

    boolean conversationExists(String conversationId);

    List<ConversationMessageEntity> queryMessageList(String conversationId);

    boolean saveMessage(ConversationMessageEntity message);
}
