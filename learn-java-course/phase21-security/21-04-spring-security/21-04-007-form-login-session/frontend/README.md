# 第007课独立前端：表单登录与会话验证

[课程正文](../README.md) · [验证记录](../01-验证记录.md)

本项目在第六课CSRF页面上增加真正的登录表单。使用框架POST `/login`，发送表单编码与CSRF请求头，识别JSON结果后重新获取令牌；后续GET `/hello`只携带会话Cookie。页面显示本次尝试，不提供`/me`身份恢复和退出功能。

## 启动

先按正文准备JDK21、Maven3.9.9与三个`LESSON_*_PASSWORD`环境变量，在本课后端目录执行`mvn spring-boot:run`。后端监听127.0.0.1:8080，没有可用的默认实验密码。

另开终端，进入本目录，使用Node22.15.0、npm10.9.2：

```bash
npm ci
npm run dev
```

浏览器打开`http://127.0.0.1:5173`。Vite8.2.2代理`/api/*`到后端并移除前缀。首次学习请从[正文跟写步骤](../README.md)建立独立目录；这里是已经完成的快照。

## 页面操作与边界

1. 从新会话开始，等待令牌就绪；输入错误密码提交得到401，密码框清空，会话访问仍401。
2. 重新输入正确密码，点击或Enter提交；观察成功提示与令牌重新就绪。
3. 点击“携带会话访问 /hello”得到200；正常CSRF探针也应200。
4. 旧匿名按钮仍使用omit并得到401，不表示顶部会话退出；Basic仍是单次显式凭据验证。
5. 已登录后错误重登只表示本次失败，会话可能仍有效。刷新页面只清空本次提示，当前用户恢复由第008课实现。

`session-client.js`统一管理内存令牌和会话操作队列；登录POST与刷新GET连续执行，探针不插队。`csrf-lab.js`只适配探针界面，`login.js`适配登录表单和只读验证。请求使用same-origin Cookie；不把密码、Cookie或令牌写进URL、观察区及持久存储，也不自动重发登录。遇未知响应可先用只读按钮核对；登录成功但令牌刷新失败时，保留成功提示，手动重新获取令牌再提交。

同源开发代理不等于生产CORS配置。后端换端口时使用`LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`重启前端，并按正文改变后端端口。

## 构建与浏览器验证

在本目录执行，确保Maven使用JDK21：

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

可用`MAVEN_CMD=/绝对路径/mvn npm run test:e2e`指定Maven。脚本构建后端并执行50项后端测试，然后启动真实Java、Vite与Chromium，运行16项浏览器测试；测试账号密码只用于隔离测试进程。

脚本在忽略目录`.e2e-work/before-login/`生成默认表单响应对照工程，使用`tests/fixtures/BeforeLoginSecurityConfig.java.txt`恢复第六课配置。正确登录实际建立会话，但fetch因302不能确认结果；随后只读请求可验证200。完成版使用JSON处理器。另一个网络故障实验仅中断真实登录成功后的CSRF GET，不伪造登录成功。

覆盖表单编码、密码输入清空、Cookie会话、令牌刷新、失败恢复、默认跳转对照及390px键盘操作，并保留前课请求边界、Basic、MVC错误和CSRF场景。trace关闭，截图只展示已清空输入的状态；不发布含真实请求凭据的浏览器详情。
