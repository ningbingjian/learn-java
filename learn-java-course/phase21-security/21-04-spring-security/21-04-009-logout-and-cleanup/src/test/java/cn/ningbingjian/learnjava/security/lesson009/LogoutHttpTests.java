package cn.ningbingjian.learnjava.security.lesson009;

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
        properties = {"lesson.users.member-password=lesson009-test-only",
                "lesson.users.support-password=support+&= 汉#test",
                "lesson.users.admin-password=lesson009-admin-test"})
class LogoutHttpTests {
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

    private Browser authenticated(String name, String password) throws Exception {
        var browser = new Browser();
        contract(browser.login(name, password, browser.token()), 200, "LOGIN_SUCCEEDED");
        return browser;
    }
    private void noContent(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(value -> {
            assertThat(value).startsWith("JSESSIONID=");
            var cookie = java.net.HttpCookie.parse(value).getFirst();
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.hasExpired()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
        });
    }

    @Test
    void postLogoutReturns204ForJsonAndHtmlAndClearsAuthentication() throws Exception {
        for (String accept : new String[]{"application/json", "text/html"}) {
            var browser = authenticated("member", "lesson009-test-only");
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/logout"))
                    .header("Accept", accept).header("X-CSRF-TOKEN", browser.token()).POST(HttpRequest.BodyPublishers.noBody()).build();
            noContent(browser.client.send(request, HttpResponse.BodyHandlers.ofString()));
            assertThat(browser.hello()).isEqualTo(401);
            assertThat(browser.request("GET", "/me", null, "", null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void missingCsrfDoesNotLogoutAndRepairWorks() throws Exception {
        var browser = authenticated("member", "lesson009-test-only");
        assertThat(browser.request("POST", "/logout", null, "", null).statusCode()).isEqualTo(403);
        assertThat(browser.hello()).isEqualTo(200);
        noContent(browser.request("POST", "/logout", browser.token(), "", null));
        assertThat(browser.hello()).isEqualTo(401);
    }

    @Test
    void wrongCsrfDoesNotLogout() throws Exception {
        var browser = authenticated("member", "lesson009-test-only");
        assertThat(browser.request("POST", "/logout", "invalid-for-lesson", "", null).statusCode()).isEqualTo(403);
        assertThat(browser.request("GET", "/me", null, "", null).statusCode()).isEqualTo(200);
    }

    @Test
    void getOnlyShowsConfirmationAndDoesNotLogout() throws Exception {
        var browser = authenticated("member", "lesson009-test-only");
        var response = browser.request("GET", "/logout", null, "", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("method=\"post\"", "action=\"/logout\"");
        assertThat(browser.hello()).isEqualTo(200);
        assertThat(browser.request("GET", "/me", null, "", null).statusCode()).isEqualTo(200);
    }

    @Test
    void replayingTheOriginalSessionCookieCannotRestoreAuthenticationOrOldCsrf() throws Exception {
        var browser = authenticated("member", "lesson009-test-only");
        String token = browser.token(); String oldId = browser.id();
        noContent(browser.request("POST", "/logout", token, "", null));
        var client = HttpClient.newHttpClient();
        for (String path : new String[]{"/hello", "/me"}) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Cookie", "JSESSIONID=" + oldId).header("Accept", "application/json").GET().build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/csrf-probe"))
                .header("Cookie", "JSESSIONID=" + oldId).header("X-CSRF-TOKEN", token)
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void freshAnonymousTokenEnablesProbeAndNewLoginAfterLogout() throws Exception {
        var browser = authenticated("member", "lesson009-test-only");
        String old = browser.token(); noContent(browser.request("POST", "/logout", old, "", null));
        String fresh = browser.token();
        assertThat(browser.request("POST", "/csrf-probe", old, "", null).statusCode()).isEqualTo(403);
        assertThat(browser.request("POST", "/csrf-probe", fresh, "", null).statusCode()).isEqualTo(200);
        assertThat(browser.hello()).isEqualTo(401);
        contract(browser.login("member", "lesson009-test-only", fresh), 200, "LOGIN_SUCCEEDED");
        assertThat(browser.request("GET", "/me", null, "", null).body()).contains("member");
    }

    @Test
    void anonymousLogoutWithFreshCsrfCanBeRepeatedButIsNotAutomaticRetry() throws Exception {
        var browser = new Browser();
        for (int i = 0; i < 2; i++) {
            noContent(browser.request("POST", "/logout", browser.token(), "", null));
            assertThat(browser.hello()).isEqualTo(401);
        }
    }

    @Test
    void logoutOnlyInvalidatesItsOwnSession() throws Exception {
        var first = authenticated("member", "lesson009-test-only");
        var other = authenticated("support", "support+&= 汉#test");
        noContent(first.request("POST", "/logout", first.token(), "", null));
        assertThat(first.hello()).isEqualTo(401);
        var response = other.request("GET", "/me", null, "", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("support");
    }
}
