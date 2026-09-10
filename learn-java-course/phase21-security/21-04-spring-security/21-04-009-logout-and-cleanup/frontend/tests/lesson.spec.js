import { test, expect } from '@playwright/test';
import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { lessonConfig } from '../vite.config.js';

const frontend = process.cwd();
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
const jarName = 'spring-security-lesson009-1.0-SNAPSHOT.jar';
let before, secured, beforeUi, securedUi;

async function startBackend(jar, port = 0) {
  const state = { logs: '', port: null, process: spawn(java, ['-jar', jar, `--server.port=${port}`, '--spring.security.user.password=lesson009-default-test',
    '--lesson.users.member-password=lesson009-test-only', '--lesson.users.support-password=lesson009-support-test',
    '--lesson.users.admin-password=lesson009-admin-test']) };
  await new Promise((resolveReady, reject) => {
    const timeout = setTimeout(() => { state.process.kill(); reject(new Error('后端启动超时')); }, 45000);
    state.process.on('error', (error) => { clearTimeout(timeout); reject(error); });
    state.process.on('exit', (code) => {
      clearTimeout(timeout);
      if (state.port === null) reject(new Error(`后端启动前退出：${code}`));
    });
    const consume = (chunk) => {
      state.logs += chunk.toString();
      const match = state.logs.match(/Tomcat started on port (\d+)/);
      if (match && state.port === null) {
        state.port = Number(match[1]); clearTimeout(timeout); resolveReady();
      }
    };
    state.process.stdout.on('data', consume);
    state.process.stderr.on('data', consume);
  });
  return state;
}

async function stopBackend(state) {
  if (!state || state.process.exitCode !== null || state.process.signalCode !== null) return;
  await new Promise((done) => {
    const timeout = setTimeout(() => state.process.kill('SIGKILL'), 10000);
    state.process.once('exit', () => { clearTimeout(timeout); done(); });
    state.process.kill('SIGTERM');
  });
}

async function startUi(port) {
  const config = lessonConfig(`http://127.0.0.1:${port}`);
  const server = await createServer({
    ...config, configFile: false, root: frontend, logLevel: 'silent',
    server: { ...config.server, port: 0 },
  });
  await server.listen();
  return { server, url: `http://127.0.0.1:${server.httpServer.address().port}` };
}

const calls = (state, marker) => state.logs.split(marker).length - 1;

test.describe.configure({ mode: 'serial' });
test.beforeAll(async () => {
  test.setTimeout(120000);
  before = await startBackend(resolve(frontend, '.e2e-work/before-login/21-04-009-logout-and-cleanup/target', jarName));
  secured = await startBackend(resolve(frontend, '../target', jarName));
  beforeUi = await startUi(before.port);
  securedUi = await startUi(secured.port);
});

