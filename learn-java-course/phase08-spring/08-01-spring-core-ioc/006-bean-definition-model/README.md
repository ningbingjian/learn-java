# 08-01-006 BeanDefinition 的基本模型：逐项写出对象创建规则

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-006) · [上一课](../005-maintainable-container-tests/README.md)

前五课已经完成对象装配、配置切换与测试。现在追问：业务对象还没产生时，容器凭什么知道要创建哪个类、传什么参数、何时初始化？

我们仍使用通知场景，但先缩小为一个对象，不复制前面全部业务配置。先手工登记最小定义，再随着构造器、属性和生命周期需求逐项补充元数据，最后回到 @Bean 对照。

本页按实际修改顺序跟写。文件路径相对于 `006-bean-definition-model`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson006.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 创建最小业务类，再只登记它的定义

### 1.1 理论：BeanDefinition 描述尚未创建的对象

前面用 @Bean 告诉容器如何创建对象，现在要看容器保存了什么。BeanDefinition 可以理解为一份对象定义：记录创建入口、参数、属性、作用域和生命周期等规则，供后续创建过程读取。

先区分四个概念。Java 类描述类型；Bean 名在注册表中标识一份定义；定义保存如何创建和管理的规则；业务实例才实际持有状态、执行方法。名字相同的讨论属于注册，实例是否相同的讨论属于创建与复用。

本节 new RootBeanDefinition 只创建了一个保存规则的 Java 对象，然后把它登记到容器。它不会因此等同于 new Notifier。RootBeanDefinition 是本例手工使用的具体定义实现，后续还会承担有效定义的表达；此刻先掌握定义与产品的区别。

### 1.2 实操：逐步实现并解释原理

先在业务构造器加入 construct 输出，再编写只登记定义的 Main。对照注册表查询与输出：定义可以已经存在，业务构造器却没有执行。

先给通知器构造器加一条输出，观察何时真的产生业务对象。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

public class Notifier {
    public Notifier() { System.out.println("construct"); }
    public void send(String orderId) { System.out.println("order=" + orderId); }
}
```

入口创建 RootBeanDefinition 并登记它，暂时不 refresh、不 getBean。RootBeanDefinition 本身也是 Java 对象，但它描述的是另一个业务对象的创建规则。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            context.registerBeanDefinition("notifier", definition);
            System.out.println("class=" + definition.getBeanClassName());
            System.out.println("registered=" + context.containsBeanDefinition("notifier"));
            System.out.println("has-instance=" + context.getBeanFactory().containsSingleton("notifier"));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
class=cn.ningbingjian.learnjava.ioc.lesson006.practice.Notifier
registered=true
has-instance=false
```

输出中没有 construct。类已经存在，定义已经登记，但业务实例尚未创建。Bean 名 notifier 是注册表标识，定义保存创建信息，Notifier.class 描述类型，最终对象才负责处理通知。先从这个实际区别理解四者，再谈更多元数据字段。

### 1.3 小总结

定义是创建规则，实例是按规则取得的对象。登记成功只证明规则进入注册表，不能证明业务对象已创建或配置正确。

<a id="topic-2"></a>

## 2. 加上 refresh，观察默认单例何时创建

### 2.1 理论：默认单例的创建与缓存复用

本例使用默认 singleton 作用域，并且没有开启懒加载。上下文刷新中的预实例化流程会创建这类普通单例；后续按名字或类型请求该 Bean，可以复用已经创建的实例。

定义注册表与单例实例缓存承担不同职责：前者说明能按什么规则创建，后者保存已经创建并供复用的对象。containsBeanDefinition 与 containsSingleton 分别检查不同状态，不能互相替代。

这里描述当前普通单例的路径，不是说 refresh 必须创建每一种作用域的所有对象。后面会通过 lazy 和 prototype 逐项改变规则。

### 2.2 实操：逐步实现并解释原理

在原登记代码后只增加刷新与两次获取。先预测 construct 出现在 refresh 内还是第一次用户 getBean 时，再用引用比较核对后续是否复用。

