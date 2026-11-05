/**
 * Web SSH 多会话客户端
 * 支持多个SSH连接和会话管理
 */

class MultiSessionWebSSHClient {
    /** 根据统一响应的 status 判断请求结果，并返回 data。 */
    async readApiResponse(response) {
        let payload;
        try {
            payload = await response.json();
        } catch {
            throw new Error(`接口未返回有效 JSON（HTTP ${response.status}）`);
        }
        if (!response.ok || payload?.status !== 1) {
            const error = new Error((payload?.message || `请求失败（HTTP ${response.status}）`) +
                (payload?.errorCode ? ` [${payload.errorCode}]` : ''));
            error.errorCode = payload?.errorCode;
            throw error;
        }
        return payload.data;
    }

    /** 下载支持二进制响应和统一 JSON 中的 Base64 / 字节数组。 */
    async readDownloadResponse(response) {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.toLowerCase().includes('json')) {
            const data = await this.readApiResponse(response);
            if (typeof data === 'string') {
                const bytes = Uint8Array.from(atob(data), character => character.charCodeAt(0));
                return new Blob([bytes], { type: 'application/octet-stream' });
            }
            if (Array.isArray(data) && data.every(value => Number.isInteger(value) && value >= -128 && value <= 255))
                return new Blob([new Uint8Array(data)], { type: 'application/octet-stream' });
            throw new Error('下载接口返回的数据不是文件内容');
        }
        if (!response.ok)
            throw new Error(`文件下载失败（HTTP ${response.status}）`);
        return response.blob();
    }


    constructor() {
        this.sessions = new Map(); // 存储所有会话
        this.activeSessionId = null; // 当前激活的会话ID
        this.nextSessionId = 1; // 下一个会话ID
        this.savedServers = []; // 缓存保存的服务器列表
        
        this.initializeUI();
        this.loadSavedServers();
    }
    
    initializeUI() {
        // 初始化时隐藏终端容器
        document.getElementById('terminalContainer').classList.add('hidden');
    }
    
    // ========== 会话管理 ==========
    createSession(host, port, username, password) {
        const sessionId = `session_${this.nextSessionId++}`;
        const serverName = `${username}@${host}:${port}`;
        
        const session = {
            id: sessionId,
            host: host,
            port: port,
            username: username,
            password: password,
            name: serverName,
            terminal: null,
            websocket: null,
            fitAddon: null,
            connected: false,
            serverId: null
        };
        
        // 创建终端实例
        session.terminal = new Terminal({
            cursorBlink: true,
            fontSize: 14,
            fontFamily: 'Monaco, Menlo, "Ubuntu Mono", Consolas, monospace',
            theme: {
                background: '#1e1e1e',
                foreground: '#d4d4d4',
                cursor: '#ffffff',
                selection: '#ffffff40'
            },
            rows: 25,
            cols: 100
        });
        
        session.fitAddon = new FitAddon.FitAddon();
        session.terminal.loadAddon(session.fitAddon);
        
        this.sessions.set(sessionId, session);
        this.createTabForSession(session);
        this.createTerminalForSession(session);
        this.switchToSession(sessionId);
        
        return session;
    }
    
    createTabForSession(session) {
        const tabsContainer = document.getElementById('terminalTabs');
        
        const tab = document.createElement('div');
        tab.className = 'terminal-tab';
        tab.id = `tab_${session.id}`;
        tab.onclick = () => this.switchToSession(session.id);
        
        tab.innerHTML = `
            <div class="tab-status disconnected"></div>
            <div class="tab-title" title="${session.name}">${session.name}</div>
            <div class="tab-actions">
                <button class="tab-btn" onclick="event.stopPropagation(); sshClient.duplicateSession('${session.id}')" title="复制会话">
                    <i class="fas fa-copy"></i>
                </button>
                <button class="tab-btn" onclick="event.stopPropagation(); sshClient.closeSession('${session.id}')" title="关闭会话">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `;
        
        tabsContainer.appendChild(tab);
    }
    
    createTerminalForSession(session) {
        const contentContainer = document.getElementById('terminalContent');
        
        const sessionDiv = document.createElement('div');
        sessionDiv.className = 'terminal-session';
        sessionDiv.id = `session_${session.id}`;
        
        sessionDiv.innerHTML = `
            <div class="terminal-header">
                <div class="terminal-info">
                    <span class="connection-status" id="status_${session.id}">
                        🔴 未连接
                    </span>
                </div>
                <div class="terminal-actions">
                    <button class="terminal-btn" onclick="switchPage('files')">
                        <i class="fas fa-folder"></i> 文件管理
                    </button>
                    <button class="terminal-btn" onclick="sshClient.disconnectSession('${session.id}')">
                        <i class="fas fa-times"></i> 断开连接
                    </button>
                </div>
            </div>
            <div class="terminal-wrapper">
                <div id="terminal_${session.id}"></div>
            </div>
        `;
        
        contentContainer.appendChild(sessionDiv);
        
        // 初始化终端
        session.terminal.open(document.getElementById(`terminal_${session.id}`));
        session.fitAddon.fit();
    }
    
    switchToSession(sessionId) {
        // 更新标签状态
        document.querySelectorAll('.terminal-tab').forEach(tab => {
            tab.classList.remove('active');
        });
        document.getElementById(`tab_${sessionId}`).classList.add('active');
        
        // 更新内容显示
        document.querySelectorAll('.terminal-session').forEach(session => {
            session.classList.remove('active');
        });
        document.getElementById(`session_${sessionId}`).classList.add('active');
        
        this.activeSessionId = sessionId;
        
        // 调整终端大小
        const session = this.sessions.get(sessionId);
        if (session && session.fitAddon)
            setTimeout(() => session.fitAddon.fit(), 100);
        
        // 显示终端容器
        document.getElementById('terminalContainer').classList.remove('hidden');
        
        this.updateStatusBar();
    }
    
    updateStatusBar() {
        const session = this.sessions.get(this.activeSessionId);
        if (session && session.terminal) {
            const size = session.terminal.buffer.active;
            document.getElementById('terminalStats').textContent = 
                `行: ${size.baseY + size.cursorY + 1}, 列: ${size.cursorX + 1}`;
        }
    }
    
    closeSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session)
            return;
        
        if (this.sessions.size === 1)
            // 如果是最后一个会话，隐藏终端容器
            document.getElementById('terminalContainer').classList.add('hidden');
        
        // 断开连接
        if (session.websocket)
            session.websocket.close();
        
        // 清理DOM元素
        const tab = document.getElementById(`tab_${sessionId}`);
        const sessionDiv = document.getElementById(`session_${sessionId}`);
        if (tab)
            tab.remove();
        if (sessionDiv)
            sessionDiv.remove();
        
        // 从sessions中删除
        this.sessions.delete(sessionId);
        
        // 如果关闭的是当前激活会话，切换到其他会话
        if (sessionId === this.activeSessionId) {
            const remainingSessions = Array.from(this.sessions.keys());
            if (remainingSessions.length > 0)
                this.switchToSession(remainingSessions[0]);
            else
                this.activeSessionId = null;
        }
        
        this.showAlert('会话已关闭', 'info');
    }
    
    duplicateSession(sessionId) {
        const originalSession = this.sessions.get(sessionId);
        if (!originalSession)
            return;
        
        // 创建新会话，使用相同的连接参数
        const newSession = this.createSession(
            originalSession.host,
            originalSession.port,
            originalSession.username,
            originalSession.password
        );
        
        // 自动连接
        this.connectSession(newSession.id);
        
        this.showAlert('会话已复制', 'success');
    }
    
    // ========== SSH连接管理 ==========
    async connectSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session)
            return;
        
        if (session.connected) {
            this.showAlert('会话已连接', 'warning');
            return;
        }
        
        try {
            // 建立WebSocket连接
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/ssh`;
            session.websocket = new WebSocket(wsUrl);
            
            session.websocket.onopen = () => {
                console.log(`Session ${sessionId} WebSocket连接建立`);
                this.updateSessionStatus(sessionId, '正在连接SSH...');
                
                // 发送SSH连接请求
                session.websocket.send(JSON.stringify({
                    type: 'connect',
                    host: session.host,
                    port: parseInt(session.port),
                    username: session.username,
                    password: session.password
                }));
            };
            
            session.websocket.onmessage = (event) => {
                const message = JSON.parse(event.data);
                this.handleSessionMessage(sessionId, message);
            };
            
            session.websocket.onerror = (error) => {
                console.error(`Session ${sessionId} WebSocket错误:`, error);
                this.showAlert('WebSocket连接错误', 'danger');
                session.terminal.writeln('\\r\\n❌ WebSocket连接错误');
            };
            
            session.websocket.onclose = () => {
                console.log(`Session ${sessionId} WebSocket连接关闭`);
                this.handleSessionDisconnection(sessionId);
            };
            
            // 处理终端输入
            session.terminal.onData((data) => {
                if (session.connected && session.websocket.readyState === WebSocket.OPEN)
                    session.websocket.send(JSON.stringify({
                        type: 'command',
                        command: data
                    }));
            });
            
            // 处理终端大小变化
            session.terminal.onResize((size) => {
                if (session.connected && session.websocket.readyState === WebSocket.OPEN)
                    session.websocket.send(JSON.stringify({
                        type: 'resize',
                        cols: size.cols,
                        rows: size.rows
                    }));
            });
            
        } catch (error) {
            console.error('连接失败:', error);
            this.showAlert('连接失败: ' + error.message, 'danger');
        }
    }
    
    handleSessionMessage(sessionId, message) {
        const session = this.sessions.get(sessionId);
        if (!session)
            return;
        
        switch (message.type) {
            case 'connected':
                session.connected = true;
                this.updateTabStatus(sessionId, true);
                this.updateSessionStatus(sessionId, '已连接');
                
                // 查找服务器ID
                session.serverId = this.findServerIdByConnection(
                    session.host, 
                    session.port, 
                    session.username
                );
                
                session.terminal.clear();
                session.terminal.writeln('🎉 SSH连接建立成功!');
                session.terminal.writeln(`连接到: ${session.name}`);
                session.terminal.writeln('');
                
                this.showAlert(`会话 "${session.name}" 连接成功`, 'success');
                break;
                
            case 'output':
                session.terminal.write(message.data);
                break;
                
            case 'error':
                session.terminal.writeln(`\\r\\n❌ 错误: ${message.message}`);
                this.showAlert(`会话连接失败: ${message.message}`, 'danger');
                break;
        }
    }
    
    handleSessionDisconnection(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session)
            return;
        
        session.connected = false;
        session.serverId = null;
        this.updateTabStatus(sessionId, false);
        this.updateSessionStatus(sessionId, '已断开连接');
        
        if (session.terminal)
            session.terminal.writeln('\\r\\n🔌 连接已关闭');
        
        this.showAlert(`会话 "${session.name}" 已断开连接`, 'warning');
    }
    
    disconnectSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session)
            return;
        
        if (session.websocket) {
            session.websocket.send(JSON.stringify({
                type: 'disconnect'
            }));
            session.websocket.close();
        }
        
        this.handleSessionDisconnection(sessionId);
    }
    
    updateTabStatus(sessionId, connected) {
        const tab = document.getElementById(`tab_${sessionId}`);
        if (!tab)
            return;
        
        const statusDot = tab.querySelector('.tab-status');
        if (connected) {
            statusDot.classList.remove('disconnected');
            statusDot.classList.add('connected');
        } else {
            statusDot.classList.remove('connected');
            statusDot.classList.add('disconnected');
        }
    }
    
    updateSessionStatus(sessionId, message) {
        const statusElement = document.getElementById(`status_${sessionId}`);
        if (statusElement)
            statusElement.innerHTML = message.includes('已连接') ? 
                `🟢 ${message}` : 
                `🔴 ${message}`;
        
        // 更新状态栏
        if (sessionId === this.activeSessionId)
            document.getElementById('statusBar').textContent = message;
    }
    
    // ========== 服务器配置管理 ==========
    async loadSavedServers() {
        try {
            const response = await fetch('/api/servers');
            const servers = await this.readApiResponse(response);
            this.savedServers = servers;
            
            const select = document.getElementById('savedServers');
            const fileServerSelect = document.getElementById('fileServerSelect');
            
            select.innerHTML = '<option value="">选择已保存的服务器...</option>';
            fileServerSelect.innerHTML = '<option value="">选择服务器...</option>';
            
            servers.forEach(server => {
                const option = new Option(`${server.name} (${server.host}:${server.port})`, server.id);
                select.add(option);
                
                const fileOption = new Option(`${server.name} (${server.host}:${server.port})`, server.id);
                fileServerSelect.add(fileOption);
            });
            
        } catch (error) {
            console.error('加载服务器列表失败:', error);
        }
    }
    
    findServerIdByConnection(host, port, username) {
        const matchedServer = this.savedServers.find(server => 
            server.host === host && 
            server.port === parseInt(port) && 
            server.username === username
        );
        return matchedServer ? matchedServer.id : null;
    }
    
    async loadServerConfig() {
        const serverId = document.getElementById('savedServers').value;
        if (!serverId)
            return;
        
        try {
            const response = await fetch(`/api/servers/${serverId}`);
            const server = await this.readApiResponse(response);
            
            document.getElementById('host').value = server.host;
            document.getElementById('port').value = server.port;
            document.getElementById('username').value = server.username;
            document.getElementById('serverName').value = server.name;
            
        } catch (error) {
            console.error('加载服务器配置失败:', error);
            this.showAlert('加载服务器配置失败: ' + error.message, 'danger');
        }
    }
    
    async saveServerConfig() {
        const serverData = {
            name: document.getElementById('serverName').value || 
                  `${document.getElementById('username').value}@${document.getElementById('host').value}`,
            host: document.getElementById('host').value,
            port: parseInt(document.getElementById('port').value),
            username: document.getElementById('username').value,
            password: document.getElementById('password').value
        };
        
        try {
            const response = await fetch('/api/servers', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(serverData)
            });
            
            await this.readApiResponse(response);
            this.showAlert('服务器配置已保存', 'success');
            this.loadSavedServers();
        } catch (error) {
            console.error('保存服务器配置失败:', error);
            this.showAlert('保存服务器配置失败: ' + error.message, 'danger');
        }
    }
    
    async testConnection() {
        const testBtn = document.getElementById('testBtn');
        const originalText = testBtn.innerHTML;
        testBtn.innerHTML = '<div class="loading"></div> 测试中...';
        testBtn.disabled = true;
        
        const serverData = {
            host: document.getElementById('host').value,
            port: parseInt(document.getElementById('port').value),
            username: document.getElementById('username').value,
            password: document.getElementById('password').value
        };
        
        try {
            const response = await fetch('/api/servers/test', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(serverData)
            });
            
            await this.readApiResponse(response);
            this.showAlert('连接测试成功', 'success');
        } catch (error) {
            console.error('连接测试失败:', error);
            this.showAlert('连接测试失败: ' + error.message, 'danger');
        } finally {
            testBtn.innerHTML = originalText;
            testBtn.disabled = false;
        }
    }
    
    // ========== 文件管理相关 ==========
    getCurrentServerId() {
        if (this.activeSessionId) {
            const session = this.sessions.get(this.activeSessionId);
            return session ? session.serverId : null;
        }
        return null;
    }
    
    // 文件管理功能
    currentFileServerId = null;
    currentPath = '/';
    
    async switchFileServer() {
        this.currentFileServerId = document.getElementById('fileServerSelect').value;
        if (this.currentFileServerId) {
            this.currentPath = '/';
            document.getElementById('currentPath').value = this.currentPath;
            await this.refreshFiles();
        } else
            document.getElementById('fileGrid').innerHTML = `
                <div class="alert alert-info">
                    请先选择一个服务器来浏览文件
                </div>
            `;
    }
    
    async refreshFiles() {
        if (!this.currentFileServerId) {
            document.getElementById('fileGrid').innerHTML = `
                <div class="alert alert-info">
                    请先选择一个服务器来浏览文件
                </div>
            `;
            return;
        }
        
        try {
            const response = await fetch(`/api/files/list/${this.currentFileServerId}?remotePath=${encodeURIComponent(this.currentPath)}`);
            
            const files = await this.readApiResponse(response);
            this.displayFiles(files);
        } catch (error) {
            console.error('获取文件列表失败:', error);
            this.showAlert('获取文件列表失败: ' + error.message, 'danger');
            document.getElementById('fileGrid').innerHTML = `
                <div class="alert alert-danger">
                    获取文件列表失败: ${error.message}
                </div>
            `;
        }
    }
    
    displayFiles(files) {
        const container = document.getElementById('fileGrid');
        container.innerHTML = '';
        
        files.forEach(file => {
            const fileItem = document.createElement('div');
            fileItem.className = 'file-item';
            fileItem.onclick = () => this.handleFileClick(file);
            
            const icon = file.directory ? 'fas fa-folder' : 'fas fa-file';
            const size = file.directory ? '-' : this.formatFileSize(file.size);
            const date = new Date(file.lastModified).toLocaleString('zh-CN');
            
            fileItem.innerHTML = `
                <i class="${icon} file-icon"></i>
                <span class="file-name">${file.name}</span>
                <span class="file-size">${size}</span>
                <span class="file-date">${date}</span>
                <div class="file-actions">
                    ${!file.directory ? `
                        <button class="btn btn-sm btn-success" onclick="event.stopPropagation(); downloadFile('${file.name}')">
                            <i class="fas fa-download"></i>
                        </button>
                    ` : ''}
                    <button class="btn btn-sm btn-danger" onclick="event.stopPropagation(); deleteFile('${file.name}', ${file.directory})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            `;
            
            container.appendChild(fileItem);
        });
    }
    
    async handleFileClick(file) {
        if (file.directory) {
            this.currentPath = this.currentPath.endsWith('/') ? 
                this.currentPath + file.name : 
                this.currentPath + '/' + file.name;
            document.getElementById('currentPath').value = this.currentPath;
            await this.refreshFiles();
        }
    }
    
    async navigateUp() {
        if (this.currentPath === '/')
            return;
        
        const pathParts = this.currentPath.split('/').filter(p => p);
        pathParts.pop();
        this.currentPath = '/' + pathParts.join('/');
        if (this.currentPath !== '/' && !this.currentPath.endsWith('/'))
            this.currentPath += '/';
        
        document.getElementById('currentPath').value = this.currentPath;
        await this.refreshFiles();
    }
    
    async uploadFiles() {
        console.log('uploadFiles called');
        
        if (!this.currentFileServerId) {
            this.showAlert('请先选择一个服务器', 'danger');
            return;
        }
        
        console.log('Server selected:', this.currentFileServerId);
        
        const files = document.getElementById('uploadFiles').files;
        const uploadPath = document.getElementById('uploadPath').value;
        
        if (files.length === 0) {
            this.showAlert('请选择要上传的文件', 'danger');
            return;
        }
        
        const formData = new FormData();
        for (let file of files)
            formData.append('files', file);
        formData.append('remotePath', uploadPath);
        
        try {
            const response = await fetch(`/api/files/upload-batch/${this.currentFileServerId}`, {
                method: 'POST',
                body: formData
            });
            
            const result = await this.readApiResponse(response);
            this.showAlert(`成功上传 ${result === true ? files.length : result.count} 个文件`, 'success');
            closeModal('uploadModal');
            await this.refreshFiles();
        } catch (error) {
            console.error('上传文件失败:', error);
            this.showAlert('上传文件失败: ' + error.message, 'danger');
        }
    }
    
    async downloadFile(filename) {
        const filePath = this.currentPath.endsWith('/') ? 
            this.currentPath + filename : 
            this.currentPath + '/' + filename;
        
        try {
            const response = await fetch(`/api/files/download/${this.currentFileServerId}?remoteFilePath=${encodeURIComponent(filePath)}`);
            
            {
                const blob = await this.readDownloadResponse(response);
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                
                this.showAlert('文件下载成功', 'success');
            }
        } catch (error) {
            console.error('下载文件失败:', error);
            this.showAlert('下载文件失败: ' + error.message, 'danger');
        }
    }
    
    async deleteFile(filename, isDirectory) {
        if (!confirm(`确定要删除${isDirectory ? '目录' : '文件'} "${filename}" 吗？`))
            return;
        
        const filePath = this.currentPath.endsWith('/') ? 
            this.currentPath + filename : 
            this.currentPath + '/' + filename;
        
        try {
            const response = await fetch(`/api/files/delete/${this.currentFileServerId}?remotePath=${encodeURIComponent(filePath)}&isDirectory=${isDirectory}`, {
                method: 'DELETE'
            });
            
            await this.readApiResponse(response);
            this.showAlert('删除成功', 'success');
            await this.refreshFiles();
        } catch (error) {
            console.error('删除失败:', error);
            this.showAlert('删除失败: ' + error.message, 'danger');
        }
    }
    
    async createFolder() {
        const folderName = prompt('请输入文件夹名称:');
        if (!folderName)
            return;
        
        const folderPath = this.currentPath.endsWith('/') ? 
            this.currentPath + folderName : 
            this.currentPath + '/' + folderName;
        
        try {
            const response = await fetch(`/api/files/mkdir/${this.currentFileServerId}?remotePath=${encodeURIComponent(folderPath)}`, {
                method: 'POST'
            });
            
            await this.readApiResponse(response);
            this.showAlert('文件夹创建成功', 'success');
            await this.refreshFiles();
        } catch (error) {
            console.error('创建文件夹失败:', error);
            this.showAlert('创建文件夹失败: ' + error.message, 'danger');
        }
    }
    
    // ========== UI工具方法 ==========
    showAlert(message, type) {
        const container = document.getElementById('alertContainer');
        const alert = document.createElement('div');
        alert.className = `alert alert-${type}`;
        alert.textContent = message;
        
        container.innerHTML = '';
        container.appendChild(alert);
        
        setTimeout(() => {
            if (alert.parentNode)
                alert.parentNode.removeChild(alert);
        }, 5000);
    }
    
    formatFileSize(bytes) {
        if (bytes === 0)
            return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
}

