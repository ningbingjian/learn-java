# 08-01-008 XML 配置与定义继承：从第一份配置逐步提取模板

[返回模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-008) · [上一课](../007-component-scanning/README.md)

第007课用扫描发现对象。这一课换成 XML，但仍然先从一个通知器开始：亲手写第一份配置，再依次加入参数、对象引用、集合和生命周期。等第二个通知器真正产生重复配置后，我们才提取父定义。

跟写文件使用独立的 `cn.ningbingjian.learnjava.ioc.lesson008.practice` 包，仍放在本课 Maven 子模块。**先按第1步创建文件，后面逐步替换；不要一开始把完整示例的所有类复制过来。** 仓库原有 `lesson008` 业务类和 XML 保留作完成后的对照，跟写使用单独的 `practice008/beans.xml`，两组资源不混合加载。

本页文件路径相对于 `008-xml-definition-inheritance`；所有 Maven 命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。`exec.mainClass` 选择正在跟写的入口，不传该参数仍运行原有完整演示。

先完成第1—9步的 XML 装配与模板主线，再继续工厂方法、Java 配置迁移和容器层级。每一步的完整文件内容便于核对当前位置；只在指示的文件上修改，其他文件保留上一步状态。深入边界与完整对照见 [机制参考](02-机制与边界参考.md)。

## 1. 先让 XML 管理一个没有依赖的对象

第一件事只是“给一个订单输出通知”。创建 `Notifier.java`，现在不需要渠道配置、日志依赖或 Spring 注解。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class Notifier {
    public void send(String orderId) {
        System.out.println("email order=" + orderId);
    }
}
```

接着创建第一份 XML。`id` 给对象定义一个容器内名称，`class` 告诉容器目标 Java 类型。先只写这一个 Bean，暂时不引入构造参数、集合或模板。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
    </bean>
</beans>
```

最后创建入口：先用 XML 读取器登记定义，再刷新上下文，随后按名字取对象并调用。try-with-resources 在退出时关闭上下文。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:practice008/beans.xml");
            context.refresh();
            context.getBean("notifier", Notifier.class).send("O-008");
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
email order=O-008
```

这一步没有组件扫描。通知器能进入容器，是因为 XML 明确声明了它；`XmlBeanDefinitionReader` 解释配置，`refresh()` 完成当前上下文的启动。

文件应放在 `src/main/resources` 下。命令带有 `compile`，会把最新资源复制到 `target/classes`；若改了 XML 却只执行旧的运行命令，应先确认新资源是否进入类路径。标准 beans XSD 在当前 Spring 依赖中有解析映射，当前示例不需要为了读取它访问远程服务器。

## 2. 希望切换渠道，于是增加构造参数

目前 `email` 写死在 `send` 内。现在把“选择渠道”移到配置中：先改 Java 类，让渠道成为构造器必须提供的数据。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class Notifier {
    private final String channel;

    public Notifier(String channel) {
        this.channel = channel;
    }

    public void send(String orderId) {
        System.out.println(channel + " order=" + orderId);
    }
}
```

Java 构造器已经改变，XML 也必须补上参数。`index="0"` 指向第0个参数；`value` 提供字符串字面值。入口暂时不用改。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
email order=O-008
```

输出与上一步相同，但渠道的来源变了。你现在可以只改 XML 的 `value="email"` 来选择渠道，不必修改业务方法。

如果只改构造器、遗漏 XML，容器无法继续按原来的无参方式创建对象。这里先明确创建要求，再写配置去满足它。字符串也并不总能直接满足任意类型：数字、枚举等参数还涉及类型转换和构造器匹配。

## 3. 希望保留通知记录，于是引入对象引用

控制台输出不便于检查后续行为。先创建一个内存记录器，给它记录和读取消息的能力。这时只需要保存通知记录；生命周期事件等需要时再加入。它是单线程学习探针，不连接外部日志系统。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/DeliveryLog.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.ArrayList;
import java.util.List;

public class DeliveryLog {
    private final List<String> deliveries = new ArrayList<>();

    public void record(String message) { deliveries.add(message); }
    public List<String> deliveries() { return List.copyOf(deliveries); }
}
```

