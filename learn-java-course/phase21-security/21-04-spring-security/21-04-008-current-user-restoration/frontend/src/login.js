import { login, sessionHello, subscribeSession } from './session-client.js';

const form = document.querySelector('#login-form');
const username = document.querySelector('#login-name');
const password = document.querySelector('#login-password');
const submit = document.querySelector('#login-submit');
const result = document.querySelector('#login-result');
const status = document.querySelector('#login-status');
const hello = document.querySelector('#session-hello');
const sessionResult = document.querySelector('#session-result');

subscribeSession(({ busy, ready }) => {
  submit.disabled = busy || !ready;
  hello.disabled = busy;
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (submit.disabled || !form.reportValidity()) return;
  const pending = login(username.value, password.value);
  password.value = '';
  status.textContent = '提交中';
  result.textContent = '等待登录响应，然后更新令牌并核对当前身份…';
  const outcome = await pending;
  status.textContent = outcome.status === null ? '未取得可识别结果' : `HTTP ${outcome.status}`;
  result.textContent = outcome.kind === 'success'
    ? '本次登录成功。请用会话按钮验证后续请求。'
    : outcome.kind === 'failure' ? '本次登录失败，请检查用户名和密码；这不代表已执行退出。'
      : outcome.kind === 'rejected' ? '登录提交被拒绝，请检查令牌与会话条件。'
        : '无法确认本次登录结果。先用会话按钮核对，不自动重发密码。';
  result.textContent += outcome.csrfReady
    ? ' 新CSRF令牌已就绪。'
    : ' 尚未取得新CSRF令牌，请重新获取后再提交；只读会话验证仍可用。';
});

hello.addEventListener('click', async () => {
  sessionResult.textContent = '正在验证会话请求…';
  try {
    const response = await sessionHello();
    sessionResult.textContent = response.status === 200
      ? 'HTTP 200：仅携带会话Cookie，受保护接口访问成功。'
      : response.status === 401 ? 'HTTP 401：本次会话请求未通过认证。'
        : `HTTP ${response.status}：请根据实际请求定位。`;
  } catch {
    sessionResult.textContent = '未取得完整响应，不能据此判断会话是否有效。';
  }
});
