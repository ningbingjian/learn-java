# 阶段21 应用安全与身份生态

[返回学习路线总导航](../00-学习路线总导航.md#stage-21) · [课程文档写作规范](../01-课程文档写作规范.md)

方向：建立应用防护、身份认证、授权与密钥管理能力。

目标：能够设计可信身份与访问控制体系，分析安全边界，并将防护融入开发和运行过程。

## 模块范围与关系

| 编号 | 模块 | 学习范围 |
| --- | --- | --- |
| 21-01 | Web 应用安全 | 威胁识别、攻击成因、防护机制与安全验证 |
| 21-02 | 密码学与 TLS | 密码协议、证书机制与代表性实现 |
| 21-03 | OAuth 2.0 与 OpenID Connect | 授权和身份协议、安全模型与交互语义 |
| 21-04 | [Spring Security](21-04-spring-security/README.md) | 认证授权、过滤与决策机制、核心源码、安全扩展与工程应用 |
| 21-05 | Spring 授权服务器 | 协议端点、令牌签发与授权服务实现 |
| 21-06 | Keycloak | 统一身份、认证联邦、授权与扩展 |
| 21-07 | Spring Session | 会话存储整合、请求协作与集群会话治理 |
| 21-08 | Vault | 秘密管理、认证与密钥生命周期 |

先具备 Web、HTTP 与 Spring MVC / Boot 的应用基础，再进入 Spring Security 的认证和授权。21-01、21-02 中的必要安全知识随实例补齐；学习外部登录和资源服务器前，先完成21-03的相关协议基础。编号表示归属，无需读完前一模块全部源码才能开始下一模块。

Spring Security 模块讲应用怎样消费身份、实施访问控制；授权服务器和 Keycloak 模块讲身份服务怎样提供这些能力；Spring Session 模块继续解决会话在不同应用实例之间的存储协作。

## 当前进度

已建立 [21-04模块入口](21-04-spring-security/README.md)、[逐课大纲](21-04-spring-security/00-模块学习大纲.md)、[版本基线](21-04-spring-security/01-版本基线.md)，并提供独立前端、浏览器联合验证，以及[第001课](21-04-spring-security/21-04-001-hello-spring-security/README.md)、[第002课](21-04-spring-security/21-04-002-explicit-security-filter-chain/README.md)、[第003课正文与源码](21-04-spring-security/21-04-003-api-authentication-errors/README.md)、[第004课正文与源码](21-04-spring-security/21-04-004-request-matching-boundaries/README.md)、[第005课正文与源码](21-04-spring-security/21-04-005-users-and-password-encoding/README.md)、[第006课正文与源码](21-04-spring-security/21-04-006-csrf-before-login/README.md)和[第007课正文与源码](21-04-spring-security/21-04-007-form-login-session/README.md)。Spring Security大纲已细化为八篇89课（80课主线、9课选修），第001—007课已实现，第一篇已齐备，第二篇已完成用户、CSRF准备与表单会话登录，第008课及后续课程仍待编写。其余模块目前仍为总纲规划，不代表已经提供课程正文。
