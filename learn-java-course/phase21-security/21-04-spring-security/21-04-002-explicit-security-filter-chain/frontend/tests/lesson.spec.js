import { test, expect } from '@playwright/test';
import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { lessonConfig } from '../vite.config.js';

const frontend = process.cwd();
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
const jarName = 'spring-security-lesson002-1.0-SNAPSHOT.jar';
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
  before = await startBackend(resolve(frontend, '.e2e-work/before-chain/21-04-002-explicit-security-filter-chain/target', jarName));
  secured = await startBackend(resolve(frontend, '../target', jarName));
  beforeUi = await startUi(before.port);
  securedUi = await startUi(secured.port);
});

test.afterAll(async () => {
  await Promise.all([beforeUi?.server.close(), securedUi?.server.close()]);
  await Promise.all([stopBackend(before), stopBackend(secured)]);
  // 只记录不含密码的业务执行计数。
  writeFileSync(resolve(frontend, '.e2e-work/browser-summary.json'), JSON.stringify({
    defaultChainPublicCalls: before ? calls(before, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    explicitChainPublicCalls: secured ? calls(secured, 'PUBLIC_INFO_HANDLER_REACHED') : null,
    explicitChainHelloCalls: secured ? calls(secured, 'HELLO_HANDLER_REACHED') : null,
  }, null, 2));
});

test('只有公开Controller、没有自定义链：匿名公开请求仍为401', async ({ page }) => {
  await page.goto(beforeUi.url);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  expect(calls(before, 'PUBLIC_INFO_HANDLER_REACHED')).toBe(0);
});

test('显式放行：公开按钮返回真实200，匿名条件与安全响应头保留', async ({ page, context }, testInfo) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  const count = calls(secured, 'PUBLIC_INFO_HANDLER_REACHED');
  await page.goto(securedUi.url);
  const pending = page.waitForResponse((response) => response.url().endsWith('/api/public/info'));
  await page.getByRole('button', { name: '访问公开信息' }).click();
  const response = await pending;
  expect(response.status()).toBe(200);
  expect(response.headers()['x-content-type-options']).toBe('nosniff');
  const headers = await response.request().allHeaders();
  expect(headers.accept).toBe('application/json');
  expect(headers.cookie).toBeUndefined();
  expect(headers.authorization).toBeUndefined();
  await expect(page.locator('#request-path')).toHaveText('GET /api/public/info');
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await expect(page.locator('#response-body')).toHaveText('{"message":"Public information is available without login"}');
  await expect.poll(() => calls(secured, 'PUBLIC_INFO_HANDLER_REACHED')).toBe(count + 1);
  await page.screenshot({ path: testInfo.outputPath('public-200.png'), fullPage: true });
});

test('切换到受保护按钮：真实401与认证挑战，结果对应本次路径', async ({ page, context }, testInfo) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  await page.goto(securedUi.url);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  const pending = page.waitForResponse((response) => response.url().endsWith('/api/hello'));
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  const response = await pending;
  expect(response.status()).toBe(401);
  expect(response.headers()['www-authenticate']).toMatch(/^Basic/);
  const headers = await response.request().allHeaders();
  expect(headers.cookie).toBeUndefined();
  expect(headers.authorization).toBeUndefined();
  await expect(page.locator('#request-path')).toHaveText('GET /api/hello');
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#result-summary')).toContainText('未通过认证');
  await expect(page.locator('#response-body')).not.toContainText('Public information');
  expect(calls(secured, 'HELLO_HANDLER_REACHED')).toBe(0);
  await page.screenshot({ path: testInfo.outputPath('protected-401.png'), fullPage: true });
});

test('真实停止后端：两种请求均为代理502，重启后恢复各自结果', async ({ page }, testInfo) => {
  const port = secured.port;
  await page.goto(securedUi.url);
  await stopBackend(secured);
  for (const name of ['访问公开信息', '访问受保护接口']) {
    await page.getByRole('button', { name }).click();
    await expect(page.locator('#status-code')).toHaveText('HTTP 502');
    await expect(page.locator('#response-source')).toHaveText('开发代理：未取得后端响应');
    await expect(page.locator('#result-summary')).toContainText('无法连接后端');
  }
  await page.screenshot({ path: testInfo.outputPath('backend-unavailable.png'), fullPage: true });
  secured = await startBackend(resolve(frontend, '../target', jarName), port);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
});

test('浏览器离线后恢复：两按钮可重试且不把网络失败当认证拒绝', async ({ page, context }) => {
  await page.goto(securedUi.url);
  await context.setOffline(true);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('未取得完整响应');
  await expect(page.locator('#response-source')).toHaveText('浏览器请求失败');
  for (const name of ['访问公开信息', '访问受保护接口']) {
    await expect(page.getByRole('button', { name })).toBeEnabled();
  }
  await context.setOffline(false);
  await page.getByRole('button', { name: '访问公开信息' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await page.getByRole('button', { name: '访问受保护接口' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
});

test('窄屏与键盘：两按钮可激活，页面无横向溢出', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(securedUi.url);
  await page.getByRole('button', { name: '访问公开信息' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await page.getByRole('button', { name: '访问受保护接口' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('mobile-401.png'), fullPage: true });
});
