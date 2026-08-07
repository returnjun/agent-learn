package top.daoha.domain.agent.service.armory.business.data;

import top.daoha.domain.agent.model.entity.ArmoryCommandEntity;
import top.daoha.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

public interface ILoadDataStrategy {
    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);
}
