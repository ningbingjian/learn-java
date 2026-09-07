# 21-04-005 显式管理用户与密码编码

[模块入口](../README.md) · [逐课大纲](../00-模块学习大纲.md#lesson-005) · [上一课](../21-04-004-request-matching-boundaries/README.md) · [验证记录](01-验证记录.md) · [前端说明](frontend/README.md)

## 本课导入

第四课已经给请求建立明确边界：公开GET允许匿名，GET `/hello`要求认证，未列入的业务请求默认拒绝。但认证仍依赖Boot提供的默认`user`，密码每次启动随机变化，也没有普通用户、客服与管理员的实验身份。

本课进入第二篇。我们要自己提供用户记录与密码编码器，在不改变第四课请求边界的情况下，让三个账号分别通过真实认证，并解释“找到用户”为什么不等于“密码正确”。前端增加单次Basic身份验证，输入的密码不回显到观察区。它是已有Basic机制的对照工具，正式前端会话登录按大纲在第006课准备CSRF、第007课实现表单提交。

完成后你应能说明用户记录由谁加载、原始密码如何与存储值核对、`{bcrypt}`前缀有什么作用，重现丢失前缀的故障并修复，并用正确密码、错误密码、旧默认用户及管理员访问未知路径证明边界。

沿用[版本基线](../01-版本基线.md)：JDK21、Maven3.9.9、Boot4.0.8、Security7.0.7、Framework7.0.9，Node22.15.0、npm10.9.2、Vite8.2.2。无数据库、Redis或新依赖。

## 准备：从第四课建立跟写工程

完成版在本课目录。首次学习请从**仓库根目录**执行以下macOS/Linux shell命令。复制仓库第四课完成版，不依赖你自行保留的练习。目标存在时停止，避免覆盖已有工作。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
previous="$module_source/21-04-004-request-matching-boundaries"
lesson_work="$PWD/work/security-followalong/21-04-005-users-and-password-encoding"
test ! -e "$lesson_work" || exit 1
mkdir -p "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson005" "$lesson_work/src/main/resources"
cp "$module_source/pom.xml" "$lesson_work/../pom.xml"
sed -e 's/lesson004/lesson005/g' -e 's/21-04-004/21-04-005/g' -e 's/Request Matching Boundaries/Users and Password Encoding/g' "$previous/pom.xml" > "$lesson_work/pom.xml"
for file in "$previous"/src/main/java/cn/ningbingjian/learnjava/security/lesson004/*.java; do
  sed 's/lesson004/lesson005/g' "$file" > "$lesson_work/src/main/java/cn/ningbingjian/learnjava/security/lesson005/$(basename "$file")"
done
sed 's/lesson004/lesson005/g' "$previous/src/main/resources/application.properties" > "$lesson_work/src/main/resources/application.properties"
```

Java包和扫描入口一起变成`lesson005`；跟写父POM只用于继承版本，始终在单课目录构建，不在跟写父目录执行聚合构建。先不复制完成版测试，避免新用户断言干扰默认用户的对照。

## 一、确定用户记录与角色的职责

### 理论：把身份数据与请求规则分开

用户详情（UserDetails）保存用户名、存储的密码表示、权限信息和账号状态。用户加载服务（UserDetailsService）按用户名返回这份记录；它的核心方法`loadUserByUsername`没有接收用户输入密码的参数，因此不能承担“本次密码是否正确”的完整认证判断。

内存用户管理器（InMemoryUserDetailsManager）是这个服务的一种实现，用内存保存实验用户。重启后从配置重新建立，不适合拿它证明数据库持久化、注册或密码修改已经完成。本课只增加一个用户服务和一个密码编码器，不手工创建另一条认证链。

| 用户名 | 身份说明 | 写入记录的角色 | 本课GET /hello |
| --- | --- | --- | --- |
| member | 普通用户 | MEMBER | 正确密码200 |
| support | 客服 | SUPPORT | 正确密码200 |
| admin | 管理员 | ADMIN | 正确密码200 |

`roles("MEMBER")`会形成`ROLE_MEMBER`权限字符串；调用时不要再写`ROLE_`前缀。本课只是准备身份数据，第四课使用的`authenticated()`不区分这些角色。管理员没有绕过`denyAll()`的特殊能力，角色授权在第三篇逐步加入。

### 实操：先观察默认用户起点

在跟写单课目录执行：

```bash
mvn clean package
java -jar target/spring-security-lesson005-1.0-SNAPSHOT.jar
```

另开终端：

```bash
curl -i -u user -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u member -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

`curl -u 用户名`交互提示输入密码，不把密码写进命令历史。第一条输入这次启动生成的默认密码，结果200；第二条即使输入同一密码也为401，因为当前用户服务并没有`member`记录。不要把后面将创建的账号当成现在已经存在。

停止后端，后续会用显式用户服务替换默认来源。已有`SecurityConfig`、Controller和错误契约全部保留。

### 小总结

先确定用户名和角色数据，再讨论如何认证它们。用户服务负责加载记录，角色只是记录中的权限信息，只有请求授权规则使用它们时才产生访问差异。

## 二、一起提供用户服务与密码编码器

### 理论：存储编码值，认证时核对原始输入

密码编码器（PasswordEncoder）有两个关键方向：初始化用户时`encode(raw)`产生存储表示；认证时`matches(raw, stored)`判断原始输入与既有表示是否匹配。它不是把存储值解密回原密码，也不是每次登录重新编码后用字符串相等判断。

本课用`PasswordEncoderFactories.createDelegatingPasswordEncoder()`创建委派编码器（DelegatingPasswordEncoder）。在固定的Security7.0.7中，工厂把新编码交给bcrypt，结果形如`{bcrypt}$2a$...`。`{bcrypt}`是算法标识，后面才是包含盐值、成本信息和结果的bcrypt表示；验证时委派编码器读取标识选择对应实现。它支持多种已登记的格式，但不代表本课应该储存明文或采用快速普通散列。

bcrypt每次编码会使用随机盐，即使密码相同，两次编码通常不同。验证所需信息保存在存储表示中，`matches`按照既有表示核对。因此重启本课时散列可能变化，但只要环境变量中的原始密码没变，账号密码仍可重复使用。具体默认算法见[PasswordEncoderFactories 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/crypto/src/main/java/org/springframework/security/crypto/factory/PasswordEncoderFactories.java)。

### 实操：新增UserConfiguration，替换启动属性

在跟写包目录`src/main/java/cn/ningbingjian/learnjava/security/lesson005/`新增`UserConfiguration.java`：

```java
package cn.ningbingjian.learnjava.security.lesson005;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration(proxyBeanMethods = false)
public class UserConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
            @Value("${lesson.users.member-password}") String memberPassword,
            @Value("${lesson.users.support-password}") String supportPassword,
            @Value("${lesson.users.admin-password}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("member").password(encode(encoder, memberPassword)).roles("MEMBER").build(),
                User.withUsername("support").password(encode(encoder, supportPassword)).roles("SUPPORT").build(),
                User.withUsername("admin").password(encode(encoder, adminPassword)).roles("ADMIN").build());
    }

    private static String encode(PasswordEncoder encoder, String raw) {
        // 实验输入约束；bcrypt按UTF-8字节计限，不能只检查字符数。
        if (raw == null || raw.isBlank() || raw.length() < 8
                || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("实验密码须至少8个字符且UTF-8不超过72字节，请设置三个LESSON_*_PASSWORD环境变量。");
        }
        return encoder.encode(raw);
    }
}
```

两个`@Bean`方法由Spring调用。`passwordEncoder`登记编码器，`userDetailsService`的`encoder`参数注入同一个容器组件；三个`@Value`参数来自启动配置。每条记录在进入内存管理器前调用一次`encode`，`roles`填写角色元数据。之后认证组件加载的是编码后的记录，不需要Controller自行比较密码。

把跟写`src/main/resources/application.properties`替换为：

```properties
spring.application.name=spring-security-lesson005
server.address=127.0.0.1
# 本机实验启动时提供；没有可登录的内置密码，空值在初始化时拒绝。
lesson.users.member-password=${LESSON_MEMBER_PASSWORD:}
lesson.users.support-password=${LESSON_SUPPORT_PASSWORD:}
lesson.users.admin-password=${LESSON_ADMIN_PASSWORD:}
```

三个环境变量没有可登录的内置密码；缺失会变成空值，由初始化检查拒绝。实验要求至少8个字符且UTF-8不超过72字节，因为bcrypt有字节长度边界，汉字的字符数不能直接当作字节数。此处只是本课输入约束，不是一套完整生产密码策略。

用户记录里保存编码值，并不表示原始密码在进程中从未出现：初始化时配置环境与方法参数仍会接触原始值。本课用环境变量避免将实验密码写进仓库；生产秘密供应、持久化用户和密钥治理不在本课实现。

在跟写单课目录**开启一个交互式bash**，接下来三次提示均关闭终端回显。每个账号自己设定符合条件的本机实验密码，不要使用个人真实账户密码：

```bash
bash
```

在该bash中执行：

```bash
read -r -s -p 'member实验密码：' LESSON_MEMBER_PASSWORD
printf '\n'
read -r -s -p 'support实验密码：' LESSON_SUPPORT_PASSWORD
printf '\n'
read -r -s -p 'admin实验密码：' LESSON_ADMIN_PASSWORD
printf '\n'
export LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
mvn clean package
java -jar target/spring-security-lesson005-1.0-SNAPSHOT.jar
```

不需要再寻找Boot生成的密码；显式提供`UserDetailsService`后，本课条件下Boot默认用户自动配置退让。判断依据是[Boot4.0.8 UserDetailsServiceAutoConfiguration](https://github.com/spring-projects/spring-boot/blob/v4.0.8/module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.java)的缺失Bean条件，以及后面真实请求中旧`user`不可用、新账号可用的结果。

若漏设变量，应用会在建立用户服务时启动失败，提示实验密码条件；这是配置阶段失败，服务没有成功开始接受请求，不是一次HTTP401。补齐三个变量后重新启动即可。不要通过写默认明文密码来掩盖配置缺失。

### 小总结

用户服务和编码器一起构成可重复的实验身份来源。初始化对原始值编码，认证对照已保存的表示；原始配置与用户记录中的存储值属于不同阶段，不能混称为“所有地方都没有明文”。

## 三、验证盐值、格式标识与修复路径

### 理论：密码格式也是认证契约的一部分

为什么保存bcrypt主体还不够？委派编码器先根据`{id}`找到验证实现，再把主体交给它。丢掉标识后，当前配置没有可选择的算法；失败发生在密码格式分派阶段，不是业务Controller，也不能简单解释为用户输错密码。

[DelegatingPasswordEncoder 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/crypto/src/main/java/org/springframework/security/crypto/password/DelegatingPasswordEncoder.java)的`matches`提取标识、查找对应编码器；未找到时交给默认的不匹配格式处理。本课未注册未知格式回退，因此缺失标识会抛出`IllegalArgumentException`。不能通过配置“任意未知格式按明文处理”来修复，正确方法是保存完整、与实际算法一致的记录。

### 实操：运行聚焦的存储验证，错误在测试内闭合

在**仓库根目录**执行，仅复制这节需要的测试。它直接调用本课配置方法与真实编码器，不启动Web服务、不需要环境变量；测试用固定值只存在于测试上下文中。

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_work="$PWD/work/security-followalong/21-04-005-users-and-password-encoding"
mkdir -p "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson005"
cp "$module_source/21-04-005-users-and-password-encoding/src/test/java/cn/ningbingjian/learnjava/security/lesson005/PasswordStorageTests.java" "$lesson_work/src/test/java/cn/ningbingjian/learnjava/security/lesson005/"
cd "$lesson_work"
mvn -Dtest=PasswordStorageTests test
```

打开复制的`PasswordStorageTests.java`，先看`sameRawValueHasDifferentSaltedEncodingsButBothMatch`：对同一原始值编码两次，字符串不同，而两次`matches`都为true。它说明不能把`encoder.encode(输入).equals(存储值)`当作认证算法。

接着看`missingAlgorithmIdFailsAndRestoringTheFullRecordRepairsIt`的关键过程：

```java
var encoder = configuration.passwordEncoder();
String complete = encoder.encode(raw);
String broken = complete.substring("{bcrypt}".length());
assertThatThrownBy(() -> encoder.matches(raw, broken))
        .isInstanceOf(IllegalArgumentException.class);
assertThat(encoder.matches(raw, complete)).isTrue();
```

这里`raw`是测试值，`complete`是完整存储表示；故意生成的`broken`只存在于这个测试局部变量。第三行删掉标识，第四行确认预期异常；最后一行立即使用完整记录修复验证，不把坏记录写回用户服务。测试总结果应是通过，因为它明确断言了故障及修复。

其余测试检查三个用户记录的`{bcrypt}`前缀、真实匹配和角色元数据，拒绝空值及超长UTF-8输入，并演示“把旧存储表示再次当原始密码编码”不会完成密码迁移。后者的`{noop}`字符串只是隔离的错误输入样本，本课正常用户始终使用bcrypt，不启用明文存储。

### 小总结

存储值既包含算法标识也包含算法所需的数据。随机盐要求使用`matches`，丢失标识要求修复记录格式；不能用字符串相等、二次编码或明文回退替代正确核对。

## 四、把用户加载与密码校验连到真实请求

### 理论：UserDetailsService不是登录Controller

本课仍通过HTTP Basic读取请求中的用户名和密码。当前只有一个用户服务与一个编码器，且未自定义认证管理器或认证提供者；Security的初始化配置会建立使用这两个组件的`DaoAuthenticationProvider`。名字里的Dao不意味着本课已经接了数据库，它可以使用内存用户服务。

机制入口是[InitializeUserDetailsBeanManagerConfigurer 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/config/src/main/java/org/springframework/security/config/annotation/authentication/configuration/InitializeUserDetailsBeanManagerConfigurer.java)：在适用条件下取得用户服务与编码器，再配置认证提供者。若以后登记多个同类型服务或自己提供认证管理器，不能继续不加条件地套用这一装配结论。

运行时，[DaoAuthenticationProvider 7.0.7](https://github.com/spring-projects/spring-security/blob/7.0.7/core/src/main/java/org/springframework/security/authentication/dao/DaoAuthenticationProvider.java)的`retrieveUser`通过用户名加载记录，`additionalAuthenticationChecks`用`matches(提交密码, 存储密码)`核对。用户名存在但密码错误仍失败；用户名不存在也不能返回认证成功。认证通过后，才继续使用第四课的请求授权规则。

### 实操：用身份和路径两维检查

后端保持第二节设置好环境变量的完成版。另开终端执行，每次按提示输入相应账号的实验密码：

```bash
curl -i -u member -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u support -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u admin -H 'Accept: application/json' http://127.0.0.1:8080/hello
curl -i -u admin -H 'Accept: application/json' http://127.0.0.1:8080/missing
curl -i -H 'Accept: application/json' http://127.0.0.1:8080/hello
```

前三条均200；第四条即使管理员密码正确也是403，因为未知业务路径默认拒绝；第五条没有携带身份，仍401。再对前三条各输入一次错误密码，均应为401及第三课的`AUTHENTICATION_REQUIRED`，没有问候业务正文。用旧`user`和任意本课密码访问也应401。

不要从三种账号都200推出“角色授权已经完成”；这里证明的是三条用户记录都能认证，而`/hello`目前只要求认证。在IDE中需要进一步观察时，可在`retrieveUser`查看加载结果的用户名，在`additionalAuthenticationChecks`查看匹配结果；不要截图或记录密码、散列和完整认证头。当前验证依据是实际HTTP与编码器测试，不把断点建议写成已完成全链路源码调试。

### 小总结

用户服务给出记录，认证提供者核对凭据，授权规则再决定资源访问。错误用户名与错误密码都走认证失败契约，正确管理员也受资源边界约束。

## 五、在独立前端验证一次身份

### 理论：显式Authorization与Cookie是不同请求条件

前端保留第四课匿名观察按钮，新增账号选择和密码输入。输入控件使用`type="password"`遮住显示，构造Basic头之后立即清空；结果区只说明本次是否携带凭据，不显示密码或编码后的认证头，也不写入localStorage/sessionStorage。

HTTP Basic的Base64是可逆的传输格式，不是第二节的bcrypt存储编码。浏览器提交原始凭据，后端负责核对存储表示；把bcrypt值放进Basic头会变成错误的密码输入。这里只在本机回环地址实验，真实网络必须使用HTTPS；不要在抓包截图、追踪或日志里分享认证头。

`credentials: 'omit'`让浏览器不自动携带Cookie等凭据，但不会替你删除代码显式写入的`Authorization`。因此只有身份按钮构造Basic头，匿名按钮每次创建新的请求头对象。身份验证失败后隐藏“重试”，要求重新输入再提交，避免为了重试保存密码；普通匿名请求仍保留第四课的原方法重试。

### 实操：复制界面骨架，改造请求函数

在**仓库根目录**执行：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-005-users-and-password-encoding"
lesson_work="$PWD/work/security-followalong/21-04-005-users-and-password-encoding"
mkdir -p "$lesson_work/frontend/src"
cp "$lesson_source/frontend/index.html" "$lesson_work/frontend/index.html"
cp "$lesson_source/frontend/src/style.css" "$lesson_work/frontend/src/style.css"
cp "$lesson_source/frontend/"{package.json,package-lock.json,.nvmrc,.gitignore,vite.config.js} "$lesson_work/frontend/"
cp "$module_source/21-04-004-request-matching-boundaries/frontend/src/main.js" "$lesson_work/frontend/src/main.js"
```

页面新增`identity-form`、`identity-name`与`identity-password`；表单另声明`method="post"`，避免脚本未加载时退回默认GET把字段写进URL。正常运行由下面的submit处理器接管单次GET验证。身份按钮用`data-identity="true"`区分。先修改`sendRequest`入口：只对身份按钮读取输入，通过`TextEncoder`把用户名、冒号和密码转换为UTF-8字节，再转Base64构造头；其他按钮的头仍只有Accept。随后立即清空输入，并更新`request-conditions`的固定说明。

接着修改重试与收尾：身份请求不给重试按钮保存路径，无论HTTP失败还是网络失败都隐藏重试；普通按钮维持旧逻辑。请求结束时删除头对象上的Authorization引用，但这不等于保证JavaScript内存已经被安全擦除。最后监听表单`submit`并阻止浏览器默认提交，避免凭据出现在页面URL；身份按钮不再额外绑定普通click处理，防止一次操作发送两次请求。

把跟写`frontend/src/main.js`替换成完整版本，重点核对上述新增部分；400/401、代理502与格式降级沿用前课：

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
const identityForm = document.querySelector('#identity-form');
const identityName = document.querySelector('#identity-name');
const passwordInput = document.querySelector('#identity-password');
const conditions = document.querySelector('#request-conditions');

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
  const withIdentity = button.dataset.identity === 'true';
  const headers = { Accept: 'application/json' };
  if (withIdentity) {
    if (!identityForm.reportValidity()) return;
    // UTF-8编码后转Base64，仅为HTTP Basic格式，不是密码加密。
    const bytes = new TextEncoder().encode(`${identityName.value}:${passwordInput.value}`);
    headers.Authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`;
    passwordInput.value = '';
  }
  conditions.textContent = withIdentity
    ? 'Accept: application/json · 本次携带Basic凭据（不展示内容）'
    : 'Accept: application/json · 不携带身份凭据';
  retry.hidden = true;
  retry.dataset.path = withIdentity ? '' : path;
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
      headers,
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
    retry.hidden = withIdentity || response.ok;
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
      summary.textContent = withIdentity
        ? '本次身份验证通过，接口返回200。匿名按钮仍不携带凭据。'
        : '请求成功。请对照后端新增的业务日志。';
    } else {
      code.dataset.state = 'error';
      summary.textContent = '收到了其他 HTTP 响应。请根据真实状态和内容定位，不能一律判断为未登录。';
    }
  } catch (error) {
    retry.hidden = withIdentity;
    code.dataset.state = 'error';
    code.textContent = '未取得完整响应';
    source.textContent = '浏览器请求失败';
    summary.textContent = error.name === 'TimeoutError'
      ? '请求超过8秒。请检查服务与网络，然后重试。'
      : '请求未完成。请检查网络、前端服务或是否发生了被禁止的重定向。';
    body.textContent = '没有可展示的完整 HTTP 响应。此处不推断认证结果。';
  } finally {
    delete headers.Authorization;
    buttons.forEach((item) => { item.disabled = false; });
    button.textContent = label;
    panel.setAttribute('aria-busy', 'false');
  }
}

buttons.forEach((button) => {
  if (button.dataset.identity !== 'true') button.addEventListener('click', () => sendRequest(button));
});

identityForm.addEventListener('submit', (event) => {
  event.preventDefault();
  if (!document.querySelector('#verify-identity').disabled) sendRequest(document.querySelector('#verify-identity'));
});
```

密码输入的自动完成提示设为off，不等于能强制所有浏览器密码管理器停止保存。本课能验证的是应用代码不主动持久保存、不在观察区回显，提交后输入框为空。

后端已在8080启动，另开终端进入跟写`frontend/`：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`，选择账号，输入该账号本机密码后点击“验证本次身份”。正确密码显示200，错误密码显示401，两种情况输入框都变空；点击“访问受保护接口”又得到匿名401。换成客服、管理员重复验证，应得到同样认证结果。Network可核对GET `/api/hello`与状态，但不要复制或展示完整Authorization。

开发代理仍去掉`/api`前缀，沿用第四课的`server.cors: false`让同源OPTIONS进入后端。没有新增CORS配置、登录成功处理器或会话恢复逻辑。身份选择器只提供输入值，不能替后端伪造角色。

### 小总结

页面把“本次携带凭据”与“保持匿名”做成两个可对照操作。密码遮挡、提交后清空、错误后重新输入和不持久保存是不同责任；任何一个都不能替代HTTPS及后端密码核对。

## 六、完成验收并保留下一课的起点

### 理论：可重复不意味着散列固定

本课可重复的是三条明确的用户记录和自己提供的实验密码，不是每次相同的bcrypt字符串。重启会重新编码、重新建立内存记录；只要输入配置相同，认证行为应相同。没有外部配置时应该明确启动失败，而不是悄悄退回到另一个可登录默认账号。

验证应同时覆盖真实HTTP、存储机制与浏览器行为。只测`loadUserByUsername`不能证明密码校验通过；只看页面200不能证明没有把凭据留在输入框或存储；只测新用户也不能证明第四课默认拒绝仍保留。

### 实操：复制全部验收工具并运行

在**仓库根目录**执行：

```bash
module_source="$PWD/learn-java-course/phase21-security/21-04-spring-security"
lesson_source="$module_source/21-04-005-users-and-password-encoding"
lesson_work="$PWD/work/security-followalong/21-04-005-users-and-password-encoding"
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

测试自带只用于测试的属性，执行这些验证命令不要求你设置实验密码。准备脚本还在`.e2e-work/before-users/`移除显式用户配置，建立默认用户对照；正式工程不改动。浏览器验证新增member在默认用户版失败、在完成版成功，并逐一核对三个账号、错误密码、清空和匿名恢复。

本课关闭Playwright网络追踪留存，避免失败追踪收集Basic头与输入；截图仅在密码框已清空时生成。普通网络状态与计数足以支撑这里的断言，不需要发布凭据证据。实测范围与结果见[验证记录](01-验证记录.md)。

结束实验后停止后端与前端，在第二节启动用的bash清理本课变量并退出该子shell：

```bash
unset LESSON_MEMBER_PASSWORD LESSON_SUPPORT_PASSWORD LESSON_ADMIN_PASSWORD
exit
```

下次启动再次设置三个变量；不要以提交环境文件替代这个步骤。

### 小总结

用户数据、编码格式、真实认证和前端处理共同构成可用起点。运行时密码由本机提供，测试密码只属于验收工具，默认拒绝与CSRF继续保持。

## 本课总结

本课把第四课默认用户替换为显式的内存用户服务，并提供委派密码编码器。初始化时三个原始实验密码被编码后存入用户记录；请求到来后，认证提供者按用户名加载记录，再用`matches`核对提交密码与完整存储表示。认证成功后才继续检查资源规则，所以普通用户、客服、管理员都能通过`/hello`的认证要求，管理员仍无法越过未知路径的默认拒绝。

`{bcrypt}`负责标明验证算法，bcrypt主体携带核对所需的信息；随机盐使同一密码的多次编码不同，因此不能用重新编码后的字符串相等比较。丢失标识与错误密码是不同故障，应修复记录格式而非开启明文回退。内存用户重启后重建，环境中提供的原始值与用户记录里的编码值也不能混为一谈。

独立前端把单次Basic凭据与匿名请求并列，提交后清空密码，不回显认证头、不主动持久保存；它只证明本次请求认证成功，没有实现完整登录会话。实际开发遇到“账号存在但不能登录”时，应依次检查用户来源、存储格式、密码核对及请求授权，避免把所有失败都推给Controller或角色。

## 理解检查与后续衔接

1. `loadUserByUsername`能够找到member，为什么仍可能401？哪一个组件调用`matches`？
2. 同一个密码编码两次结果不同，为什么两次都可以验证？重启后散列变化是否必然改变登录密码？
3. 删除`{bcrypt}`后失败与密码输错有什么不同？如何在不允许明文回退的前提下修复？
4. admin正确密码访问`/missing`仍403，说明认证失败了吗？角色字段何时才参与请求权限判断？
5. `credentials: omit`为什么没有删除身份按钮显式设置的Basic头？为什么该按钮失败后要求重新输入？

下一课是[第006课：登录之前取得并携带CSRF令牌](../00-模块学习大纲.md#lesson-006)。以本课完整三用户工程为起点，先准备最小写请求防护交互，再进入第007课的独立前端登录。
