# 08-01-003 建立可运行、可调试的学习工程：接通第一个 Spring 容器

[模块目录](../README.md) · [本课大纲](../00-模块学习大纲.md#lesson-003) · [上一课](../002-ioc-di-object-assembly/README.md)

前两课的入口自己 new 通知器、再传给服务。这一课保留那两个对象的关系，先确认工程能独立编译，再把装配入口交给 Spring，最后在断点中观察创建过程。

这是普通 main 应用，没有 HTTP 端口和数据库，也没有 Spring Boot。运行完退出是正常行为。

本页按实际修改顺序跟写。文件路径相对于 `003-runnable-debuggable-spring`，命令在父目录 `08-01-spring-core-ioc` 执行，使用 JDK 21、Maven 3.9.x。跟写文件放在独立的 `cn.ningbingjian.learnjava.ioc.lesson003.practice` 包中，与仓库完成版示例分开；仍使用当前 Maven 子模块，不另建 POM。先创建当前步骤需要的文件，之后只替换明确指出的文件，其他文件保持上一步状态。

命令中的 `exec.mainClass` 选择你正在编写的入口；不传时仍运行仓库原有 DemoApplication。源码与输出都以课程固定的 Spring Framework 7.0.9 为基线。

## 1. 先确认实际使用的 Java 和 Maven

在终端运行 `java -version` 与 `mvn -version`，重点看 Maven 报告的 Java version 与 Java home。课程要求 JDK 21，命令行 Java、Maven 使用的 JDK 和 IDE 工程 SDK 应保持一致；只改 IDE 编辑器设置并不会改变终端 Maven 的运行环境。

在 IDE 中打开 `08-01-spring-core-ioc/pom.xml`，按 Maven 工程导入整个聚合项目。本课是其中的 003 子模块，不需要把每一课重新建成单独工程。

父 POM 使用 Spring BOM 固定 7.0.9；本课子 POM 已声明 spring-context，因此这里先核对依赖，而不是重复添加一份版本配置：

```bash
mvn -pl 003-runnable-debuggable-spring dependency:tree -Dincludes=org.springframework
```

确认 spring-context、spring-beans、spring-core 等 Spring 模块对齐到 7.0.9。依赖版本问题先在 Maven 层解决，不要等到启动报错后同时修改业务代码。

## 2. 先在新子模块复现上一课的普通装配

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

## 3. 创建最小配置，把两次 new 放到配置方法里

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

## 4. 验证服务里到底保存了哪个对象

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

## 5. 故意漏掉启动，再恢复正确顺序

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

## 6. 给通知器加上可观察的打开与关闭

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

## 7. 业务异常时也要释放资源

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

## 8. 下载匹配源码，真正命中一次断点

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

## 9. 带着可调试工程进入配置课程

现在已经有一条从编译、启动、获取对象到关闭的完整路径。遇到失败，先区分 JDK/Maven、依赖解析、上下文状态和业务异常，再选择排查入口。

下一课沿用这三个业务类型，在已能运行的容器中逐步增加第二个服务、拆分配置并选择不同通知渠道。

本页中间步骤已从空的 practice 目录逐一编译和运行核验，预期错误也有对应的修复步骤。完成后可查阅 [机制与边界参考](02-机制与边界参考.md)，对照原有完整源码和测试；首次学习按本页顺序推进即可。

下一课：[08-01-004](../004-java-config-object-assembly/README.md)。

