/* Vue 3 global build: shared components for four static pages, no npm build needed. */
'use strict';
const { createApp, ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } = Vue;
// Derive the servlet context from this directory, e.g. /cms/devtools/netcapture/ -> /cms/.
const contextRoot = new URL('../../', location.href);
const apiUrl = path => new URL(path.replace(/^\//, ''), contextRoot);
async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
        const response = await fetch(apiUrl(path), {
            ...options, signal: controller.signal,
            headers: { ...options.headers, ...(options.body ? { 'Content-Type': 'application/json' } : {}) }
        });
        const text = await response.text();
        let data;
        try { data = text ? JSON.parse(text) : null; } catch { throw new Error(`响应不是 JSON（HTTP ${response.status}），请检查登录状态及接口地址`); }
        if (!response.ok || data?.status !== 1)
            throw new Error(data?.message || (response.ok ? '请求失败：响应状态异常' : `HTTP ${response.status}`));
        return data.data;
    } catch (error) {
        if (error.name === 'AbortError')
            throw new Error('请求超时，请稍后重试');
        throw error;
    } finally {
        clearTimeout(timeout);
    }
}
const bytes = value => {
    const n = Math.max(0, Number(value) || 0);
    const unit = Math.min(3, Math.floor(Math.log(n || 1) / Math.log(1024)));
    return `${(n / 1024 ** unit).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} ${['B', 'KB', 'MB', 'GB'][unit]}`;
};
const time = value => value ? String(value).replace('T', ' ') : '—';
const address = (ip, port) => ip ? `${ip.includes(':') ? `[${ip}]` : ip}${port != null ? ':' + port : ''}` : '—';
const summary = p => p.summary || [p.httpMethod, p.httpUrl, p.httpStatus, p.tcpFlags].filter(Boolean).join(' ') || '—';

const PacketTable = {
    props: { rows: { type: Array, default: () => [] }, loading: Boolean }, emits: ['detail'],
    setup: () => ({ bytes, time, address, summary }),
    template: `<div class="table-wrap"><table><thead><tr><th>捕获时间</th><th>协议</th><th>源地址</th><th>目标地址</th><th>长度</th><th>摘要</th></tr></thead><tbody>
    <tr v-if="loading"><td colspan="6" class="empty">加载中…</td></tr>
    <tr v-else-if="!rows.length"><td colspan="6" class="empty">暂无数据包</td></tr>
    <tr v-else v-for="(p,i) in rows" :key="p.id ?? i" class="packet-row" @click="$emit('detail',p)" @keydown.enter="$emit('detail',p)" tabindex="0" aria-label="查看数据包详情">
    <td>{{ time(p.captureTime) }}</td><td><span class="badge" :class="p.protocol">{{ p.protocol || '未知' }}</span></td>
    <td>{{ address(p.sourceIp,p.sourcePort) }}</td><td>{{ address(p.destinationIp,p.destinationPort) }}</td><td>{{ bytes(p.packetLength) }}</td><td class="summary" :title="summary(p)">{{ summary(p) }}</td></tr></tbody></table></div>`
};
const MetricCards = {
    props: ['stats'], setup: () => ({ bytes }),
    template: `<div class="metrics"><div class="metric"><span class="muted">数据包总量</span><strong>{{ (stats.totalPackets || 0).toLocaleString() }}</strong><small>当前统计窗口</small></div>
    <div class="metric"><span class="muted">流量总量</span><strong>{{ bytes(stats.totalBytes) }}</strong><small>捕获字节数</small></div>
    <div class="metric"><span class="muted">HTTP 数据包</span><strong>{{ (stats.httpPackets || 0).toLocaleString() }}</strong><small>TCP {{ stats.tcpPackets || 0 }} · UDP {{ stats.udpPackets || 0 }}</small></div>
    <div class="metric"><span class="muted">平均包大小</span><strong>{{ bytes(stats.averagePacketSize) }}</strong><small>ICMP {{ stats.icmpPackets || 0 }}</small></div></div>`
};
const Ranking = {
    props: ['rows', 'field', 'title'],
    setup(props) {
        const total = computed(() => props.rows.reduce((sum, row) => sum + Number(row.count || 0), 0));
        const percent = row => total.value ? (Number(row.count) / total.value * 100).toFixed(1) : '0';
        return { percent };
    },
    template: `<section class="card"><h2>{{ title }}</h2><div class="table-wrap"><table><thead><tr><th>IP 地址</th><th>数据包</th><th>Top 10 内占比</th></tr></thead><tbody><tr v-if="!rows.length"><td colspan="3" class="empty">暂无数据</td></tr><tr v-for="r in rows" :key="r[field]"><td>{{ r[field] || '未知' }}</td><td>{{ r.count }}</td><td>{{ percent(r) }}%<div class="rank"><span :style="{width:percent(r)+'%'}"></span></div></td></tr></tbody></table></div></section>`
};

