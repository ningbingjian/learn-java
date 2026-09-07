# 21-04-003 为 API 建立可观察的认证失败契约

[模块入口](../README.md) · [上一课](../21-04-002-explicit-security-filter-chain/README.md) · [本课大纲](../00-模块学习大纲.md#lesson-003) · [版本基线](../01-版本基线.md)

## 本课导入

第二课已经能够在同一页面对照公开200与受保护401。但401的响应体可能为空，前端只能根据状态给出笼统提示。现在需要一份前后端约定：请求没有通过认证时，HTTP状态仍是401，同时返回稳定的错误代码与可读说明；用户在浏览器地址栏访问受保护页面时，仍然能够跳转到可访问的登录页。

这里的困难不只是“写一段JSON”。异常可能发生在业务Controller之前，也可能发生在业务方法之内；如果把所有失败都交给Controller异常处理器，前面的认证失败可能根本到不了它。错误Basic凭据与完全没带凭据，也可能从安全链的不同位置触发失败。本课会把这几种路径用实际请求和日志区分开。

学完应能定义并实现一个保留真实状态的API认证失败契约，解释 `AuthenticationEntryPoint`与MVC异常处理器的分工，按请求选择JSON或HTML登录入口，并让前端显示错误代码、原始响应和明确的重试操作。沿用默认用户和Basic对照，不在本课提前实现页面登录、会话、角色或JWT。

前置为第002课、Spring MVC和Java基础。版本沿用JDK21（实测21.0.9）、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9、Node.js22.15.0、npm10.9.2、Vite8.2.2和Playwright1.63.0。JSON序列化使用Boot管理的Jackson 3 `tools.jackson`命名空间，具体解析版本与运行环境见[版本基线](../01-版本基线.md)及[验证记录](01-验证记录.md)。

### 完成版和跟写版

完成版位于当前课目录，包含后端、完整 `frontend/`、测试及验证记录。直接运行时，在本课目录执行 `mvn clean verify`、`mvn spring-boot:run`，另开终端在 `frontend/`执行 `npm ci`、`npm run dev`，打开 `http://127.0.0.1:5173`。

首次跟写从第二课完成版开始，先复制主源码和前端骨架，不复制最终测试，以便观察中间状态。在**仓库根目录**执行以下命令；目标若已存在，应使用新的空目录，避免覆盖个人进度：

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-003-api-authentication-errors
mkdir -p "$lesson_work/src" "$lesson_work/frontend/src"
cp "$course_module/pom.xml" work/security-followalong/pom.xml
cp "$course_module/21-04-002-explicit-security-filter-chain/pom.xml" "$lesson_work/pom.xml"
cp -R "$course_module/21-04-002-explicit-security-filter-chain/src/main" "$lesson_work/src/"
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/"{index.html,package.json,package-lock.json,vite.config.js,playwright.config.js,.nvmrc,.gitignore} "$lesson_work/frontend/"
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/src/"{main.js,style.css} "$lesson_work/frontend/src/"
cd "$lesson_work"
mv src/main/java/cn/ningbingjian/learnjava/security/lesson002 src/main/java/cn/ningbingjian/learnjava/security/lesson003
for lesson_file in pom.xml src/main/java/cn/ningbingjian/learnjava/security/lesson003/*.java src/main/resources/application.properties frontend/package*.json; do
  sed -e 's/lesson002/lesson003/g' -e 's/21-04-002 Explicit Security Filter Chain/21-04-003 API Authentication Errors/g' "$lesson_file" > "$lesson_file.tmp"
  mv "$lesson_file.tmp" "$lesson_file"
done
```

这里只改变项目和包名，安全规则仍是第二课的公开GET与认证兜底，前端也暂时仍显示第二课。父POM用于版本继承；跟写命令在单课目录执行，不从这个只复制部分课程的临时父目录运行整个聚合工程。复制和临时文件替换命令面向macOS/Linux及Git Bash，本次实际验证为macOS。

后文文件路径均相对**跟写单课目录**，Java文件均在 `src/main/java/cn/ningbingjian/learnjava/security/lesson003/`。终端A运行后端，终端B在跟写工程的 `frontend/`运行前端，终端C发送请求。Java或配置改变后先停止旧进程，再执行 `mvn spring-boot:run`；本例没有热重载。前两课服务需要先停止，以免与本课的8080、5173端口冲突。

## 1. 先把HTTP状态与错误内容的职责说清楚

### 1.1 理论：错误契约不应抹掉失败状态

HTTP状态告诉客户端请求总体结果；响应体中的稳定代码用于识别错误类型，可读消息用于说明下一步。不能把所有结果都变成HTTP200再在JSON里写“失败”，否则代理、监控以及前端的 `response.ok`会得到相互矛盾的信息。

本课定义两个错误，分别观察安全链与MVC的处理位置：

| 条件 | HTTP状态 | `code` | `message` |
| --- | --- | --- | --- |
| 明确请求JSON但未通过认证 | 401 | AUTHENTICATION_REQUIRED | 未通过身份认证，请检查凭据后重试。 |
| 公开信息的lang参数不是en或zh | 400 | UNSUPPORTED_LANGUAGE | lang只支持en或zh，请修改参数后重试。 |

这不是所有异常的通用包装协议。404、其他业务异常及未来的权限不足各有自己的语义；本课只统一这两种已定义情况。认证失败不向外暴露用户名是否存在、异常类、密码或请求头，匿名和错误凭据使用同一说明；消息由服务端固定生成，不拼接用户输入。

先实现MVC参数错误作为对照。它必须真的进入业务方法，再由MVC异常处理器处理。随后如果匿名 `/hello`仍是空体401，就能证明“已有一个可以返回JSON的MVC处理器”不足以覆盖安全阶段。

### 1.2 实操：增加一个可恢复的业务参数错误

新增 `ApiError.java`，用record定义只有两个字段的数据对象。record生成构造器和字段访问方法，后续由JSON序列化器写成对应属性：

```java
package cn.ningbingjian.learnjava.security.lesson003;

public record ApiError(String code, String message) {
}
```

新增 `UnsupportedLanguageException.java`。这个专用类型只表示公开信息的语言参数不受支持；不拿通用异常类型捕获所有问题：

```java
package cn.ningbingjian.learnjava.security.lesson003;

public class UnsupportedLanguageException extends RuntimeException {
}
```

替换 `PublicInfoController.java`。沿用第二课的路径和默认英文响应，仅增加可选语言参数：没有 `lang`时默认en，zh时返回中文，其他值触发明确的业务异常。日志位于参数判断之前，用来证明失败请求确实进入了方法：

```java
package cn.ningbingjian.learnjava.security.lesson003;

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
```

新增 `MvcExceptionHandler.java`。`@RestControllerAdvice`让MVC应用此异常处理器；`@ExceptionHandler`限定它处理的类型，`ResponseEntity.badRequest()`同时设置400和响应体，JSON由MVC消息转换组件写出：

```java
package cn.ningbingjian.learnjava.security.lesson003;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MvcExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MvcExceptionHandler.class);

    @ExceptionHandler(UnsupportedLanguageException.class)
    ResponseEntity<ApiError> unsupportedLanguage() {
        log.info("MVC_ERROR_HANDLED");
        return ResponseEntity.badRequest().body(
                new ApiError("UNSUPPORTED_LANGUAGE", "lang只支持en或zh，请修改参数后重试。"));
    }
}
```

这一步先不改安全链。启动或重启后端，在终端C执行：

```bash
curl -i -H 'Accept: application/json' 'http://127.0.0.1:8080/public/info?lang=unknown'
curl -i -H 'Accept: application/json' 'http://127.0.0.1:8080/public/info?lang=zh'
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

先预测再核对：第一个请求是公开GET，进入Controller后抛出专用异常，因此得到400及 `UNSUPPORTED_LANGUAGE`，同时新增 `PUBLIC_INFO_HANDLER_REACHED`和 `MVC_ERROR_HANDLED`；第二个把参数修正为zh，得到200及中文信息；第三个仍是401，尚未出现本课的认证错误JSON，也不会执行问候方法。

MVC处理器覆盖的是MVC调度中的相应异常，不能因为名字含Advice就认为它包围了所有Servlet过滤器。此分工见[Spring MVC异常处理说明](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)。

### 1.3 小总结

统一数据结构与统一处理位置不是一回事。业务错误已经能返回400 JSON，但认证阶段还没有接入这个协议；HTTP状态、固定错误类型和方法日志共同界定了目前处理到了哪里。

## 2. 在安全链的认证入口写出401 JSON

### 2.1 理论：认证入口是失败响应的处理点，不是登录Controller

`AuthenticationEntryPoint`负责在需要启动认证时处理请求和响应。它可以发出挑战、跳转登录地址，也可以返回API错误JSON；它本身不查询用户或比较密码。

匿名访问受保护资源时，授权要求不能满足，安全链的异常转换机制会启动认证入口。携带错误Basic凭据时，Basic认证过滤器在认证失败分支调用自己配置的入口。二者都能返回401，但不是同一个异常从同一位置冒出来；后面要把两个入口接到同一响应策略。[Spring Security的Basic说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/basic.html)分别描述了这两条路径。

在入口里写响应不经过MVC的 `ResponseEntity`转换，因此要明确设置状态、类型和编码，并使用JSON序列化器写出对象。这里使用 `setStatus(401)`直接完成本课响应，不调用 `sendError`要求容器再走错误页面处理，也不重定向到业务Controller补写JSON。

### 2.2 实操：实现固定的API认证错误响应

新增 `ApiAuthenticationEntryPoint.java`：

```java
package cn.ningbingjian.learnjava.security.lesson003;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);
    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.info("API_AUTHENTICATION_REQUIRED");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        jsonMapper.writeValue(response.getOutputStream(), new ApiError(
                "AUTHENTICATION_REQUIRED", "未通过身份认证，请检查凭据后重试。"));
    }
}
```

`@Component`将入口交给容器管理，构造器注入Boot配置的Jackson 3 `JsonMapper`；它是实际依赖中的序列化组件，不是照搬旧版本的 `com.fasterxml.jackson.databind.ObjectMapper`包名。`commence`的请求、响应和认证异常由安全组件调用时传入，方法没有返回Controller业务对象。

响应按顺序设置401、UTF-8和 `application/json`，再保留本课Basic机制需要的 `WWW-Authenticate`挑战，使用 `Cache-Control: no-store`避免缓存这份身份相关错误。最后让序列化器处理字符串转义和JSON编码，不能手工拼接用户输入。异常参数故意不写入对外响应，日志也仅记录固定标记 `API_AUTHENTICATION_REQUIRED`。

目标响应的稳定摘要为：

```text
HTTP/1.1 401
Content-Type: application/json;charset=UTF-8
WWW-Authenticate: Basic realm="Realm"
Cache-Control: no-store

{"code":"AUTHENTICATION_REQUIRED","message":"未通过身份认证，请检查凭据后重试。"}
```

字段顺序和头部大小写不作为客户端判断依据。仅创建这个类不会自动替换现有安全入口；当前还需要下一节把它接入安全链。不能在尚未接入时就把目标响应写成当前已生效的结果。

### 2.3 小总结

认证入口负责把安全阶段的失败转换为客户端可理解的响应。它与MVC处理器可以使用相同的 `ApiError`类型，但调用者和写响应方式不同；真正生效还取决于配置是否把请求路由到它。

## 3. 按请求选择入口，同时保留可访问的登录页

### 3.1 理论：响应形式与访问权限是两个维度

同一个 `/hello`需要认证，无论调用者希望JSON还是HTML，这条访问规则都不改变。Accept只是帮助选择如何说明“需要认证”，不是通过修改请求头绕过认证。

本课用 `MediaTypeRequestMatcher`匹配请求Accept中的媒体类型，忽略笼统的 `*/*`，以免把浏览器的通配接受声明也当成明确的JSON需求。`DelegatingAuthenticationEntryPoint`按注册顺序选择匹配入口：JSON在前、HTML在后，其余使用Basic挑战。前端固定发送 `Accept: application/json`；HTML导航按浏览器请求处理。

| 请求Accept条件 | 本课入口策略 |
| --- | --- |
| application/json | 自定义401 JSON |
| text/html，或普通浏览器含HTML与通配的导航头 | 跳转/login |
| 同时包含application/json与text/html | 按本课显式顺序选择JSON |
| 未提供Accept、仅*/*或未匹配类型 | Basic 401后备入口 |

这是请求匹配策略，不是完整的媒体质量权重协商算法。这里不按q权重寻找所有类型中的最优响应，也不把Accept解释为“这是一个可信用户”。媒体匹配和入口选择的实现依据分别见[MediaTypeRequestMatcher 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/util/matcher/MediaTypeRequestMatcher.java)及[DelegatingAuthenticationEntryPoint 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/DelegatingAuthenticationEntryPoint.java)。

### 3.2 实操：把两个失败位置接到同一选择策略

替换 `SecurityConfig.java`，公开GET、认证兜底和表单登录继续保留，主要新增媒体匹配、入口选择和两个接入点：

```java
package cn.ningbingjian.learnjava.security.lesson003;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiAuthenticationEntryPoint apiEntryPoint)
            throws Exception {
        var jsonRequest = new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
        jsonRequest.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        var htmlRequest = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlRequest.setIgnoredMediaTypes(Set.of(MediaType.ALL));

        var basicEntryPoint = new BasicAuthenticationEntryPoint();
        basicEntryPoint.setRealmName("Realm");
        basicEntryPoint.afterPropertiesSet();
        AuthenticationEntryPoint entryPoint = DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(apiEntryPoint, jsonRequest)
                .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), htmlRequest)
                .defaultEntryPoint(basicEntryPoint)
                .build();

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/public/info").permitAll()
                .anyRequest().authenticated());
        http.formLogin(Customizer.withDefaults());
        // 缺少身份与Basic凭据失败来自不同调用位置，但使用相同的响应选择策略。
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE));
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
```

先看选择器的构造。`jsonRequest`与 `htmlRequest`分别匹配媒体类型，`setIgnoredMediaTypes`排除通配误匹配。后备 `BasicAuthenticationEntryPoint`设置Realm并初始化；它负责没有明确JSON或HTML匹配时的挑战响应。构建器按添加顺序保存策略，`defaultEntryPoint`明确提供最后的退路。这里使用7.0版本的构建器，没有使用已标记移除的旧Map构造方式。

再看两个接入点。`defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE)`向异常处理配置注册一个适用于所有请求的入口选择器；选择器内部才决定JSON、HTML或后备响应。`httpBasic(...authenticationEntryPoint(entryPoint))`让Basic凭据失败时也使用相同策略。前者处理未认证访问受保护资源等入口选择，后者处理Basic过滤器自己的失败路径。本课将两处都显式接到相同策略，让各自在失败发生处完成协议；不能依赖某个默认错误分派偶然生成相同结果。

这里特意使用“按请求注册入口”，没有调用异常配置的全局 `authenticationEntryPoint(...)`设置方法。在本基线中，[DefaultLoginPageConfigurer](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/web/configurers/DefaultLoginPageConfigurer.java)的默认登录页生成条件会检查该全局设置；直接设置可能导致自动登录页不再加入链。保留 `formLogin`这一行本身不够，必须实际检查 `/login`能够打开，不能只测到一个302就宣布登录入口正常。

停止并重启后端，在终端C核对：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/hello
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/login
curl -i -H 'Accept: */*' http://127.0.0.1:8080/hello
```

