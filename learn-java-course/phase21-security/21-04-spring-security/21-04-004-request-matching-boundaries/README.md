# 21-04-004 请求匹配顺序与默认拒绝边界

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-004) · [上一课](../21-04-003-api-authentication-errors/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第三课把匿名API请求稳定地变成401与认证错误JSON，把公开接口参数错误交给MVC返回400。但上一课最后一条规则是`anyRequest().authenticated()`：只要身份有效，未单独声明的路径也能继续进入MVC。如果明天新增一个Controller，却忘了补安全规则，它会自动允许所有已认证用户访问。这是本课要收紧的遗漏风险。

本课沿用第三课的全部业务与错误处理，建立“明确允许的入口 + 其余默认拒绝”的边界。完成后，你应能根据**请求方法、后端路径、规则次序和身份条件**预测结果，复现一次错误顺序并修复，解释为什么匿名与已认证请求都被拒绝却分别得到401、403，并确认框架登录入口仍能工作。

没有新增用户、角色、数据库或前端框架。依赖保持[版本基线](../01-版本基线.md)：JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9，前端Node22.15.0、npm10.9.2、Vite8.2.2。运行时继续使用默认`user`与启动时随机生成的密码；它仅用于本机实验，不要复制进源码或提交记录。

## 准备：建立独立跟写目录

完成版在本课目录。首次学习请在仓库根目录执行下面命令，复制**仓库第三课完成版**作为起点，不依赖你之前是否保存过跟写工程。命令使用macOS/Linux shell，创建目标已存在时停止，避免覆盖你的练习。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-003-api-authentication-errors"
lesson_work="$PWD/work/security-followalong/21-04-004-request-matching-boundaries"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson004"
mkdir -p "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson003/lesson004/g' -e 's/21-04-003/21-04-004/g' -e 's/API Authentication Errors/Request Matching Boundaries/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson003/*.java; do
  sed 's/lesson003/lesson004/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson004/$(basename "$file")"
done
sed 's/lesson003/lesson004/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

所有Java文件现在都在`work/security-followalong/21-04-004-request-matching-boundaries/src/main/java/cn/ningbingjian/learnjava/security/lesson004/`。包名与启动扫描一起改为`lesson004`，不会扫描仓库第三课。父POM只供继承版本；跟写时始终在**单课目录**构建，不在跟写父目录跑聚合构建。测试暂不复制，避免完成版断言干扰中间步骤。

## 一、把路径清单变成可检查的边界

### 理论：认证通过不等于每个资源都允许访问

认证回答本次请求代表谁；授权回答这个身份能否执行当前请求。`authenticated()`允许已认证身份通过授权检查；`denyAll()`无论身份是谁都不给予访问许可。两者都作用于命中的请求规则，不能根据接口是否存在自动选择。

匹配器（RequestMatcher）负责判断当前请求是否属于某个范围，授权规则负责判断这个范围能否访问。`requestMatchers(HttpMethod.GET, "/public/info")`要求方法是GET且路径匹配；查询参数`?lang=zh`不属于这个路径条件。安全链看到的是代理去掉`/api`之后的`/public/info`，因此不能把浏览器页面中的`/api/public/info`原样抄到后端规则里。

先写本课的业务边界，而不是先猜框架配置：

| 到达业务授权检查的请求 | 匿名 | 正确Basic身份 | 目的 |
| --- | --- | --- | --- |
| GET /public/info | 允许 | 允许 | 已批准的公开入口 |
| GET /hello | 要求认证 | 允许 | 已批准的认证入口 |
| /public/下的其他方法或路径 | 拒绝 | 拒绝 | 公开特例之外关闭 |
| 其余未列入的业务请求 | 拒绝 | 拒绝 | 新增接口必须显式登记 |

“允许”只代表可以继续处理，不保证MVC一定返回200。例如公开入口的非法语言参数仍会返回400。反过来，安全层先拒绝一个未知路径时，MVC没有机会判断它是否存在，不能期待必然得到404。

### 实操：保留上一课作为对照

在跟写单课目录执行：

```bash
mvn clean package
java -jar target/spring-security-lesson004-1.0-SNAPSHOT.jar
```

另开终端，用`curl -u user`让curl在终端提示输入当前启动日志中的密码。不要把密码写到命令行或历史记录。每次重启密码会变化；这些请求不保存Cookie。

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/missing
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/missing
```

起点依次为200、401、404。第三个请求携带有效身份，上一课兜底授权允许继续，随后MVC找不到资源才返回404。这不能证明“将来遗漏的接口也安全”：如果同路径出现可处理请求的Controller，授权这一步不会阻止它。

### 小总结

先列出允许的**方法与路径**，再决定身份条件。用匿名和已认证两种请求检查遗漏入口，才能发现`authenticated()`兜底与默认拒绝的区别。

## 二、配置默认拒绝，同时保留错误响应

### 理论：只使用第一个匹配结果

`authorizeHttpRequests`按声明顺序检查规则。第一次匹配成功后，就执行对应授权判断；无论判断是允许还是拒绝，都不会继续寻找后面的“更具体”规则。因此，公开特例需要放在覆盖它的`/public/**`之前。末尾`anyRequest()`匹配所有剩余请求，必须放最后。

本课保留`/public/**`这一条，目的是让公开特例与宽范围拒绝产生可观察的重叠；它与最后的`denyAll()`在当前结果上有重复，但能表达公开区域的局部边界。不能把它误改成`permitAll()`，否则未来该区域新增接口也可能直接公开。

另一个必要边界是错误分派。Servlet容器遇到`sendError`等情况时，可能把同一次处理交给错误入口；这次内部处理的分派类型是`ERROR`，也可能经过授权。如果一律默认拒绝，原始错误的呈现可能再次受阻。本课显式允许**内部ERROR分派**，让已有400/403等错误处理保留其状态。它不是允许外部直接访问`/error`：浏览器直接发来的请求是`REQUEST`，仍走下面的路径规则。这里也没有允许全部转发或全部错误路径；内部错误页面只应呈现错误，不能承载敏感业务操作。

### 实操：替换SecurityConfig，其他Java文件保持不变

停止上节后端。在跟写包目录替换`SecurityConfig.java`为下面的完整版本。新增的是授权清单、`DispatcherType.ERROR`导入以及`formLogin(form -> form.permitAll())`；前课的JSON/HTML/Basic响应选择器保持原来的职责。

```java
package cn.ningbingjian.learnjava.security.lesson004;

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
                // 仅允许容器内部的错误分派；外部直接请求/error仍受下方规则约束。
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/public/info").permitAll()
                .requestMatchers("/public/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/hello").authenticated()
                .anyRequest().denyAll());
        http.formLogin(form -> form.permitAll());
        // 缺少身份与Basic凭据失败来自不同调用位置，但使用相同的响应选择策略。
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE));
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
```

`http`由Spring提供来构建当前安全链；`apiEntryPoint`仍是第三课注册的组件。`dispatcherTypeMatchers`先识别容器内部错误处理。业务GET公开特例在前，`/public/**`拒绝在后；GET `/hello`只允许已认证请求；最终兜底拒绝所有剩余请求。路径授权没有关闭Basic、CSRF或安全响应头。

`form.permitAll()`明确把框架表单登录相关入口登记为可访问，具体范围及过滤器端点的边界在第五节验证。不要顺手把前课的`defaultAuthenticationEntryPointFor`改成全局`authenticationEntryPoint`：本基线下这会影响默认登录页面过滤器的安装条件，前课已经验证过这个差异。

重新在跟写单课目录执行`mvn clean package`并启动同一个jar，再执行上一节三条请求。现在依次是200、401、**403**：正确Basic身份仍无法访问`/missing`。额外检查：

```bash
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/error
curl -i -H 'Accept: application/json' 'http://127.0.0.1:8080/public/info?lang=unknown'
```

结果依次为200、403、400。匿名拒绝会交给认证入口，沿用`AUTHENTICATION_REQUIRED`的401 JSON；已认证请求被拒绝则使用默认拒绝访问处理，结果为403。本课没有新定义403业务错误契约，也不把Boot默认错误体当成稳定接口。前端若收到其他错误形状，应保留原始状态与正文。

### 小总结

默认拒绝建立的是明确允许清单。内部错误分派是另一种处理条件，不能用公开`/error`路径代替。401和403的区别取决于认证条件及失败处理路径，不代表后一个请求已经进入业务Controller。

## 三、故意放错顺序，再在原位置修复

### 理论：更具体的模式不会自动获胜

要确认顺序是否真的影响访问，必须使用同一个请求、同一份身份条件，只交换两条会重叠的规则。访问`/hello`不能验证`/public/**`与公开特例的次序，因为它根本不在该范围。

本基线的字符串路径规则由[AbstractRequestMatcherRegistry（7.0.7）](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/web/AbstractRequestMatcherRegistry.java)的`requestMatchers(HttpMethod, String...)`构造`PathPatternRequestMatcher`。不要按旧版本文章把当前默认实现写成AntPathRequestMatcher。

顺序问题的源码入口是[RequestMatcherDelegatingAuthorizationManager（7.0.7）](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/access/intercept/RequestMatcherDelegatingAuthorizationManager.java)的`authorize`：`mappings`保存“匹配器与授权管理器”的有序列表；循环取得`matchResult`，发现匹配后立即返回对应管理器的结果。它没有收集所有匹配项再比较路径精确度。`anyRequest`之后继续追加映射还有构建期约束，不能把它放前面试图兜住后面的规则。

### 实操：只交换相邻的两行

停止后端，把跟写`SecurityConfig.java`中以下两行临时交换为：

```java
                .requestMatchers("/public/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/public/info").permitAll()
```

重新构建、启动。先预测：匿名GET `/public/info`会先命中宽范围拒绝，得401；携带正确Basic的同一GET得403。此时公开Controller的`PUBLIC_INFO_HANDLER_REACHED`日志不会因这两个请求增加。注意错序应用能够正常启动，问题发生在运行时授权选择，不能靠“启动成功”判断配置正确。

用第一节公开请求命令及其`-u user`版本验证。需要在IDE进一步观察时，在上述`authorize`的循环处设断点：检查请求方法与路径，逐项查看`matcher`和`matchResult.isMatch()`；命中`/public/**`后直接返回，没有再执行后面的公开规则。断点是可选阅读办法，本课已用真实HTTP的错序/修复对照验证这一分支结果。

现在在**同一文件原位置修复**：恢复为第二节完整版本中的顺序，先GET公开特例，后`/public/**`拒绝。重新构建启动，匿名与正确Basic的GET `/public/info`都恢复200；GET `/public/info-extra`在两种身份条件下仍分别为401、403。不能为了修复公开接口而删除最后的默认拒绝。

### 小总结

规则次序是执行语义，不只是排版。只有请求同时属于两个范围时，才能看见顺序影响；先匹配到拒绝就结束，并不会回头寻找允许项。后续各节必须使用修复后的配置。

## 四、同路径换方法，并检查近似路径

### 理论：MVC映射与安全匹配是两次判断

路径相同不代表同一安全请求。显式GET规则只匹配GET，不自动包含HEAD或OPTIONS。MVC可以对GET映射提供HEAD能力，但它发生在授权放行之后，不能反向扩展安全规则。本课有意只开放GET，HEAD与OPTIONS均保持拒绝；如果真实业务需要它们，应单独确认需求并添加规则。

使用OPTIONS做对照，是因为默认CSRF保护不要求这个安全方法携带令牌，可以把重点放在授权。POST缺少令牌时可能更早被CSRF过滤器阻止，403不能单独证明`denyAll()`是拒绝原因。CSRF令牌交互安排在第006课，本课保留默认防护。

`/public/info-extra`、`/public/info/private`和`/public/info/`都不是本课精确GET路径。查询参数`lang`则不影响路径匹配，只影响后续业务参数校验。这里按固定版本和默认Servlet映射讨论，不把分号、编码路径或任意代理重写都概括为相同行为。

### 实操：带着身份条件读取响应

后端保持修复版，在终端执行：

```bash
curl -i -X OPTIONS -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -X OPTIONS -u user -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -I -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/public/info-extra
curl -i -H 'Accept: application/json' 'http://127.0.0.1:8080/public/info?lang=zh'
```

稳定结果是401、403、401、403、200。第三条是HEAD，只看响应头与状态，不期待401 JSON正文；HTTP的HEAD响应没有消息体。第五条先通过公开GET规则，再由Controller读取`lang=zh`返回中文信息。

这组请求没有`Origin`或`Access-Control-Request-Method`，因此普通OPTIONS对照不是CORS预检，也不能证明跨域访问已经配置。前端开发代理只是保持本地同源请求。

### 小总结

检查规则至少覆盖正确路径、近似路径、查询参数以及同路径不同方法。先确认请求有没有走到授权阶段，再解释状态；不要拿CSRF拒绝替代方法匹配实验。

## 五、检查默认登录入口没有被破坏

### 理论：业务兜底不是所有过滤器端点的总开关

`anyRequest().denyAll()`约束的是进入这条链的请求授权阶段。默认登录页、登录提交和默认样式由安全过滤器在业务授权之前处理，因此不能声称“一切未在requestMatchers逐条列出的URL都会被兜底拦截”。本课仍是一条覆盖应用请求的安全链，没有用`securityMatcher`把未列出的路径排除出保护。

`formLogin(form -> form.permitAll())`使用框架配置器登记登录页、登录处理URL和失败页的访问许可。默认GET `/login`提供生成页面，POST `/login`提交认证；允许访问不代表省略密码校验或CSRF校验。[AbstractAuthenticationFilterConfigurer（7.0.7）](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/web/configurers/AbstractAuthenticationFilterConfigurer.java)中的`updateAccessDefaults`说明它怎样登记端点许可。

默认页面的GET `/default-ui.css`由[DefaultResourcesFilter（7.0.7）](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/authentication/ui/DefaultResourcesFilter.java)直接写出样式。只保留这些实际启用的框架能力，不需要为此公开整个`/login/**`或全部静态资源目录。默认退出端点也仍属于框架已有能力；退出流程在第009课展开。

### 实操：分别看重定向、页面和资源

```bash
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/hello
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/login
curl -i http://127.0.0.1:8080/default-ui.css
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/login/extra
```

依次应为302跳到`/login`、200登录HTML、200 CSS和401。只检查第一条不够：如果`/login`自身又跳转回`/login`，登录入口实际不可用。浏览器也可以直接打开`http://127.0.0.1:8080/hello`，确认出现有用户名、密码输入框的登录页面以及正常样式。

本课测试额外读取生成页的CSRF隐藏字段，在同一Cookie上下文中提交框架登录表单，再以登录后的Cookie访问GET `/hello`得到200。这是端点兼容性回归，不要求现在实现独立前端登录。直接从`/login`提交后若默认跳到未列入清单的`/`而看到403，也不等于认证失败；成功跳转位置与该目标的授权是两个问题。后续前端登录课程会配置清晰的成功响应和导航。

### 小总结

业务允许清单、框架过滤器提供的端点和错误分派需要分别确认。保留登录不仅是返回一次302，还包括目标页面、资源以及认证提交能工作。

## 六、让独立前端展示路径与方法对照

### 理论：按钮保存请求条件，重试必须原样重发

前三课前端总是GET，路径足以描述请求。本课新增OPTIONS按钮后，重试若只保存路径就会默默变回GET，把拒绝变成成功，导致观察结论错误。需要同时保存方法和路径，并在结果区显示这两个条件。

页面继续使用`credentials: 'omit'`与`Accept: application/json`，因此它观察匿名请求，不会因你在另一个标签页登录就自动带上Cookie。原来的400/401错误识别、代理502、网络故障和原始响应显示继续使用。

### 实操：复制页面骨架，修改请求函数

在**仓库根目录**执行，给已有跟写后端新增独立前端；命令只复制布局、依赖、代理与已有JS起点，随后亲自完成方法改造：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-004-request-matching-boundaries"
lesson_work="$PWD/work/security-followalong/21-04-004-request-matching-boundaries"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$module_source/21-04-003-api-authentication-errors/frontend/src/main.js" "$lesson_work/frontend/src/main.js"
```

本课复制的`vite.config.js`比第三课多一项`server.cors: false`。实测保留Vite默认CORS中间件时，普通OPTIONS会在代理之前被开发服务器返回204，后端认证日志不增加，无法用于本课授权观察。因此在`server`对象的`strictPort: true`之后增加：

```javascript
      // 同源观察台不需要开发服务器CORS；让OPTIONS继续到后端代理。
      cors: false,
```

上述复制命令已经带上这项修复，核对即可。它关闭的是Vite开发服务器的CORS响应中间件，本页与API代理仍是同源；没有关闭Spring Security的任何防护，也没有承诺生产跨域能力。[Vite server.cors文档](https://vite.dev/config/server-options#server-cors)说明该选项；本次还核对了安装的Vite8.2.2中间件顺序并通过真实浏览器验证OPTIONS实际进入后端。如果自己沿用旧代理配置看到204，应先检查响应来自谁，再改安全规则。

新增按钮通过`data-path`指定浏览器路径，OPTIONS按钮另外写`data-method="OPTIONS"`；其他按钮没有该属性，默认为GET。打开跟写`frontend/src/main.js`，在`sendRequest(button)`里做以下五处变更，其余错误分支不改：

1. 读取`path`后增加`const method = button.dataset.method || 'GET';`，把页面属性变成请求方法。
2. 设置`retry.dataset.path`后增加`retry.dataset.method = method;`，避免重试丢失方法。
3. 把结果区的固定GET改成模板字符串`${method} ${path}`。
4. 在`fetch(path, { ... })`参数对象中增加`method,`，否则页面虽然显示OPTIONS，实际仍发送默认GET。
5. 原注释中的“两按钮”改为“所有按钮”，实际继续禁用全部操作，避免共用结果区被并发响应覆盖。

完成版如下，重点核对上述方法传递，而不是重新实现第三课错误处理：

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
  const method = button.dataset.method || 'GET';
  if (!path) return;
  retry.hidden = true;
  retry.dataset.path = path;
  retry.dataset.method = method;
  errorCode.textContent = '—';
  // 共用一个结果区，请求中同时禁用所有按钮，避免较早响应覆盖较新操作。
  buttons.forEach((item) => { item.disabled = true; });
  requestPath.textContent = `${method} ${path}`;
  button.textContent = '正在请求…';
  panel.setAttribute('aria-busy', 'true');
  code.dataset.state = 'loading';
  code.textContent = '请求中';
  summary.textContent = '等待接口响应…';
  source.textContent = '—';
  body.textContent = '等待响应内容。';

  try {
    const response = await fetch(path, {
      method,
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

后端启动在8080；另开终端进入跟写`frontend/`执行：

```bash
npm ci
npm run dev
```

浏览器打开`http://127.0.0.1:5173`，依次操作：

| 页面操作 | 匿名完成版结果 | 要观察的证据 |
| --- | --- | --- |
| 访问公开信息 | 200 | GET /api/public/info，显示业务正文 |
| 访问受保护接口 | 401 | AUTHENTICATION_REQUIRED，未执行hello业务 |
| 查看参数错误对照 | 400 | UNSUPPORTED_LANGUAGE，MVC参数校验 |
| 访问近似路径 | 401 | GET /api/public/info-extra，没有扩大公开范围 |
| 访问未知路径 | 401 | GET /api/missing，安全层先拒绝 |
| 用 OPTIONS 访问公开路径 | 401 | Network与结果区都为OPTIONS |
| 重试本次请求 | 仍为401 | 仍然OPTIONS，不能偷偷变回GET |

把终端里“正确身份请求未知路径得到403”的结果与页面匿名401一起看，才能完整理解默认拒绝。不要仅根据页面没有登录表单，就把这一课解释成只实现了认证拦截。

最后在仓库根目录复制完成版测试与测试准备脚本。它们是课内验收工具，不参与正常应用启动：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-004-request-matching-boundaries"
lesson_work="$PWD/work/security-followalong/21-04-004-request-matching-boundaries"
mkdir -p "$lesson_work/src/test" "$lesson_work/frontend/scripts" "$lesson_work/frontend/tests"
cp -R "$lesson_source/src/test/." "$lesson_work/src/test/"
cp "$lesson_source/frontend/playwright.config.js" "$lesson_work/frontend/"
cp "$lesson_source/frontend/scripts/prepare-e2e.mjs" "$lesson_work/frontend/scripts/"
cp "$lesson_source/frontend/tests/lesson.spec.js" "$lesson_work/frontend/tests/"
cd "$lesson_work"
mvn clean verify
cd frontend
npm run build
npx playwright install chromium
npm run test:e2e
```

联合测试自动用随机端口启动真实后端和Vite，再启动Chromium。它还在隔离的`.e2e-work/wrong-order/`里交换两条规则，证明错序公开401、完成版公开200；不会把错误写回正常工程。完整测试范围、实测结果与限制见[验证记录](01-验证记录.md)。

### 小总结

前端观察区显示的方法必须与网络请求一致，重试必须保留方法与路径。页面负责让证据可见，后端规则仍是最终边界；按钮是否显示不能替代授权。

## 本课总结

本课把第三课“剩余请求通过认证就放行”的兜底改为明确允许清单。一次业务请求先经过认证与防护，进入授权时按次序检查方法、路径等匹配条件，第一次命中后执行对应规则，最终用`denyAll()`拒绝遗漏项。只有被允许继续的请求才有机会让MVC做映射、参数校验与业务处理。

公开GET特例必须在覆盖它的宽范围拒绝之前；路径看起来更具体不会自动提高优先级。查询参数不改变当前路径规则，HEAD和OPTIONS也不会继承显式GET许可。匿名拒绝沿认证入口返回401，已认证身份被兜底拒绝返回403，因此“被拦截”不能直接推断为密码错误或业务已执行。未知资源也可能在MVC返回404之前就被安全层拒绝。

这份业务规则不替代框架端点与内部错误分派的检查：默认登录页、样式和提交在各自过滤器中工作；内部ERROR许可用来保持错误呈现，不能当成公开错误路径。CSRF也仍在授权之前发挥作用，不能用缺令牌POST的失败证明方法授权。

实际新增接口时，先确定方法、路径及身份要求，再登记到允许清单并测试匿名、有效身份、近似路径和错误方法。修改顺序时用重叠请求做对照；修改前端请求时同时核对Network与重试条件。第一篇至此形成可观察、可验证的入口边界，为下一篇建立多用户与登录流程准备稳定起点。

## 理解检查与后续衔接

1. 把`/public/**`拒绝放到GET公开特例前面，为什么应用能启动但匿名公开访问得到401？正确Basic为什么也不能得到200？
2. `?lang=zh`、尾部斜线、OPTIONS分别改变了哪项条件？哪些会进入Controller？
3. 带有效身份访问未知路径为什么从上一课404变成403？未来新增Controller时这个变化有什么意义？
4. 页面显示OPTIONS但Network显示GET，应检查哪一行？第一次OPTIONS失败、重试却200，应检查保存了哪些条件？
5. 允许ERROR分派与公开`/error`路径有什么差别？默认拒绝后GET `/login`仍200，是否说明兜底失效？

下一课是[第005课：显式管理用户与密码编码](../00-模块学习大纲.md#lesson-005)，在本课完成版基础上引入内存用户与密码编码器，建立可重复的多用户实验环境。CSRF令牌交互在第006课、独立前端登录在第007课逐步加入。
