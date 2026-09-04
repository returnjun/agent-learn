package top.daoha.infrastructure.adapter.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.daoha.domain.conversation.adapter.repository.IConversationRepository;
import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;
import top.daoha.infrastructure.dao.IAiConversationDao;
import top.daoha.infrastructure.dao.IAiConversationMessageDao;
import top.daoha.infrastructure.dao.po.AiConversation;
import top.daoha.infrastructure.dao.po.AiConversationMessage;

import java.util.List;

/**
 * 会话仓储实现。
 */
@Repository
public class ConversationRepository implements IConversationRepository {

    private final IAiConversationDao conversationDao;
    private final IAiConversationMessageDao conversationMessageDao;

    public ConversationRepository(IAiConversationDao conversationDao,
                                  IAiConversationMessageDao conversationMessageDao) {
        this.conversationDao = conversationDao;
        this.conversationMessageDao = conversationMessageDao;
    }

    @Override
    public List<ConversationEntity> queryConversationList(String mode) {
        return conversationDao.queryList(mode).stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public ConversationEntity queryConversationById(String conversationId) {
        return toEntity(conversationDao.queryById(conversationId));
    }

    @Override
    public void saveConversation(ConversationEntity conversation) {
        conversationDao.insertOrUpdate(AiConversation.builder()
                .id(conversation.getId())
                .mode(conversation.getMode())
                .title(conversation.getTitle())
                .build());
    }

    @Override
    public boolean renameConversation(String conversationId, String title) {
        return conversationDao.updateTitle(conversationId, title) > 0;
    }

    @Override
    public boolean deleteConversation(String conversationId) {
        return conversationDao.deleteById(conversationId) > 0;
    }

    @Override
    public boolean conversationExists(String conversationId) {
        return conversationDao.countById(conversationId) > 0;
    }

    @Override
    public List<ConversationMessageEntity> queryMessageList(String conversationId) {
        return conversationMessageDao.queryByConversationId(conversationId).stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    @Transactional
    public void saveMessage(ConversationMessageEntity message) {
        conversationMessageDao.insertOrUpdate(AiConversationMessage.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .status(message.getStatus())
                .sortOrder(message.getSortOrder())
                .build());
        conversationDao.updateTime(message.getConversationId());
    }

    private ConversationEntity toEntity(AiConversation conversation) {
        if (conversation == null) return null;
        return ConversationEntity.builder()
                .id(conversation.getId())
                .mode(conversation.getMode())
                .title(conversation.getTitle())
                .createdTime(conversation.getCreatedTime())
                .updatedTime(conversation.getUpdatedTime())
                .build();
    }

    private ConversationMessageEntity toEntity(AiConversationMessage message) {
        return ConversationMessageEntity.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .status(message.getStatus())
                .sortOrder(message.getSortOrder())
                .createdTime(message.getCreatedTime())
                .updatedTime(message.getUpdatedTime())
                .build();
    }
}
