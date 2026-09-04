package top.daoha.domain.conversation.service;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import top.daoha.domain.conversation.adapter.repository.IConversationRepository;
import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;

import java.util.List;

/**
 * 会话领域服务实现。
 */
@Service
public class ConversationService implements IConversationService {

    private final IConversationRepository conversationRepository;

    public ConversationService(IConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Override
    public List<ConversationEntity> queryConversationList(String mode) {
        return conversationRepository.queryConversationList(mode);
    }

    @Override
    public ConversationEntity createConversation(ConversationEntity conversation) {
        conversationRepository.saveConversation(conversation);
        return conversationRepository.queryConversationById(conversation.getId());
    }

    @Override
    public boolean renameConversation(String conversationId, String title) {
        return conversationRepository.renameConversation(conversationId, title);
    }

    @Override
    public boolean deleteConversation(String conversationId) {
        return conversationRepository.deleteConversation(conversationId);
    }

    @Override
    public boolean conversationExists(String conversationId) {
        return conversationRepository.conversationExists(conversationId);
    }

    @Override
    public List<ConversationMessageEntity> queryMessageList(String conversationId) {
        return conversationRepository.queryMessageList(conversationId);
    }

    @Override
    public boolean saveMessage(ConversationMessageEntity message) {
        if (isAgentAnswer(message) && !retainFinalAgentSummary(message)) {
            return false;
        }
        conversationRepository.saveMessage(message);
        return true;
    }

    private boolean isAgentAnswer(ConversationMessageEntity message) {
        return "assistant".equals(message.getRole()) && "agent".equals(message.getMessageType());
    }

    /**
     * Agent 的分析、执行和监督事件仅用于前端实时展示，不进入历史库。
     * 收到 completed=true 的最终 summary 后，只保留这一条最终回答。
     */
    private boolean retainFinalAgentSummary(ConversationMessageEntity message) {
        List<AutoAgentExecuteResultEntity> events;
        try {
            events = JSON.parseArray(message.getContent(), AutoAgentExecuteResultEntity.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Agent 总结数据格式不正确", e);
        }
        if (events == null || events.isEmpty()) return false;

        AutoAgentExecuteResultEntity finalSummary = null;
        for (AutoAgentExecuteResultEntity event : events) {
            if (event != null
                    && "summary".equals(event.getType())
                    && Boolean.TRUE.equals(event.getCompleted())) {
                finalSummary = event;
            }
        }
        if (finalSummary == null) return false;

        message.setContent(JSON.toJSONString(List.of(finalSummary)));
        message.setStatus("completed");
        return true;
    }
}
