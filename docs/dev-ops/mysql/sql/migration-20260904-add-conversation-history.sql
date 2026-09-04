USE `ai-agent-station-study`;

CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` varchar(100) NOT NULL COMMENT '会话ID，由前端生成',
  `mode` varchar(16) NOT NULL COMMENT '会话模式：chat、agent',
  `title` varchar(200) NOT NULL DEFAULT '新的对话' COMMENT '会话标题',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mode_updated_time` (`mode`, `updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前端会话记录表';

CREATE TABLE IF NOT EXISTS `ai_conversation_message` (
  `id` varchar(100) NOT NULL COMMENT '消息ID，由前端生成',
  `conversation_id` varchar(100) NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT '消息角色：user、assistant',
  `content` longtext NOT NULL COMMENT 'Chat文本或Agent事件JSON',
  `message_type` varchar(32) NOT NULL DEFAULT 'chat' COMMENT '消息类型：chat、agent',
  `status` varchar(16) NOT NULL DEFAULT 'completed' COMMENT '生成状态',
  `sort_order` bigint NOT NULL COMMENT '消息排序号',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_order` (`conversation_id`, `sort_order`),
  CONSTRAINT `fk_conversation_message_conversation`
    FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前端会话消息表';
