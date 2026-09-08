# 21-04-006 登录之前：取得并携带CSRF令牌

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-006) · [上一课](../21-04-005-users-and-password-encoding/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第五课已经可以加载三个实验用户并核对密码。下一步准备做浏览器登录，但不能先写一个POST，遇到403就关闭CSRF。本课先把“写请求需要什么条件”单独做成可观察的实验，再在下一课接入真正的登录提交。

本课新增一个GET令牌入口和一个不修改业务数据的POST探针。探针明确允许匿名，用来隔离CSRF条件：同会话合法令牌得到200，缺失、错误或与会话不对应的令牌得到403。页面自动初始化令牌，提交始终手动触发，不自动重发POST。完成后你应能区分身份凭据、会话Cookie与CSRF令牌，解释为什么`permitAll`不能跳过CSRF，以及为什么“拿到令牌”不等于“已经登录”。

沿用[版本基线](../01-版本基线.md)：JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9；Node22.15.0、npm10.9.2、Vite8.2.2。后端仍需第五课的三个`LESSON_*_PASSWORD`环境变量，没有数据库、新前端框架或新依赖。正式前端登录在第007课，会话与跨源安全在第四篇继续展开。

## 准备：复制第五课完成版

在**仓库根目录**执行以下macOS/Linux shell命令，创建独立跟写目录。来源是仓库第五课，不依赖你过去的练习；目标存在时停止，避免覆盖。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-005-users-and-password-encoding"
lesson_work="$PWD/work/security-followalong/21-04-006-csrf-before-login"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson006" "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson005/lesson006/g' -e 's/21-04-005/21-04-006/g' -e 's/Users and Password Encoding/CSRF Before Login/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson005/*.java; do
  sed 's/lesson005/lesson006/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson006/$(basename "$file")"
done
sed 's/lesson005/lesson006/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

以下Java文件都在跟写`src/main/java/cn/ningbingjian/learnjava/security/lesson006/`下。父POM只供继承，构建始终在跟写单课目录进行；先不复制完成版测试。

运行后端仍按第五课设置变量。在跟写单课目录执行`bash`进入一个交互式子shell，再输入下面命令；三个密码至少8个字符，UTF-8不超过72字节。不要使用个人真实密码。

```bash
read -r -s -p 'member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
read -r -s -p 'support实验密码：' LESSON_SUPPORT_PASSWORD
printf '\n'
read -r -s -p 'admin实验密码：' LESSON_ADMIN_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
mvn clean package
java -jar target/spring-security-lesson006-1.0-SNAPSHOT.jar
```

后端仍只监听`127.0.0.1:8080`；先停止占用端口的前课服务。后续修改都在这个shell中停止进程、重新构建再启动，环境变量仍保留。另一个请求终端用来执行curl，不要混用后端正在运行的终端。

## 一、先分清Cookie、身份与CSRF

### 理论：浏览器自动附带的信息需要额外的请求证明

跨站请求伪造（Cross-Site Request Forgery，CSRF）利用浏览器可能自动给目标站点请求附带Cookie等凭据的行为。另一网站即使读不到目标响应，也可能诱导浏览器发出请求；如果服务器只根据自动附带的身份信息执行写操作，就可能接受非用户本意的操作。

CSRF令牌提供一个额外值：正常页面先取得它，写请求再主动提交；服务器检查它是否与当前会话保存的期望值对应。令牌不放到URL里，也不是账号密码。防护依赖攻击方不能任意读取目标站点令牌等条件；XSS、泄露令牌或宽泛跨源许可会破坏边界。Cookie的SameSite属性能减少部分跨站场景，但不能代替这里的机制分析。

| 数据 | 本课如何取得与提交 | 它不能单独证明什么 |
| --- | --- | --- |
| 用户名和密码 | 第五课Basic按钮主动提交 | 不替代POST的CSRF检查 |
| JSESSIONID会话Cookie | 服务器设置、浏览器按同源策略发送 | 匿名会话也可以有Cookie，不等于用户已认证 |
| CSRF令牌 | GET接口返回，页面内存保存，POST请求头提交 | 不是登录凭据，不授予角色权限 |

本课继续使用默认`HttpSessionCsrfTokenRepository`，期望令牌在服务端Session中，Cookie只是关联该会话的标识。没有使用`CookieCsrfTokenRepository`，不要去读取`XSRF-TOKEN` Cookie，也不要误用另一方案的`X-XSRF-TOKEN`头。默认实现与条件见[Security 7.0 CSRF文档](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)。

### 实操：确认当前没有公开令牌入口

在另一个终端执行：

```bash
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/public/info
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/csrf
```

第五课起点结果分别是200、401、401。最后一个请求不在允许清单，也没有本课Controller，安全层先要求认证；不能靠给前端变量随便命名为`csrfToken`就获得有效令牌。

### 小总结

身份认证、会话关联和CSRF校验承担不同职责。本课用允许匿名的探针隔离令牌机制，得到CSRF令牌后仍可能是匿名用户。

## 二、开放受控的令牌获取入口

### 理论：从请求属性读取，触发默认的延迟加载

CSRF过滤器先为请求准备令牌获取能力，不一定立刻加载Session。只有需要使用令牌时才读取或生成，这叫延迟加载。本课Controller读取`CsrfToken`参数的值，会实际触发该过程：新匿名访问也因此能够建立保存期望令牌的会话。

Spring Security为MVC登记的参数解析器从请求属性取得`CsrfToken`，并不是Controller自己随机生成一个字符串。默认请求处理器`XorCsrfTokenRequestAttributeHandler`还会对响应中的表示加入随机掩码，降低压缩响应泄露令牌的风险；服务端校验时按配套方式解析。我们直接返回框架提供的表示，后续原样放回请求头，不手工解码或重新生成。

入口只返回`headerName`、`parameterName`与`token`，不返回Session标识或用户信息。使用JSON且禁止缓存，前端不把内容写进观察区。这是“受控”的具体范围；部署到真实站点时还需要保持合适的同源与跨源访问边界，不能公开给任意来源读取令牌。

### 实操：新增CsrfController，并只放行GET入口

停止后端，在跟写包目录新增`CsrfController.java`：

```java
package cn.ningbingjian.learnjava.security.lesson006;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/csrf")
    ResponseEntity<TokenResponse> csrf(CsrfToken csrfToken) {
        // 读取请求属性中的令牌，触发延迟加载；不返回Session标识或用户信息。
        var body = new TokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record TokenResponse(String headerName, String parameterName, String token) { }
}
```

`csrfToken.getToken()`读取请求中的框架表示；`TokenResponse`是本课响应记录，明确限制字段。`CacheControl.noStore()`让响应带上`Cache-Control: no-store`。它不把会话Cookie暴露给JavaScript；浏览器自动处理服务器的Set-Cookie。

在跟写`SecurityConfig.java`的GET `/public/info`规则**之前**插入一行：

```java
                .requestMatchers(HttpMethod.GET, "/csrf").permitAll()
```

保留第四课`ERROR`分派许可、默认拒绝以及全部认证配置；没有关闭CSRF。重新构建并启动同一个jar。

在请求终端开启`bash`，以下获取与提交命令都在该终端连续执行。用权限受限的临时目录保存Cookie和JSON，输出只显示状态，不显示令牌内容。Node是本课已有前端运行环境，这里只用于读取JSON字段。

```bash
umask 077
csrf_work=$(mktemp -d)
curl -sS -c "$csrf_work/cookies.txt" -o "$csrf_work/token.json"   -w '初始化HTTP %{http_code}\n' -H 'Accept: application/json' http://127.0.0.1:8080/csrf
csrf_header=$(node -e 'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).headerName)' "$csrf_work/token.json")
csrf_value=$(node -e 'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).token)' "$csrf_work/token.json")
```

状态应为200。Cookie文件中保存当前会话，变量只供后续请求使用；不要echo这些值或提交临时文件。真实HTTP测试还检查JSON字段、禁止缓存以及JSESSIONID的HttpOnly属性。若没有新增允许规则，仍会在安全层401，Controller无法代替授权配置。

### 小总结

GET入口把框架令牌传给当前页面，同时建立或复用Session。返回值与服务器期望值之间的转换由默认处理器负责，课程代码不手工生成或解析令牌。

## 三、用无业务副作用的POST隔离CSRF检查

### 理论：permitAll只放行授权，不跳过前面的过滤器

`permitAll()`表达的是请求授权许可，CSRF过滤器在请求到达这一步及Controller之前检查写请求。默认保护POST等非安全方法，而GET、HEAD、OPTIONS、TRACE不要求同样的令牌。不能用GET修改业务状态来躲过校验。

本课探针允许匿名，且只返回固定消息、记录方法执行标记，不写工单或其他业务数据。这样，合法令牌的请求可以到达方法，缺令牌的403则能与“方法未执行”联系起来。不能把这个公开探针的权限直接搬到未来敏感业务接口。

校验顺序可以从固定版本[CsrfFilter 7.0.7的doFilterInternal](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/csrf/CsrfFilter.java)核对：加载延迟令牌、准备请求属性、判断方法是否需要保护；需要时解析实际提交值，与仓库期望值比较。缺失或不匹配时调用拒绝访问处理器并返回，不继续到Controller。第四课允许内部ERROR分派，避免错误呈现阶段再次被兜底拦住；本课保留这一设置。

### 实操：新增探针，比较“只带Cookie”与“Cookie加令牌”

停止后端，在跟写包目录新增`CsrfProbeController.java`：

```java
package cn.ningbingjian.learnjava.security.lesson006;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfProbeController {
    private static final Logger log = LoggerFactory.getLogger(CsrfProbeController.class);

    @PostMapping("/csrf-probe")
    Map<String, String> probe() {
        log.info("CSRF_PROBE_HANDLER_REACHED");
        // 仅确认请求进入方法，不创建、修改或删除任何业务资源。
        return Map.of("message", "CSRF probe accepted; no business data changed");
    }
}
```

`CSRF_PROBE_HANDLER_REACHED`是方法执行证据；不打印令牌、Cookie或请求头。在GET `/csrf`规则后再插入：

```java
                .requestMatchers(HttpMethod.POST, "/csrf-probe").permitAll()
```

新增区域最终应为GET `/csrf`、POST `/csrf-probe`、GET `/public/info`三个允许规则，其余保持第五课。重新构建启动。**服务重启后，旧内存会话失效，先重新执行第二节获取令牌的curl及两条Node赋值命令**；继续复用临时目录即可，获取后会更新文件和变量。

先预测再执行：只带Cookie仍缺少主动提交的令牌，应403；增加同一会话令牌后应200。

```bash
curl -sS -i -b "$csrf_work/cookies.txt" -X POST   -H 'Accept: application/json' http://127.0.0.1:8080/csrf-probe
curl -sS -i -b "$csrf_work/cookies.txt" -X POST   -H 'Accept: application/json' -H "$csrf_header: $csrf_value" http://127.0.0.1:8080/csrf-probe
```

第一条不增加探针方法日志；第二条返回200与`CSRF probe accepted; no business data changed`，方法日志增加一次。即使`permitAll`允许匿名，令牌仍必须满足CSRF条件。测试也验证正确Basic但无令牌仍403，避免把密码当成令牌的替代品。

这里的403沿用框架拒绝处理，没有新增统一的CSRF业务错误代码。本课只在固定实验条件和方法执行证据下解释原因，不能对任意接口的403一律断言为缺令牌。

### 小总结

公开授权不关闭CSRF。通过无业务副作用的探针，可以单独检查Cookie与令牌组合；安全失败发生在Controller之前，日志和HTTP状态应一起观察。

## 四、错误令牌、错误会话与原位修复

### 理论：令牌必须对应当前会话，而不只是“存在一个值”

默认仓库按Session读取期望令牌。把A会话的令牌交给B会话，或只给令牌不给Cookie，服务端无法按A会话完成正确校验。JSESSIONID的HttpOnly属性意味着JavaScript不能通过document.cookie读取它，但浏览器仍能自动在合适的请求中发送，前端无需手工拼Cookie头。

重复GET `/csrf`返回的文本可能不同，这是默认随机掩码变化，不代表存储期望值每次都轮换，也不表示令牌是一次性验证码。只要会话与期望值没失效，同一会话先前取得的表示仍可能通过。相反，会话被清除、过期或某些认证生命周期操作清理令牌后，需要重新取得；下一课会处理登录后的重新获取，不在这里自制刷新协议。

对应源码是[HttpSessionCsrfTokenRepository 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/csrf/HttpSessionCsrfTokenRepository.java)的loadToken/saveToken，以及[XorCsrfTokenRequestAttributeHandler 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/csrf/XorCsrfTokenRequestAttributeHandler.java)的handle/resolveCsrfTokenValue：前者维护会话中的期望值，后者处理响应表示与提交值的转换。

### 实操：每次只改变一个条件，最后恢复正常请求

保持第三节后端与刚取得的会话，执行：

```bash
curl -sS -i -b "$csrf_work/cookies.txt" -X POST   -H 'Accept: application/json' -H "$csrf_header: invalid-for-lesson" http://127.0.0.1:8080/csrf-probe
curl -sS -i -X POST -H 'Accept: application/json'   -H "$csrf_header: $csrf_value" http://127.0.0.1:8080/csrf-probe
curl -sS -i -b "$csrf_work/cookies.txt" -X POST   -H 'Accept: application/json' -H "$csrf_header: $csrf_value" http://127.0.0.1:8080/csrf-probe
```

前两条分别改变令牌内容、去掉Cookie，均403且不执行方法；第三条恢复同一会话和正确令牌，200。故意错误只在这两条请求里，不修改默认后端规则，也不把错误值覆盖到`csrf_value`。

随后用同一Cookie请求GET `/hello`，即使你把CSRF令牌也加在头里仍401：匿名Session与CSRF令牌没有完成用户认证。再看自动测试中的“不同会话令牌拒绝”“两次掩码都可用”“会话失效后旧令牌拒绝、重新获取恢复”三个场景，把文本变化与真正失效分开。

### 小总结

修复CSRF请求要一起检查令牌和它所属的会话。文本变化不必然等于过期；错误实验必须恢复正确组合，而不是通过禁用防护消除403。

## 五、让前端初始化令牌并封装提交

### 理论：同源Cookie由浏览器管理，POST不自动重发

本课增加独立CSRF观察区，页面加载时发GET `/api/csrf`，把返回对象保存在模块变量中。请求用`credentials: 'same-origin'`接收并携带同源会话Cookie；提交时读取响应给出的头名并填入令牌。浏览器通过Vite同源代理访问后端，JavaScript不读取或设置HttpOnly Cookie。

第五课匿名/Basic按钮仍用`credentials: 'omit'`，不会自动使用这份Cookie；CSRF区明确展示自己的请求条件，两个观察区不共用结果。这里没有把所有fetch都全局改成携带会话，也没有使用跨源`include`来掩盖同源模型。

初始化失败时清空缓存并禁用提交；正常模式POST遇403时提示令牌或会话可能失效，重新获取后由学生手动提交。网络失败可能只是响应未到达，服务器却已经处理请求，所以不能自动重发POST。本探针没有业务副作用，仍保留这个后续业务需要的原则。

### 实操：复制骨架，新增csrf-lab.js

在**仓库根目录**执行：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-006-csrf-before-login"
lesson_work="$PWD/work/security-followalong/21-04-006-csrf-before-login"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$module_source/21-04-005-users-and-password-encoding/frontend/src/main.js" "$lesson_work/frontend/src/main.js"
```

新增布局只提供状态区和五个操作按钮，初始提交按钮禁用。在跟写`frontend/src/`新增`csrf-lab.js`。先理解数据流：`csrf`只在成功解析本课三字段契约后赋值；`busy`串行化本区请求；`updateControls`根据这两个状态决定能否提交。

`initializeCsrf`主动使用no-store并且不显示JSON原文，避免令牌进入观察区。`submitProbe`从内存取令牌：normal对应`valid`，其余三种模式分别省略头、换成固定错误值、省略Cookie，用于第四节的对照；正常入口不会自动选择错误模式。每个请求只访问固定的同源实验路径。

```javascript
const state = document.querySelector('#csrf-state');
const summary = document.querySelector('#csrf-summary');
const status = document.querySelector('#csrf-http-status');
const responseBody = document.querySelector('#csrf-response');
const initialize = document.querySelector('#csrf-init');
const submitButtons = [...document.querySelectorAll('button[data-csrf-mode]')];
let csrf = null;
let busy = false;

function updateControls() {
  initialize.disabled = busy;
  submitButtons.forEach((button) => { button.disabled = busy || !csrf; });
}

async function initializeCsrf() {
  if (busy) return;
  busy = true;
  csrf = null;
  updateControls();
  state.textContent = '正在准备令牌…';
  try {
    const response = await fetch('/api/csrf', {
      headers: { Accept: 'application/json' }, credentials: 'same-origin',
      cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw new Error(`初始化得到HTTP ${response.status}`);
    const value = await response.json();
    // 本课固定默认仓库契约，避免把任意响应字段当作请求头。
    if (value.headerName !== 'X-CSRF-TOKEN' || value.parameterName !== '_csrf'
        || typeof value.token !== 'string' || !value.token) throw new Error('令牌响应格式不符合本课契约');
    csrf = value;
    state.textContent = '令牌已就绪（内容不展示）';
    summary.textContent = '浏览器会在同源请求中携带会话Cookie。现在可以手动提交POST。';
  } catch {
    state.textContent = '未取得令牌';
    summary.textContent = '请检查后端与网络，再点击“重新取得令牌”。初始化失败不会自动提交。';
  } finally {
    busy = false;
    updateControls();
  }
}

async function submitProbe(mode) {
  if (busy || !csrf) return;
  busy = true;
  updateControls();
  status.textContent = 'POST请求中';
  responseBody.textContent = '等待响应。';
  const headers = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (mode !== 'missing') headers[csrf.headerName] = mode === 'wrong' ? 'invalid-for-lesson' : csrf.token;
  try {
    const response = await fetch('/api/csrf-probe', {
      method: 'POST', headers, body: '{}',
      credentials: mode === 'no-cookie' ? 'omit' : 'same-origin',
      redirect: 'error', signal: AbortSignal.timeout(8000),
    });
    status.textContent = `HTTP ${response.status}`;
    responseBody.textContent = (await response.text()) || '（空响应体）';
    summary.textContent = response.ok
      ? '本次POST通过并进入实验方法，没有修改业务数据；这不表示已经登录。'
      : response.status === 403
        ? '本次实验被拒绝。对照令牌与会话Cookie条件，不要把所有403都归因为未登录。'
        : '收到了其他响应，请检查服务状态；不会自动重发POST。';
    if (response.status === 403 && mode === 'valid') {
      csrf = null;
      state.textContent = '令牌或会话可能已失效，请重新取得令牌';
    }
  } catch {
    status.textContent = '未取得完整响应';
    summary.textContent = '无法确定本次POST是否被处理。先检查服务与网络，不自动重发。';
    responseBody.textContent = '没有可展示的完整HTTP响应。';
  } finally {
    // 不保留每次请求的头对象，也不把令牌写入页面、URL或持久存储。
    delete headers['X-CSRF-TOKEN'];
    busy = false;
    updateControls();
  }
}

initialize.addEventListener('click', initializeCsrf);
submitButtons.forEach((button) => {
  button.addEventListener('click', () => submitProbe(button.dataset.csrfMode));
});
initializeCsrf();
```

在跟写`frontend/src/main.js`末尾新增一行导入：

```javascript
import './csrf-lab.js';
```

模块加载后调用`initializeCsrf()`，但不会发POST。第五课请求函数、密码清空与错误显示全部保留。后端在8080运行，另开终端到跟写`frontend/`执行：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`，按以下顺序验收：

| 操作 | 预期及证据 |
| --- | --- |
| 页面初始化 | 显示令牌已就绪，后台只有GET获取，没有自动POST |
| 携带令牌提交 | 200，探针日志增加，无需Basic身份 |
| 故意不带令牌 | 403，探针日志不增加 |
| 故意带错误令牌 | 403，探针日志不增加 |
| 故意不带会话Cookie | 403，Network中的POST无Cookie |
| 再点击携带令牌提交 | 恢复200，没有修改后端配置 |
| 清除当前站点Cookie后正常提交 | 403，提示可能失效，提交禁用 |
| 重新取得令牌，再手动提交 | 重新就绪后200，GET初始化不替你自动重发POST |

查看Network只核对是否有Cookie/令牌头、方法和状态，不复制、截图或发布真实值。获取接口的响应不进入结果区，页面不把令牌写到URL、localStorage或sessionStorage；这不保证JavaScript内存安全擦除，也不是XSS防护替代品。

开发代理仍使用第四课的`server.cors: false`；这只让同源OPTIONS继续转发。当前取得令牌的GET与POST都是浏览器对自身前端源的请求，本课没有验证浏览器直连后端的CORS预检或跨域Cookie。

### 小总结

前端只在内存中保留令牌，浏览器负责同源Cookie；初始化、提交和错误恢复是明确分开的操作。得到新令牌后也要重新决定是否提交，不能自动重发写请求。

## 六、完成验证并准备真正登录

### 理论：通过CSRF不等于通过认证与业务授权

本课探针允许匿名，只用于观察CSRF。真正业务POST往往同时需要认证、权限与CSRF：通过其中一项不意味着其他项通过。下一课登录POST本身也需要遵守防护；不能因为登录发生在用户认证之前，就关闭这一步。

验证需要覆盖令牌来源、会话关联、真实提交、Controller是否执行和恢复过程。只模拟一个“合法用户”或把所有响应写成200无法证明这些条件。当前没有新增统一403错误契约，默认错误正文的字段也不是本课稳定API承诺。

### 实操：复制完整测试，在真实HTTP与浏览器中检查

在**仓库根目录**执行：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-006-csrf-before-login"
lesson_work="$PWD/work/security-followalong/21-04-006-csrf-before-login"
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

测试自带仅限测试的密码，不依赖个人实验密码。后端使用真实HTTP和Cookie容器，覆盖默认头名、HttpOnly/no-store、合法/缺失/错误/异会话令牌、掩码变化、身份与令牌互不替代、会话失效与恢复；保留第五课全部用户与授权回归。

浏览器测试运行真实Java与Vite，检查Cookie和令牌头是否存在、正常/错误请求对应的方法日志、清Cookie后的拒绝与手动恢复、离线初始化和窄屏键盘。隔离的`.e2e-work/before-csrf/`移除本课入口，证明未开放入口时初始化失败且不能提交；正式源码保持完成状态。网络追踪留存关闭，令牌与Cookie不进入报告，截图只显示状态。具体结果见[验证记录](01-验证记录.md)。

结束后在请求bash终端清理自己生成的临时文件与变量：

```bash
rm "$csrf_work/token.json" "$csrf_work/cookies.txt"
rmdir "$csrf_work"
unset csrf_work csrf_header csrf_value
exit
```

停止后端与前端，在后端启动bash中`unset LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD`，再`exit`。本课不提交Cookie文件、令牌JSON或实际密码。

### 小总结

本课建立的是登录之前的请求条件，不是登录完成状态。真实HTTP负责核对协议组合，浏览器负责核对Cookie行为、交互与恢复，两类证据共同支撑下一课。

## 本课总结

本课在第五课三用户工程上保留默认CSRF防护，通过受控GET入口读取框架令牌，并以同会话Cookie与令牌头提交无业务副作用的POST。默认仓库保存Session中的期望值，请求处理器负责响应掩码与提交解析；GET初始化可以创建匿名Session，取得令牌并不产生已登录身份。

`permitAll`只解决授权许可，写请求仍先受CSRF约束。只有Cookie不能替代令牌，只有令牌或其他会话Cookie也不能组成正确请求；正确Basic密码同样不能代替CSRF。拒绝时应结合请求条件和Controller执行证据定位，不能把全部403概括为未登录或缺令牌。

重复获取的文本变化可能只是掩码，真正的会话或令牌失效才要求重新获取。页面用同源Cookie策略和内存令牌封装提交，错误后显式恢复，不展示敏感值，也不自动重发POST。实际接入登录或业务写操作时，先检查凭据、会话、令牌和授权四个条件，再决定成功处理与失败恢复。

## 理解检查与后续衔接

1. POST `/csrf-probe`已经permitAll，缺少令牌为什么仍403？哪个阶段终止了请求？
2. 为什么JavaScript不用读取HttpOnly的JSESSIONID，也能让POST携带会话？`omit`改变了哪个条件？
3. 两次GET拿到不同文本，为什么旧文本仍可能有效？它与清Cookie后的失效有什么区别？
4. 取得令牌后GET `/hello`为什么仍401？正确Basic但无令牌POST为什么仍403？
5. 网络失败后为什么不能自动重发POST？重新取得令牌后页面应该自动提交吗？

下一课是[第007课：独立前端表单登录与会话建立](../00-模块学习大纲.md#lesson-007)。使用本课的令牌初始化与同源会话条件，提交真正的登录表单，并处理认证后的令牌重新获取。
