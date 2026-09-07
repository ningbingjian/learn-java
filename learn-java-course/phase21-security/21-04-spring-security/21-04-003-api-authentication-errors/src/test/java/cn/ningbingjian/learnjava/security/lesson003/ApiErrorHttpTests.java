package cn.ningbingjian.learnjava.security.lesson003;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.security.user.password=lesson003-test-only")
class ApiErrorHttpTests {
    @LocalServerPort
    private int port;

    // 不跟随重定向、不保留 Cookie，使每个请求的认证条件明确。
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void jsonWithoutCredentialsIsChallengedBeforeBusinessResponse() throws Exception {
        var response = get("/hello", "application/json", null);
        assertAuthError(response);
        assertThat(response.headers().firstValue("WWW-Authenticate").orElseThrow())
                .startsWith("Basic");
        assertThat(response.body()).doesNotContain("Hello Spring Security");
    }

    @Test
    void htmlWithoutCredentialsRedirectsToLogin() throws Exception {
        var response = get("/hello", "text/html", null);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(URI.create(response.headers().firstValue("Location").orElseThrow()).getPath())
                .isEqualTo("/login");
    }

    @Test
    void generatedLoginPageIsAccessible() throws Exception {
        var response = get("/login", "text/html", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("name=\"username\"", "name=\"password\"", "name=\"_csrf\"");
    }

    @Test
    void wrongPasswordDoesNotReachBusinessResponse() throws Exception {
        var response = get("/hello", "application/json", basic("user", "wrong"));
        assertAuthError(response);
        assertThat(response.body()).doesNotContain("Hello Spring Security");
    }

    @Test
    void validPasswordReturnsBusinessJson() throws Exception {
        var response = get("/hello", "application/json", basic("user", "lesson003-test-only"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/json");
        assertThat(response.body()).isEqualTo("{\"message\":\"Hello Spring Security\"}");
    }

    @Test
    void nextRequestWithoutCredentialsIsStillRejected() throws Exception {
        assertThat(get("/hello", "application/json", basic("user", "lesson003-test-only"))
                .statusCode()).isEqualTo(200);
        assertThat(get("/hello", "application/json", null).statusCode()).isEqualTo(401);
    }

    @Test
    void missingRouteIsProtectedBeforeMvcResolvesIt() throws Exception {
        assertThat(get("/missing", "application/json", null).statusCode()).isEqualTo(401);
        assertThat(get("/missing", "application/json", basic("user", "lesson003-test-only"))
                .statusCode()).isEqualTo(404);
    }

    @Test
    void publicInfoIsAvailableAnonymouslyAndStillHasSecurityHeaders() throws Exception {
        var response = get("/public/info", "application/json", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"message\":\"Public information is available without login\"}");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().firstValue("X-Content-Type-Options").orElseThrow()).isEqualTo("nosniff");
    }

    @Test
    void publicInfoQueryDoesNotChangeTheMatchedPath() throws Exception {
        assertThat(get("/public/info?view=summary", "application/json", null).statusCode()).isEqualTo(200);
    }

    @Test
    void similarPathsDoNotBroadenThePublicBoundary() throws Exception {
        for (String path : new String[]{"/public/info-extra", "/public/info/private", "/public/other", "/api/public/info"}) {
            assertThat(get(path, "application/json", null).statusCode()).as(path).isEqualTo(401);
        }
    }

    @Test
    void invalidBasicCredentialsAreRejectedEvenOnPublicInfo() throws Exception {
        assertAuthError(get("/public/info", "application/json", basic("user", "wrong")));
    }

    @Test
    void mvcParameterErrorHasItsOwn400Contract() throws Exception {
        var response = get("/public/info?lang=unknown", "application/json", null);
        assertThat(response.statusCode()).isEqualTo(400);
        var error = new JsonMapper().readTree(response.body());
        assertThat(error.get("code").asText()).isEqualTo("UNSUPPORTED_LANGUAGE");
        assertThat(error.get("message").asText()).isEqualTo("lang只支持en或zh，请修改参数后重试。");
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void correctedLanguageReturns200() throws Exception {
        var response = get("/public/info?lang=zh", "application/json", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new JsonMapper().readTree(response.body()).get("message").asText())
                .isEqualTo("无需登录即可读取公开信息");
    }

    @Test
    void absentWildcardAndUnmatchedAcceptUseBasicFallback() throws Exception {
        for (String accept : new String[]{null, "*/*", "application/xml"}) {
            var response = get("/hello", accept, null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic");
            assertThat(response.body()).doesNotContain("AUTHENTICATION_REQUIRED");
        }
    }

    @Test
    void browserHtmlWithWildcardStillRedirects() throws Exception {
        var response = get("/hello", "text/html,application/xhtml+xml,*/*;q=0.8", null);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(URI.create(response.headers().firstValue("Location").orElseThrow()).getPath()).isEqualTo("/login");
    }

    @Test
    void malformedBasicAlsoUsesTheApiContract() throws Exception {
        assertAuthError(get("/hello", "application/json", "Basic !!!"));
    }

    @Test
    void unknownUserAndWrongPasswordHaveTheSamePublicResponse() throws Exception {
        var unknown = get("/hello", "application/json", basic("no-such-user", "wrong"));
        var wrong = get("/hello", "application/json", basic("user", "wrong"));
        assertAuthError(unknown);
        assertThat(unknown.body()).isEqualTo(wrong.body());
    }

    @Test
    void mixedJsonAndHtmlFollowOurExplicitJsonFirstPolicy() throws Exception {
        assertAuthError(get("/hello", "text/html,application/json", null));
    }

    @Test
    void wrongBasicWithHtmlUsesTheConfiguredHtmlEntry() throws Exception {
        var response = get("/hello", "text/html", basic("user", "wrong"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(URI.create(response.headers().firstValue("Location").orElseThrow()).getPath()).isEqualTo("/login");
    }

    private static void assertAuthError(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().firstValue("WWW-Authenticate").orElseThrow()).isEqualTo("Basic realm=\"Realm\"");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Location")).isEmpty();
        var error = new JsonMapper().readTree(response.body());
        assertThat(error.size()).isEqualTo(2);
        assertThat(error.get("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(error.get("message").asText()).isEqualTo("未通过身份认证，请检查凭据后重试。");
    }

    private HttpResponse<String> get(String path, String accept, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (accept != null) request.header("Accept", accept);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
