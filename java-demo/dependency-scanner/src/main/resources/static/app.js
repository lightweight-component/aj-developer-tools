/**
 * 依赖扫描器前端应用
 * 使用Alpine.js进行状态管理和DOM操作
 */
// 全局变量
let isLoading = false;
let hasScanned = false;
let scanResults = [];
let allScanResults = []; // 保存所有结果，用于过滤
let statistics = null;
let errorMessage = '';
let showModal = false;
let selectedResult = null;
        
// 复制安全版本到剪贴板
function copySafeVersion() {
    if (selectedResult && selectedResult.safeVersion) {
        const versionToCopy = extractVersionFromText(selectedResult.safeVersion);
        
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(versionToCopy).then(() => {
                showCopySuccess();
            }).catch(err => {
                console.error('复制失败:', err);
                fallbackCopyTextToClipboard(versionToCopy);
            });
        } else
            fallbackCopyTextToClipboard(versionToCopy);
    }
}

// 从格式化的文本中提取版本号
function extractVersionFromText(formattedText) {
    if (!formattedText)
        return '';
    
    // 从 "📦 升级到 2.15.3" 中提取 "2.15.3"
    const match = formattedText.match(/升级到\s+([0-9]+(\.[0-9]+)*(-[a-zA-Z0-9]+)?)/);
    if (match)
        return match[1];
    
    // 检查是否已经是纯版本号格式
    if (/^[0-9]+(\.[0-9]+)*(-[a-zA-Z0-9]+)?$/.test(formattedText))
        return formattedText;
    
    return formattedText;
}

// 降级复制方案
function fallbackCopyTextToClipboard(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    
    // 避免滚动到底部
    textArea.style.top = "0";
    textArea.style.left = "0";
    textArea.style.position = "fixed";
    
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful)
            showCopySuccess();
        else
            console.error('降级复制方案失败');
    } catch (err) {
        console.error('降级复制方案出错:', err);
    }
    
    document.body.removeChild(textArea);
}

// 显示复制成功提示
function showCopySuccess() {
    const copyBtn = document.getElementById('copyVersionBtn');
    const originalIcon = copyBtn.innerHTML;
    
    copyBtn.innerHTML = '<i class="fas fa-check text-green-600"></i>';
    copyBtn.title = '已复制！';
    
    setTimeout(() => {
        copyBtn.innerHTML = originalIcon;
        copyBtn.title = '复制版本号';
    }, 2000);
}

// 格式化安全版本显示
function formatSafeVersion(safeVersion) {
    if (!safeVersion || safeVersion.trim() === '')
        return '🔍 查询中...';
    
    if (safeVersion === '请查看最新版本' || safeVersion === '请查看官方文档')
        return '📖 请查看官方文档';
    
    // 检查是否为有效版本号格式
    if (/^[0-9]+(\.[0-9]+)*(\.[0-9]+)*(-[a-zA-Z0-9]+)?$/.test(safeVersion))
        return `📦 升级到 ${safeVersion}`;
    
    return safeVersion;
}

// 格式化漏洞描述
function formatDescription(description) {
    if (!description || description.trim() === '')
        return '暂无描述信息';
    
    // 如果描述太长，截断并添加省略号
    if (description.length > 300)
        return description.substring(0, 300) + '...';
    
    return description;
}

// 格式化CVE ID
function formatCveId(cve) {
    if (!cve || cve.trim() === '')
        return 'N/A';
    
    if (cve === 'OUTDATED-VERSION')
        return '🔄 版本过期';
    
    return cve;
}

