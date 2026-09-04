package top.daoha.trigger.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.daoha.api.response.Response;
import top.daoha.domain.conversation.model.entity.ConversationEntity;
import top.daoha.domain.conversation.model.entity.ConversationMessageEntity;
import top.daoha.domain.conversation.service.IConversationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private static final Set<String> MODES = Set.of("chat", "agent");
    private static final Set<String> ROLES = Set.of("user", "assistant");
    private static final Set<String> STATUSES = Set.of("generating", "completed", "failed", "interrupted");

    private final IConversationService conversationService;

    public ConversationController(IConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public Response<List<ConversationView>> queryConversations(
            @RequestParam(value = "mode", required = false) String mode) {
        try {
            String normalizedMode = normalizeMode(mode, false);
            List<ConversationView> conversations = conversationService
                    .queryConversationList(normalizedMode)
                    .stream()
                    .map(this::mapConversation)
                    .toList();
            return success(conversations);
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return failure("5000", "查询会话列表失败");
        }
    }

    @PostMapping
    public Response<ConversationView> createConversation(@RequestBody CreateConversationRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("会话信息不能为空");
            }
            validateId(request.id(), "会话 ID");
            String mode = normalizeMode(request.mode(), true);
            String title = normalizeTitle(request.title());
            ConversationEntity conversation = conversationService.createConversation(ConversationEntity.builder()
                    .id(request.id())
                    .mode(mode)
                    .title(title)
                    .build());
            return success(mapConversation(conversation));
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("创建会话失败, id: {}", request == null ? null : request.id(), e);
            return failure("5000", "创建会话失败");
        }
    }

    @PatchMapping("/{conversationId}")
    public Response<Boolean> renameConversation(@PathVariable String conversationId,
                                                @RequestBody RenameConversationRequest request) {
        try {
            validateId(conversationId, "会话 ID");
            if (request == null) {
                throw new IllegalArgumentException("会话信息不能为空");
            }
            String title = normalizeTitle(request.title());
            boolean updated = conversationService.renameConversation(conversationId, title);
            return updated ? success(true) : failure("4040", "会话不存在");
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("重命名会话失败, id: {}", conversationId, e);
            return failure("5000", "重命名会话失败");
        }
    }

    @DeleteMapping("/{conversationId}")
    public Response<Boolean> deleteConversation(@PathVariable String conversationId) {
        try {
            validateId(conversationId, "会话 ID");
            boolean deleted = conversationService.deleteConversation(conversationId);
            return deleted ? success(true) : failure("4040", "会话不存在");
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("删除会话失败, id: {}", conversationId, e);
            return failure("5000", "删除会话失败");
        }
    }

    @GetMapping("/{conversationId}/messages")
    public Response<List<MessageView>> queryMessages(@PathVariable String conversationId) {
        try {
            validateId(conversationId, "会话 ID");
            List<MessageView> messages = conversationService.queryMessageList(conversationId)
                    .stream()
                    .map(this::mapMessage)
                    .toList();
            return success(messages);
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("查询会话消息失败, conversationId: {}", conversationId, e);
            return failure("5000", "查询会话消息失败");
        }
    }

    @PutMapping("/{conversationId}/messages/{messageId}")
    public Response<Boolean> saveMessage(@PathVariable String conversationId,
                                         @PathVariable String messageId,
                                         @RequestBody SaveMessageRequest request) {
        try {
            validateId(conversationId, "会话 ID");
            validateId(messageId, "消息 ID");
            if (request == null || !ROLES.contains(request.role())) {
                throw new IllegalArgumentException("消息角色只能是 user 或 assistant");
            }
            String status = request.status() == null ? "completed" : request.status().toLowerCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("消息状态不合法");
            }
            if (!conversationService.conversationExists(conversationId)) {
                return failure("4040", "会话不存在");
            }

            String messageType = request.messageType() == null || request.messageType().isBlank()
                    ? "chat" : request.messageType().trim();
            String content = request.content() == null ? "" : request.content();
            long sortOrder = request.sortOrder() == null ? System.currentTimeMillis() : request.sortOrder();
            conversationService.saveMessage(ConversationMessageEntity.builder()
                    .id(messageId)
                    .conversationId(conversationId)
                    .role(request.role())
                    .content(content)
                    .messageType(messageType)
                    .status(status)
                    .sortOrder(sortOrder)
                    .build());
            return success(true);
        } catch (IllegalArgumentException e) {
            return failure("0002", e.getMessage());
        } catch (Exception e) {
            log.error("保存会话消息失败, conversationId: {}, messageId: {}", conversationId, messageId, e);
            return failure("5000", "保存会话消息失败");
        }
    }

    private ConversationView mapConversation(ConversationEntity conversation) {
        return new ConversationView(
                conversation.getId(),
                conversation.getMode(),
                conversation.getTitle(),
                conversation.getCreatedTime(),
                conversation.getUpdatedTime());
    }

    private MessageView mapMessage(ConversationMessageEntity message) {
        return new MessageView(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getMessageType(),
                message.getStatus(),
                message.getSortOrder(),
                message.getCreatedTime(),
                message.getUpdatedTime());
    }

    private String normalizeMode(String mode, boolean required) {
        if (mode == null || mode.isBlank()) {
            if (required) throw new IllegalArgumentException("会话模式不能为空");
            return null;
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        if (!MODES.contains(normalized)) throw new IllegalArgumentException("会话模式只能是 chat 或 agent");
        return normalized;
    }

    private String normalizeTitle(String title) {
        String normalized = title == null || title.isBlank() ? "新的对话" : title.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("会话标题不能超过 200 个字符");
        return normalized;
    }

    private void validateId(String id, String fieldName) {
        if (id == null || id.isBlank() || id.length() > 100) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code("0000").info("调用成功").data(data).build();
    }

    private <T> Response<T> failure(String code, String info) {
        return Response.<T>builder().code(code).info(info).build();
    }

    public record CreateConversationRequest(String id, String mode, String title) {
    }

    public record RenameConversationRequest(String title) {
    }

    public record SaveMessageRequest(String role, String content, String messageType,
                                     String status, Long sortOrder) {
    }

    public record ConversationView(String id, String mode, String title,
                                   LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public record MessageView(String id, String conversationId, String role, String content,
                              String messageType, String status, Long sortOrder,
                              LocalDateTime createdTime, LocalDateTime updatedTime) {
    }
}