第一条实际返回401与认证JSON。第二条交互输入错误密码，也得到同一401 JSON；再次执行并输入当前启动日志里的正确随机密码后恢复200。密码不写入命令，不把启动日志中的秘密提交或贴进页面。

第三条返回302且Location路径为 `/login`；第四条实际200并包含登录表单与CSRF隐藏字段；最后一条为Basic401后备响应，不必符合本课JSON结构。浏览器地址栏直接打开 `http://127.0.0.1:8080/hello`会进入登录页，本课只验证入口可访问，不提交登录表单。

同样可以在受保护路径发送格式错误的Basic或未知用户名，本课都返回统一认证契约，不暴露具体账户判断。公开路径若主动携带错误Basic，也可能在公开授权规则之前认证失败，保持第二课已经观察到的处理边界。CSRF仍未关闭，所有本课业务请求都是GET。

### 3.3 小总结

入口策略决定怎样返回认证失败，授权规则决定请求能否通过。必须同时验证匿名和错误凭据两个来源，并确认HTML跳转的目标真正可用；保留真实状态与协议挑战是错误JSON之外的一部分契约。

## 4. 前端显示错误信息，但始终保留原始响应

### 4.1 理论：解析失败与请求失败不是同一种情况

前端已经收到401响应时，`fetch`通常不会因为状态码而抛异常。此时应先展示真实状态和响应原文，再尝试识别错误结构。若响应不是JSON、JSON语法错误或错误代码与状态不符，说明协议不能被当前页面识别，不等于浏览器没有收到HTTP响应。

