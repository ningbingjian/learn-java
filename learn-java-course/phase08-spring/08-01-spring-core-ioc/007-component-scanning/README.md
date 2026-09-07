# 08-01-007 组件扫描与注解注册：从手动登记一步步改成扫描

[返回模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-007) · [上一课](../006-bean-definition-model/README.md)

这一次，我们先写出一个能运行的通知器，再让它逐渐拥有仓库、业务服务和扫描边界。每次只为眼前的问题增加代码；需要过滤、命名或诊断时，再引入相应机制。

跟写代码使用独立的 `cn.ningbingjian.learnjava.ioc.lesson007.practice` 包，仍属于本课 Maven 子模块。**从第1步开始创建文件，后续按指示替换同一个文件；不要提前复制完成版所有类。** 仓库已有的 `lesson007.app` 等包是完整对照示例，不需要删除，也不会被下面的 `practice.app` 扫描范围纳入。

本页中的文件路径均相对于 `007-component-scanning`。命令在父目录 `08-01-spring-core-ioc` 执行，沿用 JDK 21 和 Maven 3.9.x。命令中的 `exec.mainClass` 指向你正在编写的入口；不传它时仍运行仓库原有演示。

学习顺序是：手动登记一个对象 → 让扫描发现它 → 组织配置入口 → 增加业务依赖 → 控制名称与扫描范围 → 排查漏扫 → 使用过滤器 → 理解重复注册和同名冲突。核心原理在正文展开，更多边界可在学完后查阅 [参考页](02-机制与边界参考.md)。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 先亲手登记一个通知器

### 1.1 理论：显式登记与扫描是不同接入入口

第004课已说明类可以通过明确的配置或类型登记进入容器，第006课进一步区分了定义与实例。本节先复用显式登记建立一个通知器，作为随后替换入口的对照。

显式登记时，启动代码已经把目标类型交给上下文，不需要靠 @Component 让扫描器发现它。是否需要组件标记，应结合实际接入方式判断，不能把扫描的一项条件推广成所有 Bean 的前提。

### 1.2 实操：逐步实现并解释原理

先写普通 Notifier，再用当前上下文构造器明确提供 Notifier.class。这个构造器完成注册与刷新，输出用于验证业务基线，下一节再改变发现入口。

我们先实现“输出一次订单通知”，暂时只需要一个类。新建 `app/delivery/Notifier.java`，让 `send` 接收订单编号。这个类现在没有 Spring 注解。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/delivery/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery;

public class Notifier {
    public void send(String orderId) {
        System.out.println("email order=" + orderId);
    }
}
```

接着创建入口，显式把 `Notifier.class` 交给上下文。这个入口复用第004课的显式注册方式：先能运行一个明确的对象，再考虑是否需要扫描。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Notifier.class)) {
            context.getBean(Notifier.class).send("O-007");
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
```

此时容器知道 `Notifier`，因为启动代码明确列出了它。类上没有 `@Component` 也能被显式注册。先记住这个因果关系：**“进入容器”不只有扫描一种入口。**

接下来希望同一个业务包新增组件后，不必每次修改这个类型列表，因此才引入扫描。

### 1.3 小总结

显式登记直接指定类型，扫描则按范围和规则发现类型。进入容器不只有组件扫描一种路径。

<a id="topic-2"></a>

## 2. 给扫描器一个标记，再真正执行扫描

### 2.1 理论：扫描先发现并登记定义

类越来越多时，逐个维护启动类型列表容易遗漏。组件扫描将这个列表改为“基础包加候选规则”：在范围内寻找类文件，读取类型和注解等元数据，筛选候选，再形成并登记 BeanDefinition。

默认规则可以识别 @Component 及相应组合标记，但注解只提供候选信息。必须有扫描入口执行发现，类也必须位于当前范围内。通过候选筛选后还要满足组件资格和注册要求，并非看到注解就立即 new。

本节把 scan 与 refresh 分开，先观察定义登记，再观察实例创建。这个顺序直接连接第006课的模型。

### 2.2 实操：逐步实现并解释原理

增加 @Component 后，再把 Main 从显式类型列表改成 scan。刷新前分别查询定义与单例状态：两个查询观察不同阶段，不要用 getBean 代替“有没有实例”的只读检查。