test.afterAll(async () => {
  await Promise.all([beforeUi?.server.close(), securedUi?.server.close()]);
  await Promise.all([stopBackend(before), stopBackend(secured)]);
  // 只记录不含密码的业务执行计数。
  writeFileSync(resolve(frontend, '.e2e-work/browser-summary.json'), JSON.stringify({
    beforeLoginPublicCalls: before ? calls(before, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    completedPublicCalls: secured ? calls(secured, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    completedHelloCalls: secured ? calls(secured, 'HELLO_HANDLER_REACHED') : null,
    apiErrorCalls: secured ? calls(secured, 'API_AUTHENTICATION_REQUIRED') : null,
    mvcErrorCalls: secured ? calls(secured, 'MVC_ERROR_HANDLED') : null,
  }, null, 2));
});

test('默认登录响应：浏览器禁止跳转后结果不明，但会话可能已经建立', async ({ page }) => {
  await page.goto(beforeUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-submit').click();
  await expect(page.locator('#login-result')).toContainText('无法确认本次登录结果');
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 200');
});

test('认证错误JSON：保留原始401、挑战、匿名条件及安全链执行证据', async ({ page, context }, info) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  await page.goto(securedUi.url);
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  const auth = calls(secured, 'API_AUTHENTICATION_REQUIRED');
  const mvc = calls(secured, 'MVC_ERROR_HANDLED');
  const hello = calls(secured, 'HELLO_HANDLER_REACHED');
  const pending = page.waitForResponse((r) => r.url().endsWith('/api/hello'));
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  const r = await pending;
  expect(r.status()).toBe(401);
  expect(r.headers()['www-authenticate']).toBe('Basic realm="Realm"');
  expect(r.headers()['content-type']).toContain('application/json');
  const headers = await r.request().allHeaders();
  expect(headers.accept).toBe('application/json');
  expect(headers.cookie).toBeUndefined();
  expect(headers.authorization).toBeUndefined();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#error-code')).toHaveText('AUTHENTICATION_REQUIRED');
  await expect(page.locator('#result-summary')).toHaveText('未通过身份认证，请检查凭据后重试。');
  await expect.poll(() => calls(secured, 'API_AUTHENTICATION_REQUIRED')).toBe(auth + 1);
  expect(calls(secured, 'MVC_ERROR_HANDLED')).toBe(mvc);
  expect(calls(secured, 'HELLO_HANDLER_REACHED')).toBe(hello);
  await page.getByRole('button', { name: '重试本次请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect.poll(() => calls(secured, 'API_AUTHENTICATION_REQUIRED')).toBe(auth + 2);
  await page.screenshot({ path: info.outputPath('api-401.png'), fullPage: true });
});

test('MVC参数400与修复：方法和Advice执行，公开正常请求仍200', async ({ page }, info) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  const mvc = calls(secured, 'MVC_ERROR_HANDLED');
  const auth = calls(secured, 'API_AUTHENTICATION_REQUIRED');
  const publicCalls = calls(secured, 'PUBLIC_INFO_HANDLER_REACHED');
  await page.getByRole('button', { name: '查看参数错误对照' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 400');
  await expect(page.locator('#error-code')).toHaveText('UNSUPPORTED_LANGUAGE');
  await expect(page.locator('#result-summary')).toContainText('lang只支持en或zh');
  await expect.poll(() => calls(secured, 'MVC_ERROR_HANDLED')).toBe(mvc + 1);
  await expect.poll(() => calls(secured, 'PUBLIC_INFO_HANDLER_REACHED')).toBe(publicCalls + 1);
  expect(calls(secured, 'API_AUTHENTICATION_REQUIRED')).toBe(auth);
  await page.screenshot({ path: info.outputPath('mvc-400.png'), fullPage: true });
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await expect(page.locator('#error-code')).toHaveText('—');
  await expect(page.locator('#response-body')).toContainText('Public information');
  await expect(page.getByRole('button', { name: '重试本次请求' })).toBeHidden();
});

test('HTML页面导航仍到框架登录页', async ({ page }) => {
  await page.goto(`http://127.0.0.1:${secured.port}/hello`);
  await expect(page).toHaveURL(`http://127.0.0.1:${secured.port}/login`);
  await expect(page.locator('input[name="username"]')).toBeVisible();
  await expect(page.locator('input[name="_csrf"]')).toHaveCount(1);
});

test('真实后端停止与恢复：代理502不是认证错误，重试恢复401', async ({ page }, info) => {
  const port = secured.port;
  await page.goto(securedUi.url);
  await stopBackend(secured);
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 502');
  await expect(page.locator('#error-code')).toHaveText('—');
  await expect(page.locator('#response-source')).toHaveText('开发代理：未取得后端响应');
  await page.screenshot({ path: info.outputPath('proxy-502.png'), fullPage: true });
  secured = await startBackend(resolve(frontend, '../target', jarName), port);
  await page.getByRole('button', { name: '重试本次请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#error-code')).toHaveText('AUTHENTICATION_REQUIRED');
});

test('浏览器离线后原路径重试恢复公开200', async ({ page, context }) => {
  await page.goto(securedUi.url);
  await context.setOffline(true);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('未取得完整响应');
  await expect(page.locator('#error-code')).toHaveText('—');
  await context.setOffline(false);
  await page.getByRole('button', { name: '重试本次请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await expect(page.locator('#request-path')).toHaveText('GET /api/public/info');
});

test('响应格式异常：保留真实状态和原文，不误报网络失败', async ({ page }) => {
  await page.goto(securedUi.url);
  // 此项明确注入异常响应，只验证前端降级；真实认证契约由前面的联合测试验证。
  for (const [status, contentType, body] of [
    [401, 'application/json', '{broken'],
    [401, 'text/html', '<h1>not JSON</h1>'],
    [401, 'application/json', '{"code":"UNSUPPORTED_LANGUAGE","message":"不应显示"}'],
    [400, 'application/json', '{"code":"UNKNOWN","message":"不应显示"}'],
    [401, 'application/json', '{"code":["AUTHENTICATION_REQUIRED"],"message":"不应显示"}'],
  ]) {
    await page.route('**/api/hello', (route) => route.fulfill({ status, contentType, body }));
    await page.getByRole('button', { name: '访问受保护接口' }).click();
    await expect(page.locator('#status-code')).toHaveText(`HTTP ${status}`);
    await expect(page.locator('#error-code')).toHaveText('—');
    await expect(page.locator('#result-summary')).toContainText('未提供可识别的错误契约');
    await expect(page.locator('#response-body')).toHaveText(body);
    await page.unroute('**/api/hello');
  }
});

test('窄屏与键盘：错误和重试入口可操作且没有横向溢出', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(securedUi.url);
  await page.getByRole('button', { name: '查看参数错误对照' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status-code')).toHaveText('HTTP 400');
  await page.getByRole('button', { name: '重试本次请求' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status-code')).toHaveText('HTTP 400');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('mobile-400.png'), fullPage: true });
});

test('路径边界与方法：匿名近似和未知401，OPTIONS重试保持方法', async ({ page }, info) => {
  await page.goto(securedUi.url);
  for (const name of ['访问近似路径', '访问未知路径']) {
    await page.getByRole('button', { name, exact: true }).click();
    await expect(page.locator('#status-code')).toHaveText('HTTP 401');
    await expect(page.locator('#error-code')).toHaveText('AUTHENTICATION_REQUIRED');
  }
  const pending = page.waitForResponse(r => r.url().endsWith('/api/public/info') && r.request().method() === 'OPTIONS');
  await page.getByRole('button', { name: '用 OPTIONS 访问公开路径', exact: true }).click();
  expect((await pending).status()).toBe(401);
  await expect(page.locator('#request-path')).toHaveText('OPTIONS /api/public/info');
  const retry = page.waitForResponse(r => r.url().endsWith('/api/public/info') && r.request().method() === 'OPTIONS');
  await page.getByRole('button', { name: '重试本次请求' }).click();
  expect((await retry).status()).toBe(401);
  await expect(page.locator('#request-path')).toHaveText('OPTIONS /api/public/info');
  await page.screenshot({ path: info.outputPath('method-boundary.png'), fullPage: true });
});

test('三种身份、错误密码与清空：凭据不进入观察区或存储，下一次匿名仍401', async ({ page }, info) => {
  await page.goto(securedUi.url);
  for (const [name, password] of [['member', 'lesson009-test-only'], ['support', 'lesson009-support-test'], ['admin', 'lesson009-admin-test']]) {
    await page.locator('#identity-name').selectOption(name);
    await page.locator('#identity-password').fill(password);
    await page.getByRole('button', { name: '验证本次身份' }).click();
    await expect(page.locator('#status-code')).toHaveText('HTTP 200');
    await expect(page.locator('#identity-password')).toHaveValue('');
    await expect(page.locator('.result-panel')).not.toContainText(password);
    await expect(page.locator('.result-panel')).not.toContainText(btoa(`${name}:${password}`));
    await expect(page.locator('#retry-request')).toBeHidden();
  }
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  await page.locator('#identity-password').fill('wrong-password');
  await page.getByRole('button', { name: '验证本次身份' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#identity-password')).toHaveValue('');
  await expect(page.locator('#retry-request')).toBeHidden();
  await page.getByRole('button', { name: '访问受保护接口', exact: true }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#request-conditions')).toContainText('不携带身份凭据');
  await page.screenshot({ path: info.outputPath('identity-cleared.png'), fullPage: true });
});

test('真实CSRF：同会话令牌200，缺失、错误及无Cookie403且不执行实验方法', async ({ page, context }, info) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  const cookies = await context.cookies(securedUi.url);
  expect(cookies.some(c => c.name === 'JSESSIONID' && c.httpOnly)).toBe(true);
  const initial = calls(secured, 'CSRF_PROBE_HANDLER_REACHED');
  const pending = page.waitForResponse(r => r.url().endsWith('/api/csrf-probe'));
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  const success = await pending;
  expect(success.status()).toBe(200);
  const headers = await success.request().allHeaders();
  expect(headers['x-csrf-token']).toBeTruthy();
  expect(headers.cookie).toContain('JSESSIONID=');
  expect(headers.authorization).toBeUndefined();
  await expect.poll(() => calls(secured, 'CSRF_PROBE_HANDLER_REACHED')).toBe(initial + 1);
  for (const name of ['故意不带令牌', '故意带错误令牌', '故意不带会话Cookie']) {
    const response = page.waitForResponse(r => r.url().endsWith('/api/csrf-probe'));
    await page.getByRole('button', { name, exact: true }).click();
    const r = await response;
    expect(r.status()).toBe(403);
    if (name === '故意不带会话Cookie') expect((await r.request().allHeaders()).cookie).toBeUndefined();
    await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 403');
    expect(calls(secured, 'CSRF_PROBE_HANDLER_REACHED')).toBe(initial + 1);
  }
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
  await expect.poll(() => calls(secured, 'CSRF_PROBE_HANDLER_REACHED')).toBe(initial + 2);
  await expect(page.locator('body')).not.toContainText(headers['x-csrf-token']);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  await page.screenshot({ path: info.outputPath('csrf-repaired.png'), fullPage: true });
});

test('清除Cookie使缓存令牌失效：拒绝后重新取得，手动提交修复', async ({ page, context }) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  await context.clearCookies();
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 403');
  await expect(page.locator('#csrf-state')).toContainText('可能已失效');
  await expect(page.getByRole('button', { name: '携带令牌提交', exact: true })).toBeDisabled();
  const before = calls(secured, 'CSRF_PROBE_HANDLER_REACHED');
  await page.getByRole('button', { name: '重新取得令牌', exact: true }).click();
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  expect(calls(secured, 'CSRF_PROBE_HANDLER_REACHED')).toBe(before);
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
});

test('CSRF初始化离线失败可恢复；窄屏键盘POST可用且无凭据回显', async ({ page, context }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(securedUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  await context.setOffline(true);
  await page.getByRole('button', { name: '重新取得令牌', exact: true }).click();
  await expect(page.locator('#csrf-state')).toHaveText('未取得令牌');
  await expect(page.getByRole('button', { name: '携带令牌提交', exact: true })).toBeDisabled();
  await context.setOffline(false);
  await page.getByRole('button', { name: '重新取得令牌', exact: true }).click();
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('csrf-mobile.png'), fullPage: true });
});

test('真实表单登录：编码、会话轮换、令牌刷新与匿名对照', async ({ page, context }, info) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  const oldCookie = (await context.cookies(securedUi.url)).find(c => c.name === 'JSESSIONID').value;
  await page.locator('#login-password').fill('lesson009-test-only');
  const pending = page.waitForResponse(r => r.url().endsWith('/api/login'));
  await page.locator('#login-submit').click();
  const response = await pending;
  expect(response.status()).toBe(200);
  expect(response.headers().location).toBeUndefined();
  const headers = await response.request().allHeaders();
  expect(headers['content-type']).toContain('application/x-www-form-urlencoded');
  expect(headers.authorization).toBeUndefined();
  expect(headers['x-csrf-token']).toBeTruthy();
  expect(new URLSearchParams(response.request().postData()).get('username')).toBe('member');
  expect(new URL(response.url()).search).toBe('');
  await expect(page.locator('#login-result')).toContainText('本次登录成功');
  await expect(page.locator('#login-result')).toContainText('新CSRF令牌已就绪');
  await expect(page.locator('#login-password')).toHaveValue('');
  expect((await context.cookies(securedUi.url)).find(c => c.name === 'JSESSIONID').value).not.toBe(oldCookie);
  const hello = page.waitForResponse(r => r.url().endsWith('/api/hello'));
  await page.locator('#session-hello').click();
  const protectedResponse = await hello;
  expect(protectedResponse.status()).toBe(200);
  expect((await protectedResponse.request().allHeaders()).authorization).toBeUndefined();
  await expect(page.locator('#session-result')).toContainText('HTTP 200');
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
  await page.getByRole('button', { name: '访问受保护接口', exact: true }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('body')).not.toContainText('lesson009-test-only');
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  await page.screenshot({ path: info.outputPath('form-session.png'), fullPage: true });
});

test('错误密码401后重新输入成功，窄屏Enter登录', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(securedUi.url);
  await expect(page.locator('#login-submit')).toBeEnabled();
  await page.locator('#login-password').fill('wrong-password');
  await page.locator('#login-password').press('Enter');
  await expect(page.locator('#login-status')).toHaveText('HTTP 401');
  await expect(page.locator('#login-result')).toContainText('本次登录失败');
  await expect(page.locator('#login-password')).toHaveValue('');
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 401');
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-password').press('Enter');
  await expect(page.locator('#login-result')).toContainText('本次登录成功');
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 200');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('form-mobile.png'), fullPage: true });
});

test('真实登录成功但令牌刷新网络失败：保留成功，允许只读验证并显式修复', async ({ page }) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#login-submit')).toBeEnabled();
  // 只注入后续GET令牌的网络故障，登录POST仍发送到真实后端。
  await page.route('**/api/csrf', route => route.abort('failed'));
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-submit').click();
  await expect(page.locator('#login-result')).toContainText('本次登录成功');
  await expect(page.locator('#login-result')).toContainText('尚未取得新CSRF令牌');
  await expect(page.locator('#login-submit')).toBeDisabled();
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 200');
  await page.unroute('**/api/csrf');
  await page.locator('#csrf-init').click();
  await expect(page.locator('#login-submit')).toBeEnabled();
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
});

async function loginMember(page) {
  await page.goto(securedUi.url);
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-submit').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
}

test('身份恢复：刷新不重发密码，只带会话查询服务端身份', async ({ page }, info) => {
  await loginMember(page);
  let logins = 0;
  page.on('request', request => { if (request.url().endsWith('/api/login')) logins += 1; });
  const me = page.waitForResponse(response => response.url().endsWith('/api/me'));
  await page.reload();
  const response = await me;
  expect(response.status()).toBe(200);
  expect((await response.request().allHeaders()).authorization).toBeUndefined();
  expect(response.headers()['cache-control']).toContain('no-store');
  await expect(page.locator('#current-user-name')).toHaveText('member');
  await expect(page.locator('#current-user-authorities')).toHaveText('FACTOR_PASSWORD、ROLE_MEMBER');
  await expect(page.locator('#login-password')).toHaveValue('');
  expect(logins).toBe(0);
  await page.screenshot({ path: info.outputPath('identity-restored.png'), fullPage: true });
});

test('加载状态撤下旧身份，401清理用户名和权限', async ({ page, context }) => {
  await loginMember(page);
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route('**/api/me', async route => { await gate; await route.continue(); });
  await context.clearCookies();
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'loading');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  await expect(page.locator('#current-user-authorities')).toHaveText('—');
  release();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await page.unroute('**/api/me');
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 401');
});

test('网络故障是身份未知，恢复后手动查询真实会话', async ({ page, context }) => {
  await loginMember(page);
  await context.setOffline(true);
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'unavailable');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  await expect(page.locator('#current-user-status')).not.toContainText('401');
  await context.setOffline(false);
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
});

test('格式异常不能保留旧身份，原路径修复后恢复', async ({ page }) => {
  await loginMember(page);
  await page.route('**/api/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"username":"admin","authorities":"ROLE_ADMIN"}' }));
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'unavailable');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  await page.unroute('**/api/me');
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
});

test('伪造页面与存储不能授予身份，未带会话的后端仍401', async ({ page }) => {
  await page.goto(securedUi.url);
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await page.evaluate(() => {
    localStorage.setItem('currentUser', JSON.stringify({ username: 'admin', authorities: ['ROLE_ADMIN'] }));
    document.querySelector('#current-user-name').textContent = 'admin';
  });
  const status = await page.evaluate(async () => (await fetch('/api/me', { credentials: 'omit', headers: { Accept: 'application/json' } })).status);
  expect(status).toBe(401);
  await page.reload();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  await page.evaluate(() => localStorage.removeItem('currentUser'));
});

test('选择其他账号并失败重登，当前身份仍取自服务端；窄屏键盘恢复', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginMember(page);
  await page.locator('#login-name').selectOption('admin');
  await expect(page.locator('#current-user-name')).toHaveText('member');
  await page.locator('#login-password').fill('wrong');
  await page.locator('#login-password').press('Enter');
  await expect(page.locator('#login-status')).toHaveText('HTTP 401');
  await expect(page.locator('#current-user-name')).toHaveText('member');
  await page.locator('#current-user-refresh').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'authenticated');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('identity-mobile.png'), fullPage: true });
});


