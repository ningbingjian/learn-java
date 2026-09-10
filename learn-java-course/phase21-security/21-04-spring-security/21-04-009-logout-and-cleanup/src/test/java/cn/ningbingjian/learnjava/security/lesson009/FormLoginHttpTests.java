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
class FormLoginHttpTests {
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

    @Test
    void threeUsersLoginAndSessionWorksIncludingReservedAndUnicodePasswordCharacters() throws Exception {
        for (String[] identity : new String[][]{{"member", "lesson009-test-only"}, {"support", "support+&= 汉#test"},
                {"admin", "lesson009-admin-test"}}) {
            var browser = new Browser();
            contract(browser.login(identity[0], identity[1], browser.token()), 200, "LOGIN_SUCCEEDED");
            assertThat(browser.hello()).isEqualTo(200);
        }
    }

    @Test
    void wrongPasswordAndUnknownUserHaveSameFailureContractAndRemainAnonymous() throws Exception {
        var wrong = new Browser(); var unknown = new Browser();
        var a = wrong.login("member", "wrong", wrong.token());
        var b = unknown.login("unknown", "wrong", unknown.token());
        contract(a, 401, "LOGIN_FAILED"); contract(b, 401, "LOGIN_FAILED");
        assertThat(a.body()).isEqualTo(b.body());
        assertThat(wrong.hello()).isEqualTo(401); assertThat(unknown.hello()).isEqualTo(401);
    }

    @Test
    void missingCsrfIs403AndRepairUsesTheSameFrameworkLogin() throws Exception {
        var browser = new Browser(); String token = browser.token();
        var rejected = browser.login("member", "lesson009-test-only", null);
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).doesNotContain("LOGIN_FAILED", "LOGIN_SUCCEEDED");
        assertThat(browser.hello()).isEqualTo(401);
        contract(browser.login("member", "lesson009-test-only", token), 200, "LOGIN_SUCCEEDED");
    }

    @Test
    void wrongCsrfIsRejectedBeforePasswordFailureHandler() throws Exception {
        var browser = new Browser(); browser.token();
        var response = browser.login("member", "wrong", "invalid-for-lesson");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("LOGIN_FAILED");
    }

    @Test
    void jsonCredentialsAreNotFormParameters() throws Exception {
        var browser = new Browser();
        var response = browser.request("POST", "/login", browser.token(),
                "{\"username\":\"member\",\"password\":\"lesson009-test-only\"}", "application/json");
        contract(response, 401, "LOGIN_FAILED");
        assertThat(browser.hello()).isEqualTo(401);
    }

    @Test
    void successfulLoginChangesSessionIdentifierWithoutLosingAuthentication() throws Exception {
        var browser = new Browser(); String token = browser.token(); String before = browser.id();
        contract(browser.login("member", "lesson009-test-only", token), 200, "LOGIN_SUCCEEDED");
        assertThat(browser.id()).isNotEqualTo(before);
        assertThat(browser.hello()).isEqualTo(200);
    }

    @Test
    void loginClearsOldCsrfAndFetchingAgainRepairsWrites() throws Exception {
        var browser = new Browser(); String old = browser.token();
        browser.login("member", "lesson009-test-only", old);
        assertThat(browser.request("POST", "/csrf-probe", old, "", null).statusCode()).isEqualTo(403);
        assertThat(browser.request("POST", "/csrf-probe", browser.token(), "", null).statusCode()).isEqualTo(200);
    }

    @Test
    void sessionAuthenticationIsNotTransferredToAnotherCookieContainer() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson009-test-only", browser.token());
        assertThat(browser.hello()).isEqualTo(200);
        assertThat(new Browser().hello()).isEqualTo(401);
    }

    @Test
    void failedReloginDoesNotMeanExistingSessionWasLoggedOut() throws Exception {
        var browser = new Browser(); browser.login("member", "lesson009-test-only", browser.token());
        contract(browser.login("member", "wrong", browser.token()), 401, "LOGIN_FAILED");
        assertThat(browser.hello()).isEqualTo(200);
    }
}
