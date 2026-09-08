package cn.ningbingjian.learnjava.security.lesson006;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicInfoController {
    private static final Logger log = LoggerFactory.getLogger(PublicInfoController.class);

    @GetMapping("/public/info")
    public Map<String, String> info(@RequestParam(defaultValue = "en") String lang) {
        log.info("PUBLIC_INFO_HANDLER_REACHED");
        if (!lang.equals("en") && !lang.equals("zh")) {
            throw new UnsupportedLanguageException();
        }
        return Map.of("message", lang.equals("zh")
                ? "无需登录即可读取公开信息"
                : "Public information is available without login");
    }
}