因此本课只在Content-Type为 `application/json`、代码为已知类型、状态与代码对应、message为非空字符串时使用错误摘要。原始响应始终保留，通过 `textContent`展示；无法识别时给出降级提示，不把解析错误交给外层网络错误分支。

“重试本次请求”也不意味着问题已经解决。它只再次发送相同路径的GET：未提供新凭据时401仍是401，非法参数不修正时400仍是400。这个显式按钮让用户检查条件后再操作，不自动循环请求，更不自动重放未来的危险写操作。

### 4.2 实操：增加参数错误对照、错误代码和重试入口

在**仓库根目录**另开终端，复制第三课的页面与样式骨架到跟写工程：

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-003-api-authentication-errors
cp "$course_module/21-04-003-api-authentication-errors/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$course_module/21-04-003-api-authentication-errors/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
```

页面保留公开和受保护两个按钮，新增“查看参数错误对照”，其固定路径是 `/api/public/info?lang=unknown`。结果区新增 `error-code`，以及初始隐藏的 `retry-request`按钮。复制后的几个关键节点如下，布局代码作为骨架提供：

```html
<button id="send-invalid" type="button" data-path="/api/public/info?lang=unknown">查看参数错误对照</button>
<dd id="error-code">—</dd>
<button id="retry-request" type="button" data-path="" hidden>重试本次请求</button>
```

回到**跟写单课目录**，替换 `frontend/src/main.js`。本课核心增量是局部解析函数、错误摘要分支和保存重试路径，第二课的原始状态、匿名条件及代理故障处理继续使用：

```javascript
import './style.css';

