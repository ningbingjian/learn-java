# 《Spring Cloud Alibaba 微服务落地》

> Part VI · 第 1/4 本 · 状态 🟡（目录已立）

## 定位

微服务不是框架堆叠，是「拆分+治理」的工程决策。本卷以 Spring Cloud Alibaba 为落地栈：注册配置（Nacos）、调用（Feign/Dubbo/gRPC）、流量治理（Sentinel）、事务（Seata）、网关（Gateway），并把每个选型讲出理由。

## 前置

Part III（Spring/Boot）、Part IV（中间件）、Part V（理论）。Netty（Part II）是读懂网关/调用底层的前提。

## 章目录

- ch01 · 从单体到微服务：拆分原则与代价清单
- ch02 · SCA 生态全景与选型地图（vs 官方栈 vs 云托管 MSE）
- ch03 · 注册发现：Nacos（AP/CP、心跳与健康检查）
- ch04 · 配置中心：Nacos Config 与 Apollo 对照
- ch05 · 服务调用：OpenFeign / Dubbo / gRPC 取舍
- ch06 · 负载均衡与容错（与重试、幂等的关系）
- ch07 · 流量治理：Sentinel（限流/熔断/降级/热点）
- ch08 · 网关：Spring Cloud Gateway（路由/限流/安全卸载）
- ch09 · 分布式事务落地：Seata 的 AT/TCC/SAGA（接 Part V 理论）
- ch10 · 事务消息/Outbox 在微服务里的角色（RocketMQ 集成）
- ch11 · 微服务测试策略（契约测试/消费者驱动）
- ch12 · 微服务安全：鉴权与身份在服务间如何传递
- ch13 · 生产踩坑与排障清单

## 收官实验

把 3–4 个服务用 SCA 串成一个完整链路（含 Gateway/Sentinel/Seata 各一处），并在故障下演示治理效果。

## 武器自限

注册/配置/限流/事务一律走本卷生态组件，禁止自造轮子；交付要能重复部署（配合下两本）。

## 关联

- 与 [《Docker 与 Kubernetes》](../book-02-docker-kubernetes/README.md) 组成「代码→交付」闭环。
- 「为什么这样拆/选」留给 Part VII 提炼成方法论。
