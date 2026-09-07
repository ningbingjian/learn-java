# 第005课独立前端：实验身份与单次认证

[课程正文](../README.md) · [验证记录](../01-验证记录.md) · [版本基线](../../01-版本基线.md)

提供member（普通用户）、support（客服）、admin（管理员）的身份说明与单次Basic验证。密码由本机后端启动时设置，没有前端内置密码；三者目前都能访问GET /hello，角色授权尚未实现。

## 运行

先按正文第二节在后端终端设置`LESSON_MEMBER_PASSWORD`、`LESSON_SUPPORT_PASSWORD`、`LESSON_ADMIN_PASSWORD`并启动本课后端；缺少变量时启动会失败。前端需要Node22.15.0、npm10.9.2，在本目录执行：

```bash
npm ci
npm run dev
```

打开`http://127.0.0.1:5173`。默认后端`http://127.0.0.1:8080`；`/api`代理去掉前缀后转发。先停止前课占用同一端口的服务；后端换端口时以`LESSON_BACKEND_URL=http://127.0.0.1:8081 npm run dev`重启前端。

## 身份与匿名对照

选择账号并输入对应实验密码，点击“验证本次身份”：正确200、错误401。构造请求头后清空输入，结果区不显示密码或Basic内容，不使用localStorage/sessionStorage保存凭据；身份请求不提供自动重试，失败后重新输入。`autocomplete=off`只是浏览器提示，不能保证所有密码管理器行为一致。

Basic的Base64不是加密；这里只做本机回环地址实验，真实环境必须HTTPS。不要复制或分享Network中的Authorization。应用不主动保存凭据也不代表JavaScript内存已经安全擦除。

原有公开、受保护、参数错误、近似、未知、OPTIONS按钮均匿名；身份成功后再点受保护按钮仍401。`credentials: omit`不删除显式Authorization，代码只在身份按钮设置该头。这里没有会话登录或身份恢复。

`method=post`防止表单在脚本未加载时默认GET把输入写进URL；正常流程阻止原生提交，用fetch发送单次GET验证。Vite沿用`server.cors: false`，让同源OPTIONS实际进入代理，不代表生产CORS配置。

## 构建与验证

本目录执行：

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

Java/Maven也必须满足基线；可用`JAVA_HOME`与`MAVEN_CMD`指定位置。测试自行传入只用于测试的密码，不需要设置你的实验密码。准备脚本先运行本课32项后端测试，再在`.e2e-work/before-users/`移除显式用户配置，生成Boot默认用户对照，不改正常工程。

10项真实Chromium联合测试验证默认/显式用户、三种身份、错误密码与输入清空、不持久保存、匿名恢复，以及继承的路径、方法、错误响应、网络故障和窄屏键盘。只有专门的格式降级用例注入异常响应。

测试使用空闲端口并在结束后关闭服务。关闭网络追踪留存，避免Basic与输入进入追踪包；截图在密码框已清空时生成。生成目录`.e2e-work/`、`test-results/`、`node_modules/`、`dist/`均不提交。生产静态站点和API路由部署未在本课验证。
