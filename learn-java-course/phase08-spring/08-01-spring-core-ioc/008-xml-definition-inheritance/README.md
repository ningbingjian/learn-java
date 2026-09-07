# 08-01-008 XML 配置与定义继承：从第一份配置逐步提取模板

[返回模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-008) · [上一课](../007-component-scanning/README.md)

第007课用扫描登记对象定义，这一课学习用 XML 表达装配规则。我们继续从一个通知器开始，逐步加入参数、对象引用、集合和生命周期，再提取父定义。**核心目标是能解释：XML 如何成为定义、父子定义如何形成有效配置、有效配置又如何用于创建对象。**

每节先说明当前问题和必要理论，再逐步修改、运行代码，在操作位置解释容器正在做什么，最后用小总结收拢结论。已经学过的 IoC、构造器注入、作用域只作必要回顾；本课的对象引用、定义继承、集合合并与源码路径会展开讲清楚。

跟写文件使用独立的 `cn.ningbingjian.learnjava.ioc.lesson008.practice` 包，仍放在本课 Maven 子模块。**先按第1步创建文件，后面逐步替换；不要一开始把完整示例的所有类复制过来。** 仓库原有 `lesson008` 业务类和 XML 保留作完成后的对照，跟写使用单独的 `practice008/beans.xml`，两组资源不混合加载。

本页文件路径相对于 `008-xml-definition-inheritance`；所有 Maven 命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。`exec.mainClass` 选择正在跟写的入口，不传该参数仍运行原有完整演示。

先完成第1—9步的 XML 装配与模板主线，再继续工厂方法、Java 配置迁移和容器层级。每一步的完整文件内容便于核对当前位置；只在指示的文件上修改，其他文件保留上一步状态。本课核心原理直接在正文讲解，额外边界与完整对照见 [机制参考](02-机制与边界参考.md)。

<a id="topic-1"></a>

## 1. 先让 XML 管理一个没有依赖的对象

### 1.1 理论：XML、对象定义与实例的关系

第一件事是让容器创建一个通知器。[第002课](../002-ioc-di-object-assembly/README.md)已经讲过控制反转：业务对象的创建和依赖装配交给容器；[第006课](../006-bean-definition-model/README.md)讲过 BeanDefinition，也就是容器保存的“对象定义”。这一节接着回答：**我们写在 XML 里的内容，怎样成为对象定义？**

XML 是配置的表达形式。`<bean>` 描述一个对象该怎么创建，`id` 是这份定义的注册名称，`class` 是创建时使用的 Java 类型。读取到这段声明，并不等于已经执行了 Java 构造器。

下面的入口特意把三个动作分开写，便于认识它们的职责：

| 代码动作 | 此时完成什么 | 还不能据此推断什么 |
| --- | --- | --- |
| `loadBeanDefinitions(...)` | 读取 XML，把定义登记到当前容器 | 业务对象已经构造完成 |
| `refresh()` | 启动上下文，组织后处理和单例预实例化等工作 | 所有作用域、所有延迟对象都已创建 |
| `getBean(...)` | 按要求取得可用对象，必要时触发创建 | 每次调用都会重新创建对象 |

本例没有延迟配置，使用默认单例作用域，因此通知器会在刷新期间创建，随后 `getBean` 取得该实例。关于作用域的完整边界沿用第006课；这里先把“读配置”和“创建对象”分清。

### 1.2 实操：逐步实现并解释原理

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

### 1.3 小总结

XML 先形成 BeanDefinition，当前上下文刷新时再按规则创建对象。排查时先判断问题发生在资源读取、定义登记还是对象创建，不能把它们都笼统叫作“XML 没生效”。

<a id="topic-2"></a>

## 2. 希望切换渠道，于是增加构造参数

### 2.1 理论：构造器要求与 XML 参数如何对应

通知渠道不应固定在业务方法里。第002、004课已经讲过构造器注入：先由 Java 构造器声明对象成立所必需的输入，再让容器提供这些输入。本节只补充它在 XML 中的表达。

