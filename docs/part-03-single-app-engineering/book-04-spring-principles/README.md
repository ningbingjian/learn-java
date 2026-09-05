# 《Spring 框架原理》

> Part III · 第 4/5 本 · 状态 🟡（目录已立）

## 定位

Spring 是后端的事实标准，本卷把它当成「要读懂的作品」而非「黑盒注解」。从容器架构地图入手，覆盖 IoC 容器、Bean 生命周期与作用域、AOP、事务抽象、Environment 属性机制、循环依赖——读到能给别人讲清。

## 前置

[《设计模式与重构》](../book-02-patterns-refactoring/README.md)、[《进阶机制》](../../part-01-language-foundation/book-03-java-core-advanced/README.md) 的反射/注解。

## 章目录

- ch01 · IoC 是什么：从 `new` 到容器（手写极简容器）
- ch02 · 容器架构地图：BeanFactory → ApplicationContext、`refresh()` 全程导览（先给源码导航）
- ch03 · Bean 定义与注册（XML → 注解 → Java Config 演进）
- ch04 · Bean 生命周期详解：实例化→注入→初始化→销毁（含 FactoryBean）
- ch05 · Bean 作用域：singleton/prototype、单例 Bean 的并发与无状态设计
- ch06 · 依赖注入：构造/字段/方法 + `@Autowired` 语义与冲突解决（@Primary/@Qualifier/@Resource 对照）
- ch07 · 扩展点全景：BeanPostProcessor / BeanFactoryPostProcessor / Aware（框架钩子地图）
- ch08 · Environment 与属性解析：@Value/PropertySource/@Profile、占位符与类型转换（容器层；Boot 绑定另见 05 书）
- ch09 · 条件装配与 `@Enable*` 编程模型（@Conditional/@Import——Boot 自动配置的原语）
- ch10 · AOP：动态代理与切面模型（JDK 代理 vs CGLIB）
- ch11 · 声明式事务：事务抽象、传播行为与「注解失效」全集
- ch12 · 事件机制与解耦
- ch13 · 循环依赖与三级缓存（为什么要三级）
- ch14 · 读源码的方法与工具 + 框架演进：Spring 5/6、AOT 与原生镜像（收尾定位）

## 收官实验

手写一个「迷你 IoC+AOP」容器（支持构造注入与一个切面），用它跑通一个带事务语义的 demo——证明原理不是背的。

## 武器自限

收官实验自己写容器，禁止直接 `new AnnotationConfigApplicationContext` 糊弄。

## 关联

- 为下一本 [《Spring Boot 与 Web 服务》](../book-05-spring-boot-web/README.md) 提供容器与 AOP 前提。
- 事务失效与 AOP 边界在 Part IV 高并发系统里是常客。
