# learn-java-spring-in-action

这个目录专门用于按章节学习《Spring 实战（第 6 版）》 / *Spring in Action, Sixth Edition*。

> `learn-java-spring-in-action/` 只是本书的学习工作区，**本身不是 Maven 工程**。真正用于自己动手编写代码的 Maven 多模块项目位于 `learn-spring-in-action-ning/`。

## 学习资料

- 中文翻译仓库：<https://github.com/LeonLi0102/spring-in-action-v6-translate>
- 原书官方配套源码：<https://github.com/habuma/spring-in-action-6-samples>
- 原书：*Spring in Action, Sixth Edition*，Manning

> 官方配套源码用于对照章节项目结构、配置、API 用法和预期行为；自己的练习代码统一写在 `learn-spring-in-action-ning/` 中，不直接照搬官方源码。

## 目录结构

```text
learn-java-spring-in-action/
├── README.md
├── STRUCTURE.md
├── AGENTS.md
└── learn-spring-in-action-ning/        # 个人 Maven 多模块练习工程
    ├── pom.xml                         # Maven 聚合父 POM
    ├── README.md
    ├── chapter01-spring-getting-started/
    ├── chapter02-developing-web-applications/
    ├── ...
    ├── chapter18-deploying-spring/
    └── appendix-a-bootstrapping-spring-applications/
```

## Maven 工程入口

```text
learn-java-spring-in-action/learn-spring-in-action-ning/pom.xml
```

父 POM 聚合第 1～18 章以及附录 A，每一章对应一个独立 Maven 子模块。

## 使用方式

先进入个人练习工程：

```bash
cd learn-java-spring-in-action/learn-spring-in-action-ning
```

验证整个多模块工程：

```bash
mvn validate
```

只构建某一章，例如第 1 章：

```bash
mvn -pl chapter01-spring-getting-started -am test
```

后续按照 **理论 → 实操（带原理）→ 小总结** 的方式逐章补充代码与学习记录。