// ========== 全局函数 ==========
let sshClient = null;

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    sshClient = new MultiSessionWebSSHClient();
});

// 页面切换
function switchPage(pageName) {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        const onclick = item.getAttribute('onclick');
        if (onclick && onclick.includes(`switchPage('${pageName}')`))
            item.classList.add('active');
    });
    
    document.querySelectorAll('.page-content').forEach(page => {
        page.classList.remove('active');
    });
    document.getElementById(`page-${pageName}`).classList.add('active');
    
    if (pageName === 'files')
        loadCurrentServerFiles();
}

function loadCurrentServerFiles() {
    sshClient.loadSavedServers().then(() => {
        // 如果当前有激活的会话，自动选择对应的服务器
        const currentServerId = sshClient.getCurrentServerId();
        if (currentServerId) {
            const fileServerSelect = document.getElementById('fileServerSelect');
            fileServerSelect.value = currentServerId;

            // 设置文件管理的当前服务器ID并加载文件
            sshClient.currentFileServerId = currentServerId;
            sshClient.currentPath = '/';
            document.getElementById('currentPath').value = sshClient.currentPath;
            sshClient.refreshFiles(); // 自动加载文件列表
        }
    });
}

// 侧边栏折叠
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const title = document.getElementById('sidebarTitle');
    const navTexts = document.querySelectorAll('.nav-text');
    
    sidebar.classList.toggle('collapsed');
    
    if (sidebar.classList.contains('collapsed')) {
        title.style.display = 'none';
        navTexts.forEach(text => text.style.display = 'none');
    } else {
        title.style.display = 'inline';
        navTexts.forEach(text => text.style.display = 'inline');
    }
}

