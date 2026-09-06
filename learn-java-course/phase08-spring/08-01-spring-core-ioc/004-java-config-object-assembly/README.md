# 08-01-004 使用 Java 配置显式组装对象：从一个 Bean 到一组协作对象

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-004) · [上一课](../003-runnable-debuggable-spring/README.md)

上一课已经能启动并调试容器。这一课沿用订单通知，先只登记一个通知器，再增加服务和回执入口；等配置职责出现差异后才拆分配置、切换实现，并验证名称与类型的边界。

本页按实际修改顺序跟写。文件路径相对于 `004-java-config-object-assembly`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson004.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

## 1. 沿用业务类型，先只配置通知器

把上一课正常业务所需类型带过来；EmailNotifier 保留已有 open、close 回调。不要复制上一课 AppConfig，我们要逐步写出当前配置。

沿用第003课已经跟写完成的文件：`Notifier.java`、`EmailNotifier.java`、`OrderService.java`。从 `../003-runnable-debuggable-spring/src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/` 复制到本课 `src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/`，将这些文件的包声明及内部包引用中的 `lesson003` 改为 `lesson004`，其余内容先不改。这里复制的是你在上一课创建的 practice 文件；若尚未跟写，请先完成上一课对应步骤。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/AppConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            context.getBean(Notifier.class).send("configuration-ready");
            System.out.println("has-order-service=" + context.containsBean("orderService"));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
EMAIL configuration-ready
has-order-service=false
notifier.close
```

OrderService 的源码已经在类路径中，却没有自动成为 Bean。这里没有执行扫描，也没有为它登记定义。@Bean 只声明对应方法的产品，不能理解为“项目里所有类都会被管理”。

## 2. 服务需要通知器，于是增加一个工厂方法参数

在同一配置类中增加 orderService 方法。参数表达容器必须提供的依赖，方法体再把这个参数交给普通 Java 构造器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/AppConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }

    @Bean
    public OrderService orderService(Notifier notifier) { return new OrderService(notifier); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            var service = context.getBean(OrderService.class);
            service.accept("O-004");
            System.out.println("same-notifier=" + (service.notifier() == context.getBean(Notifier.class)));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
EMAIL order=O-004 accepted
same-notifier=true
notifier.close
```

这里有两次传递：容器为 @Bean 方法解析参数，Java 方法再调用构造器。当前 proxyBeanMethods=false，所以我们不通过 notifier() 方法互调来依赖配置增强；用参数表达依赖，装配关系更直接。

## 3. 第二个业务入口也要用通知能力

增加回执服务，它组织另一种消息，仍依赖相同 Notifier 契约。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/ReceiptService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

public class ReceiptService {
    private final Notifier notifier;