const buttons = [...document.querySelectorAll('button[data-path]')];
const requestPath = document.querySelector('#request-path');
const panel = document.querySelector('.result-panel');
const code = document.querySelector('#status-code');
const summary = document.querySelector('#result-summary');
const source = document.querySelector('#response-source');
const body = document.querySelector('#response-body');
const errorCode = document.querySelector('#error-code');
const retry = document.querySelector('#retry-request');

function readApiError(text, contentType, status) {
  if (contentType?.split(';')[0].trim().toLowerCase() !== 'application/json') return null;
  try {
    const value = JSON.parse(text);
    const expectedStatus = { AUTHENTICATION_REQUIRED: 401, UNSUPPORTED_LANGUAGE: 400 };
    if (value && typeof value.code === 'string' && Object.hasOwn(expectedStatus, value.code) && expectedStatus[value.code] === status
        && typeof value.message === 'string' && value.message.trim()) return value;
  } catch {
    // 响应已取得，格式不合法不等于网络失败；原文仍保留在观察区。
  }
  return null;
}

async function sendRequest(button) {
  const label = button.textContent;
  const path = button.dataset.path;
  if (!path) return;
  retry.hidden = true;
  retry.dataset.path = path;
  errorCode.textContent = '—';
  // 共用一个结果区，请求中同时禁用两按钮，避免较早响应覆盖较新操作。
  buttons.forEach((item) => { item.disabled = true; });
  requestPath.textContent = `GET ${path}`;
  button.textContent = '正在请求…';
  panel.setAttribute('aria-busy', 'true');
  code.dataset.state = 'loading';
  code.textContent = '请求中';
  summary.textContent = '等待接口响应…';
  source.textContent = '—';
  body.textContent = '等待响应内容。';

  try {
    const response = await fetch(path, {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      redirect: 'error',
      signal: AbortSignal.timeout(8000),
    });
    const text = await response.text();
    // HTTP 401/502也会得到Response，不能把所有非200都归入catch。
    code.textContent = `HTTP ${response.status}`;
    source.textContent = '后端响应（经开发代理转发）';
    body.textContent = text || '（空响应体）';
    const apiError = readApiError(text, response.headers.get('Content-Type'), response.status);
    retry.hidden = response.ok;
    errorCode.textContent = apiError?.code || '—';

    if (response.status === 502 && response.headers.get('X-Lesson-Proxy-Error') === 'upstream-unavailable') {
      code.dataset.state = 'error';
      source.textContent = '开发代理：未取得后端响应';
      summary.textContent = '无法连接后端。检查后端是否启动，以及代理目标端口是否正确。';
    } else if (response.status === 401) {
      code.dataset.state = 'unauthorized';
      summary.textContent = apiError?.message || '本次请求未通过认证，响应未提供可识别的错误契约。请检查原始响应。';
    } else if (response.status === 400) {
      code.dataset.state = 'error';
      summary.textContent = apiError?.message || '请求参数可能有误，响应未提供可识别的错误契约。请检查原始响应。';
    } else if (response.ok) {
      code.dataset.state = 'success';
      summary.textContent = '请求成功。请对照后端新增的业务日志。';
    } else {
      code.dataset.state = 'error';
      summary.textContent = '收到了其他 HTTP 响应。请根据真实状态和内容定位，不能一律判断为未登录。';
    }
  } catch (error) {
    retry.hidden = false;
    code.dataset.state = 'error';
    code.textContent = '未取得完整响应';
    source.textContent = '浏览器请求失败';
    summary.textContent = error.name === 'TimeoutError'
      ? '请求超过8秒。请检查服务与网络，然后重试。'
      : '请求未完成。请检查网络、前端服务或是否发生了被禁止的重定向。';
    body.textContent = '没有可展示的完整 HTTP 响应。此处不推断认证结果。';
  } finally {
    buttons.forEach((item) => { item.disabled = false; });
    button.textContent = label;
    panel.setAttribute('aria-busy', 'false');
  }
}

