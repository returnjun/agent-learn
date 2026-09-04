package top.daoha.domain.agent.service.auto.step;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import top.daoha.domain.agent.adapter.repository.IAgentRepository;
import top.daoha.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import top.daoha.domain.agent.model.entity.ExecuteCommandEntity;
import top.daoha.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import top.daoha.domain.agent.model.valobj.enums.AiAgentEnumVO;
import top.daoha.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import top.daoha.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import top.daoha.domain.agent.service.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext,String> {

    private final Logger log = LoggerFactory.getLogger(AbstractExecuteSupport.class);

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected IAgentRepository repository;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    protected void multiThread(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }


    protected void validateAgentFlowConfig(Map<String, AiAgentClientFlowConfigVO> flowConfigMap,
                                           String aiAgentId) {
        if (flowConfigMap == null || flowConfigMap.isEmpty()) {
            throw new IllegalStateException("AI Agent [" + aiAgentId + "] 未配置客户端执行流程");
        }

        String missingTypes = Arrays.stream(requiredClientTypes())
                .filter(type -> !flowConfigMap.containsKey(type.getCode()))
                .map(AiClientTypeEnumVO::getCode)
                .collect(Collectors.joining(", "));
        if (!missingTypes.isEmpty()) {
            throw new IllegalStateException(
                    "AI Agent [" + aiAgentId + "] 缺少客户端流程配置: " + missingTypes);
        }

        Arrays.stream(requiredClientTypes()).forEach(type -> {
            AiAgentClientFlowConfigVO config = flowConfigMap.get(type.getCode());
            requireChatClient(config, type);
        });
    }

    protected ChatClient getRequiredChatClient(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                               AiClientTypeEnumVO clientType) {
        Map<String, AiAgentClientFlowConfigVO> flowConfigMap = dynamicContext.getAiAgentClientFlowConfigVOMap();
        AiAgentClientFlowConfigVO config = flowConfigMap == null ? null : flowConfigMap.get(clientType.getCode());
        return requireChatClient(config, clientType);
    }

    private ChatClient requireChatClient(AiAgentClientFlowConfigVO config, AiClientTypeEnumVO clientType) {
        if (config == null || config.getClientId() == null || config.getClientId().isBlank()) {
            throw new IllegalStateException("缺少 Agent 客户端流程配置: " + clientType.getCode());
        }

        String beanName = AiAgentEnumVO.AI_CLIENT.getBeanName(config.getClientId());
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalStateException(
                    "Agent 客户端未装配: " + beanName + "，流程类型: " + clientType.getCode());
        }
        return applicationContext.getBean(beanName, ChatClient.class);
    }

    private AiClientTypeEnumVO[] requiredClientTypes() {
        return new AiClientTypeEnumVO[]{
                AiClientTypeEnumVO.TASK_ANALYZER_CLIENT,
                AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT,
                AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT,
                AiClientTypeEnumVO.RESPONSE_ASSISTANT
        };
    }

    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    protected void sendSseResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                 AutoAgentExecuteResultEntity result) {
        try {
            ResponseBodyEmitter emitter = dynamicContext.getValue("emitter");
            if (emitter != null) {
                // 发送SSE格式的数据
                String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
                emitter.send(sseData);
            }
        }catch (IOException e){
            log.error("发送SSE结果失败:{}",e.getMessage(),e);
        }
    }

}
