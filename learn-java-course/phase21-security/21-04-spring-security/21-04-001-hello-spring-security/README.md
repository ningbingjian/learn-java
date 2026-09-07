# 21-04-001 引入 Spring Security 后，请求发生了什么？

[模块入口](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-001) · [版本基线](../01-版本基线.md)

## 本课导入

在 Spring MVC 中，我们已经可以把一个 HTTP 请求映射到 Controller 方法。本课从这样的接口继续：如果接口没有检查访问者的身份，任何能访问服务地址的人都可能调用它。加入 Spring Security 后，即使不改 Controller，同一个请求也可能到不了业务方法。这个变化发生在哪里，应用又凭什么决定是否继续处理？

我们先建立一个只返回问候信息的后端和独立前端，通过页面确认请求成功，再只增加后端安全依赖，观察同一个按钮发出的请求为什么从200变成401。随后使用请求命令对照默认登录跳转、错误密码与正确密码的差异。学完应能独立复现这些行为，根据页面、请求头和凭据预测响应，并用业务日志或断点判断方法是否执行；还能区分后端拒绝、代理连接错误与浏览器网络失败。这些能力是后续工单权限设计的基础，本课尚不加入工单、数据库或角色规则。

前置准备是 JDK 21、Maven 3.9.9、Node.js 22.15.0、npm 10.9.2、Spring Boot / MVC 基础，以及 HTML、基础 JavaScript、HTTP 请求头与状态码。前端使用原生 JavaScript 与 Vite 8.2.2，布局文件直接提供，请求与响应处理代码在正文逐步解释。运行依赖固定为 Boot 4.0.8、Security 7.0.7、Framework 7.0.9；详细依据见[版本基线](../01-版本基线.md)。认证（Authentication）是核实访问者身份；授权（Authorization）是在当前身份和规则下判断是否允许操作。第一课只使用框架默认的“需要认证”规则，后续才加入细分权限。

### 跟写目录与完成版的关系

仓库当前单课目录保存的是**完成版**，已经包含安全依赖和测试。首次跟写使用仓库根目录下新建的 `work/security-followalong`，仅复制模块父 POM，其余文件按正文逐步创建。这样仍沿用同一模块构建结构，也不会让完成版的安全依赖、测试或组件扫描干扰中间步骤。该跟写目录由你创建，不是已经存在的课程源码。

先在仓库根目录执行下面命令。若 `work/security-followalong` 已存在，请换一个空目录或继续上次进度，避免覆盖自己写过的内容。

```bash
mkdir -p work/security-followalong/21-04-001-hello-spring-security
cp learn-java-course/phase21-security/21-04-spring-security/pom.xml work/security-followalong/pom.xml
cd work/security-followalong/21-04-001-hello-spring-security
mkdir -p src/main/java/cn/ningbingjian/learnjava/security/lesson001 src/main/resources
```

后文文件路径都相对这个**跟写单课目录**。除明确说明外，Maven命令也在这里执行。后端占用终端A，前端开发服务器占用终端B；后面的 `curl` 对照请求在终端C执行，发送请求不依赖终端C所在目录。每次修改依赖后先在终端A按 `Ctrl+C` 停止旧进程，再重新启动；本例没有自动热重载。

只想检查完成版时，在仓库本课目录执行 `mvn clean verify`、`mvn spring-boot:run`；另开终端进入本课 `frontend/` 执行 `npm ci`、`npm run dev`，打开 `http://127.0.0.1:5173`，点击按钮应看到401。完成版已加入安全依赖，200对照需要按正文建立前面的跟写状态。

## 1. 先确认请求能够到达业务方法

### 1.1 理论：接口存在不等于接口受到身份保护

Spring MVC 的请求映射负责根据请求方法和路径选择业务处理方法。`@GetMapping("/hello")` 告诉框架怎样找到这个方法，并不自动要求访问者登录。当前工程没有安全组件或自定义拦截规则，匿名的 `GET /hello` 应能进入该方法。

为了判断后面的拒绝发生在哪里，我们在方法入口增加一个固定日志标记。响应说明客户端收到什么，方法日志说明业务代码是否执行；两者一起看，比只看浏览器最后显示的页面更容易定位处理阶段。

