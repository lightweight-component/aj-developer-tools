(function () {
    'use strict';
    const root = document.getElementById('app');
    if (!window.Vue || !window.ViewUIPlus) {
        root.removeAttribute('v-cloak');
        root.textContent = '页面组件加载失败，请检查 CDN 网络连接后刷新。';
        return;
    }
    const base = location.pathname.split('/devtools/')[0];
    const staticBase = base + '/devtools/sysinfo-html/';
    const labels = {
        name: '名称', ip: 'IP 地址', osName: '操作系统', osArch: '架构', userDir: '工作目录',
        jdkVersion: 'JDK 版本', jdkHome: 'JDK 路径', jdkName: '运行时', vendor: '厂商', arch: '架构',
        jvmTotalMemory: '已分配内存', maxMemory: '最大内存', freeMemory: '空闲内存', usedMemory: '已用内存',
        usePercent: '使用率（比例）', startTime: '启动时间', uptime: '运行时长（毫秒）',
        physicalProcessorCount: '物理核心数', logicalProcessorCount: '逻辑核心数', systemPercent: '系统 CPU 比例',
        userPercent: '用户 CPU 比例', waitPercent: '等待 CPU 比例', total: '总内存', used: '已用内存', free: '可用内存',
        rxbyt: '接收速率（KB/s）', txbyt: '发送速率（KB/s）', rxpck: '接收包速率（接口原始值）', txpck: '发送包速率（接口原始值）',
        manufacturer: '制造商', model: '型号', serialNumber: '序列号', 'firmware-manufacturer': '固件厂商',
        'firmware-name': '固件名称', 'firmware-version': '固件版本', 'baseboard-manufacturer': '主板厂商',
        'baseboard-model': '主板型号', 'baseboard-version': '主板版本', 'baseboard-serialNumber': '主板序列号'
    };
    async function request(path, params, text = false) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 45000);
        try {
            const response = await fetch(base + path + (params ? '?' + new URLSearchParams(params) : ''), {signal: controller.signal});
            const body = await response.text();
            if (!response.ok) throw new Error('HTTP ' + response.status + '：' + path);
            let data;
            try { data = body ? JSON.parse(body) : null; }
            catch (_) { if (text) return body; throw new Error('接口未返回 JSON：' + path); }
            if (data && (data.status === false || data.success === false)) throw new Error(data.message || '操作失败');
            if (data && Object.hasOwn(data, 'data') && (Object.hasOwn(data, 'status') || Object.hasOwn(data, 'success'))) data = data.data;
            return text && typeof data !== 'string' ? body : data;
        } catch (e) {
            if (e.name === 'AbortError') throw new Error('请求超过 45 秒，请稍后重试');
            throw e;
        } finally { clearTimeout(timer); }
    }
    const DataView = {
        props: ['value'],
        methods: { show(value) { return value == null ? '—' : typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value); }, label(key) { return labels[key] || key; } },
        template: `<div class="scroll"><table v-if="value && Object.keys(value).length" class="data-table"><tbody><tr v-for="(item,key) in value" :key="key"><th>{{label(key)}}</th><td><pre>{{show(item)}}</pre></td></tr></tbody></table><div v-else class="empty">暂无数据</div></div>`
    };
    const template = `
    <div class="shell"><aside class="sidebar"><div class="brand">DevTools</div><div class="caption">系统监控与 JVM 管理</div><nav>
      <a v-for="item in nav" :href="url(item.file)" :class="{active:page===item.key}">{{item.title}}</a>
      <a :href="url('database-doc/index.htm')">数据库文档</a>
    </nav></aside><main>
      <div class="heading"><h1>{{title}}</h1><span class="muted">{{updated ? '最后更新 '+updated : '尚未加载'}}</span></div>
      <p class="subtitle">{{page==='system' ? '当前应用所在主机 · 按需采样，独立加载各项数据' : page==='toolkit' ? '常用开发工具' : 'JMX 连接由后端共享；切换连接会影响其他访问者。宿主机监控不随连接切换。'}}</p>
      <template v-if="page==='system'">
        <div class="grid"><section class="panel" v-for="key in ['cpu','memory','disk']"><div class="toolbar"><h2>{{sections[key].title}}</h2><i-button size="small" :loading="busy[key]" @click="loadSection(key)">刷新</i-button></div>
          <div v-if="errors[key]" class="error">{{errors[key]}}</div><template v-if="data[key]"><div class="metric">{{percent(key==='disk' ? diskRatio : data[key].usePercent)}}</div><i-progress :percent="progress(key==='disk' ? diskRatio : data[key].usePercent)"></i-progress><span class="muted">{{ key==='memory' ? data.memory.used+' / '+data.memory.total : key==='disk' ? bytes(data.disk.usedSize)+' / '+bytes(data.disk.totalSpace) : '累计采样约 1 秒' }}</span></template><div v-else class="empty">{{busy[key]?'正在采样…':'暂无数据'}}</div>
        </section></div>
        <section class="panel" v-for="key in ['system','jvm','network','hardware']"><div class="toolbar"><h2>{{sections[key].title}}</h2><i-button size="small" :loading="busy[key]" @click="loadSection(key)">{{data[key]?'刷新':'加载'}}</i-button></div><p v-if="key==='network'" class="muted">采样约 3 秒；包速率保留后端原始值，当前后端另做了 /1024 换算。</p><div v-if="errors[key]" class="error">{{errors[key]}}</div><data-view :value="data[key]"></data-view></section>
        <section class="panel"><h2>磁盘分区</h2><div class="scroll"><table class="data-table"><thead><tr><th>名称 / 挂载点</th><th>文件系统</th><th>总容量</th><th>可用</th><th>已用比例</th></tr></thead><tbody><tr v-for="disk in data.disk?.diskInfoList || []"><td>{{disk.name}} / {{disk.mount}}</td><td>{{disk.type}}</td><td>{{disk.size}}</td><td>{{disk.avail}}</td><td>{{disk.usePercent}}%</td></tr></tbody></table></div></section>
        <section class="panel"><div class="toolbar"><h2>CPU 前 10 个进程</h2><i-button :loading="busy.processes" @click="loadSection('processes')">查询进程</i-button></div><p class="muted">按进程生命周期累计平均 CPU 排序；不是实时值，多核占用可超过 100%。</p><div v-if="errors.processes" class="error">{{errors.processes}}</div><table class="data-table" v-if="data.processes?.length"><thead><tr><th>PID</th><th>名称</th><th>CPU</th></tr></thead><tbody><tr v-for="p in data.processes" :key="p.pid"><td>{{p.pid}}</td><td>{{p.name}}</td><td>{{percent(p.cpu)}}</td></tr></tbody></table><div v-else class="empty">{{busy.processes?'正在查询系统进程…':data.processes?'未发现可用进程':'点击查询加载进程列表'}}</div></section>
      </template>
      <template v-else-if="page==='jmx' || page==='tomcat'">
        <section class="panel"><h2>JMX 连接</h2><div class="connection"><i-button :loading="busy.local" @click="action('local',()=>loadLocal())">发现本地 JVM</i-button><span class="muted">{{connection}}</span></div><div v-if="errors.local" class="error">{{errors.local}}</div><div class="connection" v-for="vm in local" :key="vm.pid"><span>{{vm.pid}} · {{vm.name || '未命名 JVM'}}</span><i-button size="small" :disabled="busy.connect" @click="connect({pid:vm.pid})">连接</i-button></div><p v-if="dataLocalLoaded && !local.length" class="muted">未发现可连接 JVM</p>
        <form class="connection" @submit.prevent="connect({host,port})"><input v-model.trim="host" placeholder="远程主机" aria-label="远程主机" required><input v-model.trim="port" placeholder="JMX 端口" aria-label="JMX 端口" type="number" min="1" max="65535" required><i-button html-type="submit" :loading="busy.connect">连接远程 JVM</i-button></form><div v-if="errors.connect" class="error">{{errors.connect}}</div></section>
        <section class="panel"><div class="toolbar"><h2>已连接 JVM 概览</h2><i-button :loading="busy.overview" @click="loadOverview">刷新概览</i-button></div><div v-if="errors.overview" class="error">{{errors.overview}}</div><div class="grid"><section v-for="(value,key) in overview"><h3>{{overviewLabels[key] || key}}</h3><data-view :value="value"></data-view></section></div><p v-if="!overview" class="empty">连接 JVM 后查看概览</p></section>
        <section class="panel"><div class="toolbar"><h2>MBean 浏览器{{page==='tomcat'?' · Tomcat':''}}</h2><i-button :loading="busy.domains" @click="loadDomains">加载 MBean</i-button></div><div v-if="errors.domains" class="error">{{errors.domains}}</div><div class="columns"><div><input v-model="filter" placeholder="筛选 ObjectName" aria-label="筛选 MBean"><select v-model="selected" size="12" aria-label="MBean 列表" @change="loadBean"><option v-for="bean in filteredBeans" :value="bean.fullName">{{bean.fullName}}</option></select><span class="muted">共 {{filteredBeans.length}} 个 MBean</span></div><div><p>{{selected || '选择左侧 MBean 查看属性'}}</p><div v-if="errors.bean" class="error">{{errors.bean}}</div><p v-if="busy.bean" class="muted">正在读取属性…</p><table v-if="bean" class="data-table"><thead><tr><th>属性</th><th>值</th></tr></thead><tbody><tr v-for="attr in bean.beanAttributeInfos"><td>{{attr.name}}</td><td><pre>{{attr.value?.error || show(attr.value?.data)}}</pre></td></tr></tbody></table><h3 v-if="bean">操作</h3><div class="connection" v-for="op in bean?.operationInfos || []"><span>{{op.name}} ({{(op.signature || []).map(p=>p.type).join(', ')}})</span><i-button size="small" :disabled="busy.invoke || busy.bean || !!op.signature?.length" @click="invoke(op)">执行</i-button><span v-if="op.signature?.length" class="muted">接口仅支持无参操作</span></div><div v-if="errors.invoke" class="error">{{errors.invoke}}</div><p v-if="invokeResult" class="muted">{{invokeResult}}</p></div></div></section>
        <section class="panel"><div class="toolbar"><h2>当前应用 JDBC 连接池</h2><i-button :loading="busy.jdbc" @click="loadJdbc">读取</i-button></div><div v-if="errors.jdbc" class="error">{{errors.jdbc}}</div><data-view :value="jdbc"></data-view></section>
        <section class="panel"><h2>当前应用 MBean 文本导出</h2><div class="connection"><input v-model="dumpName" placeholder="类名后缀，逗号分隔" aria-label="导出类名筛选"><i-button :loading="busy.dump" @click="loadDump">读取文本</i-button></div><p class="muted">此接口固定查询当前应用，不随上方 JMX 连接切换。</p><div v-if="errors.dump" class="error">{{errors.dump}}</div><pre>{{dump}}</pre></section>
      </template>
      <template v-else><section class="panel"><h2>开发工具</h2><p class="muted">原页面的 SQL 格式化、代码统计和数据库备份尚无对应后端接口，暂不提供操作。</p><p><a :href="url('database-doc/index.htm')">打开数据库文档工具</a></p><p><a :href="url('jvm.html')">打开 JMX / MBean 浏览器</a></p></section></template>    </main></div>`;
    const app = Vue.createApp({
        components: {DataView}, template,
        data() { return {
            page: document.body.dataset.page || 'system',
            nav: [{key:'system',title:'系统监控',file:'index.html'},{key:'jmx',title:'JVM / JMX',file:'jvm.html'},{key:'tomcat',title:'Tomcat MBean',file:'tomcat.html'},{key:'toolkit',title:'开发工具',file:'toolkit/toolkit.html'}],
            sections: {system:{title:'操作系统',path:'sys_info'},jvm:{title:'当前应用 JVM',path:'jvm_info'},cpu:{title:'CPU',path:'cpu_info'},memory:{title:'内存',path:'memory_info'},disk:{title:'磁盘',path:'disk_info'},network:{title:'网络速率',path:'net_io_info'},hardware:{title:'主板与固件',path:'computer_system'},processes:{title:'进程',path:'process_list'}},
            data:{},busy:{},errors:{},updated:'', local:[],dataLocalLoaded:false,host:'localhost',port:'1099',connection:'尚未在此页面建立连接',overview:null,
            overviewLabels:{systemInfo:'系统',jvmInfo:'JVM',heapMemoryUsage:'堆内存',nonHeapMemoryUsage:'非堆内存',metaSpace:'元空间',threadInfo:'线程',classLoadingInfo:'类加载',garbageCollectorInfo:'垃圾收集器'},
            beans:[],filter:document.body.dataset.page==='tomcat'?'Catalina':'',selected:'',bean:null,beanVersion:0,jdbc:null,dumpName:new URLSearchParams(location.search).get('name') || '',dump:'',invokeResult:''
        }; },
        computed: {
            title() { return this.nav.find(n=>n.key===this.page)?.title || '开发工具'; },
            diskRatio() { return this.data.disk?.totalSpace > 0 ? this.data.disk.usedSize / this.data.disk.totalSpace : null; },
            filteredBeans() { const q=this.filter.toLowerCase(); return this.beans.filter(b=>b.fullName.toLowerCase().includes(q)); }
        },
        methods: {
            url(file) {return staticBase+file;}, show(value) {return value==null?'—':typeof value==='object'?JSON.stringify(value,null,2):String(value);},
            percent(value) {return value==null || !Number.isFinite(Number(value))?'—':(Number(value)*100).toFixed(2)+'%';},
            progress(value) {return Math.max(0,Math.min(100,Math.round(Number(value||0)*100)));},
            bytes(value) {if(value==null)return '—';let n=Number(value),i=0;const units=['B','KB','MB','GB','TB'];while(n>=1024&&i<4){n/=1024;i++;}return n.toFixed(1)+' '+units[i];},
            async action(key, fn) { if(this.busy[key])return;this.busy[key]=true;this.errors[key]='';try{await fn();this.updated=new Date().toLocaleTimeString();}catch(e){this.errors[key]=e.message;}finally{this.busy[key]=false;} },
            loadSection(key) {return this.action(key,async()=>{this.data[key]=await request('/system_monitor/'+this.sections[key].path);});},
            async loadLocal() {this.local=await request('/jvm/localJvms') || [];this.dataLocalLoaded=true;},
            connect(params) {return this.action('connect',async()=>{await request(params.pid!=null?'/jvm/attachLocalJvm':'/jvm/attachRemoteJvm',params);this.connection=params.pid!=null?'本地 PID '+params.pid:params.host+':'+params.port;this.beanVersion++;this.bean=null;this.selected='';this.beans=[];this.overview=null;await Promise.all([this.loadOverview(),this.loadDomains()]);});},
            loadOverview() {return this.action('overview',async()=>{this.overview=await request('/jvm/dashboard/overview');});},
            loadDomains() {return this.action('domains',async()=>{const nodes=await request('/jvm/domains') || [];const leaves=[];const walk=items=>items.forEach(n=>{if(n.fullName)leaves.push(n);if(n.children)walk(n.children);});walk(nodes);this.beans=leaves;});},
            async loadBean() {const version=++this.beanVersion;const selected=this.selected;this.bean=null;this.errors.bean='';this.invokeResult='';this.busy.bean=true;try{const value=await request('/jvm/properties',{fullName:selected});if(version===this.beanVersion)this.bean=value;}catch(e){if(version===this.beanVersion)this.errors.bean=e.message;}finally{if(version===this.beanVersion)this.busy.bean=false;}},
            invoke(op) {if(op.signature?.length || !window.confirm('确认执行 '+this.selected+' 的 '+op.name+'？这可能修改目标 JVM 状态。'))return;return this.action('invoke',async()=>{await request('/jvm/run',{fullName:this.selected,methodName:op.name});await this.loadBean();this.invokeResult='操作执行成功';});},
            loadJdbc() {return this.action('jdbc',async()=>{this.jdbc=await request('/jvm/jdbc_pool_status');});},
            loadDump() {return this.action('dump',async()=>{this.dump=await request('/jvm/mbeans',{name:this.dumpName},true);});},
        },
        mounted() {if(this.page==='system') ['system','jvm','cpu','memory','disk'].forEach(key=>this.loadSection(key));else if(this.page==='jmx'||this.page==='tomcat'){this.action('local',()=>this.loadLocal());if(location.pathname.includes('JmxMBeanBrowser'))this.loadDump();}}
    });
    app.use(ViewUIPlus);app.mount(root);
})();
