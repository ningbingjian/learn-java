# 术语表（glossary）

> 收录：跨书/跨部反复出现、容易混淆、或你曾搞混过的术语。
> 规则：一句话下定义；需要展开就链到对应书/章。保持精简，别写成百科。
> 新增术语随手加；遇到重复概念先查这里。

| 术语 | 一句话定义 | 详见 |
|---|---|---|
| JDK / JRE | JDK=开发工具包（含 javac），JRE=纯运行时；现在一般整体叫 JDK | Part I《开发环境与工具链》 |
| JVM | Java Virtual Machine，执行字节码的虚拟机，屏蔽操作系统差异 | Part II《JVM 虚拟机》 |
| 集合框架 | `java.util` 容器库：List/Set/Map 及其实现 | Part I《进阶机制》 |
| 泛型 | 编译期类型参数机制，擦除后运行时无类型 | Part I《进阶机制》 |
| NIO | New I/O（`java.nio`），非阻塞/缓冲/通道式 IO | Part I《进阶机制》ch07、Part II《Netty》 |
| 线程 | 操作系统调度的最小执行单元；Java 里 `Thread` | Part II《Java 并发编程》 |
| 四层海拔 | 会用(L1)/原理(L2)/取舍(L3)/落地(L4) 的写作深度标尺 | [writing-guide](writing-guide.md) |
| 中间件 | 位于应用与 OS/DB 之间、解决某一横切关切的软件（缓存/消息/检索/调度…） | Part IV 各书 |
| 分布式系统 | 多节点协作完成单机做不到（规模/可用性）的系统 | Part V《分布式系统理论》 |
| 微服务 | 把单体拆成可独立部署服务的架构风格 | Part VI《Spring Cloud Alibaba》 |
| 云原生 | 面向容器/编排/不可变基础设施的应用理念与交付方式 | Part VI《Docker 与 K8s》 |
| ADR | Architecture Decision Record，架构决策记录 | Part VII《架构决策与团队工程》 |
| 收官项目 | 每部读完后的从零独立项目（通关验收） | [projects/README](../../projects/README.md) |
