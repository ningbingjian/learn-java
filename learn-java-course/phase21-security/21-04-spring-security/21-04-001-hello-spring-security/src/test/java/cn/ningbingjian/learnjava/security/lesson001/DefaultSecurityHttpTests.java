package cn.ningbingjian.learnjava.security.lesson001;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.security.user.password=lesson001-test-only")
class DefaultSecurityHttpTests {
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
        assertThat(response.statusCode()).isEqualTo(401);
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
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("Hello Spring Security");
    }

    @Test
    void validPasswordReturnsBusinessJson() throws Exception {
        var response = get("/hello", "application/json", basic("user", "lesson001-test-only"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/json");
        assertThat(response.body()).isEqualTo("{\"message\":\"Hello Spring Security\"}");
    }

    @Test
    void nextRequestWithoutCredentialsIsStillRejected() throws Exception {
        assertThat(get("/hello", "application/json", basic("user", "lesson001-test-only"))
                .statusCode()).isEqualTo(200);
        assertThat(get("/hello", "application/json", null).statusCode()).isEqualTo(401);
    }

    @Test
    void missingRouteIsProtectedBeforeMvcResolvesIt() throws Exception {
        assertThat(get("/missing", "application/json", null).statusCode()).isEqualTo(401);
        assertThat(get("/missing", "application/json", basic("user", "lesson001-test-only"))
                .statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path, String accept, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", accept).GET();
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
