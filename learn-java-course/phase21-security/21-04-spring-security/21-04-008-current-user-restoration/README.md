# 21-04-008 当前用户接口与刷新后的身份恢复

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-008) · [上一课](../21-04-007-form-login-session/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第七课已经能从独立前端登录，并通过会话Cookie访问受保护接口。但刷新页面会重新加载JavaScript，页面中的“本次登录成功”随之消失；Cookie和服务端会话却可能仍有效。另一方面，用户在选择框选中admin，也不代表当前登录身份已经变成admin。

本课增加GET `/me`，从当前请求的认证结果投影出用户名与权限标识。页面启动、刷新以及每次登录尝试后都向后端核对，明确显示加载、已认证、未登录或暂时无法确认。完成后你应能解释浏览器刷新为何无需重发密码、401为何需要清理身份、网络故障为何不能冒充401，以及为什么修改页面文字不能获得后端权限。

前置为第007课；沿用[版本基线](../01-版本基线.md)的JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9，前端Node22.15.0、npm10.9.2、Vite8.2.2。没有新依赖；仍使用三个实验密码环境变量和内存用户。退出交互留待第009课，角色和业务授权在第三篇展开。

## 准备：从第七课建立独立跟写工程

在**仓库根目录**执行macOS/Linux shell命令。来源是仓库第七课完成版，目标存在时停止，避免覆盖自己的练习。暂不复制测试，以便观察当前尚未开放的/me入口。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-007-form-login-session"
lesson_work="$PWD/work/security-followalong/21-04-008-current-user-restoration"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson008" "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson007/lesson008/g' -e 's/21-04-007/21-04-008/g' -e 's/Form Login and Session/Current User and Restoration/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson007/*.java; do
  sed 's/lesson007/lesson008/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson008/$(basename "$file")"
done
sed 's/lesson007/lesson008/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

后面的Java文件都位于跟写`src/main/java/cn/ningbingjian/learnjava/security/lesson008/`。父POM只供继承，构建在**跟写单课目录**进行。先停止前课占用8080/5173的进程。在跟写单课目录执行`bash`，再输入以下命令；密码至少8个字符、UTF-8不超过72字节，不使用个人真实密码。

```bash
read -r -s -p 'member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
read -r -s -p 'support实验密码：' LESSON_SUPPORT_PASSWORD
printf '\n'
read -r -s -p 'admin实验密码：' LESSON_ADMIN_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
mvn clean package
java -jar target/spring-security-lesson008-1.0-SNAPSHOT.jar
```

后端仅监听`127.0.0.1:8080`。后续修改时在这个终端停止、构建、重启，环境变量保留。另开请求终端执行`bash`，用于以下连续的HTTP实验。
## 一、浏览器刷新丢失了什么，保留了什么

### 理论：页面状态与服务器认证有不同的生命周期

JavaScript模块变量属于当前页面，刷新后重新初始化。会话Cookie由浏览器管理，只要它仍适用于当前站点，就可能随刷新后的同源请求发送；服务端Session则继续保留第七课已保存的SecurityContext。这三者不能合并为一个“登录变量”。

`/hello`返回200只能证明该次请求通过认证，固定问候文本没有告诉页面具体用户是谁。因此本课需要一个受保护的当前用户接口；它不接受一个用户名再查询“指定用户”，而是读取这次请求真正的认证身份。

会话恢复不会重新提交密码。根据[Security 7.0认证持久化说明](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/persistence.html)，SecurityContextRepository承担跨请求的认证关联；当前默认组合同时涉及请求属性和HttpSession。第七课表单过滤器已经显式保存上下文，本课读取这份既有状态，不另造登录协议。

### 实操：在已认证会话中观察尚未开放的/me

沿用第七课的表单协议，在请求bash中输入与后端一致的member实验密码。临时目录仅当前用户可读；请求体、Cookie和令牌保存在其中，输出仅显示状态。`get_csrf`与`post_login`函数供后文复用。

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
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '已认证会话的/me HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
curl -sS -o /dev/null -w '匿名/me HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
```

结果依次200、200、403、401。当前源码仍是第七课，`/me`没有列入允许规则；已认证请求命中默认拒绝得到403，匿名请求则触发已有认证入口得到401。不能把这个403理解为“密码没有生效”，因为刚才的登录200已完成认证，缺少的是新接口及其精确规则。

### 小总结

页面刷新重建的是界面内存，服务端会话可能仍存在。需要一个读取当前认证的接口填补身份展示，不能从选择框或历史成功文字推断用户。

## 二、只返回必要的当前用户字段

### 理论：Authentication是认证结果，不应直接作为响应对象

认证结果Authentication包含当前主体、权限以及框架细节。主体（principal）通常是用户对象，可能包含密码散列或其他不应返回的字段；credentials是否已被框架清理，也不是接口泄露防护的稳定依据。即使某次响应看不到密码，也不应该把整个框架对象序列化。

本课明确构造只包含`username`与`authorities`的响应记录。用户名来自`authentication.getName()`；权限标识来自`getAuthorities()`中的GrantedAuthority，转为字符串并排序，便于观察。返回的是允许展示的字段投影，不是原对象的可变引用。响应使用no-store，前端读取时也禁用缓存，减少旧身份响应被复用。

这里展示完整的权限标识，而不把它们都称为角色。当前7.0.7用户名密码认证会得到`FACTOR_PASSWORD`与例如`ROLE_MEMBER`：前者表示密码认证因素，后者来自第五课配置的业务角色。`AbstractUserDetailsAuthenticationProvider.createSuccessAuthentication`把用户权限映射后再加入密码因素，见[7.0.7对应源码](https://github.com/spring-projects/spring-security/blob/7.0.7/core/src/main/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.java)。本课不会为了只显示角色而丢掉这个真实标识，也不由此声称已实现多因素或工单权限。

### 实操：增加Controller并仅允许已认证的GET

停止后端，在跟写包目录新增`MeController.java`。`Authentication`参数由MVC从当前请求主体解析，不来自URL中的`username`。该方法依赖下面的已认证规则，所以不能同时把它改为匿名公开再假设参数一定非空。日志只记录方法执行标记，不记录用户名、权限或其他认证字段。

```java
package cn.ningbingjian.learnjava.security.lesson008;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(Authentication authentication) {
        // 仅映射展示字段，不序列化Authentication、principal或credentials。
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).sorted().toList();
        log.info("ME_HANDLER_REACHED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUser(authentication.getName(), authorities));
    }

    record CurrentUser(String username, List<String> authorities) { }
}
```

然后在跟写`SecurityConfig.java`的GET `/hello`规则之后、`anyRequest().denyAll()`之前新增：

```java
                .requestMatchers(HttpMethod.GET, "/me").authenticated()
```

其余第七课配置保留。这里不是`permitAll`，也不是`/me/**`通配许可；HEAD、POST及近似路径仍按第四课默认拒绝。完成两个修改后重新构建并启动同一个jar。服务重启使旧内存会话失效，先重新取得令牌并登录：

```bash
get_csrf
post_login
curl -sS -b "$login_work/cookies.txt" -o "$login_work/me.json" -w '当前用户HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
node -e 'const u=JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")); console.log(JSON.stringify({username:u.username,authorities:u.authorities}))' "$login_work/me.json"
```

三次请求均200，最后显示：

```json
{"username":"member","authorities":["FACTOR_PASSWORD","ROLE_MEMBER"]}
```

这是本课固定身份下实测的字段与排序，不含实际凭据。support/admin对应`ROLE_SUPPORT`/`ROLE_ADMIN`。Controller日志增加`ME_HANDLER_REACHED`，说明这次请求通过安全链到达方法。

### 小总结

当前用户接口从认证结果构造明确的展示契约，避免直接返回框架对象。权限列表同时可能包含认证因素与业务角色，页面展示并不意味着已经实施角色授权。

## 三、沿真实请求解释身份恢复和拒绝

### 理论：读取会话、设置上下文，再解析Controller参数

需要解释的具体问题是：第二次GET没有密码，Controller的Authentication从哪里来？锁定Security7.0.7的[SecurityContextHolderFilter.doFilter](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/context/SecurityContextHolderFilter.java)：它从仓库取得延迟上下文，设置到当前请求线程使用的上下文策略中，继续过滤器链，最后清理线程上下文。这一过滤器负责加载，不在请求结束时自动代替登录代码保存认证。

[HttpSessionSecurityContextRepository.loadDeferredContext](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/context/HttpSessionSecurityContextRepository.java)在需要时从现有Session读取SecurityContext；没有Session或没有有效上下文时无法恢复已有认证。浏览器只发送标识，认证对象并不存放在Cookie正文里。也不要把“延迟加载”误解为每次GET都重新按密码认证。

安全请求包装器[SecurityContextHolderAwareRequestWrapper.getUserPrincipal](https://github.com/spring-projects/spring-security/blob/7.0.7/web/src/main/java/org/springframework/security/web/servletapi/SecurityContextHolderAwareRequestWrapper.java)从上下文读取认证，排除未认证或匿名情形。随后Framework7.0.9的[ServletRequestMethodArgumentResolver](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ServletRequestMethodArgumentResolver.java)对无注解的Principal类型参数调用`request.getUserPrincipal()`；Authentication继承Principal，因此本课参数获得的是当前认证，而不是请求参数绑定。

安全规则在Controller之前要求认证。有效会话允许GET；没有、伪造或已失效的会话得到401，Controller不执行。页面收到401应清除用户名与权限；网络错误、503或格式异常没有证明会话未登录，只能表明当前无法确认，也应撤下旧身份，避免把旧值当成新结论。当前接口仍支持继承的显式Basic认证，前端恢复流程则只使用会话Cookie。

### 实操：改变一个请求条件，最后恢复正确GET

继续第二节已认证会话，先不带Cookie请求；再用同一Cookie把查询参数伪造为admin；然后带合法CSRF做一次未允许的POST，最后恢复GET。

```bash
curl -sS -o /dev/null -w '不带会话HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
curl -sS -b "$login_work/cookies.txt" -o "$login_work/me.json" -w '伪造查询参数HTTP %{http_code}\n' \
  -H 'Accept: application/json' 'http://127.0.0.1:8080/me?username=admin'
node -e 'console.log(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).username)' "$login_work/me.json"
get_csrf
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '未允许的POST HTTP %{http_code}\n' \
  -H 'Accept: application/json' -H "$csrf_header: $csrf_value" -X POST http://127.0.0.1:8080/me
curl -sS -b "$login_work/cookies.txt" -o /dev/null -w '恢复GET HTTP %{http_code}\n' \
  -H 'Accept: application/json' http://127.0.0.1:8080/me
```

状态依次401、200、200、403、200，查询参数实验仍显示member。POST特意先取得登录后的有效令牌，排除CSRF缺失，才把拒绝定位为方法授权边界；恢复只需改回已允许的GET，不改变后端防护。成功GET增加方法日志，不带会话和错误方法都不到达方法。

自动测试另验证伪造JSESSIONID不能建立身份，服务端会话失效后GET返回401。失效实验借用现有框架POST `/logout`使会话失效，仅用于后端验证条件；本课没有提前增加退出页面，完整退出与CSRF收尾仍在第009课。

### 小总结

有效Cookie关联服务端上下文，过滤器加载它，MVC才把认证交给Controller。请求参数和页面状态都不能替代认证；诊断拒绝时必须区分会话、CSRF与方法许可。

## 四、把身份查询接入已有会话请求队列

### 理论：状态要有依据，请求完成顺序也要受控

本课身份有四个阶段：loading表示正在查询，authenticated表示收到合法200契约，anonymous表示后端401，unavailable表示暂时无法确认。开始查询就清空旧用户；失败后不展示过期身份。状态保存在页面内存，不读取localStorage中的“用户对象”作为登录证明。

第七课已经把登录、CSRF和探针放进串行队列。本课继续复用，让“登录POST→重新获取令牌→查询/me”成为一个连续操作，避免旧身份查询晚于新登录返回，覆盖页面结果。无论本次登录成功、失败还是结果不明，最后都查询/me：已经登录的member输错admin密码，仍可能得到member；不能简单根据登录401清空有效会话。

只带会话访问`/hello`若明确收到401，也立即清除当前身份。但一次200并不包含用户名，不能用它给身份状态随意填名。其他标签页或服务器主动改变会话时，当前页面不会立刻获知；本课在启动、登录后及手动查询时核对，不声称实现实时跨页同步。

### 实操：复制布局与旧请求层，再替换共享客户端

在**仓库根目录**执行，建立跟写前端。复制本课HTML/CSS提供身份区节点；第七课的主入口、登录与探针交互继续保留，核心变化按下面代码完成。先不启动页面，待第五节加入展示模块。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-008-current-user-restoration"
previous="$module_source/21-04-007-form-login-session"
lesson_work="$PWD/work/security-followalong/21-04-008-current-user-restoration"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$previous/frontend/src/"{main.js,login.js,csrf-lab.js,session-client.js} "$lesson_work/frontend/src/"
```

替换跟写`frontend/src/session-client.js`。先看三个变化：新增identity及其通知；登录末尾在同一个队列操作内调用`loadIdentity`；会话只读请求401时撤下身份。原CSRF缓存、错误探针和表单编码逻辑保留。

`loadIdentity`先切换loading。401无需把错误正文当用户解析；200必须有JSON类型和正确字段形状，再构造只含展示字段的新对象。网络、服务或契约错误走unavailable，不把异常原文回显到页面。`refreshIdentity`给外部暴露排队入口；内部登录直接调用加载函数，避免在队列任务中等待自己后面的新任务。

```javascript
let csrf = null;
let csrfState = 'empty';
let identity = { phase: 'loading', user: null };
let pending = 0;
let queue = Promise.resolve();
const listeners = new Set();

function notify() {
  const state = { busy: pending > 0, ready: csrf !== null, csrfState, identity };
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
    await loadIdentity();
    return { ...outcome, csrfReady };
  });
}

export function sessionHello() {
  return serial(async () => {
    const response = await request('/api/hello', { headers: { Accept: 'application/json' } });
    if (response.status === 401) {
      identity = { phase: 'anonymous', user: null }; notify();
    }
    return { status: response.status, text: await response.text() };
  });
}

async function loadIdentity() {
  // 开始核对就撤下旧身份，不能让加载或故障期间的旧文字冒充当前结论。
  identity = { phase: 'loading', user: null };
  notify();
  try {
    const response = await request('/api/me', { headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (response.status === 401) {
      identity = { phase: 'anonymous', user: null };
    } else {
      if (response.status !== 200 || !response.headers.get('content-type')?.includes('application/json')) {
        throw new Error('身份接口响应异常');
      }
      const value = await response.json();
      if (typeof value?.username !== 'string' || !value.username
          || !Array.isArray(value.authorities) || !value.authorities.every(item => typeof item === 'string')) {
        throw new Error('身份字段不匹配');
      }
      identity = { phase: 'authenticated', user: { username: value.username, authorities: [...value.authorities] } };
    }
  } catch {
    // 网络、服务或格式错误只表示无法确认，不伪装成401或沿用旧身份。
    identity = { phase: 'unavailable', user: null };
  } finally { notify(); }
}

export function refreshIdentity() { return serial(loadIdentity); }
```

`cache: 'no-store'`与响应no-store配合；`credentials: 'same-origin'`仍由浏览器附带Cookie，JavaScript不读取HttpOnly Cookie。`Accept`明确要求JSON，禁止自动跳转，沿用超时。当前学习环境仍是同源Vite代理，不代表已配置跨源Cookie。

### 小总结

身份状态取自/me的响应，登录状态和身份查询结果分别保留。复用队列保证这些操作按顺序完成，并在错误时撤下旧身份而不捏造未登录结论。

## 五、展示当前身份并验证刷新恢复

### 理论：界面负责展示，服务器负责认证与授权

用户名与权限通过`textContent`写入节点，避免把数据当HTML执行；但这只是安全展示方式，不能阻止用户在开发者工具里改自己页面。后端仍必须检查每次请求，不能因为前端显示了admin就放行。

账号选择框是“下一次准备提交哪个用户名”，当前身份区是“最近一次后端核对的会话身份”，两者可以不同。页面刷新后只自动发送GET获取令牌和查询身份，不重发用户名密码，不从本地存储恢复一个自称已登录的用户。

### 实操：新增展示模块并连接初始化

在跟写`frontend/src/`新增`current-user.js`。订阅回调根据阶段设置文案，每次都重写用户名和权限，包括清理分支；手动查询按钮受共享busy状态控制。模块加载时只发身份GET。

```javascript
import { refreshIdentity, subscribeSession } from './session-client.js';

const panel = document.querySelector('#current-user');
const status = document.querySelector('#current-user-status');
const name = document.querySelector('#current-user-name');
const authorities = document.querySelector('#current-user-authorities');
const refresh = document.querySelector('#current-user-refresh');

subscribeSession(({ busy, identity }) => {
  refresh.disabled = busy;
  panel.dataset.state = identity.phase;
  status.textContent = identity.phase === 'loading' ? '正在向后端确认当前身份…'
    : identity.phase === 'authenticated' ? '当前会话已认证，身份来自 /me。'
      : identity.phase === 'anonymous' ? '当前会话未登录（HTTP 401）。'
        : '暂时无法确认身份，请检查网络与服务后重新查询。';
  name.textContent = identity.user?.username || '—';
  authorities.textContent = identity.user?.authorities.join('、') || '—';
});

refresh.addEventListener('click', refreshIdentity);
refreshIdentity();
```

在跟写`frontend/src/main.js`末尾追加一行：

```javascript
import './current-user.js';
```

在跟写`frontend/src/login.js`找到等待文案，替换这一行，让读者知道登录后还会核对身份：

```javascript
  result.textContent = '等待登录响应，然后更新令牌并核对当前身份…';
```

其他登录结果文案继续描述“本次尝试”。后端保持8080运行，另开终端进入跟写`frontend/`：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`，用新会话按下表操作。只观察请求方法、Cookie是否存在和状态，不发布Network中的实际密码、Cookie或令牌。

| 操作 | 预期及其原因 |
| --- | --- |
| 新会话加载 | 查询后401，显示未登录，用户名/权限为破折号 |
| member正确表单登录 | 登录200，随后获取令牌、查询/me，显示member与FACTOR_PASSWORD、ROLE_MEMBER |
| 刷新页面 | 再次GET/me恢复相同身份，没有自动POST登录，密码框为空 |
| 选择admin但不提交 | 当前身份仍member，选择框不能改变会话 |
| admin输入错误密码 | 本次登录401；/me仍可200返回原member |
| 点击旧匿名请求按钮 | 它使用omit，结果区401；不把这个不同凭据条件误用于清除顶部会话身份 |
| 清除本实验站点Cookie，再重新查询 | loading时撤下旧身份，收到真实401后显示未登录 |
| 使用浏览器离线模式，再重新查询 | 显示暂时无法确认，不显示旧用户名或假401 |
| 恢复网络，再手动查询 | 若Cookie和服务器会话仍有效，重新显示真实用户 |

故意清Cookie只作用于本实验站点；恢复登录时先重新取得CSRF令牌，再显式输入密码提交。网络失败不自动重发登录。窄屏可用Tab/Enter操作；角色业务授权尚未实现，三种用户仍都能访问已认证的`/hello`和`/me`。

### 小总结

刷新恢复依赖浏览器会话与后端查询，不依赖保存密码或前端身份对象。界面明确区分正在加载、已认证、未登录和未知，选择框、历史文字和本地存储都不能替代服务端判断。

## 六、验证真实身份、故障恢复与篡改边界

### 理论：恢复成功之外，还要证明错误时不会沿用旧结论

只测试登录后出现用户名不足以证明恢复逻辑。需要检查刷新没有重发密码、/me确实使用Cookie、响应字段没有泄露框架对象，以及清除或失效会话后不能继续展示旧身份。

加载、网络和格式异常也需要独立条件：延迟真实请求用于观察loading，离线验证unavailable，受控畸形响应验证字段检查。伪造页面和localStorage后向真实后端请求，才能证明前端文字不授予身份；这些场景不能都靠模拟401替代真实认证边界。

### 实操：加入完整测试并复现

在**仓库根目录**执行以下命令，为跟写工程加入完成版测试；固定密码只在测试上下文中使用。普通启动仍必须配置实验密码环境变量。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-008-current-user-restoration"
lesson_work="$PWD/work/security-followalong/21-04-008-current-user-restoration"
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

本课新增`CurrentUserHttpTests`的8项真实HTTP测试，与继承测试合计58项：三种身份恢复与字段白名单、匿名401、Basic请求对照、伪造会话、服务端失效会话、查询参数不能换身份、方法/路径边界以及失败重登保留原身份。业务字段与权限标识的断言按当前版本实际结果核对。

浏览器新增6项，共22项：真实刷新恢复、加载与清Cookie后的401、离线恢复、格式异常原位修复、伪造页面和存储仍不能认证、账号选择和失败重登对照及390px键盘操作。原有错误日志计数在启动/me结束后取基线，避免把自动身份查询产生的401误算成手动请求的执行证据。

`.e2e-work/before-login/`继续隔离第七课的默认表单响应回归，但保留本课/me；它不是未添加/me的教学起点。正文起点由独立跟写工程验证。浏览器trace关闭，截图只保留清空密码后的状态；具体结果与故障注入边界见[验证记录](01-验证记录.md)。

结束请求实验，在原请求bash清理自己的临时文件：

```bash
rm -f "$login_work/token.json" "$login_work/cookies.txt" "$login_work/form.txt" "$login_work/login-response.txt" "$login_work/me.json"
rmdir "$login_work"
unset login_work csrf_header csrf_value LESSON_MEMBER_PASSWORD
unset -f get_csrf post_login
exit
```

停止前后端，在后端bash中`unset LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD`再`exit`。不提交临时凭据文件。

### 小总结

真实请求证明认证与返回字段，浏览器证明刷新、状态更新和错误恢复。故障注入与真实后端证据需要分别记录，不能把页面显示正确当成服务器已放行。

## 本课总结

本课在第七课已保存的会话上建立GET/me：Cookie关联服务端Session，上下文过滤器恢复认证，MVC把当前主体交给Controller，再投影为用户名与权限标识。界面刷新只丢失页面内存，重新发同源GET就能确认会话身份，无需保存或自动重发密码。

响应契约只保留允许展示的字段，避免直接序列化Authentication或principal；当前版本的FACTOR_PASSWORD与业务角色共同出现在权限列表中，展示这些标识不等于已实施业务授权。登录选择框、查询参数与localStorage都不能改变服务端当前主体。

前端按loading、authenticated、anonymous和unavailable区分证据：查询开始撤下旧身份，200合法契约才展示，401清理为未登录，网络或格式错误保留无法确认并允许手动恢复。登录、令牌更新与身份查询共用串行流程，本次失败重登也再次询问/me，避免把失败尝试误作退出。实际排查时先确认该请求携带的会话和响应状态，再检查页面更新；当前不保证跨标签页实时同步，后续继续完善退出与授权。

## 理解检查与后续衔接

1. 刷新后页面变量重置，为什么/me仍能返回原身份？认证对象实际保存在哪里？
2. 为什么不直接返回Authentication或UserDetails？credentials被清理是否足以证明可以安全序列化？
3. FACTOR_PASSWORD与ROLE_MEMBER分别表示什么？显示ROLE_ADMIN是否等于前端可以决定接口许可？
4. 查询/me返回401与网络失败时，用户名应如何变化？两种提示为何不同？
5. member已登录，再用admin错误密码登录，选择框、本次结果和当前身份为什么可以不同？
6. 为什么内部登录调用loadIdentity而不在队列里等待refreshIdentity？

下一课是[第009课：安全退出与页面状态收尾](../00-模块学习大纲.md#lesson-009)。在本课身份恢复基础上，完成带CSRF的退出、清理展示与重新准备匿名令牌。
