# 阶段08 Spring 应用框架生态

[返回学习路线总导航](../00-学习路线总导航.md#stage-08)

本目录用于阶段08的模块大纲、学习文档和配套源码。

方向：建立以 Spring 为核心的应用开发、运行机制理解与框架扩展能力。

目标：能够构建业务应用，沿容器、代理、事务和请求链路定位问题，并开发框架扩展。

**模块范围**

| 编号 | 模块 | 学习范围 |
| --- | --- | --- |
| 08-01 | [Spring Core / IoC](08-01-spring-core-ioc/00-模块学习大纲.md) | 从依赖注入入门到容器运行机制、完整核心源码系列与扩展开发 |
| 08-02 | Spring AOP | 从切面使用到代理与拦截机制、核心源码及代理问题分析 |
| 08-03 | Spring 事务管理 | 从事务使用到传播与资源协调机制、源码及事务失效诊断 |
| 08-04 | Spring MVC | 从接口开发到请求处理机制、核心源码、扩展与性能分析 |
| 08-05 | Spring Boot | 从应用搭建到启动与自动配置机制、核心源码和 Starter 开发 |
| 08-06 | Spring HTTP 客户端 | 从远程调用到 RestClient、WebClient、HTTP Service Clients 的机制与源码 |
| 08-07 | Spring Modulith | 从模块化应用入门到模块约束、事件协作、源码与架构演进 |

**学习规划建议**

08-01 Spring Core / IoC 的完整模块大纲已建立，[第001课](08-01-spring-core-ioc/001-why-spring-container/README.md)、[第002课](08-01-spring-core-ioc/002-ioc-di-object-assembly/README.md)、[第003课](08-01-spring-core-ioc/003-runnable-debuggable-spring/README.md)、[第004课](08-01-spring-core-ioc/004-java-config-object-assembly/README.md)与[第005课](08-01-spring-core-ioc/005-maintainable-container-tests/README.md)已提供正文与配套源码，后续继续按大纲逐课展开。每个模块保留入门、进阶、原理、源码与工程实践的完整路径；课程数量由内容和依赖决定。

第一轮学习 Core / IoC 的对象管理与依赖注入，建立可运行、可调试的应用基础。再进入 AOP、事务与 MVC 的基础应用，并使用 Boot 组织业务项目。

深入学习时回到各模块，沿实际执行路径逐步阅读源码、验证行为和开发扩展。Boot 启动与自动配置的源码在容器机制基础上展开；HTTP 客户端在 HTTP 和 MVC 基础上展开；Modulith 在模块化设计基础上展开。模块编号表示归属，不要求一次读完前一个模块全部源码才继续。

开始 IoC 学习需要能使用 Java 类、接口、构造器和异常，能运行 Maven 项目并使用断点。反射、注解及设计模式随具体机制补齐。

第一课建议围绕“为什么需要 Spring 容器”：以一个小型业务服务为例，先观察对象之间的依赖与手动组装，再引入容器管理，对比对象创建、依赖装配和业务调用。随后逐步进入容器配置、行为与源码。

IoC 容器和依赖注入的概念依据见 [Spring 官方介绍](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)，独立应用容器入口见 [容器概览](https://docs.spring.io/spring-framework/reference/core/beans/basics.html)。

**当前进度**

已完成阶段学习入口与 [08-01 Spring Core / IoC 完整模块大纲](08-01-spring-core-ioc/00-模块学习大纲.md)，共 16 篇、87 课，覆盖基础应用、核心设施、连续源码系列、扩展开发、AOT 与工程诊断。

已完成：

- [08-01-001 为什么需要 Spring 容器](08-01-spring-core-ioc/001-why-spring-container/README.md)：讲解、三个运行场景与六个测试。
- [08-01-002 IoC、DI 与对象装配](08-01-spring-core-ioc/002-ioc-di-object-assembly/README.md)：概念对照、七个运行场景与九个测试。
- [08-01-003 建立可运行、可调试的学习工程](08-01-spring-core-ioc/003-runnable-debuggable-spring/README.md)：Spring 容器工程、四个运行场景、八个测试与源码断点指引。
- [08-01-004 使用 Java 配置显式组装对象](08-01-spring-core-ioc/004-java-config-object-assembly/README.md)：显式配置、渠道替换、名称与实例对照、四个运行场景及八个测试。
- [08-01-005 把第一个应用变成可维护的测试](08-01-spring-core-ioc/005-maintainable-container-tests/README.md)：业务与装配分层、测试替身、隔离与资源释放、异常链诊断，三个运行场景及十三个测试。

[进入 IoC 模块学习目录](08-01-spring-core-ioc/README.md)。下一个待编写课程：[08-01-006 BeanDefinition 的基本模型](08-01-spring-core-ioc/00-模块学习大纲.md#lesson-006)。其他模块大纲后续展开。
