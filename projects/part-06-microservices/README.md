# Part VI · 收官项目：从零微服务系统（Microservices on Cloud）

> 所属：[Part VI 微服务与云原生](../../CURRICULUM.md#part-vi--微服务与云原生microservices-cloud-native) · 状态 ⬜ 未动工

## 目标

从零拆一个 3–4 服务的微服务系统，用 Spring Cloud Alibaba 治理，并交付到容器/K8s：可重复部署、可观测、经得起一次故障演练。

## 里程碑

- ① 服务划分与契约（为什么要这么拆 → ADR 草稿）
- ② Nacos 注册/配置 + Feign/Dubbo 调用串起业务链路
- ③ Gateway 统一入口 + Sentinel 治理（限流/熔断各有场景）
- ④ 确有必要处的分布式事务（Seata 或 Outbox，写明取舍）
- ⑤ Docker/K8s 部署 + CI 流水线 + SkyWalking 全链路
- ⑥ 一次故障演练 + 复盘（接可观测书收官）

## 武器自限

治理一律走生态组件，禁止自造轮子；交付必须可重复部署（不是「在我电脑能跑」）。

## 验收

按文档从零能一键部署整套 + 演练有复盘；每个选型能写一条 ADR。
