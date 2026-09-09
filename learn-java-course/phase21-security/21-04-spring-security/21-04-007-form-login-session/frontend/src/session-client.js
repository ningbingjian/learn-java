let csrf = null;
let csrfState = 'empty';
let pending = 0;
let queue = Promise.resolve();
const listeners = new Set();

function notify() {
  const state = { busy: pending > 0, ready: csrf !== null, csrfState };
  listeners.forEach((listener) => listener(state));
}

export function subscribeSession(listener) {
  listeners.add(listener);
  notify();
  return () => listeners.delete(listener);
}

function serial(operation) {
  pending += 1;
  notify();
  const result = queue.then(operation);
  queue = result.catch(() => {});
  return result.finally(() => { pending -= 1; notify(); });
}

function request(path, options = {}) {
  return fetch(path, {
    ...options, credentials: options.credentials || 'same-origin',
    redirect: 'error', signal: AbortSignal.timeout(8000),
  });
}

async function loadCsrf() {
  csrf = null;
  csrfState = 'loading';
  notify();
  try {
    const response = await request('/api/csrf', { headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (!response.ok) throw new Error('无法取得令牌');
    const value = await response.json();
    if (value?.headerName !== 'X-CSRF-TOKEN' || value.parameterName !== '_csrf'
        || typeof value.token !== 'string' || !value.token) throw new Error('令牌契约不匹配');
    csrf = value;
    csrfState = 'ready';
  } catch (error) {
    csrfState = 'unavailable';
    throw error;
  } finally { notify(); }
}

function csrfHeaders() {
  if (!csrf) throw new Error('请先重新取得令牌');
  return { Accept: 'application/json', [csrf.headerName]: csrf.token };
}

export function initializeCsrf() { return serial(loadCsrf); }

export function submitProbe(mode) {
  return serial(async () => {
    const headers = { ...csrfHeaders(), 'Content-Type': 'application/json' };
    if (mode === 'missing') delete headers['X-CSRF-TOKEN'];
    if (mode === 'wrong') headers['X-CSRF-TOKEN'] = 'invalid-for-lesson';
    const response = await request('/api/csrf-probe', {
      method: 'POST', headers, body: '{}', credentials: mode === 'no-cookie' ? 'omit' : 'same-origin',
    });
    if (response.status === 403 && mode === 'valid') {
      csrf = null; csrfState = 'expired'; notify();
    }
    return { status: response.status, ok: response.ok, text: await response.text() };
  });
}

export function login(username, password) {
  // 把表单构造在调用时，调用方随即清空输入；不保留自动重试凭据。
  const body = new URLSearchParams({ username, password });
  return serial(async () => {
    let outcome = { kind: 'unknown', status: null };
    try {
      const response = await request('/api/login', {
        method: 'POST', headers: { ...csrfHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' }, body,
      });
      outcome.status = response.status;
      const value = await response.json().catch(() => null);
      if (response.status === 200 && value?.code === 'LOGIN_SUCCEEDED') outcome.kind = 'success';
      else if (response.status === 401 && value?.code === 'LOGIN_FAILED') outcome.kind = 'failure';
      else if (response.status === 403) outcome.kind = 'rejected';
    } catch {
      // 可能已经认证成功但响应丢失，不能直接判定为密码错误。
    } finally {
      body.delete('password'); body.delete('username');
    }
    // 与登录POST放在同一个串行操作中，避免探针抢先使用认证前的令牌。
    const csrfReady = await loadCsrf().then(() => true, () => false);
    return { ...outcome, csrfReady };
  });
}

export function sessionHello() {
  return serial(async () => {
    const response = await request('/api/hello', { headers: { Accept: 'application/json' } });
    return { status: response.status, text: await response.text() };
  });
}