先在通知器上增加 `@Component`。它提供默认扫描器能够识别的候选标记；注解本身不会主动创建对象。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/delivery/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery;

import org.springframework.stereotype.Component;

@Component
public class Notifier {
    public void send(String orderId) {
        System.out.println("email order=" + orderId);
    }
}
```

再替换入口：不再显式登记 `Notifier.class`，改为扫描它所属的业务根包。把扫描和刷新分开，是为了观察“定义已登记”和“实例已创建”两个时刻。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan("cn.ningbingjian.learnjava.ioc.lesson007.practice.app");
            System.out.println("definition=" + context.containsBeanDefinition("notifier"));
            System.out.println("instance=" + context.getBeanFactory().containsSingleton("notifier"));
            context.refresh();
            context.getBean(Notifier.class).send("O-007");
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
definition=true
instance=false
email order=O-007
```

`scan` 已经发现并登记定义，但业务单例仍未创建；`refresh` 后才可以按这里的完整上下文流程使用对象。这与第006课的定义模型连接起来了。

现在你可以解释为什么要改两个地方：`@Component` 声明候选资格，`scan` 提供范围并执行发现与登记。只增加注解而没有扫描入口，不会让一个无关包自动进入容器。

### 2.3 小总结

扫描负责发现和登记，创建流程负责产生对象。标记、范围和实际扫描入口需要配合，注解不会自行执行。

<a id="topic-3"></a>

## 3. 把扫描边界移到配置类

### 3.1 理论：基础包是应用的发现边界

扫描边界决定当前应用接入哪些类型。字符串可以表达包名，但重构时容易失去同步；basePackageClasses 使用某个类型所在的包作为定位依据，更容易随 Java 重构更新。

这个参数不是只注册指定类型。AppComponents 是位置标记，它所在的 practice.app 及子包才是候选范围。把配置放在并列包，再由入口明确选择配置，可以让实验配置与业务组件的发现范围分开。

直接调用 context.scan 会在调用处执行扫描；通过 @ComponentScan 则先登记配置类，再在刷新中的配置解析过程触发扫描。发现目的相同，发生时点不同。

### 3.2 实操：逐步实现并解释原理

先创建空标记接口，再让 ScanConfig 引用它。Main 只选择这份配置，跟读时分别找出配置如何进入上下文、组件范围如何由它确定。

字符串包名可以运行，但重构时可能漏改。我们创建一个空接口 `AppComponents`，仅用它标记 `practice.app` 的位置；它不是要实例化的组件。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/AppComponents.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app;

public interface AppComponents {
}
```

创建 `config/ScanConfig.java`。配置类放在 `app` 的并列包中，由入口明确选择；当前业务扫描不会把实验配置也扫进去。

下面的 `basePackageClasses` 表示“这个类型所在的包及子包”，不是“只注册这个类型”。此时配置只需三个 import，其他过滤器要等后面需要时再加入。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class)
public class ScanConfig {
}
```

入口现在只选择这份配置。这里采用会自动刷新的构造器，因此无需再次调用 `refresh()`。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            context.getBean(Notifier.class).send("O-007");
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
```

业务输出保持一致，但入口发生了变化：上一步的 `context.scan` 在方法调用时扫描；现在先登记配置类，刷新期间由配置解析过程读取 `@ComponentScan` 并触发扫描。

没有指定扫描包时，`@ComponentScan` 默认使用声明它的配置类所在包。因此不要把它理解为“从 main 方法所在包扫描”。当前范围由 `AppComponents` 明确决定。

### 3.3 小总结

配置入口与扫描范围需要分别设计。basePackageClasses 定位的是包，不是仅登记该类型。

<a id="topic-4"></a>

## 4. 让通知器参与真实的对象协作

### 4.1 理论：发现组件与满足构造依赖分属两步

服务需要仓库和通知器。扫描先根据包与标记发现三份定义，创建服务时再根据构造器参数解析具体对象。依赖解析依赖当前容器已经具备的定义，不会因为参数需要某类型就自动扩大全项目扫描。

@Service、@Repository 带有 @Component 元注解，因此默认组件规则可以识别它们；同时它们表达业务和数据访问角色。角色标记不等于自动具备事务、SQL 生成等完整功能，这些能力还需要相应基础设施。

当前服务只有一个构造器，框架可以据此选择注入入口；构造器声明两个必需协作者，完整参数得到满足后才能构造服务。

### 4.2 实操：逐步实现并解释原理

先增加仓库，再写服务唯一构造器，最后让 Main 调用服务。扫描配置保持原样，逐个核对新类都在 app 边界内；业务执行时由服务组织仓库检查与通知顺序。

现在增加需求：只有订单存在时才通知。先创建内存仓库，它只认识 `O-007`；这里使用 `@Repository` 表达数据访问角色，不连接数据库。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/data/OrderRepository.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.data;

import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    public boolean exists(String orderId) {
        return "O-007".equals(orderId);
    }
}
```