### 1.2 实操：建立能够返回 JSON 的最小应用

新增跟写目录中的 `pom.xml`，此时只需要 Web MVC 和测试依赖。父 POM 从上一步复制的 `../pom.xml` 读取，再继承 Boot 的版本管理。测试依赖提前声明，但本节还没有测试文件；它不参与生产运行时的请求处理。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.ningbingjian.learnjava</groupId>
        <artifactId>spring-security-lessons</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>spring-security-lesson001</artifactId>
    <name>21-04-001 Hello Spring Security</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-boot-starter-webmvc` 提供 MVC 应用需要的组件及嵌入式服务器；`spring-boot-maven-plugin` 提供运行和可执行包构建。子课不再分别给 Spring、Security 等依赖指定版本。

新增 `src/main/java/cn/ningbingjian/learnjava/security/lesson001/SecurityLessonApplication.java`：

```java
package cn.ningbingjian.learnjava.security.lesson001;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecurityLessonApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityLessonApplication.class, args);
    }
}
```

`SpringApplication.run` 创建并启动应用上下文。`@SpringBootApplication` 启用自动配置，并以当前类的包为组件扫描起点；所以 Controller 放在同一包下就能被找到。它本身不编写登录逻辑，后面观察到的默认安全行为来自依赖满足条件后生效的配置。

新增同一目录下的 `HelloController.java`：

```java
package cn.ningbingjian.learnjava.security.lesson001;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @GetMapping("/hello")
    public Map<String, String> hello() {
        log.info("HELLO_HANDLER_REACHED");
        return Map.of("message", "Hello Spring Security");
    }
}
```

`hello()` 每次被实际调用时先写入 `HELLO_HANDLER_REACHED`，再返回只有一个键值对的 Map。`@RestController` 让返回值作为响应体处理；对本课明确接受 JSON 的请求，消息转换组件将它写成 JSON。日志标记是教学观察点，不承载认证状态，也不保存密码。

新增 `src/main/resources/application.properties`：

```properties
spring.application.name=spring-security-lesson001
server.address=127.0.0.1
```

应用名帮助辨认日志，`server.address` 把练习服务限制在本机回环地址。没有设置用户名、密码或额外安全开关。

现在先预测：请求路径匹配，接口没有认证要求，应该出现一次业务日志并返回200。在终端A执行：

```bash
mvn spring-boot:run
```

看到应用启动完成后，在终端C执行：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

本节实际验证结果的稳定摘要如下；日期、端口日志和其他响应头省略：

```text
HTTP/1.1 200
Content-Type: application/json

