/**
 * Web SSH 简化版客户端
 * 支持SSH连接和文件管理功能
 */
class SimpleWebSSHClient {
    constructor() {
        this.terminal = null;
        this.websocket = null;
        this.fitAddon = null;
        this.connected = false;
        this.currentServer = null;
        this.currentServerId = null; // 添加当前服务器ID
        this.currentFileServerId = null;
        this.currentPath = '/';
        this.savedServers = []; // 缓存保存的服务器列表
        
        this.initializeTerminal();
        this.loadSavedServers();
    }
    
    // ========== 终端初始化 ==========
    initializeTerminal() {
        this.terminal = new Terminal({
            cursorBlink: true,
            fontSize: 14,
            fontFamily: 'Monaco, Menlo, "Ubuntu Mono", Consolas, monospace',
            theme: {
                background: '#1e1e1e',
                foreground: '#d4d4d4',
                cursor: '#ffffff',
                selection: '#ffffff40'
            },
            rows: 30,
            cols: 120
        });
        
        this.fitAddon = new FitAddon.FitAddon();
        this.terminal.loadAddon(this.fitAddon);
        
        this.terminal.open(document.getElementById('terminal'));
        this.fitAddon.fit();
        
        // 监听窗口大小变化
        window.addEventListener('resize', () => {
            if (this.fitAddon) {
                setTimeout(() => this.fitAddon.fit(), 100);
            }
        });
        
        // 更新终端统计信息
        this.terminal.onResize((size) => {
            document.getElementById('terminalStats').textContent = 
                `行: ${size.rows}, 列: ${size.cols}`;
        });
    }
    