// 获取包描述信息
function getPackageDescription(groupId, artifactId) {
    const descriptions = {
        'org.springframework.boot:spring-boot-starter-actuator': '生产就绪功能，用于监控和管理应用',
        'org.springframework.boot:spring-boot-starter-web': 'Web应用开发，包含Spring MVC',
        'org.springframework.boot:spring-boot-starter-data-jpa': 'JPA数据访问，包含Hibernate',
        'org.springframework.boot:spring-boot-starter-security': 'Spring Security安全框架',
        'org.springframework.boot:spring-boot-starter-test': '测试支持，包含JUnit、Mockito',
        'com.fasterxml.jackson.core:jackson-databind': 'Jackson JSON数据绑定库',
        'com.fasterxml.jackson.core:jackson-core': 'Jackson核心库',
        'com.fasterxml.jackson.core:jackson-annotations': 'Jackson注解库',
        'org.apache.logging.log4j:log4j-core': 'Log4j核心日志框架',
        'org.apache.logging.log4j:log4j-api': 'Log4j API接口',
        'com.alibaba:fastjson': 'Alibaba高性能JSON解析库',
        'org.apache.struts:struts2-core': 'Apache Struts2 MVC框架',
        'org.slf4j:slf4j-api': 'Simple Logging Facade for Java',
        'org.hibernate:hibernate-core': 'Hibernate ORM核心库',
        'org.hibernate.validator:hibernate-validator': 'Hibernate Bean验证器',
        'mysql:mysql-connector-java': 'MySQL JDBC驱动',
        'redis.clients:jedis': 'Java Redis客户端',
        'org.apache.commons:commons-lang3': 'Apache Commons Lang工具库',
        'org.springframework:spring-core': 'Spring框架核心库',
        'org.springframework:spring-beans': 'Spring依赖注入容器',
        'org.springframework:spring-context': 'Spring应用上下文',
        'org.springframework:spring-web': 'Spring Web支持',
        'org.springframework:spring-webmvc': 'Spring Web MVC框架',
        'org.apache.tomcat.embed:tomcat-embed-core': 'Tomcat嵌入式核心',
        'ch.qos.logback:logback-classic': 'Logback经典日志实现',
        'ch.qos.logback:logback-core': 'Logback核心库',
        'org.yaml:snakeyaml': 'YAML解析库',
        'com.h2database:h2': 'H2内存数据库',
        'org.junit.jupiter:junit-jupiter': 'JUnit 5测试框架',
        'org.mockito:mockito-core': 'Mockito模拟测试框架'
    };
    
    const key = `${groupId}:${artifactId}`;
    let desc = descriptions[key];
    
    // 如果没有精确匹配，尝试通过artifactId模糊匹配
    if (!desc) {
        if (artifactId.includes('spring-boot-starter'))
            desc = 'Spring Boot启动器';
        else if (artifactId.includes('jackson'))
            desc = 'Jackson JSON处理库';
        else if (artifactId.includes('log4j'))
            desc = 'Log4j日志框架';
        else if (artifactId.includes('slf4j'))
            desc = 'SLF4J日志门面';
        else if (artifactId.includes('logback'))
            desc = 'Logback日志实现';
        else if (artifactId.includes('spring'))
            desc = 'Spring框架组件';
        else if (artifactId.includes('tomcat'))
            desc = 'Tomcat服务器组件';
        else if (artifactId.includes('hibernate'))
            desc = 'Hibernate ORM组件';
        else if (artifactId.includes('junit'))
            desc = 'JUnit测试框架';
        else if (artifactId.includes('mockito'))
            desc = 'Mockito测试框架';
        else
            desc = '第三方依赖包';
    }
    
    return desc;
}

// 截断描述文本
function truncateDescription(text, maxLength) {
    if (!text || text.trim() === '')
        return '暂无描述';
    
    if (text.length <= maxLength)
        return text;
    
    return text.substring(0, maxLength) + '...';
}

// 开始扫描
async function startScan() {
    isLoading = true;
    hasScanned = false;
    errorMessage = '';
    scanResults = [];
    statistics = null;
    
    // 更新UI状态
    updateLoadingState(true);
    hideAllResults();
    
    try {
        const response = await fetch('/api/dependencies/scan', {
            method: 'GET'
        });
        
        if (!response.ok)
            throw new Error(`HTTP error! status: ${response.status}`);
        
        const data = await response.json();
        console.log('API响应原始数据:', data);
        
        if (data.success && data.data) {
            allScanResults = Array.isArray(data.data) ? data.data : [];
            scanResults = [...allScanResults]; // 初始显示所有结果
            statistics = data.statistics || null;
            
            console.log('扫描结果数量:', scanResults.length);
            console.log('是否有漏洞:', scanResults.length > 0);
            
            // 显示过滤器
            if (allScanResults.length > 0)
                document.getElementById('riskFilters').style.display = 'flex';
            
            // 显示结果
            if (scanResults.length > 0) {
                console.log('显示漏洞列表');
                displayResults();
                displayStatistics();
            } else {
                console.log('显示未发现漏洞消息');
                showNoResults();
                displayStatistics(); // 即使没有漏洞也要显示统计信息
            }
            
            showNotification('扫描完成', 'success');
        } else {
            errorMessage = data.message || '扫描失败，请重试';
            showError(errorMessage);
            showNotification('扫描失败', 'error');
        }
    } catch (error) {
        console.error('扫描失败:', error);
        errorMessage = '网络错误或服务器异常，请重试';
        showError(errorMessage);
        showNotification('扫描失败', 'error');
    } finally {
        isLoading = false;
        hasScanned = true;
        updateLoadingState(false);
    }
}


        
// 显示详情模态框 (向后兼容)
function showDetails(result) {
    showVulnerabilityDetails(result);
}