test('真实退出：204、页面清理、旧Cookie拒绝、新令牌与再次登录', async ({ page, context }, info) => {
  await loginMember(page);
  const oldCookie = (await context.cookies(securedUi.url)).find(c => c.name === 'JSESSIONID').value;
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 200');
  await page.locator('#login-password').fill('not-submitted');
  await page.locator('#identity-password').fill('not-submitted');
  const count = calls(secured, 'LOGOUT_HANDLER_REACHED');
  const pending = page.waitForResponse(r => r.url().endsWith('/api/logout'));
  await page.locator('#logout-submit').click();
  const response = await pending;
  expect(response.status()).toBe(204);
  expect(response.request().method()).toBe('POST');
  expect((await response.request().allHeaders())['x-csrf-token']).toBeTruthy();
  expect((await response.request().allHeaders()).authorization).toBeUndefined();
  await expect(page.locator('#logout-result')).toContainText('本次退出已完成');
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  await expect(page.locator('#login-password')).toHaveValue('');
  await expect(page.locator('#identity-password')).toHaveValue('');
  await expect(page.locator('#session-result')).not.toContainText('HTTP 200');
  await expect.poll(() => calls(secured, 'LOGOUT_HANDLER_REACHED')).toBe(count + 1);
  const replay = await fetch(`http://127.0.0.1:${secured.port}/me`, { headers: { Accept: 'application/json', Cookie: `JSESSIONID=${oldCookie}` } });
  expect(replay.status).toBe(401);
  expect((await context.cookies(securedUi.url)).find(c => c.name === 'JSESSIONID').value).not.toBe(oldCookie);
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 401');
  await page.getByRole('button', { name: '携带令牌提交', exact: true }).click();
  await expect(page.locator('#csrf-http-status')).toHaveText('HTTP 200');
  await page.screenshot({ path: info.outputPath('logout-complete.png'), fullPage: true });
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-submit').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
});

