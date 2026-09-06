# 08-01-002 IoC、DI 与对象装配：从一次依赖传递开始

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-002) · [上一课](../001-why-spring-container/README.md)

上一课让入口选择通知实现，并把它交给订单服务。这一课沿用通知能力，暂时去掉库存，专门观察依赖怎样传入、业务怎样查找对象，以及谁驱动方法调用。

我们继续使用普通 Java。先写出每种关系，再给它准确的名字；Spring 容器仍在第003课引入。

本页按实际修改顺序跟写。文件路径相对于 `002-ioc-di-object-assembly`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson002.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

## 1. 沿用通知能力，留下最小业务关系

继续使用上一课已经验证的通知接口和两种输出渠道，暂时不复制库存和原订单服务，避免库存行为干扰依赖传递。

沿用第001课已经跟写完成的文件：`Notifier.java`、`EmailNotifier.java`、`SmsNotifier.java`。从 `../001-why-spring-container/src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/` 复制到本课 `src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/`，将这些文件的包声明及内部包引用中的 `lesson001` 改为 `lesson002`，其余内容先不改。这里复制的是你在上一课创建的 practice 文件；若尚未跟写，请先完成上一课对应步骤。

创建简化后的订单服务：当前假定订单已经受理成功，只负责组织受理通知。它只声明“需要一个 Notifier”，不选择具体渠道。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

import java.util.Objects;

public class OrderService {
    private final Notifier notifier;

    public OrderService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) {
        notifier.send("order=" + orderId + " accepted");
    }

    public Notifier notifier() { return notifier; }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        Notifier notifier = new EmailNotifier();
        var service = new OrderService(notifier);
        service.accept("O-002");
        System.out.println("same-reference=" + (service.notifier() == notifier));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
EMAIL order=O-002 accepted
same-reference=true
```

入口把已创建的协作者传给消费者，消费者保存这个引用。这就是本例的构造器依赖注入：DI 描述依赖进入对象的方式，不要求使用 Spring，也不要求参数类型一定是接口。

## 2. 改实现，再检查必需依赖的约束

先换短信，随后故意传 null。用这两个动作区分“可以替换具体实现”和“不能缺少这个依赖”。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        var service = new OrderService(new SmsNotifier());
        service.accept("O-002");
        try {
            new OrderService(null);
        } catch (NullPointerException error) {
            System.out.println("missing-dependency=" + error.getMessage());
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
SMS order=O-002 accepted
missing-dependency=notifier
```

缺少协作者在构造时就被拒绝，因此不能拿到一个看似正常、实际不能通知的服务。这是当前构造器约定带来的效果；构造器注入本身不会替所有类自动检查 null，检查代码仍是我们写的。

## 3. 如果依赖稍后设置，会多出什么状态

新增一个对照类，不修改正常使用的 OrderService。让它通过 setter 接收通知器，并在尚未配置时给出明确错误。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/SetterOrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

import java.util.Objects;

public class SetterOrderService {
    private Notifier notifier;