然后创建业务服务。它需要仓库和通知器，于是把两个依赖写进唯一构造器，不在服务内部自己 `new`。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/service/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.data.OrderRepository;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final Notifier notifier;

    public OrderService(OrderRepository repository, Notifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    public boolean accept(String orderId) {
        if (!repository.exists(orderId)) {
            return false;
        }
        notifier.send(orderId);
        return true;
    }
}
```

最后把入口改为调用服务。扫描配置不用改，因为新增的 `app.data`、`app.service` 都在已有边界内。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            System.out.println("accepted=" + context.getBean(OrderService.class).accept("O-007"));
            System.out.println("missing=" + context.getBean(OrderService.class).accept("O-404"));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
accepted=true
missing=false
```

这次新增对象能被发现，是因为它们的包位置和注解满足当前扫描规则。`@Service`、`@Repository` 都带有 `@Component` 元注解；它们表达角色，默认扫描器也能识别它们。

发现类与解析构造器依赖仍是不同工作。创建服务时，容器才需要解析两个参数；本例只有一个构造器，不必再加 `@Autowired`。`@Service` 不会自行开启事务，`@Repository` 也不会替你生成 SQL，相关能力需要另外的基础设施。

### 4.3 小总结

扫描回答哪些定义进入容器，依赖解析回答创建某对象时使用谁。组件角色注解与具体业务基础设施也需要区分。

<a id="topic-5"></a>

## 5. 需要按名字查找时，再学习名称规则

### 5.1 理论：名称生成发生在定义登记之前

注册对象需要名称。默认 AnnotationBeanNameGenerator 先考虑显式注解名称；没有显式名称时，从简单类名生成默认名，通常首字母小写，但开头两个字母都大写时保留原样。

因此 URLCodec 的默认名仍是 URLCodec，不能只背“所有组件名首字母变小写”。给 Notifier 显式命名为 emailNotifier 后，旧默认名也不会自动保留成别名。

第004课已区分名称与类型。本节只改变名称，没有新增同类型候选，服务原先的唯一类型依赖仍可以解析。

### 5.2 实操：逐步实现并解释原理

先改通知器注解值，再加入 URLCodec。Main 分别检查旧名、新名和工具类名，解释哪些结果来自显式名称，哪些来自默认生成算法。

现在希望明确通知器的名字。只把 `Notifier` 上的标记改为 `@Component("emailNotifier")`，其余业务代码保留。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/delivery/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery;

import org.springframework.stereotype.Component;

@Component("emailNotifier")
public class Notifier {
    public void send(String orderId) {
        System.out.println("email order=" + orderId);
    }
}
```

再加一个带组件注解的工具类 `URLCodec`，观察以两个大写字母开头时的默认名称。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/utility/URLCodec.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility;

import org.springframework.stereotype.Component;

@Component
public class URLCodec {
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            System.out.println("accepted=" + context.getBean(OrderService.class).accept("O-007"));
            System.out.println("email-name=" + context.containsBean("emailNotifier"));
            System.out.println("old-name=" + context.containsBean("notifier"));
            System.out.println("URLCodec=" + context.containsBean("URLCodec"));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
accepted=true
email-name=true
old-name=false
URLCodec=true
```

服务仍然能够注入通知器，因为本例的唯一类型候选没有改变。按名称查找的入口却变了，旧名字不会自动成为别名。

默认 `AnnotationBeanNameGenerator` 通常把简单类名的首字母小写，但前两个字母都大写时保留原样，因此这里是 `URLCodec`，不是 `uRLCodec`。显式注解名称优先于默认名称。多个同类型候选如何选择，在依赖解析课程继续讨论。

