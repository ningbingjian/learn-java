# learn-java-spring-in-action

这个目录专门用于按章节学习《Spring 实战（第 6 版）》 / *Spring in Action, Sixth Edition*。

## 学习资料

- 中文翻译仓库：<https://github.com/LeonLi0102/spring-in-action-v6-translate>
- 原书：*Spring in Action, Sixth Edition*，Manning
- 本项目只保存自己的学习代码、实验和笔记，不复制整本书内容。

## 技术基线

为了尽量贴合第 6 版正式内容，初始化阶段采用：

- Java 11
- Maven
- Spring Boot 2.5.6
- Spring Framework 5.3.x（由 Spring Boot 2.5.6 管理）

> 后续学习某一章时，如果该章源码或正文明确要求不同版本，以该章实际内容为准；升级新版本时也应单独记录差异，不直接覆盖书中版本的学习过程。

## 项目结构

这是一个 Maven 多模块项目。父工程只负责统一版本和聚合，各章节独立为子模块。

```text
learn-java-spring-in-action/
├── pom.xml
├── README.md
├── AGENTS.md
├── chapter01-spring-getting-started/
├── chapter02-developing-web-applications/
├── ...
├── chapter18-deploying-spring/
└── appendix-a-bootstrapping-spring-applications/
```

### Part 1：基础 Spring

| 模块 | 内容 |
| --- | --- |
| `chapter01-spring-getting-started` | 第 1 章：Spring 入门 |
| `chapter02-developing-web-applications` | 第 2 章：开发 Web 应用程序 |
| `chapter03-working-with-data` | 第 3 章：处理数据 |
| `chapter04-working-with-nonrelational-data` | 第 4 章：处理非关系型数据 |
| `chapter05-securing-spring` | 第 5 章：Spring 安全 |
| `chapter06-working-with-configuration-properties` | 第 6 章：使用配置属性 |

### Part 2：集成 Spring

| 模块 | 内容 |
| --- | --- |
| `chapter07-creating-rest-services` | 第 7 章：创建 REST 服务 |
| `chapter08-securing-rest-services` | 第 8 章：保护 REST 服务 |
| `chapter09-sending-messages-asynchronously` | 第 9 章：发送异步消息 |
| `chapter10-integrating-spring` | 第 10 章：集成 Spring |

### Part 3：响应式 Spring

| 模块 | 内容 |
| --- | --- |
| `chapter11-introducing-reactor` | 第 11 章：Reactor 介绍 |
| `chapter12-developing-reactive-apis` | 第 12 章：开发响应式 API |
| `chapter13-persisting-data-reactively` | 第 13 章：响应式持久化数据 |
| `chapter14-working-with-rsocket` | 第 14 章：使用 RSocket |

### Part 4：部署 Spring

| 模块 | 内容 |
| --- | --- |
| `chapter15-working-with-spring-boot-actuator` | 第 15 章：使用 Spring Boot Actuator |
| `chapter16-administering-spring` | 第 16 章：管理 Spring |
| `chapter17-monitoring-spring-with-jmx` | 第 17 章：使用 JMX 监控 Spring |
| `chapter18-deploying-spring` | 第 18 章：部署 Spring |

### 附录

| 模块 | 内容 |
| --- | --- |
| `appendix-a-bootstrapping-spring-applications` | 附录 A：引导 Spring 应用程序 |

## 使用方式

在本目录执行：

```bash
mvn validate
```

验证整个多模块工程结构。

学习某一章时，可以只构建对应模块，例如：

```bash
mvn -pl chapter01-spring-getting-started -am test
```

## 后续每章建议结构

每个章节模块在真正开始学习时，再逐步补齐：

```text
chapterXX-xxx/
├── pom.xml
├── README.md                  # 本章学习目标、知识点、总结
├── notes/                     # 更细的学习笔记（需要时再创建）
└── src/
    ├── main/
    │   ├── java/
    │   └── resources/
    └── test/
        └── java/
```

不要一次性把全书源码复制进来。建议按照“理论 → 实操（带原理）→ 小总结”的方式，一章一章演进，并保留关键演进阶段，方便以后回看。
