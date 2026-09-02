package top.daoha.domain.agent.service.auto.step;


import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.ExecuteCommandEntity;
import top.daoha.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import top.daoha.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import top.daoha.domain.agent.service.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

/**
 * 质量监督节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:43
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 第三阶段：质量监督
        log.info("\n🔍 阶段3: 质量监督检查");
        
        // 从动态上下文中获取执行结果
        String executionResult = dynamicContext.getValue("executionResult");
        if (executionResult == null || executionResult.isBlank()) {
            log.warn("⚠️ 执行结果为空，本轮按未通过处理并准备重新执行");

            String supervisionResult = "执行结果为空，无法进行质量验收";
            dynamicContext.setValue("supervisionResult", supervisionResult);
            dynamicContext.setValue("qualityDecision", QualityDecision.UNKNOWN);
            dynamicContext.setCompleted(false);
            dynamicContext.setCurrentTask("上一轮没有产生执行结果，请重新执行任务并返回可验收的结果");

            appendExecutionHistory(dynamicContext, executionResult, supervisionResult);
            dynamicContext.setStep(dynamicContext.getStep() + 1);
            return router(requestParameter, dynamicContext);
        }
        
        String supervisionPrompt = String.format("""
                **用户原始需求:** %s
                
                **执行结果:** %s
                
                **监督要求:** 请评估执行结果的质量，识别问题，并提供改进建议。
                只有用户需求已经完整实现并且执行结果中存在可核验的成果时才返回 PASS。
                只要仍有缺失项、仅给出建议但没有实际执行，或者成果不可核验，就必须返回 FAIL 或 OPTIMIZE。
                
                **输出格式:**
                质量评估: [对执行结果的整体评估]
                问题识别: [发现的问题和不足]
                改进建议: [具体的改进建议]
                质量评分: [1-10分的质量评分]
                是否通过: [PASS/FAIL/OPTIMIZE]
                """, requestParameter.getMessage(), executionResult);

        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String supervisionResult = chatClient
                .prompt(supervisionPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        QualityDecision qualityDecision = QualityDecisionParser.parse(supervisionResult);
        parseSupervisionResult(dynamicContext.getStep(), supervisionResult, qualityDecision);
        
        // 将监督结果保存到动态上下文中
        dynamicContext.setValue("supervisionResult", supervisionResult);
        dynamicContext.setValue("qualityDecision", qualityDecision);

        // 只有明确的 PASS 才能结束；无法解析的结果按未通过处理，避免误判完成。
        switch (qualityDecision) {
            case PASS -> {
                log.info("✅ 质量检查明确通过");
                dynamicContext.setCompleted(true);
            }
            case FAIL -> {
                log.info("❌ 质量检查未通过，需要重新执行");
                dynamicContext.setCompleted(false);
                dynamicContext.setCurrentTask("根据质量监督指出的问题重新执行任务：\n" + supervisionResult);
            }
            case OPTIMIZE -> {
                log.info("🔧 质量检查建议优化，继续执行下一轮");
                dynamicContext.setCompleted(false);
                dynamicContext.setCurrentTask("根据质量监督建议优化执行结果：\n" + supervisionResult);
            }
            case UNKNOWN -> {
                log.warn("⚠️ 无法识别质量监督结果，按未通过处理");
                dynamicContext.setCompleted(false);
                dynamicContext.setCurrentTask("质量监督结果格式无法识别，请重新检查任务完成情况：\n" + supervisionResult);
            }
        }
        
        // 每轮只在监督结束后记录一次完整历史，避免一轮被统计成两步。
        appendExecutionHistory(dynamicContext, executionResult, supervisionResult);
        
        // 增加步骤计数
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则返回到Step1AnalyzerNode进行下一轮分析
        return getBean("step1AnalyzerNode");
    }
    
    /**
     * 解析监督结果
     */
    private void parseSupervisionResult(int step, String supervisionResult, QualityDecision qualityDecision) {
        log.info("\n🔍 === 第 {} 步监督结果 ===", step);

        String safeSupervisionResult = supervisionResult == null ? "" : supervisionResult;
        String[] lines = safeSupervisionResult.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.contains("质量评估:")) {
                currentSection = "assessment";
                log.info("\n📊 质量评估:");
                continue;
            } else if (line.contains("问题识别:")) {
                currentSection = "issues";
                log.info("\n⚠️ 问题识别:");
                continue;
            } else if (line.contains("改进建议:")) {
                currentSection = "suggestions";
                log.info("\n💡 改进建议:");
                continue;
            } else if (line.contains("质量评分:")) {
                currentSection = "score";
                String score = line.substring(line.indexOf(":") + 1).trim();
                log.info("\n📊 质量评分: {}", score);
                continue;
            } else if (line.contains("是否通过")) {
                currentSection = "pass";
                switch (qualityDecision) {
                    case PASS -> log.info("\n✅ 检查结果: 通过");
                    case FAIL -> log.info("\n❌ 检查结果: 未通过");
                    case OPTIMIZE -> log.info("\n🔧 检查结果: 需要优化");
                    case UNKNOWN -> log.warn("\n⚠️ 检查结果: 无法识别");
                }
                continue;
            }
            
            switch (currentSection) {
                case "assessment":
                    log.info("   📋 {}", line);
                    break;
                case "issues":
                    log.info("   ⚠️ {}", line);
                    break;
                case "suggestions":
                    log.info("   💡 {}", line);
                    break;
                default:
                    log.info("   📝 {}", line);
                    break;
            }
        }
    }

    private void appendExecutionHistory(
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
            String executionResult,
            String supervisionResult) {
        String stepSummary = String.format("""
                === 第 %d 步完整记录 ===
                【分析阶段】%s
                【执行阶段】%s
                【监督阶段】%s
                """, dynamicContext.getStep(),
                dynamicContext.getValue("analysisResult"),
                executionResult,
                supervisionResult);

        dynamicContext.getExecutionHistory().append(stepSummary);
    }
    
}
