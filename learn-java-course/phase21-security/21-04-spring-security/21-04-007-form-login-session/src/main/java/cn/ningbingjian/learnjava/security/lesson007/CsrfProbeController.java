package cn.ningbingjian.learnjava.security.lesson007;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfProbeController {
    private static final Logger log = LoggerFactory.getLogger(CsrfProbeController.class);

    @PostMapping("/csrf-probe")
    Map<String, String> probe() {
        log.info("CSRF_PROBE_HANDLER_REACHED");
        // 仅确认请求进入方法，不创建、修改或删除任何业务资源。
        return Map.of("message", "CSRF probe accepted; no business data changed");
    }
}
