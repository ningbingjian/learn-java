package cn.ningbingjian.learnjava.security.lesson009;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FormLoginHandlers {
    private static final Logger log = LoggerFactory.getLogger(FormLoginHandlers.class);
    private final JsonMapper mapper;
    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public FormLoginHandlers(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        // 框架此前已执行会话策略并保存上下文；这里只收尾响应，不自行创建登录状态。
        requestCache.removeRequest(request, response);
        log.info("FORM_LOGIN_SUCCEEDED");
        write(response, 200, "LOGIN_SUCCEEDED", "本次登录成功，请使用会话访问受保护接口。");
    }

    public void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.info("FORM_LOGIN_FAILED");
        write(response, 401, "LOGIN_FAILED", "本次登录失败，请检查用户名和密码。");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new LoginResponse(code, message));
    }

    record LoginResponse(String code, String message) { }
}