test('退出CSRF缺失真实403不清理服务器身份，手动修复退出', async ({ page }) => {
  await loginMember(page);
  const count = calls(secured, 'LOGOUT_HANDLER_REACHED');
  await page.route('**/api/logout', route => {
    const headers = { ...route.request().headers() }; delete headers['x-csrf-token'];
    return route.continue({ headers });
  });
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-status')).toHaveText('HTTP 403');
  await expect(page.locator('#logout-result')).toContainText('退出被拒绝');
  await expect(page.locator('#current-user-name')).toHaveText('member');
  expect(calls(secured, 'LOGOUT_HANDLER_REACHED')).toBe(count);
  await page.unroute('**/api/logout');
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-status')).toHaveText('HTTP 204');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
});

test('退出204后令牌GET失败，保留已退出结论并手动重新准备', async ({ page }) => {
  await loginMember(page);
  await page.route('**/api/csrf', route => route.abort('failed'));
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-result')).toContainText('本次退出已完成');
  await expect(page.locator('#logout-result')).toContainText('尚未取得新令牌');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  await expect(page.locator('#login-submit')).toBeDisabled();
  await page.locator('#session-hello').click();
  await expect(page.locator('#session-result')).toContainText('HTTP 401');
  await page.unroute('**/api/csrf');
  await page.locator('#csrf-init').click();
  await expect(page.locator('#login-submit')).toBeEnabled();
});