// 关闭模态框
function closeModal() {
    showModal = false;
    selectedResult = null;
    document.getElementById('detailModal').style.display = 'none';
}
        
// 获取风险等级样式类
function getRiskLevelClass(riskLevel) {
    const classes = {
        'CRITICAL': 'risk-critical',
        'HIGH': 'risk-high',
        'MEDIUM': 'risk-medium',
        'LOW': 'risk-low'
    };
    return classes[riskLevel] || 'bg-gray-100 border-gray-500 text-gray-800';
}

// 获取风险等级文本
function getRiskLevelText(riskLevel) {
    const texts = {
        'CRITICAL': '严重',
        'HIGH': '高危',
        'MEDIUM': '中危',
        'LOW': '低危'
    };
    return texts[riskLevel] || riskLevel;
}
        
// 显示通知消息
function showNotification(message, type = 'info') {
    // 简单的控制台日志通知
    console.log(`[${type.toUpperCase()}] ${message}`);
    
    // 可选：创建简单的页面通知
    if (type === 'error')
        console.error(message);
    else if (type === 'success')
        console.info(message);
}
        
// 显示成功消息
function showSuccess(message) {
    showNotification(message, 'success');
}

// 显示错误消息
function showError(message) {
    const errorContainer = document.getElementById('errorMessage');
    const errorText = document.getElementById('errorText');
    
    if (errorContainer && errorText) {
        errorText.textContent = message;
        errorContainer.style.display = 'block';
    }
    
    errorMessage = message;
    showNotification(message, 'error');
}

