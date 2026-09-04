(() => {
    'use strict';

    const isLocalPage = window.location.protocol === 'file:'
        || window.location.hostname === 'localhost'
        || window.location.hostname === '127.0.0.1';
    const localBackendHost = window.location.hostname === 'localhost' ? 'localhost' : '127.0.0.1';
    const API_BASE = isLocalPage ? `http://${localBackendHost}:8091/api/v1` : '/api/v1';
    const URLS = {
        ragUpload: `${API_BASE}/rag/file/upload`,
        ragGit: `${API_BASE}/rag/analyze_git_repository`,
        ragTags: `${API_BASE}/rag/query_rag_tag_list`,
        agent: `${API_BASE}/agent/auto_agent`,
        conversations: `${API_BASE}/conversations`,
        authStatus: `${API_BASE}/auth/status`,
        logout: `${API_BASE}/auth/logout`
    };

    const MODE_COPY = {
        chat: {
            brand: 'Chat',
            switchLabel: 'Agent',
            switchTitle: '切换到 Agent 模式',
            description: '知识库增强对话',
            badge: 'Chat',
            welcomeTitle: '你好，我是你的 AI 助手',
            welcomeSubtitle: '有什么我可以帮您的吗？',
            placeholder: '问问我...'
        },
        agent: {
            brand: 'Agent',
            switchLabel: 'Chat',
            switchTitle: '切换到 Chat 模式',
            description: '自主规划与执行任务',
            badge: 'Agent',
            welcomeTitle: '你好，我是你的 Agent',
            welcomeSubtitle: '告诉我需要规划和执行的任务',
            placeholder: '描述一个需要 Agent 完成的任务...'
        }
    };

    const TYPE_LABELS = {
        analysis: '任务分析',
        execution: '执行过程',
        supervision: '质量监督',
        summary: '执行总结',
        error: '执行异常'
    };

    const SUB_TYPE_LABELS = {
        analysis_status: '状态分析',
        analysis_history: '历史评估',
        analysis_strategy: '下一步策略',
        analysis_progress: '完成度评估',
        execution_target: '执行目标',
        execution_process: '执行过程',
        execution_result: '执行结果',
        execution_quality: '质量检查',
        supervision_assessment: '质量评估',
        supervision_issues: '问题识别',
        supervision_suggestions: '改进建议',
        supervision_score: '质量评分'
    };

    marked.setOptions({ breaks: true, gfm: true });

    const createId = (prefix) => {
        const value = window.crypto?.randomUUID?.()
            || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        return `${prefix}-${value}`;
    };

    const createConversation = (mode, data = {}) => ({
        id: data.id || createId(mode),
        mode,
        title: data.title || '新的对话',
        messages: data.messages || [],
        messagesLoaded: data.messagesLoaded ?? false
    });

    let currentMode = 'chat';
    const conversations = {
        chat: [],
        agent: []
    };
    const activeConversationIds = {
        chat: null,
        agent: null
    };
    let currentGeneration = null;
    let historyLoadFailed = false;
    let persistenceWarningShown = false;

    const elements = {
        body: document.body,
        brandTitle: document.getElementById('brand-title'),
        modeToggle: document.getElementById('mode-toggle'),
        modeToggleLabel: document.getElementById('mode-toggle-label'),
        modeDescription: document.getElementById('mode-description'),
        modeBadge: document.getElementById('current-mode-badge'),
        chatList: document.getElementById('chat-list-container'),
        messages: document.getElementById('messages-wrapper'),
        welcome: document.getElementById('welcome-screen'),
        welcomeTitle: document.getElementById('welcome-title'),
        welcomeSubtitle: document.getElementById('welcome-subtitle'),
        chatTitle: document.getElementById('current-chat-title'),
        input: document.getElementById('message-input'),
        sendButton: document.getElementById('send-button'),
        chatContainer: document.getElementById('chat-container'),
        ragSelect: document.getElementById('rag-select'),
        agentId: document.getElementById('agent-id-input'),
        agentMaxStep: document.getElementById('agent-max-step')
    };

    function getActiveConversation() {
        return conversations[currentMode].find(
            (conversation) => conversation.id === activeConversationIds[currentMode]
        );
    }

    function redirectToLogin() {
        window.location.replace('./login.html');
    }

    async function requireAuthentication() {
        try {
            const response = await fetch(URLS.authStatus, { credentials: 'include' });
            const result = await response.json();
            if (response.ok && result.code === '0000' && result.data === true) {
                elements.body.classList.remove('auth-pending');
                return true;
            }
        } catch (error) {
            console.error('登录状态校验失败:', error);
        }
        redirectToLogin();
        return false;
    }

    async function authenticatedFetch(url, options = {}) {
        const response = await fetch(url, { ...options, credentials: 'include' });
        if (response.status === 401) {
            redirectToLogin();
            throw new Error('登录已失效');
        }
        return response;
    }

    async function logout() {
        abortGeneration();
        try {
            await authenticatedFetch(URLS.logout, { method: 'POST' });
        } finally {
            redirectToLogin();
        }
    }

    async function apiRequest(url, options = {}) {
        const response = await authenticatedFetch(url, options);
        let result;
        try {
            result = await response.json();
        } catch (error) {
            throw new Error(`服务返回了无法识别的数据 (HTTP ${response.status})`);
        }
        if (!response.ok || result.code !== '0000') {
            throw new Error(result.info || `请求失败 (HTTP ${response.status})`);
        }
        return result.data;
    }

    async function createPersistedConversation(mode) {
        const conversation = createConversation(mode, { messagesLoaded: true });
        await apiRequest(URLS.conversations, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: conversation.id,
                mode: conversation.mode,
                title: conversation.title
            })
        });
        return conversation;
    }

    function deserializeMessage(message) {
        const kind = message.role === 'user'
            ? 'user'
            : (message.messageType === 'agent' ? 'agent' : 'chat');
        if (kind === 'agent') {
            let events = [];
            try {
                const parsed = JSON.parse(message.content || '[]');
                if (Array.isArray(parsed)) events = parsed;
            } catch (error) {
                events = [{ type: 'error', content: '这条 Agent 历史记录无法解析。' }];
            }
            return {
                id: message.id,
                role: message.role,
                kind,
                events,
                status: message.status,
                sortOrder: Number(message.sortOrder)
            };
        }
        return {
            id: message.id,
            role: message.role,
            kind,
            content: message.content || '',
            status: message.status,
            sortOrder: Number(message.sortOrder)
        };
    }

    async function ensureMessagesLoaded(conversation) {
        if (!conversation || conversation.messagesLoaded) return;
        const data = await apiRequest(
            `${URLS.conversations}/${encodeURIComponent(conversation.id)}/messages`
        );
        conversation.messages = Array.isArray(data) ? data.map(deserializeMessage) : [];
        conversation.messagesLoaded = true;
    }

    async function loadConversationHistory() {
        const data = await apiRequest(URLS.conversations);
        conversations.chat = [];
        conversations.agent = [];

        (Array.isArray(data) ? data : []).forEach((item) => {
            if (!conversations[item.mode]) return;
            conversations[item.mode].push(createConversation(item.mode, item));
        });

        for (const mode of ['chat', 'agent']) {
            if (conversations[mode].length === 0) {
                conversations[mode].push(await createPersistedConversation(mode));
            }
            activeConversationIds[mode] = conversations[mode][0].id;
        }
        await ensureMessagesLoaded(getActiveConversation());
    }

    async function setMode(mode) {
        if (mode === currentMode) return;
        abortGeneration();
        currentMode = mode;

        const copy = MODE_COPY[mode];
        elements.body.dataset.mode = mode;
        elements.brandTitle.textContent = copy.brand;
        elements.modeToggleLabel.textContent = copy.switchLabel;
        elements.modeToggle.title = copy.switchTitle;
        elements.modeToggle.setAttribute('aria-label', copy.switchTitle);
        elements.modeDescription.textContent = copy.description;
        elements.modeBadge.textContent = copy.badge;
        elements.welcomeTitle.textContent = copy.welcomeTitle;
        elements.welcomeSubtitle.textContent = copy.welcomeSubtitle;
        elements.input.placeholder = copy.placeholder;
        elements.input.value = '';
        resizeInput();
        updateSendButton();
        renderConversationList();
        renderMessages();
        try {
            await ensureMessagesLoaded(getActiveConversation());
            renderMessages();
        } catch (error) {
            window.alert(`加载聊天记录失败: ${error.message}`);
        }
        elements.input.focus();
    }

    function toggleMode() {
        setMode(currentMode === 'chat' ? 'agent' : 'chat');
    }

    async function createNewConversation() {
        await abortGeneration();
        try {
            const conversation = historyLoadFailed
                ? createConversation(currentMode, { messagesLoaded: true })
                : await createPersistedConversation(currentMode);
            conversations[currentMode].unshift(conversation);
            activeConversationIds[currentMode] = conversation.id;
            renderConversationList();
            renderMessages();
            elements.input.focus();
        } catch (error) {
            window.alert(`新建对话失败: ${error.message}`);
        }
    }

    async function selectConversation(id) {
        if (activeConversationIds[currentMode] === id) return;
        abortGeneration();
        activeConversationIds[currentMode] = id;
        renderConversationList();
        renderMessages();
        try {
            await ensureMessagesLoaded(getActiveConversation());
            renderMessages();
        } catch (error) {
            window.alert(`加载聊天记录失败: ${error.message}`);
        }
    }

    async function deleteConversation(event, id) {
        event.stopPropagation();
        if (!window.confirm('确定要删除这个对话吗？')) return;

        if (activeConversationIds[currentMode] === id) {
            await abortGeneration(false);
        }
        try {
            if (!historyLoadFailed) {
                await apiRequest(`${URLS.conversations}/${encodeURIComponent(id)}`, {
                    method: 'DELETE'
                });
            }
            conversations[currentMode] = conversations[currentMode].filter(
                (conversation) => conversation.id !== id
            );
            if (conversations[currentMode].length === 0) {
                const replacement = historyLoadFailed
                    ? createConversation(currentMode, { messagesLoaded: true })
                    : await createPersistedConversation(currentMode);
                conversations[currentMode].push(replacement);
            }
            if (!conversations[currentMode].some((conversation) => conversation.id === activeConversationIds[currentMode])) {
                activeConversationIds[currentMode] = conversations[currentMode][0].id;
            }
            await ensureMessagesLoaded(getActiveConversation());
            renderConversationList();
            renderMessages();
        } catch (error) {
            window.alert(`删除对话失败: ${error.message}`);
        }
    }

    async function renameConversation(event, id) {
        event.stopPropagation();
        const conversation = conversations[currentMode].find((item) => item.id === id);
        if (!conversation) return;

        const newTitle = window.prompt('请输入新的对话名称:', conversation.title);
        if (!newTitle?.trim()) return;
        try {
            if (!historyLoadFailed) {
                await updateConversationTitle(conversation, newTitle.trim());
            } else {
                conversation.title = newTitle.trim();
            }
            renderConversationList();
            renderMessages();
        } catch (error) {
            window.alert(`重命名失败: ${error.message}`);
        }
    }

    async function updateConversationTitle(conversation, title) {
        await apiRequest(`${URLS.conversations}/${encodeURIComponent(conversation.id)}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title })
        });
        conversation.title = title;
    }

    function createIconButton(label, pathData, onClick, hoverClass) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `p-1 text-gray-400 ${hoverClass} rounded`;
        button.setAttribute('aria-label', label);
        button.innerHTML = `<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="${pathData}"></path></svg>`;
        button.addEventListener('click', onClick);
        return button;
    }

    function renderConversationList() {
        elements.chatList.replaceChildren();
        conversations[currentMode].forEach((conversation) => {
            const isActive = conversation.id === activeConversationIds[currentMode];
            const item = document.createElement('div');
            item.className = `group flex items-center justify-between px-3 py-2.5 rounded-xl cursor-pointer transition-all mb-1 ${
                isActive
                    ? 'bg-white shadow-sm border border-gray-200'
                    : 'hover:bg-gray-200/50 border border-transparent'
            }`;
            item.addEventListener('click', () => selectConversation(conversation.id));

            const title = document.createElement('div');
            title.className = `truncate text-sm select-none ${
                isActive ? 'font-medium text-blue-600' : 'text-gray-600'
            }`;
            title.textContent = conversation.title;

            const actions = document.createElement('div');
            actions.className = 'hidden group-hover:flex items-center space-x-1 shrink-0';
            actions.append(
                createIconButton(
                    '重命名对话',
                    'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
                    (event) => renameConversation(event, conversation.id),
                    'hover:text-blue-500'
                ),
                createIconButton(
                    '删除对话',
                    'M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16',
                    (event) => deleteConversation(event, conversation.id),
                    'hover:text-red-500'
                )
            );
            item.append(title, actions);
            elements.chatList.appendChild(item);
        });
    }

    function renderMessages() {
        const conversation = getActiveConversation();
        elements.messages.replaceChildren();
        elements.chatTitle.textContent = conversation?.title || '新的对话';

        if (!conversation || conversation.messages.length === 0) {
            elements.welcome.classList.remove('hidden');
            elements.messages.classList.add('hidden');
            elements.messages.classList.remove('flex');
            return;
        }

        elements.welcome.classList.add('hidden');
        elements.messages.classList.remove('hidden');
        elements.messages.classList.add('flex');
        conversation.messages.forEach((message) => {
            if (message.role === 'user') appendUserMessage(message.content);
            else appendBotMessage(message, false);
        });
        scrollToBottom();
    }

    function showMessages() {
        elements.welcome.classList.add('hidden');
        elements.messages.classList.remove('hidden');
        elements.messages.classList.add('flex');
    }

    function appendUserMessage(text) {
        const row = document.createElement('div');
        row.className = 'flex justify-end w-full';
        const bubble = document.createElement('div');
        bubble.className = 'bg-gray-100 text-gray-800 px-5 py-3 rounded-2xl rounded-tr-sm max-w-[80%] whitespace-pre-wrap leading-relaxed';
        bubble.textContent = text;
        row.appendChild(bubble);
        elements.messages.appendChild(row);
    }

    function appendBotMessage(message, isTyping) {
        const row = document.createElement('div');
        row.className = 'flex justify-start w-full';

        const avatar = document.createElement('div');
        avatar.className = `w-8 h-8 rounded-full bg-gradient-to-tr ${
            message.kind === 'agent' ? 'from-violet-500 to-pink-500' : 'from-blue-500 to-purple-500'
        } shrink-0 mr-4 flex items-center justify-center shadow-sm`;
        avatar.innerHTML = '<svg class="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path d="M19.7 10.9l-4.4-1.4-1.4-4.4c-.2-.6-1-.6-1.2 0L11.3 9.5 6.9 10.9c-.6.2-.6 1 0 1.2l4.4 1.4 1.4 4.4c.2.6 1 .6 1.2 0l1.4-4.4 4.4-1.4c.6-.2.6-1 0-1.2zM21 21l-3-1-1-3c-.1-.3-.5-.3-.6 0l-1 3-3 1c-.3.1-.3.5 0 .6l3 1 1 3c.1.3.5.3.6 0l1-3 3-1c.3-.1.3-.5 0-.6z"/></svg>';

        const content = document.createElement('div');
        content.className = `text-gray-800 pt-1 leading-relaxed max-w-none min-w-0 flex-1 ${
            message.kind === 'agent'
                ? 'agent-response'
                : 'prose prose-slate prose-p:my-1 prose-headings:mb-2 prose-headings:mt-4'
        }`;
        if (isTyping) content.classList.add('typing-cursor');
        renderBotContent(content, message);

        row.append(avatar, content);
        elements.messages.appendChild(row);
        return content;
    }

    function renderBotContent(element, message) {
        if (message.kind === 'agent') {
            renderAgentEvents(element, message.events || []);
            return;
        }
        element.innerHTML = message.content ? marked.parse(message.content) : '';
    }

    function renderAgentEvents(element, events) {
        element.replaceChildren();
        const visibleEvents = events.filter((event) => event.type !== 'complete');
        const allSummaryEvents = visibleEvents.filter((event) => event.type === 'summary');
        const completedSummary = [...allSummaryEvents].reverse().find((event) => event.completed);
        const summaryEvents = completedSummary ? [completedSummary] : allSummaryEvents;
        const errorEvents = visibleEvents.filter((event) => event.type === 'error');
        const reasoningEvents = visibleEvents.filter(
            (event) => event.type !== 'summary' && event.type !== 'error'
        );

        if (reasoningEvents.length > 0) {
            const reasoning = document.createElement('details');
            reasoning.className = 'agent-reasoning';
            reasoning.open = summaryEvents.length === 0;

            const toggle = document.createElement('summary');
            toggle.className = 'agent-reasoning-toggle';
            toggle.textContent = `Agent 思考与执行过程（${reasoningEvents.length} 条）`;

            const list = document.createElement('div');
            list.className = 'agent-event-list agent-reasoning-list';
            reasoningEvents.forEach((event) => list.appendChild(createAgentEventCard(event)));
            reasoning.append(toggle, list);
            element.appendChild(reasoning);
        }

        if (summaryEvents.length > 0) {
            const finalAnswer = document.createElement('div');
            finalAnswer.className = 'agent-final-answer';
            summaryEvents.forEach((event) => finalAnswer.appendChild(createAgentEventCard(event)));
            element.appendChild(finalAnswer);
        }

        if (errorEvents.length > 0) {
            const errors = document.createElement('div');
            errors.className = 'agent-event-list agent-error-list';
            errorEvents.forEach((event) => errors.appendChild(createAgentEventCard(event)));
            element.appendChild(errors);
        }

        if (events.some((event) => event.type === 'complete')) {
            const completed = document.createElement('div');
            completed.className = 'agent-complete';
            completed.textContent = 'Agent 执行完成';
            element.appendChild(completed);
        }
    }

    function createAgentEventCard(event) {
        const card = document.createElement('section');
        card.className = 'agent-event';
        card.dataset.type = event.type || 'execution';

        const header = document.createElement('div');
        header.className = 'agent-event-header';
        const parts = [TYPE_LABELS[event.type] || 'Agent'];
        if (event.step != null) parts.push(`第 ${event.step} 步`);
        if (event.subType) parts.push(SUB_TYPE_LABELS[event.subType] || event.subType);
        header.textContent = parts.join(' · ');

        const content = document.createElement('div');
        content.className = 'agent-event-content prose prose-sm prose-slate max-w-none';
        content.innerHTML = marked.parse(event.content || '');
        card.append(header, content);
        return card;
    }

    function scrollToBottom() {
        elements.chatContainer.scrollTop = elements.chatContainer.scrollHeight;
    }

    function nextSortOrder(conversation) {
        return conversation.messages.reduce(
            (maximum, message) => Math.max(maximum, Number(message.sortOrder) || 0),
            0
        ) + 1;
    }

    function findFinalAgentSummary(message) {
        if (message.kind !== 'agent') return null;
        return [...(message.events || [])].reverse().find(
            (event) => event.type === 'summary' && event.completed === true
        ) || null;
    }

    async function persistMessage(conversation, message) {
        if (historyLoadFailed || !conversation || !message?.id) return;
        const finalAgentSummary = findFinalAgentSummary(message);
        if (message.kind === 'agent' && !finalAgentSummary) return;
        const content = finalAgentSummary
            ? JSON.stringify([finalAgentSummary])
            : (message.content || '');
        const payload = {
            role: message.role,
            content,
            messageType: message.kind === 'agent' ? 'agent' : 'chat',
            status: finalAgentSummary ? 'completed' : (message.status || 'completed'),
            sortOrder: message.sortOrder
        };
        const previousPersistence = message.persistence || Promise.resolve();
        const nextPersistence = previousPersistence
            .catch(() => undefined)
            .then(() => apiRequest(
                `${URLS.conversations}/${encodeURIComponent(conversation.id)}/messages/${encodeURIComponent(message.id)}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                }
            ));
        message.persistence = nextPersistence;
        await nextPersistence;
    }

    function reportPersistenceError(error) {
        console.error('保存聊天记录失败:', error);
        if (persistenceWarningShown) return;
        persistenceWarningShown = true;
        window.alert(`聊天仍可继续，但本次记录保存失败: ${error.message}`);
    }

    function scheduleMessagePersistence(generation) {
        window.clearTimeout(generation.persistTimer);
        generation.persistTimer = window.setTimeout(() => {
            persistMessage(generation.conversation, generation.message)
                .catch(reportPersistenceError);
        }, 300);
    }

    function abortGeneration(saveInterrupted = true) {
        if (!currentGeneration) return Promise.resolve();
        const generation = currentGeneration;
        currentGeneration = null;
        window.clearTimeout(generation.persistTimer);
        generation.abort?.();
        generation.message.status = 'interrupted';
        generation.element?.classList.remove('typing-cursor');
        if (!saveInterrupted) {
            return (generation.message.persistence || Promise.resolve()).catch(() => undefined);
        }
        const persistence = persistMessage(generation.conversation, generation.message);
        persistence.catch(reportPersistenceError);
        return persistence.catch(() => undefined);
    }

    function finishGeneration(generation) {
        window.clearTimeout(generation.persistTimer);
        if (generation.message.status === 'generating') {
            generation.message.status = 'completed';
        }
        persistMessage(generation.conversation, generation.message).catch(reportPersistenceError);
        generation.element?.classList.remove('typing-cursor');
        if (currentGeneration === generation) currentGeneration = null;
    }

    function sendMessage() {
        if (currentGeneration) return;
        const text = elements.input.value.trim();
        if (!text) return;

        if (currentMode === 'agent' && !elements.agentId.value.trim()) {
            window.alert('请填写智能体 ID。');
            elements.agentId.focus();
            return;
        }

        const conversation = getActiveConversation();
        if (!conversation) {
            window.alert('会话尚未准备好，请稍后重试。');
            return;
        }
        showMessages();
        const userMessage = {
            id: createId('message'),
            role: 'user',
            kind: 'user',
            content: text,
            status: 'completed',
            sortOrder: nextSortOrder(conversation)
        };
        conversation.messages.push(userMessage);
        persistMessage(conversation, userMessage).catch(reportPersistenceError);
        appendUserMessage(text);

        if (conversation.title === '新的对话') {
            const title = text.length > 10 ? `${text.slice(0, 10)}...` : text;
            conversation.title = title;
            if (!historyLoadFailed) {
                apiRequest(`${URLS.conversations}/${encodeURIComponent(conversation.id)}`, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title })
                }).catch(reportPersistenceError);
            }
            renderConversationList();
            elements.chatTitle.textContent = conversation.title;
        }

        elements.input.value = '';
        resizeInput();
        updateSendButton();

        const botMessage = currentMode === 'agent'
            ? { id: createId('message'), role: 'assistant', kind: 'agent', events: [], status: 'generating' }
            : { id: createId('message'), role: 'assistant', kind: 'chat', content: '', status: 'generating' };
        botMessage.sortOrder = nextSortOrder(conversation);
        conversation.messages.push(botMessage);
        if (botMessage.kind === 'chat') {
            persistMessage(conversation, botMessage).catch(reportPersistenceError);
        }
        const botElement = appendBotMessage(botMessage, true);
        const generation = {
            mode: currentMode,
            conversation,
            message: botMessage,
            element: botElement,
            abort: null
        };
        currentGeneration = generation;
        scrollToBottom();

        if (currentMode === 'agent') sendAgentMessage(text, generation);
        else sendChatMessage(text, generation);
    }

    function sendChatMessage(text, generation) {
        const selectedRagTag = elements.ragSelect.value.trim();
        const path = selectedRagTag ? 'generate_stream_rag' : 'generate_stream';
        const params = new URLSearchParams({
            chatId: generation.conversation.id,
            message: text
        });
        if (selectedRagTag) params.set('ragTag', selectedRagTag);

        const eventSource = new EventSource(
            `${API_BASE}/rag/${path}?${params.toString()}`,
            { withCredentials: true }
        );
        generation.abort = () => eventSource.close();

        eventSource.onmessage = (event) => {
            if (currentGeneration !== generation) return;
            if (event.data === '[DONE]') {
                eventSource.close();
                finishGeneration(generation);
                return;
            }
            generation.message.content += event.data;
            renderBotContent(generation.element, generation.message);
            scheduleMessagePersistence(generation);
            scrollToBottom();
        };

        eventSource.onerror = () => {
            if (currentGeneration !== generation) return;
            if (!generation.message.content) {
                generation.message.content = '请求失败，请检查后端服务。';
                generation.element.innerHTML = '<span class="text-red-500 text-sm">请求失败，请检查后端服务。</span>';
            }
            generation.message.status = 'failed';
            eventSource.close();
            finishGeneration(generation);
        };
    }

    async function sendAgentMessage(text, generation) {
        const abortController = new AbortController();
        generation.abort = () => abortController.abort();

        try {
            const response = await authenticatedFetch(URLS.agent, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'text/event-stream'
                },
                body: JSON.stringify({
                    aiAgentId: elements.agentId.value.trim(),
                    message: text,
                    sessionId: generation.conversation.id,
                    maxStep: Number.parseInt(elements.agentMaxStep.value, 10) || 3
                }),
                signal: abortController.signal
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            if (!response.body) throw new Error('浏览器不支持流式响应');

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';

            while (true) {
                const { value, done } = await reader.read();
                buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
                buffer = consumeAgentStream(buffer, generation, done);
                if (done) break;
            }
            finishGeneration(generation);
        } catch (error) {
            if (error.name === 'AbortError') return;
            if (currentGeneration !== generation) return;
            generation.message.events.push({
                type: 'error',
                content: `请求失败，请检查 Agent 服务。(${error.message})`
            });
            generation.message.status = 'failed';
            renderBotContent(generation.element, generation.message);
            finishGeneration(generation);
        }
    }

    function consumeAgentStream(buffer, generation, flush = false) {
        const blocks = buffer.split(/\r?\n\r?\n/);
        const remainder = flush ? '' : blocks.pop();
        if (flush && blocks.length === 1 && blocks[0] === '') return '';

        blocks.forEach((block) => {
            const dataLines = block.split(/\r?\n/)
                .filter((line) => line.trim())
                .map((line) => line.replace(/^data:\s?/, ''));
            if (dataLines.length) handleAgentPayload(dataLines.join('\n'), generation);
        });

        if (flush && remainder?.trim()) handleAgentPayload(remainder, generation);
        return remainder || '';
    }

    function handleAgentPayload(rawPayload, generation) {
        if (currentGeneration !== generation) return;
        let payload = rawPayload.trim();
        while (payload.startsWith('data:')) payload = payload.slice(5).trim();
        if (!payload || payload === '[DONE]') return;

        try {
            const event = JSON.parse(payload);
            const isDuplicateComplete = event.type === 'complete'
                && generation.message.events.some((item) => item.type === 'complete');
            if (!isDuplicateComplete) generation.message.events.push(event);
        } catch (error) {
            generation.message.events.push({ type: 'error', content: payload });
        }

        renderBotContent(generation.element, generation.message);
        scheduleMessagePersistence(generation);
        scrollToBottom();
    }

    async function loadRagTagsForSelect() {
        const selectedValue = elements.ragSelect.value;
        elements.ragSelect.innerHTML = '<option value="">普通对话 (不使用知识库)</option>';
        try {
            const response = await authenticatedFetch(URLS.ragTags);
            const result = await response.json();
            if (result.code === '0000' && Array.isArray(result.data)) {
                result.data.forEach((tag) => addRagOption(tag));
                if (result.data.includes(selectedValue)) elements.ragSelect.value = selectedValue;
            }
        } catch (error) {
            console.error('加载知识库列表失败:', error);
        }
    }

    function addRagOption(tag) {
        if (Array.from(elements.ragSelect.options).some((option) => option.value === tag)) return;
        const option = document.createElement('option');
        option.value = tag;
        option.textContent = tag;
        elements.ragSelect.appendChild(option);
    }

    function openModal(id) {
        document.getElementById(id)?.classList.remove('hidden');
        elements.body.classList.add('modal-open');
    }

    function closeModal(id) {
        document.getElementById(id)?.classList.add('hidden');
        elements.body.classList.remove('modal-open');
    }

    async function openTagsModal() {
        openModal('tags-modal');
        const list = document.getElementById('tags-list');
        const loading = document.getElementById('tags-loading');
        const empty = document.getElementById('tags-empty');
        list.replaceChildren();
        loading.classList.remove('hidden');
        empty.classList.add('hidden');
        empty.textContent = '暂无知识库数据';

        try {
            const response = await authenticatedFetch(URLS.ragTags);
            const result = await response.json();
            loading.classList.add('hidden');
            if (result.code !== '0000' || !Array.isArray(result.data) || result.data.length === 0) {
                empty.classList.remove('hidden');
                return;
            }

            result.data.forEach((tag) => {
                const tagElement = document.createElement('button');
                tagElement.type = 'button';
                tagElement.className = 'px-3 py-1.5 bg-blue-50 text-blue-600 border border-blue-100 rounded-lg text-sm font-medium shadow-sm hover:bg-blue-100 cursor-pointer transition';
                tagElement.textContent = tag;
                tagElement.addEventListener('click', () => {
                    addRagOption(tag);
                    elements.ragSelect.value = tag;
                    closeModal('tags-modal');
                    elements.input.focus();
                });
                list.appendChild(tagElement);
            });
        } catch (error) {
            loading.classList.add('hidden');
            empty.textContent = '加载失败，请检查服务连接';
            empty.classList.remove('hidden');
        }
    }

    function resizeInput() {
        elements.input.style.height = '';
        elements.input.style.height = `${elements.input.scrollHeight}px`;
    }

    function updateSendButton() {
        const hasText = elements.input.value.trim().length > 0;
        elements.sendButton.classList.toggle('hidden', !hasText);
        elements.sendButton.classList.toggle('text-blue-500', hasText);
    }

    function bindEvents() {
        elements.modeToggle.addEventListener('click', toggleMode);
        document.getElementById('logout-button').addEventListener('click', logout);
        document.getElementById('new-chat-button').addEventListener('click', createNewConversation);
        document.getElementById('refresh-rag-button').addEventListener('click', loadRagTagsForSelect);
        document.getElementById('open-tags-button').addEventListener('click', openTagsModal);
        elements.sendButton.addEventListener('click', sendMessage);

        elements.input.addEventListener('input', () => {
            resizeInput();
            updateSendButton();
        });
        elements.input.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });

        document.addEventListener('click', (event) => {
            const openButton = event.target.closest('[data-modal-open]');
            if (openButton) openModal(openButton.dataset.modalOpen);
            const closeButton = event.target.closest('[data-modal-close]');
            if (closeButton) closeModal(closeButton.dataset.modalClose);
        });

        document.getElementById('file-upload-input').addEventListener('change', function () {
            const selectedFiles = document.getElementById('selected-files');
            selectedFiles.textContent = this.files.length
                ? `已选文件: ${Array.from(this.files).map((file) => file.name).join(', ')}`
                : '';
            selectedFiles.classList.toggle('text-blue-500', this.files.length > 0);
        });

        document.getElementById('upload-form').addEventListener('submit', uploadFiles);
        document.getElementById('git-upload-form').addEventListener('submit', uploadGitRepository);
    }

    async function uploadFiles(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const tag = document.getElementById('rag-tag-input').value.trim();
        const fileInput = document.getElementById('file-upload-input');
        if (!tag || fileInput.files.length === 0) {
            window.alert('请填写标签并选择至少一个文件！');
            return;
        }

        const button = document.getElementById('upload-btn');
        setSubmitState(button, true, '上传处理中...', 'bg-blue-600', 'bg-blue-400');
        const formData = new FormData();
        formData.append('ragTag', tag);
        Array.from(fileInput.files).forEach((file) => formData.append('file', file));

        try {
            const response = await authenticatedFetch(URLS.ragUpload, { method: 'POST', body: formData });
            const result = await response.json();
            if (result.code !== '0000') throw new Error(result.info || '上传失败');
            window.alert('上传成功！');
            closeModal('upload-modal');
            form.reset();
            document.getElementById('selected-files').textContent = '';
            await loadRagTagsForSelect();
        } catch (error) {
            window.alert(`上传失败: ${error.message}`);
        } finally {
            setSubmitState(button, false, '确认上传', 'bg-blue-600', 'bg-blue-400');
        }
    }

    async function uploadGitRepository(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const repositoryUrl = document.getElementById('git-url-input').value.trim();
        const username = document.getElementById('git-username-input').value.trim();
        const token = document.getElementById('git-token-input').value.trim();
        if (!repositoryUrl || !username || !token) {
            window.alert('请完整填写 Git 信息！');
            return;
        }

        const button = document.getElementById('git-upload-btn');
        setSubmitState(button, true, '拉取并解析中...(可能耗时较长)', 'bg-green-600', 'bg-green-400');
        const body = new URLSearchParams({ reUrl: repositoryUrl, userName: username, token });

        try {
            const response = await authenticatedFetch(URLS.ragGit, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: body.toString()
            });
            const result = await response.json();
            if (result.code !== '0000') throw new Error(result.info || '解析失败');
            window.alert('Git 仓库解析并存入知识库成功！');
            closeModal('git-upload-modal');
            form.reset();
            await loadRagTagsForSelect();
        } catch (error) {
            window.alert(`解析失败: ${error.message}`);
        } finally {
            setSubmitState(button, false, '开始拉取并解析', 'bg-green-600', 'bg-green-400');
        }
    }

    function setSubmitState(button, disabled, text, activeClass, disabledClass) {
        button.disabled = disabled;
        button.textContent = text;
        button.classList.toggle(activeClass, !disabled);
        button.classList.toggle(disabledClass, disabled);
    }

    async function init() {
        if (!await requireAuthentication()) return;
        bindEvents();
        renderConversationList();
        renderMessages();
        try {
            await loadConversationHistory();
        } catch (error) {
            historyLoadFailed = true;
            conversations.chat = [createConversation('chat', { messagesLoaded: true })];
            conversations.agent = [createConversation('agent', { messagesLoaded: true })];
            activeConversationIds.chat = conversations.chat[0].id;
            activeConversationIds.agent = conversations.agent[0].id;
            window.alert(`聊天记录服务暂时不可用，将使用临时会话: ${error.message}`);
        }
        renderConversationList();
        renderMessages();
        loadRagTagsForSelect();
    }

    init();
})();