接着让通知器通过构造器接收这个对象。`send` 的职责保持不变，只把输出写入记录器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class Notifier {
    private final String channel;
    private final DeliveryLog log;

    public Notifier(String channel, DeliveryLog log) {
        this.channel = channel;
        this.log = log;
    }

    public void send(String orderId) {
        log.record(channel + " order=" + orderId);
    }
}
```

现在 XML 中要先有记录器的定义，再通过 `ref` 声明依赖关系。元素先后顺序不是本例引用能否解析的决定条件；读取时保存定义，创建时才解析引用。这里把依赖放前面，方便阅读。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
    </bean>
</beans>
```

因为 `send` 不再直接打印，入口也相应改为打印记录器的内容。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:practice008/beans.xml");
            context.refresh();
            context.getBean("notifier", Notifier.class).send("O-008");
            System.out.println(context.getBean(DeliveryLog.class).deliveries());
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
[email order=O-008]
```

`value="deliveryLog"` 表达的是名字字符串，`ref="deliveryLog"` 表达的是命名对象引用。当前构造器需要 `DeliveryLog` 对象，因此必须传对象，不能把字符串当成对象替代。

### 3.1 立刻改错一次引用，再修复

保持 Java 代码不动，只把通知器引用改成不存在的 `missingLog`。下面是这次故意写错的 XML。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="missingLog"/>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
No bean named 'missingLog' available
```

这条命令应失败，输出只截取稳定的根因片段。XML 的格式可以正确，定义也可以读入，但刷新期间创建通知器时找不到目标引用。

不要换一个注解或反复调整 XML 缩进，应该核对引用名字以及目标定义是否真的加载。现在恢复引用，再运行确认，之后才进入下一步。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
[email order=O-008]
```

## 4. 增加一个可配置前缀，学习属性填充

渠道和日志仍由构造器保证。前缀则提供一个默认空值，再通过 setter 配置。先在通知器中新增 `prefix` 字段与 `setPrefix`，并在消息中使用它。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class Notifier {
    private final String channel;
    private final DeliveryLog log;
    private String prefix = "";

    public Notifier(String channel, DeliveryLog log) {
        this.channel = channel;
        this.log = log;
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public void send(String orderId) {
        log.record(channel + " " + prefix + " order=" + orderId);
    }
}
```

XML 只增加 `property` 配置。它对应 JavaBean 可写属性 `setPrefix`，不是任意私有字段的直接写入指令。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
[email [orders] order=O-008]
```

通知内容现在带有 `[orders]`。本次创建顺序是先调用构造器得到对象，再填充 setter 属性。若把属性名写错，定义读取仍可能成功，而对象创建期间会报告属性不可写。后面提取模板后，我们会再观察继承属性与目标类不兼容的情况。

## 5. 一个值不够了，再加入列表和 Map

下一项需求是多个收件人以及附加字段。这时才需要 List 和 Map。通知器增加两个 setter；复制传入集合是为了避免外部直接修改当前保存的数据，输出 Map 使用 `TreeMap` 保持可读的排序。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Notifier {
    private final String channel;
    private final DeliveryLog log;
    private String prefix = "";
    private List<String> recipients = List.of();
    private Map<String, String> headers = Map.of();

    public Notifier(String channel, DeliveryLog log) {
        this.channel = channel;
        this.log = log;
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public void setRecipients(List<String> recipients) { this.recipients = List.copyOf(recipients); }

    public void setHeaders(Map<String, String> headers) { this.headers = new TreeMap<>(headers); }

    public void send(String orderId) {
        log.record(channel + " " + prefix + " order=" + orderId + " -> " + recipients + " headers=" + headers);
    }
}
```

