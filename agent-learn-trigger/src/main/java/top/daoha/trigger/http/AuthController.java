package top.daoha.trigger.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.daoha.api.response.Response;

import static top.daoha.trigger.auth.AuthenticationSession.AUTHENTICATED_ATTRIBUTE;
import static top.daoha.trigger.auth.AuthenticationSession.MAX_INACTIVE_INTERVAL_SECONDS;

/**
 * 固定账号登录接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AuthController {

    private static final String USERNAME = "usr";
    private static final String PASSWORD = "123321";

    @PostMapping("/login")
    public ResponseEntity<Response<Boolean>> login(@RequestBody LoginRequest request,
                                                    HttpServletRequest servletRequest) {
        if (request == null
                || !USERNAME.equals(request.username())
                || !PASSWORD.equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(response("0401", "账号或密码错误", false));
        }

        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        session.setMaxInactiveInterval(MAX_INACTIVE_INTERVAL_SECONDS);
        return ResponseEntity.ok(response("0000", "登录成功", true));
    }

    @GetMapping("/status")
    public Response<Boolean> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null
                && Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED_ATTRIBUTE));
        return response("0000", "调用成功", authenticated);
    }

    @PostMapping("/logout")
    public Response<Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return response("0000", "退出成功", true);
    }

    private <T> Response<T> response(String code, String info, T data) {
        return Response.<T>builder().code(code).info(info).data(data).build();
    }

    public record LoginRequest(String username, String password) {
    }
}
