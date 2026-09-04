(() => {
    'use strict';

    const isLocalPage = window.location.protocol === 'file:'
        || window.location.hostname === 'localhost'
        || window.location.hostname === '127.0.0.1';
    const localBackendHost = window.location.hostname === 'localhost' ? 'localhost' : '127.0.0.1';
    const API_BASE = isLocalPage ? `http://${localBackendHost}:8091/api/v1` : '/api/v1';
    const form = document.getElementById('login-form');
    const button = document.getElementById('login-button');
    const errorElement = document.getElementById('login-error');

    async function readJsonResponse(response) {
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            throw new Error('登录接口不可用，请确认后端已重新启动并运行在 8091 端口');
        }
        return response.json();
    }

    async function checkExistingLogin() {
        try {
            const response = await fetch(`${API_BASE}/auth/status`, { credentials: 'include' });
            const result = await readJsonResponse(response);
            if (result.code === '0000' && result.data === true) {
                window.location.replace('./chat.html');
            }
        } catch (error) {
            errorElement.textContent = '无法连接后端服务';
        }
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorElement.textContent = '';
        button.disabled = true;
        button.textContent = '登录中...';

        try {
            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value;
            if (username !== 'usr' || password !== '123321') {
                throw new Error('账号或密码错误');
            }

            const response = await fetch(`${API_BASE}/auth/login`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    password
                })
            });
            const result = await readJsonResponse(response);
            if (!response.ok || result.code !== '0000') {
                throw new Error(result.info || '登录失败');
            }
            window.location.replace('./chat.html');
        } catch (error) {
            errorElement.textContent = error.message || '登录失败';
        } finally {
            button.disabled = false;
            button.textContent = '登录';
        }
    });

    checkExistingLogin();
})();
