package top.daoha.domain.agent.service.auto.step;


import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import top.daoha.domain.agent.model.entity.ExecuteCommandEntity;
import top.daoha.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import top.daoha.domain.agent.service.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

/**
 * 精准执行节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:42
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport{

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n⚡ 阶段2: 精准任务执行");
        
        // 从动态上下文中获取分析结果
        String analysisResult = dynamicContext.getValue("analysisResult");
        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("⚠️ 分析结果为空，使用默认执行策略");
            analysisResult = "执行当前任务步骤";
        }
        
        String executionPrompt = String.format("""
                **分析师策略:** %s
                
                **执行指令:** 根据上述分析师的策略，执行具体的任务步骤。
                
                **执行要求:**
                1. 严格按照策略执行
                2. 使用必要的工具
                3. 确保执行质量
                4. 详细记录过程
                
                **输出格式:**
                执行目标: [明确的执行目标]
                执行过程: [详细的执行步骤]
                执行结果: [具体的执行成果]
                质量检查: [自我质量评估]
                """, analysisResult);

        // 获取对话客户端
        ChatClient chatClient = getRequiredChatClient(dynamicContext, AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT);

        String executionResult = chatClient
                .prompt(executionPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        parseExecutionResult(dynamicContext, executionResult, requestParameter.getSessionId());
        
        // 将执行结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("executionResult", executionResult);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }

    /**
     * 解析执行结果
     */
    private void parseExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String executionResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n⚡ === 第 {} 步执行结果 ===", step);

        String[] lines = executionResult.split("\n");
        String currentSection = "";
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains("执行目标:")) {
                // 发送上一个section的内容
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_target";
                sectionContent = new StringBuilder();
                log.info("\n🎯 执行目标:");
                continue;
            } else if (line.contains("执行过程:")) {
                // 发送上一个section的内容
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_process";
                sectionContent = new StringBuilder();
                log.info("\n🔧 执行过程:");
                continue;
            } else if (line.contains("执行结果:")) {
                // 发送上一个section的内容
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_result";
                sectionContent = new StringBuilder();
                log.info("\n📈 执行结果:");
                continue;
            } else if (line.contains("质量检查:")) {
                // 发送上一个section的内容
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_quality";
                sectionContent = new StringBuilder();
                log.info("\n🔍 质量检查:");
                continue;
            }

            // 收集当前section的内容
            if (!currentSection.isEmpty()) {
                sectionContent.append(line).append("\n");
                switch (currentSection) {
                    case "execution_target":
                        log.info("   🎯 {}", line);
                        break;
                    case "execution_process":
                        log.info("   ⚙️ {}", line);
                        break;
                    case "execution_result":
                        log.info("   📊 {}", line);
                        break;
                    case "execution_quality":
                        log.info("   ✅ {}", line);
                        break;
                    default:
                        log.info("   📝 {}", line);
                        break;
                }
            }
        }

        // 发送最后一个section的内容
        sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
    }

    /**
     * 发送执行阶段细分结果到流式输出
     */
    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String subType, String content, String sessionId) {
        // 抽取的通用判断逻辑
        if (!subType.isEmpty() && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                    dynamicContext.getStep(), subType, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }
    
}