// SSH连接相关
function connectSSH() {
    const host = document.getElementById('host').value.trim();
    const port = document.getElementById('port').value.trim();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();
    
    if (!host || !username || !password) {
        sshClient.showAlert('请填写完整的连接信息', 'danger');
        return;
    }
    
    // 创建新会话并连接
    const session = sshClient.createSession(host, port || 22, username, password);
    sshClient.connectSession(session.id);
    
    // 保存服务器配置（如果需要）
    if (document.getElementById('saveServer').checked)
        sshClient.saveServerConfig();
}

function disconnectSSH() {
    if (sshClient.activeSessionId)
        sshClient.disconnectSession(sshClient.activeSessionId);
}

function testConnection() {
    sshClient.testConnection();
}

function loadSavedServers() {
    sshClient.loadSavedServers();
}

function loadServerConfig() {
    sshClient.loadServerConfig();
}

// 文件管理相关
function switchFileServer() {
    sshClient.switchFileServer();
}

function refreshFiles() {
    sshClient.refreshFiles();
}

function navigateUp() {
    sshClient.navigateUp();
}

function showUploadModal() {
    document.getElementById('uploadModal').classList.add('active');
    document.getElementById('uploadPath').value = sshClient.currentPath || '/';
}

function handleUpload() {
    console.log('handleUpload called');
    try {
        sshClient.uploadFiles();
    } catch (error) {
        console.error('Error in handleUpload:', error);
    }
}

