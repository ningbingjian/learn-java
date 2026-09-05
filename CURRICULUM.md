# 课程总目录（CURRICULUM）· 部 → 书 → 收官项目

> 本文件是整座课程书库的**总地图**：7 部（Part）× 25 本主线书（Book）。
> 粒度标准：**凡能撑起一门专著的主题，就自成一本书**，拥有独立目录树（篇/章）、收官实验与篇幅预算；只有撑不满 8 章的主题才降级合并。
> **学习顺序 = 部序；部内书按 `book-NN-<slug>` 两位序号推进**（NN=部内阅读顺序，文件树排序即阅读顺序）。正文写作遵循 [docs/meta/writing-guide.md](docs/meta/writing-guide.md)（四层海拔）。
> 旧版「六卷」结构见 [docs/_archive/](docs/_archive/)（留档，已废弃）。

## 层级定义

| 层级 | 含义 | 粒度 |
|---|---|---|
| 部 Part | 学习主线的大阶段，读完上一台阶 | `docs/part-0X-*/` |
| 书 Book | 一门可独立成书的专著；NN=部内阅读序号 | `docs/part-0X-*/book-NN-<slug>/README.md` |
| 章 Chapter | 书内的真实目录单元，一章 ≈ 一次可写透+可跑demo+带自测 | 各书 README 列 |
| 节 Section | 章内写作骨架 | 写作时展开 |

**项目模型**：每章实验（demos）→ 每书收官实验（中型，可选）→ **每部收官项目（从零·硬性）** → 全课程毕业设计。

---

## Part I · 语言地基（language-foundation）

> 从零到「能用纯 JDK 写出结构良好、懂原理、带自测的命令行程序」。
> 收官项目：[part-01-cli-toolchain](projects/part-01-cli-toolchain/)——命令行文档工具链

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《开发环境与工具链》 | 7 | [book-01-env-toolchain](docs/part-01-language-foundation/book-01-env-toolchain/README.md) | 🟡 目录已立 |
| 《Java 语言核心（上）：基础与面向对象》 | 11 | [book-02-java-core-basics](docs/part-01-language-foundation/book-02-java-core-basics/README.md) | 🟡 目录已立 |
| 《Java 语言核心（下）：进阶机制》 | 13 | [book-03-java-core-advanced](docs/part-01-language-foundation/book-03-java-core-advanced/README.md) | 🟡 目录已立 |
| 《数据结构与算法（Java 视角）》 | 12 | [book-04-ds-algo](docs/part-01-language-foundation/book-04-ds-algo/README.md) | 🟡 目录已立 |

## Part II · JVM·并发·网络（jvm-concurrency-network）

> 把「会用」讲到「懂它怎么跑、怎么调、怎么被它坑」；从这里开始能读框架源码。
> 收官项目：[part-02-perf-lab](projects/part-02-perf-lab/)——性能观测与并发对拍实验台

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《JVM 虚拟机：运行时·GC·调优》 | 14 | [book-01-jvm-vm](docs/part-02-jvm-concurrency-network/book-01-jvm-vm/README.md) | 🟡 目录已立 |
| 《Java 并发编程》 | 13 | [book-02-java-concurrency](docs/part-02-jvm-concurrency-network/book-02-java-concurrency/README.md) | 🟡 目录已立 |
| 《Netty 与高性能网络》 | 13 | [book-03-netty-network](docs/part-02-jvm-concurrency-network/book-03-netty-network/README.md) | 🟡 目录已立 |

## Part III · 单体应用工程（single-app-engineering）

> 第一次做出能上线的完整单体系统，掌握工程化纪律与主流技术栈原理。
> 收官项目：[part-03-single-app](projects/part-03-single-app/)——完整单体业务系统（从零）

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《测试与代码质量》 | 11 | [book-01-testing-quality](docs/part-03-single-app-engineering/book-01-testing-quality/README.md) | 🟡 目录已立 |
| 《设计模式与重构》 | 10 | [book-02-patterns-refactoring](docs/part-03-single-app-engineering/book-02-patterns-refactoring/README.md) | 🟡 目录已立 |
| 《MySQL 数据库核心》 | 14 | [book-03-mysql-core](docs/part-03-single-app-engineering/book-03-mysql-core/README.md) | 🟡 目录已立 |
| 《Spring 框架原理》 | 14 | [book-04-spring-principles](docs/part-03-single-app-engineering/book-04-spring-principles/README.md) | 🟡 目录已立 |
| 《Spring Boot 与 Web 服务》 | 13 | [book-05-spring-boot-web](docs/part-03-single-app-engineering/book-05-spring-boot-web/README.md) | 🟡 目录已立 |

## Part IV · 中间件与高并发（middleware-high-concurrency）

