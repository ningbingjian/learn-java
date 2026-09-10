package cn.ningbingjian.learnjava.security.lesson008;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(Authentication authentication) {
        // 仅映射展示字段，不序列化Authentication、principal或credentials。
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).sorted().toList();
        log.info("ME_HANDLER_REACHED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUser(authentication.getName(), authorities));
    }

    record CurrentUser(String username, List<String> authorities) { }
}
