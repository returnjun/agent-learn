package top.daoha.domain.agent.service.armory.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import top.daoha.domain.agent.model.entity.ArmoryCommandEntity;
import top.daoha.domain.agent.service.armory.RootNode;

import java.util.HashMap;
import java.util.Map;

@Service
public class DefaultArmoryStrategyFactory {

    private final RootNode rootNode;

    public DefaultArmoryStrategyFactory(RootNode rootNode) {
        this.rootNode = rootNode;
    }

    public StrategyHandler<ArmoryCommandEntity, DynamicContext, String> armoryStrategyHandler(){
        return rootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext{
        public Map<String,Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value){
            dataObjects.put(key,value);
        }

        public <T> T getValue(String key){
            return (T)dataObjects.get(key);
        }
    }
}
