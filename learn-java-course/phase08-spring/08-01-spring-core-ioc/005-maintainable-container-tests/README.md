# 08-01-005 把第一个应用变成可维护的测试

[返回模块目录](../README.md) · [本课在大纲中的位置](../00-模块学习大纲.md#lesson-005) · [上一课：使用 Java 配置显式组装对象](../004-java-config-object-assembly/README.md)

第004课已经能切换配置并观察对象关系。这一课继续使用订单通知业务，解决另一个问题：修改业务代码或配置后，如何快速知道哪里出了错，而不必每次手动启动、盯着控制台猜结果。

我们会把检查拆成业务行为、容器装配、资源生命周期和失败诊断。学完后，你应能决定一个测试是否需要 Spring，写出能发现错误的断言，替换外部依赖而保留真实业务配置，并从容器异常中找到需要修复的装配位置。

## 1. 先确定到底在验证什么

“程序能启动”不能证明消息内容正确；“消息内容正确”也不能证明应用配置注册了需要的 Bean。

本课把目标对应到四个测试类：

| 测试类 | 验证的问题 | 是否启动 Spring |
| --- | --- | --- |
| `OrderNotificationServiceTest` | 消息内容与次数、非法输入、通知失败的传播 | 否 |
| `ContainerAssemblyTest` | 真实业务配置能否接入指定渠道、实例是否复用、上下文是否隔离 | 是，只注册相关配置 |
| `ContainerLifecycleTest` | 初始化和关闭回调是否执行，异常退出是否释放本例资源 | 是 |
| `ContainerFailureTest` | 缺失依赖发生在哪个阶段，异常链指向哪个注入点 | 是，包含故意不完整的配置 |

这里的小容器测试是局部集成测试：它验证业务配置与 Spring 容器如何协作。没有数据库、HTTP 服务，也没有邮件服务器；它不代表完整系统的端到端验证。

业务测试直接 `new` 服务，容器测试才创建上下文。这种区分依据被测行为是否需要容器，而不是依据类名里有没有 `Service`。Spring 官方也说明，采用依赖注入的普通业务对象应能脱离容器进行单元测试，见 [Spring 单元测试说明](https://docs.spring.io/spring-framework/reference/testing/unit.html)。

## 2. 工程中哪些是应用代码，哪些只供测试使用

本课仍是 IoC 聚合工程的一个子模块，继承 JDK 21、Spring Framework 7.0.9、JUnit Jupiter 5.13.4 与构建插件版本。没有新增测试框架依赖。

| 位置 | 内容 |
| --- | --- |
| [src/main/java/.../lesson005](src/main/java/cn/ningbingjian/learnjava/ioc/lesson005) | `Notifier`、订单服务、业务配置、控制台渠道配置和运行入口 |
| [src/test/java/.../lesson005](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005) | 四个 JUnit 测试类 |
| [src/test/java/.../lesson005/support](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/support) | 记录型通知器、生命周期探针、测试渠道配置 |
| `target/surefire-reports` | Maven 运行产生的测试报告 |

Maven 编译测试时，测试代码可以使用应用类；正常应用编译不依赖 `src/test/java`。本工程的普通应用 JAR 不包含记录型通知器和测试配置。测试报告和编译产物由本地构建生成，不提交到仓库。

这一次的“替换依赖”有明确范围：保留 `BusinessConfig` 和 `OrderNotificationService`，只把控制台渠道配置换成测试渠道配置。不会把整个被测服务换成一个总是成功的替身。

## 3. 给业务定义可观察的约定

本课在自己的服务实现中加入空白订单编号校验。历史课程保持各自示例不变；这里的约定是：

1. 合法订单编号产生一条格式为 `order=编号 accepted` 的通知。
2. `null` 或空白编号在发送前被拒绝。
3. 构造服务时必须提供通知器。
4. 通知器抛出的异常直接向调用方传播，本课不实现重试、补偿或吞掉异常。

[OrderNotificationService.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/OrderNotificationService.java) 的业务方法是：

```java
public void notifyAccepted(String orderId) {
    Objects.requireNonNull(orderId, "orderId");
    if (orderId.isBlank()) {
        throw new IllegalArgumentException("orderId must not be blank");
    }
    notifier.send("order=" + orderId + " accepted");
}
```

`Objects.requireNonNull` 在参数为 `null` 时抛出 `NullPointerException`，`isBlank()` 判断字符串是否为空或只含空白字符。它们是普通 Java 行为，与 Spring 校验注解无关。

这些约定足够让测试有明确的预期。不要先把当前输出复制成断言，再反过来说输出就是需求；例如是否去除编号两端空格、是否支持重试，本例没有定义，测试也没有擅自添加这些规则。

## 4. 第一种测试：直接 new 服务，记录它发送了什么

[OrderNotificationServiceTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/OrderNotificationServiceTest.java) 中的第一个测试可以独立阅读：

```java
@Test
void acceptedOrderSendsExactlyOneExpectedMessage() {
    var messages = new ArrayList<String>();
    var service = new OrderNotificationService(messages::add);

    service.notifyAccepted("O-001");

    assertEquals(List.of("order=O-001 accepted"), messages);
}
```

`Notifier` 只有一个抽象方法 `send(String)`，可以用 lambda 或方法引用提供实现。`messages::add` 表示：当服务调用通知器时，把消息追加到本测试的列表里。`List.add` 的布尔返回值在这个 `void` 目标接口中被丢弃。

这里的服务是真实服务，通知器是一个手写的记录型替身。`assertEquals` 比较预期列表与实际列表，同时约束消息内容、数量和顺序。如果服务没有发送、发送两次，或者把订单编号写错，这个测试都会失败。

对照两个较弱的断言：仅验证服务不为 `null`，不能检查业务；仅验证列表非空，不能发现重复发送与错误内容。断言要约束需要保护的业务行为。

本课不需要为了一个方法就引入 Mockito。手写替身已经足够表达记录消息和主动抛错；复杂交互需要专用工具时，再学习对应框架。

## 5. 失败测试也要验证副作用

空白编号测试如下：

```java
@Test
void blankOrderIdIsRejectedBeforeAnyNotification() {
    var messages = new ArrayList<String>();
    var service = new OrderNotificationService(messages::add);

    assertThrows(IllegalArgumentException.class, () -> service.notifyAccepted(" "));

    assertTrue(messages.isEmpty());
}
```

`assertThrows` 执行传入的动作，检查它是否抛出期望类型或其子类型。没有抛异常、抛了不符合要求的异常，都会使测试失败。JUnit 的异常断言说明见 [JUnit 5.13.4 用户指南](https://docs.junit.org/5.13.4/user-guide/#writing-tests-assertions)。

异常断言只包住需要验证的业务调用。构造服务发生在前面；这样构造阶段的意外异常不会被当成“业务拒绝空白编号”的证据。

还必须检查 `messages.isEmpty()`。如果实现错误地先发送、再抛异常，单独的 `assertThrows` 仍然可以通过，但“非法输入不得发通知”的约定已经被破坏。

通知渠道故障采用另一个简单替身：

```java
var channelFailure = new IllegalStateException("simulated channel failure");
Notifier failingNotifier = message -> { throw channelFailure; };
var service = new OrderNotificationService(failingNotifier);

var actual = assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-001"));

assertSame(channelFailure, actual);
```

这里使用 `assertSame` 检查异常引用，是因为本课约定直接传播原异常。如果将来业务决定包装异常或重试，需要连同约定一起调整测试，而不是为了保留旧断言强迫实现不变。

五个纯业务测试覆盖合法消息、空白编号、`null` 编号、渠道异常与必需依赖为空。它们没有 Spring import，也不启动任何上下文。

## 6. 第二种测试：保留真实配置，选择一个可观察的渠道

应用配置 [BusinessConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/BusinessConfig.java) 仍通过参数注入依赖：

```java
@Configuration(proxyBeanMethods = false)
public class BusinessConfig {
    @Bean
    public OrderNotificationService orderNotificationService(Notifier notifier) {
        return new OrderNotificationService(notifier);
    }
}
```

正常运行注册 `BusinessConfig` 与 `ConsoleChannelConfig`。装配测试改为注册 `BusinessConfig` 与测试目录中的 [RecordingChannelConfig.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/support/RecordingChannelConfig.java)：

```java
@Configuration(proxyBeanMethods = false)
public class RecordingChannelConfig {
    @Bean
    public LifecycleProbe lifecycleProbe() {
        return new LifecycleProbe();
    }

    @Bean(initMethod = "open", destroyMethod = "close")
    public ManagedRecordingNotifier notifier(LifecycleProbe probe) {
        return new ManagedRecordingNotifier(probe);
    }
}
```

测试配置只提供通知器和观察探针，没有重新声明一个“测试版订单服务”。这样测试仍会经过应用实际的业务装配方法。

这里采用启动时选入不同配置，不是先把应用渠道注册进去再覆盖同名 Bean。两个渠道配置不会同时加入这次小容器，因此不依赖覆盖开关、优先级或扫描顺序。

[ContainerAssemblyTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/ContainerAssemblyTest.java) 验证真实服务能够使用这个替身：

```java
@Test
void realBusinessConfigurationUsesTheSelectedRecordingChannel() {
    try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
        var recorder = context.getBean(ManagedRecordingNotifier.class);

        context.getBean(OrderNotificationService.class).notifyAccepted("O-001");

        assertEquals(List.of("order=O-001 accepted"), recorder.messages());
        assertEquals(1, context.getBeansOfType(Notifier.class).size());
        assertTrue(context.getBeansOfType(ManagedConsoleNotifier.class).isEmpty());
    }
}
```

这里有一条与纯业务测试相同的消息断言，但目的不同：纯业务测试保护消息规则，这个测试用一次代表性调用证明“容器创建的服务确实接到了被选中的渠道”。没有必要把所有输入组合都在容器中再跑一遍。

`getBeansOfType(Notifier.class).size()` 约束通知器候选数量；它不等于容器中全部 Bean 的数量，配置类和基础设施也可能被容器管理。

## 7. 替身记录行为，探针记录生命周期

[ManagedRecordingNotifier.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/support/ManagedRecordingNotifier.java) 完成两件事：

- `send` 把消息放进当前实例的列表；如果尚未打开，则拒绝发送。
- 构造器、`open()`、`close()` 分别向探针写入 `created`、`opened`、`closed`。

[LifecycleProbe.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/support/LifecycleProbe.java) 保存本上下文的事件列表，通过 `count("created")` 统计构造次数，通过 `events()` 查看事件顺序。它没有静态可变字段。

重复获取测试同时检查两个维度：

```java
assertSame(first, second);
assertEquals(1, probe.count("created"));
assertEquals(1, probe.count("opened"));
assertEquals(0, probe.count("closed"));
```

引用相同表明两次查找返回同一个对象，构造计数为一表明本例记录型通知器只创建了一次。计数针对这个特定替身，不是在统计 JVM 内所有对象或所有 Spring Bean。

生命周期事件是便于断言的结构化记录。控制台日志适合人观察，但时间戳、线程名和框架日志格式可能变化；测试不应依赖整段输出文本来判断关闭是否发生。

替身的 `messages()` 与探针的 `events()` 都返回 `List.copyOf(...)`。测试能读取快照，不能通过返回列表清空内部状态来掩盖污染。

## 8. 验证隔离，不能靠“每次先清空”来代替

本课的纯业务测试把列表声明在测试方法内部；每个容器测试显式创建自己的上下文；每个上下文通过测试配置创建独立探针与通知器。

隔离测试在同一个测试方法中启动两个上下文，然后按下面的顺序验证：

| 操作 | 应观察到的结果 |
| --- | --- |
| 从两个上下文分别取通知器与探针 | 两组实例引用不同 |
| 只在第一个上下文通知 `O-001` | 第二个通知器消息列表仍为空 |
| 再在第二个上下文通知 `O-002` | 两个列表分别只含自己的订单消息 |
| 分别查看探针 | 各自记录一次通知器构造 |

这是直接验证状态隔离，不依赖 JUnit 恰好先执行某个测试。测试中没有依靠前一个测试完成初始化，也没有用静态列表存放跨测试消息。

JUnit 默认会为每个测试方法创建测试类实例，但这并不能清除静态字段、外部数据库、共享文件或缓存的应用上下文。测试类实例隔离与依赖对象隔离不是同一层面的保证。本例用方法局部状态与显式上下文边界把这些关系写清楚。

当前没有启用 Spring TestContext 上下文缓存或测试并行执行。后续学习测试框架与缓存时需要重新考虑共享状态，不能直接套用“每个测试都有新容器”的假设。

## 9. 在容器外检查关闭后的结果

[ContainerLifecycleTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/ContainerLifecycleTest.java) 保留探针与通知器引用，然后离开资源作用域：

```java
@Test
void leavingTheResourceScopeInvokesTheConfiguredCloseCallbackOnce() {
    LifecycleProbe probe;
    ManagedRecordingNotifier recorder;
    try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
        probe = context.getBean(LifecycleProbe.class);
        recorder = context.getBean(ManagedRecordingNotifier.class);
        assertTrue(recorder.isOpen());
        assertEquals(List.of("created", "opened"), probe.events());
    }

    assertFalse(recorder.isOpen());
    assertEquals(List.of("created", "opened", "closed"), probe.events());
    assertEquals(1, probe.count("closed"));
}
```

关闭后的断言位于 `try` 外。这样验证的是退出作用域触发上下文关闭，再由 Spring 调用已配置的销毁方法。测试没有手工调用 `recorder.close()` 来替框架完成需要验证的动作。

对象引用仍然存在，所以可以读取状态与事件。Spring 销毁回调与 Java 垃圾回收是两个机制；这里不通过 `System.gc()` 或等待一段时间来推测资源是否释放。

初始化和销毁方法来自 `@Bean(initMethod = "open", destroyMethod = "close")`。这类回调配置可参考 [Spring @Bean 生命周期说明](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)。本例使用默认单例，不能据此推断所有作用域都有相同销毁管理方式。

另一个测试让非法编号异常穿过内部 `try (context)`，再断言上下文已关闭、替身没有收到消息且记录了关闭事件。该测试外层还有一个资源作用域，为准备或断言意外失败时提供清理；同一上下文再次关闭不会让本例销毁回调重复执行。内层用于制造异常退出，外层负责测试本身的清理边界。

同时保留一个使用真实 `ConsoleChannelConfig` 的测试，检查应用适配器确实被初始化并关闭一次。测试配置的销毁方法写对了，不等于应用配置也写对了，这两条证据必须分开。

这里的适配器只有可观察的布尔状态和关闭计数，没有真实连接。测试通过证明本例回调接线正确；真实连接池还需要验证实际资源释放行为。

## 10. 运行正常与业务失败两个演示

以下命令都在 `08-01-spring-core-ioc` 目录执行。先构建全部课程：

```bash
mvn clean verify
```

运行正常控制台渠道：

```bash
mvn -q -pl 005-maintainable-container-tests exec:java -Dexec.args=console
```

```text
console.opened
EMAIL order=O-001 accepted
console.closed
after-close.open=false
after-close.count=1
```

再运行非法编号导致业务失败的场景：

```bash
mvn -q -pl 005-maintainable-container-tests exec:java -Dexec.args=business-failure
```

```text
console.opened
console.closed
caught=orderId must not be blank
```

通知器已经打开，业务校验在发送前抛出异常，所以没有 `EMAIL` 行。离开资源作用域时先关闭容器，再进入外层异常处理打印原因。

这两个演示用于快速观察。是否满足全部业务约定与关闭约定，以测试断言为依据；看到程序没有崩溃不能替代测试通过。

## 11. 故意少注册一份配置，读取异常链

第三个模式只注册 `BusinessConfig`，不注册任何通知渠道：

```bash
mvn -q -pl 005-maintainable-container-tests exec:java -Dexec.args=missing-bean
```

应用打印的诊断摘要为：

```text
failed-bean=orderNotificationService
cause=UnsatisfiedDependencyException
cause=NoSuchBeanDefinitionException
missing-type=cn.ningbingjian.learnjava.ioc.lesson005.Notifier
after-failure.active=false
```

Spring 还会输出取消刷新的一条警告日志；其中时间戳和日志前缀取决于运行环境。该模式捕获预期异常并打印摘要，所以命令正常结束。不要把它与一个无人处理、导致进程启动失败的生产异常混为一谈。

完整错误信息中的关键部分分别提供不同线索：

| 信息 | 本例中的含义 |
| --- | --- |
| `Error creating bean with name 'orderNotificationService'` | 正在创建订单服务时失败 |
| `defined in ...BusinessConfig` | 这个 Bean 的配置来源 |
| `method 'orderNotificationService' parameter 0` | 无法满足工厂方法的第一个参数；索引从零开始 |
| `No qualifying bean of type '...Notifier' available` | 所需通知器类型没有合适候选对象 |
| `UnsatisfiedDependencyException` | 外层补充“哪个 Bean 的哪个依赖没有满足” |
| `NoSuchBeanDefinitionException` | 内层描述无法找到符合要求的 Bean |

修复时先看最内层缺什么，再回到外层看谁需要它，最后检查这次上下文究竟注册了哪些配置。本例修复是选入渠道配置；盲目给业务类添加注解、把字段设为可空或吞掉异常，都没有补齐这个装配关系。

失败发生在 `refresh()` 内。服务需要 `Notifier` 才能创建，参数解析没有成功，工厂方法就不能正常完成业务对象创建；因此尚未进入 `notifyAccepted`。

本例失败后上下文 `isActive()` 为 `false`。测试为每个场景创建新上下文，不在这个失败实例上继续做业务验证。

## 12. 用异常对象的结构写断言

[ContainerFailureTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/ContainerFailureTest.java) 不断言完整日志文本，而是读取结构化信息：

```java
var failure = assertThrows(UnsatisfiedDependencyException.class, context::refresh);

assertEquals("orderNotificationService", failure.getBeanName());
assertNotNull(failure.getInjectionPoint());
assertEquals("orderNotificationService", failure.getInjectionPoint().getMember().getName());
assertNotNull(failure.getInjectionPoint().getMethodParameter());
assertEquals(0, failure.getInjectionPoint().getMethodParameter().getParameterIndex());
var missing = assertInstanceOf(NoSuchBeanDefinitionException.class, failure.getMostSpecificCause());
assertNotNull(missing.getResolvableType());
assertEquals(Notifier.class, missing.getResolvableType().resolve());
assertFalse(context.isActive());
```

`assertInstanceOf` 检查异常类型，并返回已转换为该类型的引用；`getMostSpecificCause()` 帮助定位最深层原因。这里仍保留外层 Bean 名和注入点断言，因为只看根因会丢失“谁缺这个依赖”的上下文。

不要写成 `assertThrows(Exception.class, ...)` 就结束。配置类拼写错误、反射调用错误或其他启动问题也可能满足这么宽泛的断言，但并没有验证期望的缺失依赖场景。

另一个测试先正确启动上下文，再执行 `getBean("unknownNotifier")`。这次直接抛出名称查找失败的 `NoSuchBeanDefinitionException`，上下文仍 active，原有业务服务仍可调用。

| 对照 | 缺失必需依赖 | 主动查找不存在的名称 |
| --- | --- | --- |
| 发生阶段 | 容器刷新、创建业务对象期间 | 容器已成功启动后 |
| 触发动作 | 解析工厂方法参数 | 显式 `getBean` |
| 本例异常 | 外层依赖异常包裹内部缺失 Bean 异常 | 直接的缺失 Bean 异常 |
| 本例上下文状态 | 刷新失败，不再 active | 保持 active |

异常类名必须与调用阶段一起解释。仅凭日志里出现 `NoSuchBeanDefinitionException`，不能立即断定整次启动失败。

## 13. 沿这个失败场景进入源码

固定使用 Spring Framework **v7.0.9** 源码，与运行依赖保持一致。当前只追“缺少工厂方法参数时，异常在哪生成、又在哪里被补充上下文”这条路径。

1. 在 `DemoApplication.missingBean()` 的 `context.refresh()` 行暂停，确认仅注册了 `BusinessConfig`。
2. 在 `DefaultListableBeanFactory.raiseNoMatchingBeanFound(...)` 设置断点，观察 `type` 与 `resolvableType` 指向本课 `Notifier`。方法前的候选查询没有得到可满足本次必需依赖的对象。
3. 回到 `ConstructorResolver.createArgumentArray(...)` 的异常处理分支，观察被捕获的 `BeansException`，以及当前 `beanName`、参数位置和工厂方法。
4. 继续执行到应用的异常处理或测试中的断言位置，核对外层依赖异常携带的 Bean 名、注入点与内部缺失类型。

源码入口：[DefaultListableBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultListableBeanFactory.java)、[ConstructorResolver](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ConstructorResolver.java)、[UnsatisfiedDependencyException](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/UnsatisfiedDependencyException.java)、[NoSuchBeanDefinitionException](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/NoSuchBeanDefinitionException.java)。

这里验证的是当前工厂方法注入场景。字段、构造器等其他注入入口可能经过不同处理路径，不能把本课看到的一段调用栈当成所有注入失败的固定模板。完整解析与候选选择会在后续源码系列展开。

## 14. 按问题选择要运行的测试

只运行本课全部测试：

```bash
mvn -pl 005-maintainable-container-tests -am test
```

只运行五个纯业务测试：

```bash
mvn -pl 005-maintainable-container-tests -Dtest=OrderNotificationServiceTest test
```

只运行容器相关测试：

```bash
mvn -pl 005-maintainable-container-tests -Dtest=ContainerAssemblyTest,ContainerLifecycleTest,ContainerFailureTest test
```

指定测试类时这里只选择本课，不加 `-am`，避免其他课程也尝试寻找这些测试类。本课通过父 POM 管理配置，不依赖其他课程的业务 JAR。

`mvn test` 会编译并执行测试，测试失败时 Maven 返回失败状态。`mvn clean verify` 还会清理历史产物并完成后续构建阶段。代码修改后不要只重新运行旧的 `target/classes`。

本课测试数量如下：

| 测试类 | 数量 |
| --- | --- |
| `OrderNotificationServiceTest` | 5 |
| `ContainerAssemblyTest` | 3 |
| `ContainerLifecycleTest` | 3 |
| `ContainerFailureTest` | 2 |
| 本课合计 | 13 |

第001—005课聚合构建共 **44 个测试**。故意触发缺失依赖的测试会产生 Spring 警告，但只要异常与断言一致，测试就是通过；最终看测试报告中的 failures、errors 与 skipped，不能只按控制台日志颜色判断。

本课没有使用 `@SpringBootTest`、Spring TestContext 或模拟服务器。后续引入这些能力时，仍应先回答测试要验证什么，再选择需要启动多少基础设施。

## 15. 检查测试是否真能发现错误

可以依次做下面的单项练习，每次只改一个地方，观察对应测试失败后立即恢复。这些是练习要求，仓库保留的是正常实现。

| 临时改动 | 预期暴露的问题 |
| --- | --- |
| 把业务 `send` 调用复制一遍 | 合法订单消息列表出现重复，业务测试失败 |
| 把空白校验移到发送之后 | 虽然仍抛异常，但出现不应有的通知，副作用断言失败 |
| 在测试渠道配置中设置 `destroyMethod = ""` | 本例显式禁用推断销毁，关闭事件缺失，生命周期测试失败 |
| 启动装配测试时漏掉 `RecordingChannelConfig` | 业务配置缺少通知器，在刷新时失败 |

第三项不能只删除 `destroyMethod` 属性：Spring 对具有合适公开关闭方法的 Bean 有默认推断行为，删除显式声明后仍可能自动关闭，实验就没有真正制造出预期缺陷。使用空字符串禁用是为了隔离这个变量。

手写替身通过，也不保证真实邮件服务一定可用；小容器装配通过，也不保证网络、认证和限流策略正确。后续增加真实外部适配器时，再针对那个边界补充集成验证。

完成本课后，先前依靠手动观察的业务、装配与生命周期已经有可执行断言。下一课进入第02篇：[08-01-006 BeanDefinition 的基本模型](../00-模块学习大纲.md#lesson-006)，开始把配置类、注册信息和业务实例对应到容器内部的定义模型。
