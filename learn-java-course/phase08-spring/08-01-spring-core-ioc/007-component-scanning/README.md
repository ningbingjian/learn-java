# 08-01-007 组件扫描与注解注册

[返回模块目录](../README.md) · [本课在大纲中的位置](../00-模块学习大纲.md#lesson-007) · [上一课：BeanDefinition 的基本模型](../006-bean-definition-model/README.md)

第006课直接登记了 BeanDefinition。这一课换一个入口：让 Spring 根据包范围与注解发现候选类，再把它们注册为定义。

需要弄清楚的不是“在类上加哪个注解就能运行”，而是范围、筛选、命名、注册和对象创建之间的关系。我们会运行正常扫描、过滤、漏扫、重复扫描、同名冲突及显式注册的对照场景。

学完后，你应能说明某个类为什么进入或没有进入容器，读懂扫描生成的名称，并判断问题发生在候选注册阶段还是后面的依赖装配阶段。

## 1. 先看业务依赖，再看包结构

本课业务很小：订单服务查询订单是否存在，存在则通过通知器发出受理消息。订单仓库在内存中预置 `O-001`；通知器只输出控制台，并记录本实例收到的消息，不连接数据库或外部通知服务。

业务依赖如下：

| 类型 | 作用 |
| --- | --- |
| `OrderNotificationService` | 根据订单是否存在决定是否发出通知 |
| `OrderRepository` | 查询订单存在性，当前由内存仓库实现 |
| `Notifier` | 通知能力，当前由控制台通知器实现 |

源码使用独立的 `lesson007` 包，继续继承 JDK 21、Spring Framework 7.0.9、JUnit 5.13.4 的工程基线。

| 包（省略共同前缀 `cn.ningbingjian.learnjava.ioc.lesson007`） | 放置内容 | 为什么这样放 |
| --- | --- | --- |
| `api` | `OrderRepository`、`Notifier` 接口 | 业务能力契约，不是待实例化的组件 |
| `app` 及子包 | 服务、仓库、通知器、工具类、包标记接口 | 正常扫描范围 |
| `config` | 四种扫描配置 | 显式选入其中一份，避免实验配置互相被扫描 |
| `outside` | 带组件注解的 `AuditReporter` | 验证范围外的组件不会自动进入正常扫描 |
| `collision.one`、`collision.two` | 两个简单类名都叫 `NotificationClient` 的组件 | 隔离复现名称冲突 |

完整文件见 [本课源码包](src/main/java/cn/ningbingjian/learnjava/ioc/lesson007)。不要把扫描根包直接扩大为整个 `lesson007`：这样会同时纳入实验配置与冲突样本，改变这次上下文要组装的应用。

## 2. @Component、@Service、@Repository 分别表达什么

本课三个主要实现类使用不同的角色注解：

| 类 | 注解 | 当前语义 |
| --- | --- | --- |
| `ConsoleNotifier` | `@Component("emailNotifier")` | 通用组件，并指定 Bean 名称 |
| `OrderNotificationService` | `@Service` | 标记业务服务角色 |
| `InMemoryOrderRepository` | `@Repository` | 标记数据访问角色 |

`@Service` 和 `@Repository` 本身带有 `@Component` 元注解，所以默认扫描规则可以将它们识别为组件候选者。元注解就是标注在另一个注解声明上的注解。角色注解并不要求你的业务类继承某个 Spring 基类。

也不要把角色标记当成已经启用了一整套功能：`@Service` 不会自行开启事务；`@Repository` 不会自动生成数据库访问代码。持久化异常转换等能力需要相应基础设施配合，本例只有内存仓库，没有配置那条处理链。注解角色与扫描基础可参考 [Spring 组件扫描文档](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)。

服务通过唯一的构造器表达两个依赖：

```java
public OrderNotificationService(OrderRepository repository, Notifier notifier) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.notifier = Objects.requireNonNull(notifier, "notifier");
}
```

这个类只有一个构造器，本例不需要额外标注 `@Autowired`。需要注意的是，扫描发现服务类和解析构造器参数是不同工作：先有候选定义，随后在创建实例时才需要把仓库与通知器传进来。

## 3. 用包标记明确扫描范围

正常配置 [DefaultScanConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/config/DefaultScanConfig.java) 为：

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class)
public class DefaultScanConfig {
}
```

`AppComponents` 是放在 `app` 包中的空接口，没有组件注解。它只是一个包位置标记。

`basePackageClasses = AppComponents.class` 表示扫描这个类型所在的包及其子包，**不是仅注册 `AppComponents` 这一个类型**。本例会覆盖 `app.delivery`、`app.data`、`app.service` 和 `app.utility`，不会向上扫描父包，也不会自动扫描并列的 `outside`。

使用类型引用可以减少手写包名拼错和重构时遗漏字符串的问题。也可以使用 `basePackages` 写包名；如果没有指定范围，`@ComponentScan` 默认从声明它的配置类所在包开始。默认规则不是“从 main 方法所在包开始”，更不是“扫描整个项目”。范围参数见 [ComponentScan 源码说明（v7.0.9）](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ComponentScan.java)。

当前选择配置类的代码是 `context.register(config)`，随后调用 `refresh()`。`@ComponentScan` 是交给容器处理的元数据，它不会在 Java 类加载后自行执行扫描。

## 4. 先运行正常场景，确认到底注册了什么

以下命令均在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn clean verify
mvn -q -pl 007-component-scanning exec:java -Dexec.args=scan
```

