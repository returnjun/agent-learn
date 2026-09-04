package top.daoha.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话表持久化对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {

    private String id;
    private String mode;
    private String title;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
