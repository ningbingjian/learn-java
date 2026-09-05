# projects/ · 每部（Part）收官项目

> 每学完一个部（Part），就在这里**重新从零**做一个完整项目——只用该部已学过的技术。
> 这是「通关验收」，不是 demo 堆：证明你真的掌握了这一部。
> 详细课程地图见根 [CURRICULUM.md](../CURRICULUM.md)。

## 原则

1. **从零、独立**：目录全新，不复制前面部项目的代码（可借鉴思路）。可反复重做、可展示、可当里程碑。
2. **武器自限**：每部项目只能用本部分及其前已学的技术；各部的自限写在本目录各项目 README。
3. **规格先行**：每个项目先写 `SPEC.md`（需求/验收/自限/自测判分器）再动工——「把模糊需求变成可验收目标」本身就是架构师基本功。
4. **跑得起来**：按 README 一键能跑、验收可复现。

## 收官项目清单

| 部 | 项目 | 一句话 |
|---|---|---|
| [Part I](part-01-cli-toolchain/README.md) | CLI 文档工具链 | JSON 解析器起步 → Markdown/CSV 处理器（纯标准库） |
| [Part II](part-02-perf-lab/README.md) | 性能观测实验台 | 自写线程池/队列 vs JDK + GC 调优，全部出报告 |
| [Part III](part-03-single-app/README.md) | 完整单体业务系统 | Spring 全家桶 + MySQL + 安全，从零可上线 |
| [Part IV](part-04-hotspot-system/README.md) | 高并发热点系统 | 秒杀/抢券：Redis+MQ+锁+幂等，不超卖 |
| [Part V](part-05-consistency-lab/README.md) | 一致性演练场 | 故障注入 + 对账，把最终一致演示出来 |
| [Part VI](part-06-microservices/README.md) | 从零微服务系统 | SCA 全家桶 + K8s 交付 + 故障演练 |
| [Part VII](part-07-graduation/README.md) | 毕业设计 | 新域完整系统 + 全套架构文档（ADR） |