buttons.forEach((button) => {
  button.addEventListener('click', () => sendRequest(button));
});
```

`readApiError`先核对媒体类型，再在局部 `try/catch`解析。`Object.hasOwn`只接受状态表中自己的已知代码；代码与HTTP状态对应以后才使用message。这里拒绝未知代码是本课页面的显示策略，不代表服务端永远不能新增错误类型；更新协议时应同步客户端及验证。

请求开始时清空旧错误代码，并把当前路径存进重试按钮的 `data-path`。所有请求按钮共用结果区，执行中统一禁用；失败后显示重试，成功后隐藏。点击重试仍调用相同请求函数，不需要另写一套网络逻辑。`finally`恢复按钮，不改变服务端认证状态。

收到响应后先写原始状态、来源和原文，再识别契约。401与400分别显示认证或参数错误；502仍由代理专用响应头辨认；离线、超时等没有完整响应时才进入外层 `catch`。解析器的局部降级不能冒充这一网络失败分支。

在终端B进入跟写 `frontend/`执行 `npm ci`、`npm run dev`，打开 `http://127.0.0.1:5173`。依次核对：

| 操作 | 页面结果与服务端证据 |
| --- | --- |
| 访问受保护接口 | 原始401、AUTHENTICATION_REQUIRED及固定说明；新增API_AUTHENTICATION_REQUIRED，不新增问候方法或MVC处理器日志 |
| 重试该请求 | 凭据未改变，仍为401；这只是一次新的请求 |
| 查看参数错误对照 | 原始400、UNSUPPORTED_LANGUAGE；公开方法及MVC处理器各有日志 |
| 访问公开信息 | 不携带错误参数，恢复200；旧错误代码清空，重试按钮隐藏 |

