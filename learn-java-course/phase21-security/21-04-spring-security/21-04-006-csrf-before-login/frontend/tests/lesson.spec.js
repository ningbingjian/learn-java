import { test, expect } from '@playwright/test';
import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { lessonConfig } from '../vite.config.js';

const frontend = process.cwd();
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
const jarName = 'spring-security-lesson006-1.0-SNAPSHOT.jar';
let before, secured, beforeUi, securedUi;

async function startBackend(jar, port = 0) {
  const state = { logs: '', port: null, process: spawn(java, ['-jar', jar, `--server.port=${port}`, '--spring.security.user.password=lesson006-default-test',
    '--lesson.users.member-password=lesson006-test-only', '--lesson.users.support-password=lesson006-support-test',
    '--lesson.users.admin-password=lesson006-admin-test']) };
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
  before = await startBackend(resolve(frontend, '.e2e-work/before-csrf/21-04-006-csrf-before-login/target', jarName));
  secured = await startBackend(resolve(frontend, '../target', jarName));
  beforeUi = await startUi(before.port);
  securedUi = await startUi(secured.port);
});

test.afterAll(async () => {
  await Promise.all([beforeUi?.server.close(), securedUi?.server.close()]);
  await Promise.all([stopBackend(before), stopBackend(secured)]);
  // 只记录不含密码的业务执行计数。
  writeFileSync(resolve(frontend, '.e2e-work/browser-summary.json'), JSON.stringify({
    beforeCsrfPublicCalls: before ? calls(before, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    completedPublicCalls: secured ? calls(secured, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    completedHelloCalls: secured ? calls(secured, 'HELLO_HANDLER_REACHED') : null,
    apiErrorCalls: secured ? calls(secured, 'API_AUTHENTICATION_REQUIRED') : null,
    mvcErrorCalls: secured ? calls(secured, 'MVC_ERROR_HANDLED') : null,
  }, null, 2));
});

test('尚未开放CSRF入口时初始化失败，完成版准备就绪', async ({ page }) => {
  await page.goto(beforeUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('未取得令牌');
  await expect(page.getByRole('button', { name: '携带令牌提交', exact: true })).toBeDisabled();
  await page.goto(securedUi.url);
  await expect(page.locator('#csrf-state')).toHaveText('令牌已就绪（内容不展示）');
});

test('认证错误JSON：保留原始401、挑战、匿名条件及安全链执行证据', async ({ page, context }, info) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  const auth = calls(secured, 'API_AUTHENTICATION_REQUIRED');
  const mvc = calls(secured, 'MVC_ERROR_HANDLED');
  const hello = calls(secured, 'HELLO_HANDLER_REACHED');
  await page.goto(securedUi.url);
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
  const mvc = calls(secured, 'MVC_ERROR_HANDLED');
  const auth = calls(secured, 'API_AUTHENTICATION_REQUIRED');
  const publicCalls = calls(secured, 'PUBLIC_INFO_HANDLER_REACHED');
  await page.goto(securedUi.url);
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
  for (const [name, password] of [['member', 'lesson006-test-only'], ['support', 'lesson006-support-test'], ['admin', 'lesson006-admin-test']]) {
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
