# 08-01-005 把第一个应用变成可维护的测试：从一个断言逐步拆分

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-005) · [上一课](../004-java-config-object-assembly/README.md)

上一课已经能选择配置并调用两个业务服务。这一课接着解决“每次修改后都靠盯控制台判断是否正确”的问题。先写一个能检查结果的测试，等它暴露出职责混合，再拆分测试层次。

JUnit 已由父 POM 管理，本课不需要引入 Mockito、数据库或网络服务。记录型替身只记录被调用的行为，真实业务服务和业务配置仍由你上一课的代码提供。

本页按实际修改顺序跟写。文件路径相对于 `005-maintainable-container-tests`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson005.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 先保留真实业务，再增加可观察的通知替身

### 1.1 理论：记录型替身让业务结果可断言

前面用控制台观察通知，容易漏掉重复发送、订单号错误等问题。测试需要把业务结果变成可以稳定检查的数据，记录型替身就是本节的观察手段。

RecordingNotifier 实现真实业务使用的接口，但只保存传入消息，不自己组织订单文案。真实 OrderService 仍执行被测业务，测试通过消息列表判断发生了什么。这样既不依赖真实邮件服务，也没有把待验证的逻辑放进替身里。

断言应来自业务要求。完整比较预期消息列表能同时检查内容、数量和顺序；仅判断非空只能证明至少发生过一次记录。

### 1.2 实操：逐步实现并解释原理

先写记录器，再用测试配置提供它，并沿用真实 BusinessConfig。阅读断言时确认预期文案是独立写出的要求，而不是再次调用业务生成逻辑算出预期。

复用上一课正常的业务关系。这里不复制邮件、短信和实验配置，测试中先选择一个可读取消息的通知器。

沿用第004课已经跟写完成的文件：`Notifier.java`、`OrderService.java`、`ReceiptService.java`、`BusinessConfig.java`。从 `../004-java-config-object-assembly/src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/` 复制到本课 `src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/`，将这些文件的包声明及内部包引用中的 `lesson004` 改为 `lesson005`，其余内容先不改。这里复制的是你在上一课创建的 practice 文件；若尚未跟写，请先完成上一课对应步骤。

在 src/test/java 中创建 RecordingNotifier。它实现业务契约，保存实际收到的消息；没有自己组织订单消息，因此不会替业务代码完成被测试的工作。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/RecordingNotifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.ArrayList;
import java.util.List;

public class RecordingNotifier implements Notifier {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void send(String message) { messages.add(message); }
    public List<String> messages() { return List.copyOf(messages); }
}
```

新增一份只选择测试渠道的配置，随后用真实 BusinessConfig 创建服务。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/TestChannelConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TestChannelConfig {
    @Bean
    public RecordingNotifier recordingNotifier() { return new RecordingNotifier(); }
}
```

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/ContainerAssemblyTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAssemblyTest {
    @Test
    void sendsAcceptedMessage() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, TestChannelConfig.class)) {
            context.getBean(OrderService.class).accept("O-005");
            assertEquals(List.of("order=O-005 accepted"), context.getBean(RecordingNotifier.class).messages());
        }
    }
}
```

这一个断言同时检查消息内容与数量；仅判断列表非空，发现不了重复发送或订单号错误。下面的输出按 Surefire 报告列出结果摘要，省略执行时间与插件日志。

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.ContainerAssemblyTest test
```

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

现在已经有一个能验证业务结果的测试，但为了检查一段字符串，每次都启动了容器。业务方法本身只依赖 Notifier，能否直接构造它完成这部分验证？

### 1.3 小总结

替身提供可控制、可观察的协作者，真实业务负责生成行为。断言应直接表达要保障的业务约定。

<a id="topic-2"></a>

## 2. 把消息断言移到不启动 Spring 的测试

### 2.1 理论：按验证问题拆分业务与装配测试

消息生成只依赖 Notifier 契约，可以直接 new 服务测试。是否选对配置、服务是否得到容器中的通知器，则需要启动容器才能验证。

拆分的目的是让失败更容易定位。纯业务测试失败时，优先检查消息逻辑；容器装配测试失败时，优先检查定义、配置选择和依赖关系。测试环境应包含回答当前问题所必需的部分，增加框架不等于增加有效证据。

比较消息内容用值相等断言，检查是否为同一通知器用 assertSame。即使两个记录器保存了相同消息，也不能用内容相等代替引用相同。

### 2.2 实操：逐步实现并解释原理

