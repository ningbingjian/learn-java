# 《Java 并发编程》

> Part II · 第 2/3 本 · 状态 🟡（目录已立）

## 定位

从「会写多线程」到「写得对、说得清、被坑过」。覆盖 JMM、锁与无锁、并发容器、执行框架，以及 JDK21 的虚拟线程——这是后端性能与正确性的分水岭。

## 前置

[《JVM 虚拟机》](../book-01-jvm-vm/README.md)（ch03 对象布局、ch10 JIT）与 Part I 集合源码。

## 章目录

- ch01 · 线程模型与状态（创建/生命周期/上下文切换成本）
- ch02 · 中断与协作（wait/notify、LockSupport）
- ch03 · JMM：原子性/可见性/有序性（缓存一致性直觉）
- ch04 · happens-before 与 volatile 的正确用法
- ch05 · synchronized 与锁升级（偏向/轻量/重量级）
- ch06 · CAS 与原子类（ABA 与解决）
- ch07 · AQS 框架：ReentrantLock/读写/StampedLock
- ch08 · 并发容器：ConcurrentHashMap 演进与其他
- ch09 · 线程池与 Executor（拒绝策略、线程数怎么定）
- ch10 · ForkJoin 与任务分治
- ch11 · CompletableFuture 与异步流水线
- ch12 · 虚拟线程与结构化并发（JDK21 之后怎么写）
- ch13 · 并发陷阱复现（伪共享/锁竞争/死锁/活锁）与压测

## 收官实验

自写阻塞队列 + 线程池，与 JDK 线程池对拍；并复现 3 个并发陷阱，各配一份「现象—原因—修复」报告。

## 武器自限

实验台用纯 JDK；结论必须配 JMH/JFR/压测数字。

## 关联

- 为 Part IV 的分布式锁、限流提供「单机并发正确性」前提。
- 为 Netty 事件循环、虚拟线程的适用边界提供判断力。
