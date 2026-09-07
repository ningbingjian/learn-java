# 第003课独立前端：认证失败与MVC错误观察台

[第三课正文](../README.md) · [验证记录](../01-验证记录.md) · [模块入口](../../README.md) · [版本基线](../../01-版本基线.md)

使用原生HTML、JavaScript与Vite，保留公开和受保护按钮，增加业务参数错误对照、错误代码及显式重试。页面以真实HTTP状态为依据，识别约定JSON时显示说明，同时保留原始响应。

## 启动与观察

先在本课目录（上一级）使用Java21与Maven3.9.9执行 `mvn spring-boot:run`。在本目录执行：

```bash
npm ci
npm run dev
```

使用Node.js22.15.0、npm10.9.2、Vite8.2.2，打开 `http://127.0.0.1:5173`。默认Java后端监听 `127.0.0.1:8080`；先停止占用相同端口的前两课服务。

| 操作 | 浏览器路径 | 正常完成版结果 |
| --- | --- | --- |
| 访问公开信息 | /api/public/info | 200 |
| 访问受保护接口 | /api/hello | 401及AUTHENTICATION_REQUIRED |
| 查看参数错误对照 | /api/public/info?lang=unknown | 400及UNSUPPORTED_LANGUAGE |
| 重试本次请求 | 使用本次保存的路径 | 条件未变时错误仍可能存在 |

浏览器仍使用JSON Accept、不携带Cookie/Authorization。Vite去掉 `/api`前缀转发，保留查询参数；后端错误状态与挑战透传。本课不验证浏览器直连后端的CORS，页面也不提供登录、角色或身份存储。

需要改端口时，前端目录执行 `LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`，同时改变后端端口；前端端口可用 `npm run dev -- --port 5174`调整。代理目标修改后需重启Vite。

## 错误显示与恢复

先显示原始HTTP状态和响应体，再核对Content-Type、已知错误代码、状态对应关系和非空说明。识别失败时使用降级提示，原文通过 `textContent`保留；JSON解析失败不被当作网络断开。

重试复用本次GET路径，不改变凭据或参数。参数错误需要改正输入：点击不带非法参数的公开按钮恢复200。后端真实停止时显示代理502且不显示认证错误代码；重启后重试受保护请求恢复401 JSON。浏览器离线时没有完整响应，恢复网络后可以重试。

## 构建与测试

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

构建生成的 `dist/`不包含Java服务和Vite开发代理。普通学习仅需开发服务；自动化验证需要Java21、Maven3.9.9与Playwright1.63.0匹配的Chromium，可通过 `JAVA_HOME`和 `MAVEN_CMD`指定本机工具。

联合脚本先构建完成版并运行19项后端HTTP测试，再在 `.e2e-work/before-contract`保留MVC处理器和参数校验、恢复第二课安全配置，形成仅MVC处理器的对照工程。固定配置夹具位于 `tests/fixtures/BeforeContractSecurityConfig.java.txt`，来自第二课且只改包名；不会作为完成版Java源文件扫描。

8项浏览器测试覆盖MVC对照、真实401契约与重试、真实400及修复、HTML登录页、真实后端停机恢复、浏览器离线恢复、异常响应格式降级、390像素和键盘。前六项及窄屏测试均使用真实服务响应；格式降级用例明确注入损坏JSON、HTML、状态与代码不符、未知代码及非字符串代码，只证明前端识别边界。

测试使用空闲端口，结束后关闭Java及Vite。日志位于 `.e2e-work/`，截图与失败追踪位于 `test-results/`，均不提交；实际结果见[验证记录](../01-验证记录.md)。
