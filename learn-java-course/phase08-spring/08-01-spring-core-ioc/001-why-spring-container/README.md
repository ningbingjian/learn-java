# 08-01-001 为什么需要 Spring 容器：先把对象组装的问题做出来

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-001)

这一课先不用 Spring。我们要写一个只有一件库存的订单程序，逐步加入两个下单入口，亲眼发现对象组装错误怎样影响业务，再把创建与共享关系集中起来。

只需要类、构造器、字段和方法调用；Maven 已是前置学习内容，当前子模块只依赖 JUnit 测试库，没有 Spring 运行依赖。这里的通知只是控制台输出，库存是单进程内存整数，所有下单顺序执行。

本页按实际修改顺序跟写。文件路径相对于 `001-why-spring-container`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson001.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

## 1. 先实现一份能扣减的库存

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

## 2. 加入订单服务，先让它自己创建依赖

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

## 3. 增加第二个入口，观察错误出现

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

## 4. 把库存交给构造器，让入口决定共享关系

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

## 5. 渠道需要变化，再提取通知能力

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

## 6. 亲手换一次实现，检验业务是否被隔离

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

## 7. 已经能手动装配，容器接下来承接什么

你刚才已经在 main 中承担了组装者的工作：创建对象、选择实现、传递依赖、决定哪些实例共享。对象继续增多时，这些关系会增加，后续还可能需要初始化、资源关闭和扩展处理。

Spring 容器可以根据配置完成这些工作，但它不会猜出业务上应该共享哪份库存，也不会自动把错误的配置变正确。这里先建立对象模型，第002课解释相关概念，第003课再把同一类关系交给实际容器。

也不需要把所有对象都交给容器。长期服务、仓库、客户端适合统一组装；订单请求数据、某次计算的临时集合，通常仍按业务需要创建。库存示例也不是生产级库存扣减方案：没有数据库事务、并发控制和分布式一致性保证。

## 8. 带着修改过程检查理解

先把初始库存改成2，再追加第三次下单，预测输出；然后故意给两个服务传不同库存，解释为何结果变化。最后检查换短信时有没有改动 OrderService。

下一课沿用刚写的 Notifier、EmailNotifier 和 SmsNotifier，把库存暂时从演示中拿掉，集中研究“业务对象如何得到协作者”。这不是换一个无关项目，而是从已经成功装配的关系中单独观察一个问题。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-002](../002-ioc-di-object-assembly/README.md)。

