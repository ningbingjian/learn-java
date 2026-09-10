import { initializeCsrf, submitProbe, subscribeSession } from './session-client.js';

const state = document.querySelector('#csrf-state');
const summary = document.querySelector('#csrf-summary');
const status = document.querySelector('#csrf-http-status');
const responseBody = document.querySelector('#csrf-response');
const initialize = document.querySelector('#csrf-init');
const submitButtons = [...document.querySelectorAll('button[data-csrf-mode]')];

subscribeSession(({ busy, ready, csrfState }) => {
  initialize.disabled = busy;
  submitButtons.forEach(button => { button.disabled = busy || !ready; });
  state.textContent = csrfState === 'ready' ? '令牌已就绪（内容不展示）'
    : csrfState === 'loading' ? '正在准备令牌…'
      : csrfState === 'expired' ? '令牌或会话可能已失效，请重新取得令牌' : '未取得令牌';
});

async function prepare() {
  try {
    await initializeCsrf();
    summary.textContent = '浏览器会在同源请求中携带会话Cookie。现在可以手动提交POST。';
  } catch {
    summary.textContent = '请检查后端与网络，再点击“重新取得令牌”。初始化失败不会自动提交。';
  }
}

initialize.addEventListener('click', prepare);
submitButtons.forEach(button => button.addEventListener('click', async () => {
  status.textContent = 'POST请求中';
  responseBody.textContent = '等待响应。';
  try {
    const response = await submitProbe(button.dataset.csrfMode);
    status.textContent = `HTTP ${response.status}`;
    responseBody.textContent = response.text || '（空响应体）';
    summary.textContent = response.ok
      ? '本次POST通过并进入实验方法，没有修改业务数据；这不表示已经登录。'
      : response.status === 403 ? '本次实验被拒绝。对照令牌与会话Cookie条件，不要把所有403都归因为未登录。'
        : '收到了其他响应，请检查服务状态；不会自动重发POST。';
  } catch {
    status.textContent = '未取得完整响应';
    summary.textContent = '无法确定本次POST是否被处理。先检查服务与网络，不自动重发。';
    responseBody.textContent = '没有可展示的完整HTTP响应。';
  }
}));
prepare();