function uploadFiles(event) {
    sshClient.uploadFiles(event);
}

function downloadFile(filename) {
    sshClient.downloadFile(filename);
}

function deleteFile(filename, isDirectory) {
    sshClient.deleteFile(filename, isDirectory);
}

function createFolder() {
    sshClient.createFolder();
}

// 弹窗相关
function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

// 点击弹窗背景关闭弹窗
document.addEventListener('click', function(event) {
    if (event.target.classList.contains('modal'))
        event.target.classList.remove('active');
});

// 键盘快捷键
document.addEventListener('keydown', function(event) {
    // Escape 关闭弹窗
    if (event.key === 'Escape')
        document.querySelectorAll('.modal.active').forEach(modal => {
            modal.classList.remove('active');
        });
    
    // Ctrl+Enter 快速连接
    if (event.ctrlKey && event.key === 'Enter' && sshClient.sessions.size === 0)
        connectSSH();
    
    // Ctrl+T 新建会话 (when connected)
    if (event.ctrlKey && event.key === 't' && sshClient.activeSessionId) {
        const currentSession = sshClient.sessions.get(sshClient.activeSessionId);
        if (currentSession)
            sshClient.duplicateSession(sshClient.activeSessionId);
    }
    
    // Ctrl+W 关闭当前会话
    if (event.ctrlKey && event.key === 'w' && sshClient.activeSessionId)
        sshClient.closeSession(sshClient.activeSessionId);
});

// 页面卸载时断开所有连接
window.addEventListener('beforeunload', function(event) {
    if (sshClient && sshClient.sessions.size > 0)
        sshClient.sessions.forEach((session, sessionId) => {
            if (session.websocket)
                session.websocket.close();
        });
});