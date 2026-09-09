package cn.ningbingjian.learnjava.security.lesson007;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/csrf")
    ResponseEntity<TokenResponse> csrf(CsrfToken csrfToken) {
        // 读取请求属性中的令牌，触发延迟加载；不返回Session标识或用户信息。
        var body = new TokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record TokenResponse(String headerName, String parameterName, String token) { }
}
