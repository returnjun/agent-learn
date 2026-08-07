package top.daoha.domain.agent.service.armory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.ArmoryCommandEntity;
import top.daoha.domain.agent.model.valobj.AiAgentEnumVO;
import top.daoha.domain.agent.service.armory.business.data.ILoadDataStrategy;
import top.daoha.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RootNode extends AbstractArmorySupport{
    @Resource
    private AiClientApiNode aiClientApiNode;

    private final Map<String, ILoadDataStrategy> loadDataStrategyMap;

    public RootNode(Map<String, ILoadDataStrategy> loadDataStrategyMap) {
        this.loadDataStrategyMap = loadDataStrategyMap;
    }

    @Override
    protected void multiThread(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 通过策略加载数据。这和之前的区别其实就是这里的commandType可以使用业务层面的说法了，api，model等不用直接写bean的名字了
        // 获取命令；不同的命令类型，对应不同的数据加载策略
        String commandType = requestParameter.getCommandType();

        // 获取策略
        AiAgentEnumVO aiAgentEnumVO = AiAgentEnumVO.getByCode(commandType);
        String loadDataStrategyKey = aiAgentEnumVO.getLoadDataStrategy();

        // 加载数据
        ILoadDataStrategy loadDataStrategy = loadDataStrategyMap.get(loadDataStrategyKey);
        loadDataStrategy.loadData(requestParameter, dynamicContext);
    }

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建，数据加载节点 {}", JSON.toJSONString(requestParameter));
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientApiNode;
    }
}
