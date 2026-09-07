# 21-04-002 显式定义公开接口与认证接口

[模块入口](../README.md) · [上一课](../21-04-001-hello-spring-security/README.md) · [本课大纲](../00-模块学习大纲.md#lesson-002) · [版本基线](../01-版本基线.md)

## 本课导入

第一课已经让同一个 `/hello` 请求在不同条件下得到200、401或登录跳转。现在应用要增加一份任何人都能读取的说明，同时继续保护原来的问候接口。仅把方法放进名为 `PublicInfoController` 的类，或者把路径写成 `/public/info`，能让它自动公开吗？

不能。Controller决定请求进入业务以后由谁处理，访问规则决定请求能否走到那里。本课先新增公开说明方法，验证它在默认规则下仍需要认证；再自行定义安全过滤器链（`SecurityFilterChain`），用一条具体规则放行公开GET请求，用兜底规则继续保护其余业务请求。独立前端增加第二个按钮，两次操作都不带身份凭据，从而把结果差异对应到后端规则。

学完应能解释三件事：公开接口如何配置，为什么受保护接口仍然拒绝匿名访问，以及自行定义安全链后为什么还要显式启用表单登录和HTTP Basic。我们也会用实际结果核对默认用户是否仍然存在，避免把“默认链退让”理解为“所有安全自动配置关闭”。

前置为第001课、Java与Spring MVC基础，以及HTTP方法、Accept和基础JavaScript。环境沿用JDK21（实测21.0.9）、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9、Node.js22.15.0、npm10.9.2、Vite8.2.2；版本依据见[版本基线](../01-版本基线.md)。本课只完成两个GET接口，不增加页面登录表单、角色、数据库或工单业务。

### 完成版与跟写版

当前课目录提供完成版：后端 `pom.xml` / `src/`、独立前端 `frontend/` 和[验证记录](01-验证记录.md)。可以直接在本课目录执行 `mvn clean verify`、`mvn spring-boot:run`，另开终端在 `frontend/` 执行 `npm ci`、`npm run dev`，打开 `http://127.0.0.1:5173`。公开按钮应得到200，受保护按钮应得到401。

首次学习建议建立独立跟写目录，从第一课完成版开始。这能保留“Controller已增加、安全链尚未修改”的中间状态，也不会让最终测试提前要求公开接口返回200。先在**仓库根目录**执行；如果目标目录已存在，请使用新的空目录，避免覆盖自己的进度。

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-002-explicit-security-filter-chain
mkdir -p "$lesson_work/src" "$lesson_work/frontend/src"
cp "$course_module/pom.xml" work/security-followalong/pom.xml
cp "$course_module/21-04-001-hello-spring-security/pom.xml" "$lesson_work/pom.xml"
cp -R "$course_module/21-04-001-hello-spring-security/src/main" "$lesson_work/src/"
cp "$course_module/21-04-001-hello-spring-security/frontend/"{index.html,package.json,package-lock.json,vite.config.js,playwright.config.js,.nvmrc,.gitignore} "$lesson_work/frontend/"
cp "$course_module/21-04-001-hello-spring-security/frontend/src/"{main.js,style.css} "$lesson_work/frontend/src/"
cd "$lesson_work"
mv src/main/java/cn/ningbingjian/learnjava/security/lesson001 src/main/java/cn/ningbingjian/learnjava/security/lesson002
for lesson_file in pom.xml src/main/java/cn/ningbingjian/learnjava/security/lesson002/*.java src/main/resources/application.properties frontend/package*.json; do
  sed -e 's/lesson001/lesson002/g' -e 's/21-04-001 Hello Spring Security/21-04-002 Explicit Security Filter Chain/g' "$lesson_file" > "$lesson_file.tmp"
  mv "$lesson_file.tmp" "$lesson_file"
done
```

复制的是第一课源码，随后只调整包名、项目名和前端包名；Java业务逻辑和安全依赖不变，前端暂时仍显示第一课的观察页。`sed`通过临时文件替换，macOS/Linux和Git Bash都能按此方式操作；本次实际验证平台为macOS。`src/test`没有复制，最后再接入本课测试。父POM沿用正式模块的版本管理，但跟写命令都在单课目录运行，不在这个只准备了部分课程的临时父目录运行聚合构建。

此后文件路径均相对**跟写单课目录**。终端A运行后端，终端B进入其 `frontend/` 运行前端，终端C发出请求；`curl`不依赖终端C当前目录。修改Java或配置后，在终端A用 `Ctrl+C` 停止旧进程并重新执行 `mvn spring-boot:run`，本例没有自动热重载。相同端口上只运行一个课的后端和前端。

## 1. 接口名叫“公开”，为什么仍需要认证？

### 1.1 理论：业务映射与访问规则分别生效

Boot在当前依赖和配置条件下提供默认安全链，要求业务请求先完成认证。这个条件作用于请求路径，不取决于Java类名是否含有Public，也不读取业务文案来猜测哪些信息可以公开。请求被拒绝后，Controller还没有机会返回JSON。

因此，新增接口后匿名访问仍应该被拒绝。为了同时确认“方法确实存在”，需要再用正确凭据发出同一路径请求：如果此时返回业务JSON，就能区分访问被保护与业务映射根本不存在。每个Controller记录自己的固定日志标记，方便观察哪个方法实际执行。

### 1.2 实操：先只增加公开说明Controller

新增 `src/main/java/cn/ningbingjian/learnjava/security/lesson002/PublicInfoController.java`，尚不创建安全配置类：

```java
package cn.ningbingjian.learnjava.security.lesson002;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicInfoController {
    private static final Logger log = LoggerFactory.getLogger(PublicInfoController.class);

    @GetMapping("/public/info")
    public Map<String, String> info() {
        log.info("PUBLIC_INFO_HANDLER_REACHED");
        return Map.of("message", "Public information is available without login");
    }
}
```
`@GetMapping`把GET请求映射到 `info()`，`Map.of`的单项结果写成JSON。`PUBLIC_INFO_HANDLER_REACHED`只在方法真正执行时出现，不代表框架已经认可“公开”的含义。本课继续保留第一课的 `HelloController`，其返回内容和 `HELLO_HANDLER_REACHED` 不变。

先预测：匿名GET会被默认链拒绝，正确凭据GET才进入新方法。在终端A启动后端：

```bash
mvn spring-boot:run
```

终端C先发出匿名请求，再发出认证请求：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/public/info
```

第二条命令会交互提示输入密码，使用**当前进程**启动日志中的随机密码；不要把它写进命令、页面或提交文件。重启后应重新读取密码。这里不使用 `-L`，避免自动跟随跳转后只看到最后一次响应。

实际对照的稳定摘要是：匿名请求401，且没有新增公开方法日志；正确凭据请求200，响应为：

```json
{"message":"Public information is available without login"}
```

这时才出现一次 `PUBLIC_INFO_HANDLER_REACHED`。完整日期和其他日志省略。匿名401并不能单独说明路径是否存在，正确认证后的业务内容补全了证据。

### 1.3 小总结

新Controller只提供业务处理方法，没有改变安全规则。先确认同一路径在正确凭据下可执行，才能把接下来的匿名访问变化归因到访问规则，而不是路由修复。

## 2. 用显式安全链表达公开与保护边界

### 2.1 理论：HttpSecurity描述规则，SecurityFilterChain承接请求

`HttpSecurity`是构建安全链的配置对象。应用启动时，我们告诉它哪些请求允许匿名访问、哪些要求认证，以及需要哪些认证入口；`build()`据此创建 `SecurityFilterChain` 对象。随后到来的请求由这条链中的组件处理，配置方法本身不是每次请求都调用的业务方法。

`@Configuration`让Spring发现配置类，`@Bean`把方法返回的安全链交给容器管理。方法参数 `HttpSecurity http`由框架提供，不是浏览器传入的数据。`proxyBeanMethods = false`表示这个配置类不需要代理其Bean方法间调用；本课没有手工调用该方法，只让容器创建链。

规则按声明顺序匹配：先判断“请求方法是GET，路径是 `/public/info`”，匹配时采用 `permitAll()`，不要求先登录；不匹配时走 `anyRequest().authenticated()`，要求可接受的认证身份。`anyRequest()`是最后的兜底规则，应放在具体规则之后。本课的兜底是“需要认证”，更严格的默认拒绝策略与复杂顺序将在第004课展开。[官方请求授权说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html)解释了首条匹配规则的行为。

“选择哪条安全链”与“链内如何授权”也不同。我们本课只定义一条链，没有用 `securityMatcher`缩小链的范围；`requestMatchers`用于这条链内部的授权匹配。因此，不符合公开规则的请求仍由同一条链保护。若误用 `securityMatcher("/public/**")`把唯一的链缩小到公开路径，其他路径可能完全不经过安全链；它不是本课的公开配置方法。[官方Java配置说明](https://docs.spring.io/spring-security/reference/7.0/servlet/configuration/java.html)说明了链匹配与链内授权的区别。

### 2.2 实操：只放行公开GET，保留两种认证入口

新增同包下的 `SecurityConfig.java`：

```java
package cn.ningbingjian.learnjava.security.lesson002;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                // 匹配后端收到的路径，不包含开发代理使用的 /api 前缀。
                .requestMatchers(HttpMethod.GET, "/public/info").permitAll()
                .anyRequest().authenticated());
        http.formLogin(Customizer.withDefaults());
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```
配置中最关键的是三个相互配合的决定：

| 配置位置 | 在本课中的职责 |
| --- | --- |
| `requestMatchers(HttpMethod.GET, "/public/info").permitAll()` | 只为匹配的公开GET请求取消认证要求 |
| `anyRequest().authenticated()` | 其余请求继续要求身份，不能把公开规则扩成全局放行 |
| `formLogin` 与 `httpBasic` | 显式启用默认表单登录机制及HTTP Basic机制，使先前的认证对照继续成立 |

`Customizer.withDefaults()`给对应机制提供不额外修改参数的配置器；它是在启用该机制，不是恢复Boot的整条默认链。`http.build()`把当前配置构造成链并返回Bean。方法声明的 `throws Exception`承接构建API可能抛出的异常，不是为Controller提供统一异常处理。

此处没有关闭跨站请求伪造防护（CSRF）。本课的读操作使用GET，不需要为了让它成功而关闭写请求保护。`permitAll()`也不等于绕过所有安全过滤器：请求仍可能经过响应头、防护和凭据处理。比如主动发送错误Basic凭据的公开请求，在本基线下也会认证失败；公开访问应使用本课固定的匿名条件验证。

停止并重启后端，在终端C依次执行：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

预测并核对：第一条匿名200，新增公开方法日志；第二条匿名401，没有新增问候方法日志；第三条输入本次启动的正确密码后200，新增问候方法日志。前两条只有路径不同，凭据条件相同，说明公开与保护的差异来自后端规则。第三条则证明保护规则仍允许认证成功的请求继续执行。

公开响应还能看到 `X-Content-Type-Options: nosniff`。这条实际响应头是公开请求仍经过安全处理的一个观察点，不能据此推断所有防护都已验证。POST、会话与CSRF生命周期将在后续专门展开。

### 2.3 小总结

显式安全链同时负责公开范围、其余请求的保护要求和所启用的认证入口。放行某个路径不等于删除安全链，也不等于这个地址的所有方法、近似路径都公开；规则必须与后端收到的真实请求相匹配。

## 3. 默认链退让以后，默认用户去了哪里？

### 3.1 理论：不同的自动配置分别检查自己的条件

第一课依靠Boot提供默认链；当我们声明 `SecurityFilterChain` Bean后，Boot的默认链条件不再满足。Boot不会先创建一条默认链、再把自定义规则拼上去。因此本课在自己的链中明确写出 `formLogin`和 `httpBasic`，保留两种认证入口。

默认用户属于另一项自动配置。在本课依赖下，我们没有定义自己的 `UserDetailsService`、认证管理器或提供者，也没有引入替代身份方案，因此默认内存用户仍然建立。可见，“默认链退让”不能被概括为“关闭所有安全自动配置”。后续第005课会接管用户配置，本课先保留这个控制变量。

按Boot v4.0.8标签核对两个入口即可，无需现在阅读整套源码：

| 源码入口 | 当前需要确认的条件与作用 |
| --- | --- |
| [ServletWebSecurityAutoConfiguration](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/web/servlet/ServletWebSecurityAutoConfiguration.java)及[DefaultWebSecurityCondition](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/web/servlet/DefaultWebSecurityCondition.java) | 默认链条件检查是否缺少SecurityFilterChain Bean；条件不满足时该默认链配置退让 |
| [UserDetailsServiceAutoConfiguration](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.java) | 用户配置有自己的Bean、类路径及应用条件；本课只新增安全链并不让它退让 |

这里确认的是本版本、本依赖组合下的配置分工；不能用这两个结果推断接入OAuth2或自定义认证管理器后的用户初始化行为。

### 3.2 实操：把配置条件、随机用户与HTTP行为对应起来

停止后端后，以条件报告模式重新启动：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--debug
```

在终端搜索 `ServletWebSecurityAutoConfiguration.SecurityFilterChainConfiguration`：它出现在未匹配配置中，原因包含找到了 `securityFilterChain` Bean。再搜索 `UserDetailsServiceAutoConfiguration`：在本课条件下仍匹配，启动也会记录创建默认用户所用的随机密码。日志中的其他自动配置不需要逐项研究；不要将包含密码的整份日志贴入页面或提交。

沿用终端C，继续对照以下请求：

```bash
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/hello
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/login
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

实际结果为302且 `Location`路径是 `/login`、登录页200、输入新密码后的问候接口200。登录页由表单登录机制提供，不是我们新增了一个登录Controller；页面包含用户名、密码和CSRF隐藏字段。本课只检查入口仍可访问，尚未验证浏览器提交表单后的会话保持。

将最后一条命令交互输入的密码换为错误值，再次请求会得到401；重新运行并输入正确值恢复200。这个成功与失败对照结合条件报告，说明默认用户及认证能力没有因自定义链而消失。`Accept: text/html`的跳转与 `Accept: application/json`的401是当前配置和请求条件下的认证入口选择，不能解释为只有一种结果代表“受保护”。

### 3.3 小总结

Boot的默认安全链退让，应用开始使用自己声明的链；默认用户是否创建则由另一组条件决定。把源码条件、实际配置报告和请求结果对应起来，才能准确说明究竟接管了哪一部分能力。

## 4. 两个前端按钮，用相同条件观察不同规则

### 4.1 理论：后端收到的路径与浏览器显示的路径不同

前端仍由Vite在5173提供页面，Java在8080处理业务。浏览器请求同源的 `/api/...`，Vite去掉 `/api`前缀再转发。开发代理改变的是到达后端之前的路径，不负责决定请求能否匿名访问。

| 页面操作 | 浏览器请求 | 后端实际收到 | 本课匿名结果 |
| --- | --- | --- | --- |
| 访问公开信息 | `GET /api/public/info` | `GET /public/info` | 200 |
| 访问受保护接口 | `GET /api/hello` | `GET /hello` | 401 |

因此后端的 `requestMatchers`必须写 `/public/info`。配置 `/api/public/info`不会匹配代理转发后的请求。浏览器只向5173发出同源请求，本课不是浏览器直连8080的CORS实验；这种开发代理也不会自动存在于前端构建出来的静态文件中。

两个按钮使用同一份请求处理逻辑。各自的 `data-path`保存固定请求路径，点击时读取该路径；结果区域另外显示“本次路径”，避免把上一次公开请求的200误认为本次受保护请求的结果。请求仍使用 `credentials: 'omit'`且不设置Authorization，使两种操作都固定为匿名对照。后续使用Cookie会话时再改变凭据策略，本课不先存储伪造的登录状态。

### 4.2 实操：替换页面骨架，再改请求代码

在**仓库根目录**另开终端，复制本课提供的页面与样式骨架到跟写目录：

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-002-explicit-security-filter-chain
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
```

相对第一课，骨架新增公开信息卡片和按钮，为两个按钮分别设置 `data-path`，并在结果区增加 `id="request-path"`。样式只补充卡片间距与长路径换行；布局和无障碍属性沿用第一课。核心HTML关系如下，可对照复制后的文件查找：

```html
<button id="send-public" type="button" data-path="/api/public/info">访问公开信息</button>
<button id="send-request" type="button" data-path="/api/hello">访问受保护接口</button>
<dd id="request-path">—</dd>
```

接下来回到**跟写单课目录**，替换 `frontend/src/main.js`。主要变化是把单按钮监听器提取为 `sendRequest(button)`，从按钮读取路径，再给两个按钮绑定同一个处理函数；第一课的状态、响应来源和故障分支继续保留：

```javascript
import './style.css';

const buttons = [...document.querySelectorAll('button[data-path]')];
const requestPath = document.querySelector('#request-path');
const panel = document.querySelector('.result-panel');
const code = document.querySelector('#status-code');
const summary = document.querySelector('#result-summary');
const source = document.querySelector('#response-source');
const body = document.querySelector('#response-body');

async function sendRequest(button) {
  const label = button.textContent;
  const path = button.dataset.path;
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

    if (response.status === 502 && response.headers.get('X-Lesson-Proxy-Error') === 'upstream-unavailable') {
      code.dataset.state = 'error';
      source.textContent = '开发代理：未取得后端响应';
      summary.textContent = '无法连接后端。检查后端是否启动，以及代理目标端口是否正确。';
    } else if (response.status === 401) {
      code.dataset.state = 'unauthorized';
      summary.textContent = '未通过认证。请查看后端日志，确认这次请求是否进入业务方法。';
    } else if (response.ok) {
      code.dataset.state = 'success';
      summary.textContent = '请求成功。请对照后端新增的业务日志。';
    } else {
      code.dataset.state = 'error';
      summary.textContent = '收到了其他 HTTP 响应。请根据真实状态和内容定位，不能一律判断为未登录。';
    }
  } catch (error) {
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
先看请求开始前：`label`保存当前按钮文字，`path`来自本次点击的按钮。两个按钮共用结果区，因此一次请求进行时同时禁用两个按钮，避免连续操作产生先后顺序相反的响应。结果区清空旧内容，`requestPath.textContent`记录本次目标；结果返回后，`finally`无论成功失败都恢复两个按钮和原来的文字。

再看请求与响应：`fetch(path, ...)`只改变目标路径，JSON Accept、匿名凭据条件、禁止自动重定向及8秒超时都沿用第一课。HTTP401仍会得到 `Response`，需要在状态分支处理；代理连接失败的502有专用响应头标记，不能显示成未登录；浏览器离线等没有完整响应的情况才进入 `catch`。响应通过 `textContent`展示，不执行服务端返回的HTML。

在终端B进入跟写目录的 `frontend/`，执行：

```bash
npm ci
npm run dev
```

打开 `http://127.0.0.1:5173`。在后端采用正确安全链的条件下，先点击“访问公开信息”，应看到路径 `/api/public/info`、HTTP200和真实JSON；再点击“访问受保护接口”，路径变为 `/api/hello`、状态变为401，旧业务JSON被替换。默认401响应体可能为空，本次实测为空，页面显示“（空响应体）”，不能自行编造一个后端并未返回的JSON错误。

在浏览器Network中核对每次真实响应及Accept，再看后端：公开请求增加 `PUBLIC_INFO_HANDLER_REACHED`，匿名受保护请求不增加 `HELLO_HANDLER_REACHED`。页面文字、网络证据和业务执行三者应能对应起来。

后端端口若改为8081，需在前端目录用 `LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`重新启动代理，终端C也改用8081；5173被占用可用 `npm run dev -- --port 5174`并访问对应地址。代理配置仍在 `vite.config.js`，本节无需修改。

### 4.3 小总结

相同的匿名凭据条件消除了身份差异，两个路径的不同结果才有助于观察后端访问规则。前端负责发送与展示，不能用按钮文字决定接口是否公开；路径重写、真实状态和业务日志共同说明请求经过了哪里。

## 5. 规则写了却不生效：在原位置复现和修复

### 5.1 理论：匹配条件必须对应真正进入后端的请求

看到401时不能立即放宽成 `/**`。在本课中，路径层级写错就足以让公开请求落入兜底认证规则；这与Controller不存在、错误密码或后端没启动不是同一种问题。

另外，`/public/info`只是一个具体路径，不代表整个 `/public/`目录公开。查询字符串不属于该路径匹配的路径部分，所以 `/public/info?view=summary`仍命中；`/public/info-extra`和 `/public/info/private`则不会命中这条具体规则。我们保留这些对照来验证边界，而不是只测一次成功请求。

### 5.2 实操：故意混入代理前缀，再只修复这个错误

下面只修改**跟写工程**，完成版维持正确配置。把 `SecurityConfig.java`中的规则临时改成：

```java
.requestMatchers(HttpMethod.GET, "/api/public/info").permitAll()
```

停止并重启后端，前端页面不需要改。再次点击公开按钮，实际会得到401：浏览器显示的路径含 `/api`，Vite转发后已经去掉这个前缀，后端实际路径 `/public/info`与错误规则不匹配，于是使用了兜底认证要求。匿名请求不会新增公开方法日志。

在终端C直接请求后端 `/public/info`并通过 `-u user`输入本次正确密码，可以得到200，说明业务映射本身存在。现在把错误规则改回下面这一行，再重启后端：

```java
.requestMatchers(HttpMethod.GET, "/public/info").permitAll()
```

同一页面公开按钮恢复200；受保护按钮仍为401。修复只改变匹配路径，没有关闭防护或扩大放行范围。继续在终端C验证：

```bash
curl -i -H 'Accept: application/json' 'http://127.0.0.1:8080/public/info?view=summary'
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info-extra
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info/private
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/other
```

本次稳定结果依次为200、401、401、401。后面三条被认证要求拦住，不能用这些401推断Controller是否存在；它们证明的是没有意外落入公开规则。

最后保持前端运行，停止后端，两个按钮都应得到带代理错误标记的502；重新启动正确后端，公开与受保护请求分别恢复200和401。浏览器页面已加载时切到Offline再点击，会显示“未取得完整响应”；恢复网络后可以在同一页重试。故障结束时必须恢复网络、正确规则和运行中的后端，不能把故意错误保留给下一课。

### 5.3 小总结

排查应先核对浏览器路径、代理重写和后端匹配条件，再看原始响应与方法日志。正确的修复要在原位置恢复预期结果，同时保留其他请求的保护边界。

## 6. 把学习结论变成可重复验证

### 6.1 理论：成功请求与拒绝请求共同定义边界

只看到公开200，不能证明 `/hello`仍受保护；只看到401，也不能证明正确凭据还能工作。因此后端验证使用真实端口和HTTP请求，不模拟已登录用户。测试上下文设置固定的测试密码，使正确与错误输入可重复；普通启动仍使用框架生成的随机密码，不把测试凭据当成课程默认账户密码。

浏览器验证补充后端HTTP客户端无法证明的部分：按钮实际发出哪个请求、Cookie是否省略、代理是否透传认证挑战、服务停止和离线后是否恢复，以及窄屏与键盘是否可用。这里通过开发代理测试，不能据此宣称浏览器直连后端的跨源配置已经完成。

### 6.2 实操：接入测试并验证跟写完成版

完成上述修复后，在**仓库根目录**执行下面的复制命令，将测试与构建脚本接入跟写工程。这些文件用于验证刚刚完成的行为，不作为前面步骤的隐含依赖：

```bash
course_module=learn-java-course/phase21-security/21-04-spring-security
lesson_work=work/security-followalong/21-04-002-explicit-security-filter-chain
mkdir -p "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson002" "$lesson_work/frontend/scripts" "$lesson_work/frontend/tests"
cp "$course_module/21-04-002-explicit-security-filter-chain/src/test/java/cn/ningbingjian/learnjava/security/lesson002/ExplicitSecurityHttpTests.java" "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson002/"
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/scripts/prepare-e2e.mjs" "$lesson_work/frontend/scripts/"
cp "$course_module/21-04-002-explicit-security-filter-chain/frontend/tests/lesson.spec.js" "$lesson_work/frontend/tests/"
cd "$lesson_work"
mvn clean verify
```

后端测试共11项，覆盖公开JSON与安全响应头、查询字符串、近似路径、公开路径携带错误Basic、受保护接口匿名/错误/正确凭据、HTML跳转、登录页可用、成功后独立匿名请求仍拒绝，以及不存在路径在正确认证后交给MVC处理。具体代码见[ExplicitSecurityHttpTests.java](src/test/java/cn/ningbingjian/learnjava/security/lesson002/ExplicitSecurityHttpTests.java)。

进入跟写工程的 `frontend/`执行：

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

普通页面学习无需安装测试浏览器。联合测试需要Java21和Maven3.9.9；如果它们不在默认路径，可设置 `JAVA_HOME`和 `MAVEN_CMD`为本机对应安装位置，先核对 `mvn -v`。`npm run test:e2e`重新构建完成版并运行其11项后端测试，同时在 `.e2e-work/before-chain`创建对照工程：保留安全依赖和公开Controller，只移除 `SecurityConfig.java`，恢复Boot默认链。随后启动真实Java服务、Vite和Chromium，完成6项浏览器测试。

| 场景 | 浏览器验证重点 |
| --- | --- |
| 只有公开Controller、没有自定义链 | 公开按钮仍为401，方法未执行 |
| 正确显式链的公开按钮 | 真实200、JSON、安全响应头，无Cookie或Authorization |
| 同页切换到受保护按钮 | 真实401及Basic挑战，路径和响应体同步更新，方法未执行 |
| 实际停止后端再重启 | 两按钮先502，再分别恢复200与401 |
| 浏览器离线后恢复 | 无完整响应时不猜测认证结果，两按钮恢复可用 |
| 390像素宽度与键盘 | 两按钮可用Enter激活，页面无横向溢出 |

测试使用空闲端口并在结束时关闭服务；日志、截图和失败追踪位于 `.e2e-work/`及 `test-results/`，由忽略规则隔离。真实结果和额外跟写检查见[验证记录](01-验证记录.md)。测试验证的是本课GET边界及现有入口，不把所有HTTP方法、状态码和浏览器都声称为已覆盖。

正式源码的父POM已经聚合第001、002课。在**正式模块目录**可执行 `mvn clean verify`验证两课，或用 `mvn -pl 21-04-002-explicit-security-filter-chain -am test`仅构建第二课及父工程。启动应用仍需进入具体课目录；前端构建产物不包含Java服务或Vite开发代理。

### 6.3 小总结

后端真实认证测试定义访问边界，浏览器测试连接页面、代理和业务执行；跟写的默认链与显式链对照又把行为变化对应到具体配置。每种测试只为它实际观察到的结论提供证据。

## 本课总结

本课把“所有业务请求都需要认证”改成了可解释的两类入口：公开GET `/public/info`允许匿名访问，其他请求继续要求认证。新增Controller先证明业务存在，新增 `SecurityFilterChain`才改变了匿名请求能否继续进入业务的条件。

应用启动时，Spring发现配置类，用框架提供的 `HttpSecurity`构建我们的安全链，Boot的默认链随之退让。请求进入后，具体的公开规则先匹配，未匹配则使用认证兜底；显式启用的表单和Basic机制继续处理各自的认证入口。默认用户由另一组自动配置条件建立，因此当前工程仍可以用随机密码完成正确与错误凭据对照。配置构建、认证入口、请求授权和业务映射是相互连接但职责不同的环节。

前端增加两个按钮，保持相同匿名条件。页面路径经开发代理重写后才到达后端，因此安全规则必须使用 `/public/info`；把 `/api`错误带入规则会导致公开请求落入兜底。用原始HTTP状态、当前请求路径和两类业务日志核对，才能说明200、401或代理502来自哪个阶段。

| 请求条件 | 本课结果 | 结论边界 |
| --- | --- | --- |
| 默认链，匿名GET新公开接口 | 401 | Controller命名与文案不能改变认证要求 |
| 显式链，匿名GET公开接口 | 200与公开业务日志 | 具体公开规则生效，仍经过安全处理 |
| 显式链，匿名JSON GET /hello | 401，无问候日志 | 其余业务仍需要认证 |
| 显式链，正确Basic GET /hello | 200与问候日志 | 认证能力和默认用户仍可用 |
| 显式链，匿名HTML GET /hello | 302指向/login | 当前表单入口仍然启用 |
| 公开路径主动携带错误Basic | 401 | permitAll不表示跳过此前的凭据处理 |
| 近似路径匿名GET | 401 | 未意外扩大公开范围，不据此判断业务是否存在 |
| 后端停止或浏览器离线 | 代理502或无完整响应 | 网络环节失败不能冒充认证结果 |

这些结果限定于本课版本、GET方法、Accept和凭据条件。尚未建立页面登录后的会话保持、细分角色、业务资源归属或生产部署；CSRF没有关闭，其写请求流程在后续课程展开。实际开发中，应先清楚写出公开方法和路径，再保留明确兜底与需要的认证入口，用正向及拒绝证据一起验证；不能以放宽所有请求替代定位配置错误。

## 理解检查与后续衔接

1. 只新增 `PublicInfoController`时匿名401，正确Basic却为200，这两个结果分别说明什么？
2. `requestMatchers`配置 `/public/info`与浏览器请求 `/api/public/info`为什么并不矛盾？写错前缀后怎样最小修复？
3. 自行定义链后仍能看到随机用户密码，是否表示Boot还在使用原来的默认链？用条件报告解释。
4. 公开请求携带错误Basic得到401，是否与 `permitAll()`矛盾？为什么本课两个按钮固定为匿名请求？
5. 如果只把唯一链的 `securityMatcher`限定到 `/public/**`，能否据此认定 `/hello`继续受保护？
6. 页面从公开200切换到受保护401，哪些页面和后端证据应同时发生变化？停止后端后又应如何判断？

<details>
<summary>完成推演后再核对参考要点</summary>

1. 匿名401说明当前请求被认证要求拦截；正确认证后的200和业务日志确认映射存在且方法可执行。
2. Vite先去掉代理前缀再转发；后端匹配收到的路径。只修正规则，重启后验证公开200及保护401。
3. 不是。默认链与用户各自检查条件；本课链Bean让前者退让，后者仍满足条件。
4. 不矛盾。公开授权规则不跳过前面的凭据处理；固定匿名条件才能公平比较两个路径。
5. 不能。链匹配限制了整个安全链的作用范围，范围外可能没有安全链保护；本课用链内规则配置公开访问。
6. 本次路径、原始状态、响应内容与对应方法日志共同变化；停止后端的代理502和离线的无完整响应不代表认证失败。

</details>

下一课是[21-04-003 为 API 建立可观察的认证失败契约](../21-04-003-api-authentication-errors/README.md)：在保留真实HTTP状态和HTML登录入口的前提下，让API认证失败返回可解释的结构化响应，区分安全过滤器阶段与MVC异常处理阶段。