`constructor-arg` 为创建动作提供参数。当前构造器只有一个 `String channel`，因此 `index="0"` 指向它；索引从零开始，和参数变量叫不叫 `channel` 无关。`value="email"` 提供文本值，容器在创建对象时结合目标参数类型进行匹配和必要的转换。

这意味着 XML 与 Java 之间有一份明确的约定：**类要求什么参数，配置就必须能够提供什么参数。** 配置文本不是可以任意塞给任何类型的 Java 值。当前目标就是 String，转换很直接；以后遇到数字或多个重载构造器，还要分别考虑能否转换、能否匹配到正确构造器。

### 2.2 实操：逐步实现并解释原理

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

### 2.3 小总结

构造器声明创建要求，XML 提供匹配的参数。`index` 对应参数位置，`value` 提供需要按目标类型处理的值；理解这层对应关系，才能判断改 Java 签名后是否也要改配置。

<a id="topic-3"></a>

## 3. 希望保留通知记录，于是引入对象引用

### 3.1 理论：ref 如何在创建阶段成为真实对象引用

通知器需要把发送记录写到记录器里。这个依赖要求的是一个可以调用 `record` 方法的 `DeliveryLog` 对象，单独一段“deliveryLog”文字无法完成记录。

所以 XML 区分两种输入：`value` 提供值，`ref` 描述对容器中另一个对象的引用。**引用描述先存入定义，真实对象在创建阶段才解析出来。** 这也是本课需要掌握的新原理。

以 `ref="deliveryLog"` 为例，容器内部经历以下过程：

1. 读取 XML 时，把目标名称保存成“运行时 Bean 引用”（`RuntimeBeanReference`）这样的元数据。此时它表达的是以后需要查找谁。
2. 创建 `Notifier`、准备构造器参数时，值解析器看到这个引用，按 `deliveryLog` 这个名字向容器获取对象。
3. 目标是默认单例：已有实例就复用，还没有就按它的定义创建。拿到的对象还必须能满足 `DeliveryLog` 参数类型。
4. 参数准备好后，容器调用通知器构造器，把真实对象引用传进去。通知器之后使用的是自己的 `log` 字段，不会每次记录都重新解析 XML。

这里按 `ref` 指定的名字查找，不是让容器猜测要用哪个同类型对象。两个通知器以后引用同一个默认单例记录器时，会拿到同一个记录器；复用来自目标定义的作用域，不能简单归因于“用了 ref”。

还可以先作一个预测：把引用名称写错，XML 仍可能语法正确，但到准备构造参数时会失败。下面既实现正常引用，也亲手验证这个失败位置。

### 3.2 实操：逐步实现并解释原理

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

这次输出来自 `Main` 最后取得的记录器。请把它和代码连起来看：`Notifier.send` 调用自己持有的 `log.record`，入口又通过 `getBean(DeliveryLog.class)` 读取记录。能读到同一条记录，是因为当前两个获取路径最终使用同一个默认单例记录器。

回到创建过程，XML 读取器先登记 `ref="deliveryLog"` 这个引用描述；准备通知器构造参数时，容器按名字取得记录器，再把它传到参数索引1。构造器中的 `this.log = log` 才把这个真实引用保存到业务对象中。`value="deliveryLog"` 只表达文字，不会因为文字恰好与 Bean 名相同就自动转成对象查找。

当前 XML 把记录器放在前面便于阅读，但普通定义引用不要求依靠声明先后来表达依赖。读取配置与创建对象分开后，容器可以根据已经登记的定义解析依赖；不要用调整 XML 排列来替代检查引用关系。

#### 3.2.1 验证引用在创建阶段解析：改错再修复

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

这条命令应失败，输出只截取稳定的根因片段。请定位失败发生在哪一层：`ref` 是合法属性，`missingLog` 也是合法文本，所以 XML 格式检查不负责保证目标对象存在；读取器可以先登记这个引用描述。到了 `refresh()` 创建通知器、准备第1号构造参数时，容器才发现名字无法解析。

