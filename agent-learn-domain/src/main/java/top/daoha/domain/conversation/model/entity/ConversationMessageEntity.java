package top.daoha.domain.conversation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageEntity {

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String messageType;
    private String status;
    private Long sortOrder;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