    public void setNotifier(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) {
        if (notifier == null) { throw new IllegalStateException("notifier is not configured"); }
        notifier.send("order=" + orderId + " accepted");
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        var service = new SetterOrderService();
        try {
            service.accept("O-002");
        } catch (IllegalStateException error) {
            System.out.println("before-setting=" + error.getMessage());
        }
        service.setNotifier(new EmailNotifier());
        service.accept("O-002");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
before-setting=notifier is not configured
EMAIL order=O-002 accepted
```

对象已经存在，不代表它已经具备完成业务的条件。setter 允许后续设置或替换，也把“先配置再使用”的约束交给了调用方。对于可选配置、框架约定或可变协作者，它可能有用途；对于必需且稳定的协作者，构造器更容易表达完整状态。

## 4. 字段写入也需要有人执行

再创建一个教学对照：把字段设为包可见，允许同包入口直接写入。这里没有注解扫描，也没有反射工具，直接观察外部字段赋值。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/FieldOrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class FieldOrderService {
    Notifier notifier;

    public void accept(String orderId) {
        if (notifier == null) { throw new IllegalStateException("notifier field is empty"); }
        notifier.send("order=" + orderId + " accepted");
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        var service = new FieldOrderService();
        try {
            service.accept("O-002");
        } catch (IllegalStateException error) {
            System.out.println("before-writing=" + error.getMessage());
        }
        service.notifier = new EmailNotifier();
        service.accept("O-002");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
before-writing=notifier field is empty
EMAIL order=O-002 accepted
```

字段不会自己得到值，入口确实执行了一次写入。Spring 的字段注入也是由相应处理器在适当阶段完成，不是注解具有独立执行能力；其实现留到后续注入源码系列。

这个类为了演示开放了字段，不作为生产服务的推荐封装。与构造器相比，它也允许对象在依赖缺失的状态下存在。

## 5. 注入了对象，就一定满足依赖倒置吗

新增一个对照类，它仍通过构造器接收依赖，但参数直接使用 EmailNotifier。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/EmailOnlyService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class EmailOnlyService {
    private final EmailNotifier notifier;

    public EmailOnlyService(EmailNotifier notifier) { this.notifier = notifier; }

    public void accept(String orderId) {
        notifier.send("order=" + orderId + " accepted");
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        new EmailOnlyService(new EmailNotifier()).accept("O-002");
        new OrderService(new SmsNotifier()).accept("O-002");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
EMAIL order=O-002 accepted
SMS order=O-002 accepted
```

两个对象都由外部提供依赖，因此都有 DI；但 EmailOnlyService 的源码直接依赖邮件实现，不能直接传入 SmsNotifier。OrderService 则依赖业务需要的 Notifier 契约。

DIP 讨论高层规则和实现细节之间的源码依赖方向。接口应反映业务所需能力，渠道实现遵守这个能力契约。并非给任何具体类套一个同名接口，就自然获得了合理的依赖倒置；这里通过实际替换渠道来检验边界。

## 6. 让业务主动查找渠道，再与注入比较

现在模拟一个可以更换当前渠道的定位器。先写保存和查询渠道的对象，保持普通 Java，不引入全局静态表。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/NotifierLocator.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

import java.util.Objects;

public class NotifierLocator {
    private Notifier current;

    public NotifierLocator(Notifier current) { replace(current); }
    public void replace(Notifier notifier) { current = Objects.requireNonNull(notifier); }
    public Notifier getNotifier() { return current; }
}
```

新增查找型服务，让每次业务调用都向定位器取通知器。定位器本身通过构造器传入，但具体通知器是在业务执行时主动查找。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/LookupOrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class LookupOrderService {
    private final NotifierLocator locator;

    public LookupOrderService(NotifierLocator locator) { this.locator = locator; }

    public void accept(String orderId) {
        locator.getNotifier().send("order=" + orderId + " accepted");
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

public class Main {
    public static void main(String[] args) {
        var locator = new NotifierLocator(new EmailNotifier());
        var injected = new OrderService(locator.getNotifier());
        var lookup = new LookupOrderService(locator);
        locator.replace(new SmsNotifier());
        System.out.println("injected:");
        injected.accept("O-002");
        System.out.println("lookup:");
        lookup.accept("O-002");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
injected:
EMAIL order=O-002 accepted
lookup:
SMS order=O-002 accepted
```

已注入服务保留原先的邮件对象引用，定位器更换当前对象没有重写那个字段。查找型服务在调用时重新取值，所以使用短信。

这分别体现 DI 和 Service Locator 的依赖获取方式。一个对象可以注入定位器，再通过它定位另一个对象；判断时要说明谈的是哪一层依赖。若查找型服务在构造时查询并缓存通知器，替换后的行为又会不同，关键是查找时机。

## 7. 控制反转还包括谁来驱动业务

前面的 main 直接调用服务。接下来写一个很小的分发器：业务只提供回调，分发器遍历订单并调用它。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/OrderDispatcher.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

import java.util.List;
import java.util.function.Consumer;

public class OrderDispatcher {
    private final Consumer<String> handler;

    public OrderDispatcher(Consumer<String> handler) { this.handler = handler; }

    public void dispatch(List<String> orderIds) {
        for (String orderId : orderIds) { handler.accept(orderId); }
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson002.practice;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        var service = new OrderService(new EmailNotifier());
        var dispatcher = new OrderDispatcher(service::accept);
        dispatcher.dispatch(List.of("O-002", "O-003"));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 002-ioc-di-object-assembly compile
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.practice.Main
```

```text
EMAIL order=O-002 accepted
EMAIL order=O-003 accepted
```

main 发起分发，但逐项调用业务回调的控制权交给了分发器。这是解释 IoC 的另一个角度，不涉及 Bean 注册表。IoC 的范围比 DI 更广；DI 是把对象装配控制交给外部的一种方式。

| 你刚才做过的动作 | 主要对应的问题 |
| --- | --- |
| 构造器、setter 或外部字段赋值 | 协作者怎样进入对象：DI |
| 业务依赖 Notifier，渠道实现该契约 | 源码依赖怎样安排：DIP |
| 每次调用 locator.getNotifier | 业务如何主动查找协作者：Service Locator |
| 分发器调用已经登记的回调 | 执行流程由谁驱动：IoC 的一个例子 |

这些概念并不互斥，也不是四个不同框架。要结合具体对象、引用和控制权讨论。

## 8. 用下一课检验这些概念

我们仍然只写了普通 Java 对象；JavaBean 的属性约定、普通对象和被 Spring 管理的 Bean 也不是同一个概念。一个类不必实现特定 Spring 基类才能成为 Bean，关键是实例是否通过容器的管理路径产生或接入。

下一课只沿用正常的 Notifier、EmailNotifier 和构造器版 OrderService；setter、字段、定位器和分发器保留作对照，不把所有变体一起塞进容器。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-003](../003-runnable-debuggable-spring/README.md)。