    // ========== SSH连接管理 ==========
    async connect(host, port, username, password) {
        if (this.connected) {
            this.showAlert('已有连接存在，请先断开', 'danger');
            return;
        }
        
        this.currentServer = { 
            host, port, username, 
            name: `${username}@${host}:${port}` 
        };
        
        try {
            // 建立WebSocket连接
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/ssh`;
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                console.log('WebSocket连接建立');
                this.updateStatus('正在连接SSH...');
                
                // 发送SSH连接请求
                this.websocket.send(JSON.stringify({
                    type: 'connect',
                    host: host,
                    port: parseInt(port),
                    username: username,
                    password: password
                }));
            };
            
            this.websocket.onmessage = (event) => {
                const message = JSON.parse(event.data);
                this.handleWebSocketMessage(message);
            };
            
            this.websocket.onerror = (error) => {
                console.error('WebSocket错误:', error);
                this.showAlert('WebSocket连接错误', 'danger');
                this.terminal.writeln('\r\n❌ WebSocket连接错误');
            };
            
            this.websocket.onclose = () => {
                console.log('WebSocket连接关闭');
                this.handleDisconnection();
            };
            
            // 处理终端输入
            this.terminal.onData((data) => {
                if (this.connected && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send(JSON.stringify({
                        type: 'command',
                        command: data
                    }));
                }
            });
            
            // 处理终端大小变化
            this.terminal.onResize((size) => {
                if (this.connected && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send(JSON.stringify({
                        type: 'resize',
                        cols: size.cols,
                        rows: size.rows
                    }));
                }
            });
            
        } catch (error) {
            console.error('连接失败:', error);
            this.showAlert('连接失败: ' + error.message, 'danger');
        }
    }
    
    handleWebSocketMessage(message) {
        switch (message.type) {
            case 'connected':
                this.connected = true;
                this.updateConnectionStatus(true);
                
                // 查找并设置当前服务器ID
                this.currentServerId = this.findServerIdByConnection(
                    this.currentServer.host, 
                    this.currentServer.port, 
                    this.currentServer.username
                );
                
                this.terminal.clear();
                this.terminal.writeln('🎉 SSH连接建立成功!');
                this.terminal.writeln(`连接到: ${this.currentServer.name}`);
                this.terminal.writeln('');
                this.showAlert('SSH连接成功', 'success');
                this.updateStatus('已连接');
                
                // 显示终端容器
                document.getElementById('terminalContainer').classList.remove('hidden');
                this.fitAddon.fit();
                
                // 保存服务器配置（如果需要）
                if (document.getElementById('saveServer').checked) {
                    this.saveServerConfig();
                }
                break;
                
            case 'output':
                this.terminal.write(message.data);
                break;
                
            case 'error':
                this.terminal.writeln(`\r\n❌ 错误: ${message.message}`);
                this.showAlert(`连接失败: ${message.message}`, 'danger');
                this.updateStatus('连接失败');
                break;
        }
    }
    
    disconnect() {
        if (this.websocket) {
            this.websocket.send(JSON.stringify({
                type: 'disconnect'
            }));
            this.websocket.close();
        }
        
        this.handleDisconnection();
    }
    
    handleDisconnection() {
        this.connected = false;
        this.currentServer = null;
        this.currentServerId = null; // 清除当前服务器ID
        this.updateConnectionStatus(false);
        this.updateStatus('已断开连接');
        
        if (this.terminal) {
            this.terminal.writeln('\r\n🔌 连接已关闭');
        }
        
        document.getElementById('terminalContainer').classList.add('hidden');
        this.showAlert('SSH连接已断开', 'danger');
    }
    
    // ========== 服务器配置管理 ==========
    async loadSavedServers() {
        try {
            const response = await fetch('/api/servers');
            const servers = await response.json();
            
            // 缓存服务器列表
            this.savedServers = servers;
            
            const select = document.getElementById('savedServers');
            const fileServerSelect = document.getElementById('fileServerSelect');
            
            // 清空现有选项
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
    
    // 根据连接信息查找服务器ID
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
        if (!serverId) return;
        
        try {
            const response = await fetch(`/api/servers/${serverId}`);
            const server = await response.json();
            
            document.getElementById('host').value = server.host;
            document.getElementById('port').value = server.port;
            document.getElementById('username').value = server.username;
            document.getElementById('serverName').value = server.name;
            // 不填充密码，出于安全考虑
            
        } catch (error) {
            console.error('加载服务器配置失败:', error);
            this.showAlert('加载服务器配置失败', 'danger');
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
            
            const result = await response.json();
            if (result.success) {
                this.showAlert('服务器配置已保存', 'success');
                this.loadSavedServers(); // 重新加载列表
            } else {
                this.showAlert('保存失败: ' + result.message, 'danger');
            }
        } catch (error) {
            console.error('保存服务器配置失败:', error);
            this.showAlert('保存服务器配置失败', 'danger');
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
            
            const result = await response.json();
            if (result.success) {
                this.showAlert('连接测试成功', 'success');
            } else {
                this.showAlert('连接测试失败: ' + result.message, 'danger');
            }
        } catch (error) {
            console.error('连接测试失败:', error);
            this.showAlert('连接测试失败', 'danger');
        } finally {
            testBtn.innerHTML = originalText;
            testBtn.disabled = false;
        }
    }
    
    // 自动选择当前连接的服务器并切换到文件管理
    async switchToFileManagerWithCurrentServer() {
        if (this.currentServerId) {
            // 设置文件服务器选择框
            const fileServerSelect = document.getElementById('fileServerSelect');
            fileServerSelect.value = this.currentServerId;
            
            // 切换文件服务器
            this.currentFileServerId = this.currentServerId;
            this.currentPath = '/';
            document.getElementById('currentPath').value = this.currentPath;
            await this.refreshFiles();
        }
    }
    
    // ========== 文件管理 ==========
    async switchFileServer() {
        this.currentFileServerId = document.getElementById('fileServerSelect').value;
        if (this.currentFileServerId) {
            this.currentPath = '/';
            document.getElementById('currentPath').value = this.currentPath;
            await this.refreshFiles();
        } else {
            document.getElementById('fileGrid').innerHTML = `
                <div class="alert alert-info">
                    请先选择一个服务器来浏览文件
                </div>
            `;
        }
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
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const result = await response.json();
            
            if (result.success) {
                this.displayFiles(result.files);
            } else {
                this.showAlert('获取文件列表失败: ' + result.message, 'danger');
                document.getElementById('fileGrid').innerHTML = `
                    <div class="alert alert-danger">
                        获取文件列表失败: ${result.message}
                    </div>
                `;
            }
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
        if (this.currentPath === '/') return;
        
        const pathParts = this.currentPath.split('/').filter(p => p);
        pathParts.pop();
        this.currentPath = '/' + pathParts.join('/');
        if (this.currentPath !== '/' && !this.currentPath.endsWith('/')) {
            this.currentPath += '/';
        }
        
        document.getElementById('currentPath').value = this.currentPath;
        await this.refreshFiles();
    }
    
    async uploadFiles() {
        console.log('uploadFiles called');
        
        // 检查是否已选择服务器
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
        for (let file of files) {
            formData.append('files', file);
        }
        formData.append('remotePath', uploadPath);
        
        try {
            const response = await fetch(`/api/files/upload-batch/${this.currentFileServerId}`, {
                method: 'POST',
                body: formData
            });
            
            const result = await response.json();
            if (result.success) {
                this.showAlert(`成功上传 ${result.count} 个文件`, 'success');
                this.closeModal('uploadModal');
                await this.refreshFiles();
            } else {
                this.showAlert('上传失败: ' + result.message, 'danger');
            }
        } catch (error) {
            console.error('上传文件失败:', error);
            this.showAlert('上传文件失败', 'danger');
        }
    }
    
    async downloadFile(filename) {
        const filePath = this.currentPath.endsWith('/') ? 
            this.currentPath + filename : 
            this.currentPath + '/' + filename;
        
        try {
            const response = await fetch(`/api/files/download/${this.currentFileServerId}?remoteFilePath=${encodeURIComponent(filePath)}`);
            
            if (response.ok) {
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                
                this.showAlert('文件下载成功', 'success');
            } else {
                this.showAlert('文件下载失败', 'danger');
            }
        } catch (error) {
            console.error('下载文件失败:', error);
            this.showAlert('下载文件失败', 'danger');
        }
    }
    
    async deleteFile(filename, isDirectory) {
        if (!confirm(`确定要删除${isDirectory ? '目录' : '文件'} "${filename}" 吗？`)) return;
        
        const filePath = this.currentPath.endsWith('/') ? 
            this.currentPath + filename : 
            this.currentPath + '/' + filename;
        
        try {
            const response = await fetch(`/api/files/delete/${this.currentFileServerId}?remotePath=${encodeURIComponent(filePath)}&isDirectory=${isDirectory}`, {
                method: 'DELETE'
            });
            
            const result = await response.json();
            if (result.success) {
                this.showAlert('删除成功', 'success');
                await this.refreshFiles();
            } else {
                this.showAlert('删除失败: ' + result.message, 'danger');
            }
        } catch (error) {
            console.error('删除失败:', error);
            this.showAlert('删除失败', 'danger');
        }
    }
    
    async createFolder() {
        const folderName = prompt('请输入文件夹名称:');
        if (!folderName) return;
        
        const folderPath = this.currentPath.endsWith('/') ? 
            this.currentPath + folderName : 
            this.currentPath + '/' + folderName;
        
        try {
            const response = await fetch(`/api/files/mkdir/${this.currentFileServerId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ remotePath: folderPath })
            });
            
            const result = await response.json();
            if (result.success) {
                this.showAlert('文件夹创建成功', 'success');
                await this.refreshFiles();
            } else {
                this.showAlert('创建失败: ' + result.message, 'danger');
            }
        } catch (error) {
            console.error('创建文件夹失败:', error);
            this.showAlert('创建文件夹失败', 'danger');
        }
    }
    
    // ========== UI工具方法 ==========
    updateConnectionStatus(connected) {
        const statusElement = document.getElementById('connectionStatus');
        const connectBtn = document.querySelector('button[onclick="connectSSH()"]');
        const disconnectBtn = document.getElementById('disconnectBtn');
        
        if (connected) {
            statusElement.innerHTML = `🟢 已连接 - ${this.currentServer.name}`;
            connectBtn.disabled = true;
            disconnectBtn.disabled = false;
        } else {
            statusElement.innerHTML = '🔴 未连接';
            connectBtn.disabled = false;
            disconnectBtn.disabled = true;
        }
    }
    
    updateStatus(message) {
        document.getElementById('statusBar').textContent = message;
    }
    
    showAlert(message, type) {
        const container = document.getElementById('alertContainer');
        const alert = document.createElement('div');
        alert.className = `alert alert-${type}`;
        alert.textContent = message;
        
        container.innerHTML = '';
        container.appendChild(alert);
        
        // 5秒后自动消失
        setTimeout(() => {
            if (alert.parentNode) {
                alert.parentNode.removeChild(alert);
            }
        }, 5000);
    }
    
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    closeModal(modalId) {
        document.getElementById(modalId).classList.remove('active');
    }
}

