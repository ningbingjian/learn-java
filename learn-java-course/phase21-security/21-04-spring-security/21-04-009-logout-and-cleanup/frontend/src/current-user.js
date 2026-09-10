import { refreshIdentity, subscribeSession } from './session-client.js';

const panel = document.querySelector('#current-user');
const status = document.querySelector('#current-user-status');
const name = document.querySelector('#current-user-name');
const authorities = document.querySelector('#current-user-authorities');
const refresh = document.querySelector('#current-user-refresh');

subscribeSession(({ busy, identity }) => {
  refresh.disabled = busy;
  panel.dataset.state = identity.phase;
  status.textContent = identity.phase === 'loading' ? '正在向后端确认当前身份…'
    : identity.phase === 'authenticated' ? '当前会话已认证，身份来自 /me。'
      : identity.phase === 'anonymous' ? '当前会话未登录。'
        : '暂时无法确认身份，请检查网络与服务后重新查询。';
  name.textContent = identity.user?.username || '—';
  authorities.textContent = identity.user?.authorities.join('、') || '—';
});

refresh.addEventListener('click', refreshIdentity);
refreshIdentity();
