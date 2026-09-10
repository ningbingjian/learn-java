package cn.ningbingjian.learnjava.security.lesson009;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class LogoutObservationHandler implements LogoutHandler {
    private static final Logger log = LoggerFactory.getLogger(LogoutObservationHandler.class);

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 仅证明进入退出处理链，不代替框架清理，也不宣称此时全部清理已完成。
        log.info("LOGOUT_HANDLER_REACHED");
    }
}
