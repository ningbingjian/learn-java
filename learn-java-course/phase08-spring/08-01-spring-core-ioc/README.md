# 08-01 Spring Core / IoC

[阶段08](../README.md) · [完整模块大纲](00-模块学习大纲.md)

本模块从普通 Java 对象协作开始，逐步学习配置、依赖注入、生命周期、核心基础设施、源码与容器扩展。

**已编写课程**

| 课程 | 文档与源码 | 状态 |
| --- | --- | --- |
| 08-01-001 为什么需要 Spring 容器 | [进入第001课](001-why-spring-container/README.md) | 已提供讲解、三个运行场景与六个测试 |
| 08-01-002 IoC、DI 与对象装配 | [进入第002课](002-ioc-di-object-assembly/README.md) | 已提供讲解、七个运行场景与九个测试 |

下一课：[08-01-003 建立可运行、可调试的学习工程](00-模块学习大纲.md#lesson-003)（大纲已列出，正文待编写）。

**运行方式**

使用 JDK 21、Maven 3.9.x，在本目录执行：

```bash
mvn clean verify
java -cp 001-why-spring-container/target/classes cn.ningbingjian.learnjava.ioc.lesson001.DemoApplication manual
java -cp 002-ioc-di-object-assembly/target/classes cn.ningbingjian.learnjava.ioc.lesson002.DemoApplication constructor-email
```

按需选择单课构建和测试：

```bash
mvn -pl 001-why-spring-container -am test
mvn -pl 002-ioc-di-object-assembly -am test
```

每课的文档、子模块 POM 与 src 放在同一目录；本目录的父 POM 统一管理依赖和构建插件。第001—002课使用普通 Java，只有测试依赖 JUnit；聚合构建共运行 15 个测试；Spring 容器依赖在第003课引入。