```text
before-refresh.has-service=false
app-beans=[URLCodec, emailNotifier, inMemoryOrderRepository, orderNotificationService]
EMAIL order=O-001 accepted
accepted=true
outside-included=false
```

`before-refresh.has-service=false` 在配置类注册之后、刷新之前输出；此时还没有处理该配置中的组件扫描。刷新过程中发现组件并登记定义，然后完成当前非懒加载单例的创建与装配。

`app-beans` 是入口按业务包范围筛选、排序后的**定义名称列表**。它不是容器中全部 Bean 的清单：配置类和内部基础设施没有被这份输出列出。

业务调用返回 `true`，表示内存仓库中存在 `O-001`，通知器收到了消息。`AuditReporter` 虽有 `@Component`，但位于范围外，因此 `outside-included=false`。

同样位于 `app.utility` 中的 `PlainHelper` 没有组件注解，所以默认没有被选中。一个类在类路径中、一个类在扫描包内、一个类满足候选规则，是三个不同条件。

## 5. Bean 名称：显式指定与默认生成

正常场景中的名称并不都采用“类名首字母转小写”这种简单处理：

| Java 类名 | 注解中的名字 | 本例 Bean 名 |
| --- | --- | --- |
| `ConsoleNotifier` | `emailNotifier` | `emailNotifier` |
| `InMemoryOrderRepository` | 未指定 | `inMemoryOrderRepository` |
| `OrderNotificationService` | 未指定 | `orderNotificationService` |
| `URLCodec` | 未指定 | `URLCodec` |

显式名称优先，所以 `ConsoleNotifier` 没有采用默认的 `consoleNotifier`。`URLCodec` 的前两个字母都是大写，默认命名保留其开头，结果不是 `uRLCodec`。

这是当前 `AnnotationBeanNameGenerator` 的命名规则，可以对照 [固定版本源码](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/AnnotationBeanNameGenerator.java)。本课不修改命名生成器时，显式注册这些组件类也会识别它们的组件名称。

名称是注册表中的标识，接口类型用于依赖关系。服务构造器参数叫 `notifier`，当前候选名字叫 `emailNotifier`，唯一合适的通知器仍能被解析。多个候选者怎样选择会在依赖解析课程进一步展开。

## 6. 排除一个不影响业务的组件

[ExcludeUtilityConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/config/ExcludeUtilityConfig.java) 保留默认规则，只排除 `URLCodec`：

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = URLCodec.class))
public class ExcludeUtilityConfig {
}
```

`ASSIGNABLE_TYPE` 根据类型可赋值关系匹配。这里的 `URLCodec` 是 final 类，因此本例匹配的就是它；这不是根据 Bean 名字符串排除。

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=exclude
```

```text
before-refresh.has-service=false
app-beans=[emailNotifier, inMemoryOrderRepository, orderNotificationService]
EMAIL order=O-001 accepted
accepted=true
outside-included=false
```

业务依赖不需要 `URLCodec`，所以移除它后应用仍然能运行。如果排除的是必需的仓库或通知器，就不能只看候选列表变少了，还必须检查业务装配是否仍完整。

## 7. includeFilters 默认是补充，不是只保留

[IncludeOnlyConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/config/IncludeOnlyConfig.java) 明确关闭默认过滤器，只选择两个类型：

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {ConsoleNotifier.class, PlainHelper.class}))
public class IncludeOnlyConfig {
}
```

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=include-only
```

```text
before-refresh.has-service=false
app-beans=[emailNotifier, plainHelper]
helper=plain-helper
outside-included=false
```

`PlainHelper` 没有 `@Component`，但符合这次显式包含规则，而且是可实例化的独立类，所以被注册为 Bean。由此可以看出：“必须有 `@Component` 才可能被扫描注册”并不成立，关键取决于实际过滤器。