浏览器仍只请求5173上的 `/api/...`，Vite去掉前缀后转发到8080。参数会一并转发；后端访问规则与第一、二课一样不包含 `/api`。本课没有用浏览器直连后端来验证CORS。端口需要调整时，在前端目录设置 `LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`并重启代理，后端和终端请求同步改为8081；5173被占用可改用5174。

### 4.3 小总结

前端以原始HTTP状态为依据，结构化错误帮助解释结果，原文保留诊断证据。格式异常、代理失败与认证拒绝不能混为一类；重试只重新执行操作，修复仍取决于凭据、参数或网络条件的实际变化。

## 5. Basic入口只写状态，为什么认证协议不完整？

### 5.1 理论：相同状态码不代表相同失败路径

匿名访问受保护资源时，链中的授权拒绝会启动认证入口；错误Basic则在Basic过滤器的认证尝试中失败，该过滤器直接使用自己的入口。这就是需要分别接入异常处理配置和Basic配置的原因。MVC处理器处理的是另一条业务异常路径，不能为了共用JSON而假装三者来自同一个Controller。

阅读源码时只需要定位本课问题相关的入口与调用条件：[ExceptionTranslationFilter 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/access/ExceptionTranslationFilter.java)中的认证启动，以及[BasicAuthenticationFilter 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/www/BasicAuthenticationFilter.java)的认证失败分支。它们的完整状态传播留在后续源码系列，本课通过配置对照核对两个调用位置的差异。

### 5.2 实操：错误入口只写401，再原位修复

只在**跟写工程**中，把下面的本课配置：

```java
http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
```

临时改成一个只设置状态的回调：

```java
http.httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) ->
        response.setStatus(401)));
```

其他配置不变，停止并重启后端。终端C先请求匿名 `/hello`，再用 `-u user`交互输入错误密码：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

本次实际对照是：匿名请求仍得到401与本课JSON；错误Basic也得到401，却没有 `AUTHENTICATION_REQUIRED`结构。前者仍走已接入的异常处理策略，后者使用我们临时写的回调，只设置401，既没有JSON也没有Basic挑战。这个实验限定于上述明确的错误回调，不把框架默认Basic入口等同于它。只检查状态码，或者只点击匿名页面按钮，都会漏掉这个缺口。