把消息断言移到直接构造的测试，并同步修改容器测试的职责。不要只是复制一份断言，让两处仍然混合验证所有问题。

新增 OrderServiceTest，直接 new 服务并提供记录型替身，复用同样的业务断言。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderServiceTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    @Test
    void sendsAcceptedMessageOnce() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        service.accept("O-005");
        assertEquals(List.of("order=O-005 accepted"), notifier.messages());
    }
}
```

原容器测试也同步改职责：检查真实业务配置创建的两个服务是否使用容器中的同一个通知器，而不重复断言订单消息文案。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/ContainerAssemblyTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAssemblyTest {
    @Test
    void realBusinessConfigurationSharesTheSelectedNotifier() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, TestChannelConfig.class)) {
            var notifier = context.getBean(RecordingNotifier.class);
            assertSame(notifier, context.getBean(OrderService.class).notifier());
            assertSame(notifier, context.getBean(ReceiptService.class).notifier());
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

现在消息错误由纯业务测试发现，配置遗漏或错误对象关系由容器测试发现。拆分依据是验证的问题，不是为了机械追求某个测试分类数量。

### 2.3 小总结

测试层次按要发现的错误划分。行为正确与对象装配正确各自需要合适的证据。

<a id="topic-3"></a>

## 3. 新增输入约束，同时验证没有副作用

### 3.1 理论：拒绝输入也要保证未发生副作用

无效订单号应当被拒绝，还应当没有发送通知。这是同一次业务调用的两个约束：返回或异常结果，以及对外发生了什么。

如果程序先发送再抛错，只断言异常仍可能通过。所以输入校验必须放在通知动作之前，测试也要同时检查异常和记录列表为空。这里的先后关系直接影响业务正确性，并非只是代码风格。

### 3.2 实操：逐步实现并解释原理

先在 OrderService 的业务入口增加校验，再分别测试 null 与空白。每次使用新的记录器，抛错后读取它，确认这次调用没有留下通知。

业务补充要求：订单号不能为 null，也不能是空白。先修改 OrderService，在发送前检查输入；保留原先的构造器依赖约束。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.Objects;

public class OrderService {
    private final Notifier notifier;

    public OrderService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank()) { throw new IllegalArgumentException("orderId must not be blank"); }
        notifier.send("order=" + orderId + " accepted");
    }

    public Notifier notifier() { return notifier; }
}
```

接着增加两个纯业务测试。除了断言异常，还检查消息列表为空，防止程序先发送消息再报错。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderServiceTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    @Test
    void sendsAcceptedMessageOnce() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        service.accept("O-005");
        assertEquals(List.of("order=O-005 accepted"), notifier.messages());
    }

    @Test
    void rejectsNullBeforeSending() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        assertThrows(NullPointerException.class, () -> service.accept(null));
        assertTrue(notifier.messages().isEmpty());
    }

    @Test
    void rejectsBlankBeforeSending() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        assertThrows(IllegalArgumentException.class, () -> service.accept("  "));
        assertTrue(notifier.messages().isEmpty());
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

无效输入的“无通知副作用”也属于业务约定。只断言抛了异常，可能遗漏错误操作已经发生的问题。

### 3.3 小总结

失败路径的契约包含错误结果与副作用边界。异常断言和“没有发送”的断言共同保障校验顺序。

<a id="topic-4"></a>

## 4. 通知器自己失败时，检查异常如何传播

### 4.1 理论：异常传播策略要成为可验证的约定

业务参数合法，不代表协作者一定成功。通知器可能在 send 时抛错，服务必须有明确策略：原样传播、转换、重试或其他处理，需要由需求决定。

当前代码没有重试或转换，本节验证原异常对象传播到调用方。用会抛出指定异常的实现，就能稳定制造这个边界条件；assertSame 检查的是那一个异常实例，强于只检查异常类型相同。

### 4.2 实操：逐步实现并解释原理

先创建代表本次失败的异常，再让通知实现抛出它，调用真实服务后取得实际异常。对照服务代码没有 catch 或再次调用 send，理解当前传播路径。