在 XML 中为两个属性分别提供 `list` 与 `map`。当前只放一个收件人和一个字段，先确认装配路径，稍后在父子配置中再增加和合并。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/></map></property>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
```

观察到的列表与 Map 是实际对象上的数据。定义读取时它们先以配置元数据表示，创建对象时才解析为目标属性值。不要把“XML 里写了一串文字”与“最终 Java 类型是什么”混在一起。

现在已经有构造器参数、引用和集合，但还没有初始化约束。下一步让通知器在接收配置后才能进入可用状态。

## 6. 配置完整后才能发送：接入初始化和关闭

新增一个需求：没有收件人时不能启动通知器，关闭后也不能继续发送。因此增加 `initialize`、`shutdown` 和 `ready`。注意，初始化检查必须等属性填充完成后才能进行，不能提前放进当前构造器。

先给 DeliveryLog 增加事件列表以及 event、events 方法，用来记录初始化和关闭。此前的通知记录仍然保留。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/DeliveryLog.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.ArrayList;
import java.util.List;

public class DeliveryLog {
    private final List<String> deliveries = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    public void record(String message) { deliveries.add(message); }
    public void event(String message) { events.add(message); }
    public List<String> deliveries() { return List.copyOf(deliveries); }
    public List<String> events() { return List.copyOf(events); }
}
```

