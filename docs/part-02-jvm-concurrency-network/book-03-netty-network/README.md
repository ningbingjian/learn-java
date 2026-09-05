# 《Netty 与高性能网络》

> Part II · 第 3/3 本 · 状态 🟡（目录已立）

## 定位

网络编程从 BIO/NIO 到 Reactor 再到 Netty，讲透高性能服务端为何长这样。**Netty 是通信基座而非分布式中间件**——学会它，等于拿到读 Dubbo/网关/RocketMQ/gRPC 底层的一把钥匙。

## 前置

[《进阶机制》](../../part-01-language-foundation/book-03-java-core-advanced/README.md) 的 NIO 章、[《Java 并发编程》](../book-02-java-concurrency/README.md)。

## 章目录

- ch01 · 网络 IO 模型演进（BIO→NIO→多路复用→Reactor）
- ch02 · Java NIO 深入（Channel/Buffer/Selector 手写 echo）
- ch03 · Reactor 线程模型（单/多 Reactor、主从）
- ch04 · EventLoop 与线程模型陷阱（不要在 IO 线程阻塞）
- ch05 · ByteBuf（池化、零拷贝、引用计数）
- ch06 · 编解码与粘包拆包（定长/分隔/长度域）
- ch07 · ChannelPipeline 与入站出站
- ch08 · 自写协议：设计一个长度域协议并编解码
- ch09 · 连接与空闲管理、写缓冲水位
- ch10 · 背压与高并发连接（百万连接怎么来）
- ch11 · 实战：基于 Netty 的 HTTP/IM 服务器
- ch12 · Netty 与 Tomcat/虚拟线程：各自定位
- ch13 · 读框架源码的方法论（用它读 Dubbo/网关）

## 收官实验

基于 Netty 自写一个迷你 RPC（注册/调用/编解码），压测；为 Part VI 读 Dubbo/网关源码铺路。

## 武器自限

收官实验允许 Netty（这本就是它的主场）；自定义协议、编解码要自己写，不抄现成 RPC。

## 关联

- 打通 Part VI：Spring Cloud Gateway 底层就是 Netty/WebFlux。
