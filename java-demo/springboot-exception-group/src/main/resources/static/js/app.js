/**
 * Error Fingerprint Monitor - JavaScript Application
 * 前端管理界面的核心逻辑
 */

class ErrorMonitorApp {
    constructor() {
        this.apiBaseUrl = '/api/error-stats';  // 使用相对路径，避免CORS问题
        this.refreshInterval = null;
        this.init();
    }

    /**
     * 初始化应用
     */
    init() {
        this.bindEvents();
        this.loadData();
        this.startAutoRefresh();
    }

    /**
     * 绑定事件处理器
     */
    bindEvents() {
        // 刷新按钮
        document.getElementById('refreshBtn').addEventListener('click', () => {
            this.loadData();
        });

        // 清空缓存按钮
        document.getElementById('clearCacheBtn').addEventListener('click', () => {
            this.clearCache();
        });

        // 错误模拟按钮
        document.querySelectorAll('.simulate-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const errorType = e.target.dataset.type;
                this.simulateError(errorType);
            });
        });

        // 模态框关闭
        document.querySelector('.close').addEventListener('click', () => {
            this.closeModal();
        });

        // 点击模态框外部关闭
        document.getElementById('detailModal').addEventListener('click', (e) => {
            if (e.target.id === 'detailModal')
                this.closeModal();
        });
    }

    /**
     * 加载所有数据
     */
    async loadData() {
        try {
            await Promise.all([
                this.loadOverview(),
                this.loadFingerprints()
            ]);
        } catch (error) {
            console.error('Failed to load data:', error);
            this.showError('加载数据失败，请检查后端服务是否正常运行');
        }
    }

    /**
     * 加载概览数据
     */
    async loadOverview() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/overview`);
            if (!response.ok)
                throw new Error('Failed to fetch overview');

            const data = await response.json();

            document.getElementById('totalFingerprints').textContent = data.totalFingerprints || 0;
            document.getElementById('totalErrors').textContent = this.formatNumber(data.totalErrors || 0);
            document.getElementById('cacheUsage').textContent = `${data.cacheCapacity || 0}/1000`;

            if (data.mostFrequentError) {
                const shortFingerprint = data.mostFrequentError.fingerprint.substring(0, 8);
                document.getElementById('mostFrequentError').textContent =
                    `${shortFingerprint} (${data.mostFrequentError.count})`;
            } else
                document.getElementById('mostFrequentError').textContent = '无';
        } catch (error) {
            console.error('Failed to load overview:', error);
        }
    }

    /**
     * 加载指纹列表
     */
    async loadFingerprints() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/fingerprints`);
            if (!response.ok)
                throw new Error('Failed to fetch fingerprints');

            const fingerprints = await response.json();
            this.renderFingerprintsTable(fingerprints);
        } catch (error) {
            console.error('Failed to load fingerprints:', error);
            this.renderEmptyTable();
        }
    }

    /**
     * 渲染指纹表格
     */
    renderFingerprintsTable(fingerprints) {
        const tbody = document.getElementById('fingerprintsTableBody');

        if (!fingerprints || fingerprints.length === 0) {
            this.renderEmptyTable();
            return;
        }

        // 按错误计数排序
        fingerprints.sort((a, b) => b.count - a.count);

        tbody.innerHTML = fingerprints.map(fp => `
            <tr>
                <td>
                    <span class="fingerprint-short" title="${fp.fingerprint}">
                        ${fp.fingerprint.substring(0, 12)}...
                    </span>
                </td>
                <td><span class="count-badge">${fp.count}</span></td>
                <td class="datetime">${this.formatDateTime(fp.firstOccurrence)}</td>
                <td class="datetime">${this.formatDateTime(fp.lastOccurrence)}</td>
                <td>
                    <span class="trace-id" title="${fp.sampleTraceId}">
                        ${fp.sampleTraceId || 'N/A'}
                    </span>
                </td>
                <td>
                    <div class="recent-traces">
                        ${(fp.recentTraceIds || []).map(traceId =>
                            `<span class="trace-id" title="${traceId}">${traceId.substring(0, 8)}...</span>`
                        ).join('')}
                    </div>
                </td>
                <td>
                    <button class="btn btn-info" onclick="app.showFingerprintDetail('${fp.fingerprint}')">
                        详情
                    </button>
                </td>
            </tr>
        `).join('');
    }

    /**
     * 渲染空表格
     */
    renderEmptyTable() {
        const tbody = document.getElementById('fingerprintsTableBody');
        tbody.innerHTML = `
            <tr>
                <td colspan="7">
                    <div class="empty-state">
                        <div class="empty-state-icon">📭</div>
                        <div>暂无错误指纹数据</div>
                        <small>尝试使用错误模拟器生成一些测试数据</small>
                    </div>
                </td>
            </tr>
        `;
    }

    /**
     * 显示指纹详情
     */
    async showFingerprintDetail(fingerprint) {
        try {
            const response = await fetch(`${this.apiBaseUrl}/fingerprints/${fingerprint}`);
            if (!response.ok)
                throw new Error('Failed to fetch fingerprint detail');

            const detail = await response.json();

            const modalBody = document.getElementById('modalBody');
            modalBody.innerHTML = `
                <div class="detail-item">
                    <div class="detail-label">完整指纹</div>
                    <div class="detail-value">${detail.fingerprint}</div>
                </div>
                ${detail.exceptionType ? `
                <div class="detail-item">
                    <div class="detail-label">异常类型</div>
                    <div class="detail-value">${detail.exceptionType}</div>
                </div>
                ` : ''}
                <div class="detail-item">
                    <div class="detail-label">错误计数</div>
                    <div class="detail-value">${detail.count}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">首次出现时间</div>
                    <div class="detail-value">${this.formatDateTime(detail.firstOccurrence)}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">最后出现时间</div>
                    <div class="detail-value">${this.formatDateTime(detail.lastOccurrence)}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">样本链路ID</div>
                    <div class="detail-value">${detail.sampleTraceId || 'N/A'}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">最近链路IDs (最多5个)</div>
                    <div class="detail-value">
                        ${(detail.recentTraceIds || []).join('<br>') || '无'}
                    </div>
                </div>
                ${detail.stackTrace ? `
                <div class="detail-item">
                    <div class="detail-label">异常堆栈跟踪</div>
                    <div class="detail-value stack-trace">${this.escapeHtml(detail.stackTrace)}</div>
                </div>
                ` : ''}
            `;

            document.getElementById('detailModal').style.display = 'block';
        } catch (error) {
            console.error('Failed to load fingerprint detail:', error);
            alert('加载详情失败');
        }
    }

    /**
     * 关闭模态框
     */
    closeModal() {
        document.getElementById('detailModal').style.display = 'none';
    }

    /**
     * 模拟错误
     */
    async simulateError(errorType) {
        const resultDiv = document.getElementById('simulationResult');
        resultDiv.innerHTML = '<div class="loading"></div> 正在模拟错误...';
        resultDiv.className = 'simulation-result show';

        try {
            const response = await fetch(`${this.apiBaseUrl}/simulate/${errorType}`, {
                method: 'POST'
            });

            if (!response.ok)
                throw new Error('Simulation failed');

            const result = await response.json();

            resultDiv.className = 'simulation-result show success';
            resultDiv.innerHTML = `
                <strong>✅ 模拟成功</strong><br>
                错误类型: ${result.errorType || errorType}<br>
                链路ID: ${result.traceId}<br>
                ${result.message ? `消息: ${result.message}` : ''}
            `;

            // 自动刷新数据
            setTimeout(() => {
                this.loadData();
            }, 1000);

        } catch (error) {
            console.error('Simulation failed:', error);
            resultDiv.className = 'simulation-result show error';
            resultDiv.innerHTML = `
                <strong>❌ 模拟失败</strong><br>
                请检查后端服务是否正常运行
            `;
        }
    }

    /**
     * 清空缓存
     */
    async clearCache() {
        if (!confirm('确定要清空所有错误指纹缓存吗？'))
            return;

        try {
            const response = await fetch(`${this.apiBaseUrl}/clear`, {
                method: 'DELETE'
            });

            if (!response.ok)
                throw new Error('Clear cache failed');

            alert('✅ 缓存清空成功');
            this.loadData();
        } catch (error) {
            console.error('Failed to clear cache:', error);
            alert('❌ 清空缓存失败');
        }
    }

    /**
     * 开始自动刷新
     */
    startAutoRefresh() {
        // 每30秒自动刷新一次
        this.refreshInterval = setInterval(() => {
            this.loadData();
        }, 30000);
    }

    /**
     * 停止自动刷新
     */
    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }

    /**
     * 显示错误信息
     */
    showError(message) {
        const resultDiv = document.getElementById('simulationResult');
        resultDiv.className = 'simulation-result show error';
        resultDiv.innerHTML = `<strong>❌ 错误</strong><br>${message}`;
    }

    /**
     * HTML转义防止XSS
     */
    escapeHtml(text) {
        if (!text)
            return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * 格式化数字
     */
    formatNumber(num) {
        return num.toLocaleString();
    }

    /**
     * 格式化日期时间
     */
    formatDateTime(dateTimeStr) {
        if (!dateTimeStr)
            return 'N/A';

        try {
            const date = new Date(dateTimeStr);
            return date.toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch (error) {
            return dateTimeStr;
        }
    }
}

// 全局应用实例
let app;

// DOM加载完成后初始化应用
document.addEventListener('DOMContentLoaded', () => {
    app = new ErrorMonitorApp();
});

// 页面卸载时清理定时器
window.addEventListener('beforeunload', () => {
    if (app)
        app.stopAutoRefresh();
});