这次没有选择订单服务，也就没有触发它的仓库依赖。这个模式用于检查筛选结果，不负责运行完整订单业务。

如果删除 `useDefaultFilters = false`，默认组件规则仍然存在，`includeFilters` 会在其基础上增加候选者。配套测试使用只增加 `PlainHelper` 的配置，确认服务、仓库和 `URLCodec` 仍会被选中。不要把 `includeFilters` 的属性名直接理解为“其他类型全部排除”。

常见过滤器的边界如下：

| `FilterType` | 按什么判断 | 使用时要注意什么 |
| --- | --- | --- |
| `ANNOTATION` | 注解类型及其匹配规则 | 例如按 `Repository.class` 筛选角色 |
| `ASSIGNABLE_TYPE` | 类型可赋值关系 | 当前用于具体类型，不依赖名称拼写 |
| `REGEX` | 类名称的正则匹配 | 不是根据最终 Bean 名匹配 |
| `ASPECTJ` | AspectJ 类型模式 | 需要对应支持，当前工程没有使用 |
| `CUSTOM` | 自定义 `TypeFilter` | 匹配逻辑与维护成本由扩展代码承担 |

过滤条件也不能扩大基础扫描范围：范围外的 `AuditReporter` 不会因为你增加一个包含规则，就自动从并列包中被找出来。实际候选还需要通过后续资格检查；只匹配过滤器，不代表任意接口或不适合独立实例化的类型都会成为普通组件。

## 8. 漏扫：服务找到了，依赖却没有找到

[NarrowScanConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson007/config/NarrowScanConfig.java) 使用服务类作为包位置：

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = OrderNotificationService.class)
public class NarrowScanConfig {
}
```

这表示扫描 `app.service` 及其子包，不包含并列的 `app.data` 和 `app.delivery`。扫描器不会因为读取到构造器参数，就自动追踪接口实现并扩大到其他包。

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=narrow
```

```text
failed-bean=orderNotificationService
cause=UnsatisfiedDependencyException
missing-type=cn.ningbingjian.learnjava.ioc.lesson007.api.OrderRepository
active=false
```

Spring 还会打印取消刷新的警告。示例捕获预期错误并输出摘要；测试断言失败 Bean 名、根因类型和缺失接口，不依赖时间戳或整段日志的文字格式。

当前首先失败的是服务构造器第0个参数 `OrderRepository`。完整范围下的内存实现位于 `app.data`，本次没有扫描到它。通知器所在包也没有选入，但容器会在遇到未满足的依赖时失败，不会保证一次日志就列出所有缺失依赖。

此时应依次检查：实际基础包、实现类所在位置、实现类注解、过滤器，以及这次真正选入的配置。不要把扩大到整个公司根包作为默认修复，那可能把其他应用或测试组件一起纳入。

## 9. 重复扫描同一个包，会发生什么

为了直接观察扫描时刻，这个模式使用编程式 `context.scan(...)`，连续扫描相同包两次：

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=repeat
```

```text
first-scan.count=4
second-scan.count=4
same-definition=true
before-refresh.has-singleton=false
same-service=true
```

第一次 `scan` 返回时，业务定义已经登记；第二次遇到同一来源、同一名称的兼容定义，本例保留既有定义。因此计数不增加，读取到的服务定义仍是同一个对象。

随后刷新上下文，按类型重复获取默认单例服务，返回同一个实例。这与第006课的区别一致：定义去重和单例复用不是同一个动作，发生阶段也不同。

| 入口 | 本例中扫描发生的时机 |
| --- | --- |
| `register(DefaultScanConfig.class)` 中的 `@ComponentScan` | 刷新时解析配置类，进而触发扫描 |
| `context.scan(packageName)` | 调用扫描方法时直接发现、注册候选定义；后续仍需刷新 |

本例验证的是同一个上下文、同一批类、相同命名与扫描规则。它不证明任意重复配置都安全：改变命名策略、跨上下文注册、混合其他定义来源，可能得到不同结果。

## 10. 不同类产生同名 Bean，是另一种问题

两个冲突样本分别是：

- `collision.one.NotificationClient`
- `collision.two.NotificationClient`

它们是两个不同 Java 类，都标记了不带显式名字的 `@Component`。默认命名只根据简单类名生成名字，因此都尝试使用 `notificationClient`。

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=collision
```

```text
scan-error=ConflictingBeanDefinitionException
mentions-both-classes=true
active=false
```