此时通知器构造器还没有拿到全部参数，后面的 `send` 更没有机会执行。异常外层可能带有创建失败等包装信息，最底层的“没有这个 Bean 名”才指向这次故意制造的问题。

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

### 3.3 小总结

`ref` 保存的是命名依赖，创建阶段才解析成真实对象引用。引用失败要核对名称及目标定义是否加载；引用成功后是否复用同一对象，还要看目标的作用域。

<a id="topic-4"></a>

## 4. 增加一个可配置前缀，学习属性填充

### 4.1 理论：属性填充为何发生在构造之后

渠道和记录器是构造器必需参数；前缀允许先有默认值，再在创建过程中补充。我们用 setter 注入表达这种装配方式。初学时要区分“构造出来”和“配置完成”：对象已经存在，属性仍可能没有填好。

`<property name="prefix" ...>` 使用的是 JavaBean 属性约定。在当前类中，`prefix` 这个可写属性由 `setPrefix(String)` 提供。Spring 在对象构造完成后，通过属性访问组件找到写入方法、准备参数，再调用 setter。它不会因为 XML 写了 `prefix`，就自动获得对任意同名私有字段的直接写入能力。

因此当前顺序必然是先执行 `Notifier(channel, log)`，再执行 `setPrefix(...)`。在构造器内部读取 `prefix`，看到的仍是代码中的初始空字符串；如果业务校验依赖 setter 注入的数据，就需要放到属性填充之后。这正是第6节引入初始化回调的原因。

### 4.2 实操：逐步实现并解释原理

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

### 4.3 小总结

`property` 在对象构造后通过可写属性完成注入。因此 setter 配置不能在当前构造器里提前使用，依赖完整配置的校验应放到后续初始化阶段。

<a id="topic-5"></a>

## 5. 一个值不够了，再加入列表和 Map

### 5.1 理论：配置集合与业务集合有什么区别

多个收件人需要有顺序的列表，附加字段需要按键取值的 Map。Java 类先通过 `setRecipients(List<String>)` 和 `setHeaders(Map<String, String>)` 声明接收什么，XML 再提供相应集合配置。

这里要认识集合的两个状态。读取阶段，Spring 用 `ManagedList`、`ManagedMap` 保存集合配置；其中的元素可以是文本值描述，也可以是 Bean 引用。它们属于定义元数据。属性填充时，容器逐个解析元素、按需要转换类型，然后把可供业务使用的集合传给 setter。后面的父子集合合并发生在前一种状态，第8、9节会详细跟进。

本节还有两处属于我们自己的 Java 设计：`List.copyOf` 保存不可修改的列表，`new TreeMap<>(headers)` 复制 Map 并按键排序。这些行为来自 setter 的代码。不能看到输出有序，就归纳成“Spring 注入的 Map 都按键排序”，也不能认为所有注入集合都不可修改。

### 5.2 实操：逐步实现并解释原理

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

### 5.3 小总结

集合先作为定义元数据保存，再解析成传给 setter 的实际值。最终集合是否可修改、是否排序，还取决于业务 setter 如何保存它，不能全部归因于容器。

<a id="topic-6"></a>

## 6. 配置完整后才能发送：接入初始化和关闭

### 6.1 理论：配置完成后如何进入可用状态

有了收件人配置，还要保证通知器只有准备好后才能发送。对象刚执行完构造器时，收件人仍是空列表；如果立刻检查，就会把随后能正确装配的对象误判为配置错误。

初始化回调解决的是这个时间点问题：**容器完成属性填充后，再让对象检查配置、建立可用状态。** 本例通过 `init-method="initialize"` 指定普通 Java 方法。方法名本身没有魔法；是这项配置告诉容器在初始化阶段调用它。

