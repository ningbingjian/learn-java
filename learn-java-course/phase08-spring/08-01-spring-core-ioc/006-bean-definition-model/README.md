# 08-01-006 BeanDefinition 的基本模型：逐项写出对象创建规则

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-006) · [上一课](../005-maintainable-container-tests/README.md)

前五课已经完成对象装配、配置切换与测试。现在追问：业务对象还没产生时，容器凭什么知道要创建哪个类、传什么参数、何时初始化？

我们仍使用通知场景，但先缩小为一个对象，不复制前面全部业务配置。先手工登记最小定义，再随着构造器、属性和生命周期需求逐项补充元数据，最后回到 @Bean 对照。

本页按实际修改顺序跟写。文件路径相对于 `006-bean-definition-model`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson006.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

## 1. 创建最小业务类，再只登记它的定义

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

## 2. 加上 refresh，观察默认单例何时创建

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

## 3. 修改 Java 创建要求，再补充构造参数与属性

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

## 4. 配置齐备后初始化，关闭时执行销毁

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

## 5. 对象暂时用不到，再开启懒加载

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

## 6. 服务需要它时，懒加载也会提前创建

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

## 7. 需要每次新对象，再改成 prototype

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

## 8. 定义登记成功，不代表属性配置正确

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

## 9. 回到 @Bean，比较相同效果的不同描述

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

## 10. 带着创建时机问题进入源码

在手工登记的步骤中，先在 GenericApplicationContext.registerBeanDefinition 观察定义，再在业务 Notifier 构造器暂停。沿 AbstractBeanFactory.doGetBean、getMergedLocalBeanDefinition 和 AbstractAutowireCapableBeanFactory 的创建过程，检查何时取得有效定义、何时构造和填充属性。

观察懒加载时复现第5、6步，分别找到主动获取和被其他 Bean 依赖的调用栈。不要为了查看“有没有对象”在定义阶段主动求值 getBean，这会改变正在观察的创建时机。

源码：[AbstractBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java)、[AbstractAutowireCapableBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java)。

## 11. 接上扫描与 XML 的学习主线

你已经逐项写出类、构造参数、属性、生命周期、懒加载与作用域，并观察了它们对实例的影响。元数据不是一张脱离运行的字段表，每一项都对应过具体创建行为。

第007课会让扫描器替你发现并登记定义；第008课再用 XML 表达创建规则，并观察父子定义如何合并。它们更换的是配置入口，仍会连接到这里建立的定义与实例模型。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-007](../007-component-scanning/README.md)。