// ========== 全局函数 ==========
let sshClient = null;

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    sshClient = new SimpleWebSSHClient();
});

// 页面切换
function switchPage(pageName) {
    // 更新导航状态
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    
    // 找到对应的导航项并设为激活状态
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        const onclick = item.getAttribute('onclick');
        if (onclick && onclick.includes(`switchPage('${pageName}')`)) {
            item.classList.add('active');
        }
    });
    
    // 切换页面内容
    document.querySelectorAll('.page-content').forEach(page => {
        page.classList.remove('active');
    });
    document.getElementById(`page-${pageName}`).classList.add('active');
    
    // 根据页面执行特定操作
    if (pageName === 'files') {
        sshClient.loadSavedServers().then(() => {
            // 如果当前有连接的服务器，自动选择它
            sshClient.switchToFileManagerWithCurrentServer();
        });
    }
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
    
    sshClient.connect(host, port || 22, username, password);
}

function disconnectSSH() {
    sshClient.disconnect();
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
    if (event.target.classList.contains('modal')) {
        event.target.classList.remove('active');
    }
});

// 键盘快捷键
document.addEventListener('keydown', function(event) {
    // Escape 关闭弹窗
    if (event.key === 'Escape') {
        document.querySelectorAll('.modal.active').forEach(modal => {
            modal.classList.remove('active');
        });
    }
    
    // Ctrl+Enter 快速连接
    if (event.ctrlKey && event.key === 'Enter' && !sshClient.connected) {
        connectSSH();
    }
});

// 页面卸载时断开连接
window.addEventListener('beforeunload', function(event) {
    if (sshClient && sshClient.connected) {
        sshClient.disconnect();
    }
});