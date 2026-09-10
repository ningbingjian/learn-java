# 21-04-009 安全退出与页面状态收尾

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-009) · [上一课](../21-04-008-current-user-restoration/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第八课已经能查询当前用户并在刷新后恢复身份。现在需要完整结束这份会话：后端清理认证，浏览器不再使用旧会话，页面撤下用户名和历史成功提示，再准备下一次登录需要的匿名CSRF令牌。

只把页面用户名清空不能退出服务器会话；只删Cookie也不能证明旧标识在服务端已经失效。本课使用Spring Security已有POST `/logout`，保留默认清理职责，明确返回204，并从真实请求验证旧Cookie被拒绝、GET不执行退出、重新登录正常，以及网络异常时不误报成功。

前置为第008课。沿用[版本基线](../01-版本基线.md)的JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9，以及Node22.15.0、npm10.9.2、Vite8.2.2；没有新依赖。普通启动仍使用三个实验密码环境变量，用户存储仍在内存；第010课才转入数据库用户。

## 准备：从第八课建立独立跟写工程

在**仓库根目录**执行macOS/Linux shell命令。来源是仓库第八课完成版，目标存在时停止，避免覆盖自己的练习。暂不复制测试，先观察框架现有退出入口。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-008-current-user-restoration"
lesson_work="$PWD/work/security-followalong/21-04-009-logout-and-cleanup"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson009" "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson008/lesson009/g' -e 's/21-04-008/21-04-009/g' -e 's/Current User and Restoration/Logout and Cleanup/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson008/*.java; do
  sed 's/lesson008/lesson009/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson009/$(basename "$file")"
done
sed 's/lesson008/lesson009/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

后面的Java文件都位于跟写`src/main/java/cn/ningbingjian/learnjava/security/lesson009/`。父POM只供继承，构建在**跟写单课目录**进行。先停止前课占用8080/5173的进程。在跟写单课目录执行`bash`，再输入以下命令；密码至少8个字符、UTF-8不超过72字节，不使用个人真实密码。

```bash
read -r -s -p 'member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
read -r -s -p 'support实验密码：' LESSON_SUPPORT_PASSWORD
printf '\n'
read -r -s -p 'admin实验密码：' LESSON_ADMIN_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
mvn clean package
java -jar target/spring-security-lesson009-1.0-SNAPSHOT.jar
```

后端仅监听`127.0.0.1:8080`。后续修改时在这个终端停止、构建、重启，环境变量保留。另开请求终端执行`bash`，用于以下连续的HTTP实验。
## 一、区分退出确认页与真正退出

### 理论：GET展示页面，带CSRF的POST改变会话

启用现有安全配置后，框架已经提供退出入口。保留CSRF防护时，GET `/logout`展示确认页，POST `/logout`才执行退出。GET读取页面不应改变登录状态；不能为了把退出写成超链接而关闭CSRF或放宽退出请求匹配器。

POST需要当前会话对应的令牌。第七课登录后旧令牌失效，所以这里先重新GET `/csrf`，再提交退出。框架的退出过滤器在请求授权过滤器之前处理匹配的退出请求；无需新增一个“允许匿名的退出Controller”或把所有路径放行。默认职责见[官方退出说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/logout.html)。

第八课配置已经具有真实退出能力：当前JSON请求可得到204，HTML请求走默认成功跳转。本课不是从无到有重写退出，而是明确响应契约、增加执行观察、把独立前端的生命周期补完整。204表示处理成功且没有响应正文，前端不能继续调用`response.json()`。

### 实操：先观察第八课已有入口

在请求bash中输入与后端一致的member实验密码。沿用前课表单与令牌函数，新增`get_me`只输出身份接口状态。确认页可能含隐藏令牌，保存临时文件而不输出源码；Cookie和密码同样只留在当前用户可读的临时目录。

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
get_me() {
  curl -sS -b "$login_work/cookies.txt" -o "$login_work/me.json" -w '当前身份HTTP %{http_code}\n' \
    -H 'Accept: application/json' http://127.0.0.1:8080/me
}
get_csrf
post_login
get_csrf
curl -sS -b "$login_work/cookies.txt" -o "$login_work/confirmation.html" -w '退出确认页HTTP %{http_code}\n' \
  -H 'Accept: text/html' http://127.0.0.1:8080/logout
get_me
curl -sS -b "$login_work/cookies.txt" -c "$login_work/cookies.txt" -o /dev/null -w 'HTML退出HTTP %{http_code}\n' \
  -H 'Accept: text/html' -H "$csrf_header: $csrf_value" -X POST http://127.0.0.1:8080/logout
get_me
```

依次为200、200、200、200、200、302、401。GET确认页之后/me仍200；带令牌的HTML POST之后才401。curl不跟随302，所以不会把跳转后的页面误作退出响应。最后已经退出，不再沿用先前的令牌；后面会重新建立实验会话。

### 小总结

退出功能已经在过滤器链里，GET确认页不执行退出，POST需要CSRF。响应格式与真正清理会话是不同职责，本课将其明确接入前端。

## 二、配置清理职责与204响应

### 理论：LogoutHandler做清理，成功处理器决定响应

退出处理器（LogoutHandler）负责清理或参与退出过程；退出成功处理器（LogoutSuccessHandler）在处理器链完成之后决定响应。它们与Controller返回值不同，不需要自己创建MVC接口来触发框架清理。

当前配置使用框架的SecurityContextLogoutHandler：使现有Session失效，清理线程上下文，移除认证，并向上下文仓库保存空上下文。CSRF配置还提供CsrfLogoutHandler清理保存的令牌。CookieClearingLogoutHandler通过响应中的过期Cookie通知浏览器清除当前JSESSIONID；浏览器删除Cookie与服务器会话失效相互配合，不能只验证其中一个。

本课新增的观察处理器只打印固定标记，证明进入退出处理链。它不是全部清理完成的证据，也不自行清Session；是否真正退出，要看204之后的原会话请求。处理器不增加远程调用、凭据日志或故意异常，避免观察逻辑中断清理。

### 实操：增加观察处理器，显式配置现有退出链

停止后端，在跟写包目录新增`LogoutObservationHandler.java`：

```java
package cn.ningbingjian.learnjava.security.lesson009;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class LogoutObservationHandler implements LogoutHandler {
    private static final Logger log = LoggerFactory.getLogger(LogoutObservationHandler.class);

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 仅证明进入退出处理链，不代替框架清理，也不宣称此时全部清理已完成。
        log.info("LOGOUT_HANDLER_REACHED");
    }
}
```

修改`SecurityConfig.java`：给安全链工厂方法增加观察处理器参数，增加HttpStatus与HttpStatusReturningLogoutSuccessHandler导入，在表单登录配置之后增加logout配置。下面提供完整文件核对；新增区域不改变第八课请求边界和CSRF设置。

```java
package cn.ningbingjian.learnjava.security.lesson009;

import java.util.Set;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
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
            FormLoginHandlers loginHandlers, LogoutObservationHandler logoutObserver)
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
                .requestMatchers(HttpMethod.GET, "/me").authenticated()
                .anyRequest().denyAll());
        http.formLogin(form -> form
                .successHandler(loginHandlers::onSuccess)
                .failureHandler(loginHandlers::onFailure)
                .permitAll());
        http.logout(logout -> logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .addLogoutHandler(logoutObserver)
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        // 缺少身份与Basic凭据失败来自不同调用位置，但使用相同的响应选择策略。
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE));
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
```

`invalidateHttpSession(true)`与`clearAuthentication(true)`明确保留框架默认清理意图，`deleteCookies`补充本机路径下Cookie过期指令；`addLogoutHandler`是追加，不替换框架上下文处理器。成功响应明确为204，对JSON和HTML请求一致，不再依赖默认内容协商结果。没有增加LogoutController，也没有手工再次调用一遍上下文退出处理器。

重新构建启动同一个jar。原请求bash重新取得令牌、登录、取得登录后令牌；在退出前备份当前Cookie文件并保留令牌变量，供第三节重放。定义`post_logout`，只输出状态，不解析不存在的JSON正文。

```bash
get_csrf
post_login
get_csrf
cp "$login_work/cookies.txt" "$login_work/old-cookies.txt"
old_csrf_value=$csrf_value
post_logout() {
  curl -sS -b "$login_work/cookies.txt" -c "$login_work/cookies.txt" -o "$login_work/logout-response.txt" \
    -w '退出HTTP %{http_code}\n' -H 'Accept: application/json' -H "$csrf_header: $csrf_value" \
    -X POST http://127.0.0.1:8080/logout
}
post_logout
```

四个请求依次200、200、200、204。响应文件应为空，后端增加一次`LOGOUT_HANDLER_REACHED`。测试核对无Location、no-store和已过期的JSESSIONID。本机容器实际返回Expires形式的过期Cookie，不能要求每个容器都输出同样的Max-Age文本；测试按解析后的过期语义判断。

### 小总结

清理链负责终止会话与认证，成功处理器负责204响应。自定义处理器只补充观察，默认清理仍完整保留；HTTP状态与后续请求共同证明结果。

## 三、证明旧会话失效，再准备下一次登录

### 理论：新匿名Session不等于退出失败

退出后再GET `/csrf`会建立新的匿名Session以保存新令牌，所以浏览器可能很快又出现JSESSIONID。这不是退出失败：必须比较旧会话是否仍能恢复认证，以及新会话访问/me是否401，而不是只看有没有Cookie。

为什么旧标识不能继续使用？查看[SecurityContextLogoutHandler 7.0.7的logout](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/logout/SecurityContextLogoutHandler.java)：`request.getSession(false)`只获取现有Session，启用失效选项时调用invalidate；随后清理上下文、清空认证并保存空上下文。[CsrfLogoutHandler.logout](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/csrf/CsrfLogoutHandler.java)则调用令牌仓库`saveToken(null, ...)`清除旧令牌。

这些处理由[LogoutFilter.doFilter](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/logout/LogoutFilter.java)串接：匹配退出请求，取当前Authentication，调用组合处理器，再调用成功处理器并返回，不继续到Controller。具体POST匹配与默认处理器追加顺序可在[LogoutConfigurer 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/web/configurers/LogoutConfigurer.java)的`createLogoutRequestMatcher`与`createLogoutFilter`核对。观察处理器的日志出现时，后面的框架清理未必已经完成，因此日志名称没有写成“全部退出完成”。

### 实操：重放旧Cookie被拒绝，新令牌可用于重新登录

继续第二节退出后的请求bash，使用**备份的旧Cookie**访问/me与/hello，再重放退出前的令牌：

```bash
curl -sS -b "$login_work/old-cookies.txt" -o /dev/null -w '旧会话/me HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
curl -sS -b "$login_work/old-cookies.txt" -o /dev/null -w '旧会话/hello HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -sS -b "$login_work/old-cookies.txt" -o /dev/null -w '旧令牌探针HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $old_csrf_value" -X POST http://127.0.0.1:8080/csrf-probe
```

依次401、401、403。旧会话无法恢复身份，旧令牌不能使POST通过；第三条拒绝也不增加探针方法日志。接下来恢复到当前Cookie文件，取得匿名令牌、提交公开探针，再次登录：

```bash
get_csrf
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '新匿名令牌探针HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $csrf_value" -X POST http://127.0.0.1:8080/csrf-probe
get_me
post_login
get_csrf
get_me
```

依次200、200、401、200、200、200。匿名令牌足以通过公开探针，不授予身份；重新登录后/me才恢复200。退出没有删除用户、改变密码或终止另一个浏览器容器的独立会话，这些范围在真实HTTP测试中分别验证。

再做失败与修复对照。当前已重新登录且令牌有效；GET不退出、缺少或错误令牌的POST403也不能退出，最后恢复正常POST。

```bash
curl -sS -b "$login_work/cookies.txt" -o "$login_work/confirmation.html" -w 'GET仍只确认HTTP %{http_code}\n' \
  -H 'Accept: text/html' http://127.0.0.1:8080/logout
get_me
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '缺令牌退出HTTP %{http_code}\n' \
  -H 'Accept: application/json' -X POST http://127.0.0.1:8080/logout
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '错令牌退出HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: invalid-for-lesson" -X POST http://127.0.0.1:8080/logout
get_me
post_logout
get_csrf
get_me
```

依次200、200、403、403、200、204、200、401。错误发生在CSRF过滤器，未进入退出处理链；原有会话仍有效。修复只恢复同会话合法令牌，不关闭防护，也不把GET改成退出动作。

### 小总结

旧Cookie失效与新匿名令牌就绪可以同时成立。退出清理当前会话，不删除用户；失败实验需要恢复正确令牌再显式提交，不能靠清页面文字假装退出。

## 四、前端区分退出结果与令牌恢复

### 理论：204、403与结果未知需要不同收尾

退出204是已确认的结果，立即清空身份、权限和旧CSRF缓存；随后GET `/csrf`为下一次登录准备匿名令牌。若GET失败，仍保留“已退出”结论，只禁用需要令牌的提交，允许手动重新获取。

退出403没有执行清理，不能直接显示未登录。网络异常更不明确：请求可能根本没到服务器，也可能已经执行而响应丢失。因此未知或拒绝时先重新准备令牌，再请求/me核对实际会话；不自动重发POST。若查询也失败，沿用第八课unavailable状态并撤下旧字段。

这一过程必须复用现有串行队列，避免登录或探针插在退出与令牌重取之间。新函数定义在原共享模块中，可以访问私有的身份与令牌状态；不另外维护一套退出专用缓存。确认204后不必强制等待/me才能清理本地身份，后续手动查询与刷新会继续向后端核对。

### 实操：复制布局并给共享模块追加退出操作

在**仓库根目录**执行，复制本课布局与第八课完整请求交互。布局只新增退出按钮和状态区；先完成本节及第五节，再启动页面。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-009-logout-and-cleanup"
previous="$module_source/21-04-008-current-user-restoration"
lesson_work="$PWD/work/security-followalong/21-04-009-logout-and-cleanup"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$previous/frontend/src/"{main.js,login.js,csrf-lab.js,session-client.js,current-user.js} "$lesson_work/frontend/src/"
```

在跟写`frontend/src/session-client.js`末尾追加以下函数。POST使用同源Cookie与当前CSRF头，不读取204正文；finally清掉旧令牌及展示身份。后续只有在明确204时保留anonymous，否则用loadIdentity重新核对。调用的是内部加载函数，避免队列任务等待自身后面的任务。

```javascript
export function logout() {
  return serial(async () => {
    identity = { phase: 'loading', user: null };
    notify();
    let outcome = { kind: 'unknown', status: null };
    try {
      const response = await request('/api/logout', { method: 'POST', headers: csrfHeaders() });
      outcome.status = response.status;
      if (response.status === 204) outcome.kind = 'success';
      else if (response.status === 403) outcome.kind = 'rejected';
      // 204没有JSON正文；不能调用response.json()把成功误报成解析失败。
    } catch {
      // 请求可能已执行但响应丢失，不自动重发退出。
    } finally {
      csrf = null; csrfState = 'empty';
      identity = { phase: outcome.kind === 'success' ? 'anonymous' : 'unavailable', user: null };
      notify();
    }
    const csrfReady = await loadCsrf().then(() => true, () => false);
    // 已确认204足以清理本地身份；响应不明或被拒绝时，再问/me核对实际会话。
    if (outcome.kind !== 'success') await loadIdentity();
    return { ...outcome, csrfReady };
  });
}
```

`csrfReady`只表示接下来能否提交，并不改写退出结果。403之后重新取得的令牌仍可能属于原已认证会话；成功204之后取得的则是新的匿名会话令牌。状态必须依赖具体分支，不把“GET拿到令牌”一律叫作匿名登录。

### 小总结

退出与令牌恢复是两个阶段。已确认退出不因初始化失败被推翻，拒绝或未知则通过/me核对；同源Cookie、共享缓存与串行队列维持一致的请求条件。

## 五、清理界面并保持可恢复的操作入口

### 理论：清理历史提示不等于宣称退出成功

点击退出先清空尚未提交的两处密码输入，以及旧登录结果、旧会话访问200等容易误解的提示；这些是界面收尾，可以立即执行。但当前身份区何时显示未登录，仍由共享客户端的204或/me401分支决定。被拒绝时/me可能重新显示原用户。

原有匿名/Basic请求区是历史实验结果，仍标为“本次请求结果”，不作为当前身份来源。本课不读取或批量删除无关localStorage，也不声称清空浏览器全部数据。账户选择框可以保留，方便再次输入密码；它不能授予身份。

未登录文案也需要调整：以前这个状态只来自/me401，本课还可以来自退出204，因此不能始终在文案里写“HTTP 401”。退出状态区会显示真实204或403，身份区只描述已确认的身份状态。

### 实操：新增退出交互并调整身份文案

在跟写`frontend/src/`新增`logout.js`。按钮受busy与令牌准备约束；处理结果把退出与令牌恢复分别显示，未知结果不写成“退出失败所以仍登录”，也不写成成功。

```javascript
import { logout, subscribeSession } from './session-client.js';

const button = document.querySelector('#logout-submit');
const result = document.querySelector('#logout-result');
const status = document.querySelector('#logout-status');

subscribeSession(({ busy, ready }) => { button.disabled = busy || !ready; });
button.addEventListener('click', async () => {
  document.querySelector('#login-password').value = '';
  document.querySelector('#identity-password').value = '';
  document.querySelector('#login-status').textContent = '历史结果已清理';
  document.querySelector('#login-result').textContent = '已开始退出核对；当前身份以上方状态为准。';
  document.querySelector('#session-result').textContent = '历史会话访问结果已清理，请重新验证。';
  status.textContent = '正在提交退出';
  result.textContent = '等待退出响应并重新准备CSRF令牌…';
  const outcome = await logout();
  status.textContent = outcome.status === null ? '未取得退出响应' : `HTTP ${outcome.status}`;
  result.textContent = outcome.kind === 'success' ? '本次退出已完成，原会话身份已清理。'
    : outcome.kind === 'rejected' ? '退出被拒绝，不能宣称已退出；当前身份已重新核对。'
      : '无法确认退出响应，未自动重发；当前会话以上方核对结果为准。';
  result.textContent += outcome.csrfReady ? ' CSRF令牌已重新就绪，可手动开始下一次登录。'
    : ' 尚未取得新令牌，请重新取得令牌后再提交；已确认的退出结果不受影响。';
});
```

在跟写`frontend/src/current-user.js`把匿名分支的文字`当前会话未登录（HTTP 401）。`替换为`当前会话未登录。`，其他分支不变。再在跟写`frontend/src/main.js`末尾追加：

```javascript
import './logout.js';
```

后端保持8080运行，另开终端进入跟写`frontend/`：

```bash
npm ci
npm run dev
```

浏览器打开`http://127.0.0.1:5173`，从正确登录member开始，按以下顺序观察。不要截图或发布Network中的实际密码、Cookie和令牌值。

| 操作 | 预期与解释 |
| --- | --- |
| 登录后点击会话访问 | /hello 200，当前用户member |
| 点击退出当前会话 | 带CSRF的POST返回204，无JSON解析错误；身份和历史会话成功提示清理 |
| 自动重新取得令牌 | 新匿名令牌就绪；出现新的Cookie不等于仍登录 |
| 会话按钮与正常探针 | 会话访问401；合法匿名探针200，二者检查不同条件 |
| 重新输入密码登录 | 登录与/me均200，恢复member |
| 使用浏览器离线模式，再点击退出 | 不误报成功；无法确认身份，旧字段撤下；不自动重发POST |
| 恢复网络，重新查询身份 | 若退出请求未到后端，仍返回member |
| 手动取得令牌，再点击退出 | 正常204，完成退出；刷新页面/me仍401 |

额外两种响应边界通过第六节自动化重现：真实退出204后仅中断CSRF GET，保持已退出状态；真实退出已执行后丢弃退出响应，页面保持响应未知，再由真实/me401确认当前匿名。网络失败不能仅凭异常类型断言服务端是否已执行。

### 小总结

页面立即清理输入和历史提示，但身份结论仍来自后端证据。204、令牌失败和退出结果未知分开显示，恢复操作显式进行，不用自动重发掩盖不确定性。

## 六、把退出前、退出后与再次登录连成验证闭环

### 理论：清Cookie的界面测试不足以证明服务器安全边界

完整证据至少包括退出前可访问、带令牌POST成功、旧Cookie重放被拒绝、新匿名令牌可用、再次登录正常。还要确认GET不退出、错误令牌不进入退出链，以及一个会话退出不会意外清除另一个独立会话。

退出不要求“当前一定已登录”才能运行默认清理。匿名会话带合法CSRF提交也可返回204，新令牌准备后再次手动提交仍可成功；这不构成前端自动重试POST的理由。网络异常下重复退出可能操作的是已经变化的会话，应先核对再决定。

### 实操：复制并执行真实HTTP和浏览器测试

在**仓库根目录**执行，为跟写工程加入测试：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-009-logout-and-cleanup"
lesson_work="$PWD/work/security-followalong/21-04-009-logout-and-cleanup"
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

新增`LogoutHttpTests`8项，与继承测试共66项：JSON/HTML一致204、空正文与过期Cookie，缺失/错误CSRF拒绝，GET确认，旧会话与令牌重放，新匿名令牌与再次登录，匿名重复手动退出及独立会话隔离。框架组件没有被测试替身代替。

浏览器新增6项，共28项。真实退出及缺令牌拒绝通过后端日志与后续请求互证；令牌GET中断、退出响应丢弃是明确的故障注入，其中退出POST真实执行。离线场景则验证请求没有进入退出链。另检查密码清空、旧成功提示移除、再次登录、刷新后匿名与390px键盘操作。

继承的`.e2e-work/before-login/`仍用于默认表单响应对照，保留当前用户接口；它没有本课显式退出配置，不作为完成版退出验收目标。正文独立跟写负责验证第八课默认退出与本课新增配置。浏览器trace关闭，截图只显示已清空输入的页面。结果见[验证记录](01-验证记录.md)。

结束请求实验，在原请求bash清理全部临时文件：

```bash
rm -f "$login_work/token.json" "$login_work/cookies.txt" "$login_work/old-cookies.txt" \
  "$login_work/form.txt" "$login_work/login-response.txt" "$login_work/me.json" \
  "$login_work/confirmation.html" "$login_work/logout-response.txt"
rmdir "$login_work"
unset login_work csrf_header csrf_value old_csrf_value LESSON_MEMBER_PASSWORD
unset -f get_csrf post_login get_me post_logout
exit
```

停止前后端，在后端bash中`unset LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD`再`exit`。不提交实际凭据或会话文件。

### 小总结

退出验收不能停在按钮文字或Cookie消失。原会话拒绝、匿名令牌重建和再次登录构成完整闭环；故障注入说明响应边界，真实后端请求证明清理效果。

## 本课总结

本课用已有POST/logout完成登录生命周期的结束：CSRF先验证写请求，LogoutFilter匹配后调用退出处理器链，框架清理会话、认证与令牌，再由成功处理器返回204。GET只展示确认页，自定义观察处理器只证明进入处理链，不能替代清理或后续请求验证。

退出后旧Cookie不能恢复认证，旧令牌也不能继续用于写请求。重新GET/csrf可能创建新的匿名Session，因此有新Cookie不等于仍登录；匿名探针可200而/me仍401，再次输入密码登录后才恢复身份。清理当前会话不会删除用户或自动终止另一个独立会话。

前端把退出响应、当前身份和令牌准备分开处理。已确认204时清理身份，即使后续令牌获取失败也保留退出结论；403或未知响应先核对真实会话，不自动重发。页面清空输入与历史提示，保留明确恢复入口。实际诊断按“请求方法与CSRF→退出处理链→旧会话验证→新令牌和再次登录”检查，而不是仅看一个成功提示。

## 理解检查与后续衔接

1. GET/logout返回200为什么不能证明已退出？CSRF开启时哪个请求才匹配退出过滤器？
2. LogoutHandler和LogoutSuccessHandler分别承担什么？观察日志出现是否等于全部清理完成？
3. 为什么删除浏览器Cookie还需要验证旧Cookie重放？退出后又出现JSESSIONID为什么可能正常？
4. 退出204后新令牌获取失败，应该显示什么、禁用什么？能把退出改判为失败吗？
5. 网络异常时如何区分请求未到达与响应丢失？为什么不自动重发POST？
6. 退出是否删除用户？为什么另一个独立会话仍可访问？

下一课是[第010课：从内存用户到数据库用户](../00-模块学习大纲.md#lesson-010)。保留本课完整登录、身份恢复和退出流程，将用户读取与存储改为数据库实现。
