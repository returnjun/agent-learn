package top.daoha.domain.agent.service.auto.step.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.ExecuteCommandEntity;
import top.daoha.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import top.daoha.domain.agent.service.auto.step.RootNode;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class DefaultAutoAgentExecuteStrategyFactory {


    private final RootNode executeRootNode;

    public DefaultAutoAgentExecuteStrategyFactory(RootNode rootNode) {
        this.executeRootNode = rootNode;
    }

    public StrategyHandler<ExecuteCommandEntity, DynamicContext, String> armoryStrategyHandler(){
        return executeRootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext{
        // 任务执行步骤
        private int step = 1;
        // 最大任务步骤
        private int maxStep = 1;
        //执行历史
        private StringBuilder executionHistory;
        //相关任务
        private String currentTask;
        //是否完成
        boolean isCompleted = false;
        //这个是用来讯在不同的agent类型的map
        private Map<String, AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOMap;

        public Map<String,Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value){
            dataObjects.put(key,value);
        }

        public <T> T getValue(String key){
            return (T)dataObjects.get(key);
        }
    }
}