const application = createApp({
    components: { PacketTable, MetricCards, Ranking },
    setup() {
        const page = document.body.dataset.page || 'index';
        const titles = { index: '抓包控制台', packets: '数据包查询', statistics: '统计分析', realtime: '实时监控' };
        const notice = ref(''), noticeKind = ref('error');
        const notify = (message, kind = 'error') => { notice.value = message; noticeKind.value = kind; };
        const stats = ref({}), status = ref({}), interfaces = ref([]), busy = ref(false);
        const capture = reactive({ networkInterface: '', filter: '' });
        const lastUpdated = ref('');
        let timer, destroyed = false, refreshing = false;
        const detail = ref(null), detailLoading = ref(false);
        let detailVersion = 0;
        const detailFields = { id: 'ID', captureTime: '捕获时间', protocol: '协议', packetLength: '长度（字节）', sourceIp: '源 IP', sourcePort: '源端口', destinationIp: '目标 IP', destinationPort: '目标端口', networkInterface: '网络接口', tcpSeqNumber: 'TCP 序列号', tcpAckNumber: 'TCP 确认号', tcpFlags: 'TCP 标志', httpMethod: 'HTTP 方法', httpUrl: 'HTTP URL', httpStatus: 'HTTP 状态码' };
        async function showDetail(packet) {
            const version = ++detailVersion;
            detail.value = packet;
            if (!packet.id)
                return;
            detailLoading.value = true;
            try {
                const value = await request(`api/packets/${packet.id}`);
                if (version === detailVersion) detail.value = value;
            } catch (e) { notify(e.message); }
            finally { if (version === detailVersion) detailLoading.value = false; }
        }
        function closeDetail() { detailVersion++; detail.value = null; detailLoading.value = false; }
        function keydown(event) { if (event.key === 'Escape') closeDetail(); }
        async function loadInterfaces() {
            try { interfaces.value = (await request('api/capture/interfaces')).interfaces || []; }
            catch (e) { notify(e.message); }
        }
        async function control(action) {
            if (busy.value) return;
            busy.value = true;
            try {
                await request('api/capture/control', { method: 'POST', body: JSON.stringify({ action, ...capture }) });
                notify(action === 'start' ? '抓包已启动' : '抓包已停止', 'success');
                status.value = await request('api/capture/status');
            } catch (e) { notify(e.message); }
            finally { busy.value = false; }
        }

        const filters = reactive({ protocol: '', sourceIp: '', destinationIp: '', sourcePort: '', destinationPort: '', startTime: '', endTime: '' });
        const packets = ref([]), packetLoading = ref(false), pageNo = ref(1), pageSize = ref(20), totalCount = ref(0), totalPage = ref(0);
        const appliedFilters = ref({});
        async function loadPackets(target = 1, apply = false) {
            if (packetLoading.value) return;
            if (apply) {
                if (filters.startTime && filters.endTime && filters.startTime > filters.endTime) {
                    notify('开始时间不能晚于结束时间'); return;
                }
                appliedFilters.value = { ...filters };
            }
            packetLoading.value = true;
            try {
                const params = new URLSearchParams({ pageNo: target, pageSize: pageSize.value });
                for (const [key, value] of Object.entries(appliedFilters.value))
                    if (value !== '' && value != null) {
                        const formatted = key.endsWith('Time') ? value.replace('T', ' ') : value;
                        params.set(key, key.endsWith('Time') && formatted.length === 16 ? formatted + ':00' : formatted);
                    }
                const data = await request(`api/packets?${params}`);
                packets.value = data.list || [];
                pageNo.value = data.currentPage || target;
                totalCount.value = data.totalCount || 0;
                totalPage.value = data.totalPage || 0;
            } catch (e) { notify(e.message); }
            finally { packetLoading.value = false; }
        }
        function resetFilters() {
            for (const key of Object.keys(filters)) filters[key] = '';
            loadPackets(1, true);
        }

        const hours = ref(24), protocols = ref([]), trend = ref([]), sources = ref([]), destinations = ref([]), history = ref([]);
        const chartUnavailable = ref(false);
        let protocolChart, trendChart;
        async function drawCharts() {
            await nextTick();
            if (destroyed) return;
            if (!window.Chart) { chartUnavailable.value = true; return; }
            const colors = ['#2563eb', '#14b8a6', '#f59e0b', '#8b5cf6', '#ec4899', '#64748b'];
            if (!protocolChart) {
                protocolChart = new Chart(document.getElementById('protocol-chart'), { type: 'doughnut', data: { labels: [], datasets: [{ data: [], backgroundColor: colors, borderWidth: 2 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } } });
                trendChart = new Chart(document.getElementById('trend-chart'), { type: 'line', data: { labels: [], datasets: [
                    { label: '数据包', data: [], borderColor: '#2563eb', backgroundColor: '#2563eb12', fill: true, tension: .3, yAxisID: 'packets' },
                    { label: '字节数', data: [], borderColor: '#14b8a6', tension: .3, yAxisID: 'bytes' }
                ] }, options: { responsive: true, maintainAspectRatio: false, scales: { packets: { beginAtZero: true, title: { display: true, text: '数据包' } }, bytes: { position: 'right', beginAtZero: true, grid: { drawOnChartArea: false }, title: { display: true, text: '字节数' } } } } });
            }
            protocolChart.data.labels = protocols.value.map(r => r.protocol || '未知');
            protocolChart.data.datasets[0].data = protocols.value.map(r => Number(r.count));
            protocolChart.update();
            // Repository returns newest first; display time from left to right.
            const points = [...trend.value].reverse();
            trendChart.data.labels = points.map(r => r.timeWindow);
            trendChart.data.datasets[0].data = points.map(r => Number(r.totalPackets));
            trendChart.data.datasets[1].data = points.map(r => Number(r.totalBytes));
            trendChart.update();
        }
        async function refresh() {
            if (refreshing) return;
            refreshing = true;
            const jobs = [];
            jobs.push(request('api/statistics/current').then(r => stats.value = r));
            if (page === 'index')
                jobs.push(request('api/capture/status').then(r => status.value = r));
            if (page === 'statistics') {
                const suffix = `?hours=${hours.value}`;
                jobs.push(request('api/statistics/protocols' + suffix).then(r => protocols.value = r));
                jobs.push(request('api/statistics/trend' + suffix).then(r => trend.value = r));
                jobs.push(request('api/statistics/top-sources' + suffix).then(r => sources.value = r));
                jobs.push(request('api/statistics/top-destinations' + suffix).then(r => destinations.value = r));
                jobs.push(request('api/statistics/recent' + suffix + '&limit=20').then(r => history.value = r));
            }
            try {
                const results = await Promise.allSettled(jobs);
                const failed = results.filter(r => r.status === 'rejected');
                if (failed.length) notify(failed.map(r => r.reason.message).filter((v,i,a) => a.indexOf(v) === i).join('；'));
                else lastUpdated.value = new Date().toLocaleTimeString('zh-CN');
                if (page === 'statistics') await drawCharts();
            } finally { refreshing = false; }
        }

        const connection = ref('未连接'), recent = ref([]), logs = ref([]), received = ref(0), receivedBytes = ref(0), elapsed = ref(1);
        const streamFilter = ref(''), appliedStreamFilter = ref('');
        let socket, reconnectTimer, manualClose = false, startedAt = Date.now(), sequence = 0;
        const rate = computed(() => Math.round(received.value * 60 / elapsed.value));
        const byteRate = computed(() => bytes(receivedBytes.value * 60 / elapsed.value));
        function log(message) {
            logs.value.push({ id: ++sequence, time: new Date().toLocaleTimeString('zh-CN'), message });
            if (logs.value.length > 500) logs.value.shift();
            nextTick(() => {
                const el = document.getElementById('stream-log');
                if (el) el.scrollTop = el.scrollHeight;
            });
        }
        function applyStreamFilter() {
            appliedStreamFilter.value = streamFilter.value.trim();
            if (socket?.readyState === WebSocket.OPEN) {
                socket.send('filter:' + appliedStreamFilter.value);
                log('过滤关键词：' + (appliedStreamFilter.value || '全部'));
            }
        }
        function connect() {
            if (destroyed || socket && socket.readyState < WebSocket.CLOSING) return;
            manualClose = false;
            clearTimeout(reconnectTimer);
            connection.value = '连接中';
            const url = apiUrl('ws/packets');
            url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
            const current = new WebSocket(url);
            socket = current;
            current.onopen = () => {
                if (socket !== current) return;
                connection.value = '已连接'; log('实时连接已建立');
                current.send('filter:' + appliedStreamFilter.value);
            };
            current.onmessage = event => {
                if (socket !== current) return;
                try {
                    const p = JSON.parse(event.data);
                    if (!p || typeof p !== 'object' || Array.isArray(p)) throw new Error('invalid packet');
                    received.value++; receivedBytes.value += Number(p.packetLength) || 0;
                    recent.value.unshift(p);
                    if (recent.value.length > 50) recent.value.pop();
                    log(`${p.protocol || '未知'}  ${address(p.sourceIp,p.sourcePort)} → ${address(p.destinationIp,p.destinationPort)}  ${summary(p)}  ${bytes(p.packetLength)}`);
                } catch { log('收到无法解析的数据包'); }
            };
            current.onerror = () => { if (socket === current) connection.value = '连接异常'; };
            current.onclose = () => {
                if (socket !== current) return;
                connection.value = '已断开'; log('实时连接已断开');
                if (!manualClose && !destroyed) {
                    connection.value = '等待重连';
                    reconnectTimer = setTimeout(connect, 5000);
                }
            };
        }
        function disconnect() {
            manualClose = true;
            clearTimeout(reconnectTimer);
            if (socket) socket.close();
            socket = null;
            connection.value = '已断开';
        }
        function clearStream() {
            recent.value = []; logs.value = []; received.value = 0; receivedBytes.value = 0;
            startedAt = Date.now(); elapsed.value = 1;
        }
        onMounted(() => {
            document.addEventListener('keydown', keydown);
            if (page === 'packets') loadPackets();
            else if (page === 'realtime') {
                connect();
                timer = setInterval(() => elapsed.value = Math.max(1, (Date.now() - startedAt) / 1000), 1000);
            } else {
                if (page === 'index') loadInterfaces();
                refresh();
                // Schedule after completion, so slow responses never overlap.
                const poll = async () => {
                    if (destroyed) return;
                    await refresh();
                    if (!destroyed) timer = setTimeout(poll, page === 'index' ? 5000 : 30000);
                };
                timer = setTimeout(poll, page === 'index' ? 5000 : 30000);
            }
        });
        onBeforeUnmount(() => {
            destroyed = true; clearTimeout(timer); clearInterval(timer); disconnect();
            protocolChart?.destroy(); trendChart?.destroy();
            document.removeEventListener('keydown', keydown);
        });
        return { page, titles, notice, noticeKind, stats, status, interfaces, busy, capture, lastUpdated, control, loadInterfaces,
            bytes, time, detail, detailLoading, detailFields, showDetail, closeDetail,
            filters, packets, packetLoading, pageNo, pageSize, totalCount, totalPage, loadPackets, resetFilters,
            hours, protocols, trend, sources, destinations, history, chartUnavailable, refresh,
            connection, recent, logs, received, receivedBytes, rate, byteRate, streamFilter, appliedStreamFilter, connect, disconnect, clearStream, applyStreamFilter };
    },
    template: `
    <header><a class="brand" href="index.html">NetCapture<small>NETWORK TRAFFIC ANALYZER</small></a><nav aria-label="主导航"><a v-for="(title,key) in titles" :key="key" :href="key+'.html'" :class="{active:page===key}" :aria-current="page===key?'page':undefined">{{ title }}</a></nav></header>
    <main><div class="heading"><div><div class="eyebrow">NETWORK OBSERVABILITY</div><h1>{{ titles[page] }}</h1><p class="muted">{{ {index:'选择网络接口，捕获并分析网络流量。',packets:'按协议、地址、端口和时间定位数据包。',statistics:'流量趋势、协议分布与活跃地址一览。',realtime:'通过 WebSocket 实时查看捕获的数据流。'}[page] }}</p></div><span v-if="lastUpdated" class="muted">更新于 {{ lastUpdated }}</span></div>
    <div v-if="notice" class="notice" :class="noticeKind" role="alert"><span>{{ notice }}</span><button @click="notice=''" aria-label="关闭提示">×</button></div>

    <template v-if="page==='index'">
    <metric-cards :stats="stats"/>
    <div class="grid"><section class="card"><div class="heading"><h2>捕获设置</h2><span class="state"><i class="dot" :class="{on:status.isCapturing}"></i>{{ status.isCapturing?'正在抓包':'已停止' }}</span></div>
    <form @submit.prevent="control('start')"><label>网络接口<select v-model="capture.networkInterface" :disabled="busy || status.isCapturing" required><option disabled value="">请选择网络接口</option><option v-for="n in interfaces" :key="n.name" :value="n.name">{{ n.description || n.name }} · {{ (n.addresses || []).join(', ') }}</option></select></label><label style="margin:16px 0">BPF 过滤规则（可选）<input v-model="capture.filter" placeholder="例如：tcp port 80 或 host 192.168.1.1" :disabled="busy || status.isCapturing"></label>
    <div class="toolbar"><button class="primary" :disabled="busy || status.isCapturing || !capture.networkInterface">{{ busy?'处理中…':'开始抓包' }}</button><button type="button" class="danger" @click="control('stop')" :disabled="busy || !status.isCapturing">停止抓包</button><button type="button" @click="loadInterfaces" :disabled="busy">刷新接口</button></div></form><p class="muted" style="margin-top:18px">本次已捕获 {{ status.capturedPackets || 0 }} 个数据包</p></section>
    <section class="card"><h2>流量分析工作区</h2><p class="muted">抓包在服务器执行，网络接口属于服务器所在的机器。</p><div class="links"><a href="packets.html">数据包查询<small>筛选与报文详情 →</small></a><a href="statistics.html">统计分析<small>协议与流量趋势 →</small></a><a href="realtime.html">实时监控<small>连接实时数据流 →</small></a></div><p class="muted" style="margin-top:24px">接口加载失败时，请检查服务器的 Npcap / libpcap 安装及抓包权限。统计计数由后端按窗口保存、重置。</p></section></div>
    </template>

    <template v-if="page==='packets'">
    <section class="card"><h2>筛选条件</h2><form @submit.prevent="loadPackets(1,true)"><div class="fields"><label>协议<select v-model="filters.protocol"><option value="">全部协议</option><option v-for="p in ['HTTP','TCP','UDP','DNS','DHCP','ICMP']" :key="p">{{ p }}</option></select></label><label>源 IP<input v-model.trim="filters.sourceIp" placeholder="支持部分匹配"></label><label>目标 IP<input v-model.trim="filters.destinationIp" placeholder="支持部分匹配"></label><label>每页条数<select v-model.number="pageSize" @change="loadPackets(1)"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label><label>源端口<input type="number" v-model="filters.sourcePort" min="0" max="65535" placeholder="全部端口"></label><label>目标端口<input type="number" v-model="filters.destinationPort" min="0" max="65535" placeholder="全部端口"></label><label>开始时间<input type="datetime-local" step="1" v-model="filters.startTime"></label><label>结束时间<input type="datetime-local" step="1" v-model="filters.endTime"></label></div><div class="toolbar"><button class="primary" :disabled="packetLoading">{{ packetLoading?'查询中…':'查询数据包' }}</button><button type="button" @click="resetFilters" :disabled="packetLoading">重置</button></div></form></section>
    <section class="card"><h2>数据包列表</h2><packet-table :rows="packets" :loading="packetLoading" @detail="showDetail"/><div class="pagination"><span class="muted">共 {{ totalCount }} 条 · 第 {{ pageNo }} / {{ Math.max(1,totalPage) }} 页</span><div class="toolbar"><button @click="loadPackets(pageNo-1)" :disabled="packetLoading || pageNo<=1">上一页</button><button @click="loadPackets(pageNo+1)" :disabled="packetLoading || pageNo>=totalPage">下一页</button></div></div></section>
    </template>

    <template v-if="page==='statistics'">
    <div class="toolbar" style="margin-bottom:20px"><label>历史统计范围<select v-model.number="hours" @change="refresh"><option :value="1">最近 1 小时</option><option :value="6">最近 6 小时</option><option :value="24">最近 24 小时</option><option :value="168">最近 7 天</option></select></label><button @click="refresh">立即刷新</button><span class="muted">每 30 秒刷新 · 指标卡为当前窗口，图表与排名为所选范围</span></div>
    <metric-cards :stats="stats"/>
    <div v-if="chartUnavailable" class="notice">图表 CDN 加载失败，请检查网络；统计表格仍可使用。</div>
    <div class="grid"><section class="card"><h2>协议分布</h2><div class="chart"><canvas id="protocol-chart" role="img" aria-label="协议分布图"></canvas></div><p v-if="!protocols.length" class="muted">暂无协议数据</p><div class="toolbar"><span v-for="p in protocols" :key="p.protocol" class="badge">{{ p.protocol }} · {{ p.count }}</span></div></section><section class="card"><h2>流量趋势 <small class="muted">最近 20 个统计点</small></h2><div class="chart"><canvas id="trend-chart" role="img" aria-label="流量趋势图"></canvas></div><p v-if="!trend.length" class="muted">暂无趋势数据</p></section><ranking title="Top 10 源 IP" :rows="sources" field="source_ip"/><ranking title="Top 10 目标 IP" :rows="destinations" field="destination_ip"/></div>
    <section class="card" style="margin-top:20px"><h2>历史统计 · 最近 20 条</h2><div class="table-wrap"><table><thead><tr><th>统计时间</th><th>时间窗口</th><th>数据包</th><th>流量</th><th>HTTP</th><th>TCP</th><th>UDP</th><th>平均包大小</th></tr></thead><tbody><tr v-if="!history.length"><td colspan="8" class="empty">暂无历史统计</td></tr><tr v-for="s in history" :key="s.id"><td>{{ time(s.statisticsTime) }}</td><td>{{ s.timeWindow }}</td><td>{{ s.totalPackets }}</td><td>{{ bytes(s.totalBytes) }}</td><td>{{ s.httpPackets }}</td><td>{{ s.tcpPackets }}</td><td>{{ s.udpPackets }}</td><td>{{ bytes(s.averagePacketSize) }}</td></tr></tbody></table></div></section>
    </template>

    <template v-if="page==='realtime'">
    <section class="card"><div class="toolbar"><span class="state"><i class="dot" :class="{on:connection==='已连接'}"></i>{{ connection }}</span><button class="primary" @click="connect" :disabled="connection==='已连接' || connection==='连接中'">连接</button><button @click="disconnect" :disabled="connection==='已断开' || connection==='未连接'">断开</button><button @click="clearStream">清空记录</button><span class="muted">断线后 5 秒自动重连，手动断开则停止重连</span></div><form @submit.prevent="applyStreamFilter" class="toolbar" style="margin-top:18px"><label style="flex:1">过滤关键词<input v-model="streamFilter" placeholder="协议、IP 或 URL 中的一个关键词，如 HTTP / 192.168 / api"></label><button>应用过滤</button></form><p class="muted">当前过滤：{{ appliedStreamFilter || '全部' }} · 仅影响实时接收，不修改服务器抓包规则。</p></section>
    <div class="metrics"><div class="metric"><span class="muted">已接收数据包</span><strong>{{ received.toLocaleString() }}</strong><small>本页打开或清空以来</small></div><div class="metric"><span class="muted">已接收流量</span><strong>{{ bytes(receivedBytes) }}</strong><small>过滤后的数据流</small></div><div class="metric"><span class="muted">平均数据包速率</span><strong>{{ rate }}</strong><small>包 / 分钟</small></div><div class="metric"><span class="muted">平均流量速率</span><strong>{{ byteRate }}</strong><small>每分钟</small></div></div>
    <section class="card"><h2>实时日志 <small class="muted">保留最近 500 条</small></h2><div id="stream-log" class="log" role="log" aria-label="实时数据包日志"><p v-if="!logs.length">等待数据流…</p><div v-for="line in logs" :key="line.id" class="log-line"><time>{{ line.time }}</time>{{ line.message }}</div></div></section><section class="card"><h2>最新数据包 <small class="muted">保留最近 50 条</small></h2><packet-table :rows="recent" @detail="showDetail"/></section>
    </template>
    <footer>NetCapture · 网络流量抓包与分析</footer></main>
    <div v-if="detail" class="modal-backdrop" @click.self="closeDetail"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="detail-title" tabindex="-1"><div class="heading"><h2 id="detail-title">数据包详情</h2><button @click="closeDetail" aria-label="关闭详情" autofocus>关闭 ×</button></div><p v-if="detailLoading" class="muted">正在加载完整报文…</p><div class="detail-grid"><div v-for="(label,key) in detailFields" :key="key"><span>{{ label }}</span>{{ key==='captureTime'?time(detail[key]):(detail[key] ?? '—') }}</div></div><template v-for="(label,key) in {httpHeaders:'HTTP Headers',httpBody:'HTTP Body',payload:'原始载荷'}" :key="key"><div v-if="detail[key]!=null && detail[key]!==''"><div class="subheading">{{ label }}</div><pre>{{ detail[key] }}</pre></div></template></section></div>`
});
application.mount('#app');
// Full-page navigation also releases timers, charts and WebSocket resources.
window.addEventListener('pagehide', () => application.unmount(), { once: true });
window.addEventListener('pageshow', event => { if (event.persisted) location.reload(); });
