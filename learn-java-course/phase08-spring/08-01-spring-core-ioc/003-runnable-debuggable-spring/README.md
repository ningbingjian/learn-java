# 08-01-003 建立可运行、可调试的学习工程：接通第一个 Spring 容器

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-003) · [上一课](../002-ioc-di-object-assembly/README.md)

前两课的入口自己 new 通知器、再传给服务。这一课保留那两个对象的关系，先确认工程能独立编译，再把装配入口交给 Spring，最后在断点中观察创建过程。

这是普通 main 应用，没有 HTTP 端口和数据库，也没有 Spring Boot。运行完退出是正常行为。

本页按实际修改顺序跟写。文件路径相对于 `003-runnable-debuggable-spring`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson003.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

**本课读法：** 每节先理解当前问题和必要理论，再逐步修改与运行代码，在操作位置解释原理，最后用小总结收拢结论。课末另有[整课总结](#course-summary)，把各节知识连接起来。前面已学过的内容只作必要回顾，本课核心机制展开讲解。

<a id="topic-1"></a>

## 1. 先确认实际使用的 Java 和 Maven

### 1.1 理论：运行环境与依赖版本决定代码能否执行

Maven 已是前置知识，本节只核对当前工程实际使用的环境。IDE 的工程 SDK、终端 java 命令和启动 Maven 的 JDK 可能来自不同设置；编辑器能识别语法，不代表命令行一定能按相同版本编译运行。

父 POM 的 BOM 用来统一依赖版本约束，不会仅因为导入 BOM 就自动加入全部 Spring 模块。本课子 POM 声明 spring-context 后，Maven 再解析它及相关依赖。先核对解析结果，才能让后面的运行与源码阅读基于同一版本。

### 1.2 实操：逐步实现并解释原理

打开聚合 POM 后，分别检查 Maven 报告的 JDK 和依赖树中的版本。这一步先定位构建条件，不需要改业务 Java 类。

在终端运行 `java -version` 与 `mvn -version`，重点看 Maven 报告的 Java version 与 Java home。课程要求 JDK 21，命令行 Java、Maven 使用的 JDK 和 IDE 工程 SDK 应保持一致；只改 IDE 编辑器设置并不会改变终端 Maven 的运行环境。

在 IDE 中打开 `08-01-spring-core-ioc/pom.xml`，按 Maven 工程导入整个聚合项目。本课是其中的 003 子模块，不需要把每一课重新建成单独工程。

父 POM 使用 Spring BOM 固定 7.0.9；本课子 POM 已声明 spring-context，因此这里先核对依赖，而不是重复添加一份版本配置：

```bash
mvn -pl 003-runnable-debuggable-spring dependency:tree -Dincludes=org.springframework
```

确认 spring-context、spring-beans、spring-core 等 Spring 模块对齐到 7.0.9。依赖版本问题先在 Maven 层解决，不要等到启动报错后同时修改业务代码。

### 1.3 小总结

环境检查关注实际执行者：Maven 使用哪个 JDK，最终解析了哪些依赖。版本统一是复现运行与调试的基础。

<a id="topic-2"></a>

## 2. 先在新子模块复现上一课的普通装配

### 2.1 理论：用已知业务关系建立工程基线

第002课的构造器装配已经验证过。先在新子模块复现相同行为，是为了单独确认包名、源目录、依赖和运行入口都正确。

把已知可用的普通 Java 程序作为基线，下一步只改变装配入口，出现问题时就更容易判断变化来自哪里。复制类并修改包名不会自动启动 Spring，类路径中存在框架也不等于正在使用容器。

### 2.2 实操：逐步实现并解释原理

本步 Main 仍自己 new 邮件通知器和订单服务。运行时先确认选中的类是 lesson003.practice.Main，避免误跑仓库完成版而得到看似正确的输出。

沿用正常的构造器版服务和邮件渠道，保留唯一的业务关系，不复制上一课的实验类。

沿用第002课已经跟写完成的文件：`Notifier.java`、`EmailNotifier.java`、`OrderService.java`。从 `../002-ioc-di-object-assembly/src/main/java/cn/ningbingjian/learnjava/ioc/lesson002/practice/` 复制到本课 `src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/`，将这些文件的包声明及内部包引用中的 `lesson002` 改为 `lesson003`，其余内容先不改。这里复制的是你在上一课创建的 practice 文件；若尚未跟写，请先完成上一课对应步骤。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

public class Main {
    public static void main(String[] args) {
        var service = new OrderService(new EmailNotifier());
        service.accept("O-003");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
EMAIL order=O-003 accepted
```

这一步仍然没有创建 Spring 上下文，只确认复制后的包名、JDK、Maven 模块和入口都正确。下一步只改变对象组装方式。

### 2.3 小总结

先验证当前工程能运行已知逻辑，再接入容器，才能把构建问题与装配问题分开判断。

<a id="topic-3"></a>

## 3. 创建最小配置，把两次 new 放到配置方法里

### 3.1 理论：配置声明与容器启动怎样配合

这里首次接入实际容器，需要认识配置类、工厂方法和上下文三者的职责。@Configuration 表明这是配置来源，@Bean 标记用于提供对象的工厂方法，ApplicationContext 则组织配置处理、对象创建和后续管理。

注册 AppConfig 后，容器在刷新流程中解析配置，为 @Bean 方法登记产品定义。创建 orderService 时，容器先解析其 Notifier 参数，取得合适的通知器，再调用方法。方法体中的 new OrderService 仍是普通 Java 构造；变化在于谁组织并执行这次创建。

所以“把 new 移到配置里”只是代码形式，关键是入口启动容器，让容器按配置协调依赖。单独 new AppConfig 并普通调用方法，不等于走完同一套容器管理流程。

### 3.2 实操：逐步实现并解释原理

先读 notifier 方法的返回值，再看 orderService 的参数来源。Main 使用无参上下文，把 register 与 refresh 分开：前者提供配置入口，后者启动处理。观察时不要把二者当作同一个动作。

新增 AppConfig。先读第一个方法：它创建一个 Notifier 实现；第二个方法声明需要 Notifier，并用得到的参数构造服务。方法参数由容器提供，不是在方法体里再次 new 一个通知器。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/AppConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean
    public Notifier notifier() { return new EmailNotifier(); }

    @Bean
    public OrderService orderService(Notifier notifier) {
        return new OrderService(notifier);
    }
}
```

配置只描述装配入口，还需要创建和启动容器。替换 Main，把注册与刷新明确分成两行，方便稍后打断点。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            System.out.println("before-active=" + context.isActive());
            context.refresh();
            System.out.println("after-active=" + context.isActive());
            context.getBean(OrderService.class).accept("O-003");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
before-active=false
after-active=true
EMAIL order=O-003 accepted
```

业务消息没有变化，创建规则却已经由配置交给容器执行。无参上下文先建立容器对象，register 登记配置入口，refresh 处理配置并完成当前启动过程。

本例 @Bean 方法默认采用方法名作为 Bean 名，两个定义分别为 notifier 和 orderService。这里只用足够运行的配置形式，第004课再一步步拆开配置边界、名称与类型。

### 3.3 小总结

配置描述产品与依赖，容器执行注册解析和创建管理。业务对象仍是普通 Java 对象，协作关系由容器组织。

<a id="topic-4"></a>

## 4. 验证服务里到底保存了哪个对象

### 4.1 理论：用引用相等检查当前装配关系

消息相同只能说明行为相似，不能证明用了同一个通知器。两个独立邮件对象也可能打印完全相同的内容，因此要检查共享关系，需要比较引用。

Java 的 == 用于引用比较时，判断是否指向同一对象。本例分别比较服务保存的通知器与容器取出的通知器，以及同一 Bean 的两次获取结果。前者检查依赖装配，后者检查当前定义的单例复用。

这里的 singleton 是容器中某份定义的复用规则，不是给 Java 类施加全局唯一限制。

### 4.2 实操：逐步实现并解释原理

不要只看两个 true。先找到每一边引用从哪里来，再说明它验证的具体关系。带配置类的上下文构造器已触发刷新，本步不再重复调用 refresh。

配置能运行还不够。现在检查服务保存的协作者是否就是容器中的通知器，而不是某处额外 new 出的对象。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            var service = context.getBean(OrderService.class);
            var notifier = context.getBean(Notifier.class);
            System.out.println("same-notifier=" + (service.notifier() == notifier));
            System.out.println("same-service=" + (service == context.getBean(OrderService.class)));
            service.accept("O-003");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
same-notifier=true
same-service=true
EMAIL order=O-003 accepted
```

带配置类的构造器会完成注册和刷新，因此本步不再补 refresh。两个相等结果分别证明当前依赖引用一致、当前单例定义的获取结果复用；这不意味着同一个类在任意容器里都只能有一个对象。

### 4.3 小总结

行为相同与实例相同是不同证据。当前定义的复用要用引用关系验证，并明确所在容器范围。

<a id="topic-5"></a>

## 5. 故意漏掉启动，再恢复正确顺序

### 5.1 理论：上下文引用存在不代表启动完成

无参构造器先给出上下文对象，register 先登记配置入口。刷新流程尚未执行时，上下文还没有成为可按正常流程获取业务对象的活动容器。

因此 getBean 的失败可能来自容器状态，而不仅是目标定义缺失。本节故意省略 refresh，让这个时序约束显式出现，再在同一入口恢复正确顺序。异常名称需要结合消息和调用位置解释。

### 5.2 实操：逐步实现并解释原理

在第一次 getBean 前确认 Main 只执行了 register，随后再看 refresh 后同一个业务调用如何恢复。改变的是上下文启动状态，不是重新添加 Maven 依赖。

回到无参构造器，只 register 就 getBean，看看“有容器引用”和“容器已启动”有什么区别。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            try {
                context.getBean(OrderService.class);
            } catch (IllegalStateException error) {
                System.out.println("before-refresh=" + error.getClass().getSimpleName());
            }
            context.refresh();
            context.getBean(OrderService.class).accept("O-003");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
before-refresh=IllegalStateException
EMAIL order=O-003 accepted
```

同一个入口中先观察预期错误，再刷新并成功调用。错误不等于缺少 spring-context 依赖：代码已经编译，问题是当前上下文状态。真实排查应保留异常栈，不能只凭 IllegalStateException 这个通用名称判断根因。

### 5.3 小总结

容器对象、配置入口和可用业务对象分属不同状态。状态相关错误要沿启动顺序排查。

<a id="topic-6"></a>

## 6. 给通知器加上可观察的打开与关闭

### 6.1 理论：初始化与销毁让容器管理资源时机

前两课讨论如何取得协作者，本节增加另一个管理要求：何时准备资源、何时清理。初始化回调让对象在创建和依赖准备后完成约定的启动动作；销毁回调让受管理对象在关闭时释放资源。

本例通过 @Bean 的 initMethod 与 destroyMethod 指定方法，方法名称是配置内容。try-with-resources 管理的是上下文本身；退出代码块时先调用上下文 close，再由上下文组织当前单例的销毁。

关闭不会删除已有 Java 引用，也不保证对象自身拒绝后续调用。本节的 close 只打印事件，后续第006课才加入明确的可用状态。

### 6.2 实操：逐步实现并解释原理

先给对象增加可观察方法，再在配置里指定它们，最后让 Main 的 try 作用域负责关闭上下文。对照 open、业务输出、close 的相对位置，分别说明调用者是谁。

假设通知器需要准备和释放资源。先用输出代表这两个动作，在已有 EmailNotifier 上增加 open、close；send 的业务内容保持原样。这里不建立真实网络连接。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/EmailNotifier.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

public class EmailNotifier implements Notifier {
    public void open() { System.out.println("notifier.open"); }
    public void close() { System.out.println("notifier.close"); }

    @Override
    public void send(String message) {
        System.out.println("EMAIL " + message);
    }
}
```

然后在通知器 @Bean 上明确配置初始化与销毁方法。方法名本身不会使任意 Java 对象自动获得生命周期管理。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/AppConfig.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public Notifier notifier() { return new EmailNotifier(); }

    @Bean
    public OrderService orderService(Notifier notifier) {
        return new OrderService(notifier);
    }
}
```

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("business-start");
            context.getBean(OrderService.class).accept("O-003");
        }
        System.out.println("context-closed");
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
notifier.open
business-start
EMAIL order=O-003 accepted
notifier.close
context-closed
```

open 出现在业务前，close 出现在上下文退出时。try-with-resources 调用上下文 close，再由容器调用当前受管理单例的销毁回调；入口不必额外关闭通知器一次。

关闭也不等于立即垃圾回收。已经取出的引用仍是普通 Java 引用，本例的 close 只打印事件，并没有阻止它继续 send。生产资源关闭后能否再次使用，要看对象自身的状态约束。

### 6.3 小总结

容器根据生命周期配置调用对象方法，上下文的使用方负责关闭上下文。清理动作与内存回收、业务可用状态要分别理解。

<a id="topic-7"></a>

## 7. 业务异常时也要释放资源

### 7.1 理论：异常退出时资源作用域仍要结束

正常路径关闭成功，还不能证明异常路径也正确。Java 的 try-with-resources 会在离开资源块时执行关闭，包括代码块抛出异常的情况；外层 catch 随后才能处理传播出来的异常。

这次实验针对已经成功创建的上下文，以及业务区域抛出的异常。它没有模拟所有启动失败或关闭失败情况，不应将观察结果推广到未覆盖的路径。

### 7.2 实操：逐步实现并解释原理

业务调用后主动抛错，预测 close 与外层 catch 输出谁先出现。完成观察再恢复普通入口，保证下一节断点调试沿正常路径进行。

只改入口，在业务之后主动抛出一个异常，观察 Java 退出 try 之前是否关闭上下文。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try {
            try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
                context.getBean(OrderService.class).accept("O-003");
                throw new IllegalStateException("simulated business failure");
            }
        } catch (IllegalStateException error) {
            System.out.println("caught=" + error.getMessage());
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
notifier.open
EMAIL order=O-003 accepted
notifier.close
caught=simulated business failure
```

关闭事件先于外层 catch 输出。这是当前作用域管理资源的结果，不是建议生产入口把所有业务异常吞掉。完成观察后，把入口恢复为下面便于单步调试的形式。

文件：`src/main/java/cn/ningbingjian/learnjava/ioc/lesson003/practice/Main.java`。以下是本步该文件的完整内容。

```java
package cn.ningbingjian.learnjava.ioc.lesson003.practice;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            System.out.println("before-active=" + context.isActive());
            context.refresh();
            System.out.println("after-active=" + context.isActive());
            context.getBean(OrderService.class).accept("O-003");
        }
    }
}
```

运行当前这一步：

```bash
mvn -q -pl 003-runnable-debuggable-spring compile exec:java -Dexec.mainClass=cn.ningbingjian.learnjava.ioc.lesson003.practice.Main
```

```text
before-active=false
notifier.open
after-active=true
EMAIL order=O-003 accepted
notifier.close
```

### 7.3 小总结

资源管理要覆盖正常和异常退出。当前关闭来自语言作用域与容器管理的配合，并不要求业务方法自己清理容器。

<a id="topic-8"></a>

## 8. 下载匹配源码，真正命中一次断点

### 8.1 理论：源码调试用执行证据连接职责

调试不是按源码目录依次阅读。先提出一个具体问题，例如通知器工厂何时执行，再在自己的配置方法暂停，通过调用栈查看谁触发了它。

断点位置来自源码行号，执行的是已加载的二进制类，因此源码包应匹配实际依赖版本。下载 sources JAR 是为了对应阅读，不会替换当前运行的 Spring，也不会自动开启调试模式。

本节只跟通注册、刷新、工厂调用和关闭。刷新内部还有扩展处理，但当前先建立真实调用位置，后续再围绕各机制深入。

### 8.2 实操：逐步实现并解释原理

先附加匹配源码，然后用 Debug 运行当前 Main。断点暂停后查看变量与调用栈；不要为了观察对象主动执行 getBean，以免改变创建时机。

先为当前已解析依赖下载匹配源码：

```bash
mvn -pl 003-runnable-debuggable-spring dependency:sources -DincludeGroupIds=org.springframework
```

这会获取 sources JAR，不会重新编译 Spring。IDE 的 Maven 依赖中应同时对应 spring-context-7.0.9.jar 与 spring-context-7.0.9-sources.jar。若打开类仍只有反编译代码，重新加载 Maven 或附加这个已下载的源码包。

用 IDE 的 Debug 启动本课 practice.Main，不要启动原有 DemoApplication。按下面顺序设置断点，每轮只回答一个问题：

1. 在 Main 的 register 之后、refresh 之前暂停，观察 context.isActive 为 false，此时尚未进入 notifier 工厂方法。
2. 在 AppConfig.notifier 暂停，继续执行后观察实际返回的 EmailNotifier；再在 orderService 参数处确认拿到同一个对象引用。
3. 在 Spring 的 AbstractApplicationContext.refresh 暂停，查看调用栈中 Main 的位置，确认你调试的是本次上下文启动。
4. 在 EmailNotifier.close 暂停，继续退出 try，检查调用来自上下文关闭流程。

第一次追源码只需要连通“入口 → 配置创建 → 业务 → 关闭”，不要求立刻理解 refresh 的每一个扩展点。源码断点没有命中时先检查 main 入口、是否 Debug 启动、二进制与源码版本，而不是随意切换到另一版本源码。

源码：[AnnotationConfigApplicationContext](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/AnnotationConfigApplicationContext.java)、[AbstractApplicationContext](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java)。

### 8.3 小总结

有效的源码阅读要有问题、断点和运行证据。先连接自己写的入口与框架调用，再深入负责该问题的内部实现。

<a id="course-summary"></a>

## 9. 本课总结

### 9.1 本课解决的问题

本课把前两课的手工装配接入实际 Spring 上下文，并建立能够编译、运行、观察引用和命中源码断点的学习工程。

### 9.2 把原理串起来

首先确认 Maven 使用的 JDK 与最终依赖版本，保证当前入口运行的是预期代码。配置类提供 @Bean 工厂方法，注册把配置入口交给容器，刷新组织配置解析与对象创建。创建业务服务前，容器解析通知器参数，方法再完成普通 Java 构造。

取得对象后，业务方法依照原有规则运行。上下文退出资源作用域时执行关闭，并组织受管理单例的销毁。断点和调用栈把这些职责对应到真实执行位置，而不是只凭最后一条业务消息判断全部过程正确。

### 9.3 核心结论与边界

| 观察对象 | 可以说明什么 |
| --- | --- |
| Maven 环境与依赖树 | 实际构建和运行基线 |
| register、refresh 的位置 | 配置入口登记与启动时序 |
| 对象引用比较 | 当前服务使用谁、当前定义是否复用 |
| open、close 回调 | 本例初始化与清理是否在约定位置执行 |
| 调用栈与匹配源码 | 当前动作由哪条路径触发 |

本课是普通 main 程序，运行后退出正常。上下文关闭不等于引用消失，也不自动保证业务对象拒绝后续使用。

### 9.4 如何应用与检查理解

启动失败时先分清构建环境、依赖版本、容器状态和业务异常。源码断点未命中时，先核对入口、Debug 启动与版本，再分析框架逻辑。

能从 Main 跟到通知器创建、服务参数及关闭回调，并说出每一步的调用者，就具备继续深入容器机制的工程基础。

### 9.5 复习与后续学习

现在已经有一条从编译、启动、获取对象到关闭的完整路径。遇到失败，先区分 JDK/Maven、依赖解析、上下文状态和业务异常，再选择排查入口。

下一课沿用这三个业务类型，在已能运行的容器中逐步增加第二个服务、拆分配置并选择不同通知渠道。

本页代码、命令与输出沿用已经逐步编译运行核验的跟写版本，故意错误均有对应修复步骤。本次补充理论与总结，没有改变这些示例。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-004](../004-java-config-object-assembly/README.md)。