{"message":"Hello Spring Security"}
```

终端A新增 `HELLO_HANDLER_REACHED`。这只能证明当前匿名请求成功执行了业务方法，不能说明这个接口适合直接暴露给真实用户。暂时保持终端A中的后端运行，下一节从独立前端访问它。

### 1.3 小总结

请求映射解决“由哪个方法处理”，身份保护解决“请求能否继续进入业务”。先得到能运行的接口和可观察的业务入口，才能把下一步的响应变化归因到安全依赖，而不是接口原本就有错误。

## 2. 从独立前端发出同一个请求

### 2.1 理论：页面展示与接口处理分开，仍要观察同一个请求

前端负责把操作变成请求，并把响应转换成界面；后端负责认证、授权和业务处理。第一课只需要一个按钮和结果区域，学生先观察匿名访问，无需同时实现登录状态、路由或权限菜单。

前端页面由 `127.0.0.1:5173` 提供，后端运行在 `127.0.0.1:8080`，是两个独立进程。端口不同属于不同源；如果浏览器直接从前端页面请求8080，会涉及浏览器跨域规则。为了先隔离认证这一变量，本课的浏览器只请求当前页面同源的 `/api/hello`，由Vite开发服务器转发给后端 `/hello`。后端Java代码仍然只映射 `/hello`。

这意味着页面中的 `/api/hello` 与终端直接访问后端 `/hello` 最终到达同一个业务入口，但经过的网络环节不同。开发代理由服务器转发请求，不等于浏览器已经完成跨域授权，也不等于Spring Security被绕过。跨域预检和凭据携带在第四篇另设直连实验。[Vite代理配置说明](https://vite.dev/config/server-options#server-proxy)给出了路径转发与重写的职责。

### 2.2 实操：复制页面骨架，逐步理解请求代码

先在跟写单课目录复制前端骨架。以下来源路径和Java测试复制路径一样，`../../../`回到仓库根目录；若你调整了跟写目录深度，应同步调整来源。只复制明确列出的文件，不复制 `node_modules` 或构建产物。

```bash
course_frontend=../../../learn-java-course/phase21-security/21-04-spring-security/21-04-001-hello-spring-security/frontend
mkdir -p frontend/src
cp "$course_frontend/package.json" "$course_frontend/package-lock.json" "$course_frontend/.nvmrc" "$course_frontend/index.html" "$course_frontend/vite.config.js" frontend/
cp "$course_frontend/src/style.css" frontend/src/
```

`index.html` 提供按钮、状态码、响应来源和响应内容区域，`style.css` 负责排版；二者没有身份校验逻辑。`package.json` 声明运行命令和依赖，锁文件固定依赖解析，后面用 `npm ci` 安装。浏览器测试脚本在第6节再复制，此时只使用开发和构建命令。

打开复制的 `vite.config.js`，先确认三项：`target` 默认指向后端8080，`rewrite`去掉路径开头的 `/api`，`strictPort: true`在5173占用时直接报错，防止学生访问错误端口。`changeOrigin`修改代理发给后端的Host头，不是关闭浏览器同源限制。配置只在代理连接失败时产生带专用标记的502；后端实际返回的状态、响应头和内容保持原样转发。

接下来新增 `frontend/src/main.js`。先看元素引用与点击回调：页面元素承载显示状态，点击事件才触发请求；初次打开页面不会自动访问接口。请求开始时禁用按钮、清除旧结果，结束后在 `finally` 中恢复按钮，使失败也可以重试。

```javascript
import './style.css';

const button = document.querySelector('#send-request');
const panel = document.querySelector('.result-panel');
const code = document.querySelector('#status-code');
const summary = document.querySelector('#result-summary');
const source = document.querySelector('#response-source');
const body = document.querySelector('#response-body');

