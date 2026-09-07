# 08-01-001 为什么需要 Spring 容器：先把对象组装的问题做出来

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-001)

这一课先不用 Spring。我们要写一个只有一件库存的订单程序，逐步加入两个下单入口，亲眼发现对象组装错误怎样影响业务，再把创建与共享关系集中起来。

只需要类、构造器、字段和方法调用；Maven 已是前置学习内容，当前子模块只依赖 JUnit 测试库，没有 Spring 运行依赖。这里的通知只是控制台输出，库存是单进程内存整数，所有下单顺序执行。

本页按实际修改顺序跟写。文件路径相对于 `001-why-spring-container`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson001.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 先实现一份能扣减的库存

### 1.1 理论：对象的状态为什么属于实例

订单能否受理，先取决于还有没有库存。本节用一个实例字段 remaining 保存剩余数量，让一次扣减的结果影响下一次调用。

类描述结构和行为，实例保存某一次创建得到的具体状态。每次执行 new Inventory(1)，都会得到一份初值为1的独立状态；把已有库存引用赋给另一个变量，则不会复制库存。后面判断是否共享，依据的是引用指向哪个实例。

reserveOne 要维护一个简单约定：数量为零就拒绝，有库存才扣减并返回成功。当前只有顺序调用，先把这个业务规则验证清楚，再研究多个服务如何使用它。

### 1.2 实操：逐步实现并解释原理

写 Inventory 时，先看构造器如何拒绝负数，再看 reserveOne 为什么必须先判断后扣减。入口要对同一个变量连续调用两次；如果每次都 new，验证的就不是同一份库存的变化。

