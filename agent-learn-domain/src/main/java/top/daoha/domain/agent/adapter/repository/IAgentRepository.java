package top.daoha.domain.agent.adapter.repository;

import top.daoha.domain.agent.model.valobj.*;

import java.util.List;
import java.util.Map;

public interface IAgentRepository {
    //查出client所需要的所有api
    List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList);
    //查出client所需要的model对象
    List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList);
    //查出需要的toolmcp工具
    List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList);

    List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList);

    Map<String,AiClientSystemPromptVO> AiClientSystemPromptMapByClientIds(List<String> clientIdList);

    List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList);

    List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList);

    List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList);

    List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList);

}
