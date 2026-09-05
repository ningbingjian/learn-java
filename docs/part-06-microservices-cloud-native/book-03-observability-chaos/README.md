# 《可观测性与混沌工程》

> Part VI · 第 3/4 本 · 状态 🟡（目录已立）

## 定位

线上系统看不见就没法谈稳定性。本卷把「日志/指标/链路」三支柱落地成具名工具（ELK/Loki、Prometheus/Grafana、SkyWalking/Jaeger），再以混沌工程与故障演练让稳定性可验证。

## 前置

[《Docker 与 Kubernetes》](../book-02-docker-kubernetes/README.md)（部署载体）。

## 章目录

- ch01 · 可观测三支柱：为什么日志/指标/链路缺一不可
- ch02 · 日志体系：ELK 与 Loki 的取舍
- ch03 · 指标：Prometheus 数据模型与采集
- ch04 · Grafana 面板与告警
- ch05 · 链路追踪：SkyWalking / Jaeger（trace 与 span）
- ch06 · 慢调用与瓶颈定位（三支柱协同定位一个真实问题）
- ch07 · 告警体系与告警疲劳治理
- ch08 · SLO/SLI 与错误预算
- ch09 · 混沌工程理念（先假设，再验证）
- ch10 · 故障演练：注入延迟/丢包/节点故障并复盘

## 收官实验

给自己的一套系统接入完整可观测，并执行一次「注入故障→定位→复盘」的演练，产出含 trace/指标证据的复盘报告。

## 武器自限

工具可以 Docker 自建；但「可观测」与否要看能不能回答「某请求为何慢」，而不是装了几个面板。

## 关联

- 与 Part V 故障理论、Part VI 微服务共同构成「稳定性」闭环。