现在只有一种商品，先用整数表示剩余数量。`reserveOne` 成功时扣一件并返回 true，不足时返回 false。我们不同时加入订单类、容器或通知策略，先验证这一条规则。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Inventory.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Inventory {
    private int remaining;

    public Inventory(int remaining) {
        if (remaining < 0) { throw new IllegalArgumentException("remaining must be >= 0"); }
        this.remaining = remaining;
    }

    public boolean reserveOne() {
        if (remaining == 0) { return false; }
        remaining--;
        return true;
    }

    public int remaining() { return remaining; }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var inventory = new Inventory(1);
        System.out.println("first=" + inventory.reserveOne());
        System.out.println("second=" + inventory.reserveOne());
        System.out.println("remaining=" + inventory.remaining());
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
first=true
second=false
remaining=0
```

第二次失败，说明同一个库存对象能够记住第一次扣减后的状态。先确认这一点，后面才能判断异常来自库存逻辑还是对象关系。

### 1.3 小总结

实例字段随对象保留状态。同一个类可以有多个独立实例；判断是否共享库存，应追踪引用，而不是只看类型名称。

<a id="topic-2"></a>

## 2. 加入订单服务，先让它自己创建依赖

### 2.1 理论：业务调用与依赖创建是两项决定

订单服务负责组织“预留库存，成功后通知”的业务顺序。它需要调用 Inventory，这种完成工作时需要的协作者称为依赖。

如果服务在字段中直接 new 库存，就同时决定了业务流程和依赖的创建方式。每次创建服务，它都生成自己的一份库存。单个服务可能运行正确，但外部无法要求两个服务复用同一份库存；这个限制来自创建位置，而不是 reserveOne 算法。

### 2.2 实操：逐步实现并解释原理

下面先保留这种写法作对照。读 OrderService 时注意两处：字段初始化负责创建库存；accept 内的提前返回保证预留失败时不会继续通知。

订单受理需要先扣库存，成功后输出通知。先按最直接的方式写：服务字段内部创建库存。请留意这行 new，后面会回到它。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class OrderService {
    private final Inventory inventory = new Inventory(1);

    public boolean accept(String orderId) {
        if (!inventory.reserveOne()) { return false; }
        System.out.println("EMAIL order=" + orderId + " accepted");
        return true;
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var webOrders = new OrderService();
        System.out.println("web=" + webOrders.accept("O-001"));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
EMAIL order=O-001 accepted
web=true
```

单个入口看起来没有问题。下一项需求是合作方也能下单，而且应扣减同一件库存。这才是检查对象共享关系的时机。

### 2.3 小总结

内部 new 让依赖来源固定在服务中。业务方法正确，并不能证明多个服务之间的共享关系也符合业务要求。

<a id="topic-3"></a>

## 3. 增加第二个入口，观察错误出现

### 3.1 理论：两个入口为什么可能得到两份库存

网页与合作方是两个下单入口，但业务要求它们消费同一份库存。服务实例的数量与库存实例的数量不必相同：两个服务完全可以引用一个库存对象。

当前写法却把两者绑定起来。创建第一个 OrderService 时执行一次库存字段初始化，创建第二个时再执行一次；于是每个入口各自看到一件库存。即使两个订单顺序执行，也都可能成功。

所以本节要观察的是对象关系：网页服务指向谁，合作方服务指向谁。先推演每个 new 会产生什么，再运行第二个入口。

### 3.2 实操：逐步实现并解释原理

Main 只增加一个服务和一次调用，不改库存算法。这样结果如果偏离“第二单应该失败”的业务要求，就能将原因收敛到新增的对象关系。

只改入口，为网页和合作方分别创建服务，并连续下单。库存类和服务类都先保持原样。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var webOrders = new OrderService();
        var partnerOrders = new OrderService();
        System.out.println("web=" + webOrders.accept("O-001"));
        System.out.println("partner=" + partnerOrders.accept("O-002"));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
EMAIL order=O-001 accepted
web=true
EMAIL order=O-002 accepted
partner=true
```

两个订单都成功了。库存算法没有失忆：每次 new OrderService 都执行自己的字段初始化，因此创建了两份各有一件商品的库存。业务需要共享状态，当前对象关系却把状态拆开了。

这不是并发问题；当前是两次顺序调用。也不能因为类名都叫 Inventory 就认为它们是同一个对象。我们要改的是依赖从哪里来。

### 3.3 小总结

共享需求必须落实为共享引用。两个独立库存各自正确地扣减，也可能组合出错误的整体业务结果。

<a id="topic-4"></a>

## 4. 把库存交给构造器，让入口决定共享关系

### 4.1 理论：通过构造器把装配决定交给外部

要让外部决定共享关系，服务就应声明“我需要 Inventory”，而把具体实例由谁创建留给入口。构造器参数正好可以表达这个要求。

Java 传递对象参数时，传递的是引用的值。两个构造器收到同一个库存引用后，两个服务字段指向同一实例；调用其中任意一个服务，修改的都是这份库存。

final 保证字段初始化后不能重新指向另一库存，不会让库存对象本身不可修改。Objects.requireNonNull 则拒绝缺失依赖；这是当前类主动建立的约束。构造器提供了注入入口，正确共享仍取决于装配代码传了什么。

### 4.2 实操：逐步实现并解释原理

先把 OrderService 的内部创建改为接收参数，再在 Main 中只 new 一次 Inventory。检查两个服务构造器使用的是同一个变量；随后预测第一次扣减后第二个服务能看到的 remaining。

先改服务：删去内部 new，新增接收库存的构造器。库存是必需依赖，在构造时检查非 null，使错误尽早暴露。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

import java.util.Objects;

public class OrderService {
    private final Inventory inventory;

    public OrderService(Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public boolean accept(String orderId) {
        if (!inventory.reserveOne()) { return false; }
        System.out.println("EMAIL order=" + orderId + " accepted");
        return true;
    }
}
```

服务不再决定库存实例来自哪里。入口创建一份库存，再把同一个引用传给两个服务。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var inventory = new Inventory(1);
        var webOrders = new OrderService(inventory);
        var partnerOrders = new OrderService(inventory);
        System.out.println("web=" + webOrders.accept("O-001"));
        System.out.println("partner=" + partnerOrders.accept("O-002"));
        System.out.println("remaining=" + inventory.remaining());
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
EMAIL order=O-001 accepted
web=true
partner=false
remaining=0
```

第二单失败，而且没有发送第二条通知。现在两个服务持有同一个库存引用，状态才真正共享。

如果入口仍分别传入两个 new Inventory，问题还会出现。因此“改成构造器参数”只提供了正确组装的能力，具体共享关系仍需由组装者负责。

### 4.3 小总结

构造器声明依赖，外部入口创建并传入对象。它让共享关系可配置，但不会自动保证装配者传入的是同一实例。

<a id="topic-5"></a>

## 5. 渠道需要变化，再提取通知能力

### 5.1 理论：用业务契约隔离通知实现

订单受理需要“发送受理消息”这项能力，邮件还是短信则是渠道细节。如果把渠道实现写进受理流程，切换渠道就必须修改本来没有变化的业务规则。

Notifier 接口描述订单服务需要的能力。服务通过接口调用 send，运行时由实际对象执行邮件或短信实现。接口负责约束可调用的方法，入口负责选择实现，两者共同形成可替换的边界。

这里保留订单消息的组织工作在 OrderService 中，让渠道只负责发送收到的消息。否则把业务文案搬到渠道里，可能使切换渠道同时改变订单规则。

### 5.2 实操：逐步实现并解释原理

先写最小 send 契约，再移动已有邮件输出。替换 OrderService 时保留“库存失败立即返回”的位置，只把通知动作交给注入的 Notifier。

下一项需求是用短信代替邮件。若继续把 EMAIL 输出写在业务方法里，就必须修改订单逻辑。先提取业务需要的最小契约：发送一条消息。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public interface Notifier {
    void send(String message);
}
```

再把现有邮件输出移到具体实现中。此时没有改变订单流程，只改变通知实现的放置位置。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/EmailNotifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class EmailNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("EMAIL " + message);
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

import java.util.Objects;

public class OrderService {
    private final Inventory inventory;
    private final Notifier notifier;

    public OrderService(Inventory inventory, Notifier notifier) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public boolean accept(String orderId) {
        if (!inventory.reserveOne()) { return false; }
        notifier.send("order=" + orderId + " accepted");
        return true;
    }
}
```

最后由入口选邮件实现，并保持库存共享方式。这里发生的是两个不同决定：库存要共享，通知能力要选实现。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var inventory = new Inventory(1);
        Notifier notifier = new EmailNotifier();
        var webOrders = new OrderService(inventory, notifier);
        var partnerOrders = new OrderService(inventory, notifier);
        System.out.println("web=" + webOrders.accept("O-001"));
        System.out.println("partner=" + partnerOrders.accept("O-002"));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
EMAIL order=O-001 accepted
web=true
partner=false
```

### 5.3 小总结

接口表达业务所需能力，具体类实现渠道细节。抽象是否有效，要看切换实现时业务规则能否保持稳定。

<a id="topic-6"></a>

## 6. 亲手换一次实现，检验业务是否被隔离

### 6.1 理论：用一次替换验证接口边界

上一节的接口设计需要通过真实替换检验。SmsNotifier 只要履行同一个 send 契约，就能作为构造器参数传给原服务；Java 的接口多态会将调用分派到短信实现。

替换渠道不应改变库存实例的共享，也不应改变失败不通知的规则。它们属于不同的设计决定。下面用相同订单顺序检验这些条件，而不仅检查控制台前缀是否变成 SMS。

### 6.2 实操：逐步实现并解释原理

新增实现后，只修改 Main 的渠道选择。对照业务服务文件不需要修改，并留意第二次受理失败后仍没有额外通知。

新增短信实现，接口和订单服务均不改。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/SmsNotifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class SmsNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("SMS " + message);
    }
}
```

入口只把渠道选择改成 SmsNotifier，两个服务仍共用同一库存。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson001/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson001.practice;

public class Main {
    public static void main(String[] args) {
        var inventory = new Inventory(1);
        Notifier notifier = new SmsNotifier();
        var webOrders = new OrderService(inventory, notifier);
        var partnerOrders = new OrderService(inventory, notifier);
        System.out.println("web=" + webOrders.accept("O-001"));
        System.out.println("partner=" + partnerOrders.accept("O-002"));
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 001-why-spring-container compile
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.practice.Main
```