// 隐藏错误消息
function hideError() {
    const errorContainer = document.getElementById('errorMessage');
    if (errorContainer)
        errorContainer.style.display = 'none';
    errorMessage = '';
}

        
// 导出扫描结果
function exportResults() {
    if (scanResults.length === 0) {
        showError('没有可导出的扫描结果');
        return;
    }
    
    try {
        const data = {
            scanTime: new Date().toISOString(),
            statistics: statistics,
            results: scanResults
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], {
            type: 'application/json'
        });
        
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `dependency-scan-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        showSuccess('扫描结果已导出');
    } catch (error) {
        console.error('导出失败:', error);
        showError('导出失败: ' + error.message);
    }
}
        
// 重置应用状态
function reset() {
    scanResults = [];
    statistics = null;
    errorMessage = '';
    hasScanned = false;
    showModal = false;
    selectedResult = null;
    console.log('应用状态已重置');
}

// DOM操作函数
function updateLoadingState(loading) {
    const scanButton = document.getElementById('scanButton');
    const loadingSpinner = document.getElementById('loadingSpinner');
    const statusIndicator = document.getElementById('statusIndicator');
    const statusSpinner = document.getElementById('statusSpinner');
    const statusText = document.getElementById('statusText');
    
    if (scanButton) {
        scanButton.disabled = loading;
        scanButton.innerHTML = loading ? 
            '<i class="fas fa-spinner fa-spin mr-2"></i>扫描中...' : 
            '<i class="fas fa-search mr-2"></i>开始扫描';
    }
    
    if (loadingSpinner)
        loadingSpinner.style.display = loading ? 'block' : 'none';
    
    // 更新导航栏状态
    if (statusIndicator)
        statusIndicator.style.display = loading ? 'none' : 'block';
    if (statusSpinner)
        statusSpinner.style.display = loading ? 'block' : 'none';
    if (statusText)
        statusText.textContent = loading ? '扫描中...' : '就绪';
}

function hideAllResults() {
    document.getElementById('scanResultsContainer').style.display = 'none';
    document.getElementById('noResultsMessage').style.display = 'none';
    hideError(); // 隐藏错误消息
}

function displayResults() {
    console.log('displayResults() called with', scanResults.length, 'results');
    
    const container = document.getElementById('scanResultsContainer');
    const tbody = document.getElementById('resultsTableBody');
    const countSpan = document.getElementById('resultCount');
    
    // 首先隐藏"未发现漏洞"消息
    document.getElementById('noResultsMessage').style.display = 'none';
    
    // 更新结果数量
    countSpan.textContent = scanResults.length;
    
    // 清空现有内容
    tbody.innerHTML = '';
    
    // 生成表格行
    scanResults.forEach((result, index) => {
        const row = document.createElement('tr');
        row.className = 'hover:bg-gray-50';
        
        row.innerHTML = `
            <td class="dependency-cell">
                <div class="dependency-name">${result.groupId}:${result.artifactId}</div>
                <div class="dependency-desc">${getPackageDescription(result.groupId, result.artifactId)}</div>
            </td>
            <td class="version-cell">
                <div class="text-sm text-gray-900">${result.version}</div>
            </td>
            <td class="risk-cell">
                <span class="inline-flex px-2 py-1 text-xs font-semibold rounded-full border ${getRiskLevelClass(result.riskLevel)}">
                    ${getRiskLevelText(result.riskLevel)}
                </span>
            </td>
            <td class="cve-cell">
                <div class="text-sm text-gray-900">${formatCveId(result.cve)}</div>
            </td>
            <td class="safe-version-cell">
                <div class="text-sm font-medium text-green-600">${formatSafeVersion(result.safeVersion)}</div>
            </td>
            <td class="details-cell">
                <div class="text-sm text-gray-700">${truncateDescription(result.description, 60)}</div>
            </td>
            <td class="action-cell">
                <button class="vulnerability-detail-btn inline-flex items-center px-2 py-1 border border-transparent text-xs leading-4 font-medium rounded-md text-blue-700 bg-blue-100 hover:bg-blue-200 transition duration-150 ease-in-out">
                    <i class="fas fa-eye text-xs"></i>
                </button>
             </td>
        `;
        
        tbody.appendChild(row);
         
        // 绑定漏洞详情按钮事件
        const vulnerabilityDetailBtn = row.querySelector('.vulnerability-detail-btn');
        if (vulnerabilityDetailBtn) {
            vulnerabilityDetailBtn._resultData = result;
            vulnerabilityDetailBtn.addEventListener('click', function() {
                showVulnerabilityDetails(this._resultData);
            });
        }
        
        // 绑定修复方案按钮事件
        const solutionBtn = row.querySelector('.solution-btn');
        if (solutionBtn) {
            solutionBtn._resultData = result;
            solutionBtn.addEventListener('click', function() {
                showSolutionDetails(this._resultData);
            });
        }
    });
    
    container.style.display = 'block';
}

function displayStatistics() {
    if (!statistics)
        return;
    
    const container = document.getElementById('statisticsContainer');
    if (!container)
        return;
    
    // 使用后端返回的统计数据
    const totalDeps = statistics.totalDependencies || 0;      // 所有依赖数
    const vulnerableDeps = statistics.vulnerableDependencies || 0;  // 有漏洞的依赖数
    const scanDuration = statistics.scanDuration ? (statistics.scanDuration / 1000).toFixed(1) + 's' : '0s';
    
    // 计算风险等级分类
    const criticalCount = statistics.criticalCount || 0;
    const highCount = statistics.highCount || 0;
    const mediumCount = statistics.mediumCount || 0;
    const lowCount = statistics.lowCount || 0;
    
    const criticalHighCount = criticalCount + highCount;
    const mediumLowCount = mediumCount + lowCount;
    
    document.getElementById('totalDependencies').textContent = totalDeps;
    document.getElementById('vulnerableDependencies').textContent = vulnerableDeps;
    document.getElementById('criticalHighCount').textContent = criticalHighCount;
    document.getElementById('mediumLowCount').textContent = mediumLowCount;
    document.getElementById('scanDuration').textContent = scanDuration;
    
    container.style.display = 'block';
}

// 风险等级过滤功能
function applyRiskFilter() {
    const filterAll = document.getElementById('filter-all').checked;
    const filterCritical = document.getElementById('filter-critical').checked;
    const filterHigh = document.getElementById('filter-high').checked;
    const filterMedium = document.getElementById('filter-medium').checked;
    const filterLow = document.getElementById('filter-low').checked;
    
    if (filterAll)
        scanResults = [...allScanResults];
    else {
        scanResults = allScanResults.filter(result => {
            const riskLevel = result.riskLevel ? result.riskLevel.toUpperCase() : '';
            return (
                (filterCritical && riskLevel === 'CRITICAL') ||
                (filterHigh && riskLevel === 'HIGH') ||
                (filterMedium && riskLevel === 'MEDIUM') ||
                (filterLow && riskLevel === 'LOW')
            );
        });
    }
    
    // 重新显示过滤后的结果
    if (scanResults.length > 0)
        displayResults();
    else
        showNoResults();
}

function showNoResults() {
    console.log('showNoResults() called');
    
    // 隐藏漏洞结果表格
    document.getElementById('scanResultsContainer').style.display = 'none';
    // 显示"未发现漏洞"消息
    document.getElementById('noResultsMessage').style.display = 'block';
}

// 页面加载完成后的初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log('页面加载完成，依赖扫描器准备就绪');
    
    // 绑定扫描按钮事件
    const scanButton = document.getElementById('scanButton');
    if (scanButton) {
        scanButton.addEventListener('click', function() {
             console.log('扫描按钮被点击');
             startScan();
         });
    }
    
    // 绑定导出按钮事件
    const exportButton = document.getElementById('exportButton');
    if (exportButton)
        exportButton.addEventListener('click', exportResults);
    
    // 绑定过滤器事件
    const filterInputs = ['filter-all', 'filter-critical', 'filter-high', 'filter-medium', 'filter-low'];
    filterInputs.forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            input.addEventListener('change', function() {
                // "全部"选项的特殊处理
                if (id === 'filter-all') {
                    const allChecked = input.checked;
                    filterInputs.slice(1).forEach(otherId => {
                        const otherInput = document.getElementById(otherId);
                        if (otherInput)
                            otherInput.checked = allChecked;
                    });
                } else {
                    // 如果取消选择某个具体选项，则取消全选
                    if (!input.checked) {
                        const allInput = document.getElementById('filter-all');
                        if (allInput)
                            allInput.checked = false;
                    }
                    // 如果所有具体选项都选中，则选中全选
                    const specificInputs = filterInputs.slice(1).map(inputId => 
                        document.getElementById(inputId)?.checked
                    );
                    if (specificInputs.every(checked => checked)) {
                        const allInput = document.getElementById('filter-all');
                        if (allInput)
                            allInput.checked = true;
                    }
                }
                
                applyRiskFilter();
            });
        }
    });
    
    // 绑定模态框关闭事件
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape')
            closeModal();
    });
});

// 显示漏洞详情
function showVulnerabilityDetails(result) {
    selectedResult = result;
    console.log('显示漏洞详情:', result);
    
    showModal = true;
    
    // 更新模态框标题和图标
    updateModalHeader(result);
    
    // 填充基本信息
    fillBasicInfo(result);
    
    // 填充漏洞详情
    fillVulnerabilityDetails(result);
    
    // 填充解决方案
    fillSolutionDetails(result);
    
    // 填充相关信息
    fillRelatedInfo(result);
    
    // 显示模态框
    document.getElementById('detailModal').style.display = 'block';
}

// 显示解决方案详情（直接跳转到解决方案部分）
function showSolutionDetails(result) {
    showVulnerabilityDetails(result);
    // 滚动到解决方案部分
    setTimeout(() => {
        const solutionSection = document.querySelector('.bg-green-50');
        if (solutionSection) {
            solutionSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }, 100);
}

// 更新模态框头部
function updateModalHeader(result) {
    const severityIcon = document.getElementById('modalSeverityIcon');
    const subtitle = document.getElementById('modalSubtitle');
    
    // 根据严重程度设置图标样式
    const severity = result.riskLevel || 'MEDIUM';
    severityIcon.className = `p-2 rounded-full ${getSeverityIconClass(severity)}`;
    severityIcon.innerHTML = `<i class="fas ${getSeverityIcon(severity)} text-xl"></i>`;
    
    subtitle.textContent = `${result.groupId}:${result.artifactId} 的安全漏洞分析`;
}

// 填充基本信息
function fillBasicInfo(result) {
    document.getElementById('modalDependency').textContent = `${result.groupId}:${result.artifactId}`;
    document.getElementById('modalVersion').textContent = result.version;
    document.getElementById('modalCve').textContent = formatCveId(result.cve);
    
    // 设置严重程度徽章
    const severityBadge = document.getElementById('modalSeverityBadge');
    const severity = result.riskLevel || 'MEDIUM';
    severityBadge.className = `inline-flex px-2 py-1 text-xs font-semibold rounded-full ${getRiskLevelClass(severity)}`;
    severityBadge.textContent = getRiskLevelText(severity);
}

// 填充漏洞详情
function fillVulnerabilityDetails(result) {
    document.getElementById('modalDescription').textContent = formatDescription(result.description);
    
    // 显示影响版本信息（如果可用）
    const vulnerableVersionsContainer = document.getElementById('vulnerableVersionsContainer');
    const vulnerableVersions = result.vulnerableVersions;
    
    if (vulnerableVersions && vulnerableVersions.trim() !== '') {
        document.getElementById('modalVulnerableVersions').textContent = vulnerableVersions;
        vulnerableVersionsContainer.style.display = 'block';
    } else
        vulnerableVersionsContainer.style.display = 'none';
}

// 填充解决方案详情
function fillSolutionDetails(result) {
    // 推荐版本
    document.getElementById('modalSafeVersion').textContent = formatSafeVersion(result.safeVersion);
    
    // 生成Maven和Gradle指令
    const safeVersionForCommand = extractVersionFromText(result.safeVersion);
    generateUpgradeCommands(result, safeVersionForCommand);
    
    // 生成解决方案建议
    generateSolutionTips(result);
}

// 填充相关信息
function fillRelatedInfo(result) {
    // 处理参考链接
    const referenceContainer = document.getElementById('modalReferenceContainer');
    const referenceLink = document.getElementById('modalReference');
    
    if (result.reference && result.reference.trim() !== '') {
        referenceLink.href = result.reference;
        referenceLink.textContent = result.reference;
        referenceContainer.style.display = 'block';
    } else
        referenceContainer.style.display = 'none';
    
    // 设置相关资源链接
    setupRelatedLinks(result);
}

// 生成升级指令
function generateUpgradeCommands(result, safeVersion) {
    const groupId = result.groupId;
    const artifactId = result.artifactId;
    
    // Maven指令
    const mavenCommand = document.getElementById('mavenCommand');
    if (safeVersion && safeVersion !== '请查看官方文档' && safeVersion !== '查询中...') {
        mavenCommand.textContent = `<!-- 在 pom.xml 中更新依赖版本 -->
<dependency>
    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>${safeVersion}</version>
</dependency>

<!-- 或者使用Maven命令检查更新 -->
mvn versions:use-latest-versions -Dincludes="${groupId}:${artifactId}"`;
    } else {
        mavenCommand.textContent = `<!-- 请查看官方文档获取最新安全版本 -->
<dependency>
    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version><!-- 请填入安全版本 --></version>
</dependency>`;
    }
    
    // Gradle指令
    const gradleCommand = document.getElementById('gradleCommand');
    if (safeVersion && safeVersion !== '请查看官方文档' && safeVersion !== '查询中...') {
        gradleCommand.textContent = `// 在 build.gradle 中更新依赖版本
implementation '${groupId}:${artifactId}:${safeVersion}'

// 或者使用Gradle命令检查更新
./gradlew dependencyUpdates`;
    } else {
        gradleCommand.textContent = `// 请查看官方文档获取最新安全版本
implementation '${groupId}:${artifactId}:版本号'`;
    }
}

// 生成解决方案提示
function generateSolutionTips(result) {
    const solutionList = document.getElementById('solutionList');
    const tips = [];
    
    const severity = result.riskLevel || 'MEDIUM';
    const cve = result.cve || '';
    const groupId = result.groupId || '';
    
    // 基于严重程度的建议
    if (severity === 'CRITICAL') {
        tips.push('⚠️ 这是一个严重漏洞，建议立即升级到安全版本');
        tips.push('🔄 升级后请重新测试应用程序的核心功能');
    } else if (severity === 'HIGH') {
        tips.push('🚨 这是一个高危漏洞，建议尽快升级');
        tips.push('✅ 优先在测试环境中验证升级后的兼容性');
    } else if (severity === 'MEDIUM') {
        tips.push('⚡ 建议在下次维护窗口期间升级此依赖');
        tips.push('📋 将此依赖加入升级计划清单');
    } else
        tips.push('📝 可在方便时升级此依赖以获得最新安全补丁');
    
    // 基于依赖类型的特定建议
    if (groupId.includes('springframework')) {
        tips.push('🍃 Spring框架升级请参考官方迁移指南');
        tips.push('🧪 特别注意Spring Security相关的配置变更');
    } else if (groupId.includes('jackson')) {
        tips.push('🔧 Jackson升级可能影响JSON序列化/反序列化');
        tips.push('🧪 建议测试JSON处理相关功能');
    } else if (groupId.includes('log4j')) {
        tips.push('📝 Log4j升级可能需要更新日志配置文件');
        tips.push('⚠️ 检查自定义日志Appender的兼容性');
    }
    
    // 通用建议
    tips.push('📚 查看CHANGELOG了解版本间的变更内容');
    tips.push('🔍 使用依赖扫描工具定期检查安全漏洞');
    
    // 生成列表
    solutionList.innerHTML = tips.map(tip => `<li>${tip}</li>`).join('');
}

// 设置相关资源链接
function setupRelatedLinks(result) {
    const cve = result.cve || '';
    const groupId = result.groupId || '';
    const artifactId = result.artifactId || '';
    
    // CVE详情链接
    const cveDetailsLink = document.getElementById('cveDetailsLink');
    if (cve && cve !== 'N/A' && !cve.includes('版本过期'))
        cveDetailsLink.href = `https://cve.mitre.org/cgi-bin/cvename.cgi?name=${cve}`;
    else
        cveDetailsLink.href = `https://cve.mitre.org/cgi-bin/cvekey.cgi?keyword=${artifactId}`;
    
    // Maven Central链接
    const mavenCentralLink = document.getElementById('mavenCentralLink');
    mavenCentralLink.href = `https://search.maven.org/artifact/${groupId}/${artifactId}`;
    
    // OSV数据库链接
    const osvDetailsLink = document.getElementById('osvDetailsLink');
    osvDetailsLink.href = `https://osv.dev/query?package=${groupId}:${artifactId}`;
    
    // GitHub安全公告链接
    const githubAdvisoryLink = document.getElementById('githubAdvisoryLink');
    if (cve && cve !== 'N/A')
        githubAdvisoryLink.href = `https://github.com/advisories?query=${cve}`;
    else
        githubAdvisoryLink.href = `https://github.com/advisories?query=${artifactId}`;
}

