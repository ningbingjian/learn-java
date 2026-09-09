# 21-04-007 独立前端表单登录与会话建立

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-007) · [上一课](../21-04-006-csrf-before-login/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第六课已经能取得CSRF令牌，并带同一会话Cookie提交POST；但取得令牌仍是匿名状态。本课把第五课的三个用户接到真正的登录表单：前端提交用户名、密码和CSRF令牌，后端完成认证，返回JSON；后续请求只携带会话Cookie，也能访问受保护的`/hello`。

完成后，你应能解释表单参数由哪个过滤器读取、认证结果如何跨请求保留、为什么登录后重新取得CSRF令牌，并区分密码错误、CSRF拒绝和结果无法确认。页面只显示“本次登录结果”，尚不恢复刷新后的当前用户；第008课再增加`/me`与身份展示，第009课处理退出。

沿用[版本基线](../01-版本基线.md)：JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9；Node22.15.0、npm10.9.2、Vite8.2.2。后端仍使用三个`LESSON_*_PASSWORD`环境变量、内存用户与默认CSRF防护，不增加数据库、JWT或前端框架。

## 准备：从第六课建立独立跟写工程

在**仓库根目录**执行macOS/Linux shell命令。来源是仓库第六课完成版，目标存在时停止，避免覆盖自己的练习。暂不复制测试，以便观察默认登录响应。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-006-csrf-before-login"
lesson_work="$PWD/work/security-followalong/21-04-007-form-login-session"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson007" "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson006/lesson007/g' -e 's/21-04-006/21-04-007/g' -e 's/CSRF Before Login/Form Login and Session/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson006/*.java; do
  sed 's/lesson006/lesson007/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson007/$(basename "$file")"
done
sed 's/lesson006/lesson007/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

后面的Java文件都位于跟写`src/main/java/cn/ningbingjian/learnjava/security/lesson007/`。父POM只供继承，构建在**跟写单课目录**进行。先停止前课占用8080/5173的进程。在跟写单课目录执行`bash`，再输入以下命令；密码至少8个字符、UTF-8不超过72字节，不使用个人真实密码。

```bash
read -r -s -p 'member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
read -r -s -p 'support实验密码：' LESSON_SUPPORT_PASSWORD
printf '\n'
read -r -s -p 'admin实验密码：' LESSON_ADMIN_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
mvn clean package
java -jar target/spring-security-lesson007-1.0-SNAPSHOT.jar
```

后端仅监听`127.0.0.1:8080`。后续修改时在这个终端停止、构建、重启，环境变量保留。另开请求终端执行`bash`，用于以下连续的HTTP实验。

## 一、先看现有登录入口已经做了什么

### 理论：返回登录页与处理登录表单是两件事

`formLogin`已经注册表单认证能力。GET `/login`展示框架生成的页面；POST `/login`由`UsernamePasswordAuthenticationFilter`处理，不需要再写同名Controller。它默认读取名为`username`和`password`的请求参数，交给认证管理器；第五课用户服务与密码编码器参与用户名查找和密码核对。

表单编码`application/x-www-form-urlencoded`把字段写在请求体中，`URLSearchParams`负责对空格、中文、`+`、`&`等字符编码。手写`username=...&password=...`容易把密码中的分隔符解释成字段。JSON是另一种请求体格式，默认表单过滤器不会自动把JSON转换为这些请求参数。[官方表单认证说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/form.html)给出了页面、过滤器和处理器的职责。

默认成功处理器通常返回302，让浏览器导航到已保存请求或默认目标。独立前端的fetch需要稳定数据：自动跟随跳转可能最终拿到HTML，禁止跳转则抛出错误；两者都不能据此认定密码错误，因为服务器可能已建立登录状态。

### 实操：保留默认响应，证明302之后会话已可用

在请求bash中输入与后端一致的member实验密码。临时文件仅供本地请求，权限设为仅当前用户；不显示Cookie、令牌或请求体。以下两个函数后面继续复用：`get_csrf`更新同一Cookie容器和令牌变量；`post_login`提交表单，仅输出状态。

```bash
umask 077
login_work=$(mktemp -d)
read -r -s -p '与后端一致的member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD
node -e 'require("node:fs").writeFileSync(process.argv[1], new URLSearchParams({username:"member",password:process.env.LESSON_MEMBER_PASSWORD}).toString())' "$login_work/form.txt"
get_csrf() {
  curl -sS -b "$login_work/cookies.txt" -c "$login_work/cookies.txt" \
    -o "$login_work/token.json" -w '取得令牌HTTP %{http_code}\n' \
    -H 'Accept: application/json' http://127.0.0.1:8080/csrf
  csrf_header=$(node -e 'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).headerName)' "$login_work/token.json")
  csrf_value=$(node -e 'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).token)' "$login_work/token.json")
}
post_login() {
  curl -sS -b "$login_work/cookies.txt" -c "$login_work/cookies.txt" \
    -o "$login_work/login-response.txt" -w '表单登录HTTP %{http_code}\n' \
    -H 'Accept: application/json' -H "$csrf_header: $csrf_value" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-binary @"$login_work/form.txt" http://127.0.0.1:8080/login
}
get_csrf
post_login
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '会话访问HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

结果依次为200、302、200；curl没有使用`-L`跟随跳转。最后一条没有Authorization头，仍能访问`/hello`，说明默认框架已经保存认证。此时需要改变的是响应契约，而不是在Controller里重新实现密码校验。

### 小总结

登录入口、表单参数解析与认证能力已存在。独立前端需要可识别的成功和失败响应，302或fetch跳转异常并不能证明认证失败。

## 二、把登录响应改成明确的JSON契约

### 理论：响应处理器接在认证流程之后

成功处理器（AuthenticationSuccessHandler）决定认证完成后如何响应；失败处理器（AuthenticationFailureHandler）处理本次认证异常。它们不是用户服务，也不替代密码编码器。我们以方法引用把一个组件的方法交给配置，由框架在对应时点调用。

本课契约只包含`code`和`message`。成功200/`LOGIN_SUCCEEDED`，用户名不存在或密码错误统一401/`LOGIN_FAILED`，不回显用户名、密码、异常堆栈或Session标识。登录响应不带跳转Location；失败处理器也不发Basic认证挑战。此前匿名访问`/hello`的`AUTHENTICATION_REQUIRED`契约仍独立存在。

默认成功处理器还负责处理“未登录时保存的目标请求”。我们选择由页面决定下一步，不再重定向，因此只删除这个保存的请求。`HttpSessionRequestCache.removeRequest`移除的是请求缓存属性，不是销毁Session，也不是删除认证。响应设置no-store，避免缓存登录结果。

### 实操：新增处理器，再接入现有安全链

停止后端，在跟写包目录新增`FormLoginHandlers.java`。构造器的`JsonMapper`来自Boot容器，使用当前Jackson3的`tools.jackson`包；`Authentication`参数是认证结果，本课无需把它序列化给前端。日志只记录固定事件标记。

```java
package cn.ningbingjian.learnjava.security.lesson007;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FormLoginHandlers {
    private static final Logger log = LoggerFactory.getLogger(FormLoginHandlers.class);
    private final JsonMapper mapper;
    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public FormLoginHandlers(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        // 框架此前已执行会话策略并保存上下文；这里只收尾响应，不自行创建登录状态。
        requestCache.removeRequest(request, response);
        log.info("FORM_LOGIN_SUCCEEDED");
        write(response, 200, "LOGIN_SUCCEEDED", "本次登录成功，请使用会话访问受保护接口。");
    }

    public void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.info("FORM_LOGIN_FAILED");
        write(response, 401, "LOGIN_FAILED", "本次登录失败，请检查用户名和密码。");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new LoginResponse(code, message));
    }

    record LoginResponse(String code, String message) { }
}
```

然后修改`SecurityConfig.java`：给`securityFilterChain`增加处理器参数，把原来的`formLogin(form -> form.permitAll())`替换为成功和失败方法引用。以下完整文件供核对；其余匹配边界、Basic入口、JSON/HTML认证入口选择和CSRF默认行为均继承第六课。

```java
package cn.ningbingjian.learnjava.security.lesson007;

import java.util.Set;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiAuthenticationEntryPoint apiEntryPoint,
            FormLoginHandlers loginHandlers)
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
                // 仅允许容器内部的错误分派；外部直接请求/error仍受下方规则约束。
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/csrf-probe").permitAll()
                .requestMatchers(HttpMethod.GET, "/public/info").permitAll()
                .requestMatchers("/public/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/hello").authenticated()
                .anyRequest().denyAll());
        http.formLogin(form -> form
                .successHandler(loginHandlers::onSuccess)
                .failureHandler(loginHandlers::onFailure)
                .permitAll());
        // 缺少身份与Basic凭据失败来自不同调用位置，但使用相同的响应选择策略。
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE));
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
```

不要新增POST `/login` Controller，也不要手动创建SecurityContext或关闭CSRF。`permitAll()`继续允许访问表单相关入口，但不能跳过CSRF过滤器。生成的GET `/login`仍能作为诊断页面；它的POST也会使用本课JSON响应，不再自动导航成功页。

重新构建启动同一个jar。服务重启会使之前的内存会话失效，在原请求终端重新获取令牌再提交：

```bash
get_csrf
post_login
node -e 'const r=JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")); console.log(r.code)' "$login_work/login-response.txt"
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '会话访问HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

应得到200、200、`LOGIN_SUCCEEDED`、200。后端增加`FORM_LOGIN_SUCCEEDED`标记。可见JSON替换了跳转，后续会话认证仍有效；正文最后的测试还检查无Location、字段集合和no-store。

### 小总结

处理器只负责本次登录的响应与请求缓存收尾，认证和会话保存仍由框架负责。成功、认证失败、访问接口时未认证是不同的响应场景。

## 三、会话为何能留下来，CSRF令牌为何要重取

### 理论：会话策略和上下文保存先于成功响应

这里有两个具体问题：为什么后续不再发密码仍能访问？为什么刚登录完不能继续使用旧CSRF令牌？针对7.0.7，查看[AbstractAuthenticationProcessingFilter](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.java)的`doFilter`与`successfulAuthentication`：认证成功产生`Authentication`；随后调用会话认证策略；成功流程创建并设置安全上下文，通过`SecurityContextRepository.saveContext`保存，再调用成功处理器。不是处理器输出200才触发保存。

安全上下文（SecurityContext）保存当前认证结果。当前会话配置将它与服务端Session关联，后续带同一会话Cookie的请求从仓库恢复认证。本课没有配置无状态会话，也没有采用一个“只设置线程变量但不保存”的自制登录Controller。

默认会话固定攻击防护在已有Session上改变会话标识，避免登录前已知的标识原样成为登录后的凭据。具体默认策略见[SessionManagementConfigurer 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java)的`createDefaultSessionFixationProtectionStrategy`。标识改变不表示所有会话属性都清空，也不能靠“有Cookie”判断已登录。

另外，[CsrfAuthenticationStrategy 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/csrf/CsrfAuthenticationStrategy.java)在认证时发现已有令牌，会通过仓库清理它，再准备延迟令牌。前端内存里的旧表示没有同步更新。因此，成功之后必须GET `/csrf`，使用当前会话拿到新的有效表示，再做写请求；不能仅比较两次文本是否不同，因为第六课已证明随机掩码也会改变文本。

### 实操：用旧令牌失败，再在同一会话原位恢复

继续使用第二节刚登录成功的终端。`csrf_value`仍是登录前取得的值；Cookie文件已被登录响应更新。先预测：旧令牌POST403，重新获取后POST200；GET `/hello`仍200。

```bash
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '登录前令牌HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $csrf_value" -X POST http://127.0.0.1:8080/csrf-probe
get_csrf
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '新令牌HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $csrf_value" -X POST http://127.0.0.1:8080/csrf-probe
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '会话访问HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

依次403、200、200、200；只有成功探针增加`CSRF_PROBE_HANDLER_REACHED`日志。测试`FormLoginHttpTests`还保存登录前后会话标识并断言不相同，但不输出标识；另用独立Cookie容器验证不能继承这份登录状态。源码中的“策略、保存、处理器”顺序，由这些真实请求现象共同验证。

### 小总结

会话Cookie让后续请求找到已保存的认证；认证成功同时触发会话标识更新与CSRF生命周期处理。登录成功和令牌就绪是两个状态，页面必须分别处理。

## 四、定位表单错误与认证失败的边界

### 理论：403、401与当前会话身份不能混为一谈

带Cookie却缺CSRF令牌时，请求先被CsrfFilter拒绝，尚未进入表单认证。令牌正确但密码错误才进入失败处理器，得到本课401契约。即使JSON里写着正确用户名和密码，默认表单过滤器仍通过`request.getParameter`读取字段，读不到表单参数就无法完成这次登录，见[UsernamePasswordAuthenticationFilter 7.0.7的attemptAuthentication](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/UsernamePasswordAuthenticationFilter.java)。

“本次失败”不等于“已经退出”。在本课配置中，已经登录的会话再次提交错误密码，失败分支会清理当前线程的安全上下文，但不因此执行退出流程、清除之前保存的会话认证；后续请求仍可能恢复已有身份。新匿名会话错误登录后则仍是匿名。当前页面必须描述这次尝试，不能把一个401直接当成全局身份清空指令。

网络异常或不认识的响应也只能表示结果无法确认：请求可能已成功执行而响应丢失。安全的交互是保留不确定提示，允许只读会话验证，重新输入后再由用户决定是否提交，不自动重发密码。

### 实操：只改变请求格式或令牌，再恢复正常提交

沿用第三节会话，先构造相同字段的JSON请求体；文件中仍有实验密码，保持临时权限且不输出内容。第一个请求缺令牌应403；第二个带有效令牌但格式错误应401/`LOGIN_FAILED`。最后回到原有`post_login`函数，恢复表单编码并重新取得令牌。

```bash
node -e 'require("node:fs").writeFileSync(process.argv[1], JSON.stringify({username:"member",password:process.env.LESSON_MEMBER_PASSWORD}))' "$login_work/form.json"
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '缺少令牌HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-binary @"$login_work/form.txt" http://127.0.0.1:8080/login
curl -sS -b "$login_work/cookies.txt" -o "$login_work/login-response.txt" -w '错误JSON格式HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $csrf_value" -H 'Content-Type: application/json' \
  --data-binary @"$login_work/form.json" http://127.0.0.1:8080/login
node -e 'console.log(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).code)' "$login_work/login-response.txt"
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '已有会话仍可访问HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/hello
post_login
get_csrf
```

应得到403、401、`LOGIN_FAILED`、200、200、200。缺令牌不记录`FORM_LOGIN_FAILED`，错误JSON记录一次，恢复表单后记录成功。本段故意错误仅在两条请求中，默认函数和后端规则保持正确。

自动测试还在独立匿名Cookie容器中对照错误密码与不存在用户，断言相同失败JSON，随后`/hello`401；另用已有认证会话验证失败重登后仍可访问。测试包含`+`、`&`、`=`、空格、中文等测试密码字符，验证编码后确实认证成功，避免只测纯字母密码遗漏字段拆分问题。

### 小总结

先检查CSRF与请求格式，再判断本次认证结果。登录失败不是退出操作；响应不明也不是密码错误。修复应回到正确表单和令牌条件，再显式提交。

## 五、在独立前端接上共享令牌与表单

### 理论：一次登录包含提交和令牌重取两个连续动作

浏览器向自身源的`/api/login`发POST，Vite移除`/api`前缀后转到后端`/login`。`credentials: 'same-origin'`让浏览器管理会话Cookie；JavaScript不用读取HttpOnly Cookie。用户名、密码在表单请求体中，CSRF在请求头中，都不写进URL或持久存储。第005课的匿名/Basic观察区仍用`omit`，保留是否使用会话的对照。

登录和第六课探针必须共享一份令牌缓存。若两个文件各留一份旧值，登录后探针会继续发失效令牌；若探针在登录POST和刷新GET之间插队，同样可能失败。本课把同源会话操作放入一个Promise队列，让“登录POST→重取令牌”成为一个连续操作，并以待处理数禁用相关按钮。

页面分别保存本次结果与令牌准备状态。200成功后，即使刷新令牌的GET失败，仍显示登录成功，并禁用需要令牌的提交；只读`/hello`不需要CSRF，仍允许核对会话。初始化失败或无法识别响应时，提示明确恢复方式，不自动重发登录。

### 实操：复制布局，依次迁移请求、探针与登录交互

在**仓库根目录**执行。复制本课布局和样式，仅提供表单与状态节点；保留第六课`main.js`，其末尾已导入`csrf-lab.js`。在三个模块全部写完之前先不启动页面。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-007-form-login-session"
lesson_work="$PWD/work/security-followalong/21-04-007-form-login-session"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$module_source/21-04-006-csrf-before-login/frontend/src/main.js" "$lesson_work/frontend/src/main.js"
```

先新增`frontend/src/session-client.js`。`subscribeSession`只通知busy、ready与状态名称，不把令牌交给界面。`serial`串行执行操作，捕获队列尾部的错误以免一次失败阻断后续恢复；返回给调用方的结果仍能报告错误。`loadCsrf`先清除旧缓存，成功核对第六课三字段契约后才标记就绪。

`login`在调用时用`URLSearchParams`构造请求体，准确编码字段；按状态和`code`共同识别响应，无法识别时保持unknown。最后删除请求体中的字段并重新取得令牌；不承诺擦除JavaScript引擎的所有内存副本。`submitProbe`继续保留第六课三种故意错误，正常入口使用同一份最新令牌。

```javascript
let csrf = null;
let csrfState = 'empty';
let pending = 0;
let queue = Promise.resolve();
const listeners = new Set();

function notify() {
  const state = { busy: pending > 0, ready: csrf !== null, csrfState };
  listeners.forEach((listener) => listener(state));
}

export function subscribeSession(listener) {
  listeners.add(listener);
  notify();
  return () => listeners.delete(listener);
}

function serial(operation) {
  pending += 1;
  notify();
  const result = queue.then(operation);
  queue = result.catch(() => {});
  return result.finally(() => { pending -= 1; notify(); });
}

function request(path, options = {}) {
  return fetch(path, {
    ...options, credentials: options.credentials || 'same-origin',
    redirect: 'error', signal: AbortSignal.timeout(8000),
  });
}

async function loadCsrf() {
  csrf = null;
  csrfState = 'loading';
  notify();
  try {
    const response = await request('/api/csrf', { headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (!response.ok) throw new Error('无法取得令牌');
    const value = await response.json();
    if (value?.headerName !== 'X-CSRF-TOKEN' || value.parameterName !== '_csrf'
        || typeof value.token !== 'string' || !value.token) throw new Error('令牌契约不匹配');
    csrf = value;
    csrfState = 'ready';
  } catch (error) {
    csrfState = 'unavailable';
    throw error;
  } finally { notify(); }
}

function csrfHeaders() {
  if (!csrf) throw new Error('请先重新取得令牌');
  return { Accept: 'application/json', [csrf.headerName]: csrf.token };
}

export function initializeCsrf() { return serial(loadCsrf); }

export function submitProbe(mode) {
  return serial(async () => {
    const headers = { ...csrfHeaders(), 'Content-Type': 'application/json' };
    if (mode === 'missing') delete headers['X-CSRF-TOKEN'];
    if (mode === 'wrong') headers['X-CSRF-TOKEN'] = 'invalid-for-lesson';
    const response = await request('/api/csrf-probe', {
      method: 'POST', headers, body: '{}', credentials: mode === 'no-cookie' ? 'omit' : 'same-origin',
    });
    if (response.status === 403 && mode === 'valid') {
      csrf = null; csrfState = 'expired'; notify();
    }
    return { status: response.status, ok: response.ok, text: await response.text() };
  });
}

export function login(username, password) {
  // 把表单构造在调用时，调用方随即清空输入；不保留自动重试凭据。
  const body = new URLSearchParams({ username, password });
  return serial(async () => {
    let outcome = { kind: 'unknown', status: null };
    try {
      const response = await request('/api/login', {
        method: 'POST', headers: { ...csrfHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' }, body,
      });
      outcome.status = response.status;
      const value = await response.json().catch(() => null);
      if (response.status === 200 && value?.code === 'LOGIN_SUCCEEDED') outcome.kind = 'success';
      else if (response.status === 401 && value?.code === 'LOGIN_FAILED') outcome.kind = 'failure';
      else if (response.status === 403) outcome.kind = 'rejected';
    } catch {
      // 可能已经认证成功但响应丢失，不能直接判定为密码错误。
    } finally {
      body.delete('password'); body.delete('username');
    }
    // 与登录POST放在同一个串行操作中，避免探针抢先使用认证前的令牌。
    const csrfReady = await loadCsrf().then(() => true, () => false);
    return { ...outcome, csrfReady };
  });
}

export function sessionHello() {
  return serial(async () => {
    const response = await request('/api/hello', { headers: { Accept: 'application/json' } });
    return { status: response.status, text: await response.text() };
  });
}
```

接着新增跟写`frontend/src/csrf-lab.js`（功能上替换第六课的同名实现）。它现在只负责按钮与文字，不再独立保存令牌。页面加载调用一次`prepare`，只发GET，不自动提交POST。

```javascript
import { initializeCsrf, submitProbe, subscribeSession } from './session-client.js';

const state = document.querySelector('#csrf-state');
const summary = document.querySelector('#csrf-summary');
const status = document.querySelector('#csrf-http-status');
const responseBody = document.querySelector('#csrf-response');
const initialize = document.querySelector('#csrf-init');
const submitButtons = [...document.querySelectorAll('button[data-csrf-mode]')];

subscribeSession(({ busy, ready, csrfState }) => {
  initialize.disabled = busy;
  submitButtons.forEach(button => { button.disabled = busy || !ready; });
  state.textContent = csrfState === 'ready' ? '令牌已就绪（内容不展示）'
    : csrfState === 'loading' ? '正在准备令牌…'
      : csrfState === 'expired' ? '令牌或会话可能已失效，请重新取得令牌' : '未取得令牌';
});

async function prepare() {
  try {
    await initializeCsrf();
    summary.textContent = '浏览器会在同源请求中携带会话Cookie。现在可以手动提交POST。';
  } catch {
    summary.textContent = '请检查后端与网络，再点击“重新取得令牌”。初始化失败不会自动提交。';
  }
}

initialize.addEventListener('click', prepare);
submitButtons.forEach(button => button.addEventListener('click', async () => {
  status.textContent = 'POST请求中';
  responseBody.textContent = '等待响应。';
  try {
    const response = await submitProbe(button.dataset.csrfMode);
    status.textContent = `HTTP ${response.status}`;
    responseBody.textContent = response.text || '（空响应体）';
    summary.textContent = response.ok
      ? '本次POST通过并进入实验方法，没有修改业务数据；这不表示已经登录。'
      : response.status === 403 ? '本次实验被拒绝。对照令牌与会话Cookie条件，不要把所有403都归因为未登录。'
        : '收到了其他响应，请检查服务状态；不会自动重发POST。';
  } catch {
    status.textContent = '未取得完整响应';
    summary.textContent = '无法确定本次POST是否被处理。先检查服务与网络，不自动重发。';
    responseBody.textContent = '没有可展示的完整HTTP响应。';
  }
}));
prepare();
```

再新增`frontend/src/login.js`。表单`submit`事件同时支持点击和Enter，`preventDefault`接管浏览器默认提交；HTML也声明`method="post"`，避免回退为把密码放到查询串的GET。请求构造后立即清空密码输入，不保存重试凭据；自动填充设置只减少普通留存，不保证所有密码管理器遵循。

`outcome.kind`只描述本次尝试，`csrfReady`决定是否可以再提交。会话按钮调用不带Basic的GET，页面不会把匿名区的401覆盖为登录区结论。这里也不根据选择框宣称“当前用户就是member”，实际身份来源留待第008课。

```javascript
import { login, sessionHello, subscribeSession } from './session-client.js';

const form = document.querySelector('#login-form');
const username = document.querySelector('#login-name');
const password = document.querySelector('#login-password');
const submit = document.querySelector('#login-submit');
const result = document.querySelector('#login-result');
const status = document.querySelector('#login-status');
const hello = document.querySelector('#session-hello');
const sessionResult = document.querySelector('#session-result');

subscribeSession(({ busy, ready }) => {
  submit.disabled = busy || !ready;
  hello.disabled = busy;
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (submit.disabled || !form.reportValidity()) return;
  const pending = login(username.value, password.value);
  password.value = '';
  status.textContent = '提交中';
  result.textContent = '等待登录响应，然后重新取得CSRF令牌…';
  const outcome = await pending;
  status.textContent = outcome.status === null ? '未取得可识别结果' : `HTTP ${outcome.status}`;
  result.textContent = outcome.kind === 'success'
    ? '本次登录成功。请用会话按钮验证后续请求。'
    : outcome.kind === 'failure' ? '本次登录失败，请检查用户名和密码；这不代表已执行退出。'
      : outcome.kind === 'rejected' ? '登录提交被拒绝，请检查令牌与会话条件。'
        : '无法确认本次登录结果。先用会话按钮核对，不自动重发密码。';
  result.textContent += outcome.csrfReady
    ? ' 新CSRF令牌已就绪。'
    : ' 尚未取得新CSRF令牌，请重新获取后再提交；只读会话验证仍可用。';
});

hello.addEventListener('click', async () => {
  sessionResult.textContent = '正在验证会话请求…';
  try {
    const response = await sessionHello();
    sessionResult.textContent = response.status === 200
      ? 'HTTP 200：仅携带会话Cookie，受保护接口访问成功。'
      : response.status === 401 ? 'HTTP 401：本次会话请求未通过认证。'
        : `HTTP ${response.status}：请根据实际请求定位。`;
  } catch {
    sessionResult.textContent = '未取得完整响应，不能据此判断会话是否有效。';
  }
});
```

最后在跟写`frontend/src/main.js`末尾追加：

```javascript
import './login.js';
```

后端保持8080运行，另开终端进入跟写`frontend/`执行：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`，先清除本实验站点Cookie或使用新的无痕窗口，从匿名条件开始操作。以下“查看请求”只检查方法、字段名与头是否存在，不复制或截图实际密码、令牌、Cookie。

| 操作 | 预期和机制 |
| --- | --- |
| 页面加载 | 自动GET取得令牌；本页尚未登录，身份尚未检查 |
| 新会话输入错误密码并提交 | 401/LOGIN_FAILED；密码框清空；会话按钮401 |
| 重新输入正确密码，点击或Enter提交 | POST表单、无查询串和Authorization；200/LOGIN_SUCCEEDED，随后GET新令牌 |
| 点击携带会话访问`/hello` | 200，无Basic凭据，使用浏览器Cookie |
| 正常CSRF探针 | 使用共享的新令牌得到200 |
| 点击旧匿名观察按钮 | 仍401，因为它使用omit；不代表顶部会话退出 |
| 已登录后再次输入错误密码 | 本次401，会话按钮仍可200；没有执行退出 |
| 刷新页面 | 本次结果回到初始提示；Cookie可能仍有效，手动会话按钮可200，身份恢复留待下一课 |

窄屏下可以按Tab与Enter完成登录，结果区不横向溢出。开发代理沿用`server.cors: false`，让同源OPTIONS经过代理；这些证据不表示已完成浏览器跨源Cookie或生产CORS配置。

### 小总结

共享令牌缓存和串行提交使登录后的新令牌真正被后续写请求使用。界面分别描述本次登录、令牌准备和只读会话验证，不用单一“成功/失败”掩盖不同阶段。

## 六、联合验证响应、会话与恢复

### 理论：成功响应和后续请求必须一起验证

仅断言登录返回200，无法证明会话已保存；只观察页面出现成功文字，也无法证明请求真实到达后端。需要联合检查表单格式、CSRF条件、JSON契约、会话标识变化、Cookie后续访问，以及旧令牌拒绝和新令牌恢复。

失败恢复也必须具备真实条件。默认302对照用实际后端认证；令牌刷新网络失败实验保留真实登录POST，只中断随后的GET。这样才能证明页面没有把“登录成功但刷新失败”误报为密码错误，而不是用整套假响应展示预设文案。

### 实操：复制测试并运行完成版验证

在**仓库根目录**执行，测试仅在这一步加入跟写工程。测试自带仅限测试的密码，普通启动仍必须设置实验密码环境变量。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-007-form-login-session"
lesson_work="$PWD/work/security-followalong/21-04-007-form-login-session"
mkdir -p "$lesson_work/src/test" "$lesson_work/frontend/scripts" "$lesson_work/frontend/tests"
cp -R "$lesson_source/src/test/." "$lesson_work/src/test/"
cp "$lesson_source/frontend/playwright.config.js" "$lesson_work/frontend/"
cp "$lesson_source/frontend/scripts/prepare-e2e.mjs" "$lesson_work/frontend/scripts/"
cp -R "$lesson_source/frontend/tests/." "$lesson_work/frontend/tests/"
cd "$lesson_work"
mvn clean verify
cd frontend
npm run build
npx playwright install chromium
npm run test:e2e
```

`FormLoginHttpTests`新增9项真实HTTP测试，覆盖三用户、特殊字符、均一失败、缺失/错误CSRF、错误JSON、会话标识轮换、旧令牌更新、独立Cookie容器与失败重登；与继承测试合计50项。浏览器共16项，覆盖真实表单和前课回归。

浏览器对照工程位于被忽略的`.e2e-work/before-login/`：保留第六课完整用户、CSRF与授权配置，仅恢复默认表单响应，不影响正常源码。测试禁止fetch跳转后得到无法确认提示，但随后会话访问200，重现第一节现象。另一项测试在实际登录200之后中断`/api/csrf`，验证成功状态保留、只读访问可用、手动重新获取后探针恢复；只有这次GET网络故障是注入的，不能把它写成真实后端停机。

浏览器网络追踪留存关闭，截图只保留清空密码后的状态，敏感值不写入报告。浏览器工具本身可检查请求，请勿把包含凭据的Network详情发布到仓库。具体运行结果与边界见[验证记录](01-验证记录.md)。

结束请求实验时，在原请求bash中清理本次临时文件：

```bash
rm -f "$login_work/token.json" "$login_work/cookies.txt" "$login_work/form.txt" "$login_work/form.json" "$login_work/login-response.txt"
rmdir "$login_work"
unset login_work csrf_header csrf_value LESSON_MEMBER_PASSWORD
unset -f get_csrf post_login
exit
```

停止前后端，在后端bash中`unset LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD`再`exit`。临时文件和实际凭据不提交。

### 小总结

真实HTTP证明认证保存与令牌生命周期，浏览器证明请求编码、Cookie使用、结果识别与恢复交互。测试既要覆盖成功，也要覆盖已经成功却没有拿到完整后续响应的情况。

## 本课总结

本课把第六课同会话CSRF准备接入默认表单认证。前端用表单编码主动提交用户名、密码和令牌，CSRF过滤器先检查写请求条件，表单认证过滤器再读取参数并调用现有用户与密码服务。认证成功后，框架执行会话策略、保存安全上下文，最后由自定义处理器输出JSON；后续只带会话Cookie的请求可以恢复认证。

登录后的会话标识发生变化，旧CSRF令牌也不再适用于后续写请求，因此前端把登录与重新获取令牌串行执行，并让探针复用同一份缓存。成功结果和令牌就绪必须分别呈现：刷新令牌失败并不撤销已经完成的认证，只读请求仍能帮助确认会话。未知响应不能直接归因于密码错误，也不能触发自动重发登录。

诊断时依次核对请求方法与编码、Cookie与CSRF、登录JSON契约、后续会话请求。缺令牌403、错误格式或密码401、匿名接口401各有不同发生阶段；已有会话的失败重登不是退出。当前页面只报告尝试结果，不承担权威身份展示、刷新恢复、退出和权限控制，这些能力按后续课程继续完善。

## 理解检查与后续衔接

1. 为什么`fetch`因302抛错之后，`/hello`仍可能200？成功处理器之前已经发生了什么？
2. 为什么JSON中字段看起来正确仍不能登录？密码包含`&`时手动拼表单会改变什么？
3. 登录后Cookie已更新，为什么旧CSRF令牌仍会403？重新获取改变了哪个条件？
4. 新匿名会话错误登录与已有会话错误重登，后续`/hello`结果为什么不同？
5. 登录POST成功、刷新令牌失败时，哪些操作应该禁用，哪些可以继续？为什么不能自动重发密码？

下一课是[第008课：当前用户接口与刷新后的身份恢复](../00-模块学习大纲.md#lesson-008)。在本课已保存会话的基础上，增加`/me`并恢复刷新后的身份展示，让页面从“本次尝试”进一步认识“当前会话是谁”。
