# 08-01-004 使用 Java 配置显式组装对象：从一个 Bean 到一组协作对象

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-004) · [上一课](../003-runnable-debuggable-spring/README.md)

上一课已经能启动并调试容器。这一课沿用订单通知，先只登记一个通知器，再增加服务和回执入口；等配置职责出现差异后才拆分配置、切换实现，并验证名称与类型的边界。

本页按实际修改顺序跟写。文件路径相对于 `004-java-config-object-assembly`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson004.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 沿用业务类型，先只配置通知器

### 1.1 理论：类路径中的类型与容器产品的边界

第003课已经用 @Bean 运行过通知服务，本节先明确容器管理范围。在类路径中能找到一个 Java 类，只代表程序具备加载它的条件；是否由当前容器创建，还取决于是否接入了相应定义。

@Bean 描述当前工厂方法提供的产品。我们只登记通知器方法时，OrderService 即使已经编译，也不会因此被自动创建。本课不启用扫描，所以入口选择的配置及其声明决定当前对象集合。

### 1.2 实操：逐步实现并解释原理

沿用上一课的业务类，但先只写一个通知器工厂方法。Main 同时检查通知能力与服务是否登记，将“源码存在”和“容器已接入”分开观察。

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

### 1.3 小总结

对象进入容器需要明确入口。@Bean 管理方法产品，不会顺带把类路径上的所有类型变成 Bean。

<a id="topic-2"></a>

## 2. 服务需要通知器，于是增加一个工厂方法参数

### 2.1 理论：工厂方法参数怎样表达容器依赖

增加业务服务时，有两层参数传递。第一层由容器完成：创建 orderService 产品前，解析 @Bean 方法所需的 Notifier 参数。第二层由 Java 方法完成：把这个参数传给 OrderService 构造器并返回产品。

当前只有一个满足条件的通知器定义，容器可以取得它并继续创建服务。方法参数不要求在同一个配置类中就近寻找另一个方法，而是在当前容器的候选中解析依赖。

本课配置使用 proxyBeanMethods=false，不通过配置类增强拦截方法之间的普通调用。如果在方法体直接写 notifier()，执行的是普通方法调用；该方法里的 new 可能产生额外对象。参数注入则明确让容器提供受管理对象，避免把普通方法调用误当作容器查找。

### 2.2 实操：逐步实现并解释原理

新增 orderService 时，保留参数 Notifier notifier，并把它原样传入构造器。读到 new OrderService 时，回头检查 notifier 从方法参数进入，而不是在方法体里重新创建。

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

### 2.3 小总结

容器先解析工厂参数，Java 方法再创建产品。proxyBeanMethods=false 下的方法互调不自动具有容器查找语义，当前通过参数清楚表达依赖。

<a id="topic-3"></a>

## 3. 第二个业务入口也要用通知能力

### 3.1 理论：依赖解析与实例复用分别决定什么

订单服务与回执服务都需要通知能力。先由依赖解析确定它们各自应该使用哪个 Bean，再由该 Bean 的作用域决定取得的是已有实例还是新实例。

本例只有一个 Notifier 候选，且它是默认单例，因此两次参数解析得到同一对象。若存在多个候选，首先要解决选谁；若作用域不同，取得实例的规则也会不同。不能把这些决定都简化为“接口相同，所以对象相同”。

### 3.2 实操：逐步实现并解释原理

添加回执服务时仍使用同一个接口参数。Main 在验证两种业务输出之外，再比较它们保存的通知器引用，确认这次装配关系。

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

### 3.3 小总结

选哪个 Bean 与是否复用实例是两件事。当前唯一候选加默认单例，共同解释了两个服务共享通知器。

<a id="topic-4"></a>

## 4. 拆分配置，再显式选择要装配的应用

### 4.1 理论：配置拆分按装配职责组织

配置类可以分别负责稳定的业务关系和可替换的基础能力。BusinessConfig 声明两个服务需要 Notifier，EmailConfig 负责提供邮件实现；拆分后两者的定义仍进入同一个上下文。

所以配置文件分开，并不等于容器分开，也不会阻断工厂参数解析。另一方面，磁盘中留下旧 AppConfig 只是留下源码；没有显式登记、导入或扫描到它，就不会因此参与当前装配。

