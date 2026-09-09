package cn.ningbingjian.learnjava.security.lesson007;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lesson.users.member-password=lesson007-test-only",
                "lesson.users.support-password=lesson007-support-test",
                "lesson.users.admin-password=lesson007-admin-test"})
class CsrfHttpTests {
    @LocalServerPort
    private int port;

    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private HttpResponse<String> request(HttpClient client, String method, String path, String token, String authorization)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("X-CSRF-TOKEN", token);
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode token(HttpClient client) throws Exception {
        var response = request(client, "GET", "/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return new JsonMapper().readTree(response.body());
    }

    @Test
    void bootstrapIsAnonymousUncachedAndEstablishesHttpOnlySessionCookie() throws Exception {
        var response = request(browser(), "GET", "/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).contains("JSESSIONID=", "HttpOnly");
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        var value = new JsonMapper().readTree(response.body());
        assertThat(value.size()).isEqualTo(3);
        assertThat(value.get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        assertThat(value.get("parameterName").asText()).isEqualTo("_csrf");
        assertThat(value.get("token").asText()).isNotBlank();
    }

    @Test
    void sameSessionAndTokenReachThePublicProbe() throws Exception {
        var client = browser();
        String token = token(client).get("token").asText();
        var response = request(client, "POST", "/csrf-probe", token, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("CSRF probe accepted; no business data changed");
    }

    @Test
    void permitAllDoesNotSkipMissingOrWrongCsrfToken() throws Exception {
        var client = browser();
        token(client);
        assertThat(request(client, "POST", "/csrf-probe", null, null).statusCode()).isEqualTo(403);
        assertThat(request(client, "POST", "/csrf-probe", "invalid-for-lesson", null).statusCode()).isEqualTo(403);
    }

    @Test
    void tokenWithoutItsSessionIsRejected() throws Exception {
        var client = browser();
        String token = token(client).get("token").asText();
        var noCookies = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        assertThat(request(noCookies, "POST", "/csrf-probe", token, null).statusCode()).isEqualTo(403);
    }

    @Test
    void tokenFromAnotherSessionDoesNotWorkEvenWithACookie() throws Exception {
        var a = browser();
        var b = browser();
        String tokenA = token(a).get("token").asText();
        token(b);
        assertThat(request(b, "POST", "/csrf-probe", tokenA, null).statusCode()).isEqualTo(403);
    }

    @Test
    void repeatedReadsHaveDifferentMasksButBothRemainValidInTheSameSession() throws Exception {
        var client = browser();
        String first = token(client).get("token").asText();
        String second = token(client).get("token").asText();
        assertThat(first).isNotEqualTo(second);
        assertThat(request(client, "POST", "/csrf-probe", first, null).statusCode()).isEqualTo(200);
        assertThat(request(client, "POST", "/csrf-probe", second, null).statusCode()).isEqualTo(200);
    }

    @Test
    void csrfTokenAndSessionDoNotAuthenticateTheUser() throws Exception {
        var client = browser();
        String token = token(client).get("token").asText();
        assertThat(request(client, "GET", "/hello", token, null).statusCode()).isEqualTo(401);
    }

    @Test
    void correctBasicDoesNotReplaceCsrfOnPost() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                "member:lesson007-test-only".getBytes(StandardCharsets.UTF_8));
        assertThat(request(browser(), "POST", "/csrf-probe", null, basic).statusCode()).isEqualTo(403);
    }

    @Test
    void invalidatedSessionRejectsOldTokenAndFreshBootstrapRepairsIt() throws Exception {
        var client = browser();
        String old = token(client).get("token").asText();
        // 仅借框架退出端点使会话失效，用于生命周期回归；退出教学在第009课。
        assertThat(request(client, "POST", "/logout", old, null).statusCode()).isEqualTo(204);
        assertThat(request(client, "POST", "/csrf-probe", old, null).statusCode()).isEqualTo(403);
        String fresh = token(client).get("token").asText();
        assertThat(request(client, "POST", "/csrf-probe", fresh, null).statusCode()).isEqualTo(200);
    }
}