错误在 `context.scan(...)` 期间发生，尚未执行 `refresh()`，也没有进入业务依赖注入。本例新候选与已有扫描定义不同且不兼容，扫描器拒绝使用同一个名字登记它们。

诊断输出只检查异常是否提到了两类，避免依赖“文件 A 一定先被扫描”这种顺序假设。实际排查要保留完整类名与定义来源，不能只看日志中的两个简单类名都一样。

| 场景 | 判断依据 | 本例结果 |
| --- | --- | --- |
| 相同类按相同规则重复扫描 | 名称相同，来源/定义兼容 | 保留原定义 |
| 不同包的同名类被扫描 | 默认名称相同，定义不兼容 | 扫描阶段报错 |
| 不同名称的两个同接口实现 | 名称未必冲突 | 之后按单一接口注入仍可能出现候选歧义 |

最后一种情况不是当前冲突样本，但第004课已经演示过类型候选不唯一。修复名称冲突与决定注入哪个实现，是两个需要分别处理的问题。

## 11. 用完整类名处理默认命名碰撞

如果两个组件都需要存在，可以根据业务角色显式命名，或者为合适的扫描边界统一采用命名生成器。当前提供后一种对照：

```java
context.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
```

这行代码在 `scan` 之前执行。它让未显式命名的扫描组件使用完整类名作为默认 Bean 名。

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=qualified
```

```text
first-name-exists=true
second-name-exists=true
same-instance=false
```

两个样本现在分别通过完整名称登记，得到两个不同对象。默认命名策略仍尊重显式注解名称；如果两个类都显式写了同一个名字，不能指望它自动改名解决冲突。

修改命名策略也会影响按名称查找、名称限定及其他依赖名字的配置。它不应被当成全项目无成本开关，应明确应用在哪个扫描范围和启动入口上。

本课没有通过打开覆盖开关来掩盖冲突。两个不同组件是否都应该存在，需要由应用边界决定，而不是让扫描顺序偶然决定保留谁。

## 12. 注解、扫描器、处理器分别做什么

可以把当前过程拆开理解：

| 环节 | 输入与结果 |
| --- | --- |
| 组件角色注解 | 声明候选标记或显式名称，本身不运行构造器 |
| `@ComponentScan` 配置 | 提供扫描包、过滤规则、命名策略等信息 |
| 配置解析过程 | 读取当前配置中的扫描声明，调用扫描器 |
| 候选发现 | 读取类路径资源与类型元数据，判断是否满足规则 |
| 扫描注册 | 为候选创建定义、生成名字、检查冲突并登记 |
| 后续容器创建过程 | 根据已注册定义选择构造器、解析依赖并初始化对象 |

默认扫描可以通过类文件元数据识别许多候选信息，不需要为了检查注解就先构造每一个业务对象。但不要把它夸大为“扫描过程中绝不会加载任何类”：具体过滤器、类型解析和扩展路径仍可能涉及类加载。类加载也不等于创建业务实例。

扫描得到的定义与第006课的模型衔接：类名、作用域、来源等信息先被保存，业务实例随后再创建。普通扫描组件的构造器参数还需要在创建时解析，不能仅靠 `getConstructorArgumentValues()` 的显式值数量判断有无依赖。

本例使用 `AnnotationConfigApplicationContext` 提供的注解配置基础设施，没有自己实现注解处理器。更完整的配置解析、注解注入与后处理器执行顺序在后续源码系列展开。

## 13. 沿真实候选进入源码

固定使用 Spring **v7.0.9**，先围绕本课的一个服务类和一个冲突名字观察，不必一次跟完整个扫描过程。

1. 使用 `scan` 模式，沿配置处理进入扫描入口；或者使用 `repeat`、`collision` 模式，从显式 `context.scan` 直接进入。
2. 在 `ClassPathScanningCandidateComponentProvider.findCandidateComponents` 观察实际基础包。继续到 `isCandidateComponent(MetadataReader)`，检查排除与包含过滤器；固定版本实现先检查排除规则，再检查包含规则及相关条件。
3. 进入 `ClassPathBeanDefinitionScanner.doScan`，观察候选定义中的完整类名，以及命名生成器产出的 Bean 名。两者不是同一个字符串。
4. 在 `checkCandidate(beanName, beanDefinition)` 观察注册表中是否已有该名字。`repeat` 模式查看兼容判断后跳过新定义的分支；`collision` 模式查看不同类同名时抛错的分支。
5. 在显式调用 `context.scan` 的 `repeat` 模式中，对正常候选继续到定义注册，再返回应用代码，在刷新前检查定义存在、业务单例尚未创建。配置注解触发的扫描则发生在 `refresh()` 内部，不能等整个刷新返回后再把状态当作“刷新前”。

源码入口：[候选发现器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathScanningCandidateComponentProvider.java)、[扫描与注册器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/ClassPathBeanDefinitionScanner.java)、[默认命名生成器](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/context/annotation/AnnotationBeanNameGenerator.java)。

在 `checkCandidate` 中，可以比较 `beanName`、新定义的类名、已有定义的类名和来源。不要在这个阶段为了“看看对象”就求值 `getBean`，那会把创建行为引入正在观察的注册阶段。

扫描器使用具体实现中的兼容性判断，不能把源码里一个 `return false` 简化为“没有扫描到”。在本例重复扫描分支，它表示不用再次注册，因为已有兼容定义；判断依据必须结合上下文来看。

## 14. 显式注册与扫描注册如何选择

最后一个模式直接列出与正常扫描相同的组件类：

```java
try (var context = new AnnotationConfigApplicationContext(OrderNotificationService.class,
        InMemoryOrderRepository.class, ConsoleNotifier.class, URLCodec.class)) {
    System.out.println("app-beans=" + applicationBeanNames(context));
    System.out.println("accepted=" + context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
}
```

```bash
mvn -q -pl 007-component-scanning exec:java -Dexec.args=explicit
```

```text
app-beans=[URLCodec, emailNotifier, inMemoryOrderRepository, orderNotificationService]
EMAIL order=O-001 accepted
accepted=true
```

这段入口没有执行包扫描，但仍使用注解配置上下文注册和处理这些明确给出的类。显式注册不等于关闭全部注解支持。

| 比较项 | 显式列出组件类 | 组件扫描 |
| --- | --- | --- |
| 范围在哪里体现 | 启动入口或配置中列出的类型 | 包结构、扫描声明与过滤规则 |
| 新增一个组件 | 需要检查并更新注册列表 | 满足范围和规则时可能自动进入容器 |
| 排查没有注册 | 查注册列表及导入路径 | 还要查包位置、候选资格和过滤器 |
| 常见风险 | 遗漏新增类型或依赖 | 扫描过宽、意外引入组件、默认名称冲突 |
| 当前例子的结果 | 相同组件得到相同业务行为 | 相同组件得到相同业务行为 |

小型测试上下文和明确的模块边界适合显式注册；组织稳定、组件较多的业务包可以采用受控扫描。无论选择哪种方式，都应能回答“本次上下文为什么包含这个类”。

## 15. 十个测试与排查顺序

[ComponentScanningTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson007/ComponentScanningTest.java) 验证：

1. 配置触发扫描、默认与显式名称以及业务依赖装配。
2. 默认规则不纳入普通无注解类和包范围外组件。
3. 排除工具组件后，必需业务关系仍可运行。
4. 关闭默认过滤器后，只选择显式包含的类，包括无注解类。
5. 保留默认过滤器时，包含规则是新增候选而不是收窄范围。
6. 只扫描服务包时，错误准确指向缺失仓库依赖。
7. 相同扫描保留兼容定义，刷新前不产生业务单例。
8. 不同类产生相同默认名称，在扫描阶段冲突。
9. 完整类名策略允许两个未显式命名的同名类并存。
10. 显式注册同一组组件时保留对应名称和装配行为。

只测试本课：

```bash
mvn -pl 007-component-scanning -am test
```

修改后重新编译并运行：

```bash
mvn -q -pl 007-component-scanning compile exec:java -Dexec.args=scan
```

第001—007课聚合构建共 **63 个测试**。漏扫场景故意触发 Spring 警告，测试以断言是否满足为准；正常构建不代表每个演示模式都必须成功启动业务上下文。

排查时建议按当前失败阶段选择入口：扫描时冲突先查名字和定义来源；刷新时依赖不满足先查候选是否被选入；启动后按名字查找失败先核对命名规则。不要把所有情况都处理成“再加一个注解”。

可以自行做三个单变量练习，仓库保留的是上述正常与隔离对照实现：

- 把 `ConsoleNotifier` 的显式名称改掉，比较按接口装配与按旧名称查询的结果。
- 将 `ExcludeUtilityConfig` 的排除类型改为 `InMemoryOrderRepository`，预测错误阶段和缺失类型，再运行验证。
- 在 `IncludeOnlyConfig` 中恢复默认过滤器，检查候选列表增加了哪些类，并解释原因。

下一课：[08-01-008 XML 配置与定义继承](../00-模块学习大纲.md#lesson-008)。我们会继续比较不同注册入口怎样表达同一个定义模型，并区分定义继承、Java 类继承和父子容器。