### 4.2 实操：逐步实现并解释原理

迁移工厂方法时，先核对每个产品只在本次选择的配置中声明一次，再修改 Main 的配置列表。业务输出与引用检查应保持原结果。

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

### 4.3 小总结

配置拆分组织的是装配职责。当前上下文实际选择了哪些配置，才决定哪些产品参与创建。

<a id="topic-5"></a>

## 5. 替换一份渠道配置，保留业务配置

### 5.1 理论：切换配置就是改变本次装配选择

业务依赖稳定的 Notifier 契约，因此切换渠道可以只替换提供方配置。两份渠道配置分别能满足需求，但本课应用每次只需要其中一种。

这里的替换发生在创建一个新上下文时选择配置，不是在已经运行的上下文中热替换所有对象引用。配置选择、依赖注入与运行时动态切换是不同能力，本节先完成启动时选择。

### 5.2 实操：逐步实现并解释原理

保留 BusinessConfig，只把入口的 EmailConfig 换成 SmsConfig。短信实现没有邮件资源回调，因此生命周期配置也应对应具体产品能力。

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

### 5.3 小总结

稳定业务配置可以搭配不同能力提供方。启动时二选一解决当前需求，多个实现并存则需要额外设计选择条件。

<a id="topic-6"></a>

## 6. 需要明确名称时，再给 Bean 命名

### 6.1 理论：Bean 名、方法名与产品类型各有职责

默认情况下，@Bean 方法名用于产品命名；显式指定名称后，注册名称按注解决定。Java 方法仍保留原方法名，所以“能普通调用哪个方法”和“能向容器请求哪个名字”是两个问题。

按类型查找表达所需能力，按名称查找指定某个注册对象，名称加类型则同时约束目标与类型。本例依赖解析只有一个 Notifier 候选，因此重命名不改变选择结果。

方法返回接口类型表达调用契约，实际返回的对象仍有其具体类。容器在不同阶段能掌握的类型信息可能不同，不能只凭接口返回签名推断全部运行时细节。

### 6.2 实操：逐步实现并解释原理

先修改 @Bean 的名称，再分别看 Main 的名称查询和类型查询。这里不要顺手修改业务服务参数类型，否则无法单独观察命名的影响。

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

### 6.3 小总结

名称标识注册对象，类型描述能力和兼容性，方法名标识 Java 创建入口。三者可能相关，但不能互相替代。

<a id="topic-7"></a>

## 7. 先证明同类可以有两个单例，再观察候选歧义

### 7.1 理论：同类单例、多类型候选与同名冲突

下面要区分三个常被混淆的问题。单例规定当前容器中某份定义如何复用实例；两个不同名字的定义可以使用同一个 Java 类，并各自拥有单例。它们能否共享对象，还取决于实际工厂返回什么，本节两个方法分别 new 产品。

当不同名字的邮件与短信都满足 Notifier 依赖时，定义可以同时存在，但消费者只要一个对象，容器需要进一步选择。当前没有限定条件，也没有能消除歧义的参数名匹配，因此会在依赖解析时失败。

同名冲突则发生在多个定义争用同一个注册名称时，属于注册层面的问题。解决名字冲突，不代表已经解决按类型选哪个；反过来也一样。

### 7.2 实操：逐步实现并解释原理

先验证两个邮件定义各自复用且彼此不同，再让邮件与短信以不同名字并存。观察失败发生在业务工厂方法取得依赖之前；最后恢复当前应用只选一种渠道的约束。

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

### 7.3 小总结

单例复用、定义命名和候选选择要分层判断。当前修复应符合业务只需一种渠道的目标，而不是随意让某个候选胜出。

<a id="topic-8"></a>

## 8. 在当前装配点进入源码

### 8.1 理论：从工厂参数追踪依赖解析

现在读源码应围绕一个问题：调用业务工厂方法之前，Notifier 参数怎样取得？先在自己的方法体暂停，可以看到解析后的结果；再到框架的依赖解析入口观察请求类型及候选选择。

工厂方法参数携带类型等依赖描述，创建流程把解析任务交给 BeanFactory。满足条件时取出候选对象，不满足时在方法真正执行前报告依赖问题。因而业务方法断点未命中，不一定是配置未登记，也可能是参数准备失败。