然后修改通知器，接入可用状态与生命周期方法。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Notifier {
    private final String channel;
    private final DeliveryLog log;
    private String prefix = "";
    private List<String> recipients = List.of();
    private Map<String, String> headers = Map.of();
    private boolean ready;

    public Notifier(String channel, DeliveryLog log) {
        this.channel = channel;
        this.log = log;
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public void setRecipients(List<String> recipients) { this.recipients = List.copyOf(recipients); }

    public void setHeaders(Map<String, String> headers) { this.headers = new TreeMap<>(headers); }

    public void initialize() {
        if (recipients.isEmpty()) { throw new IllegalStateException("recipients must not be empty"); }
        ready = true;
        log.event("init:" + channel);
    }

    public void shutdown() {
        ready = false;
        log.event("close:" + channel);
    }

    public void send(String orderId) {
        if (!ready) { throw new IllegalStateException("notifier is not ready"); }
        log.record(channel + " " + prefix + " order=" + orderId + " -> " + recipients + " headers=" + headers);
    }
}
```

Java 方法不会因为名字叫 initialize 就自动被容器执行。XML 需要明确写出 `init-method` 和 `destroy-method`。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/></map></property>
    </bean>
</beans>
```

入口取出记录器，分别在业务前与上下文关闭后查看事件。关闭后的记录器引用只用于观察结果，没有再次调用已关闭的通知器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class Main {
    public static void main(String[] args) {
        DeliveryLog log;
        try (var context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:practice008/beans.xml");
            context.refresh();
            log = context.getBean(DeliveryLog.class);
            System.out.println("before=" + log.events());
            context.getBean("notifier", Notifier.class).send("O-008");
            System.out.println("deliveries=" + log.deliveries());
        }
        System.out.println("after=" + log.events());
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
after=[init:email, close:email]
```

到这里才完整建立起本例生命周期：构造对象 → 填充配置 → 初始化 → 业务调用 → 关闭时销毁。`init:email` 与 `close:email` 是这条顺序的观察证据。

单独删除 `init-method` 后，当前上下文仍可能完成启动，业务调用则因 `ready` 为 false 失败。这说明“能启动”与“对象已经按业务约定准备好”需要分别验证。该变体留作练习；下面继续使用已验证的正常配置。

## 7. 先配置第二个通知器，亲眼看到重复

现在要同时拥有邮件和短信通知器，它们共享前缀、收件人、字段和生命周期方法，渠道不同。先按已掌握的方式写出第二份定义，暂时不要引入新机制。将原 `notifier` 改名为 `emailNotifier`，新增 `smsNotifier`。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="emailNotifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/></map></property>
    </bean>
    <bean id="smsNotifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="sms"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/></map></property>
    </bean>
</beans>
```

因为名称和对象数量改变，入口也要明确分别获取两者。不能继续只按唯一类型取对象，因为现在有两个 `Notifier`。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class Main {
    public static void main(String[] args) {
        DeliveryLog log;
        try (var context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:practice008/beans.xml");
            context.refresh();
            log = context.getBean(DeliveryLog.class);
            System.out.println("before=" + log.events());
            context.getBean("emailNotifier", Notifier.class).send("O-008");
            context.getBean("smsNotifier", Notifier.class).send("O-008");
            System.out.println("deliveries=" + log.deliveries());
        }
        System.out.println("after=" + log.events());
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}, sms [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
after=[init:email, init:sms, close:sms, close:email]
```

这里记录的是当前运行的事件顺序；独立 Bean 之间的具体先后不应被当作业务执行契约。我们要确认的是每个通知器完成初始化，并在所属上下文关闭时被销毁。

现在看 XML：两份构造器第1个参数、前缀、列表、Map、初始化与销毁配置几乎完全重复。若修改公共字段，就要维护两处。这是接下来提取父定义的实际原因。

### 7.1 把公共配置提取成模板

把公共部分挪到 `notifierTemplate`；子定义用 `parent` 指向它，并只覆盖构造器第0个参数。`abstract="true"` 表示模板不能直接作为业务对象请求。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifierTemplate" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" abstract="true" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="template"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/></map></property>
    </bean>
    <bean id="emailNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="email"/>
    </bean>
    <bean id="smsNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="sms"/>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}, sms [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
after=[init:email, init:sms, close:sms, close:email]
```

业务行为与关闭事件保持一致，但公共配置只有一份。容器先合并父子定义，再为两个名字创建各自对象，不需要创建一个“父通知器实例”。

父子定义都使用同一个 Java 类，没有添加 `extends`。这里的 `abstract` 也是定义标志，与 Java 抽象类无关。构造参数使用明确索引，是为了让第0个参数的覆盖关系清楚可见。

## 8. 子配置要增加收件人，先观察默认替换

邮件现在希望增加业务负责人，同时改变 region 并增加 priority。先不写 `merge`，看看子集合默认做什么。短信则把收件人改为 `on-call`。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifierTemplate" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" abstract="true" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="template"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/><entry key="source" value="course"/></map></property>
    </bean>
    <bean id="emailNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="email"/>
        <property name="recipients"><list><value>owner@example.test</value></list></property>
        <property name="headers">
            <map><entry key="region" value="us"/><entry key="priority" value="high"/></map>
        </property>
    </bean>
    <bean id="smsNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="sms"/>
        <property name="recipients"><list><value>on-call</value></list></property>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [owner@example.test] headers={priority=high, region=us}, sms [orders] order=O-008 -> [on-call] headers={region=cn, source=course}]
after=[init:email, init:sms, close:sms, close:email]
```

邮件列表只有负责人，公共邮箱消失了；邮件 Map 中的 source 也没有保留。这不是父定义没有生效，而是子集合替换了对应父集合。

目标是“保留公共配置并增加差异”，所以现在只在邮件的**子集合**上启用 `merge="true"`。短信继续保留替换行为。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="notifierTemplate" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.Notifier" abstract="true" init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="template"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/><entry key="source" value="course"/></map></property>
    </bean>
    <bean id="emailNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="email"/>
        <property name="recipients"><list merge="true"><value>owner@example.test</value></list></property>
        <property name="headers">
            <map merge="true"><entry key="region" value="us"/><entry key="priority" value="high"/></map>
        </property>
    </bean>
    <bean id="smsNotifier" parent="notifierTemplate">
        <constructor-arg index="0" value="sms"/>
        <property name="recipients"><list><value>on-call</value></list></property>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test, owner@example.test] headers={priority=high, region=us, source=course}, sms [orders] order=O-008 -> [on-call] headers={region=cn, source=course}]
after=[init:email, init:sms, close:sms, close:email]
```

邮件列表恢复公共邮箱，并在其后追加负责人；邮件 Map 保留 source，同名 region 使用子值，并增加 priority。短信列表仍只有 on-call。

列表合并不负责业务去重；Map 同键按子值覆盖。`merge` 写在子集合上，不是写在 `<bean>` 上。它只合并父子定义中对应的集合配置，也要求父子集合类型兼容。

现在的规则来自你刚才一次明确修改，不需要先背一张抽象规则表再猜它是否适用。

## 9. 带着刚写的模板进入元数据和源码

此时已经知道有效对象是什么样，接下来回答“父模板在哪一步参与了创建”。新增一个观察入口 `DefinitionMain.java`，保留业务入口 `Main`，不用来回改业务代码。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/DefinitionMain.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.List;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class DefinitionMain {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:practice008/beans.xml");
            var factory = context.getBeanFactory();
            var raw = factory.getBeanDefinition("emailNotifier");
            var merged = factory.getMergedBeanDefinition("emailNotifier");
            System.out.println("raw-class=" + raw.getBeanClassName());
            System.out.println("parent=" + raw.getParentName());
            System.out.println("raw-size=" + ((List<?>) raw.getPropertyValues().get("recipients")).size());
            System.out.println("merged-size=" + ((List<?>) merged.getPropertyValues().get("recipients")).size());
            System.out.println("has-instance=" + factory.containsSingleton("emailNotifier"));
            context.refresh();
            try {
                context.getBean("notifierTemplate");
            } catch (BeanIsAbstractException error) {
                System.out.println("template-error=" + error.getClass().getSimpleName());
            }
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.DefinitionMain
```

```text
raw-class=null
parent=notifierTemplate
raw-size=1
merged-size=2
has-instance=false
template-error=BeanIsAbstractException
```

原始子定义没有写 class，所以查询结果为 null；它只记录子列表的一项。合并结果中有两项，但查询合并定义并没有创建邮件单例。列表内部此时仍可能是 `TypedStringValue` 等元数据，不应强转为业务 `List<String>`。

刷新后请求模板出现 `BeanIsAbstractException`，说明模板定义存在与模板可实例化是两件事。不要把服务引用指向抽象模板。

现在在 Spring v7.0.9 的 `AbstractBeanFactory.getMergedBeanDefinition` 父定义分支设置断点，观察 parentBeanName，再看复制父定义与 `overrideFrom` 的调用。继续跟踪属性合并，便能进入 `ManagedList.merge` 和 `ManagedMap.merge`。不要在断点里主动调用 getBean，否则会把实例创建引入当前观察。

源码：[AbstractBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java)、[AbstractBeanDefinition](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanDefinition.java)、[ManagedList](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedList.java)、[ManagedMap](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedMap.java)。

**定义继承也不是所有开关一律沿用父值。** 例如 XML 子 bean 的 lazy-init 会先按文档默认值解析，再参加合并。原有完整示例专门验证了父模板 lazy-init 为 true、子定义却为 false 的情况；这个边界及不同注册入口的区别见 [机制参考第7节](02-机制与边界参考.md#section-7)。

## 10. 创建过程需要工厂时，只替换创建入口

前面都直接调用通知器构造器。现在假设创建逻辑需要由工厂封装。先新增普通 Java 工厂类，分别提供静态和实例方法；通知器自身不需要改动。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/NotifierFactory.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class NotifierFactory {
    public static Notifier createStatic(String channel, DeliveryLog log) {
        return new Notifier(channel, log);
    }

    public Notifier create(String channel, DeliveryLog log) {
        return new Notifier(channel, log);
    }
}
```

这是沿现有程序做的另一种创建方式对照。本步用两份独立产品定义替换模板配置：邮件由静态方法创建，短信由工厂 Bean 的实例方法创建。将上一步合并后的收件人和 Map 显式展开，保持有效配置不变。它们仍需要初始化，不能因为工厂负责 new 就遗漏后续配置。

文件：`src/main/resources/practice008/beans.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.DeliveryLog"/>
    <bean id="emailNotifier" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.NotifierFactory" factory-method="createStatic"
          init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>ops@example.test</value><value>owner@example.test</value></list></property>
        <property name="headers"><map><entry key="region" value="us"/><entry key="source" value="course"/><entry key="priority" value="high"/></map></property>
    </bean>
    <bean id="notifierFactory" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.NotifierFactory"/>
    <bean id="smsNotifier" factory-bean="notifierFactory" factory-method="create"
          init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="sms"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients"><list><value>on-call</value></list></property>
        <property name="headers"><map><entry key="region" value="cn"/><entry key="source" value="course"/></map></property>
    </bean>
</beans>
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test, owner@example.test] headers={priority=high, region=us, source=course}, sms [orders] order=O-008 -> [on-call] headers={region=cn, source=course}]
after=[init:email, init:sms, close:sms, close:email]
```

静态定义的 class 是工厂类，产品却是 Notifier；实例产品定义甚至没有 class，通过 factory-bean 找工厂对象。这时 `<constructor-arg>` 实际表达工厂方法参数，不能只按标签名字判断执行路径。

业务入口 Main 无需改动，产品仍完成属性填充、初始化和销毁。当前工厂是普通 Java 类，并没有实现 `FactoryBean<T>`，不要把两个机制混为一谈。第9步的 DefinitionMain 专用于模板配置，本步改为工厂后继续运行 Main，不再拿已经移除的模板名查询。

## 11. 迁移成 Java 配置时，先保持当前行为

接着把当前两条通知器装配关系写成 Java 配置。先保留名称、收件人和生命周期配置，再考虑消除重复。`@Bean` 方法参数由容器提供，方法体负责设置属性。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/JavaConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JavaConfig {
    @Bean
    public DeliveryLog deliveryLog() { return new DeliveryLog(); }

    @Bean(initMethod = "initialize", destroyMethod = "shutdown")
    public Notifier emailNotifier(DeliveryLog deliveryLog) {
        var notifier = new Notifier("email", deliveryLog);
        notifier.setPrefix("[orders]");
        notifier.setRecipients(List.of("ops@example.test", "owner@example.test"));
        notifier.setHeaders(Map.of("region", "us", "source", "course", "priority", "high"));
        return notifier;
    }

    @Bean(initMethod = "initialize", destroyMethod = "shutdown")
    public Notifier smsNotifier(DeliveryLog deliveryLog) {
        var notifier = new Notifier("sms", deliveryLog);
        notifier.setPrefix("[orders]");
        notifier.setRecipients(List.of("on-call"));
        notifier.setHeaders(Map.of("region", "cn", "source", "course"));
        return notifier;
    }
}
```

新增 JavaMain，业务调用与上一步保持相同，只把配置入口换掉。这个入口仅注册 JavaConfig，不同时导入 XML，避免两套同名定义混合。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/JavaMain.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class JavaMain {
    public static void main(String[] args) {
        DeliveryLog log;
        try (var context = new AnnotationConfigApplicationContext(JavaConfig.class)) {
            log = context.getBean(DeliveryLog.class);
            System.out.println("before=" + log.events());
            context.getBean("emailNotifier", Notifier.class).send("O-008");
            context.getBean("smsNotifier", Notifier.class).send("O-008");
            System.out.println("deliveries=" + log.deliveries());
        }
        System.out.println("after=" + log.events());
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.JavaMain
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test, owner@example.test] headers={priority=high, region=us, source=course}, sms [orders] order=O-008 -> [on-call] headers={region=cn, source=course}]
after=[init:email, init:sms, close:sms, close:email]
```

比较业务消息和生命周期事件，确认创建入口变化没有丢失关键配置。两种方式的定义内部表达并不完全一致：XML 属性由定义元数据保存，Java 方法体中的 setter 则是普通代码，不会逐句变成 PropertyValues。

若存量 XML 一时不能全部改写，可以先把 Java 入口与原配置连接。下面创建一个只导入 XML 的配置，再把 JavaMain 中注册的类型替换为它。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/ImportXmlConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:practice008/beans.xml")
public class ImportXmlConfig {
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/JavaMain.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class JavaMain {
    public static void main(String[] args) {
        DeliveryLog log;
        try (var context = new AnnotationConfigApplicationContext(ImportXmlConfig.class)) {
            log = context.getBean(DeliveryLog.class);
            System.out.println("before=" + log.events());
            context.getBean("emailNotifier", Notifier.class).send("O-008");
            context.getBean("smsNotifier", Notifier.class).send("O-008");
            System.out.println("deliveries=" + log.deliveries());
        }
        System.out.println("after=" + log.events());
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.JavaMain
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test, owner@example.test] headers={priority=high, region=us, source=course}, sms [orders] order=O-008 -> [on-call] headers={region=cn, source=course}]
after=[init:email, init:sms, close:sms, close:email]
```

输出一致，但此时业务对象仍由 XML 定义；这只是迁移入口，不表示 XML 已经改写成 @Bean。JavaConfig 文件仍在项目中，但这里没有注册它，也没有执行组件扫描去发现它。

实际迁移时按一个对象组替换，移除对应旧定义，再比较名字、引用、集合、作用域和生命周期。尤其不要把列表合并默默改成替换，或遗漏初始化方法。

## 12. 最后区分父子容器与父子定义

模板主线只用过一个上下文。现在再创建一个子上下文，让子服务使用父上下文已有的邮件通知器，看看这是另一种什么关系。

先创建一个小服务类，它只接收并使用通知器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/ChildService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

public class ChildService {
    private final Notifier notifier;

    public ChildService(Notifier notifier) { this.notifier = notifier; }
    public Notifier notifier() { return notifier; }
}
```

文件：`src/main/resources/practice008/child.xml`。以下是本步该文件的完整内容。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="childService" class="cn.ningbingjian.learnjava.ioc.lesson008.practice.ChildService">
        <constructor-arg ref="emailNotifier"/>
    </bean>
</beans>
```

新增层级入口：父上下文继续读取当前工厂 XML；子上下文只读取 child.xml，在刷新前通过 setParent 关联父级。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/practice/HierarchyMain.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson008.practice;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;

public class HierarchyMain {
    public static void main(String[] args) {
        try (var parent = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(parent).loadBeanDefinitions("classpath:practice008/beans.xml");
            parent.refresh();
            var email = parent.getBean("emailNotifier", Notifier.class);
            var log = parent.getBean(DeliveryLog.class);
            try (var child = new GenericApplicationContext()) {
                child.setParent(parent);
                new XmlBeanDefinitionReader(child).loadBeanDefinitions("classpath:practice008/child.xml");
                child.refresh();
                System.out.println("local-definition=" + child.containsBeanDefinition("emailNotifier"));
                System.out.println("same-parent-object=" + (child.getBean(ChildService.class).notifier() == email));
                System.out.println("parent-sees-child=" + parent.containsBean("childService"));
            }
            System.out.println("closed-after-child=" + log.events().stream().filter(e -> e.startsWith("close:")).count());
            email.send("O-008");
            System.out.println("parent-still-usable=true");
        }
    }
}
```

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.HierarchyMain
```

```text
local-definition=false
same-parent-object=true
parent-sees-child=false
closed-after-child=0
parent-still-usable=true
```

子容器本地没有通知器定义，却能通过父级查找拿到父容器对象；父容器不能反向找到子服务。关闭子容器没有销毁父级通知器，它还可以继续发送，随后才随父上下文关闭。

| 关系 | 连接什么 | 刚才的实现 |
| --- | --- | --- |
| 定义继承 | 两份配置定义 | 第7步 parent 属性指向抽象模板，合并后创建子 Bean |
| Java 继承 | Java 类型 | 本页没有靠 extends 建立通知器父子类型 |
| 容器层级 | 两个上下文 | 本步 setParent 提供向父级查找对象的路径 |

它们可以在复杂系统中组合，但不是同一个“父子”概念。继承配置也要求目标类能接收那些构造参数、属性和生命周期方法；例如让没有 setPrefix 的 Object 使用 prefix 模板，会在属性填充阶段失败。这个隔离样例与更详细的解析链路保留在 [机制参考第13—15节](02-机制与边界参考.md#section-13)。

## 13. 完成后回看自己的实现过程

现在你已经亲手从一个 bean 元素走到对象引用、属性和集合，先经历重复配置，再提取父定义，随后验证合并规则并进入源码。每个概念都对应过一次明确的程序修改。

尝试独立回答：为什么配置格式正确仍可能引用失败？为什么父模板不需要对象实例？为什么开启 merge 后邮件增加了公共邮箱？工厂返回产品后，谁继续执行初始化？关闭子容器为什么不销毁父通知器？

第9步的 DefinitionMain 对应模板配置；完成后续工厂改写后，请使用相应步骤入口，或恢复第8步 XML 再观察模板。

本页各步已从不存在的 practice 目录逐步编译和运行验证，包括引用错误及修复。原有十个完整场景和十二个测试仍保留，可运行 `mvn -pl 008-xml-definition-inheritance -am test` 对照边界；第001—008课原有聚合测试仍共75个。完整输出对照、lazy-init 边界、失败定位和源码入口见 [机制与边界参考](02-机制与边界参考.md)。

下一课：[08-01-009 编程式注册与外部对象接入](../00-模块学习大纲.md#lesson-009)。

