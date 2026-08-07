package top.daoha.domain.agent.service.armory.business.data.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.adapter.repository.IAgentRepository;
import top.daoha.domain.agent.model.entity.ArmoryCommandEntity;
import top.daoha.domain.agent.model.valobj.AiClientApiVO;
import top.daoha.domain.agent.model.valobj.AiClientModelVO;
import top.daoha.domain.agent.service.armory.business.data.ILoadDataStrategy;
import top.daoha.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class AiClientModelLoadDataStrategy implements ILoadDataStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected ThreadPoolExecutor threadPoolExecutor;

    @Override
    public void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        List<String> modelIdList = armoryCommandEntity.getCommandIdList();

        CompletableFuture<List<AiClientApiVO>> aiClientApiListFuture = CompletableFuture.supplyAsync(() -> {
            log.info("查询配置数据(ai_client_api) {}", modelIdList);
            return repository.queryAiClientApiVOListByModelIds(modelIdList);
        }, threadPoolExecutor);

        CompletableFuture<List<AiClientModelVO>> aiClientModelListFuture = CompletableFuture.supplyAsync(() -> {
            log.info("查询配置数据(ai_client_model) {}", modelIdList);
            return repository.AiClientModelVOByModelIds(modelIdList);
        }, threadPoolExecutor);
    }
}