### 5.3 小总结

Bean 名由实际命名策略生成，显式名称优先。重命名是否影响使用者，还要看它通过名称还是候选类型获取对象。

<a id="topic-6"></a>

## 6. 验证扫描边界，不凭“类上有注解”猜测

### 6.1 理论：未登记可能发生在不同筛选环节

类上有无注解只回答候选规则的一部分。扫描还需要在类路径中找到资源，并且该资源位于所选基础包内；进入范围后才进一步检查过滤规则和组件资格。

本节设置两个对照：PlainHelper 在范围内却没有默认组件标记；AuditReporter 有标记却在并列的范围外。两者最终都没有定义，但失败环节不同。

实际排查可沿资源、范围、规则、注册结果逐项检查。如果类根本没有进入资源范围，修改包含过滤器也无法让本次扫描凭空看到它。

### 6.2 实操：逐步实现并解释原理

先在 app 内新增普通类，再在 outside 新增标记类。运行前根据包结构分别预测原因，运行后不要把两个 false 合并解释成“缺少注解”。

先在扫描范围内加入一个没有注解的普通类。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/app/utility/PlainHelper.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility;

public class PlainHelper {
    public String label() { return "plain-helper"; }
}
```

再在 `practice.outside` 中加入一个带组件注解的类。这个包与 `practice.app` 并列，位于当前扫描范围外。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/outside/AuditReporter.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.outside;

import org.springframework.stereotype.Component;

@Component
public class AuditReporter {
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            System.out.println("accepted=" + context.getBean(OrderService.class).accept("O-007"));
            System.out.println("email-name=" + context.containsBean("emailNotifier"));
            System.out.println("old-name=" + context.containsBean("notifier"));
            System.out.println("URLCodec=" + context.containsBean("URLCodec"));
            System.out.println("plain=" + context.containsBean("plainHelper"));
            System.out.println("outside=" + context.containsBean("auditReporter"));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
accepted=true
email-name=true
old-name=false
URLCodec=true
plain=false
outside=false
```

两个结果都为 `false`，原因不同：一个进入了包范围却不符合默认组件规则，另一个有标记但没有进入扫描范围。排查时要分别确认类路径、基础包和候选规则，不能只检查注解。

接下来希望保留业务组件，却排除 `URLCodec`，因此才引入排除过滤器。

### 6.3 小总结

最终没有 Bean 是结果，排查还要找出在哪一层未被纳入。范围问题与候选规则问题需要不同修复。

<a id="topic-7"></a>

## 7. 排除一个类，确认业务仍然成立

### 7.1 理论：排除规则决定哪些候选退出

已经位于扫描范围内的类，也可能因为不属于当前应用职责而需要排除。excludeFilters 在候选过滤中优先检查，命中排除后不会再因包含规则重新入选。

ASSIGNABLE_TYPE 按类型可赋值关系匹配，可以覆盖指定类型及其可赋值子类型；它不按 Bean 名字符串匹配。排除 URLCodec 影响的是定义是否登记，而不是创建对象后再把它关闭。

同时要检查剩余业务依赖是否仍完整。排除无关工具与排除必需仓库，后果完全不同。

### 7.2 实操：逐步实现并解释原理

仅替换配置中的排除规则。检查 URLCodec 消失后订单服务仍能运行，这才能说明本次排除没有破坏当前业务依赖。