`destroy-method="shutdown"` 则声明关闭时执行的清理动作。对于本例由上下文管理的单例，容器会登记销毁回调，在关闭时调用。关闭回调和 JVM 垃圾回收不是一回事：前者执行我们约定的业务清理，后者处理内存回收；Java 变量也可能仍持有已关闭对象的引用。

这节只聚焦我们指定的初始化、销毁方法。第006课介绍过不同作用域的生命周期差异，不能把本例的单例关闭行为直接推广到 prototype。完整的后处理器参与顺序会在后续生命周期专题中展开。

### 6.2 实操：逐步实现并解释原理

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

### 6.3 小总结

实例化得到对象，属性填充提供配置，初始化让对象达到业务可用状态，关闭回调执行清理。`ready` 和事件记录分别帮助我们检查可用状态与调用时机。

<a id="topic-7"></a>

## 7. 先配置第二个通知器，亲眼看到重复

### 7.1 理论：父子定义如何复用装配规则

邮件和短信需要两个对象，但它们的大部分配置相同。如果分别维护两份 XML，改一次公共前缀就需要修改两处，遗漏一处会造成行为不一致。父子定义用来复用这些创建和装配规则。

这里的“父”首先是一份 **BeanDefinition，也就是配置定义**。子定义的 `parent="notifierTemplate"` 表示：以这份父定义为基础，补上或覆盖当前子定义提供的配置，得到当前对象的有效定义。然后容器按有效定义创建子 Bean。

这能解释三个容易混淆的点：

1. 不需要先创建父通知器再复制它。复用的是定义中的配置，邮件和短信分别创建自己的实例。
2. Java 类不必建立 `extends` 关系。本例两份子定义都使用同一个 `Notifier` 类；改变的是装配规则，不是 Java 类型体系。
3. 公共模板只供复用时，应标记 `abstract="true"`。这是 Spring 定义的标志：跳过它的预实例化，也不允许直接请求它的业务实例；它不会把 `Notifier` 变成 Java 抽象类。

先用具体字段理解合并。本节提取模板后，邮件定义会得到以下配置：

| 配置位置 | 父模板提供 | 邮件子定义提供 | 邮件有效定义 |
| --- | --- | --- | --- |
| Java 类型 | `Notifier` | 未填写 | `Notifier` |
| 构造参数索引0 | `template` | `email` | `email` |
| 构造参数索引1 | 引用 `deliveryLog` | 未填写 | 引用 `deliveryLog` |
| `prefix` 属性 | `[orders]` | 未填写 | `[orders]` |
| 初始化、销毁方法 | `initialize`、`shutdown` | 未填写 | 沿用这两个方法 |
| 是否抽象 | `true` | 子定义默认 `false` | 子定义可参与实例化 |

注意最后一行：定义合并有具体字段规则，不能概括成“子配置没写的任何开关都继承父值”。本节先掌握表中的字段，第9节再结合解析与合并源码说明原因。

下面先把重复配置写出来，建立改造前的行为对照；随后提取模板，检查对象行为是否保持一致。

### 7.2 实操：逐步实现并解释原理

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

#### 7.2.1 把公共配置提取成模板

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

运行前先对照理论中的表推演一次：邮件子定义没有 class，合并结果使用父模板的 `Notifier`；索引0被子值 `email` 覆盖，索引1继续引用 `deliveryLog`，属性和生命周期方法沿用模板。因此邮件仍会初始化并发送；短信把同一套计算中的渠道换成 `sms`。