只修改入口，在登记后刷新并重复获取通知器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            context.registerBeanDefinition("notifier", definition);
            System.out.println("before-refresh");
            context.refresh();
            System.out.println("after-refresh");
            var first = context.getBean(Notifier.class);
            var second = context.getBean(Notifier.class);
            System.out.println("same=" + (first == second));
            first.send("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
before-refresh
construct
after-refresh
same=true
order=O-006
```

construct 出现在刷新过程里。当前定义没有开启懒加载，默认单例在启动中创建，后续两次获取复用同一实例。不要把“注册定义”误记为“立即执行了构造器”，上一步已经把这两个时刻分开。

### 2.3 小总结

默认非懒加载单例在本例刷新期间创建；后续获取复用缓存。定义存在、实例存在和实例相同是三种不同判断。

<a id="topic-3"></a>

## 3. 修改 Java 创建要求，再补充构造参数与属性

### 3.1 理论：参数与属性元数据对应不同执行时点

通知器现在要求构造时确定渠道，再通过 setter 接收前缀。因此定义要分别表达构造参数和值属性，不能把两类输入都当成构造后随意写字段。

构造参数的索引0对应 Java 构造器的第一个参数；容器在取得实例前准备和匹配它。PropertyValues 中的 prefix 则保存可写属性名称和值，实例产生后再通过属性访问机制找到 setPrefix 并写入。

本例直接向定义提供 String。其他配置入口可能先提供带类型描述的值或对象引用，再由创建流程解析；定义中的“值”不保证已经等于最终传入业务方法的对象。来源描述只帮助诊断，不承担装载资源的工作。

### 3.2 实操：逐步实现并解释原理

先看 Java 构造器与 setter 的要求，再为定义补上对应条目。运行时将构造、属性设置和业务输出连接起来，避免只把 API 当成一组需要背诵的字段名。

现在通知器必须接收渠道名，并允许配置前缀。先修改类，再把这些要求写进定义；这样能看清每个元数据字段对应什么 Java 操作。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

public class Notifier {
    private final String channel;
    private String prefix = "";

    public Notifier(String channel) {
        this.channel = channel;
        System.out.println("construct:" + channel);
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public void send(String orderId) {
        System.out.println(channel + " " + prefix + " order=" + orderId);
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            context.registerBeanDefinition("notifier", definition);
            System.out.println("arguments=" + definition.getConstructorArgumentValues().getArgumentCount());
            System.out.println("properties=" + definition.getPropertyValues().size());
            System.out.println("source=" + definition.getResourceDescription());
            context.refresh();
            context.getBean(Notifier.class).send("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
arguments=1
properties=1
source=lesson006:practice
construct:email
email [orders] order=O-006
```

第0个构造参数负责创建时的渠道；prefix 属性负责对象创建后的 setter 填充。resourceDescription 只是我们提供的来源诊断标记，不表示存在同名文件。

这里元数据中的值是直接提供的字符串；XML 等其他入口还可能先保存 TypedStringValue、RuntimeBeanReference 等对象，后续再解析。不要把不同入口的原始元数据表达强行视为完全相同。

### 3.3 小总结

元数据字段对应实际调用：构造参数用于取得实例，属性值用于后续填充。定义值还可能需要解析和类型转换。

<a id="topic-4"></a>

## 4. 配置齐备后初始化，关闭时执行销毁

### 4.1 理论：初始化建立业务可用状态

构造器执行完，只能证明已有实例；如果对象还依赖 setter 配置，它未必能够执行完整业务。初始化回调位于属性填充之后，适合检查配置并建立可用状态。

本例的 initialize 将 ready 设为 true，send 在使用时检查 ready，shutdown 再撤销可用状态。这些状态规则由业务类实现；定义中保存方法名，容器据此安排调用时机。

当前单例创建完成后，容器还会按配置登记销毁处理。对当前上下文执行 close，才会触发相应清理。初始化和销毁是两个时点的管理工作，不能用“有这个方法”代替“已经配置并执行了回调”。

### 4.2 实操：逐步实现并解释原理

给类增加状态与回调后，再补定义中的初始化、销毁方法名。观察业务调用发生时 ready 已成立，退出上下文时执行 shutdown；如果配置错方法名，应按调用阶段定位。

现在要求通知器初始化后才允许发送。在类上增加 ready 状态和两个生命周期方法；当前用输出观察，不申请真实资源。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Notifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

public class Notifier {
    private final String channel;
    private String prefix = "";
    private boolean ready;

    public Notifier(String channel) {
        this.channel = channel;
        System.out.println("construct:" + channel);
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public void initialize() {
        ready = true;
        System.out.println("initialize:" + channel);
    }

    public void shutdown() {
        ready = false;
        System.out.println("shutdown:" + channel);
    }

    public void send(String orderId) {
        if (!ready) { throw new IllegalStateException("notifier is not ready"); }
        System.out.println(channel + " " + prefix + " order=" + orderId);
    }
}
```

再在原定义上补充方法名。对象上存在这些方法，不代表容器已经被告知何时调用它们。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            context.getBean(Notifier.class).send("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
construct:email
initialize:email
email [orders] order=O-006
shutdown:email
```

同一份定义现在描述了构造、属性填充、初始化和销毁。后面的实验只修改创建时机或作用域，业务类保留当前状态。

### 4.3 小总结

实例化、属性填充和初始化共同形成当前可用对象。容器调度回调，业务类负责回调实际实现的状态约束。

<a id="topic-5"></a>

## 5. 对象暂时用不到，再开启懒加载

### 5.1 理论：懒加载改变创建时机而非复用规则

懒加载允许当前单例在刷新预实例化时暂不创建，等它被实际需要时再走创建流程。定义仍然登记，容器仍然知道如何创建，只是尚未准备实例。

lazyInit 不会把 singleton 改成 prototype。首次需要时依然会执行构造、属性填充和初始化，创建成功后继续按单例复用。若该单例已创建并登记销毁回调，关闭时仍由容器清理。

延后创建也会延后某些配置错误或初始化成本的暴露时机。因此理解 lazy 时要同时问“谁在什么时候需要它”，而不只是看一个布尔值。

### 5.2 实操：逐步实现并解释原理

在刷新后先查 containsSingleton，避免这次观察本身触发创建。接着再 getBean，观察构造与初始化何时出现，把检查动作和使用动作分开。

只在定义登记前设置 lazyInit=true。入口在刷新后先检查单例缓存，再显式获取通知器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            definition.setLazyInit(true);
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            System.out.println("after-refresh=" + context.getBeanFactory().containsSingleton("notifier"));
            var notifier = context.getBean(Notifier.class);
            notifier.send("O-006");
            System.out.println("after-get=" + context.getBeanFactory().containsSingleton("notifier"));
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
after-refresh=false
construct:email
initialize:email
email [orders] order=O-006
after-get=true
shutdown:email
```

刷新后没有实例，第一次获取才触发构造和初始化；关闭时仍由容器销毁这个已创建的单例。懒加载改变当前实例的创建时机，不改变默认单例复用规则。

但“本例由 getBean 触发”不等于“只能由用户主动 getBean 触发”。接下来增加一个立即创建的服务来观察。

### 5.3 小总结

lazy 影响何时创建，scope 影响如何复用。当前单例可以延迟创建，但创建后的管理仍遵循单例规则。

<a id="topic-6"></a>

## 6. 服务需要它时，懒加载也会提前创建

### 6.1 理论：直接依赖会触发懒加载对象的创建

现在增加一个非懒加载服务，它在构造时必须得到 Notifier。容器要在刷新期间创建服务，就必须先满足这个参数，因此会获取通知器；这个需求足以触发懒加载单例的创建。

RuntimeBeanReference 在定义中保存目标名称，值解析阶段再向容器取真实对象。它没有在登记时立即 new 通知器，也没有为服务提供延迟访问代理。当前服务构造器需要的是一个已经可以使用的协作者。

所以 lazy 的含义是允许延后预实例化，不是无条件禁止启动期间创建。判断是否“提前”，应从依赖者的创建路径解释。

### 6.2 实操：逐步实现并解释原理

分别登记通知器定义与服务定义。追踪服务的参数索引0如何通过引用指向 notifier，再预测刷新后单例缓存为什么已有通知器。

新增服务，构造时接收通知器。我们不使用扫描或 @Bean，继续用同一套定义模型表达关系。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/OrderService.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

public class OrderService {
    private final Notifier notifier;

    public OrderService(Notifier notifier) {
        this.notifier = notifier;
        System.out.println("service.construct");
    }

    public void accept(String orderId) { notifier.send(orderId); }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            definition.setLazyInit(true);
            context.registerBeanDefinition("notifier", definition);
            var serviceDefinition = new RootBeanDefinition(OrderService.class);
            serviceDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference("notifier"));
            context.registerBeanDefinition("orderService", serviceDefinition);
            context.refresh();
            System.out.println("after-refresh=" + context.getBeanFactory().containsSingleton("notifier"));
            context.getBean(OrderService.class).accept("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
construct:email
initialize:email
service.construct
after-refresh=true
email [orders] order=O-006
shutdown:email
```

服务是默认非懒加载单例，启动时就需要协作者，于是通知器被创建。RuntimeBeanReference 保存的是依赖名称，解析时才得到实例；把它改成普通字符串 notifier，不能表达同一对象引用。

这个例子解释了为什么配置 lazy 后仍可能在启动时看见构造输出：先查谁需要它，再判断懒加载是否失效。

### 6.3 小总结

懒加载对象被非懒加载单例直接需要时，仍会在启动期间创建。先检查实际依赖链，再判断创建时机是否符合配置。

<a id="topic-7"></a>

## 7. 需要每次新对象，再改成 prototype

### 7.1 理论：prototype 的获取次数与清理责任

原型作用域（prototype）表示每次向容器请求该定义时创建新的实例。本例两次显式 getBean 会分别经历构造、属性填充和初始化，因此可以得到不同对象。

与单例不同，容器不会在上下文关闭时统一调用这些已交付原型对象的销毁回调，使用者需要安排清理。创建与初始化受到容器支持，并不等于交付后的完整生命周期也由它跟踪。

“每次请求新实例”还要明确请求对象是谁。如果只在创建单例服务时注入一次原型引用，后续服务使用字段不会自动再次 getBean。本节只验证入口明确获取两次的场景。

### 7.2 实操：逐步实现并解释原理

先去掉上一节服务定义，只修改通知器作用域。两次获取后关闭上下文，再由当前消费者手动 shutdown，观察不同创建次数与清理位置。

本步回到只有通知器的上下文，不登记上一节的服务。把作用域改为 prototype，并获取两次；关闭上下文后，再由消费者显式清理这两个对象。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        Notifier first;
        Notifier second;
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            definition.setScope("prototype");
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            System.out.println("after-refresh=" + context.getBeanFactory().containsSingleton("notifier"));
            first = context.getBean(Notifier.class);
            second = context.getBean(Notifier.class);
            System.out.println("same=" + (first == second));
        }
        System.out.println("context-closed");
        first.shutdown();
        second.shutdown();
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
after-refresh=false
construct:email
initialize:email
construct:email
initialize:email
same=false
context-closed
shutdown:email
shutdown:email
```

两次获取分别构造并初始化，实例不相同；销毁输出发生在我们手动调用 shutdown 之后，而非上下文关闭之前。prototype 的创建和初始化受容器支持，交付后的完整生命周期通常由使用者负责，不能照搬单例的自动销毁预期。

这不是“prototype 就完全不受 Spring 管理”，也不表示把 prototype 注入单例后，每次业务调用都会自动得到新对象。当前只验证按次获取，其他注入场景在作用域课程展开。

### 7.3 小总结

prototype 每次容器请求创建新对象，交付后的清理要明确责任。业务方法被调用多次不自动等于容器请求多次。

<a id="topic-8"></a>

## 8. 定义登记成功，不代表属性配置正确

### 8.1 理论：定义登记与创建校验为什么分开

注册表可以保存属性名和值，并不要求此时就完成业务对象的构造和属性填充。错误的 prefx 字符串可以先进入定义，直到真正写入属性时才发现目标类没有相应可写属性。

本例构造参数仍正确，所以可以先构造对象；随后属性填充失败，正常初始化无法继续完成。错误出现在哪个时点，取决于配置影响了哪个操作，不能统一归因于注册失败。

来源描述、属性名和目标类型共同帮助定位这个问题。创建过程中已有构造输出，也不能证明对象已成为可用 Bean。

### 8.2 实操：逐步实现并解释原理

只改属性名，保留其他创建条件。运行时先确认 registered，再看 construct 和初始化输出的边界；修复时只恢复 prefix，验证失败阶段的判断。

故意把 prefix 拼成不存在的 prefx，保持构造器参数和初始化方法不变。捕获当前属性填充失败的摘要。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.BeanCreationException;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefx", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            context.registerBeanDefinition("notifier", definition);
            System.out.println("registered=" + context.containsBeanDefinition("notifier"));
            try {
                context.refresh();
            } catch (BeanCreationException error) {
                System.out.println("root=" + error.getMostSpecificCause().getClass().getSimpleName());
                System.out.println("active=" + context.isActive());
            }
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
registered=true
construct:email
root=NotWritablePropertyException
active=false
```

你能看到构造已经发生，但初始化没有成功进入。定义的登记与实际创建校验处于不同阶段。排查时检查来源标记、属性名字与目标类，而不是仅凭 registered=true 判断配置正确。

现在把属性名改回 prefix，恢复正常运行。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Main {
    public static void main(String[] args) {
        try (var context = new GenericApplicationContext()) {
            var definition = new RootBeanDefinition(Notifier.class);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
            definition.getPropertyValues().add("prefix", "[orders]");
            definition.setResourceDescription("lesson006:practice");
            definition.setInitMethodName("initialize");
            definition.setDestroyMethodName("shutdown");
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            context.getBean(Notifier.class).send("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
construct:email
initialize:email
email [orders] order=O-006
shutdown:email
```

### 8.3 小总结

定义登记成功是前置条件，不是全部配置正确的证明。按构造、填充、初始化的实际失败位置排查。

<a id="topic-9"></a>

## 9. 回到 @Bean，比较相同效果的不同描述

### 9.1 理论：相同行为不要求相同元数据表达

把创建逻辑改成 @Bean 方法，可以得到相同通知效果，但其中部分规则由普通 Java 方法体执行。容器保存工厂方法入口和相关管理元数据，不会把方法体内的每条 setter 语句反向翻译成 PropertyValues。

因此手工定义有一个 prefix 属性条目，Java 配置的产品定义却可以没有对应 PropertyValues，而产品仍带有前缀。观察定义时必须结合创建入口：构造器、工厂方法或其他提供方式。

这也是为什么 beanClassName 一个字段未必足以描述最终产品类型。读元数据需要知道当前定义由哪个入口产生，不能套用上一种入口的全部表现。

### 9.2 实操：逐步实现并解释原理

先写在方法体里设置前缀的 JavaConfig，再同时打印定义属性数量与实际业务效果。解释 property-count 为0时，setPrefix 实际由哪一行 Java 调用完成。

新增 JavaConfig，把刚才同样的构造、前缀和生命周期写成工厂方法配置。这个方法体中的 setter 是普通 Java 调用。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/JavaConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JavaConfig {
    @Bean(initMethod = "initialize", destroyMethod = "shutdown")
    public Notifier notifier() {
        var notifier = new Notifier("email");
        notifier.setPrefix("[orders]");
        return notifier;
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson006/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson006.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(JavaConfig.class)) {
            var definition = context.getBeanFactory().getBeanDefinition("notifier");
            System.out.println("factory-method=" + definition.getFactoryMethodName());
            System.out.println("property-count=" + definition.getPropertyValues().size());
            context.getBean(Notifier.class).send("O-006");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 006-bean-definition-model compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson006.practice.Main
```

```text
construct:email
initialize:email
factory-method=notifier
property-count=0
email [orders] order=O-006
shutdown:email
```

通知效果一致，但 property-count 为0：Spring 保存工厂方法入口，没有把方法体中的 setter 逐句翻译为 PropertyValues。手工定义保存显式属性值，@Bean 让普通 Java 方法执行创建和设置，两条路径可以达到相同结果。

如果通过实例工厂创建产品，定义的 beanClassName 也未必直接代表最终产品类型。不要用某一个字段概括所有创建方式，下一课和 XML 课程还会继续比较不同注册入口。

### 9.3 小总结

定义描述创建入口，入口内仍可执行普通 Java 装配。运行结果相同，不意味着每个原始元数据字段都相同。

<a id="topic-10"></a>

## 10. 带着创建时机问题进入源码

### 10.1 理论：源码围绕定义、创建与时机阅读

前面每次修改都改变了一个可观察行为，现在可以按职责追源码。注册入口处理定义；获取入口决定复用还是创建；创建流程取得实例、填充属性并初始化。不要把这些不同阶段的方法只背成一条长调用链。

在当前普通 Bean 的路径中，AbstractBeanFactory.doGetBean 组织获取，AbstractAutowireCapableBeanFactory 的 doCreateBean 组织具体创建，内部的 populateBean 与 initializeBean 分别连接属性填充和初始化。有效定义是创建依据，不代表业务实例已经产生。

同一个构造器可能由刷新预实例化、用户获取或依赖解析触发。调用栈能解释这次由谁提出需求，单看一条 construct 日志则不够。

### 10.2 实操：逐步实现并解释原理

以第4节正常定义为基线，先在注册入口看 definition，再在自己的构造器、setPrefix、initialize 暂停。对应跟进 doCreateBean 中取得实例、populateBean、initializeBean 的位置；复现第5、6节时，再比较触发 doGetBean 的上层调用者。

在手工登记的步骤中，先在 GenericApplicationContext.registerBeanDefinition 观察 definition 中的渠道、prefix 和方法名，再在业务 Notifier 构造器暂停。此时传入构造器的已经是创建过程准备好的渠道参数。

沿 AbstractBeanFactory.doGetBean 观察当前 Bean 名与是否已有可复用实例；需要创建时，再跟到有效定义的获取和 AbstractAutowireCapableBeanFactory.doCreateBean。对当前普通对象，createBeanInstance 负责取得实例，随后 populateBean 填充 prefix，initializeBean 再组织初始化。这几个方法分别解决不同问题，不要把“实例已取得”当作“初始化已完成”。

第8节 prefx 写错的实验可以直接定位到属性填充：构造断点已经命中，填充阶段却无法找到可写属性，因而没有正常走完后续初始化。第9节换成工厂方法后，setPrefix 在 JavaConfig 方法体中执行；用调用栈即可解释它为什么不需要出现在产品定义的 PropertyValues 中。

观察懒加载时复现第5、6步，分别找到主动获取和被其他 Bean 依赖的调用栈。不要为了查看“有没有对象”在定义阶段主动求值 getBean，这会改变正在观察的创建时机。

源码：[AbstractBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java)、[AbstractAutowireCapableBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java)。

### 10.3 小总结

源码阅读既要确认正在处理定义还是实例，也要确认是谁触发了创建。把字段、分支和业务断点对应起来，才能解释运行行为。

<a id="course-summary"></a>

## 11. 本课总结

### 11.1 本课解决的问题

本课解释容器在业务实例产生之前，怎样保存创建与管理规则，以及这些规则如何决定构造、属性填充、初始化、创建时机和复用方式。

### 11.2 把原理串起来

注册时用名字关联 BeanDefinition，定义保存目标类型、参数和管理规则。容器获取对象时，结合有效定义与现有实例决定复用还是创建；创建期间准备依赖参数、取得实例、填充属性并初始化。已创建的当前单例在所属上下文关闭时执行约定清理。

lazy 允许延后单例预实例化，但非懒加载服务直接需要它时仍会触发创建。prototype 则改变每次容器请求的实例规则，并把交付后的清理交给使用者。换成 @Bean 后，方法体里的 Java 代码也能完成装配，不要求所有动作都体现为显式属性元数据。

### 11.3 核心结论与边界

| 概念 | 本课要点 |
| --- | --- |
| 类、名称、定义、实例 | 分别描述类型、注册标识、规则和运行对象 |
| 构造参数与属性值 | 分别对应取得实例前的参数准备和取得实例后的填充 |
| 初始化 | 在当前属性配置完成后建立业务可用状态 |
| lazy | 改变创建时机，不改变默认单例复用 |
| prototype | 每次请求创建，交付后的清理需明确责任 |
| 定义校验 | 登记成功不代表创建、填充和初始化都能成功 |
| 工厂定义 | 保存创建入口，不逐行翻译 Java 方法体 |

单例作用域不自动保证线程安全；原型注入一次也不代表每次业务调用都会重新获取。

### 11.4 如何应用与检查理解

排查对象为何创建、为何未创建时，同时看定义规则、实例缓存和实际依赖调用栈。排查配置错误时，再区分构造、属性和初始化阶段。

能解释“lazy 对象为何在刷新时出现”以及“PropertyValues 为0为何仍有前缀”，就已经把元数据与运行行为连接起来。

### 11.5 复习与后续学习

你已经逐项写出类、构造参数、属性、生命周期、懒加载与作用域，并观察了它们对实例的影响。元数据不是一张脱离运行的字段表，每一项都对应过具体创建行为。

第007课会让扫描器替你发现并登记定义；第008课再用 XML 表达创建规则，并观察父子定义如何合并。它们更换的是配置入口，仍会连接到这里建立的定义与实例模型。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-007](../007-component-scanning/README.md)。
