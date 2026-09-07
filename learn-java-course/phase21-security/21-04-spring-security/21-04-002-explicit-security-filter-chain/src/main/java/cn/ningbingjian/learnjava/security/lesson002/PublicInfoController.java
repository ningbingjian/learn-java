package cn.ningbingjian.learnjava.security.lesson002;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicInfoController {
    private static final Logger log = LoggerFactory.getLogger(PublicInfoController.class);

    @GetMapping("/public/info")
    public Map<String, String> info() {
        log.info("PUBLIC_INFO_HANDLER_REACHED");
        return Map.of("message", "Public information is available without login");
    }
}
