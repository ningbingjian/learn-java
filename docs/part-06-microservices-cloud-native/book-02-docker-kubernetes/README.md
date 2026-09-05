# 《Docker 与 Kubernetes》

> Part VI · 第 2/4 本 · 状态 🟡（目录已立）

## 定位

把「在我电脑上能跑」变成「在集群上可重复部署」：容器原理、镜像最佳实践、K8s 核心对象、伸缩与发布、CI/CD 流水线。这是交付能力的物理载体。

## 前置

Part I–III（会构建与测试），[《Spring Cloud Alibaba》](../book-01-spring-cloud-alibaba/README.md) 可并行。

## 章目录

- ch01 · 容器原理：namespace/cgroup 与镜像分层
- ch02 · Dockerfile 最佳实践（多阶段、缓存、瘦身）
- ch03 · Compose 编排（本地起一套服务）
- ch04 · K8s 核心对象：Pod/Deployment/Service
- ch05 · Ingress 与网关（流量如何进集群）
- ch06 · 配置与密钥：ConfigMap/Secret
- ch07 · 存储：PV/PVC 与有状态服务
- ch08 · 伸缩与调度：HPA、资源请求/限制、亲和性
- ch09 · 发布策略：滚动/蓝绿/金丝雀与回滚
- ch10 · Helm 打包与参数化
- ch11 · 本地开发环境：kind/minikube（低成本练习）
- ch12 · CI/CD 流水线：构建→镜像→部署的自动化
- ch13 · Java 应用上 K8s 的 JVM 参数与排障
- ch14 · 集群运维基本功：日志/事件/资源排查

## 收官实验

把一个 Part III/IV 的 Java 系统容器化并部署到本地 K8s，完成一次滚动发布与回滚，写清步骤可复现。

## 武器自限

本卷以部署为成果，禁止「截图证明」，一切要有可执行清单与命令。

## 关联

- 与 SCA、可观测书共同构成 Part VI 收官项目的交付底座。
