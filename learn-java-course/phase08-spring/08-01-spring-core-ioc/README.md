# 08-01 Spring Core / IoC

[阶段08](../README.md) · [完整模块大纲](00-模块学习大纲.md)

本模块从普通 Java 对象协作开始，逐步学习配置、依赖注入、生命周期、核心基础设施、源码与容器扩展。

**已编写课程**

| 课程 | 文档与源码 | 状态 |
| --- | --- | --- |
| 08-01-001 为什么需要 Spring 容器 | [进入第001课](001-why-spring-container/README.md) | 已提供讲解、三个运行场景与六个测试 |
| 08-01-002 IoC、DI 与对象装配 | [进入第002课](002-ioc-di-object-assembly/README.md) | 已提供讲解、七个运行场景与九个测试 |
| 08-01-003 建立可运行、可调试的学习工程 | [进入第003课](003-runnable-debuggable-spring/README.md) | 已提供讲解、四个运行场景、八个测试与源码断点指引 |
| 08-01-004 使用 Java 配置显式组装对象 | [进入第004课](004-java-config-object-assembly/README.md) | 已提供讲解、四个运行场景、八个测试与装配断点指引 |
| 08-01-005 把第一个应用变成可维护的测试 | [进入第005课](005-maintainable-container-tests/README.md) | 已提供分层测试讲解、三个运行场景与十三个测试 |
| 08-01-006 BeanDefinition 的基本模型 | [进入第006课](006-bean-definition-model/README.md) | 已提供元数据模型讲解、六个运行场景与九个测试 |

下一课：[08-01-007 组件扫描与注解注册](00-模块学习大纲.md#lesson-007)（大纲已列出，正文待编写）。

**运行方式**

使用 JDK 21、Maven 3.9.x，在本目录执行：

```bash
mvn clean verify
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.DemoApplication manual
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.DemoApplication constructor-email
mvn -q -pl 003-runnable-debuggable-spring exec:java -Dexec.args=lifecycle
mvn -q -pl 004-java-config-object-assembly exec:java -Dexec.args=email
mvn -q -pl 005-maintainable-container-tests exec:java -Dexec.args=console
mvn -q -pl 006-bean-definition-model exec:java -Dexec.args=metadata
```

按需选择单课构建和测试：

```bash
mvn -pl 001-why-spring-container -am test
mvn -pl 002-ioc-di-object-assembly -am test
mvn -pl 003-runnable-debuggable-spring -am test
mvn -pl 004-java-config-object-assembly -am test
mvn -pl 005-maintainable-container-tests -am test
mvn -pl 006-bean-definition-model -am test
```

每课的文档、子模块 POM 与 src 放在同一目录；本目录的父 POM 统一管理依赖和构建插件。第001—002课使用普通 Java，只有测试依赖 JUnit；第003课引入 Spring Framework 7.0.9，通过 Maven 运行并附加同版本源码；第004课继续学习 Java 配置与对象装配；第005课拆分业务、装配、生命周期与异常测试；第006课进入 Bean 定义、元数据与创建时机；聚合构建共运行 53 个测试。
