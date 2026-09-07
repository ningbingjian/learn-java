import './style.css';

const buttons = [...document.querySelectorAll('button[data-path]')];
const requestPath = document.querySelector('#request-path');
const panel = document.querySelector('.result-panel');
const code = document.querySelector('#status-code');
const summary = document.querySelector('#result-summary');
const source = document.querySelector('#response-source');
const body = document.querySelector('#response-body');
const errorCode = document.querySelector('#error-code');
const retry = document.querySelector('#retry-request');

function readApiError(text, contentType, status) {
  if (contentType?.split(';')[0].trim().toLowerCase() !== 'application/json') return null;
  try {
    const value = JSON.parse(text);
    const expectedStatus = { AUTHENTICATION_REQUIRED: 401, UNSUPPORTED_LANGUAGE: 400 };
    if (value && typeof value.code === 'string' && Object.hasOwn(expectedStatus, value.code) && expectedStatus[value.code] === status
        && typeof value.message === 'string' && value.message.trim()) return value;
  } catch {
    // 响应已取得，格式不合法不等于网络失败；原文仍保留在观察区。
  }
  return null;
}

async function sendRequest(button) {
  const label = button.textContent;
  const path = button.dataset.path;
  if (!path) return;
  retry.hidden = true;
  retry.dataset.path = path;
  errorCode.textContent = '—';
  // 共用一个结果区，请求中同时禁用两按钮，避免较早响应覆盖较新操作。
  buttons.forEach((item) => { item.disabled = true; });
  requestPath.textContent = `GET ${path}`;
  button.textContent = '正在请求…';
  panel.setAttribute('aria-busy', 'true');
  code.dataset.state = 'loading';
  code.textContent = '请求中';
  summary.textContent = '等待接口响应…';
  source.textContent = '—';
  body.textContent = '等待响应内容。';

  try {
    const response = await fetch(path, {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      redirect: 'error',
      signal: AbortSignal.timeout(8000),
    });
    const text = await response.text();
    // HTTP 401/502也会得到Response，不能把所有非200都归入catch。
    code.textContent = `HTTP ${response.status}`;
    source.textContent = '后端响应（经开发代理转发）';
    body.textContent = text || '（空响应体）';
    const apiError = readApiError(text, response.headers.get('Content-Type'), response.status);
    retry.hidden = response.ok;
    errorCode.textContent = apiError?.code || '—';

    if (response.status === 502 && response.headers.get('X-Lesson-Proxy-Error') === 'upstream-unavailable') {
      code.dataset.state = 'error';
      source.textContent = '开发代理：未取得后端响应';
      summary.textContent = '无法连接后端。检查后端是否启动，以及代理目标端口是否正确。';
    } else if (response.status === 401) {
      code.dataset.state = 'unauthorized';
      summary.textContent = apiError?.message || '本次请求未通过认证，响应未提供可识别的错误契约。请检查原始响应。';
    } else if (response.status === 400) {
      code.dataset.state = 'error';
      summary.textContent = apiError?.message || '请求参数可能有误，响应未提供可识别的错误契约。请检查原始响应。';
    } else if (response.ok) {
      code.dataset.state = 'success';
      summary.textContent = '请求成功。请对照后端新增的业务日志。';
    } else {
      code.dataset.state = 'error';
      summary.textContent = '收到了其他 HTTP 响应。请根据真实状态和内容定位，不能一律判断为未登录。';
    }
  } catch (error) {
    retry.hidden = false;
    code.dataset.state = 'error';
    code.textContent = '未取得完整响应';
    source.textContent = '浏览器请求失败';
    summary.textContent = error.name === 'TimeoutError'
      ? '请求超过8秒。请检查服务与网络，然后重试。'
      : '请求未完成。请检查网络、前端服务或是否发生了被禁止的重定向。';
    body.textContent = '没有可展示的完整 HTTP 响应。此处不推断认证结果。';
  } finally {
    buttons.forEach((item) => { item.disabled = false; });
    button.textContent = label;
    panel.setAttribute('aria-busy', 'false');
  }
}

buttons.forEach((button) => {
  button.addEventListener('click', () => sendRequest(button));
});