button.addEventListener('click', async () => {
  button.disabled = true;
  button.textContent = '正在请求…';
  panel.setAttribute('aria-busy', 'true');
  code.dataset.state = 'loading';
  code.textContent = '请求中';
  summary.textContent = '等待接口响应…';
  source.textContent = '—';
  body.textContent = '等待响应内容。';

  try {
    const response = await fetch('/api/hello', {
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
    button.disabled = false;
    button.textContent = '发送匿名请求';
    panel.setAttribute('aria-busy', 'false');
  }
});
```

核心在请求参数和结果分支，需要逐项解释：

- `fetch('/api/hello', ...)`使用当前页面的源发出GET，开发代理再转发。`await`等待异步结果，并不阻塞浏览器整页操作。
- `Accept: application/json`控制本次请求接受的响应格式，和上一节终端请求一致。
- `credentials: 'omit'`让本课每次请求都保持不携带浏览器凭据的条件；即使其他页面已经登录，本课按钮也不借用会话。代码也没有构造Authorization头。后续讲会话保持时会明确调整这个参数。
- `redirect: 'error'`避免静默跟随登录跳转后，把登录页的200当作业务200；如果实际收到重定向，fetch会失败，应到Network核对原始请求。本课的明确JSON匿名请求预期为401。
- `AbortSignal.timeout(8000)`为等待设置8秒上限，避免连接异常时按钮一直不可用。超时属于请求未完成，不表示已验证身份。
- `fetch`收到401、404或502时通常仍返回 `Response`，因此先读取 `status`和响应体，再分类。只有连接失败、禁止的重定向或超时等导致操作失败时进入 `catch`。[Fetch使用说明](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch)解释了HTTP错误与Promise拒绝的区别。
- 响应通过 `textContent`按文本显示；即使后端返回HTML，也不会被当作页面脚本执行。页面不收集或展示密码、Cookie或完整身份凭据。

现在打开终端B，进入跟写单课的 `frontend/` 目录后执行：

```bash
node --version
npm --version
npm ci
npm run dev
```

确认Node为22.15.0、npm为10.9.2，打开 `http://127.0.0.1:5173`。此时后端仍是第1节**没有安全依赖**的版本，预期点击“发送匿名请求”得到 **HTTP 200**、业务JSON及后端新增的 `HELLO_HANDLER_REACHED`。

打开浏览器开发者工具的Network面板，再点击一次。找到 `/api/hello`，核对请求URL的端口是5173、Accept是JSON、状态是200；后端日志则证明请求经过代理后实际执行了Java方法。页面请求头不会显示“浏览器直接访问8080”，因为转发发生在开发服务器上。

### 2.3 小总结

前端项目提供交互与观察入口，后端仍决定业务是否执行。明确路径重写、Accept和凭据条件，才能与终端请求建立有效对照。当前200来自真实后端响应；接下来只改变后端依赖，页面和请求代码都保持不变。

## 3. 只增加安全依赖，为什么业务代码没有执行？

### 3.1 理论：自动配置把安全处理接入请求入口

在当前 Boot Servlet 应用中，加入安全依赖后，框架能够提供安全过滤器链（Security Filter Chain）：请求按配置经过一系列安全处理组件，通过后才继续交给 MVC。过滤器既可以继续调用后面的处理过程，也可以直接写响应并结束当前请求。

本例没有自定义安全链、用户服务或其他认证组件，相关自动配置条件满足，于是提供默认用户，并要求访问业务接口前完成认证。安全规则不需要写进 `hello()`；对没有身份凭据的请求，链上的处理会在业务方法之前产生认证入口响应。本节固定 `Accept: application/json`，不带 Cookie、Authorization 或额外的浏览器异步请求头，预期得到401及 Basic 认证提示。

这里的401表示当前请求缺少可接受的身份认证。它不等同于业务方法抛错，也不能单凭状态码判断 Controller 是否存在。默认配置条件可对照 [Boot 4.0.8 Servlet 安全配置](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/web/servlet/ServletWebSecurityAutoConfiguration.java)；过滤器职责见 [Security Servlet 架构](https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html)。完整构建和执行源码在第五篇深入。

### 3.2 实操：保持业务文件不变，只修改 POM

在第1节 `pom.xml` 的 `<dependencies>` 内、Web MVC 依赖后，**新增**下面一项。保留其他依赖、插件和所有 Java、资源文件不变：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

先在终端A按 `Ctrl+C` 停止旧后端，再重新启动。终端B的前端继续运行：

```bash
mvn spring-boot:run
```

这次日志会出现 `Using generated security password:`，其后是**本次启动生成的密码**。默认用户名是 `user`。在本例未配置其他用户服务的条件下，Boot 建立内存用户服务，用户名和校验所需信息保存在当前应用进程中；没有数据库，也没有持久化用户注册。重新启动会生成新密码，旧进程的密码不能继续用于新进程。该初始化行为可对照 [Boot 4.0.8默认用户配置](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.java)。

先回到 `http://127.0.0.1:5173`，保持页面和前端源码不变，再点击“发送匿名请求”。页面应显示 **HTTP 401**、未通过认证的提示和本次响应体，后端不会新增业务日志。浏览器 Network 中 `/api/hello` 的状态也是401，响应头保留后端的 `WWW-Authenticate`；代理没有把拒绝改成成功。

接着用终端C直接访问后端，保持同样的 Accept 与匿名条件，核对页面结果：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

稳定摘要：

```text
HTTP/1.1 401
WWW-Authenticate: Basic realm="Realm"
```

没有业务 JSON，终端A也没有因这次请求新增 `HELLO_HANDLER_REACHED`。`WWW-Authenticate` 是服务器给客户端的认证方式提示，当前值说明可用 Basic 方式提交凭据；它不是访问令牌。响应体可能包含错误描述，其格式并不是本课的验证重点。

为了验证方法确实没有被调用，可用 IDE 以 Debug 模式运行 `SecurityLessonApplication.main`，在 `HelloController.hello()` 的日志行设置断点，再发送上述请求。请求被拒绝时不会进入此业务断点；第5节带正确凭据的同路径请求应命中它。先停止终端A服务再使用IDE启动，避免端口冲突。

### 3.3 小总结

本次改变的是类路径和由此满足的自动配置条件。默认安全处理在业务方法之前要求认证，因此业务源码未变，匿名请求却由200变成401。“加入依赖就有这些默认行为”只适用于本课无自定义覆盖的配置条件，后续显式定义安全链时要重新判断。

## 4. 浏览器为什么显示登录页，而客户端看到401？

### 4.1 理论：身份要求相同，认证入口响应可以不同

同一个未认证请求，服务器可以用不同方式引导访问者提供身份。浏览器导航通常表示希望接收 HTML，默认配置会用重定向引导到登录页；本课的 JSON 客户端则得到401与认证提示。根据请求接受的媒体类型等条件选择响应方式，称为内容协商（Content Negotiation）。

`Accept` 表示客户端愿意接收什么格式，`Content-Type` 描述当前消息体是什么格式；没有请求体的这个 GET 实验需要控制的是前者。此处不是框架简单识别“用了浏览器还是 curl”，而是请求条件影响了入口选择。默认选择还涉及其他请求头和匹配规则，所以不要把所有 HTML、JSON 或异步请求笼统归为同一结果。[官方入门说明](https://docs.spring.io/spring-security/reference/7.0/servlet/getting-started.html)也区分浏览器跳转与客户端401。

### 4.2 实操：只改变 Accept，保留匿名条件

在服务仍运行的情况下发送：

```bash
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/hello
```

预期是302而不是200，因为服务器此时要告诉客户端“去登录地址”，尚未执行问候方法。实际稳定摘要：

```text
HTTP/1.1 302
Location: http://127.0.0.1:8080/login
```

这里不要加 `-L`。`curl -L` 会继续请求重定向地址，使你看到后续登录页的响应，容易误以为 `/hello` 已经匿名放行。现在单独访问登录页：

```bash
curl -i -H 'Accept: text/html' http://127.0.0.1:8080/login
```

得到200及 HTML 表单，其中包含用户名、密码输入框。这个200属于 `/login` 页面，不属于 `/hello` 业务接口。仓库没有 `/login` Controller：本例的登录页由框架生成。表单还包含框架生成的 CSRF 隐藏字段；本课只知道提交表单时需要原样携带它，攻击模型与校验机制在第四篇讲解。

在浏览器的全新隐私窗口访问 `http://127.0.0.1:8080/hello`，也可以观察地址跳转。先保持未登录状态；旧窗口可能带有会话 Cookie，无法作为“没有身份”的对照。自动化验证使用明确请求头的真实 HTTP 请求核对跳转及页面，本次没有把 GUI 截图或不同浏览器的外观作为验证结果。

### 4.3 小总结

401与登录跳转都可能源于未完成认证。302需要结合 `Location` 理解，登录页200需要结合请求路径理解。排查时先看原始请求头、是否带凭据、是否自动跟随跳转，再判断安全规则是否生效。

## 5. 凭据正确之后，请求怎样回到业务处理？

### 5.1 理论：身份校验结果决定请求是否继续

HTTP Basic 通过 `Authorization` 请求头发送用户名和密码的编码结果。客户端发送凭据后，安全组件从请求中读取它们，交给认证体系校验；本例使用刚才的内存用户信息。密码不匹配就产生认证失败响应；匹配时建立表示已认证身份的结果，供后续安全规则使用。当前规则只要求完成认证，因此正确身份可以继续进入 MVC 和 `hello()`。

框架用安全上下文（Security Context）承载当前处理过程中的身份信息。它的保存和恢复机制留到第四、五篇；现在应知道它是安全组件交换认证结果的载体，不是 Controller 自己维护的全局“已登录”开关。

Basic 使用 Base64 编码，编码可以还原，不是加密。这里仅在本机回环地址实验；真实系统传输此类凭据需要 HTTPS。默认随机用户和启动日志密码用于开发观察，不是生产用户体系。Basic 的处理职责参考 [Security 7.0 Basic 认证说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/basic.html)。

### 5.2 实操：先验证失败，再在同一个请求位置修复

先故意使用错误密码，验证凭据失败发生在认证阶段，而不是调用业务后才报错：

```bash
curl -i -H 'Accept: application/json' -u 'user:wrong-password' http://127.0.0.1:8080/hello
```

结果仍为401，没有业务日志标记。这里的根因是请求密码与当前进程的用户信息不匹配，不是用户名参数名写错或 JSON 序列化失败。

现在在原位置修复凭据。为避免把当前生成密码直接写入 shell 历史，只向 `curl` 指定用户名，让它交互提示输入密码：

```bash
curl -i -H 'Accept: application/json' -u user http://127.0.0.1:8080/hello
```

在提示出现后输入终端A**本次启动**日志里的密码。正确时返回200及 `{"message":"Hello Spring Security"}`，终端A新增一次 `HELLO_HANDLER_REACHED`。如果IDE设置了第3节的断点，此时应该命中；继续运行后客户端才能收到响应。这个正向对照把先前“没有日志”与安全拦截的解释连接起来。

再执行一次不带 `-u` 的 JSON 请求：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

仍为401。本课每次 curl 都是独立请求，没有用 `-b` 或 `-c` 携带、保存 Cookie，成功请求中的 Authorization 头也不会自动出现在下一个命令里。这个结果说明当前无凭据请求仍受保护，不能扩展为“Spring Security 永远不保存会话”。浏览器表单登录与会话恢复将在后续课程单独验证。

最后区分“安全通过”和“路由存在”：把路径改成未定义的 `/missing`，先匿名发送，再用同样方式输入正确密码：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/missing
curl -i -H 'Accept: application/json' -u user http://127.0.0.1:8080/missing
```

实际结果依次是401和404。匿名请求先遇到安全要求，不能据此判断路由存在；认证通过后，请求继续到后面的映射和错误处理，才得到资源不存在的404。`/missing` 不会打印问候方法日志。实验结束后回到 `/hello` 的正确凭据请求，确认正常业务响应仍为200。

### 5.3 小总结

错误密码在认证阶段失败；正确密码使当前请求获得已认证身份，再由默认规则决定是否继续。安全放行不保证业务路径存在，也不保证未来所有权限规则都通过。分析响应时应把认证、安全决策、MVC映射和业务执行分开判断。

## 6. 区分网络故障，并固定联合验证

### 6.1 理论：测试应检查请求条件与响应，而不是跳过认证

页面提示需要区分两类证据：后端已经返回HTTP状态，或浏览器根本没有取得完整响应。代理连接不到后端时，由开发服务器生成的502也不代表Spring Security作出了决策。

后端测试要验证真实凭据如何影响真实 HTTP 请求，所以测试启动嵌入式服务器，用 JDK 的 HTTP 客户端发送请求；不直接调用 Controller，也不预先模拟一个已认证用户。每个请求都明确 Accept 和 Authorization，客户端不跟随跳转、不保留 Cookie，避免不同用例意外共享身份。

测试上下文单独设置 `spring.security.user.password=lesson001-test-only`，让成功和失败请求可重复；用户名、默认安全链和实际密码校验仍由框架提供。这个测试值只在测试类注解里生效，正常启动继续使用随机密码。因此，“固定密码的自动化测试通过”和“普通启动的随机密码验证通过”是两份不同证据。

### 6.2 实操：在完成安全依赖步骤后加入测试

先保持页面打开，停止终端A的后端，再点击按钮。页面应显示 **HTTP 502**，响应来源为“开发代理：未取得后端响应”，提示检查后端是否启动。Network中可看到专用的 `X-Lesson-Proxy-Error: upstream-unavailable`；这是开发代理产生的故障标记，不能解释为密码错误或权限不足。

重新启动后端，等待启动完成后再点击，应恢复401。随后在浏览器Network面板选择Offline（页面已经加载完成），再点击按钮，页面应显示“未取得完整响应”，不会编造401。切回No throttling或正常网络，点击应再次恢复401。这里先观察故障阶段，再恢复原位置的运行条件，避免错误状态留到后续。

现在停止跟写服务。在跟写单课目录执行下列命令，复制仓库完成版的测试文件，其他文件保持第5节完成状态。下面的 `../../../` 从 `仓库/work/security-followalong/本课目录` 回到仓库根目录；若你选择了不同深度的跟写目录，应相应调整来源路径。

```bash
mkdir -p src/test/java/cn/ningbingjian/learnjava/security/lesson001
cp ../../../learn-java-course/phase21-security/21-04-spring-security/21-04-001-hello-spring-security/src/test/java/cn/ningbingjian/learnjava/security/lesson001/DefaultSecurityHttpTests.java src/test/java/cn/ningbingjian/learnjava/security/lesson001/
```

完整测试见 [DefaultSecurityHttpTests.java](src/test/java/cn/ningbingjian/learnjava/security/lesson001/DefaultSecurityHttpTests.java)。运行前先理解其中三个关键位置：

- `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ...)` 启动完整应用并只覆盖测试密码；随机端口避免测试依赖8080空闲。
- `@LocalServerPort` 把实际端口注入测试字段，`get` 辅助方法据此构造 URL。辅助方法只在传入非空凭据时添加 Authorization，没有隐式登录。
- `basic` 将用户名与密码按 `用户名:密码` 拼接并编码，只生成请求头；它不会替代服务端认证。断言检查状态、关键响应头或业务内容，不把整段会变化的错误页面当固定契约。

例如第一个用例保持无凭据条件，检查401和认证挑战，并确认没有业务 JSON：

```java
@Test
void jsonWithoutCredentialsIsChallengedBeforeBusinessResponse() throws Exception {
    var response = get("/hello", "application/json", null);
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("WWW-Authenticate").orElseThrow())
            .startsWith("Basic");
    assertThat(response.body()).doesNotContain("Hello Spring Security");
}
```

这个响应断言本身不能证明所有内部方法从未执行，所以前面另用业务日志和可选断点核对执行边界；不要把两个证据混为一谈。其余用例覆盖HTML跳转、登录页、错误密码、正确密码、成功后无凭据再访问，以及未定义路径。

在跟写单课目录执行：

```bash
mvn clean verify
```

预期7项测试全部通过，生成可执行包。完成验证后还可以使用同一份完成代码启动：

```bash
java -jar target/spring-security-lesson001-1.0-SNAPSHOT.jar
```

这个普通启动不使用测试密码，仍应从新的启动日志读取生成密码。

接着在跟写单课目录补齐浏览器验证脚本：

```bash
course_frontend=../../../learn-java-course/phase21-security/21-04-spring-security/21-04-001-hello-spring-security/frontend
cp "$course_frontend/playwright.config.js" frontend/
cp -R "$course_frontend/scripts" "$course_frontend/tests" frontend/
cd frontend
npm run build
npx playwright install chromium
npm run test:e2e
```

`build`验证独立前端能生成静态文件；`dist/`本身不包含Java服务或开发代理，正式部署时需要另外配置 `/api` 的转发，不能把构建成功等同于已完成部署。

浏览器验证脚本先运行完成版后端的7项测试，再在被忽略的 `.e2e-work/` 中建立“仅移除安全依赖”的对照工程。测试启动真实Java服务与Vite代理，使用自动分配的空闲端口，依次验证页面200、401透传、停止后端后的502及恢复、浏览器离线及恢复、窄屏与键盘操作。它不通过预设响应模拟200或401，也不修改课程完成版的安全配置。测试结束关闭这些服务，截图和失败追踪保存在被忽略的 `test-results/`。

普通学习只需 `npm ci` 与 `npm run dev`，无需安装测试浏览器。执行联合测试时还需要Java21和Maven；`mvn -v`必须指向正确JDK。测试没有要求安装数据库、Redis或Docker。完整入口见[前端项目说明](frontend/README.md)，实际验证范围见 [01-验证记录](01-验证记录.md)。

### 6.3 小总结

可重复验证需要把请求条件固定下来，同时保持所研究的认证链真实执行。后端测试的确定性凭据验证认证链，浏览器测试验证真实页面、开发代理及故障恢复；普通启动的随机凭据实验另验证默认初始化。每种证据都应对应具体范围。

## 本课总结

本课完成了独立前端与后端的接口观察实验：同一个页面按钮从匿名访问200变成401，并通过保持业务源码不变、仅新增安全依赖，定位了行为变化的来源：在当前自动配置条件下，安全处理被接入 MVC 前面的请求过程，访问者必须先提供可以验证的身份。

请求不携带可接受身份时，默认认证入口根据本课控制的请求条件返回401或登录跳转；提供错误密码时认证失败；提供正确密码时建立当前身份，默认认证要求得到满足，请求才能继续经过 MVC 映射进入业务方法。这个处理过程解释了为什么 Controller 没改，客户端却会收到不同结果，也解释了为什么认证成功仍可能得到404。

| 本课条件 | 观察结果 | 能支持的结论 |
| --- | --- | --- |
| 未加入安全依赖，匿名 GET /hello | 200及业务日志 | 当前映射与业务方法可运行 |
| 加入依赖，JSON GET，无凭据 | 401及Basic提示，无业务日志 | 本次匿名请求在业务方法前被拒绝 |
| HTML GET，无凭据、无会话 | 302，Location指向/login | 默认入口引导客户端登录 |
| 单独访问/login | 200登录表单 | 认证入口页面可访问，不表示业务接口公开 |
| JSON GET /hello，错误密码 | 401 | 当前凭据未通过认证 |
| JSON GET /hello，正确密码 | 200及业务日志 | 当前默认认证要求满足，业务方法执行 |
| JSON GET /missing，正确密码 | 404 | 安全放行之后仍要判断资源是否存在 |
| 前端运行、后端停止 | 开发代理502及专用错误标记 | 代理未取得后端响应，不是认证拒绝 |
| 页面加载后浏览器离线 | 未取得完整响应 | 请求没有得到可判断认证结果的完整证据 |

这些结果受依赖版本、自动配置条件、Accept、凭据和会话状态约束，不能无条件套用到自定义安全链、异步请求、POST或其他权限规则。页面请求经开发代理转发，仍受后端安全链约束；本课没有通过浏览器直连来验证CORS配置。真实开发中遇到接口“进不去”，先核对页面与Network中的原始响应、代理来源、重定向、凭据和业务执行证据，再判断问题发生在安全处理、路由还是业务阶段。本课尚未建立角色与资源归属授权，也没有提供生产用户管理体系。

## 理解检查与后续衔接

1. 相同 Controller 加入安全依赖后从200变成401，改变的是业务逻辑还是请求到达业务之前的处理条件？用一次正向和一次拒绝请求说明。
2. 将 `Accept: application/json` 改为 `text/html`，在本课匿名条件下会发生什么？为什么加上 `curl -L` 可能让观察产生误解？
3. 正确认证后访问 `/missing` 得到404，是否说明密码没有生效？怎样用已讲过的对照请求判断？
4. 重启应用后旧密码不再可用，首先应该检查哪里？自动化测试为什么不依赖这个随机密码？
5. 一次带Basic凭据请求成功后，另一次不带凭据和Cookie的请求为什么仍被拒绝？这是否足以证明浏览器登录无法保持会话？

6. 停止后端得到502、浏览器离线没有完整响应，它们为什么不能统一显示为“未登录”？怎样在原位置恢复并验证？

<details>
<summary>完成推演后再核对参考要点</summary>

1. 改变的是安全组件与自动配置；同一路径正确凭据请求出现业务日志、匿名请求没有新增日志，构成执行边界的对照。
2. 本课明确请求头条件下变为302，`-L`会继续访问登录页，其200不属于原始业务请求。
3. 404发生在安全允许继续之后。匿名同路径401、正确凭据同路径404，能区分认证与资源解析。
4. 检查当前进程的启动日志和默认用户条件。测试只在测试上下文覆盖密码，使输入确定；普通启动另行验证。
5. 新请求没有携带前次身份凭据。本课的独立curl请求没有保存会话，不能据此推断浏览器表单登录的会话行为。

6. 502带开发代理错误标记，说明没有取得后端响应；离线时浏览器请求失败，都不足以推断认证状态。重新启动后端或恢复网络后，再用同一个按钮核对401。

</details>

下一课计划是 [21-04-002 显式定义公开接口与认证接口](../00-模块学习大纲.md#lesson-002)：继承本课前后端工程，在页面增加公开接口按钮，用显式安全链表达“哪些请求公开，哪些请求需要认证”。下一课正文尚未编写。
