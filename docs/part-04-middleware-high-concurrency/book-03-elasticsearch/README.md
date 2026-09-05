# 《Elasticsearch 检索工程》

> Part IV · 第 3/4 本 · 状态 🟡（目录已立）

## 定位

检索是独立于 OLTP 的关切。从倒排索引原理（Part I 你已自己实现过最小版）到 ES 的索引/查询/聚合/集群，再到与 MySQL 的协同（双写/Canal）。目标：会用、能排障、能说出选型边界。

## 前置

[《数据结构与算法》](../../part-01-language-foundation/book-04-ds-algo/README.md)（倒排索引收官实验）、[《MySQL》](../../part-03-single-app-engineering/book-03-mysql-core/README.md)。

## 章目录

- ch01 · 为什么检索不用 SQL：倒排与相关性的差距
- ch02 · 索引与映射（字段类型、动态映射的坑）
- ch03 · 分词与分析器（中文分词/自定义）
- ch04 · 文档 CRUD 与近实时原理（refresh/translog）
- ch05 · 查询 DSL：全文/词项/复合查询
- ch06 · 聚合：桶与指标（GroupBy 的 ES 形态）
- ch07 · 相关性打分（TF-IDF→BM25）
- ch08 · 集群：分片/副本/一致性与脑裂
- ch09 · ES 运维：容量、段合并、慢查询与排障
- ch10 · 与 MySQL 协同：双写一致性 vs Canal 同步
- ch11 · 实战：日志检索与商品搜索两个完整案例

## 收官实验

自建 MySQL+ES（Canal 同步）的「文章搜索」小系统，做一次分词与相关性的调优对比。

## 武器自限

允许 Docker 起 ES/Canal；同步一致性方案要写清取舍，不假设「双写必然一致」。

## 关联

- 倒排/分片与 Part IV 分库书共享思维。
- 为 Part VI 微服务里的搜索服务与可观测日志（ELK）供底。
