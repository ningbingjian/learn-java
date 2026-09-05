# 《Java 语言核心（下）：进阶机制》

> Part I · 第 3/4 本 · 状态 🟡（目录已立）

## 定位

从「会用」跨到「懂实现」：泛型、集合框架与其内部实现、IO/NIO、函数式、注解反射。读完本卷，你会第一次「读得懂 JDK 源码」。

## 前置

[《Java 语言核心（上）》](../book-02-java-core-basics/README.md)、[《数据结构与算法》](../book-04-ds-algo/README.md)（并行可）。

## 章目录

- ch01 · 泛型系统（通配符、擦除、桥方法）
- ch02 · 集合框架全景（`Collection`/`Map` 生态与选型）
- ch03 · `ArrayList`/`LinkedList` 源码对读（扩容/迭代器）
- ch04 · 哈希与 `HashMap` 源码（扰动/树化/扩容，并发问题预埋）
- ch05 · `Set`/`TreeMap` 与红黑树直觉
- ch06 · IO：字节流/字符流/装饰器
- ch07 · NIO：通道、缓冲、选择器（为 Netty 埋点）
- ch08 · 函数式：lambda 与函数接口、方法引用
- ch09 · Stream 流水线（惰性/短路/并行前提）
- ch10 · `Optional` 与空安全
- ch11 · 注解与反射（自省与框架的接口）
- ch12 · 现代特性：密封类、模式匹配、增强 switch（JDK21）
- ch13 · 模块系统 JPMS 简介（何时用得上）

## 收官实验

自写一个极简「对象映射器」：用反射+泛型把任意 POJO ↔ Map（为 Spring 的 DI/序列化埋直觉），带自测。

## 武器自限

只许 JDK 标准库 + JUnit。

## 关联

- 集合源码、NIO、函数式分别给 Part II 的并发容器、Netty、Stream 并行提供前置。
- 为本 Part 收官工具链项目提供「解析→树→格式化」的能力。