当前服务没有重试或吞异常的逻辑。用一个会抛出指定异常的通知实现，验证调用方得到的就是那次失败。替身只制造边界条件，不替服务决定异常策略。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderServiceTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    @Test
    void sendsAcceptedMessageOnce() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        service.accept("O-005");
        assertEquals(List.of("order=O-005 accepted"), notifier.messages());
    }

    @Test
    void rejectsNullBeforeSending() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        assertThrows(NullPointerException.class, () -> service.accept(null));
        assertTrue(notifier.messages().isEmpty());
    }

    @Test
    void rejectsBlankBeforeSending() {
        var notifier = new RecordingNotifier();
        var service = new OrderService(notifier);
        assertThrows(IllegalArgumentException.class, () -> service.accept("  "));
        assertTrue(notifier.messages().isEmpty());
    }

    @Test
    void propagatesDeliveryFailure() {
        var failure = new IllegalStateException("delivery unavailable");
        Notifier notifier = message -> { throw failure; };
        var service = new OrderService(notifier);
        var actual = assertThrows(IllegalStateException.class, () -> service.accept("O-005"));
        assertSame(failure, actual);
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

这里测试当前已约定的传播行为。将来增加重试、降级或错误转换，应先明确业务要求，再调整断言，不能让测试替你隐含决定策略。

### 4.3 小总结

替身可以稳定制造协作者失败，断言则固定已明确的异常契约。未来策略改变时，应先修改业务约定再调整测试。

<a id="topic-5"></a>

## 5. 不靠手动 clear，验证上下文本来就隔离

### 5.1 理论：测试隔离依靠实例归属与状态位置

记录器有可变列表，若多次测试意外共享它，前一次消息会影响后一次结果。隔离首先要知道列表存在哪里、记录器由谁创建。

本例每个独立上下文创建自己的记录器，列表是实例字段，因此第一份上下文的消息不应进入第二份。若列表改成静态字段，即使记录器实例不同，状态仍可能共享。

在每次断言前调用 clear，可能掩盖本该发现的共享错误。本节直接验证实例不同及另一份记录为空，观察真实隔离关系。

### 5.2 实操：逐步实现并解释原理

同时创建两个上下文，只在第一份调用业务。分别检查记录器引用和第二份消息列表；这两个检查共同说明对象与状态都没有意外共享。

接下来担心两次测试共用了可变记录。给容器测试增加两个独立上下文：只在第一份执行一次订单，确认第二份仍为空，而且记录器不是同一个对象。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/ContainerAssemblyTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAssemblyTest {
    @Test
    void realBusinessConfigurationSharesTheSelectedNotifier() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, TestChannelConfig.class)) {
            var notifier = context.getBean(RecordingNotifier.class);
            assertSame(notifier, context.getBean(OrderService.class).notifier());
            assertSame(notifier, context.getBean(ReceiptService.class).notifier());
        }
    }

    @Test
    void separateContextsDoNotShareRecordedMessages() {
        try (var first = new AnnotationConfigApplicationContext(BusinessConfig.class, TestChannelConfig.class);
             var second = new AnnotationConfigApplicationContext(BusinessConfig.class, TestChannelConfig.class)) {
            first.getBean(OrderService.class).accept("O-005");
            var firstNotifier = first.getBean(RecordingNotifier.class);
            var secondNotifier = second.getBean(RecordingNotifier.class);
            assertNotSame(firstNotifier, secondNotifier);
            assertEquals(List.of("order=O-005 accepted"), firstNotifier.messages());
            assertTrue(secondNotifier.messages().isEmpty());
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

隔离来自不同上下文和实例字段，没有使用全局静态列表，也没有靠每次清空掩盖共享。容器单例不表示整个 JVM 只能有一份。

### 5.3 小总结

容器单例不等于 JVM 全局单例。测试隔离既要检查实例归属，也要检查可变状态是否逃到静态或外部共享位置。

<a id="topic-6"></a>

## 6. 资源是否关闭，要在上下文外检查

### 6.1 理论：生命周期断言必须跨过关闭边界

资源是否初始化应在业务使用前检查，是否清理应在上下文关闭后检查。若一直在 try 内看 closed，只能知道资源尚未关闭，不能证明退出后真的执行了清理。

TrackingNotifier 是生命周期探针，用布尔状态记录回调是否执行。测试在上下文内取得引用，退出后继续读取这个普通 Java 对象的状态；不需要也不应再向已关闭上下文请求 Bean。

正常退出与业务异常退出是两条不同路径。两者都验证实际 closed 状态，才能把“代码用了 try-with-resources”转成关闭确实发生的证据。

### 6.2 实操：逐步实现并解释原理

先在 try 内断言 opened 和未 closed，再到块外检查 closed。异常测试同时比较传播出来的异常对象，避免关闭检查掩盖业务异常行为。

增加一个生命周期探针，明确记录 open、close 状态。它仍然是测试代码，不新增真实资源。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/TrackingNotifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

public class TrackingNotifier implements Notifier {
    private boolean opened;
    private boolean closed;

    public void open() { opened = true; }
    public void close() { closed = true; }
    public boolean opened() { return opened; }
    public boolean closed() { return closed; }

    @Override
    public void send(String message) {
        if (!opened || closed) { throw new IllegalStateException("notifier is not available"); }
    }
}
```

再用两个测试分别检查正常关闭和业务区域抛异常后的关闭。注意保留探针引用，在上下文关闭后读取结果；不要再次向已关闭上下文 getBean。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/ContainerLifecycleTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerLifecycleTest {
    @Configuration(proxyBeanMethods = false)
    static class LifecycleConfig {
        @Bean(initMethod = "open", destroyMethod = "close")
        public TrackingNotifier notifier() { return new TrackingNotifier(); }
    }

    @Test
    void closesTheManagedNotifier() {
        TrackingNotifier notifier;
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, LifecycleConfig.class)) {
            notifier = context.getBean(TrackingNotifier.class);
            assertTrue(notifier.opened());
            assertFalse(notifier.closed());
        }
        assertTrue(notifier.closed());
    }

    @Test
    void closesEvenWhenTheBusinessBlockThrows() {
        var context = new AnnotationConfigApplicationContext(BusinessConfig.class, LifecycleConfig.class);
        var notifier = context.getBean(TrackingNotifier.class);
        var failure = new IllegalStateException("business failed");
        var actual = assertThrows(IllegalStateException.class, () -> {
            try (context) {
                context.getBean(OrderService.class).accept("O-005");
                throw failure;
            }
        });
        assertSame(failure, actual);
        assertTrue(notifier.closed());
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

生命周期测试验证资源归属和退出路径，不重复验证消息内容。异常退出用 try-with-resources 保证关闭，断言则确认实际效果。

### 6.3 小总结

生命周期测试要跨越对应时点，并明确资源管理者。保留探针引用可以观察关闭结果，不需要重新访问关闭后的容器。

<a id="topic-7"></a>

## 7. 故意少注册渠道，读取异常结构

### 7.1 理论：预期启动失败也可以是通过的测试

缺少 Notifier 时，业务工厂参数无法满足，容器应在刷新期间失败。测试的目标就是验证这条失败约束，因此日志出现异常与测试是否通过不是一回事。

异常对象通常保留多层信息：外层说明哪个 Bean、哪个依赖点创建失败，内层说明缺少什么类型。assertThrows 返回实际异常，后续断言再核对根因和上下文状态，比匹配整段带时间戳的日志更稳定。

本节只缺少同一种依赖，不需要假定两个独立业务定义谁先被处理。让断言围绕真实约束，能减少无关执行顺序造成的脆弱性。

### 7.2 实操：逐步实现并解释原理

只登记 BusinessConfig 后，在 refresh 这一动作上断言失败。再读取最具体原因中的 Notifier 类型，最后检查上下文没有保持为正常活动状态。

现在只登记 BusinessConfig，不提供任何 Notifier。把缺失依赖写成一个预期失败测试：不是看构建日志里是否出现红字，而是检查异常对象中的类型信息和上下文状态。

文件：`src/test/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/ContainerFailureTest.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerFailureTest {
    @Test
    void identifiesTheMissingNotifierDuringRefresh() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BusinessConfig.class);
            var error = assertThrows(UnsatisfiedDependencyException.class, context::refresh);
            var missing = assertInstanceOf(NoSuchBeanDefinitionException.class, error.getMostSpecificCause());
            assertEquals(Notifier.class, missing.getResolvableType().resolve());
            assertFalse(context.isActive());
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

Spring 会输出取消刷新的警告，但测试通过说明这个错误被准确断言。当前两个业务工厂方法都需要同一类型，我们不依赖哪一个独立定义先被处理来判断缺失原因。

排查真实问题时，沿外层 Bean 创建异常定位配置和注入点，再看最深层原因；不要把整段带时间戳的日志作为测试预期。可沿 DefaultListableBeanFactory 的依赖解析与候选搜索入口跟踪本例。

### 7.3 小总结

预期失败测试验证系统能否正确拒绝错误配置。沿异常结构定位原因，比把控制台红字当成判定依据更可靠。

<a id="topic-8"></a>

## 8. 改错一次消息，确认断言真的会失败

### 8.1 理论：故意改错能检验断言是否有效

测试通过并不能单独证明它有保护作用：断言可能没有覆盖关键结果，也可能预期值错误地随实现一起变化。一次受控的错误修改，可以检查测试是否真的感知了要保护的行为。

本节把 accepted 改成 rejected，保持测试中的业务期望不变。若消息断言失败，说明它至少能发现这一类文案回归；若仍通过，就需要检查测试选取、调用路径和断言内容。

这只验证一个具体错误，不代表测试已经覆盖全部缺陷。实验完成后恢复代码，并运行本课跟写测试，保证故意错误没有成为最终版本。

### 8.2 实操：逐步实现并解释原理

只改业务消息，不同步修改预期字符串。先确认目标测试因内容不符而失败，再恢复 accepted 并运行全部跟写测试，观察保护行为与恢复结果。

现在只把业务消息的 accepted 改成错误的 rejected，其余代码保留。下面是故意错误的 OrderService，运行针对业务的测试应失败。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.Objects;

public class OrderService {
    private final Notifier notifier;

    public OrderService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank()) { throw new IllegalArgumentException("orderId must not be blank"); }
        notifier.send("order=" + orderId + " rejected");
    }

    public Notifier notifier() { return notifier; }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.OrderServiceTest test
```

```text
Failures: 1
```

这是预期的测试失败，不是待保留的代码。恢复 accepted，运行全部跟写测试，确认修复后回到通过状态。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson005/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson005.practice;

import java.util.Objects;

public class OrderService {
    private final Notifier notifier;

    public OrderService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank()) { throw new IllegalArgumentException("orderId must not be blank"); }
        notifier.send("order=" + orderId + " accepted");
    }

    public Notifier notifier() { return notifier; }
}
```

运行当前这一步：

```bash
mvn -q -pl 005-maintainable-container-tests -Dtest=cn.ningbingjian.learnjava.ioc.lesson005.practice.*Test test
```

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

### 8.3 小总结

有效测试应能被针对业务约定的错误修改触发。故意改错是检验当前断言的一种手段，完成后必须恢复正常实现。

<a id="course-summary"></a>

## 9. 本课总结

### 9.1 本课解决的问题

本课把人工查看控制台变成可重复验证的测试，并按问题拆分业务行为、容器装配、状态隔离、生命周期和启动失败的验证职责。

### 9.2 把原理串起来

记录型替身只记录真实业务传入的消息，让断言能够检查内容、数量及无副作用。消息规则可直接构造服务验证，配置与引用关系则通过真实业务配置和小范围上下文验证。

输入失败既要看异常，也要看通知是否发生；协作者失败要按约定检查传播；上下文隔离要检查对象和状态；关闭要跨过资源作用域观察。最后故意改错一条消息，确认断言确实能够发现这类回归，再恢复正常实现。

### 9.3 核心结论与边界

| 要保障的行为 | 使用的证据 |
| --- | --- |
| 消息正确且不重复 | 完整消息列表的值比较 |
| 服务使用同一协作者 | assertSame 检查引用 |
| 无效输入未发送 | 异常与空记录一起断言 |
| 异常原样传播 | 比较实际异常与指定异常实例 |
| 上下文正确隔离 | 不同记录器、互不污染的状态 |
| 正常或异常退出后清理 | 上下文关闭后的探针状态 |
| 错误配置被拒绝 | 异常结构、缺失类型与上下文状态 |

测试通过只说明所写断言成立，不等于覆盖全部风险。预期失败被准确断言时，控制台可以有警告而测试仍正确通过。

### 9.4 如何应用与检查理解

新增测试先问它要发现哪一种错误，再选择最小必要环境。业务约定改变时修改相应断言，避免无关日志、处理顺序或共享状态让测试脆弱。

能说明为什么“只断言异常”会漏掉先通知再报错，以及为什么装配测试用引用比较，就理解了本课测试设计的主要依据。

### 9.5 复习与后续学习

你最初只有一个容器测试；随后把业务验证移出 Spring，把装配、隔离、关闭和错误定位分别补齐。划分依据始终是“这个测试要发现哪种错误”。

本页的九个跟写测试位于你创建的 practice 测试包，与仓库原有十三个测试分开。命令使用完整包名选择本页测试，不会把参考样例的数量混进当前步骤。完整异常与副作用边界可在机制参考中继续查阅。

下一课暂时放下测试组织，从业务对象创建前的配置元数据入手，解释容器凭什么知道怎样创建它们。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-006](../006-bean-definition-model/README.md)。