```text
SMS order=O-001 accepted
web=true
partner=false
```

现在通知渠道改变，库存规则与“失败不通知”的顺序保持不变。接口有没有帮助，应从这次修改涉及哪些文件来判断，而不是只看项目里是否定义了 interface。

### 6.3 小总结

一次成功的替换应同时证明新实现生效、原业务约定保持。替换能力来自稳定契约与外部装配的配合。

<a id="topic-7"></a>

## 7. 已经能手动装配，容器接下来承接什么

### 7.1 理论：容器承接的是对象组装与管理

回看现在的 Main，它知道要创建哪些对象、选哪个实现、如何传入依赖、哪些实例复用。集中表达这些决定的位置通常称为装配入口。

对象增多后，装配还可能包含初始化、关闭以及配置选择。容器根据我们提供的规则执行这些管理工作，减轻各个业务类自行创建协作者的负担。它依然需要明确的配置，不会从两个下单入口自动推断库存应当共享。

本课先把可交给容器的对象关系建立好。长期协作的服务和客户端可以统一管理；每次请求的输入数据和临时计算对象，仍可按业务需要创建。

### 7.2 实操：逐步实现并解释原理

打开当前 Main，逐一找出创建库存、选择渠道、创建服务的语句。再看 accept：它只处理库存判断和通知。用这两处代码区分装配职责与业务职责。