在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson008.practice.Main
```

```text
before=[init:email, init:sms]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}, sms [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
after=[init:email, init:sms, close:sms, close:email]
```

输出中应当有邮件和短信的初始化事件，却没有 `init:template`。`template` 只是父定义中可被覆盖的参数值，抽象模板不参加业务对象创建。两个通知器拥有各自状态，同时引用同一个默认单例记录器，这两件事并不矛盾。

这次重构保持了业务结果与关闭行为。公共配置只有一份，容器先合并父子定义，再为两个名字创建各自对象；整个过程不需要一个“父通知器实例”。

父子定义都使用同一个 Java 类，没有添加 `extends`。构造参数使用明确索引，是为了让索引0的覆盖关系清楚可见。

配置复用仍受 Java 类型能力约束：如果子定义覆盖 class，新类型必须能接收继承来的构造参数、可写属性及生命周期方法。仅仅写了 `parent`，不会给新类型自动补出 `setPrefix` 或 `initialize`。这类不兼容可能在构造器匹配、属性填充或初始化时暴露，需要按实际失败阶段定位。

### 7.3 小总结

父子定义复用配置，合并后各自创建子 Bean。模板标记 abstract 才能明确表达“只供复用”；子类配置仍必须满足目标 Java 类的构造器、属性和方法要求。

<a id="topic-8"></a>

## 8. 子配置要增加收件人，先观察默认替换

### 8.1 理论：同名集合的替换与合并规则

邮件需要保留公共邮箱，再增加负责人；短信只需要值班收件人。同一个 `recipients` 属性，因此出现了两种不同意图：追加与整体替换。容器不能替我们判断业务意图，需要配置明确表达。

父子定义合并时，属性按名称对应。子定义再次声明 `recipients`，默认使用子属性值替换父属性值；即使这个值恰好是列表，也不会自动逐项相加。子定义完全没写的属性，仍可以来自父定义。

`merge="true"` 要写在**子集合元素**上，例如子定义的 `<list>`、`<map>`。原因是容器处理同名属性时，会检查新加入的子值是否支持合并、是否启用合并，然后由子值接收父值并计算结果。这个动作发生在定义合并阶段，之后才解析集合内容并传给业务 setter。

具体计算规则由集合种类决定：

| 子集合配置 | 合并过程 | 本节应当得到的结果 |
| --- | --- | --- |
| 列表未开启 merge | 用子列表作为该属性值 | 邮件只有负责人 |
| 列表开启 merge | 先加入父列表元素，再加入子列表元素 | 公共邮箱在前，负责人在后 |
| Map 未开启 merge | 用子 Map 作为该属性值 | 父 Map 独有的 `source` 消失 |
| Map 开启 merge | 先放父键值，再放子键值 | 保留 `source`，`region` 改成子值，增加 `priority` |

List 的追加不会检查业务上的重复收件人，所以不负责去重。Map 中同键只能对应一个值，后放入的子值覆盖父值。这些规则与通知器采用哪个 Java 类无关，也不要求存在父通知器实例。

下面分两次运行：先验证默认替换，再只为邮件打开合并。运行前先根据这张表预测结果；这样输出才能用来检验你对规则的理解。

### 8.2 实操：逐步实现并解释原理

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

先分别检查两个属性。处理 `recipients` 时，父定义原本提供公共邮箱，子定义再次声明同名属性且未启用合并，所以有效属性值变成只有负责人的子列表。处理 `headers` 时也是整体替换，因此父 Map 独有的 `source` 没有进入邮件有效配置。

再看短信：它只覆盖收件人，没有重新声明 `headers`，所以仍沿用父 Map 的 `region` 和 `source`。这组对照说明替换发生在对应的同名属性上，不是整个父定义失效。

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

再把结果对应到执行时机：容器先在定义合并时算出邮件的两项收件人配置，随后解析其中的文本值，最后调用一次当前对象的 `setRecipients` 保存结果。不是先把公共邮箱注入业务对象，再运行一次“merge 方法”追加负责人。这里的 `merge` 属于配置元数据，业务类没有实现这个方法。

如果希望邮件彻底替换公共名单，就应保留默认替换；如果希望公共名单与专属名单组合，才开启合并。能说明这个业务选择，比记住一个 merge 属性更重要。

### 8.3 小总结

同名子集合默认替换；子集合开启 merge 后才按集合规则合并。List 按父后子追加，Map 同键由子值覆盖；计算的是有效配置，不是对已创建的父对象做修改。

<a id="topic-9"></a>

## 9. 带着刚写的模板进入元数据和源码

### 9.1 理论：原始定义、合并定义和值解析

前两节已经有了父子定义如何合并的解释，现在用可观察的元数据和源码核对它。先区分四个概念，避免调试时把“看到了两项配置”误认为“已经创建了对象”。

| 概念 | 当前邮件通知器中的例子 | 适合回答的问题 |
| --- | --- | --- |
| 原始定义 | 子 XML 只写了 parent、渠道和新增集合项 | 子配置原本声明了什么？ |
| 合并定义 | 已有有效类型、公共配置和合并后的两项收件人配置 | 创建这个 Bean 应使用什么规则？ |
| 解析后的参数、属性值 | 引用变为对象，集合中的文本描述变为实际值 | 构造器或 setter 实际接收什么？ |
| Bean 实例 | 能调用 `send` 的 `Notifier` | 对象是否创建并完成初始化？ |

`getBeanDefinition` 返回注册的原始定义；`getMergedBeanDefinition` 得到考虑父定义后的有效定义。**获取合并定义这一动作，本身不要求调用通知器构造器。** 容器可以缓存合并结果，因此也不要把每次查询等同于必然重新合并。

源码中，这项工作主要分为两段：`AbstractBeanFactory` 递归取得父定义，以父定义建立 `RootBeanDefinition`，再通过 `overrideFrom` 纳入子配置；当同名属性的子值支持合并时，再调用集合自身的合并方法。`RootBeanDefinition` 在这里表示创建时使用的有效定义，不是一个名为 root 的业务对象。

下面把观察代码放在 `refresh()` 之前。这样当前上下文尚未进入单例预实例化，可以清楚区分元数据查询与后续对象创建。

### 9.2 实操：逐步实现并解释原理

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

接下来用 Spring **v7.0.9** 源码核对刚才的推演。保持第8节的模板 XML，调试运行 `DefinitionMain`。每次只追一个问题，不需要先展开整个容器启动调用栈。

**第一处：父配置从哪里进入子定义？** 在 `AbstractBeanFactory.getMergedBeanDefinition` 中找到包含 `new RootBeanDefinition(pbd)` 和 `overrideFrom(bd)` 的父定义分支。运行到邮件合并时，观察父名为 `notifierTemplate`：`pbd` 是已经取得的父有效定义，`bd` 是当前子定义，`mbd` 是正形成的合并结果。先以父配置为基础，再纳入子配置，正好对应第7节的表。方法有多个重载，应选择包含这段逻辑的实现。

**第二处：同名属性为什么会替换或合并？** 跟进 `AbstractBeanDefinition.overrideFrom` 的属性处理，再到 `MutablePropertyValues.addPropertyValue`。它按属性名称找到已有父值，然后调用 `mergeIfRequired`。这个方法检查的是新加入的子属性值：是否实现 `Mergeable`，以及 `isMergeEnabled()` 是否为 true。条件满足才用子值的 `merge` 接收父值；否则返回子属性值，完成替换。这也解释了为什么开关要写在子集合上。

**第三处：合并算法为什么产生这个次序？** 进入 `ManagedList.merge`，能看到新集合先加入父元素，再加入当前子元素；进入 `ManagedMap.merge`，能看到先放父键值再放子键值。观察邮件 `recipients` 从父1项、子1项得到2项，随后回到调用处。此刻业务通知器还没构造，不要在调试表达式里主动调用 `getBean`，否则会改变正在观察的阶段。

**第四处：元数据什么时候变成构造参数或属性值？** 继续进入 `refresh()` 触发的通知器创建过程，在 `BeanDefinitionValueResolver.resolveValueIfNecessary` 观察不同值的分支：`RuntimeBeanReference` 交给引用解析；`ManagedList` 逐项解析，形成传入属性的集合。再在自己的 `Notifier` 构造器、`setRecipients`、`initialize` 上设断点，就能连接起“引用得到真实参数、对象构造、集合传入、初始化检查”的过程。

本节读源码的落点是能解释每个输出：`raw-size=1` 说明子配置只声明一项，`merged-size=2` 说明定义合并已完成，`has-instance=false` 说明这次元数据观察没有创建邮件单例。只记住类名和方法名，还不能完成这个解释。

源码：[AbstractBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java)、[AbstractBeanDefinition](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanDefinition.java)、[ManagedList](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedList.java)、[ManagedMap](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedMap.java)。

**为什么不能说“子定义没写就全部继承”？** 合并看到的是解析后的 BeanDefinition，不是原始 XML 字符。以 `lazy-init` 为例：在本课 XML 入口和默认文档配置下，子 `<bean>` 没写该属性，解析器仍会按文档默认值给子定义设置 false；合并时这个值就会覆盖父模板的 true。因此“XML 里没有写”和“定义中没有设置值”不能直接画等号。若需要延迟创建，应在子配置或适当的文档默认配置中明确表达，并验证实际效果。

可以在 `BeanDefinitionParserDelegate.parseBeanDefinitionAttributes` 核对 lazy-init 的解析，再与 `AbstractBeanDefinition.overrideFrom` 中非空值的覆盖条件对照。父模板保持 abstract、普通子定义默认非 abstract，也体现了合并要按字段理解。原有完整示例提供 lazy-init 对照，见 [机制参考第7节](02-机制与边界参考.md#section-7)；本节先掌握“解析结果参与合并”这条原因。

补充源码入口：[MutablePropertyValues](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/MutablePropertyValues.java)、[BeanDefinitionValueResolver](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/BeanDefinitionValueResolver.java)、[BeanDefinitionParserDelegate](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/xml/BeanDefinitionParserDelegate.java)。

### 9.3 小总结

原始定义、合并定义、解析后的值、业务实例是不同观察对象。先找清楚源码正在处理哪一种，再读复制、覆盖、引用解析和初始化，才能解释配置如何变成对象。

<a id="topic-10"></a>

## 10. 创建过程需要工厂时，只替换创建入口

### 10.1 理论：工厂创建与容器后续管理的分工

有时一个对象的创建过程需要封装在工厂中，例如需要选择实现或集中处理创建参数。工厂解决的是“用什么动作得到对象”，得到对象后仍可能需要属性填充和初始化。

XML 可以描述两种工厂入口。静态工厂用 `class` 指定工厂类、用 `factory-method` 指定静态方法；实例工厂用 `factory-bean` 指定已经注册的工厂对象，再调用它的实例方法。因此读取这类定义时，不能看到 `class` 就断定它一定是最终产品类型。

两种入口都沿用 `<constructor-arg>` 来配置调用参数，但此时参数交给工厂方法。对当前示例而言，Spring 先解析渠道和记录器，调用工厂取得 `Notifier`，再对这个产品执行 property 填充、初始化，并管理配置的销毁回调。

这个工厂是普通 Java 类。它与 Spring 的 `FactoryBean<T>` 扩展接口不是同一种接入方式；本节先掌握 XML 指定普通工厂方法的机制，避免同时引入两套概念。

### 10.2 实操：逐步实现并解释原理

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

### 10.3 小总结

工厂方法改变创建入口，产品仍接受后续容器管理。读 XML 时同时看 class、factory-bean、factory-method 和参数，才能知道真正调用谁、最终得到什么对象。

<a id="topic-11"></a>

## 11. 迁移成 Java 配置时，先保持当前行为

### 11.1 理论：迁移配置时要保持哪些行为

第004课已经学习过 `@Configuration` 和 `@Bean`，本节重点是迁移时如何保持装配语义。先问清楚：换一种配置表达后，哪些行为必须保持一致？名字、依赖对象、属性值、作用域和生命周期都属于这个问题。

XML 与 Java 配置都能向容器提供 Bean 定义，但具体工作所在的位置不同。XML 的 `<property>` 是由容器读取、保存、解析后调用 setter；`@Bean` 方法体中的 `setPrefix` 则是我们写的普通 Java 语句，在工厂方法执行期间直接调用。不能认为 Spring 会把 Java 方法体逐行转换成 XML 式属性元数据。

所以迁移父子模板时，需要在 Java 实现中保留最终有效配置，或者用 Java 方法复用公共装配逻辑。`parent`、集合 `merge` 不会因为类上加了 `@Configuration` 就自动发生。初始化和关闭也要继续通过相应配置交给容器。

此外，`@ImportResource` 可以让 Java 配置导入 XML 定义。这些定义进入当前上下文，不会因此自动多出一个父容器。下面先完成 Java 表达，再观察导入旧 XML 的过渡方式。

### 11.2 实操：逐步实现并解释原理

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

### 11.3 小总结

迁移要保持有效装配行为，并弄清哪些动作在 Java 方法体执行、哪些仍由容器回调完成。导入 XML 只是接入原有定义，不等于已经完成 Java 配置改写。

<a id="topic-12"></a>

## 12. 最后区分父子容器与父子定义

### 12.1 理论：向父级查找与对象管理归属

第7节的 parent 连接两份定义；本节的 `setParent` 连接两个容器。这次要解决的是：子容器里的服务怎样使用父容器已经管理的对象，同时保留各自的管理范围？

在本例按名字查找 Bean 时，子容器先检查本地；本地没有对应对象定义，就可以委托父容器查找。父容器没有一条反向遍历子容器的查找路径。这里的委托查找不会把父定义复制到子注册表，也不会把父对象重新变成一个子容器创建的对象。

因此两个查询可以同时成立：`child.containsBeanDefinition("emailNotifier")` 为 false，子服务却能拿到父容器的邮件通知器。前者只检查本地定义；后者的依赖解析可以走父级查找。

对象由哪个容器创建并管理，还关系到关闭责任。本例父级的单例通知器由父容器管理；子服务持有它的引用，不会因此接管它的销毁。下面关闭子容器后再使用父通知器，验证这条边界。

### 12.2 实操：逐步实现并解释原理

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

### 12.3 小总结

定义继承组织配置，Java 继承组织类型，容器层级组织查找和管理范围。子容器能借用父对象，并不意味着它拥有父定义或负责销毁父对象。

## 13. 完成后检查自己能否解释装配过程

现在回到本课的核心问题：一份 XML 怎样变成可以发送通知的对象？请用自己的话说明：读取器登记原始定义；父子配置形成有效定义；创建期间引用与集合值被解析；容器通过构造器或工厂取得对象，再完成属性填充和初始化；所属上下文关闭时执行已登记的清理。

这是一条帮助理解的职责主线，不是要求所有 Bean 的所有定义先统一合并、再统一创建。实际创建依赖时可能继续触发其他 Bean 的获取和创建；你应当能够指出当前正在观察哪一个 Bean、哪个处理阶段。

尝试独立回答：为什么配置格式正确仍可能引用失败？为什么父模板不需要对象实例？为什么开启 merge 后邮件增加了公共邮箱？工厂返回产品后，谁继续执行初始化？关闭子容器为什么不销毁父通知器？

第9步的 DefinitionMain 对应模板配置；完成后续工厂改写后，请使用相应步骤入口，或恢复第8步 XML 再观察模板。

跟写步骤中的代码、命令及输出沿用已逐步编译运行验证的版本，包括引用错误及修复。本次补充理论与实操原理讲解，未改变这些示例。原有完整场景和测试可运行 `mvn -pl 008-xml-definition-inheritance -am test` 对照边界；更多输出对照、lazy-init 边界、失败定位和源码入口见 [机制与边界参考](02-机制与边界参考.md)。

下一课：[08-01-009 编程式注册与外部对象接入](../00-模块学习大纲.md#lesson-009)。

