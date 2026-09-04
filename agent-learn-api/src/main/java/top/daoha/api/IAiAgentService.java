package top.daoha.api;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import top.daoha.api.dto.AutoAgentRequestDTO;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @ClassName : iAiAgentService
 * @Description :
 * @github:
 * @Author : 24209
 * @Date: 2026/9/3  19:43
 */

public interface IAiAgentService {
    ResponseBodyEmitter autoAgent(AutoAgentRequestDTO request, HttpServletResponse response);
}