你刚才已经在 main 中承担了组装者的工作：创建对象、选择实现、传递依赖、决定哪些实例共享。对象继续增多时，这些关系会增加，后续还可能需要初始化、资源关闭和扩展处理。

Spring 容器可以根据配置完成这些工作，但它不会猜出业务上应该共享哪份库存，也不会自动把错误的配置变正确。这里先建立对象模型，第002课解释相关概念，第003课再把同一类关系交给实际容器。

也不需要把所有对象都交给容器。长期服务、仓库、客户端适合统一组装；订单请求数据、某次计算的临时集合，通常仍按业务需要创建。库存示例也不是生产级库存扣减方案：没有数据库事务、并发控制和分布式一致性保证。

### 7.3 小总结

使用容器前先设计正确的对象关系。容器执行装配规则，业务仍负责自己的状态、流程和正确性约束。

<a id="course-summary"></a>

## 8. 本课总结

### 8.1 本课解决的问题

本课解决的是对象装配怎样影响业务正确性。库存算法可以正确，两个服务却可能因为分别创建库存而受理了不该成功的第二单。我们把共享关系移到入口，再通过通知接口隔离渠道变化。

### 8.2 把原理串起来

类描述行为，实例保存状态。服务内部每执行一次 new Inventory，就增加一份独立库存；外部只创建一次库存并把同一引用传给两个构造器，才真正建立共享。构造器声明服务需要什么，入口决定提供哪一个对象。

通知接口把订单需要的能力固定下来，邮件与短信分别实现它。订单服务继续决定何时通知，入口决定选哪种渠道。这样业务流程、实现选择和实例共享成为可以分别理解和修改的决定。

### 8.3 核心结论与边界

| 本课结论 | 使用时的边界 |
| --- | --- |
| 同类型不等于同实例，共享要落实为共享引用 | final 固定字段引用，不会让库存状态不可修改 |
| 构造器让必需依赖显式化 | 是否传入同一实例，仍由装配代码决定 |
| 接口可以隔离业务与渠道实现 | 接口要表达业务所需能力，不能只为增加类型层数 |
| 容器可以承接装配与管理 | 它不会自动推断业务共享关系或修正错误规则 |

本例是单进程顺序调用的内存模型，不能代替生产库存的并发、事务和分布式一致性设计。

### 8.4 如何应用与检查理解

遇到多个入口行为不一致，先画清或逐项列出服务持有哪些实例，再决定该共享还是隔离。切换实现时，检查修改是否局限于实现和装配位置，以及原业务规则是否仍成立。

不用 Spring，解释当前第二单为什么失败、切换短信为什么不改 OrderService，就能证明你已经理解容器要承接的基础工作。

### 8.5 复习与后续学习

先把初始库存改成2，再追加第三次下单，预测输出；然后故意给两个服务传不同库存，解释为何结果变化。最后检查换短信时有没有改动 OrderService。

下一课沿用刚写的 Notifier、EmailNotifier 和 SmsNotifier，把库存暂时从演示中拿掉，集中研究“业务对象如何得到协作者”。这不是换一个无关项目，而是从已经成功装配的关系中单独观察一个问题。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-002](../002-ioc-di-object-assembly/README.md)。
