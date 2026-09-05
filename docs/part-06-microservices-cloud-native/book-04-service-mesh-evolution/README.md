# 《服务网格与新演进》（选学）

> Part VI · 第 4/4 本 · 状态 🟡（目录已立 · 选修：可跳过，不影响主线）
> 标记为选学：本卷偏前瞻/团队演进话题，主线学完再回头不迟。

## 定位

回答「微服务治理的下一个形态是什么」：Service Mesh 把治理下沉到基础设施层，gRPC/protobuf 的契约化通信，Serverless 与 Java 在云原生的位置（含 GraalVM 原生镜像的代价与收益）。**作用是建立视野，不是上岗必修。**

## 前置

[《Spring Cloud Alibaba》](../book-01-spring-cloud-alibaba/README.md)（先有「代理/治理」的参照系）。

## 章目录

- ch01 · 云原生演进图谱：从裸机到 Serverless
- ch02 · Service Mesh 理念：sidecar 与控制面/数据面
- ch03 · Envoy 与 Istio 架构（读完能说清它替你做了什么）
- ch04 · gRPC 与 protobuf：契约化 RPC 的另一形态
- ch05 · Serverless/FaaS 简介与 Java 的适配成本
- ch06 · GraalVM 原生镜像：代价与收益
- ch07 · 演进取舍：何时引入 Mesh/Serverless（决策清单）

## 收官实验

用本地（kind）跑一个最小 Istio 示例：配置一次流量灰度/熔断并观察 sidecar 介入；或以一篇「演进选型对比」报告替代（本卷允许「文档收官」）。

## 武器自限

选修书，实验可以「复现官方最小示例 + 自己的观察」，重在理解而不在生产化。

## 关联

- 是 Part VII 架构演进方法论的一个鲜活案例源。
