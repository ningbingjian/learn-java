# 第008课独立前端：当前用户与刷新恢复

[课程正文](../README.md) · [验证记录](../01-验证记录.md)

本项目在第七课表单登录上增加当前身份区。页面启动、刷新、每次登录尝试后以及手动查询时请求GET `/api/me`，使用同源会话Cookie，以后端结果显示用户名和权限标识。登录选择框仍只表示下一次准备提交的账号，不代表当前用户。

## 启动与学习入口

后端使用JDK21、Maven3.9.9。先在本课后端目录按正文设置三个`LESSON_*_PASSWORD`环境变量，再执行`mvn spring-boot:run`，默认127.0.0.1:8080。没有可用的默认实验密码。

另开终端进入本目录，使用Node22.15.0和npm10.9.2：

```bash
npm ci
npm run dev
```

浏览器打开`http://127.0.0.1:5173`。Vite8.2.2把同源`/api/*`请求转发到后端并移除前缀。第一次学习请从[正文](../README.md)复制第七课建立独立跟写目录；此目录是已完成快照。

## 操作与状态

1. 新匿名会话打开页面，/me返回401，身份区显示未登录。
2. 正确登录member后，依次更新CSRF和查询/me，显示member、FACTOR_PASSWORD与ROLE_MEMBER。
3. 刷新页面仍恢复身份，不自动提交密码；修改账号选择框不改变当前会话。
4. 已登录member后错误尝试admin，本次登录失败，但/me继续显示服务端实际的member。
5. 清除本实验站点Cookie再查询，撤下旧身份并显示未登录；重新登录前手动重新取得令牌。
6. 查询时离线或响应格式异常，显示暂时无法确认并清理旧字段；恢复条件后手动查询。

共享`session-client.js`继续串行管理会话操作，新增四种身份阶段。`current-user.js`只用textContent展示服务端投影，不信任页面文字、选择框或localStorage；`login.js`与匿名/Basic/CSRF观察区仍保留。旧匿名按钮使用omit，其401不等于携带Cookie的当前会话失效。

后端权限列表包含认证因素和角色。FACTOR_PASSWORD是当前Security7.0.7密码认证的标识，不是新增业务角色；本课尚未实现工单角色授权、退出界面或跨标签页实时同步。

## 构建与验证

在本目录执行，Maven须使用JDK21：

```bash
npm run build
npx playwright install chromium
npm run test:e2e
```

可通过`MAVEN_CMD=/绝对路径/mvn npm run test:e2e`指定Maven。脚本先运行58项后端测试并构建，再启动真实Java、Vite与Chromium执行22项浏览器测试。默认表单302对照在忽略目录`.e2e-work/before-login/`中独立构建，保留本课/me，仅恢复默认登录响应以验证第七课回归。

新增场景包括刷新不重发密码、真实Cookie恢复、延迟查询的loading、清Cookie后的401、离线恢复、畸形响应修复、伪造页面和存储不授予身份、失败重登以及390px键盘操作。延迟和格式响应是明确的测试注入，身份恢复与无会话401使用真实后端。

trace关闭，截图只展示清空密码后的页面；不发布包含密码、Cookie或CSRF令牌的Network详情。这里只验证本机同源代理，未完成生产跨源Cookie配置。
