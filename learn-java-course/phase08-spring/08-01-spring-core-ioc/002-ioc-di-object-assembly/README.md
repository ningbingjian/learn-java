# 08-01-002 IoC、DI 与对象装配：从一次依赖传递开始

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-002) · [上一课](../001-why-spring-container/README.md)

上一课让入口选择通知实现，并把它交给订单服务。这一课沿用通知能力，暂时去掉库存，专门观察依赖怎样传入、业务怎样查找对象，以及谁驱动方法调用。

我们继续使用普通 Java。先说明每种关系解决什么问题，再用具体对象与调用过程验证，区分 IoC、DI、DIP 和服务定位器；Spring 容器在第003课引入。

本页按实际修改顺序跟写。文件路径相对于 `002-ioc-di-object-assembly`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson002.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 沿用通知能力，留下最小业务关系

### 1.1 理论：依赖注入描述协作者怎样进入对象

第001课已经把通知器从服务内部移到构造器参数。本节给这件事准确的名字：依赖注入（Dependency Injection，DI），即由外部把对象需要的协作者提供给它。

这里有三个角色：OrderService 使用通知能力，EmailNotifier 提供能力，Main 负责把二者组装起来。服务只声明所需类型并保存引用，不负责挑选邮件实现。普通 Java 构造器调用就能完成这个过程，不需要先有框架。

是否使用接口是另一层设计问题。参数可以是接口，也可以是具体类；判断 DI 时先问依赖由谁提供。

### 1.2 实操：逐步实现并解释原理

复制已经学过的通知接口和实现后，重点看两个位置：Main 把对象传进构造器，构造器把引用保存到字段。accept 之后直接使用这个字段，不再重新寻找通知器。

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

### 1.3 小总结

DI 讨论依赖的提供方式：外部提供，消费者使用。Spring 可以执行注入，但 DI 本身不依赖 Spring。

<a id="topic-2"></a>

## 2. 改实现，再检查必需依赖的约束

### 2.1 理论：可替换依赖与不可缺失依赖

可替换表示服务接受满足契约的不同实现；不可缺失表示任何合法的服务对象都必须有协作者。两者可以同时成立：可以选邮件或短信，但不能没有通知器。

构造器让必需依赖出现在创建接口中；其中的非空检查进一步保证 null 会立即被拒绝。否则 Java 完全允许把 null 传给引用类型参数，错误可能拖到调用 send 才暴露。

### 2.2 实操：逐步实现并解释原理

先正常替换实现，确认契约仍成立，再构造一个缺少协作者的服务。对照错误是否在 new OrderService 时出现，而不是等到 accept 执行。

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

### 2.3 小总结

构造器注入把要求显式写进创建接口；输入校验决定要求能否被强制执行。可替换不等于可缺失。

<a id="topic-3"></a>

## 3. 如果依赖稍后设置，会多出什么状态

### 3.1 理论：setter 注入引入配置前后的状态

通过 setter 提供依赖也是 DI，但对象的创建与配置分成两次调用。服务会经历“已构造但未配置”以及“已配置可使用”两种状态，调用方必须遵守先后顺序。

构造器适合表达创建时必须具备的稳定协作者。setter 可以表达后续设置或替换，但类要处理未设置、重复设置等情况。本节的服务在 accept 开始时检查状态，将不完整对象的误用转成明确错误。

### 3.2 实操：逐步实现并解释原理

先创建 SetterOrderService 后直接使用，验证配置前状态，再设置通知器并调用。同一个对象前后行为变化，来自字段被赋值，而不是服务被重新构造。

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

### 3.3 小总结

setter 提供后续配置能力，也要求明确配置完成前能否使用。对象已经存在与业务已经可用，需要分别判断。

<a id="topic-4"></a>

## 4. 字段写入也需要有人执行

### 4.1 理论：字段注入仍需要执行赋值的主体

实例字段的默认 null 不会因为我们希望注入就自行变成对象。直接字段赋值也是从外部提供协作者，但它依赖访问权限，并绕过构造器或 setter 中本可集中的校验入口。

本节使用同包代码写入包可见字段，先观察最普通的 Java 赋值。框架字段注入会通过相应处理器完成访问和赋值，注解只提供元信息；其完整实现将在后续专题讲解。

### 4.2 实操：逐步实现并解释原理

注意 FieldOrderService 没有接收协作者的构造器。真正改变字段的是 Main 的赋值语句；在这之前调用业务方法，仍然面对缺失依赖。

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

### 4.3 小总结

字段注入有实际执行者，也有赋值时机。选择注入方式时，要考虑依赖能否显式表达以及状态约束放在哪里。

<a id="topic-5"></a>

## 5. 注入了对象，就一定满足依赖倒置吗

### 5.1 理论：DI 与依赖倒置解决不同问题

依赖倒置原则（Dependency Inversion Principle，DIP）关注源码依赖方向：高层业务规则通过所需能力的抽象与实现细节协作，具体实现也遵守这个抽象。

OrderService 的源码只需要 Notifier 契约；EmailNotifier、SmsNotifier 实现该契约。业务不再需要了解邮件类的细节，入口仍知道具体实现，因为总要有一处选择和组装它。

