# learn-spring-in-action-ning

《Spring 实战（第 6 版）》个人编码练习工程。

这是 `learn-java-spring-in-action` 学习区下面真正用于亲手编写代码、运行实验和保留章节演进过程的 Maven 多模块项目。

## 参考资料

- 中文翻译：<https://github.com/LeonLi0102/spring-in-action-v6-translate>
- 原书官方配套源码：<https://github.com/habuma/spring-in-action-6-samples>

> 官方源码只用于对照书中的项目结构、配置和实现方式；这里优先自己从零编写和演进代码。

## 模块组织

父 `pom.xml` 聚合第 1～18 章及附录 A，每一章对应一个独立 Maven 子模块。

```text
learn-spring-in-action-ning/
├── pom.xml
├── chapter01-spring-getting-started/
├── chapter02-developing-web-applications/
├── ...
├── chapter18-deploying-spring/
└── appendix-a-bootstrapping-spring-applications/
```

## 使用方式

在本目录执行：

```bash
mvn validate
```

学习某一章时，例如第 1 章：

```bash
mvn -pl chapter01-spring-getting-started -am test
```

后续按照 **理论 → 实操（带原理）→ 小总结** 的方式逐章补充代码与学习记录。