把Basic配置恢复为本课写法，再重启后端；错误密码重新得到401 JSON，正确密码恢复200。随后再检查HTML `/hello`跳转和 `/login`实际200，保证修复没有破坏另一类入口。不要把这段故意错误作为后续课程默认配置。

最后保持页面运行并停止后端，点击受保护按钮应显示代理502，而不是认证错误代码；重启后点击“重试本次请求”恢复401 JSON。页面加载后将浏览器切到Offline会显示“未取得完整响应”，恢复网络后重试公开请求恢复200。故障闭合需要同时恢复服务、网络和正确的入口配置。

### 5.3 小总结

验证失败契约必须覆盖不同的失败来源。对照相同401的不同响应体，能定位协议不完整的入口；修复以后还要回到成功请求和HTML导航，确认完整边界仍然成立。

## 6. 验证协议、执行位置与前端降级

### 6.1 理论：每类证据证明不同的部分

后端真实HTTP测试负责状态、内容类型、错误结构、Basic挑战以及正确凭据行为；浏览器负责真实按钮、匿名请求条件、页面状态、重试、HTML导航及代理和离线恢复。方法日志用于区分安全阶段与MVC阶段，固定标记不记录凭据或输入内容。

异常响应格式需要单独测试，但不能用伪造401来证明真实认证机制工作。因此浏览器联合测试中的正常401、400、502都由真实服务产生，只有格式损坏的降级用例明确注入响应，并在验证记录中单独说明其范围。

### 6.2 实操：复制测试并重建中间状态

正文代码与故障修复完成后，在**仓库根目录**接入本课测试：

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-003-api-authentication-errors
mkdir -p "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson003" "$lesson_work/frontend/scripts" "$lesson_work/frontend/tests/fixtures"
cp "$course_module/21-04-003-api-authentication-errors/src/test/java/cn/ningbingjian/learnjava/security/lesson003/ApiErrorHttpTests.java" "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson003/"
cp "$course_module/21-04-003-api-authentication-errors/frontend/scripts/prepare-e2e.mjs" "$lesson_work/frontend/scripts/"
cp "$course_module/21-04-003-api-authentication-errors/frontend/tests/lesson.spec.js" "$lesson_work/frontend/tests/"
cp "$course_module/21-04-003-api-authentication-errors/frontend/tests/fixtures/BeforeContractSecurityConfig.java.txt" "$lesson_work/frontend/tests/fixtures/"
cd "$lesson_work"
mvn clean verify
```

测试类见[ApiErrorHttpTests.java](src/test/java/cn/ningbingjian/learnjava/security/lesson003/ApiErrorHttpTests.java)。19项测试包括前课公开与保护范围、JSON认证契约、错误/未知/格式损坏凭据、MVC参数错误与修复、HTML和通配Accept、同含JSON/HTML的显式选择策略。测试只在测试上下文固定密码；普通启动仍用默认生成的密码。

在跟写 `frontend/`执行：

```bash
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

普通学习只需开发服务，自动化浏览器验证才需要匹配Chromium。联合脚本会运行19项后端测试并打包完成版，再建立 `.e2e-work/before-contract`对照版：保留MVC异常处理和业务校验，只把安全配置换回第二课的配置。夹具文件来自第二课，仅更换包名，使用 `.java.txt`防止作为完成版Java源文件被扫描编译；脚本明确复制到临时工程的 `SecurityConfig.java`后才生效。

随后8项浏览器测试覆盖：仅MVC处理器的对照、真实401 JSON与重试、真实MVC400及正常参数修复、HTML页面导航、真实后端停止和恢复、离线重试、明确注入的异常格式降级、390像素与键盘操作。测试会检查业务日志和未携带Cookie/Authorization的请求条件。

Java21和Maven3.9.9需可用；必要时用 `JAVA_HOME`和 `MAVEN_CMD`指向本机安装位置。服务使用空闲端口，测试结束关闭进程；构建日志、截图和失败追踪保存在 `.e2e-work/`与 `test-results/`，不提交。实际结果及跟写证据见[验证记录](01-验证记录.md)。

正式模块父POM已加入第三课。从**正式模块目录**执行 `mvn clean verify`可验证前三课；或用 `mvn -pl 21-04-003-api-authentication-errors -am test`只构建本课及父工程。启动应用仍在具体课目录执行，前端 `dist/`也不包含Java后端或Vite开发代理。