EmailOnlyService 即使也从构造器接收对象，它的参数仍是 EmailNotifier，源码仍依赖邮件实现。因此它有 DI，却没有像接口版那样隔离渠道细节。不能用“有注入”直接证明依赖方向设计合理。

### 5.2 实操：逐步实现并解释原理

先看两个构造器的参数类型，再看 Main 能传入哪些实现。尝试在脑中把 EmailOnlyService 的参数换成 SmsNotifier：这里首先遇到的是 Java 类型不匹配，不是容器配置问题。

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

### 5.3 小总结

DI 看协作者如何进入对象，DIP 看业务源码依赖什么。两者可以配合，但需要分别判断。

<a id="topic-6"></a>

## 6. 让业务主动查找渠道，再与注入比较

### 6.1 理论：主动查找依赖取决于查找时机

服务定位器（Service Locator）提供一个查找协作者的入口。使用者持有定位器，并在需要时调用它获得对象；与直接注入协作者相比，业务代码多了一次主动查找。

下面有两层依赖：LookupOrderService 的定位器由构造器注入，而通知器由业务方法每次主动查询。说它“用了 DI”或“用了定位器”都需要明确指哪一层。

定位器替换 current 字段，只改变今后的查询结果。已经注入其他服务的邮件引用不会因此重写。只有重新查找的路径才会看到新短信对象；本节没有代理，也没有自动刷新所有字段的机制。

### 6.2 实操：逐步实现并解释原理

创建直接注入版与查找版服务后，再替换定位器中的对象。分别追踪 accept 读取的是原先保存的 notifier，还是刚调用 getNotifier 得到的结果，再预测两个渠道。

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

### 6.3 小总结

注入保存引用，定位器提供查找。动态变化能否被看见，取决于实际取值时机，不能只看有无定位器。

<a id="topic-7"></a>

## 7. 控制反转还包括谁来驱动业务

### 7.1 理论：控制反转也体现在回调的驱动者

控制反转（Inversion of Control，IoC）要说明哪项控制权发生变化。在对象装配中，业务把协作者的创建选择交给外部；在执行流程中，业务还可以把何时被调用交给一个调度组件。

本节分发器持有 Consumer<String> 回调。业务提供“收到订单号后做什么”，分发器负责遍历列表并调用。Main 仍负责发起整个程序，但不再逐条直接调用服务。

这说明 IoC 的范围比 DI 广。回调驱动可用于解释执行控制，构造器注入可用于解释装配控制，二者不需要成为互斥的分类。

### 7.2 实操：逐步实现并解释原理

看 Main 如何把服务方法交给分发器，再进入 dispatch 的循环：每次 handler.accept 才真正驱动业务执行。这里 Consumer.accept 与服务的 accept 分属不同对象，通过回调连接。

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

### 7.3 小总结

讨论 IoC 时先指出谁原来做决定、现在谁做决定。DI 是外部装配的一种实现方式，回调则展示了执行流程的控制变化。

<a id="course-summary"></a>

## 8. 本课总结

### 8.1 本课解决的问题

本课把“对象由外部提供”拆成依赖获取、源码依赖方向和执行控制三个问题，避免把 IoC、DI、DIP 和服务定位器混成一组同义词。

### 8.2 把原理串起来

外部通过构造器、setter 或字段赋值把协作者交给对象，这是 DI。具体选择哪个入口，会影响对象能否以未配置状态存在。构造器表达要求，非空检查等代码进一步落实约束。

消费者依赖业务契约、渠道实现遵守契约，讨论的是 DIP。消费者主动向定位器取协作者，讨论的是查找方式；换掉定位器里的引用不会改写已经注入的引用。把逐条执行交给分发器、业务只提供回调，则展示了执行控制反转。

### 8.3 核心结论与边界

| 概念 | 本课判断方法 |
| --- | --- |
| DI | 谁把哪一个协作者提供给消费者？ |
| DIP | 高层业务源码依赖能力契约，还是渠道细节？ |
| Service Locator | 业务何时主动查询，查询结果是否被缓存？ |
| IoC | 哪项决定由业务自身转交给外部组件？ |

这些关系可以同时存在。定位器可以被注入，注入的参数也可以是具体类；不能用某一标签替代对实际关系的分析。

### 8.4 如何应用与检查理解

设计服务时先声明稳定且必需的依赖，再考虑配置完成时点。需要运行时选择时，明确查找发生在哪次调用，避免误以为已有字段会自动更新。

能解释“EmailOnlyService 有 DI，但渠道替换仍受限制”，以及定位器切换后两种服务为何选择不同渠道，就掌握了本课最关键的区别。

### 8.5 复习与后续学习

我们仍然只写了普通 Java 对象；JavaBean 的属性约定、普通对象和被 Spring 管理的 Bean 也不是同一个概念。一个类不必实现特定 Spring 基类才能成为 Bean，关键是实例是否通过容器的管理路径产生或接入。

下一课只沿用正常的 Notifier、EmailNotifier 和构造器版 OrderService；setter、字段、定位器和分发器保留作对照，不把所有变体一起塞进容器。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-003](../003-runnable-debuggable-spring/README.md)。
