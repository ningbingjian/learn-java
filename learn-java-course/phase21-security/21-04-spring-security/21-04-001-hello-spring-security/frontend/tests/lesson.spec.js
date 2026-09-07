import { test, expect } from '@playwright/test';
import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { lessonConfig } from '../vite.config.js';

const frontend = process.cwd();
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
const jarName = 'spring-security-lesson001-1.0-SNAPSHOT.jar';
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

const calls = (state) => (state.logs.match(/HELLO_HANDLER_REACHED/g) || []).length;

test.describe.configure({ mode: 'serial' });
test.beforeAll(async () => {
  test.setTimeout(120000);
  before = await startBackend(resolve(frontend, '.e2e-work/before-security/21-04-001-hello-spring-security/target', jarName));
  secured = await startBackend(resolve(frontend, '../target', jarName));
  beforeUi = await startUi(before.port);
  securedUi = await startUi(secured.port);
});

test.afterAll(async () => {
  await Promise.all([beforeUi?.server.close(), securedUi?.server.close()]);
  await Promise.all([stopBackend(before), stopBackend(secured)]);
  // 只记录不含密码的业务执行计数。
  writeFileSync(resolve(frontend, '.e2e-work/browser-summary.json'), JSON.stringify({
    beforeSecurityHandlerCalls: before ? calls(before) : null,
    securedHandlerCalls: secured ? calls(secured) : null,
  }, null, 2));
});

test('无安全依赖：点击页面得到200与业务JSON，后端方法执行', async ({ page }, testInfo) => {
  const count = calls(before);
  await page.goto(beforeUi.url);
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 200');
  await expect(page.locator('#response-body')).toHaveText('{"message":"Hello Spring Security"}');
  await expect.poll(() => calls(before)).toBe(count + 1);
  await page.screenshot({ path: testInfo.outputPath('anonymous-200.png'), fullPage: true });
});

test('有安全依赖：透传真实401和认证挑战，匿名请求不携带Cookie', async ({ page, context }, testInfo) => {
  await context.addCookies([{ name: 'lesson-probe', value: 'not-a-credential', url: securedUi.url }]);
  await page.goto(securedUi.url);
  const pending = page.waitForResponse((response) => response.url().endsWith('/api/hello'));
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  const response = await pending;
  expect(response.status()).toBe(401);
  expect(response.headers()['www-authenticate']).toMatch(/^Basic/);
  const requestHeaders = await response.request().allHeaders();
  expect(requestHeaders.accept).toBe('application/json');
  expect(requestHeaders.cookie).toBeUndefined();
  expect(requestHeaders.authorization).toBeUndefined();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  await expect(page.locator('#result-summary')).toContainText('未通过认证');
  await expect(page.locator('#response-body')).not.toContainText('Hello Spring Security');
  expect(calls(secured)).toBe(0);
  await page.screenshot({ path: testInfo.outputPath('anonymous-401.png'), fullPage: true });
});

test('真实停止后端：代理502不冒充401；重启后再次得到401', async ({ page }, testInfo) => {
  const port = secured.port;
  await page.goto(securedUi.url);
  await stopBackend(secured);
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 502');
  await expect(page.locator('#response-source')).toHaveText('开发代理：未取得后端响应');
  await expect(page.locator('#result-summary')).toContainText('无法连接后端');
  await page.screenshot({ path: testInfo.outputPath('backend-unavailable.png'), fullPage: true });
  secured = await startBackend(resolve(frontend, '../target', jarName), port);
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
});

test('浏览器离线：显示无完整响应，恢复网络后可以重试', async ({ page, context }) => {
  await page.goto(securedUi.url);
  await context.setOffline(true);
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('未取得完整响应');
  await expect(page.locator('#response-source')).toHaveText('浏览器请求失败');
  await expect(page.getByRole('button', { name: '发送匿名请求' })).toBeEnabled();
  await context.setOffline(false);
  await page.getByRole('button', { name: '发送匿名请求' }).click();
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
});

test('窄屏与键盘：页面无横向溢出，Enter可发出匿名请求', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(securedUi.url);
  await page.getByRole('button', { name: '发送匿名请求' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status-code')).toHaveText('HTTP 401');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('mobile-401.png'), fullPage: true });
});