### 6.3 小总结

真实凭据测试和浏览器交互分别证明协议与页面行为，源码入口和日志帮助定位执行阶段。格式异常注入只证明降级处理，中间状态对照与原位修复才证明配置改变如何影响真实响应。

## 本课总结

本课为原有公开与受保护接口补上可观察的失败协议：API未认证或Basic失败时，保留HTTP401并返回固定认证错误代码与说明；公开接口参数错误则通过MVC返回400及另一种错误代码。相同数据结构由不同处理位置产生，不能用一个Controller异常处理器代替所有安全组件的失败入口。

请求先经过安全处理。如果身份条件不满足，异常转换机制或Basic失败路径会进入配置的认证入口选择器，再按Accept决定JSON、HTML登录跳转或Basic后备响应。只有进入MVC以后，业务方法抛出的专用参数异常才由MVC处理器转为400。这个先后关系解释了为什么只增加Advice不能改变前面的匿名401，也解释了为什么Basic入口只写状态会出现“匿名失败有JSON、错误密码没有JSON”。

前端继续展示原始状态和原文，在类型、错误代码与状态一致时使用结构化摘要。无法解析的响应不被冒充为网络失败，502与离线也不被当成未认证。重试显式复用本次GET路径，本身不会产生登录身份或修正非法参数。

| 核心条件 | 本课实际结果 | 解释 |
| --- | --- | --- |
| 只有MVC处理器，匿名JSON /hello | 401但没有本课认证结构 | 尚未覆盖安全阶段 |
| 正确配置，匿名或错误Basic的JSON请求 | 401及AUTHENTICATION_REQUIRED | 两处失败入口都接入协议 |
| 正确Basic访问/hello | 200 | 失败协议不替代认证与正常业务 |
| HTML导航访问/hello | 302并可打开200登录页 | 响应选择与页面生成同时验证 |
| 公开接口lang=unknown | 400及UNSUPPORTED_LANGUAGE | 业务方法执行后由MVC处理 |
| 公开接口正常参数 | 200 | 改正输入后恢复正常业务 |
| 缺失/通配Accept | Basic401后备响应 | 不把所有调用都强制包装成JSON |
| 损坏JSON或代码与状态不符 | 保留状态和原文，摘要降级 | 响应格式与网络失败分开 |

这些结论限定于本课版本、请求策略和GET场景。没有建立角色授权、完整CSRF交互或页面登录后的会话恢复，也没有把404和其他业务异常统一包装。应用中建立错误契约时，应先明确错误来源与状态，再接入正确的扩展点，最后验证成功、各类拒绝、HTML入口及故障恢复。

## 理解检查与后续衔接

1. 公开接口参数错误已返回JSON，匿名/hello却仍是空体401，为什么不应继续给MVC处理器添加一个更宽的异常捕获？
2. `AuthenticationEntryPoint`负责核对密码吗？它接收的异常和请求来自哪里？
3. Basic失败入口只写状态后，为什么只用匿名按钮测试可能发现不了问题？
4. 为什么登录导航不能只验证302，还要验证/login为200且有表单？
5. 收到HTTP401但响应体是损坏JSON，应显示网络断开、登录成功还是保留401并降级解释？
6. 同一个参数错误请求直接重试，为什么仍会得到400？恢复200需要改变什么？

<details>
<summary>完成推演后再核对参考要点</summary>

1. 认证失败发生在MVC业务之前；扩大业务异常捕获范围不能自动包围上游过滤器，应配置安全阶段入口。
2. 不负责。它处理启动认证时的响应，由安全链的相应组件调用，认证管理器和提供者负责实际凭据校验。
3. 匿名拒绝与Basic认证失败有不同调用位置，前者配置正确不能证明后者也接入了同一协议。
4. 跳转目标可能不存在或继续被拦截；本版本默认页生成还受到入口配置方式影响。
5. HTTP响应已经收到；保留401及原文，说明结构无法识别，不能把解析错误伪装成网络失败。
6. 重试没有改变参数；需要将lang改为en/zh或使用不带错误参数的公开按钮。

</details>

下一课是[21-04-004 请求匹配顺序与默认拒绝边界](../00-模块学习大纲.md#lesson-004)：在本课响应协议基础上，对照路径、方法、规则顺序与未列入资源的兜底策略，继续验证登录入口不被误拦。
