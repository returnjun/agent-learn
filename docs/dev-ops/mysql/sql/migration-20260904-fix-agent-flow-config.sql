USE `ai-agent-station-study`;

-- 修复 Agent 1 缺少四阶段客户端流程映射的问题。
-- 该脚本可重复执行，不会删除已有的 DEFAULT 配置。
INSERT INTO `ai_agent_flow_config`
    (`agent_id`, `client_id`, `client_name`, `client_type`, `sequence`, `create_time`)
VALUES
    ('1', '3101', '任务分析和状态判断', 'TASK_ANALYZER_CLIENT', 1, NOW()),
    ('1', '3102', '具体任务执行', 'PRECISION_EXECUTOR_CLIENT', 2, NOW()),
    ('1', '3103', '质量检查和优化', 'QUALITY_SUPERVISOR_CLIENT', 3, NOW()),
    ('1', '3104', '智能响应助手', 'RESPONSE_ASSISTANT', 4, NOW())
ON DUPLICATE KEY UPDATE
    `client_name` = VALUES(`client_name`),
    `client_type` = VALUES(`client_type`),
    `sequence` = VALUES(`sequence`);
