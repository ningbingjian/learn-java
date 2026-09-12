# AGENTS.md

## 作用范围

本文件约束 `learn-java-spring-in-action/` 目录及其所有子目录。

## 项目目标

本目录用于按《Spring 实战（第 6 版）》章节顺序学习 Spring，并保存学习资料、实验记录以及个人练习代码。

## 组织规则

1. `learn-java-spring-in-action/` 是学习工作区，本身不是 Maven 工程，不在该目录放父 `pom.xml`。
2. 真正的 Maven 多模块练习工程固定放在 `learn-java-spring-in-action/learn-spring-in-action-ning/`。
3. `learn-spring-in-action-ning/pom.xml` 是聚合父 POM，一章对应一个 Maven 子模块。
4. 学到某章时再增加该章依赖和源码，不提前堆入后续章节实现。
5. 若需要展示代码演进，优先保留关键阶段，而不是只保留最终版本。
6. 学习笔记以中文初学者视角编写，不包含个人姓名、邮箱等隐私信息。
7. 原书官方配套源码 <https://github.com/habuma/spring-in-action-6-samples> 只用于对照，不直接复制为个人练习代码。

## 内容讲解规则

每个章节的学习文档优先采用：

1. 本章目标
2. 核心理论
3. 实操步骤
4. 每一步背后的原理
5. 常见问题与调试
6. 本章小总结

对于以前已经详细讲过的概念可以简要复述；本章首次出现的核心概念必须把原理讲清楚。

## 版本规则

- 默认以《Spring 实战（第 6 版）》对应技术版本为学习基线。
- 如需升级到新版 Spring / Spring Boot，应明确记录“书中版本”和“新版实现”的差异。
- 不为了追求最新版而直接改写原书学习路径。
