# 卷二 · 并发与 JVM 深潜（Concurrency & JVM Deep Dive）

> 目标读者：已学完卷一。这一卷把「会用」的 Java 讲到「懂它怎么跑、怎么调、怎么被它坑」。从这里开始，你第一次能读懂别人写的框架源码。

## 本卷地图

| 章    | 主题                   | 一句话说明                                                       |
| ---- | -------------------- | ----------------------------------------------------------- |
| ch01 | 线程与锁                 | Thread、synchronized、volatile、wait/notify：先会写对               |
| ch02 | JMM 与 happens-before | Java 内存模型：为什么没 volatile 你会「看到」旧值                            |
| ch03 | 并发容器与工具              | ConcurrentHashMap/CopyOnWrite 等与 Executor、CompletableFuture |
| ch04 | AQS 与自写锁             | AbstractQueuedSynchronizer 原理，手写一个简单锁                       |
| ch05 | GC 全景与调优             | 分代→G1→ZGC 演进；看懂/调 GC 日志；虚拟线程（JDK21）                         |
| ch06 | 类加载与字节码              | 类加载器机制；ASM 读/改字节码入门                                         |
| ch07 | 性能测量方法论              | JMH/JFR/Profiler：让结论有数字（L4 的底气）                             |
| ch08 | Netty 与高性能网络         | Reactor/EventLoop、粘包拆包、编解码；实验：Netty 自写迷你 RPC                |

**写作进度**：⬜ 尚未开始写正文。

## 本卷新增武器

并发正确性直觉（锁、原子、不可变）；能读懂并发 bug 与工具类的源码；能对着 GC/JFR 数据调优而不是「听说很慢」；Netty 这把钥匙 → 之后读网关/MQ/Dubbo 的底层。

## 武器自限（卷末项目不许用的东西）

- 禁止第三方库，**包括 Netty**：ch08 学的 Netty 只用来「读懂原理」，卷末实验台用纯 JDK 实现并与之对照。
- 禁止改 JVM 参数以外的一切「魔法」；结论必须配 JFR/JMH/压测数字。

## 卷末项目（从零 · 独立规划）

**并发实验台：自写线程池 + 阻塞队列 vs JDK 对照。**

- 从零写 `MyThreadPool`（含阻塞队列），再与 `ThreadPoolExecutor`/`Executors` 对照，用 JMH/JFR 压测，产出一份「线程模型与调优」报告。
- 它逼你用到：线程与锁、AQS（阻塞队列）、并发容器、GC 调优、性能测量。
- 验收 = 实验能复现（README 里命令可一键跑）+ 报告有数字、有对比、有结论。

## 前置 / 出口

- 前置：卷一全部。
- 进入卷三的条件：卷末报告写清楚「为什么 JDK 的线程池在某些负载下比我自写的好 / 差」，而不是「我抄了一段」。

## 与前后卷的关系

- 卷一 NIO/函数式在这里被用到深处；Netty 章为卷四读中间件源码、卷五读网关/Dubbo 提供底层。JVM/GC 深潜反哺卷一 ch07 埋的线。
