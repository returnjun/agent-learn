package top.daoha.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @ClassName : AutoAgentRequestDTO
 * @Description :
 * @github:
 * @Author : 24209
 * @Date: 2026/9/3  19:48
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoAgentRequestDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * AI智能体ID
     */
    private String aiAgentId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 最大执行步数
     */
    private Integer maxStep;
}