> 单体规模上去之后，用缓存/消息/检索/分片等中间件撑住高并发；概念先于组件。
> 收官项目：[part-04-hotspot-system](projects/part-04-hotspot-system/)——高并发热点系统（秒杀/抢券，从零）

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《Redis 原理与应用》 | 15 | [book-01-redis-in-depth](docs/part-04-middleware-high-concurrency/book-01-redis-in-depth/README.md) | 🟡 目录已立 |
| 《消息队列：原理与选型》 | 12 | [book-02-message-queue](docs/part-04-middleware-high-concurrency/book-02-message-queue/README.md) | 🟡 目录已立 |
| 《Elasticsearch 检索工程》 | 11 | [book-03-elasticsearch](docs/part-04-middleware-high-concurrency/book-03-elasticsearch/README.md) | 🟡 目录已立 |
| 《分库分表与数据扩展》 | 10 | [book-04-sharding-scale](docs/part-04-middleware-high-concurrency/book-04-sharding-scale/README.md) | 🟡 目录已立 |

## Part V · 分布式系统（distributed-systems）

> 从「一台机器」到「一个系统」：一致性、故障与协同的理论与工程底座。
> 收官项目：[part-05-consistency-lab](projects/part-05-consistency-lab/)——一致性演练场（故障注入/对账）

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《分布式系统理论：一致性与故障》 | 13 | [book-01-distributed-theory](docs/part-05-distributed-systems/book-01-distributed-theory/README.md) | 🟡 目录已立 |
| 《协调服务与任务调度》 | 10 | [book-02-coordination-scheduling](docs/part-05-distributed-systems/book-02-coordination-scheduling/README.md) | 🟡 目录已立 |

## Part VI · 微服务与云原生（microservices-cloud-native）

> 学会用 Spring Cloud Alibaba 生态把一个系统拆成可治理的微服务，并交付到容器/云上。
> 收官项目：[part-06-microservices](projects/part-06-microservices/)——从零微服务系统（云上交付+演练）

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《Spring Cloud Alibaba 微服务落地》 | 13 | [book-01-spring-cloud-alibaba](docs/part-06-microservices-cloud-native/book-01-spring-cloud-alibaba/README.md) | 🟡 目录已立 |
| 《Docker 与 Kubernetes》 | 14 | [book-02-docker-kubernetes](docs/part-06-microservices-cloud-native/book-02-docker-kubernetes/README.md) | 🟡 目录已立 |
| 《可观测性与混沌工程》 | 10 | [book-03-observability-chaos](docs/part-06-microservices-cloud-native/book-03-observability-chaos/README.md) | 🟡 目录已立 |
| 《服务网格与新演进》（选学） | 7 | [book-04-service-mesh-evolution](docs/part-06-microservices-cloud-native/book-04-service-mesh-evolution/README.md) | 🟡 目录已立 |

## Part VII · 架构师（architect）

> 不再教组件，只教做决策的方法；用全新领域的毕业设计证明可迁移能力。
> 毕业设计（收官）：[part-07-graduation](projects/part-07-graduation/)——新域完整系统 + 全套架构文档

| 书 | 章数 | 路径 | 状态 |
|---|---|---|---|
| 《领域驱动设计 DDD》 | 10 | [book-01-domain-driven-design](docs/part-07-architect/book-01-domain-driven-design/README.md) | 🟡 目录已立 |
| 《大型系统设计案例与方法》 | 11 | [book-02-large-system-design](docs/part-07-architect/book-02-large-system-design/README.md) | 🟡 目录已立 |
| 《架构决策与团队工程》 | 9 | [book-03-architecture-team](docs/part-07-architect/book-03-architecture-team/README.md) | 🟡 目录已立 |

## 选修（毕业后可另开，不进主线）

大数据流处理（Flink/Spark）、对象存储与数据湖（MinIO/OSS）——独立大山，避免撑爆主线深度。

---

## 状态图例

🟡 目录骨架已立（结构一眼到底）· ⬜ 未开始 · 🧩 章正文写作中 · ✅ 完成并通过书收官实验

## 目录速查（仓库怎么找东西）

```
CURRICULUM.md                 ← 本文件：部→书→状态总地图
docs/meta/                    ← 写作规范 / 术语表 / 修订记录
docs/_chapter-template.md     ← 章正文模板（书 README 之下的具体章用）
docs/part-0X-*/book-NN-*/      ← 每本书：README=书地图 + 后续 chNN-*.md 正文（NN=部内序号）
demos/book-NN-*/chNN-*/       ← 每章的 demo（真实可跑，分组名镜像书目录 book-NN-<slug>）
projects/part-0X-*/           ← 每部收官项目（从零）
```