只替换 `ScanConfig.java`，入口和业务类全部保留。这里增加 `FilterType` 和 `URLCodec` 的 import。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility.URLCodec;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = URLCodec.class))
public class ScanConfig {
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
accepted=true
email-name=true
old-name=false
URLCodec=false
plain=false
outside=false
```

输出中 `URLCodec` 变成 `false`，订单通知仍成功。`ASSIGNABLE_TYPE` 按类型可赋值关系匹配，不按 Bean 名字符串匹配；这里也会匹配其可赋值子类型，尽管当前没有这样的子类。

如果移除的是业务必需依赖，就不能只满意于“列表少了一个组件”。下一步故意收窄扫描范围，看看服务找得到、依赖找不到时发生什么。

### 7.3 小总结

排除发生在候选筛选阶段，并优先于包含规则。验证过滤效果还要检查保留下来的业务是否能完整装配。

<a id="topic-8"></a>

## 8. 故意漏扫一次，并在原位置修复

### 8.1 理论：漏扫在依赖创建阶段怎样暴露

把基础包收窄到 app.service，会发现服务，却看不到位于并列包的仓库和通知器。服务定义能够登记，但创建服务需要的参数不再完整。

因此失败可以发生在 refresh 内解析构造依赖时，而不是 scan 一开始。错误中的注入点与缺失类型，能够反向提示哪个应参与的组件没有进入当前上下文。

修复应恢复符合业务边界的包范围。盲目扩大到所有课程根包虽然可能找到缺失类，也可能把其他实验配置引入，造成新冲突。

### 8.2 实操：逐步实现并解释原理

只改基础包并在 refresh 处捕获当前预期异常，观察第0个构造参数指向哪个依赖。恢复 AppComponents 边界后，再运行原业务入口验证修复。

把配置改为从 `OrderService` 所在包开始扫描。它位于 `app.service`，并列的 `app.data` 与 `app.delivery` 不在这个范围。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = OrderService.class)
public class ScanConfig {
}
```

入口暂时改成显式刷新并捕获本次预期异常，以便只观察稳定的错误摘要。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import org.springframework.beans.factory.UnsatisfiedDependencyException;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ScanConfig.class);
            try {
                context.refresh();
            } catch (UnsatisfiedDependencyException error) {
                System.out.println("failed-bean=" + error.getBeanName());
                System.out.println("root=" + error.getMostSpecificCause().getClass().getSimpleName());
                System.out.println("active=" + context.isActive());
            }
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
failed-bean=orderService
root=NoSuchBeanDefinitionException
active=false
```

Spring 还会输出取消刷新的警告。服务定义已经被发现，失败发生在刷新期间创建服务、解析构造器第0个参数时：仓库没有选入。扫描器不会看到构造参数后就自动去其他包寻找实现。

修复应恢复正确的业务边界。现在把配置和入口恢复为下面两份内容，然后运行确认。不要扩大到整个项目根包，那会把不相关的实验配置也纳入。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class)
public class ScanConfig {
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            System.out.println("accepted=" + context.getBean(OrderService.class).accept("O-007"));
            System.out.println("email-name=" + context.containsBean("emailNotifier"));
            System.out.println("old-name=" + context.containsBean("notifier"));
            System.out.println("URLCodec=" + context.containsBean("URLCodec"));
            System.out.println("plain=" + context.containsBean("plainHelper"));
            System.out.println("outside=" + context.containsBean("auditReporter"));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
email order=O-007
accepted=true
email-name=true
old-name=false
URLCodec=true
plain=false
outside=false
```

### 8.3 小总结

定义被发现不等于依赖能满足。漏扫应结合缺失类型和包结构修复，不能只看服务类自己有没有注解。

<a id="topic-9"></a>

## 9. 只想选择两个类型，为什么还要关闭默认规则

### 9.1 理论：包含规则默认是扩展而非替换

includeFilters 为扫描增加接纳候选的规则。默认组件过滤器仍启用时，一个类匹配默认规则或新增包含规则，都可能继续进入资格检查；新增规则不会自动排除原来符合默认规则的组件。

如果当前目标是只选择两个指定类型，需要关闭默认规则，再添加这两个类型的包含条件。即便如此，排除规则、条件判断和组件资格仍然有效，基础包范围也不会改变。

PlainHelper 是可实例化普通类，所以可以通过指定类型过滤器接入，即使没有 @Component。这解释了为什么注解不是所有注册入口的必要条件。

### 9.2 实操：逐步实现并解释原理

第一次关闭默认规则，分别查看通知器、PlainHelper 与其他组件是否存在；第二次仅恢复默认规则，再预测原业务组件为什么返回。两次对照只改变一个开关。

现在做一个独立筛选需求：只纳入通知器与无注解的 `PlainHelper`，暂时不启动订单服务。先明确本次目标，再修改配置。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility.PlainHelper;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {Notifier.class, PlainHelper.class}))
public class ScanConfig {
}
```

入口也要随上下文目标调整：本次不再请求没有选入的订单服务，而是检查包含规则的效果。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.config.ScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility.PlainHelper;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            System.out.println("helper=" + context.getBean(PlainHelper.class).label());
            System.out.println("service=" + context.containsBean("orderService"));
            System.out.println("utility=" + context.containsBean("URLCodec"));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
helper=plain-helper
service=false
utility=false
```

