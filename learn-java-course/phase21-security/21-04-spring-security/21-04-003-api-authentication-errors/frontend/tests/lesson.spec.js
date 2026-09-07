import { test, expect } from '@playwright/test';
import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { lessonConfig } from '../vite.config.js';

const frontend = process.cwd();
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
const jarName = 'spring-security-lesson003-1.0-SNAPSHOT.jar';
let before, secured, beforeUi, securedUi;

async function startBackend(jar, port = 0) {
  const state = { logs: '', port: null, process: spawn(java, ['-jar', jar, `--server.port=${port}`]) };
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
  before = await startBackend(resolve(frontend, '.e2e-work/before-contract/21-04-003-api-authentication-errors/target', jarName));
  secured = await startBackend(resolve(frontend, '../target', jarName));
  beforeUi = await startUi(before.port);
  securedUi = await startUi(secured.port);
});

test.afterAll(async () => {
  await Promise.all([beforeUi?.server.close(), securedUi?.server.close()]);
  await Promise.all([stopBackend(before), stopBackend(secured)]);
  // 只记录不含密码的业务执行计数。
  writeFileSync(resolve(frontend, '.e2e-work/browser-summary.json'), JSON.stringify({
    beforeContractPublicCalls: before ? calls(before, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    contractPublicCalls: secured ? calls(secured, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    contractHelloCalls: secured ? calls(secured, 'HELLO_HANDLER_REACHED') : null,
    apiErrorCalls: secured ? calls(secured, 'API_AUTHENTICATION_REQUIRED') : null,
    mvcErrorCalls: secured ? calls(secured, 'MVC_ERROR_HANDLED') : null,
  }, null, 2));
});

test('只有MVC处理器：参数400有JSON，匿名401仍没有认证契约', async ({ page }) => {
  await page.goto(beforeUi.url);
  await page.getByRole('button', { name: '查看参数错误对照' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 400');
  await expect(page.locator('#error-code')).toHaveText('UNSUPPORTED_LANGUAGE');
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#error-code')).toHaveText('—');
  await expect(page.locator('#result-summary')).toContainText('未提供可识别的错误契约');
});

test('认证错误JSON：保留原始401、挑战、匿名条件及安全链执行证据', async ({ page, context }, info) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  const auth = calls(secured, 'API_AUTHENTICATION_REQUIRED');
  const mvc = calls(secured, 'MVC_ERROR_HANDLED');
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
  expect(calls(secured, 'HELLO_HANDLER_REACHED')).toBe(0);
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
