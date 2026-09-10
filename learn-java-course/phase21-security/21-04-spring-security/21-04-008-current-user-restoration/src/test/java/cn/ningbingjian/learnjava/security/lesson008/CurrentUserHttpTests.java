package cn.ningbingjian.learnjava.security.lesson008;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lesson.users.member-password=lesson008-test-only",
                "lesson.users.support-password=support+&= 汉#test",
                "lesson.users.admin-password=lesson008-admin-test"})
class CurrentUserHttpTests {
    @LocalServerPort
    private int port;

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> request(String method, String path, String csrf, String body, String contentType) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (contentType != null) request.header("Content-Type", contentType);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        String token() throws Exception {
            var response = request("GET", "/csrf", null, "", null);
            assertThat(response.statusCode()).isEqualTo(200);
            return new JsonMapper().readTree(response.body()).get("token").asText();
        }
        HttpResponse<String> login(String name, String password, String token) throws Exception {
            return request("POST", "/login", token, "username=" + encode(name) + "&password=" + encode(password),
                    "application/x-www-form-urlencoded");
        }
        int hello() throws Exception { return request("GET", "/hello", null, "", null).statusCode(); }
        String id() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().getValue(); }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void contract(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        var value = new JsonMapper().readTree(response.body());
        assertThat(value.size()).isEqualTo(2);
        assertThat(value.get("code").asText()).isEqualTo(code);
    }

    private void user(HttpResponse<String> response, String username, String authority) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        var value = new JsonMapper().readTree(response.body());
        assertThat(value.size()).isEqualTo(2);
        assertThat(value.get("username").asText()).isEqualTo(username);
        assertThat(value.get("authorities").isArray()).isTrue();
        assertThat(value.get("authorities").size()).isEqualTo(2);
        assertThat(value.get("authorities").get(0).asText()).isEqualTo("FACTOR_PASSWORD");
        assertThat(value.get("authorities").get(1).asText()).isEqualTo(authority);
        assertThat(response.body()).doesNotContain("password", "credentials", "principal", "{bcrypt}", "JSESSIONID");
    }

    @Test
    void anonymousMeUsesExisting401Contract() throws Exception {
        var response = new Browser().request("GET", "/me", null, "", null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("AUTHENTICATION_REQUIRED").doesNotContain("username", "authorities");
    }

    @Test
    void threeUsersAreRestoredByCookieAndOnlyDisplayFieldsAreReturned() throws Exception {
        for (String[] row : new String[][]{{"member", "lesson008-test-only", "ROLE_MEMBER"},
                {"support", "support+&= 汉#test", "ROLE_SUPPORT"}, {"admin", "lesson008-admin-test", "ROLE_ADMIN"}}) {
            var browser = new Browser();
            contract(browser.login(row[0], row[1], browser.token()), 200, "LOGIN_SUCCEEDED");
            for (int i = 0; i < 2; i++) user(browser.request("GET", "/me", null, "", null), row[0], row[2]);
        }
    }

    @Test
    void explicitBasicIdentifiesOnlyThatRequestWithoutTransferringItsIdentity() throws Exception {
        String credentials = java.util.Base64.getEncoder().encodeToString("member:lesson008-test-only".getBytes(StandardCharsets.UTF_8));
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/me"))
                .header("Accept", "application/json").header("Authorization", "Basic " + credentials).GET().build();
        user(client.send(request, HttpResponse.BodyHandlers.ofString()), "member", "ROLE_MEMBER");
        assertThat(new Browser().request("GET", "/me", null, "", null).statusCode()).isEqualTo(401);
    }

    @Test
    void inventedSessionCookieCannotCreateIdentity() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/me"))
                .header("Accept", "application/json").header("Cookie", "JSESSIONID=invalid-for-lesson").GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("username", "authorities");
    }

    @Test
    void invalidatedServerSessionReturns401OnTheNextIdentityRequest() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson008-test-only", browser.token());
        user(browser.request("GET", "/me", null, "", null), "member", "ROLE_MEMBER");
        assertThat(browser.request("POST", "/logout", browser.token(), "", null).statusCode()).isEqualTo(204);
        assertThat(browser.request("GET", "/me", null, "", null).statusCode()).isEqualTo(401);
    }

    @Test
    void queryUsernameDoesNotOverrideAuthenticatedPrincipal() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson008-test-only", browser.token());
        user(browser.request("GET", "/me?username=admin", null, "", null), "member", "ROLE_MEMBER");
    }

    @Test
    void onlyListedGetIsAllowedEvenWithAnAuthenticatedSessionAndValidCsrf() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson008-test-only", browser.token());
        assertThat(browser.request("POST", "/me", browser.token(), "", null).statusCode()).isEqualTo(403);
        assertThat(browser.request("HEAD", "/me", null, "", null).statusCode()).isEqualTo(403);
        assertThat(browser.request("GET", "/me-extra", null, "", null).statusCode()).isEqualTo(403);
    }

    @Test
    void failedLoginAsAnotherNameDoesNotReplaceCurrentUser() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson008-test-only", browser.token());
        contract(browser.login("admin", "wrong", browser.token()), 401, "LOGIN_FAILED");
        user(browser.request("GET", "/me", null, "", null), "member", "ROLE_MEMBER");
    }
}
