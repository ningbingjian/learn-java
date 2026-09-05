# 《Spring Boot 与 Web 服务》

> Part III · 第 5/5 本 · 状态 🟡（目录已立）

## 定位

把 Spring Boot 用透并讲清「一个请求从端口到响应的全程」；含 REST 设计、安全（Spring Security + OWASP）、Web 测试与可观测性预埋。读完能独立交付一个安全的完整单体 REST 服务。

## 前置

[《Spring 框架原理》](../book-04-spring-principles/README.md)、[《MySQL 数据库核心》](../book-03-mysql-core/README.md)。

## 章目录

- ch01 · Boot 自动配置机制（`@EnableAutoConfiguration` 拆解）
- ch02 · 配置体系：properties/YAML/profile/外部化/配置绑定
- ch03 · Web 架构：Servlet 容器与 Spring MVC 定位
- ch04 · 请求生命周期：过滤器→DispatcherServlet→响应
- ch05 · 控制器与参数绑定（@PathVariable/@RequestBody…）
- ch06 · 参数校验与统一异常处理（全局处理器模式）
- ch07 · REST 设计（资源/状态码/版本化）
- ch08 · 数据访问：Spring Data JDBC/JPA 与 MyBatis 取舍
- ch09 · 安全：认证授权、Session 与 JWT、Spring Security 配置
- ch10 · OWASP 十大漏洞与 Spring 侧防护（实战复现）
- ch11 · Web 层测试（MockMvc/Testcontainers 端到端）
- ch12 · Actuator 与可观测性预埋（指标/健康/审计）
- ch13 · 部署形态：内嵌容器/瘦身包/启动排查

## 收官实验

一个带用户与资源的完整单体 API（含安全与校验），配套测试全绿。

## 武器自限

仍是单体：一个进程、一个数据库、无中间件（缓存/消息属于 Part IV）。

## 关联

- 它「顶不住规模」的那一刻，正是 Part IV 的引子。
