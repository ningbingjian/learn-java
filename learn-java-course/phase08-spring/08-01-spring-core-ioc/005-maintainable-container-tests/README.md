# 08-01-005 把第一个应用变成可维护的测试：从一个断言逐步拆分

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-005) · [上一课](../004-java-config-object-assembly/README.md)

上一课已经能选择配置并调用两个业务服务。这一课接着解决“每次修改后都靠盯控制台判断是否正确”的问题。先写一个能检查结果的测试，等它暴露出职责混合，再拆分测试层次。

JUnit 已由父 POM 管理，本课不需要引入 Mockito、数据库或网络服务。记录型替身只记录被调用的行为，真实业务服务和业务配置仍由你上一课的代码提供。

本页按实际修改顺序跟写。文件路径相对于 `005-maintainable-container-tests`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson005.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

## 1. 先保留真实业务，再增加可观察的通知替身

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

## 2. 把消息断言移到不启动 Spring 的测试

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

## 3. 新增输入约束，同时验证没有副作用

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

## 4. 通知器自己失败时，检查异常如何传播

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

## 5. 不靠手动 clear，验证上下文本来就隔离

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

## 6. 资源是否关闭，要在上下文外检查

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

## 7. 故意少注册渠道，读取异常结构

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

## 8. 改错一次消息，确认断言真的会失败

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

## 9. 测试结构是在解决问题中形成的

你最初只有一个容器测试；随后把业务验证移出 Spring，把装配、隔离、关闭和错误定位分别补齐。划分依据始终是“这个测试要发现哪种错误”。

本页的九个跟写测试位于你创建的 practice 测试包，与仓库原有十三个测试分开。命令使用完整包名选择本页测试，不会把参考样例的数量混进当前步骤。完整异常与副作用边界可在机制参考中继续查阅。

下一课暂时放下测试组织，从业务对象创建前的配置元数据入手，解释容器凭什么知道怎样创建它们。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-006](../006-bean-definition-model/README.md)。

