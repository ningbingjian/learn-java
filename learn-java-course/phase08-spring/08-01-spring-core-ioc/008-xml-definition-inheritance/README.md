# 08-01-008 XML 配置与定义继承

[返回模块目录](../README.md) · [本课在大纲中的位置](../00-模块学习大纲.md#lesson-008) · [上一课：组件扫描与注解注册](../007-component-scanning/README.md)

第006课直接构造 BeanDefinition，第007课通过组件扫描注册定义。这一课使用 XML 描述对象装配，再观察父定义中的公共配置怎样与子定义合并。

学习 XML 的价值，一是能够维护已有系统，二是把“配置从哪里来”和“容器如何使用配置”连接起来。看见 `parent="notifierTemplate"` 时，你应知道这是定义模板关系；看见 `factory-bean` 时，你应知道要找的是哪个工厂对象，而不是直接按 `class` 实例化产品。

本课沿用 JDK 21、Spring Framework 7.0.9、JUnit 5.13.4 和 Maven 聚合工程。所有例子都在独立的 `lesson008` 包中，无需数据库、网络通知服务或历史课程的 JAR。

## 1. 先把要装配的对象关系说清楚

当前业务是记录一次订单受理通知。`OrderService` 接收一个 `RouteNotifier`；通知器通过构造器接收渠道名与 `DeliveryLog`，通过 setter 接收前缀、收件人列表和附加字段。

| 源码 | 职责 | 与本课的关系 |
| --- | --- | --- |
| [OrderService.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/OrderService.java) | 把订单编号交给通知器 | 验证 `ref` 是否连接到容器中的那个对象 |
| [RouteNotifier.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/RouteNotifier.java) | 保存渠道配置，初始化后记录通知 | 同时观察构造器、属性、集合与生命周期配置 |
| [DeliveryLog.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/DeliveryLog.java) | 保存通知记录和生命周期事件 | 每个上下文拥有自己的观察记录 |
| [NotifierFactory.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/NotifierFactory.java) | 使用实例方法创建通知器 | 对照静态工厂与实例工厂 |
| [XmlContexts.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/XmlContexts.java) | 读入 XML，保留尚未刷新的上下文 | 分开观察定义加载和对象创建 |
| [DemoApplication.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/DemoApplication.java) | 提供十个运行模式 | 输出每个场景真正需要观察的状态 |

这里的“通知”只是向内存列表写入字符串。`email`、`sms` 是演示渠道名，`ops@example.test` 是样例收件人；不会发送真实邮件或短信。记录器用于单线程学习场景，不承担并发日志系统的职责。

通知器有两个时间上的要求：调用 `initialize()` 时必须已有收件人；调用 `send()` 时必须已经初始化。`shutdown()` 则把它置为不可用并记录关闭事件。这让我们可以观察容器是否按有效配置完成了对象准备，而不只是断言“能拿到一个对象”。

## 2. 第一份 XML：构造器、对象引用与属性

完整配置见 [xml-basics.xml](src/main/resources/lesson008/xml-basics.xml)。它位于 Maven 的 `src/main/resources` 下，构建后会进入类路径。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="deliveryLog" class="cn.ningbingjian.learnjava.ioc.lesson008.DeliveryLog"/>
    <bean id="notifier" class="cn.ningbingjian.learnjava.ioc.lesson008.RouteNotifier"
          init-method="initialize" destroy-method="shutdown">
        <constructor-arg index="0" value="email"/>
        <constructor-arg index="1" ref="deliveryLog"/>
        <property name="prefix" value="[orders]"/>
        <property name="recipients">
            <list><value>ops@example.test</value></list>
        </property>
        <property name="headers">
            <map><entry key="region" value="cn"/></map>
        </property>
    </bean>
    <bean id="orderService" class="cn.ningbingjian.learnjava.ioc.lesson008.OrderService">
        <constructor-arg ref="notifier"/>
    </bean>
</beans>
```

先沿着服务的依赖往下读：`orderService` 的构造器参数引用 `notifier`，通知器的第1个构造器参数引用 `deliveryLog`。构造器索引从0开始，所以第0个参数是字符串渠道名，第1个参数才是日志对象。

`value="deliveryLog"` 和 `ref="deliveryLog"` 不是两种写法的同义词。前者提供待转换的字面值，后者保存一个命名对象引用，在创建目标 Bean 时再解析到实例。本例的构造器需要 `DeliveryLog` 对象，不能用名字字符串代替。

`<property name="prefix">` 对应 JavaBean 写入属性，即本例的 `setPrefix(...)`。它并不是任意私有字段的直接赋值指令。拼错属性名或让目标类缺少相应可写属性，会在属性填充时失败，后面有隔离样例。

`list` 的内容传给 `setRecipients(List<String>)`，`map` 的键值传给 `setHeaders(Map<String, String>)`。当前都是字符串；遇到数字、枚举或对象引用时，还应核对目标类型和转换规则。XML 标签本身不能替代 Java 类型约束。

文件头中的命名空间用于识别元素，`schemaLocation` 为命名空间关联 XSD。这个标准 Spring beans schema 在本工程中可以通过 Spring JAR 的映射解析，已用离线 Maven 运行验证；不要由此推断任意自定义 schema 都不需要外部资源。

## 3. 把“加载定义”和“刷新上下文”分开

入口工具的核心代码如下：

```java
public static GenericApplicationContext load(String resource) {
    var context = new GenericApplicationContext();
    new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:lesson008/" + resource);
    return context;
}
```

这个方法只负责把指定资源读入上下文的注册表，调用方随后决定何时 `refresh()`。因此它适合本课观察原始定义和合并定义。

以下命令均在 `08-01-spring-core-ioc` 目录执行：

```bash
mvn clean verify
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=xml
```

```text
lifecycle.before=[init:email]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
same-reference=true
lifecycle.after=[init:email, close:email]
```

`lifecycle.before` 在刷新完成后输出，说明通知器已经执行初始化。服务调用后，`deliveries` 展示渠道、前缀、订单号、列表和 Map 的实际内容。

`same-reference=true` 表示服务持有的对象就是容器中名为 `notifier` 的对象。退出 try-with-resources 时，上下文关闭，`shutdown()` 被调用，所以最后多出 `close:email`。

本例对象的顺序可以理解为：构造通知器、填充配置属性、执行初始化，然后供业务调用；上下文关闭时销毁其管理的单例。定义加载阶段没有执行这套业务对象生命周期。

如果直接使用 `new ClassPathXmlApplicationContext("lesson008/xml-basics.xml")`，常用的这个构造器会完成加载和刷新。不要拿它构造完成后的状态，与这里 `XmlContexts.load(...)` 返回时的状态直接比较：两者完成的工作不同。本课特意分开这两个步骤。

## 4. 从重复配置提取父定义模板

假设邮件和短信通知器共用日志对象、默认前缀、部分附加字段及生命周期方法，只有渠道和收件人不同。可以复制两段 XML，也可以通过定义继承明确哪些配置共同维护。

完整配置见 [xml-inheritance.xml](src/main/resources/lesson008/xml-inheritance.xml)，其中模板为：

```xml
<bean id="notifierTemplate" class="cn.ningbingjian.learnjava.ioc.lesson008.RouteNotifier" abstract="true"
      lazy-init="true" init-method="initialize" destroy-method="shutdown">
    <constructor-arg index="0" value="template"/>
    <constructor-arg index="1" ref="deliveryLog"/>
    <property name="prefix" value="[orders]"/>
    <property name="recipients">
        <list><value>ops@example.test</value></list>
    </property>
    <property name="headers">
        <map>
            <entry key="region" value="cn"/>
            <entry key="source" value="course"/>
        </map>
    </property>
</bean>
```

`abstract="true"` 表示这份定义用于模板，不能直接请求实例。它与 Java 的 `abstract class` 无关：本例 `RouteNotifier` 反而是 `final` 类。

模板中的 `lazy-init="true"` 是特意保留的观察变量，后面会验证它是否传播到子定义。模板不被创建的决定性条件是定义的 `abstract` 标志；单独使用懒加载不能让一个普通定义变成不可实例化的模板。

邮件子定义如下：

```xml
<bean id="emailNotifier" parent="notifierTemplate">
    <constructor-arg index="0" value="email"/>
    <property name="prefix" value="[priority]"/>
    <property name="recipients">
        <list merge="true"><value>owner@example.test</value></list>
    </property>
    <property name="headers">
        <map merge="true">
            <entry key="region" value="us"/>
            <entry key="priority" value="high"/>
        </map>
    </property>
</bean>
```

`parent` 指向父定义的 Bean 名。子定义没有写 `class`，有效类型会从父定义取得；第0个构造器参数改成 `email`，第1个日志引用沿用父定义。这里用相同的显式索引表达覆盖关系，避免把没有索引的构造器参数组合规则也混进当前例子。

定义合并不需要先构造一个“父对象”。Spring 使用的是父定义的配置数据，之后为 `emailNotifier` 创建自己的对象。

## 5. 先看元数据，暂时不创建通知器

运行：

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=definitions
```

```text
raw.class=null
raw.parent=notifierTemplate
raw.recipients=1
merged.class=cn.ningbingjian.learnjava.ioc.lesson008.RouteNotifier
merged.recipients=2
merged.abstract=false
merged.lazy=false
has-log-instance=false
has-email-instance=false
```

这个模式只有 XML 加载与定义查询，没有调用 `refresh()` 或 `getBean()`。

| 观察项 | 原始邮件子定义 | 合并后的有效定义 |
| --- | --- | --- |
| 类名 | 没有在子定义中填写，因此为 `null` | 从模板取得 `RouteNotifier` 的完整类名 |
| 收件人数量 | 只有子定义自己的1项 | 列表启用合并后为2项 |
| 构造器第0个参数 | 子定义的 `email` | 使用子定义的 `email` |
| 构造器第1个参数 | 子定义没有重新填写 | 使用父定义中的 `deliveryLog` 引用 |
| 初始化、销毁方法 | 子定义没有重新填写 | 沿用 `initialize`、`shutdown` |

`getBeanDefinition` 用于查看注册表保存的定义；`getMergedBeanDefinition` 会在需要时解析模板关系，得到有效的合并结果。本例中查询合并结果并没有创建日志或邮件单例，两个 `has-...-instance` 都为 `false`。

还要区分“合并完成”和“值已解析为运行对象”。此时集合里的值仍可能是 XML 元数据对象，例如 `TypedStringValue`；构造器中的对象引用是 `RuntimeBeanReference`。不要把元数据列表直接强转成业务 `List<String>`。配套测试通过相应元数据类型检查参数，之后再在刷新场景验证实际 Java 对象。

原始子定义仍保留自己的那1项收件人。观察合并结果不等于把原始 XML 的父配置逐项写回子定义，更不等于已经完成实例装配。

## 6. 列表合并、Map 覆盖与普通属性覆盖

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=inheritance
```

```text
email.channel=email
email.prefix=[priority]
email.recipients=[ops@example.test, owner@example.test]
email.headers={priority=high, region=us, source=course}
sms.prefix=[orders]
sms.recipients=[on-call]
same-java-class=true
template-has-instance=false
initializations=[init:email, init:sms]
close-count=2
```

邮件的有效结果包含三种不同规则：

1. 普通属性 `prefix` 被子定义的 `[priority]` 覆盖。
2. 子列表启用 `merge="true"`，父列表的 `ops@example.test` 在前，子列表的 `owner@example.test` 接在后面。列表合并不负责按业务身份去重。
3. 子 Map 启用合并，保留父 Map 中的 `source=course`，把同名键 `region` 改成 `us`，并增加 `priority=high`。

输出 Map 时使用 `TreeMap` 排序，便于阅读与比较；这不意味着业务依赖 Spring 按字母排序注入 Map。

短信子定义没有开启列表合并：

```xml
<bean id="smsNotifier" parent="notifierTemplate">
    <constructor-arg index="0" value="sms"/>
    <property name="recipients">
        <list><value>on-call</value></list>
    </property>
</bean>
```

因此它的列表只有 `on-call`，不会保留父列表中的邮箱。它没有重新配置 `prefix` 和 `headers`，所以这两项沿用父定义。

`merge="true"` 应写在需要合并的**子集合元素**上，而不是写在 `<bean>` 上，也不是只在父集合上设置后期待所有子集合自动合并。它描述的是父子定义中同一个集合属性的合并，不是两个任意 Bean 的内容合并。父子集合类型也必须兼容，不能把父 Map 与子 List 当作同一种集合拼接。

本例验证列表和 Map，源码可分别对照 [ManagedList（v7.0.9）](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedList.java) 与 [ManagedMap（v7.0.9）](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/ManagedMap.java)。

## 7. 定义继承不是把父定义所有开关原样复制

`definitions` 模式中有两个值得停下来看的结果：`merged.abstract=false`、`merged.lazy=false`。

父定义是抽象模板，子定义默认不是抽象定义，所以子 Bean 可以被创建。若抽象标志也机械继承下来，模板就无法自然产出可实例化的子定义了。

懒加载要进一步结合当前 XML 解析过程看。本文件没有设置根元素的 `default-lazy-init`；子 `<bean>` 未写 `lazy-init` 时，解析器会按当前文档默认值形成 `false`。合并时这个子定义值覆盖父定义上的 `true`，因此两个子通知器都在刷新期间初始化。

这个结果验证的是**当前 XML 配置及其解析后的定义**。编程方式构造的定义可能保留“未设置”的状态，不能把本例扩大为“所有注册入口的 lazy 标志都不可能继承”。先看解析结果，再看合并规则，是读这类源码时更可靠的顺序。

类名、构造参数、属性值、作用域和生命周期等设置也各有合并条件。需要扩展例子时，逐项核对有效定义，不要写一个“全部继承”或“全部覆盖”的总规则。当前细节可对照 [BeanDefinitionParserDelegate](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/xml/BeanDefinitionParserDelegate.java) 的属性解析，以及 [AbstractBeanDefinition.overrideFrom](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanDefinition.java) 的合并实现。

## 8. 抽象模板存在于注册表，却不能直接使用

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=abstract-template
```

```text
template-definition-exists=true
get-template-error=BeanIsAbstractException
child-ready=true
```

注册表中有 `notifierTemplate` 的定义，不代表可以 `getBean("notifierTemplate")`。这里抛出的 `BeanIsAbstractException` 针对定义的抽象标志，Java 类型本身是具体类也无济于事。

父定义可作为模板被合并；业务对象应引用实际子 Bean。如果把服务的 `ref` 改为这个抽象模板，服务创建也无法得到可用依赖。反过来，单纯把模板改成 `lazy-init="true"`、去掉 `abstract`，并不能禁止别人按名字请求它。

定义继承的概念说明见 [Spring 官方文档](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html)。实际排查时，同时检查定义上的标志和 Java 类的修饰符，避免把两个层面的“抽象”混为一谈。

## 9. 静态工厂和实例工厂怎样写

完整配置见 [xml-factories.xml](src/main/resources/lesson008/xml-factories.xml)。静态工厂定义是：

```xml
<bean id="staticNotifier" class="cn.ningbingjian.learnjava.ioc.lesson008.RouteNotifier" factory-method="createStatic"
      init-method="initialize" destroy-method="shutdown">
    <constructor-arg index="0" value="static"/>
    <constructor-arg index="1" ref="deliveryLog"/>
    <property name="recipients"><list><value>ops@example.test</value></list></property>
</bean>
```

这里 `class` 表示持有静态工厂方法的类。它刚好也是返回对象的类型，但一般情况下工厂类可以与产品类型不同。配置了 `factory-method` 后，容器会调用该工厂方法，而不是把这些参数直接传给该类构造器。

实例工厂则先有工厂 Bean，再由它的方法创建产品：

```xml
<bean id="notifierFactory" class="cn.ningbingjian.learnjava.ioc.lesson008.NotifierFactory"/>
<bean id="instanceNotifier" factory-bean="notifierFactory" factory-method="create"
      init-method="initialize" destroy-method="shutdown">
    <constructor-arg index="0" value="instance"/>
    <constructor-arg index="1" ref="deliveryLog"/>
    <property name="recipients"><list><value>ops@example.test</value></list></property>
</bean>
```

注意，实例产品定义没有 `class`。它通过 `factory-bean` 找到工厂对象，再调用 `create`；这里的 `<constructor-arg>` 实际用于工厂方法参数。标签名字沿用了构造参数表示法，不能仅凭名字判断执行的是 Java 构造器。

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=factory
```

```text
static.method=createStatic
instance.factory=notifierFactory
instance.definition-class=null
product-type=RouteNotifier
products-ready=true
shared-log=true
```

`instance.definition-class=null` 不代表容器拿到了 `null` 产品。定义中的工厂信息足够描述创建入口，真实产品类型是 `RouteNotifier`。这也延续了第006课的结论：不能只看定义的 `beanClassName` 就断定最终对象类型。

两个产品都经过属性填充与初始化，且共享同一个日志 Bean。工厂返回对象后，容器仍会按产品定义处理后续生命周期；不是“工厂负责 new，因此产品从此与容器无关”。关闭后的销毁也由测试验证。

本例的 `NotifierFactory` 是一个普通 Java 类，没有实现 Spring 的 `FactoryBean` 接口。实例工厂方法配置与 `FactoryBean<T>` 是不同机制，后者在专门课程中展开。

## 10. 用 Java 配置表达相同业务结果

[JavaConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/JavaConfig.java) 保留与第一份 XML 相同的 Bean 名、依赖和属性。通知器方法为：

```java
@Bean(initMethod = "initialize", destroyMethod = "shutdown")
public RouteNotifier notifier(DeliveryLog deliveryLog) {
    var notifier = new RouteNotifier("email", deliveryLog);
    notifier.setPrefix("[orders]");
    notifier.setRecipients(List.of("ops@example.test"));
    notifier.setHeaders(Map.of("region", "cn"));
    return notifier;
}
```

这里 setter 在 `@Bean` 方法体内调用；方法返回之后，容器再按 `initMethod` 调用初始化。方法参数由容器注入，不依赖配置类方法之间的直接调用。

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=java-config
```

```text
lifecycle.before=[init:email]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
same-reference=true
lifecycle.after=[init:email, close:email]
```

两个模式业务输出一致，但定义的内部表达并不完全一样：XML 直接记录通知器类、构造参数与属性值；`@Bean` 定义记录工厂方法，方法体中的 setter 调用属于普通 Java 代码，不会逐句变成定义里的 `PropertyValues`。

配套测试特意验证：XML 通知器定义有 `prefix` 属性元数据，Java 配置通知器定义没有这项属性元数据；两者仍能产生相同的前缀和业务行为。

| 配置入口 | 通常在哪里表达装配 | 如何进入容器 | 阅读时的关键问题 |
| --- | --- | --- | --- |
| XML `<bean>` | 外部资源的参数、属性、引用及工厂信息 | XML 读取与解析后登记定义 | 资源是否加载，名称和类型是否正确 |
| 组件注解与扫描 | 类上的角色标记、注入点及扫描配置 | 候选发现并登记定义，再由相关处理器处理 | 类为什么被纳入，依赖怎样解析 |
| Java `@Bean` | 配置类方法和参数 | 配置解析后登记工厂方法定义 | 方法何时执行，返回对象由谁管理 |

共同点是进入容器的定义与创建流程，不是三种入口生成的每一个字段都一致，也不是容器必须把所有 Java 代码翻译成 XML 式属性表。

## 11. 迁移旧 XML：先保留可验证的装配边界

本课提供一个过渡入口 [ImportXmlConfig.java](src/main/java/cn/ningbingjian/learnjava/ioc/lesson008/ImportXmlConfig.java)：

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:lesson008/xml-basics.xml")
public class ImportXmlConfig {
}
```

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=hybrid
```

```text
lifecycle.before=[init:email]
deliveries=[email [orders] order=O-008 -> [ops@example.test] headers={region=cn}]
same-reference=true
lifecycle.after=[init:email, close:email]
```

这个入口仍由 XML 定义业务对象。它证明 Java 配置入口可以暂时保留既有 XML，而不是已经把全部定义迁移成 `@Bean`。当前没有同时注册 `JavaConfig`，因此没有让两套同名业务定义相互覆盖。

实际迁移可以按以下顺序进行：

1. 找到真实加载入口，包括 `<import>`、框架启动配置和 `@ImportResource`，确认哪份 XML 正在生效。
2. 记录当前 Bean 名、别名、引用关系、作用域、初始化与销毁行为，以及父定义和集合合并的最终结果。
3. 用容器测试固定关键行为。本例对照通知结果、对象引用和生命周期；只断言启动成功不足以确认语义一致。
4. 按一个可独立验证的对象组改写为 Java 配置，同时移除该组旧定义，明确剩余 XML 的保留范围。
5. 比较迁移前后有效配置。尤其检查合并列表是否变成了替换、Bean 名是否变化、原先的初始化方法是否遗漏。

把有公共属性的父定义改成 Java 辅助方法，是一种代码复用选择，但 Java 辅助方法并不会自动保留 Spring 定义继承语义。应先展开并理解每个子定义的有效结果，再决定如何组织新代码。

如果旧 XML 还包含 `context:component-scan` 或其他命名空间解析功能，就不能按“每个 `<bean>` 改成一个方法”机械转换。它们可能还会注册基础设施；本课的纯 beans 配置没有演示这些额外机制。

## 12. 失败一：XML 能读入，对象引用却不存在

[xml-missing-ref.xml](src/main/resources/lesson008/xml-missing-ref.xml) 中，服务引用了没有注册的 `missingNotifier`。

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=missing-ref
```

```text
definitions-loaded=true
failed-bean=orderService
root-cause=NoSuchBeanDefinitionException
active=false
```

`definitions-loaded=true` 表示资源语法和基本定义读取已经通过。`ref` 在定义中先作为引用元数据存在，不要求 XML 读到这一行时目标对象就已经创建。

刷新时服务开始创建，容器解析该引用，才发现目标名字不存在。测试同时检查失败 Bean 为 `orderService`、最深层异常为 `NoSuchBeanDefinitionException`，以及缺失名字确实是 `missingNotifier`。

排查顺序应围绕这条引用：名字是否拼错、目标所在资源是否加载、迁移是否删掉了旧定义、引用是否指向错误的上下文。这个例子并不是 XML 格式错误，重新调整缩进不会解决依赖缺失。

演示会打印 Spring 取消刷新的警告，再输出异常摘要。命令正常退出表示入口捕获了这个预期失败；是否符合预期，由配套异常断言验证。

## 13. 失败二：子定义指定了 class，却无法接收继承属性

[xml-incompatible-class.xml](src/main/resources/lesson008/xml-incompatible-class.xml) 使用没有指定类的抽象模板：

```xml
<bean id="propertyTemplate" abstract="true">
    <property name="prefix" value="[orders]"/>
</bean>
<bean id="invalidTarget" parent="propertyTemplate" class="java.lang.Object"/>
```

模板可以只声明公共属性，具体子定义自己提供类。本例故意选用 `Object`，它没有 `setPrefix(...)`。

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=incompatible-class
```

```text
definitions-loaded=true
failed-bean=invalidTarget
root-cause=NotWritablePropertyException
active=false
```

定义合并本身成功：有效类型是 `java.lang.Object`，有效属性包含 `prefix`。随后创建对象并填充属性时失败，最深层异常是 `NotWritablePropertyException`。

这说明定义继承没有要求子 Java 类必须 `extends` 某个父 Java 类，却要求最终类能实际接收继承下来的配置。除属性外，还应检查继承的构造参数、工厂信息和生命周期方法是否与最终类型匹配。

本例的失败点是属性填充。不要把所有父子定义错误都归为“合并失败”：父定义名字不存在、构造器不匹配、属性不可写和初始化异常，发生的位置及排查入口都不同。

## 14. 父子定义、Java 继承、父子容器是三种关系

| 关系 | 连接的对象 | 核心作用 | 本课例子 |
| --- | --- | --- | --- |
| 定义继承 | 两份 BeanDefinition | 复用并合并配置数据 | `emailNotifier` 的 `parent="notifierTemplate"` |
| Java 类继承 | Java 类型 | 语言层面的成员继承和类型关系 | 本课通知器是 `final`，没有业务父子类体系 |
| 父子容器 | 两个上下文或 BeanFactory | 分层持有对象，并提供向父级查找的路径 | 子容器中的服务引用父容器的通知器 |

邮件、短信对象都属于同一个 `RouteNotifier` 类。它们的定义分别使用模板，并没有生成两个 Java 子类，也没有自动创建两个上下文。

为了把父子容器的区别落实到运行结果，[xml-child-context.xml](src/main/resources/lesson008/xml-child-context.xml) 只定义 `childService`，其构造器引用 `notifier`。父容器加载 `xml-basics.xml`，子容器在刷新前通过 `setParent(parent)` 与它关联。

```bash
mvn -q -pl 008-xml-definition-inheritance exec:java -Dexec.args=hierarchy
```

```text
child-local-notifier=false
child-can-find-notifier=true
child-uses-parent-instance=true
parent-can-find-child-service=false
parent-notifier-ready-after-child-close=true
```

子容器本地没有 `notifier` 定义，因此 `containsBeanDefinition` 为 `false`；通过层级查找可以找到父容器的对象，因此 `containsBean` 为 `true`。子服务持有的正是父容器中已经存在的通知器，父容器则不能反向找到 `childService`。

关闭子容器后，父通知器仍处于可用状态，因为它由父容器管理，随后才随父容器关闭而销毁。测试验证了这个生命周期归属。本例没有父子同名对象，也没有展开多层容器的类型查询规则；这里需要掌握的是层级查找与定义模板合并属于不同关系。

这两种容器机制可以在更复杂配置中组合，不能因为本课把它们分开解释，就推断定义继承永远只允许在同一个 BeanFactory 内解析。遇到实际工程，应分别画清定义的 `parent` 指向和上下文的父级关系。

## 15. 沿着 XML 解析与定义合并进入源码

源码固定为 **Spring v7.0.9**。第003课已有调试环境说明，本课建议先用 `definitions` 模式，避免启动业务对象时大量创建调用干扰当前观察。

1. 在 `XmlBeanDefinitionReader.loadBeanDefinitions` 观察实际资源位置；继续看 `doLoadBeanDefinitions` 如何获得 XML 文档并交给定义注册过程。
2. 在 `BeanDefinitionParserDelegate.parseBeanDefinitionElement` 观察 `id`、`class`、`parent`；在属性、构造参数和集合解析方法中观察引用与集合元数据的形成。
3. XML 加载返回后，在应用代码检查 `getBeanDefinition("emailNotifier")`。此时原始子定义没有类名，只包含它自己声明的差异。
4. 单步进入 `AbstractBeanFactory.getMergedBeanDefinition`。在处理父定义的分支观察 `parentBeanName`，再看 `new RootBeanDefinition(pbd)` 与 `mbd.overrideFrom(bd)`。
5. 在 `AbstractBeanDefinition.overrideFrom` 观察子定义如何补充和覆盖配置。集合属性继续进入 `MutablePropertyValues` 的属性合并逻辑，再进入 `ManagedList.merge`、`ManagedMap.merge`。
6. 返回应用代码，再观察有效类名、构造参数、收件人数量及单例缓存。此时没有 `refresh()`，合并得到有效定义并没有自动触发通知器构造。

源码入口：[XmlBeanDefinitionReader](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/xml/XmlBeanDefinitionReader.java)、[BeanDefinitionParserDelegate](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/xml/BeanDefinitionParserDelegate.java)、[AbstractBeanFactory](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java)、[MutablePropertyValues](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-beans/src/main/java/org/springframework/beans/MutablePropertyValues.java)。

`getMergedBeanDefinition` 有多个重载。使用方法断点时应选择接收 `beanName`、`BeanDefinition`、`containingBd` 的实现，或直接在父定义分支设置行断点。单次查询也可能递归处理父模板，不要把“进入两次”直接当成重复创建对象。

调试元数据时不要主动求值 `getBean()`；它会引入实例创建。也不要在这里修改定义后便假定已创建对象随之更新：合并元数据、缓存失效与对象生命周期是不同问题，后续课程会继续展开。

## 16. 十二个测试与下一步

[XmlDefinitionInheritanceTest.java](src/test/java/cn/ningbingjian/learnjava/ioc/lesson008/XmlDefinitionInheritanceTest.java) 包含十二个测试，分别验证：

1. XML 引用、集合和属性能够组装并执行通知业务。
2. 初始化和销毁确实作用于被管理的通知器实例。
3. 原始定义与合并定义不同，合并查询不创建业务对象。
4. 子定义覆盖普通属性和指定索引参数，并按规则合并列表与 Map。
5. 未启用合并的列表替换父列表，模板不产生实例。
6. 抽象定义不能直接请求，即使 Java 类本身可实例化。
7. 不存在的 `ref` 在创建阶段产生明确的缺失对象错误。
8. 继承属性与目标类型不兼容时，在属性填充阶段失败。
9. 静态、实例工厂产品继续接受初始化及销毁管理。
10. Java 配置与 XML 业务行为一致，但定义表达有所不同。
11. `@ImportResource` 入口保留 XML 业务装配并且没有重复通知器。
12. 子容器向父级查找依赖，关闭子容器不销毁父级对象。

单独测试本课：

```bash
mvn -pl 008-xml-definition-inheritance -am test
```

修改源码或 XML 后重新编译并运行：

```bash
mvn -q -pl 008-xml-definition-inheritance compile exec:java -Dexec.args=inheritance
```

`compile` 会连同资源处理一起执行，因此这里也适用于修改 `src/main/resources` 中的 XML。只运行 `exec:java` 时，应确认 `target/classes` 已包含最新源码和资源。

第001—008课聚合构建共 **75 个测试**。十个演示模式已逐一运行，预期失败模式的异常摘要与测试断言一致。父模板的 `lazy-init` 行为与集合合并结果均按当前固定版本验证。

可以继续做三个单变量练习：删除邮件子列表的 `merge="true"`，预测列表内容；给邮件子定义显式加 `lazy-init="true"`，在没有其他对象依赖它时观察创建时机；把 Java 配置的初始化方法配置去掉，判断上下文启动与业务调用哪个先暴露问题。仓库保留的是正文已经验证的版本，这些变体需要你修改后再运行。

下一课：[08-01-009 编程式注册与外部对象接入](../00-模块学习大纲.md#lesson-009)。我们会比较定义注册、`registerBean` 与已有实例接入，并进一步明确初始化、后处理和销毁分别由谁负责。
