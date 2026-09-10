import { defineConfig } from 'vite';

export function lessonConfig(backend = process.env.LESSON_BACKEND_URL || 'http://127.0.0.1:8080') {
  return defineConfig({
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      // 同源观察台不需要开发服务器CORS；让OPTIONS继续到后端代理。
      cors: false,
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api(?=\/|$)/, ''),
          configure(proxy) {
            // 只处理代理连接错误；不改写后端的401、响应头或响应体。
            proxy.on('error', (_error, _request, response) => {
              if ('writeHead' in response && !response.headersSent) {
                response.writeHead(502, {
                  'Content-Type': 'application/json; charset=utf-8',
                  'X-Lesson-Proxy-Error': 'upstream-unavailable',
                });
                response.end(JSON.stringify({ error: 'BACKEND_UNAVAILABLE' }));
              }
            });
          },
        },
      },
    },
  });
}

export default lessonConfig();
