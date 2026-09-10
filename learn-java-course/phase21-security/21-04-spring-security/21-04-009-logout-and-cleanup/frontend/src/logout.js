import { logout, subscribeSession } from './session-client.js';

const button = document.querySelector('#logout-submit');
const result = document.querySelector('#logout-result');
const status = document.querySelector('#logout-status');

subscribeSession(({ busy, ready }) => { button.disabled = busy || !ready; });
button.addEventListener('click', async () => {
  document.querySelector('#login-password').value = '';
  document.querySelector('#identity-password').value = '';
  document.querySelector('#login-status').textContent = '历史结果已清理';
  document.querySelector('#login-result').textContent = '已开始退出核对；当前身份以上方状态为准。';
  document.querySelector('#session-result').textContent = '历史会话访问结果已清理，请重新验证。';
  status.textContent = '正在提交退出';
  result.textContent = '等待退出响应并重新准备CSRF令牌…';
  const outcome = await logout();
  status.textContent = outcome.status === null ? '未取得退出响应' : `HTTP ${outcome.status}`;
  result.textContent = outcome.kind === 'success' ? '本次退出已完成，原会话身份已清理。'
    : outcome.kind === 'rejected' ? '退出被拒绝，不能宣称已退出；当前身份已重新核对。'
      : '无法确认退出响应，未自动重发；当前会话以上方核对结果为准。';
  result.textContent += outcome.csrfReady ? ' CSRF令牌已重新就绪，可手动开始下一次登录。'
    : ' 尚未取得新令牌，请重新取得令牌后再提交；已确认的退出结果不受影响。';
});
