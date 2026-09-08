# 第006课独立前端：CSRF初始化与提交

[课程正文](../README.md) · [验证记录](../01-验证记录.md) · [版本基线](../../01-版本基线.md)

新增独立CSRF观察区：加载时GET `/api/csrf`，手动POST `/api/csrf-probe`，对照合法、缺失、错误令牌与不带Cookie的请求。探针不修改业务数据，也不登录用户。

## 启动

先按正文准备三个`LESSON_*_PASSWORD`环境变量，在本课后端目录启动服务。前端需要Node22.15.0、npm10.9.2，在本目录执行：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`，默认后端8080；先停止前课占用同一端口的进程。若后端改用8081，在本目录使用`LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`重启前端。

Vite去掉`/api`前缀后转发；CSRF初始化和提交使用`credentials: same-origin`，由浏览器管理会话Cookie。继续采用默认HttpSessionCsrfTokenRepository，没有XSRF-TOKEN Cookie或CookieCsrfTokenRepository。开发代理不代表已配置生产CORS。

## 操作与恢复

初始化成功后正常提交200；缺令牌、错令牌和不带Cookie均403，探针方法不执行。点击正常提交恢复200。清除站点Cookie后旧令牌提交403，正常提交按钮禁用；重新取得令牌后再手动提交恢复。

初始化失败不会自动提交，POST网络失败也不自动重发，因为响应缺失不能证明请求没有被处理。故意错误模式只用于课堂对照，正常模式会携带同会话令牌。

令牌只保留在模块内存，不显示内容、不写URL或localStorage/sessionStorage；HttpOnly会话Cookie不由JavaScript读取。Network仅检查头是否存在，不复制或分享令牌与Cookie值。本课不是XSS或生产部署的完整方案。

第五课匿名/Basic观察区保留原有`credentials: omit`条件，不自动使用CSRF区Cookie。单次Basic成功不是完整会话登录；本课尚未实现独立前端登录提交。

## 构建与自动验证

本目录执行：

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

Java/Maven也需满足基线；可设置`JAVA_HOME`、`MAVEN_CMD`指定可执行文件。准备脚本使用测试属性，先运行本课41项后端测试，再在`.e2e-work/before-csrf/`移除本课Controller与授权例外，验证未开放令牌入口时初始化失败；正式源码保持完成状态。

13项Chromium联合测试涵盖真实CSRF请求头/Cookie与方法执行计数、错误对照、清Cookie失效/恢复、离线初始化与390像素键盘操作，并保留前课匿名/Basic、错误响应、方法边界及网络恢复验证。只有专门的格式降级测试注入响应，CSRF场景全部请求真实后端。

使用随机空闲端口，结束关闭服务。网络追踪留存关闭，截图只显示状态、不展示令牌；生成目录`.e2e-work/`、`test-results/`、`node_modules/`、`dist/`均不提交。生产部署与浏览器直连跨源交互未验证。
