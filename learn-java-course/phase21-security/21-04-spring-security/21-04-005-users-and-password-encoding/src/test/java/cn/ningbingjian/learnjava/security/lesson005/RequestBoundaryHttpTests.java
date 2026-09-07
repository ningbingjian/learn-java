package cn.ningbingjian.learnjava.security.lesson005;

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
        properties = {"lesson.users.member-password=lesson005-test-only",
                "lesson.users.support-password=lesson005-support-test",
                "lesson.users.admin-password=lesson005-admin-test"})
class RequestBoundaryHttpTests {
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
        var response = get("/hello", "application/json", basic("member", "wrong"));
        assertAuthError(response);
        assertThat(response.body()).doesNotContain("Hello Spring Security");
    }

    @Test
    void validPasswordReturnsBusinessJson() throws Exception {
        var response = get("/hello", "application/json", basic("member", "lesson005-test-only"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/json");
        assertThat(response.body()).isEqualTo("{\"message\":\"Hello Spring Security\"}");
    }

    @Test
    void nextRequestWithoutCredentialsIsStillRejected() throws Exception {
        assertThat(get("/hello", "application/json", basic("member", "lesson005-test-only"))
                .statusCode()).isEqualTo(200);
        assertThat(get("/hello", "application/json", null).statusCode()).isEqualTo(401);
    }

    @Test
    void missingRouteIsProtectedBeforeMvcResolvesIt() throws Exception {
        assertThat(get("/missing", "application/json", null).statusCode()).isEqualTo(401);
        assertThat(get("/missing", "application/json", basic("member", "lesson005-test-only"))
                .statusCode()).isEqualTo(403);
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
        assertAuthError(get("/public/info", "application/json", basic("member", "wrong")));
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
        var wrong = get("/hello", "application/json", basic("member", "wrong"));
        assertAuthError(unknown);
        assertThat(unknown.body()).isEqualTo(wrong.body());
    }

    @Test
    void mixedJsonAndHtmlFollowOurExplicitJsonFirstPolicy() throws Exception {
        assertAuthError(get("/hello", "text/html,application/json", null));
    }

    @Test
    void wrongBasicWithHtmlUsesTheConfiguredHtmlEntry() throws Exception {
        var response = get("/hello", "text/html", basic("member", "wrong"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(URI.create(response.headers().firstValue("Location").orElseThrow()).getPath()).isEqualTo("/login");
    }

    @Test
    void authenticatedSimilarAndUnlistedPathsAreDeniedInsteadOfReachingMvc() throws Exception {
        for (String path : new String[]{"/public/info-extra", "/public/info/private", "/public/other",
                "/public/info/", "/api/public/info", "/missing", "/error"}) {
            var response = get(path, "application/json", basic("member", "lesson005-test-only"));
            assertThat(response.statusCode()).as(path).isEqualTo(403);
            assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
            assertThat(response.body()).doesNotContain("Hello Spring Security", "Public information", "AUTHENTICATION_REQUIRED");
        }
    }

    @Test
    void optionsDoesNotInheritThePublicGetRule() throws Exception {
        assertAuthError(request("OPTIONS", "/public/info", null));
        assertThat(request("OPTIONS", "/public/info", basic("member", "lesson005-test-only"))
                .statusCode()).isEqualTo(403);
    }

    @Test
    void headDoesNotInheritGetAuthorizationEvenThoughMvcSupportsHead() throws Exception {
        var anonymous = request("HEAD", "/public/info", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).isEmpty();
        assertThat(request("HEAD", "/public/info", basic("member", "lesson005-test-only"))
                .statusCode()).isEqualTo(403);
    }

    @Test
    void errorDispatchPreservesCsrfRejectionWithoutOpeningDirectErrorRequests() throws Exception {
        // POST无令牌先由CSRF拒绝，不能用这个403证明请求匹配规则生效。
        assertThat(request("POST", "/public/info", null).statusCode()).isEqualTo(403);
        assertAuthError(get("/error", "application/json", null));
    }

    @Test
    void generatedLoginAssetsRemainAvailableButNearbyPathsDoNot() throws Exception {
        var css = get("/default-ui.css", "text/css", null);
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/css");
        assertThat(css.body()).isNotBlank();
        assertAuthError(get("/login/extra", "application/json", null));
        assertAuthError(get("/default-ui.css-extra", "application/json", null));
    }

    @Test
    void loginProcessingWithCsrfStillAuthenticatesAndCookieCanAccessListedHello() throws Exception {
        // 框架登录入口的兼容性回归；会话与CSRF的完整教学放在后续课程。
        var cookieJar = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        var browser = HttpClient.newBuilder().cookieHandler(cookieJar)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        var login = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        var token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertThat(token.find()).isTrue();
        String form = "username=member&password=lesson005-test-only&_csrf="
                + java.net.URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        var result = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.headers().firstValue("Location").orElseThrow()).doesNotContain("error");
        var hello = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hello"))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(hello.statusCode()).isEqualTo(200);
        assertThat(hello.body()).contains("Hello Spring Security");
    }

    private HttpResponse<String> request(String method, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void threeIdentitiesAuthenticateButAdminDoesNotBypassDefaultDeny() throws Exception {
        for (String[] user : new String[][]{{"member", "lesson005-test-only"},
                {"support", "lesson005-support-test"}, {"admin", "lesson005-admin-test"}}) {
            assertThat(get("/hello", "application/json", basic(user[0], user[1])).statusCode()).isEqualTo(200);
            assertAuthError(get("/hello", "application/json", basic(user[0], "wrong")));
            assertThat(get("/missing", "application/json", basic(user[0], user[1])).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void bootDefaultUserIsNoLongerAvailable() throws Exception {
        assertAuthError(get("/hello", "application/json", basic("user", "lesson005-test-only")));
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
