package top.daoha.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息表持久化对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationMessage {

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