本节理解这条职责分工即可。完整的候选优先级、类型预测和配置增强会分别深入，不需要一次读完整个 BeanFactory。

### 8.2 实操：逐步实现并解释原理

先恢复第7节正常邮件配置，在 orderService、receiptService 比较实际引用，再复现歧义配置观察失败位置。读 DefaultListableBeanFactory 的 resolveDependency 与 doResolveDependency 时，确认请求类型是 Notifier，并跟进实际进入的候选分支；不要根据方法名猜它执行过哪个分支。

在 BusinessConfig.orderService 与 receiptService 上设置断点，检查两次参数 notifier 的引用。向上查看创建调用栈，工厂参数准备可以跟到 ConstructorResolver，再进入 DefaultListableBeanFactory 的 resolveDependency、doResolveDependency。

先确认依赖描述要求的是 Notifier，再观察当前分支如何确定候选名称并取得实例。正常邮件配置下，只需要解释为什么能够确定邮件对象；两种渠道并存的对照中，再跟到候选不能确定的失败位置。工厂方法体没有拿到参数时，不会执行其中的 new OrderService。

不要把某一次调试恰好经过的分支当成所有依赖解析的固定步骤。框架存在按名称等信息处理和复用解析结果的路径；本课先用当前的唯一候选与歧义两个场景说明它分别作了什么决定。

补充源码：[ConstructorResolver](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ConstructorResolver.java)。

@Bean 返回接口类型时，不要在注册阶段只根据方法签名断言所有运行时类型细节。这里先通过实际返回对象和引用验证；更完整的类型预测、工厂方法元数据和配置增强会在后续源码系列解释。

源码：[DefaultListableBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/DefaultListableBeanFactory.java)。

### 8.3 小总结

源码阅读应连接依赖描述、候选解析和工厂实参。先解释当前装配成功或失败，再逐步扩展其他选择规则。

<a id="course-summary"></a>

## 9. 本课总结

### 9.1 本课解决的问题

本课学习用 Java 配置组织一组协作对象：让两个服务复用通知能力，按职责拆分配置，切换渠道，并处理名称、类型和多候选的区别。

### 9.2 把原理串起来

入口选择配置，配置中的 @Bean 方法提供产品。业务工厂方法声明需要的 Notifier，容器先解析候选，再把实际对象作为参数传入，Java 方法据此构造服务。当前唯一候选及默认单例规则共同决定两个服务共享通知器。

把服务配置与渠道配置拆开后，它们仍为同一个上下文提供定义。切换提供方即可改变渠道；显式命名则改变注册标识。不同名字的多个实现可以共存，但单值依赖还需要明确选谁。

### 9.3 核心结论与边界

| 设计问题 | 本课结论 |
| --- | --- |
| 哪些类被管理？ | 看当前配置入口和登记内容，不能只看磁盘文件 |
| 工厂参数从哪里来？ | 先由容器解析，再由 Java 方法传入业务构造器 |
| 一个类能有几个单例？ | 不同定义可以各自创建单例，单例不等于类型全局唯一 |
| 改名是否影响注入？ | 要区分按名称使用与类型候选解析 |
| 两种渠道为什么报歧义？ | 本例都满足单值依赖，却没有足以确定唯一对象的条件 |

proxyBeanMethods=false 下，普通工厂方法互调不自动等于容器获取；当前使用参数注入。启动时更换配置，也不等于运行中自动替换已有引用。

### 9.4 如何应用与检查理解

组织应用配置时，将稳定业务关系与可替换提供方分开。遇到错误先判断是定义未接入、名字冲突，还是类型候选无法选择。

能解释“两个 EmailNotifier 各自单例但互不相同”，以及“名字不同仍可能发生类型歧义”，就能避免把注册与依赖解析混在一起。

### 9.5 复习与后续学习

本课你先添加对象，再拆分配置、替换渠道，最后才制造名称和类型相关的问题。检查自己能否指出每一次变化发生在哪份配置，而不是只记住某个注解。

下一课沿用 OrderService、ReceiptService、Notifier 和 BusinessConfig，把“业务消息正确”和“配置装配正确”逐步写成不同层次的测试。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-005](../005-maintainable-container-tests/README.md)。