    public ReceiptService(Notifier notifier) { this.notifier = notifier; }
    public void ready(String orderId) { notifier.send("receipt=" + orderId + " ready"); }
    public Notifier notifier() { return notifier; }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/AppConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }

    @Bean
    public OrderService orderService(Notifier notifier) { return new OrderService(notifier); }

    @Bean
    public ReceiptService receiptService(Notifier notifier) { return new ReceiptService(notifier); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            var orders = context.getBean(OrderService.class);
            var receipts = context.getBean(ReceiptService.class);
            orders.accept("O-004");
            receipts.ready("O-004");
            System.out.println("shared=" + (orders.notifier() == receipts.notifier()));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
EMAIL order=O-004 accepted
EMAIL receipt=O-004 ready
shared=true
notifier.close
```

两个服务通过方法参数得到同一通知器。原因是当前配置只有一个合适的 Notifier 定义，且它是默认单例；不是因为接口只能有一个实现。

现在配置中已经出现两类决定：怎样组装业务服务，以及选择哪种通知渠道。下一步按这两个职责拆分。

## 4. 拆分配置，再显式选择要装配的应用

新增 BusinessConfig，保留两个业务服务方法；新增 EmailConfig，只负责邮件渠道。旧 AppConfig 文件可以保留对照，但入口不再登记它，也不扫描它。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/BusinessConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessConfig {
    @Bean
    public OrderService orderService(Notifier notifier) { return new OrderService(notifier); }

    @Bean
    public ReceiptService receiptService(Notifier notifier) { return new ReceiptService(notifier); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/EmailConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmailConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailConfig.class)) {
            var orders = context.getBean(OrderService.class);
            var receipts = context.getBean(ReceiptService.class);
            orders.accept("O-004");
            receipts.ready("O-004");
            System.out.println("shared=" + (orders.notifier() == receipts.notifier()));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
EMAIL order=O-004 accepted
EMAIL receipt=O-004 ready
shared=true
notifier.close
```

业务输出和共享关系保持不变。配置拆分没有增加第二份通知器，只改变职责的组织位置。真正进入当前上下文的是入口列出的配置类，以及这些配置明确导入或登记的内容；磁盘上留着 AppConfig 不意味着它同时生效。

## 5. 替换一份渠道配置，保留业务配置

复用第002课已写过的短信实现，然后新增 SmsConfig。短信例子没有需要打开、关闭的资源，因此不配置邮件那组生命周期方法。

沿用第002课已经跟写完成的文件：`SmsNotifier.java`。从 `../002-ioc-di-object-assembly/src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/` 复制到本课 `src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/`，将这些文件的包声明及内部包引用中的 `lesson002` 改为 `lesson004`，其余内容先不改。这里复制的是你在上一课创建的 practice 文件；若尚未跟写，请先完成上一课对应步骤。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/SmsConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SmsConfig {
    @Bean
    public Notifier notifier() { return new SmsNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, SmsConfig.class)) {
            var orders = context.getBean(OrderService.class);
            var receipts = context.getBean(ReceiptService.class);
            orders.accept("O-004");
            receipts.ready("O-004");
            System.out.println("shared=" + (orders.notifier() == receipts.notifier()));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
SMS order=O-004 accepted
SMS receipt=O-004 ready
shared=true
```

两个业务入口都改用短信，而业务服务与 BusinessConfig 没有修改。当前邮件和短信配置是二选一，不能在尚未规划名称时直接把两份 notifier 定义混在同一上下文中。

## 6. 需要明确名称时，再给 Bean 命名

回到邮件配置，将通知器名字明确设为 emailNotifier。Java 方法名仍叫 notifier，但容器中的名称已经由注解覆盖。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/EmailConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmailConfig {
    @Bean(name = "emailNotifier", initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailConfig.class)) {
            Notifier byType = context.getBean(Notifier.class);
            Object byName = context.getBean("emailNotifier");
            Notifier byBoth = context.getBean("emailNotifier", Notifier.class);
            System.out.println("same=" + (byType == byName && byType == byBoth));
            System.out.println("runtime-type=" + byType.getClass().getSimpleName());
            System.out.println("old-name=" + context.containsBean("notifier"));
            context.getBean(OrderService.class).accept("O-004");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
same=true
runtime-type=EmailNotifier
old-name=false
EMAIL order=O-004 accepted
notifier.close
```

按类型、按名称、按名称加类型是在提供不同的查找条件。本例它们得到同一个对象。接口返回类型没有把运行时对象变成接口，实际类型仍是 EmailNotifier；方法名、Bean 名与产品类型也不是同一概念。

业务仍按唯一 Notifier 候选解析，因此改名未破坏装配。多个候选出现后，问题就会不同。

## 7. 先证明同类可以有两个单例，再观察候选歧义

新增只用于对照的配置，登记两个不同名字的 EmailNotifier。每个方法都按自己的定义创建产品。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/TwoEmailsConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TwoEmailsConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier first() { return new EmailNotifier(); }

    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier second() { return new EmailNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(TwoEmailsConfig.class)) {
            Object first = context.getBean("first");
            Object second = context.getBean("second");
            System.out.println("same-class=" + (first.getClass() == second.getClass()));
            System.out.println("same-instance=" + (first == second));
            System.out.println("first-reused=" + (first == context.getBean("first")));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
notifier.open
same-class=true
same-instance=false
first-reused=true
notifier.close
notifier.close
```

单例复用围绕定义和当前容器展开，不能简化成“一个 Java 类只有一个实例”。这两个定义名字不同，因此不是同名覆盖冲突。

接着先把短信明确命名为 smsNotifier，再同时选择两种渠道和业务配置。这样两个渠道名字既不冲突，也都不等于方法参数名 notifier，避免按参数名回退选择掩盖类型歧义。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/SmsConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SmsConfig {
    @Bean(name = "smsNotifier")
    public Notifier notifier() { return new SmsNotifier(); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.UnsatisfiedDependencyException;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BusinessConfig.class, EmailConfig.class, SmsConfig.class);
            try {
                context.refresh();
            } catch (UnsatisfiedDependencyException error) {
                System.out.println("root=" + error.getMostSpecificCause().getClass().getSimpleName());
                System.out.println("active=" + context.isActive());
            }
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
root=NoUniqueBeanDefinitionException
active=false
```

Spring 会输出取消刷新的警告。错误发生在解析依赖候选时，与重复登记同一个名字是不同问题。

本课应用仍然要求每次只选择一种渠道，所以修复为只登记邮件配置。业务真的需要多个渠道并存时，才应进一步设计明确的限定条件；@Qualifier、@Primary 等选择规则会在依赖解析专题展开。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson004/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson004.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailConfig.class)) {
            var orders = context.getBean(OrderService.class);
            var receipts = context.getBean(ReceiptService.class);
            orders.accept("O-004");
            receipts.ready("O-004");
            System.out.println("shared=" + (orders.notifier() == receipts.notifier()));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 004-java-config-object-assembly compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson004.practice.Main
```

```text
notifier.open
EMAIL order=O-004 accepted
EMAIL receipt=O-004 ready
shared=true
notifier.close
```

## 8. 在当前装配点进入源码

在 BusinessConfig.orderService 与 receiptService 上设置断点，检查两次参数 notifier 的引用。随后沿 DefaultListableBeanFactory 的依赖解析入口观察类型候选，联系刚才的唯一候选与歧义场景。

@Bean 返回接口类型时，不要在注册阶段只根据方法签名断言所有运行时类型细节。这里先通过实际返回对象和引用验证；更完整的类型预测、工厂方法元数据和配置增强会在后续源码系列解释。

源码：[DefaultListableBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultListableBeanFactory.java)。

## 9. 将手工观察转成下一课的断言

本课你先添加对象，再拆分配置、替换渠道，最后才制造名称和类型相关的问题。检查自己能否指出每一次变化发生在哪份配置，而不是只记住某个注解。

下一课沿用 OrderService、ReceiptService、Notifier 和 BusinessConfig，把“业务消息正确”和“配置装配正确”逐步写成不同层次的测试。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-005](../005-maintainable-container-tests/README.md)。

