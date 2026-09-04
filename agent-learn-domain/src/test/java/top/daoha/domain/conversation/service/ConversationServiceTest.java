package top.daoha.domain.conversation.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import top.daoha.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import top.daoha.domain.conversation.adapter.repository.IConversationRepository;
import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConversationServiceTest {

    @Test
    public void shouldIgnoreAgentIntermediateEvents() {
        RecordingRepository repository = new RecordingRepository();
        ConversationService service = new ConversationService(repository);
        ConversationMessageEntity message = agentMessage(JSON.toJSONString(List.of(
                AutoAgentExecuteResultEntity.createAnalysisResult(1, "分析过程", "session-1")
        )));

        assertFalse(service.saveMessage(message));
        assertNull(repository.savedMessage);
    }

    @Test
    public void shouldPersistOnlyCompletedAgentSummary() {
        RecordingRepository repository = new RecordingRepository();
        ConversationService service = new ConversationService(repository);
        AutoAgentExecuteResultEntity partialSummary = AutoAgentExecuteResultEntity.createSummarySubResult(
                "summary_overview", "分段总结", "session-1");
        AutoAgentExecuteResultEntity finalSummary = AutoAgentExecuteResultEntity.createSummaryResult(
                "最终总结", "session-1");
        ConversationMessageEntity message = agentMessage(JSON.toJSONString(List.of(
                AutoAgentExecuteResultEntity.createAnalysisResult(1, "分析过程", "session-1"),
                partialSummary,
                finalSummary,
                AutoAgentExecuteResultEntity.createCompleteResult("session-1")
        )));

        assertTrue(service.saveMessage(message));
        List<AutoAgentExecuteResultEntity> savedEvents = JSON.parseArray(
                repository.savedMessage.getContent(), AutoAgentExecuteResultEntity.class);
        assertEquals(1, savedEvents.size());
        assertEquals("summary", savedEvents.get(0).getType());
        assertEquals("最终总结", savedEvents.get(0).getContent());
        assertTrue(savedEvents.get(0).getCompleted());
        assertEquals("completed", repository.savedMessage.getStatus());
    }

    private ConversationMessageEntity agentMessage(String content) {
        return ConversationMessageEntity.builder()
                .id("message-1")
                .conversationId("conversation-1")
                .role("assistant")
                .messageType("agent")
                .content(content)
                .status("generating")
                .sortOrder(2L)
                .build();
    }

    private static class RecordingRepository implements IConversationRepository {

        private ConversationMessageEntity savedMessage;

        @Override
        public List<ConversationEntity> queryConversationList(String mode) {
            return List.of();
        }

        @Override
        public ConversationEntity queryConversationById(String conversationId) {
            return null;
        }

        @Override
        public void saveConversation(ConversationEntity conversation) {
        }

        @Override
        public boolean renameConversation(String conversationId, String title) {
            return false;
        }

        @Override
        public boolean deleteConversation(String conversationId) {
            return false;
        }

        @Override
        public boolean conversationExists(String conversationId) {
            return false;
        }

        @Override
        public List<ConversationMessageEntity> queryMessageList(String conversationId) {
            return List.of();
        }

        @Override
        public void saveMessage(ConversationMessageEntity message) {
            this.savedMessage = message;
        }
    }
}