test('真实退出已执行但响应丢失：不自动重发，通过me核对匿名', async ({ page }) => {
  await loginMember(page);
  let posts = 0;
  const count = calls(secured, 'LOGOUT_HANDLER_REACHED');
  await page.route('**/api/logout', async route => {
    posts += 1;
    const response = await route.fetch();
    expect(response.status()).toBe(204);
    await route.abort('failed');
  });
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-result')).toContainText('无法确认退出响应');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  expect(posts).toBe(1);
  await expect.poll(() => calls(secured, 'LOGOUT_HANDLER_REACHED')).toBe(count + 1);
  await page.unroute('**/api/logout');
  await page.locator('#login-password').fill('lesson009-test-only');
  await page.locator('#login-submit').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
});

test('窄屏键盘退出后刷新仍未登录，不自动再次退出', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginMember(page);
  await page.locator('#logout-submit').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#logout-status')).toHaveText('HTTP 204');
  const count = calls(secured, 'LOGOUT_HANDLER_REACHED');
  await page.reload();
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'anonymous');
  expect(calls(secured, 'LOGOUT_HANDLER_REACHED')).toBe(count);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('logout-mobile.png'), fullPage: true });
});


test('离线退出未到后端：结果未知且不误报成功，恢复网络后原会话仍可查询', async ({ page, context }) => {
  await loginMember(page);
  const count = calls(secured, 'LOGOUT_HANDLER_REACHED');
  await context.setOffline(true);
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-result')).toContainText('无法确认退出响应');
  await expect(page.locator('#current-user')).toHaveAttribute('data-state', 'unavailable');
  await expect(page.locator('#current-user-name')).toHaveText('—');
  expect(calls(secured, 'LOGOUT_HANDLER_REACHED')).toBe(count);
  await context.setOffline(false);
  await page.locator('#current-user-refresh').click();
  await expect(page.locator('#current-user-name')).toHaveText('member');
  await page.locator('#csrf-init').click();
  await page.locator('#logout-submit').click();
  await expect(page.locator('#logout-status')).toHaveText('HTTP 204');
});