// 获取严重程度图标类
function getSeverityIconClass(severity) {
    switch (severity.toUpperCase()) {
        case 'CRITICAL': return 'bg-red-100 text-red-600';
        case 'HIGH': return 'bg-orange-100 text-orange-600';
        case 'MEDIUM': return 'bg-yellow-100 text-yellow-600';
        case 'LOW': return 'bg-blue-100 text-blue-600';
        default: return 'bg-gray-100 text-gray-600';
    }
}

// 获取严重程度图标
function getSeverityIcon(severity) {
    switch (severity.toUpperCase()) {
        case 'CRITICAL': return 'fa-exclamation-triangle';
        case 'HIGH': return 'fa-exclamation-circle';
        case 'MEDIUM': return 'fa-exclamation';
        case 'LOW': return 'fa-info-circle';
        default: return 'fa-shield-alt';
    }
}

// 复制Maven命令
function copyMavenCommand() {
    const mavenCommand = document.getElementById('mavenCommand').textContent;
    copyToClipboard(mavenCommand, '已复制Maven配置！');
}

// 复制Gradle命令
function copyGradleCommand() {
    const gradleCommand = document.getElementById('gradleCommand').textContent;
    copyToClipboard(gradleCommand, '已复制Gradle配置！');
}

// 通用复制函数
function copyToClipboard(text, successMessage) {
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
            showToast(successMessage);
        }).catch(err => {
            console.error('复制失败:', err);
            fallbackCopyTextToClipboard(text, successMessage);
        });
    } else
        fallbackCopyTextToClipboard(text, successMessage);
}

// 显示提示消息
function showToast(message) {
    // 创建toast元素
    const toast = document.createElement('div');
    toast.className = 'fixed top-4 right-4 bg-green-500 text-white px-4 py-2 rounded-lg shadow-lg z-50 transition-opacity duration-300';
    toast.textContent = message;
    
    document.body.appendChild(toast);
    
    // 3秒后自动移除
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => {
            document.body.removeChild(toast);
        }, 300);
    }, 3000);
}

// 全局错误处理
window.addEventListener('error', function(event) {
    console.error('全局错误:', event.error);
});

// 全局未处理的Promise拒绝
window.addEventListener('unhandledrejection', function(event) {
    console.error('未处理的Promise拒绝:', event.reason);
    event.preventDefault();
});
