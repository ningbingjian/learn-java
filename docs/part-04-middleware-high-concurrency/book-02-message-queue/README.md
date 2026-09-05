# 《消息队列：原理与选型》

> Part IV · 第 2/4 本 · 状态 🟡（目录已立）

## 定位

消息队列解决「削峰、异步、解耦」三件事——本书讲清模型与可靠性的每一环，并以 RocketMQ 为主线、Kafka 为对照（RocketMQ 的完整落地在 Part VI 与业务集成）。

## 前置

Part II 并发、[《MySQL》](../../part-03-single-app-engineering/book-03-mysql-core/README.md)（事务对照）。

## 章目录

- ch01 · 消息模型与使用场景（何时真的需要 MQ）
- ch02 · 可靠投递：生产者确认与「不丢消息」的链路
- ch03 · 消费可靠性：ACK、重试与**幂等消费**
- ch04 · 顺序消息（全局/局部顺序及其代价）
- ch05 · 事务消息与本地消息表（与 DB 事务协同）
- ch06 · 延迟消息 / 定时任务（与 XXL-Job 的分工）
- ch07 · RocketMQ 架构：NameServer/Broker/CommitLog
- ch08 · RocketMQ 高可用与存储（刷盘/主从/重平衡）
- ch09 · Kafka 架构对照：分区/ISR/日志段（对比找共同抽象）
- ch10 · 消息积压与治理（积压了怎么快速消费）
- ch11 · 选型矩阵：RocketMQ/Kafka/RabbitMQ/Pulsar
- ch12 · 集成模式：削峰/异步化/事件驱动（CQRS 预埋）

## 收官实验

用 RocketMQ（Docker）搭一个「异步下单 + 幂等消费 + 消息积压治理」实验台，制造一次消费故障并演示恢复。

## 武器自限

允许 Docker 起 MQ；可靠性结论要有「杀掉进程/断电」级验证。

## 关联

- ch05 事务消息与 Part V 的分布式事务模式对接。
- 为 Part IV 秒杀收官项目提供削峰通道。
