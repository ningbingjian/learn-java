# 08-01-007 组件扫描与注解注册：从手动登记一步步改成扫描

[返回模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-007) · [上一课](../006-bean-definition-model/README.md)

这一次，我们先写出一个能运行的通知器，再让它逐渐拥有仓库、业务服务和扫描边界。每次只为眼前的问题增加代码；需要过滤、命名或诊断时，再引入相应机制。

跟写代码使用独立的 `cn.ningbingjian.learnjava.ioc.lesson007.practice` 包，仍属于本课 Maven 子模块。**从第1步开始创建文件，后续按指示替换同一个文件；不要提前复制完成版所有类。** 仓库已有的 `lesson007.app` 等包是完整对照示例，不需要删除，也不会被下面的 `practice.app` 扫描范围纳入。

本页中的文件路径均相对于 `007-component-scanning`。命令在父目录 `08-01-spring-core-ioc` 执行，沿用 JDK 21 和 Maven 3.9.x。命令中的 `exec.mainClass` 指向你正在编写的入口；不传它时仍运行仓库原有演示。

学习顺序是：手动登记一个对象 → 让扫描发现它 → 组织配置入口 → 增加业务依赖 → 控制名称与扫描范围 → 排查漏扫 → 使用过滤器 → 理解重复注册和同名冲突。完整机制和边界可在学完后查阅 [参考页](02-机制与边界参考.md)。

## 1. 先亲手登记一个通知器

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

## 2. 给扫描器一个标记，再真正执行扫描

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

## 3. 把扫描边界移到配置类

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

## 4. 让通知器参与真实的对象协作

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

## 5. 需要按名字查找时，再学习名称规则

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

## 6. 验证扫描边界，不凭“类上有注解”猜测

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

## 7. 排除一个类，确认业务仍然成立

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

## 8. 故意漏扫一次，并在原位置修复

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

## 9. 只想选择两个类型，为什么还要关闭默认规则

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

## 10. 回到注册时刻，判断重复扫描做了什么

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

## 11. 用两个不同类制造真正的同名冲突

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

## 12. 在扫描之前改名，再回到源码解释分支

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

现在再进入源码，你已经有两个具体问题：第10步为什么跳过重复定义，第11步为什么抛出异常？在固定的 Spring v7.0.9 中，沿 `ClassPathBeanDefinitionScanner.doScan` 进入 `checkCandidate`，观察名字、类名、来源及已有定义。兼容时跳过登记；不兼容时进入冲突分支。不要把其中的 `return false` 简化为“没扫描到”。

源码：[扫描注册器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathBeanDefinitionScanner.java)、[候选发现器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathScanningCandidateComponentProvider.java)、[默认命名生成器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/AnnotationBeanNameGenerator.java)。

第7、9步可以继续追踪候选发现器的过滤过程：先检查排除规则，再检查包含规则和其他条件。扫描能利用类文件元数据，不需要先构造每个业务对象；这不等于所有扩展过滤器都绝不加载类。

## 13. 完成后怎样检查自己是否掌握

回顾你的修改路径：扫描替代了显式类型列表，配置类固定扫描边界，新增组件形成依赖关系，再通过命名、过滤与冲突处理控制本次上下文。

尝试不看答案回答：为何 `PlainHelper` 在第6步没有出现，第9步却能出现？第8步和第11步分别应从哪个阶段排查？将 Bean 改名为什么没有破坏本例唯一类型依赖？

本页跟写过程已从不存在的 `practice` 目录逐步编译和运行验证。仓库原有八个完整演示与十个测试继续保留，细节见 [机制与边界参考](02-机制与边界参考.md)，不要把它们当作首次学习时必须先阅读的代码清单。可在完成跟写后运行 `mvn -pl 007-component-scanning -am test`，将自己的理解与测试断言对照。

下一课：[08-01-008 XML 配置与定义继承](../008-xml-definition-inheritance/README.md)。我们会从第一份最小 XML 开始，亲手把对象装配写出来，再从重复配置中提取父定义。

