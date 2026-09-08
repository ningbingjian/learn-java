const state = document.querySelector('#csrf-state');
const summary = document.querySelector('#csrf-summary');
const status = document.querySelector('#csrf-http-status');
const responseBody = document.querySelector('#csrf-response');
const initialize = document.querySelector('#csrf-init');
const submitButtons = [...document.querySelectorAll('button[data-csrf-mode]')];
let csrf = null;
let busy = false;

function updateControls() {
  initialize.disabled = busy;
  submitButtons.forEach((button) => { button.disabled = busy || !csrf; });
}

async function initializeCsrf() {
  if (busy) return;
  busy = true;
  csrf = null;
  updateControls();
  state.textContent = '正在准备令牌…';
  try {
    const response = await fetch('/api/csrf', {
      headers: { Accept: 'application/json' }, credentials: 'same-origin',
      cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw new Error(`初始化得到HTTP ${response.status}`);
    const value = await response.json();
    // 本课固定默认仓库契约，避免把任意响应字段当作请求头。
    if (value.headerName !== 'X-CSRF-TOKEN' || value.parameterName !== '_csrf'
        || typeof value.token !== 'string' || !value.token) throw new Error('令牌响应格式不符合本课契约');
    csrf = value;
    state.textContent = '令牌已就绪（内容不展示）';
    summary.textContent = '浏览器会在同源请求中携带会话Cookie。现在可以手动提交POST。';
  } catch {
    state.textContent = '未取得令牌';
    summary.textContent = '请检查后端与网络，再点击“重新取得令牌”。初始化失败不会自动提交。';
  } finally {
    busy = false;
    updateControls();
  }
}

async function submitProbe(mode) {
  if (busy || !csrf) return;
  busy = true;
  updateControls();
  status.textContent = 'POST请求中';
  responseBody.textContent = '等待响应。';
  const headers = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (mode !== 'missing') headers[csrf.headerName] = mode === 'wrong' ? 'invalid-for-lesson' : csrf.token;
  try {
    const response = await fetch('/api/csrf-probe', {
      method: 'POST', headers, body: '{}',
      credentials: mode === 'no-cookie' ? 'omit' : 'same-origin',
      redirect: 'error', signal: AbortSignal.timeout(8000),
    });
    status.textContent = `HTTP ${response.status}`;
    responseBody.textContent = (await response.text()) || '（空响应体）';
    summary.textContent = response.ok
      ? '本次POST通过并进入实验方法，没有修改业务数据；这不表示已经登录。'
      : response.status === 403
        ? '本次实验被拒绝。对照令牌与会话Cookie条件，不要把所有403都归因为未登录。'
        : '收到了其他响应，请检查服务状态；不会自动重发POST。';
    if (response.status === 403 && mode === 'valid') {
      csrf = null;
      state.textContent = '令牌或会话可能已失效，请重新取得令牌';
    }
  } catch {
    status.textContent = '未取得完整响应';
    summary.textContent = '无法确定本次POST是否被处理。先检查服务与网络，不自动重发。';
    responseBody.textContent = '没有可展示的完整HTTP响应。';
  } finally {
    // 不保留每次请求的头对象，也不把令牌写入页面、URL或持久存储。
    delete headers['X-CSRF-TOKEN'];
    busy = false;
    updateControls();
  }
}

initialize.addEventListener('click', initializeCsrf);
submitButtons.forEach((button) => {
  button.addEventListener('click', () => submitProbe(button.dataset.csrfMode));
});
initializeCsrf();
