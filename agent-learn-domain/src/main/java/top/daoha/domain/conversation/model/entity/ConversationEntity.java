package top.daoha.domain.conversation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEntity {

    private String id;
    private String mode;
    private String title;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