无注解类现在也被选入，说明候选资格取决于实际过滤器；默认组件注解只是其中一种规则。

接着只恢复默认过滤器，保留包含规则和入口。这次预测服务与工具类是否会重新出现，再运行：

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/config/ScanConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.config;

import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.delivery.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.utility.PlainHelper;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {Notifier.class, PlainHelper.class}))
public class ScanConfig {
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
helper=plain-helper
service=true
utility=true
```

默认过滤器仍存在时，`includeFilters` 增加候选规则，没有把其他默认组件排除掉。要表达“只选择这两个类型”，才需要前一步的 `useDefaultFilters = false`。

包含过滤器也不会扩大基础包。`outside.AuditReporter` 仍然不在本次发现范围内。更多过滤类型和资格检查放在 [机制参考](02-机制与边界参考.md) 中，先掌握这个最常见的误区。

### 9.3 小总结

包含规则通常增加候选来源。表达精确选择时要同时考虑默认规则、排除规则和基础包，而不是只写 includeFilters。

<a id="topic-10"></a>

## 10. 回到注册时刻，判断重复扫描做了什么

### 10.1 理论：重复发现与重复创建是不同问题

相同包扫描两次，可能再次发现同一类文件，但注册器还会检查已有同名定义。对于本例相同规则下的兼容定义，第二次可以跳过登记，而不是保存一份重复定义。

随后刷新并多次获取通知器时，是否复用实例又由作用域决定。定义去重处理扫描注册，单例复用处理对象获取；两者发生在不同阶段。

如果重复扫描时换了规则、名称或定义来源，就需要重新分析实际兼容分支，不能把本例结果推广成“任何重复注册都被忽略”。

### 10.2 实操：逐步实现并解释原理

先比较两次 scan 后的定义数量及同一名称，再刷新比较对象引用。解释每条输出究竟证明定义兼容还是实例复用。

最后几个步骤直接调用扫描器入口，不再使用实验 `ScanConfig`。替换入口，连续扫描两次同一个业务包，比较定义与实例状态。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import cn.ningbingjian.learnjava.ioc.lesson007.practice.app.service.OrderService;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan("cn.ningbingjian.learnjava.ioc.lesson007.practice.app");
            var first = context.getBeanFactory().getBeanDefinition("orderService");
            int count = context.getBeanDefinitionCount();
            context.scan("cn.ningbingjian.learnjava.ioc.lesson007.practice.app");
            System.out.println("count-unchanged=" + (count == context.getBeanDefinitionCount()));
            System.out.println("same-definition=" + (first == context.getBeanFactory().getBeanDefinition("orderService")));
            System.out.println("has-instance=" + context.getBeanFactory().containsSingleton("orderService"));
            context.refresh();
            System.out.println("same-service=" + (context.getBean(OrderService.class) == context.getBean(OrderService.class)));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
count-unchanged=true
same-definition=true
has-instance=false
same-service=true
```

前两行说明相同规则下的兼容定义被保留，扫描没有重复登记。最后一行说明刷新后默认单例被复用。定义兼容性判断与单例复用是两个阶段的事情，不能合并成一句“Spring 不会重复创建”。

### 10.3 小总结

扫描到不代表必须再次登记，已登记也不代表每次获取都新建。定义兼容与实例作用域需要分别解释。

<a id="topic-11"></a>

## 11. 用两个不同类制造真正的同名冲突

### 11.1 理论：同名不兼容定义会阻止注册

不同包里的两个 NotificationClient 具有相同简单类名。默认命名策略会为二者生成 notificationClient，而注册表需要用一个名字标识具体定义。

本例两个都是扫描发现、来源和类不同的不兼容定义。第二个候选占用已有名称时，注册器不能把它视为同一份重复发现，因而抛出冲突异常。这时还没有进入业务构造依赖解析。

解决方法要对应问题：如果两类都属于应用，就给它们不同名字；如果其中一个不该接入，就调整扫描边界。单纯处理类型选择不能消除已经发生的名称冲突。

### 11.2 实操：逐步实现并解释原理

在隔离 collision 包中构造冲突，保持两个类都带组件标记。Main 将 scan 与 refresh 分开，观察异常是否在 scan 调用处就出现。

相同类重复扫描没有冲突，不代表同名定义都能兼容。分别创建下面两个简单类名相同、包不同的组件。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/collision/one/NotificationClient.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.collision.one;

import org.springframework.stereotype.Component;

@Component
public class NotificationClient {
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/collision/two/NotificationClient.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice.collision.two;

import org.springframework.stereotype.Component;

@Component
public class NotificationClient {
}
```

只扫描隔离的 `practice.collision` 包，观察错误出现在扫描时还是刷新时。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            try {
                context.scan("cn.ningbingjian.learnjava.ioc.lesson007.practice.collision");
            } catch (IllegalStateException error) {
                System.out.println("scan-error=" + error.getClass().getSimpleName());
                System.out.println("active=" + context.isActive());
            }
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
scan-error=ConflictingBeanDefinitionException
active=false
```

两个默认名称都是 `notificationClient`，但类和定义不兼容，因此扫描阶段就报错，甚至还没有调用 `refresh()`。它与第8步的缺失依赖不是同一类故障。

若两个类都应存在，可以显式起不同名字。另一种做法是改变默认命名策略，下一步就沿当前冲突现场修改。

### 11.3 小总结

重复扫描同一兼容定义可以跳过，不同类争用同名则可能冲突。先判断注册阶段的名称问题，再考虑后续依赖选择。

<a id="topic-12"></a>

## 12. 在扫描之前改名，再回到源码解释分支

### 12.1 理论：命名策略与候选注册分支如何衔接

名称在扫描登记前生成，因此要更换默认命名策略，应在扫描开始前设置。完整类名包含包路径，本例两个同名简单类处于不同包，生成的默认名称就可以区分。

这项策略仍尊重显式注解名称；如果两个组件都显式指定同一名字，完整类名策略不会替我们重写它。策略改变的是默认命名，不是无条件保证任何配置都不冲突。

源码阅读围绕已经观察的三个结果展开：首次候选为何登记，同一候选为何跳过，不同候选同名为何报错。把 beanName、候选定义和已有定义对应起来，才能读懂 checkCandidate 的返回值。

### 12.2 实操：逐步实现并解释原理

先在现有冲突入口设置完整类名策略，再用完整名称获取两个对象。随后带着第10、11节的配置分别进入 doScan 和 checkCandidate，比较同一位置的输入如何决定不同分支。

替换入口，在扫描之前设置完整类名策略，并按两个完整名称取对象。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson007.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
            context.scan("cn.ningbingjian.learnjava.ioc.lesson007.practice.collision");
            context.refresh();
            Object first = context.getBean("cn.ningbingjian.learnjava.ioc.lesson007.practice.collision.one.NotificationClient");
            Object second = context.getBean("cn.ningbingjian.learnjava.ioc.lesson007.practice.collision.two.NotificationClient");
            System.out.println("different-objects=" + (first != second));
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson007.practice.Main
```

```text
different-objects=true
```

这次两个默认名字不同，定义可以并存。这个策略仍尊重显式注解名称；如果两类显式写了同一个名字，它不会自动改名。

现在在固定的 Spring v7.0.9 中，沿 `ClassPathBeanDefinitionScanner.doScan` 进入 `checkCandidate`。对照三个输入：`beanName` 是本次生成的名称，`beanDefinition` 是新候选，`existingDef` 是注册表中已有的同名定义。

| 对照步骤 | 当前条件 | 该方法在本例中的结果 |
| --- | --- | --- |
| 第10节第一次扫描 | 名字尚未登记 | 返回 true，允许继续登记 |
| 第10节第二次扫描 | 同一来源或等价的兼容扫描定义已存在 | 返回 false，跳过再次登记 |
| 第11节两个不同扫描类同名 | 已有定义与新候选不兼容 | 抛出 ConflictingBeanDefinitionException |
| 第12节改用完整默认名称 | 两个不同名称分别尚未登记 | 两份定义分别通过名称检查 |

这张表限定在本课的扫描定义场景。源码还处理已有定义来自显式登记等分支，不能把它们都概括成“同名一定报错”。先看已有定义的种类和来源，再解释实际命中的条件。

继续回看第7、9节过滤场景，在 `ClassPathScanningCandidateComponentProvider.isCandidateComponent(MetadataReader)` 暂停：先遍历排除过滤器，命中就返回 false；再检查包含过滤器，匹配后还要通过条件判断。之后针对定义的资格检查还会考虑类型是否独立、是否为可实例化的具体类等条件。当前 PlainHelper 是普通具体类，所以通过指定类型包含规则后具备相应资格。

因此“不进入最终注册表”可能来自范围、过滤、资格或兼容处理，不能只看到某个 return false 就断定没有读到类资源。

源码：[扫描注册器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathBeanDefinitionScanner.java)、[候选发现器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathScanningCandidateComponentProvider.java)、[默认命名生成器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/AnnotationBeanNameGenerator.java)。

第7、9步可以继续追踪候选发现器的过滤过程：先检查排除规则，再检查包含规则和其他条件。扫描能利用类文件元数据，不需要先构造每个业务对象；这不等于所有扩展过滤器都绝不加载类。

### 12.3 小总结

命名策略决定候选的注册标识，兼容检查决定能否登记。源码返回 false 可能表示已有兼容定义，而不是未扫描到类。

<a id="course-summary"></a>

## 13. 本课总结

### 13.1 本课解决的问题

本课把显式类型登记改为有边界的组件扫描，并解释组件为什么被发现、为什么未接入，以及重复定义和同名冲突在什么阶段处理。

### 13.2 把原理串起来

扫描先在基础包范围内发现类资源，通过排除、包含、条件和组件资格检查得到候选，再生成名称并检查与已有定义是否兼容，最后完成登记。刷新中的创建流程随后解析依赖、产生业务对象。由配置注解触发扫描时，扫描本身也发生在刷新中的配置解析阶段。

因此有注解但在范围外仍不能被本次扫描发现；无注解的普通具体类也可以通过合适的包含规则入选。同一兼容定义可以跳过重复登记，不同扫描类争用默认同名则可能在注册时失败；多次取得同一单例属于后续实例复用问题。

### 13.3 核心结论与边界

| 现象 | 应检查的机制 |
| --- | --- |
| 有标记却没有 Bean | 类资源、基础包、过滤、条件和注册结果 |
| include 后其他组件仍在 | 默认包含规则是否仍然启用 |
| 服务已发现但启动失败 | 创建期间是否能满足全部构造依赖 |
| 重复扫描没有新增定义 | 同名已有定义的兼容检查 |
| 不同类在扫描时冲突 | 名称生成与不兼容定义冲突 |
| 完整类名策略仍冲突 | 是否存在同名的显式注解名称 |

扫描不会按构造器需要自动扩展到其他包；扩大到整个项目根包也可能带入无关配置。默认命名、类型查找和单例复用是不同规则。

### 13.4 如何应用与检查理解

以明确的业务根包组织扫描，将实验配置置于可控入口。遇到问题先定位是候选未入选、登记冲突还是依赖创建失败，再调整对应配置。

能解释 PlainHelper 两次出现结果不同的原因，并区分漏扫仓库与两个 NotificationClient 同名的失败阶段，就掌握了本课的主要诊断方法。

### 13.5 复习与后续学习

回顾你的修改路径：扫描替代了显式类型列表，配置类固定扫描边界，新增组件形成依赖关系，再通过命名、过滤与冲突处理控制本次上下文。

尝试不看答案回答：为何 `PlainHelper` 在第6步没有出现，第9步却能出现？第8步和第11步分别应从哪个阶段排查？将 Bean 改名为什么没有破坏本例唯一类型依赖？

本页代码、命令与输出沿用已经从空 `practice` 目录逐步编译运行验证的版本，本次补充理论与总结没有改变示例。仓库原有八个完整演示与十个测试继续保留，细节见 [机制与边界参考](02-机制与边界参考.md)，不要把它们当作首次学习时必须先阅读的代码清单。可在完成跟写后运行 `mvn -pl 007-component-scanning -am test`，将自己的理解与测试断言对照。

下一课：[08-01-008 XML 配置与定义继承](../008-xml-definition-inheritance/README.md)。我们会从第一份最小 XML 开始，亲手把对象装配写出来，再从重复配置中提取父定义。
