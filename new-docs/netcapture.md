# Spring Boot 网络流量抓包分析系统：完整指南

本文整合本目录下的三份原始文档，合并重复内容，保留功能、安装、使用、API、架构、实现示例与数据库结构。原文档保持不变。

参考文章：[Spring Boot 网络流量抓包分析系统](https://juejin.cn/post/7537182902523969578)。版本、目录、命令和凭据沿用原文示例，以实际项目配置为准。代码片段用于说明设计，并非可独立编译的完整源码。

## 1. 背景与功能

网络故障排查、性能监控和安全审计需要及时且可关联业务的数据。传统工具如 Wireshark 功能强大，但操作、部署和维护较复杂，难以直接集成业务系统，应用层实时监控和业务关联分析也存在不足。

本系统以 Spring Boot + Pcap4j 构建轻量、模块化、易于部署扩展的网络监控服务。

- **多协议支持**: 支持 HTTP、TCP、UDP、DNS、DHCP 等多种网络协议解析
- **实时监控**: WebSocket 实时数据推送，支持在线流量监控
- **数据分析**: 提供流量统计、协议分析、IP排名等多维度数据分析
- **智能过滤**: 支持 BPF 过滤规则，可按协议、IP、端口等条件筛选
- **前后端分离**: 纯 HTML + 原生 JavaScript 实现，无需复杂的模板引擎
- **数据持久化**: 使用 H2 数据库存储抓包数据和统计信息
- **RESTful API**: 完整的 REST API 接口支持

前端支持多种屏幕尺寸；系统采用异步数据处理，统计字段还包含 ICMP 包数量。

![image.png](https://p6-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/45ad3a401d7e4910887f3323455b7603~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1794262629&x-signature=nDx%2FrDn9rlCcDuFF%2FEdJAsVWaO4%3D)

## 2. 技术栈与架构

- **Spring Boot 3.2**: 主框架
- **Pcap4j 1.8.2**: 网络抓包库
- **MyBatis Plus 3.5.5**: 数据访问框架
- **H2 Database**: 内存数据库
- **WebSocket**: 实时通信
- **HTML5 + 原生 JavaScript**: 前端实现
- **Bootstrap 5**: 前端UI框架

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web 前端      │◄──►│  Spring Boot     │◄──►│   Pcap4j       │
│  (Bootstrap)    │    │   应用服务       │    │  抓包引擎       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   WebSocket     │    │  REST API        │    │  网络接口       │
│   实时推送       │    │  数据服务        │    │  (eth0/wlan0)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                     ┌──────────────────┐
                     │   H2 Database    │
                     │   数据存储        │
                     └──────────────────┘
```

### 项目结构

```
src/main/java/com/example/netcapture/
├── config/          # 配置类
├── controller/      # Web控制器  
├── dto/            # 数据传输对象
├── entity/         # 数据库实体
├── repository/     # 数据访问层
├── service/        # 业务逻辑层
└── utils/          # 工具类

src/main/resources/
├── static/         # 静态资源
│   ├── css/        # 样式文件
│   ├── js/         # JavaScript文件
│   └── *.html      # HTML页面
└── application.yml # 应用配置
```

## 3. 环境准备

- JDK 17 或更高版本。
- Maven 3.6 或更高版本。
- Windows、Linux 或 macOS，具备访问网络接口所需的权限。

### Windows：Npcap 安装

原项目说明提到 WinPcap 或 Npcap，专项安装指南采用 Npcap，以下统一按 Npcap 安装。

- 访问官网：https://npcap.com/
- 点击 "Download" 下载最新版本
- 选择 "Npcap x.xx installer for Windows" 

### 安装 Npcap
1. **以管理员身份运行**安装程序
2. 在安装选项中按以下说明选择：
   - ✅ "Install Npcap in WinPcap API-compatible mode"
   - ✅ "Support raw 802.11 traffic"（可选）
3. 点击 "Install" 完成安装
4. 重启计算机（推荐）

以管理员身份启动命令行或 IDE，再按快速开始运行应用。检查控制台日志，确认出现“WinPcap/Npcap 本地库加载成功”和“找到 X 个网络接口”，并访问主页面验证功能。验证失败时按故障排查章节检查或重新安装 Npcap。

### Linux

```bash
# Ubuntu/Debian
sudo apt-get install libpcap-dev
# CentOS/RHEL
sudo yum install libpcap-devel
```

可通过 sudo 启动应用，或按以下示例为 Java 配置抓包权限。路径应替换为实际 Java 可执行文件路径，该配置会影响使用该 Java 的进程，应按部署环境选择。

```bash
sudo setcap cap_net_raw,cap_net_admin=eip /usr/bin/java
```

### macOS

使用 Homebrew 安装 libpcap，并以具备抓包权限的账户启动应用：

```bash
brew install libpcap
```

## 4. 快速开始

以下仓库地址、目录和 JAR 名称为原文示例，需要按实际项目调整。Windows 请在管理员终端执行 Maven/Java 命令，无需 sudo。

### 获取项目

```bash
git clone <repository-url>
cd springboot-net-capture
```

### 编译项目

```bash
mvn clean compile
```

### 启动应用

```bash
# 使用管理员权限启动
sudo mvn spring-boot:run

# 或者编译后运行 JAR
mvn clean package
sudo java -jar target/springboot-net-capture-1.0.0.jar
```

### 访问系统

- **主界面**: http://localhost:8080
- **数据包列表**: http://localhost:8080/packets.html
- **统计分析**: http://localhost:8080/statistics.html
- **实时监控**: http://localhost:8080/realtime.html
- **数据库控制台**: http://localhost:8080/h2-console

数据库连接信息:
- **URL**: `jdbc:h2:mem:netcapture`
- **用户名**: `sa`
- **密码**: `password`

## 5. 使用指南

### 开始抓包

1. 访问主页面 http://localhost:8080
2. 选择网络接口（可选，默认自动选择）
3. 设置过滤规则（可选，BPF 格式）
4. 点击"开始抓包"按钮

### 查看数据包

- 访问数据包页面查看抓包数据
- 支持按协议、IP、端口等条件过滤
- 点击数据包行查看详细信息

### 统计分析

- 访问统计分析页面查看流量统计
- 包含协议分布图、流量趋势图
- Top IP 地址排名
- 历史统计数据

### 实时监控

- 访问实时监控页面进行实时监控
- WebSocket 实时数据流
- 可设置实时过滤条件
- 实时统计信息展示

## 6. API

### 抓包控制 API

#### 获取网络接口列表
```http
GET /api/capture/interfaces
```

#### 获取抓包状态
```http
GET /api/capture/status
```

#### 控制抓包
```http
POST /api/capture/control
Content-Type: application/json

{
  "action": "start",
  "networkInterface": "eth0",
  "filter": "tcp port 80"
}
```

### 数据包查询 API

#### 查询数据包
```http
GET /api/packets?protocol=HTTP&sourceIp=192.168.1.1&pageNo=1&pageSize=20
```

#### 获取数据包详情
```http
GET /api/packets/{id}
```

### 统计分析 API

#### 获取当前统计
```http
GET /api/statistics/current
```

#### 获取协议统计
```http
GET /api/statistics/protocols
```

#### 获取流量趋势
```http
GET /api/statistics/trend?hours=24
```

## 7. 配置与性能调优

主要配置位于 `src/main/resources/application.yml`:

```yaml
network:
  capture:
    # 默认网络接口（为空时自动选择）
    interface: ""
    # 抓包过滤器（BPF格式）
    filter: ""
    # 抓包缓冲区大小（字节）
    buffer-size: 65536
    # 抓包超时时间（毫秒）
    timeout: 1000
    # 是否启用混杂模式
    promiscuous: false
    # 最大抓包数量（0表示无限制）
    max-packets: 0
    # 是否自动启动抓包
    auto-start: false
    # 数据保留时间（小时）
    data-retention-hours: 24
```

### 性能调优

根据流量调整缓冲区，使用非混杂模式及 BPF 规则减少处理量，例如 tcp port 80 or tcp port 443；合理设置最大抓包数和数据保留时间，配合异步处理控制资源占用。

数据库连接池示例：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
```

### 异步处理线程池

```
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Bean(name = "captureTaskExecutor")
    public ThreadPoolTaskExecutor captureTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("CaptureTask-");
        return executor;
    }
}
```

## 8. 核心实现

### 抓包引擎设计

#### 网卡选择与权限管理

```
@Service
public class PacketCaptureService {
    
    public List<PcapNetworkInterface> getAvailableNetworkInterfaces() throws PcapNativeException {
        return Pcaps.findAllDevs();
    }
    
    private PcapNetworkInterface selectNetworkInterface(String interfaceName) throws PcapNativeException {
        List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
        
        // 自动选择最佳网络接口
        return allDevs.stream()
            .filter(nif -> {
                try {
                    return nif.getAddresses().stream()
                        .anyMatch(addr -> {
                            InetAddress inetAddr = addr.getAddress();
                            return inetAddr != null && 
                                   !inetAddr.isLoopbackAddress() && 
                                   !inetAddr.isLinkLocalAddress();
                        });
                } catch (Exception e) {
                    return false;
                }
            })
            .findFirst()
            .orElse(allDevs.get(0));
    }
}
```

#### 多协议解析器实现

系统支持多种网络协议的智能解析，自动识别 HTTP、TCP、UDP 等协议类型：

```
@Service
public class ProtocolAnalyzer {
    
    private static final Pattern HTTP_REQUEST_PATTERN = Pattern.compile(
        "^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH)\s+(\S+)\s+HTTP/([0-9\.]+)"
    );
    
    public void analyzeHttpPacket(PacketInfo packetInfo) {
        if (packetInfo.getPayload() == null) return;
        
        String payload = packetInfo.getPayload();
        
        if (isHttpRequest(payload)) {
            parseHttpRequest(packetInfo, payload);
        } else if (isHttpResponse(payload)) {
            parseHttpResponse(packetInfo, payload);
        }
    }
}
```

### 数据处理层

#### 实时统计分析

使用原子操作和并发集合实现高性能的实时统计：

```
@Service
public class TrafficStatisticsService {
    
    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final Map<String, AtomicLong> sourceIpCounts = new ConcurrentHashMap<>();
    
    public void updateStatistics(PacketInfo packetInfo) {
        totalPackets.incrementAndGet();
        totalBytes.addAndGet(packetInfo.getPacketLength());
        
        // 更新IP统计
        if (packetInfo.getSourceIp() != null) {
            sourceIpCounts.computeIfAbsent(packetInfo.getSourceIp(), 
                k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    @Scheduled(fixedRate = 60000) // 每分钟生成统计报告
    public void generateStatistics() {
        TrafficStatistics statistics = new TrafficStatistics();
        statistics.setTotalPackets(totalPackets.get());
        statistics.setTotalBytes(totalBytes.get());
        // ... 保存统计数据
    }
}
```

### API 服务层

#### WebSocket 实时推送

实现 WebSocket 服务，支持实时数据推送和客户端过滤：

```
@Service
public class PacketWebSocketHandler extends TextWebSocketHandler {
    
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, String> sessionFilters = new ConcurrentHashMap<>();
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        
        if (payload.startsWith("filter:")) {
            String filter = payload.substring(7).trim();
            sessionFilters.put(session.getId(), filter);
        }
    }
    
    public void broadcastPacket(PacketInfo packetInfo) {
        if (sessions.isEmpty()) return;
        
        String packetJson = objectMapper.writeValueAsString(packetInfo);
        TextMessage message = new TextMessage(packetJson);
        
        sessions.forEach(session -> {
            if (session.isOpen() && shouldSendToSession(session, packetInfo)) {
                session.sendMessage(message);
            }
        });
    }
}
```

### 前端实时展示

WebSocket 端点为 `/ws/packets`。客户端发送 `filter:` 前缀消息设置会话过滤条件，服务端按会话筛选推送；会话过滤与抓包阶段的 BPF 过滤属于不同处理阶段。

使用 WebSocket 实现实时数据展示，支持动态过滤和实时统计：

```
function connectWebSocket() {
    const wsUrl = `ws://${window.location.host}/ws/packets`;
    websocket = new WebSocket(wsUrl);
    
    websocket.onmessage = function(event) {
        const packet = JSON.parse(event.data);
        handlePacketData(packet);
        updateRealtimeStats();
        addToPacketLog(packet);
    };
}

function applyFilter() {
    const protocol = document.getElementById('protocolFilter').value;
    const ip = document.getElementById('ipFilter').value;
    const url = document.getElementById('urlFilter').value;
    
    let filterString = `${protocol} ${ip} ${url}`.trim();
    websocket.send('filter:' + filterString);
}
```

## 9. 数据库设计

以下为原文 H2 表结构及索引示例。默认数据源是内存数据库 `jdbc:h2:mem:netcapture`，写入数据库不代表重启进程后数据仍保留；长期保存需要调整实际数据源配置。

```
-- 数据包信息表
CREATE TABLE IF NOT EXISTS packet_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    capture_time TIMESTAMP NOT NULL,
    source_ip VARCHAR(45),
    destination_ip VARCHAR(45),
    source_port INTEGER,
    destination_port INTEGER,
    protocol VARCHAR(20),
    packet_length INTEGER,
    payload CLOB,
    http_method VARCHAR(10),
    http_url VARCHAR(500),
    http_headers CLOB,
    http_body CLOB,
    http_status INTEGER,
    tcp_seq_number BIGINT,
    tcp_ack_number BIGINT,
    tcp_flags VARCHAR(1000),
    network_interface VARCHAR(100)
);

-- 创建索引（H2数据库语法）
CREATE INDEX IF NOT EXISTS idx_capture_time ON packet_info(capture_time);
CREATE INDEX IF NOT EXISTS idx_protocol ON packet_info(protocol);
CREATE INDEX IF NOT EXISTS idx_source_ip ON packet_info(source_ip);
CREATE INDEX IF NOT EXISTS idx_destination_ip ON packet_info(destination_ip);

-- 流量统计表
CREATE TABLE IF NOT EXISTS traffic_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    statistics_time TIMESTAMP NOT NULL,
    time_window VARCHAR(10),
    total_packets BIGINT DEFAULT 0,
    total_bytes BIGINT DEFAULT 0,
    http_packets BIGINT DEFAULT 0,
    tcp_packets BIGINT DEFAULT 0,
    udp_packets BIGINT DEFAULT 0,
    icmp_packets BIGINT DEFAULT 0,
    top_source_ip VARCHAR(45),
    top_destination_ip VARCHAR(45),
    top_source_port INTEGER,
    top_destination_port INTEGER,
    average_packet_size DOUBLE DEFAULT 0
);

-- 创建索引（H2数据库语法）
CREATE INDEX IF NOT EXISTS idx_statistics_time ON traffic_statistics(statistics_time);
CREATE INDEX IF NOT EXISTS idx_time_window ON traffic_statistics(time_window);
```

## 10. 应用场景

说明：端口 443 过滤可以捕获 TLS 流量，但普通抓包无法直接读取加密后的 HTTP URL、状态码或正文。以下 API 监控代码只展示调用次数和错误次数聚合，响应时间分析还需请求响应关联逻辑。

### API 接口监控

**场景描述**：监控系统对外部 API 的调用情况，分析响应时间和错误率。

**实现方式**：

1.  设置 HTTP 协议过滤器：`tcp port 80 or tcp port 443`
2.  解析 HTTP 请求/响应，提取 URL、状态码、响应时间
3.  生成 API 调用报告和性能分析图表

```
// API 监控控制器
@GetMapping("/api/monitor/apis")
public ResponseEntity<List<ApiCallStats>> getApiCallStatistics(
        @RequestParam(defaultValue = "24") int hours) {
    
    List<PacketInfo> httpPackets = packetQueryService.queryHttpPackets(hours);
    Map<String, ApiCallStats> apiStats = new HashMap<>();
    
    httpPackets.forEach(packet -> {
        String url = packet.getHttpUrl();
        if (url != null) {
            ApiCallStats stats = apiStats.computeIfAbsent(url, k -> new ApiCallStats(url));
            stats.incrementCallCount();
            if (packet.getHttpStatus() != null && packet.getHttpStatus() >= 400) {
                stats.incrementErrorCount();
            }
        }
    });
    
    return ResponseEntity.ok(new ArrayList<>(apiStats.values()));
}
```

### 网络故障排查

**场景描述**：当应用出现网络连接问题时，快速定位故障原因。

**排查流程**：

1.  启动实时监控，观察网络流量模式
2.  应用协议和 IP 地址过滤器，缩小问题范围
3.  分析 TCP 连接状态和错误包
4.  生成故障诊断报告

### 安全审计

**场景描述**：检测异常网络访问行为，识别潜在安全威胁。

**实现策略**：

```
@Component
public class SecurityAuditService {
    
    @EventListener
    public void handlePacketCaptured(PacketCaptureEvent event) {
        PacketInfo packet = event.getPacketInfo();
        
        // 检测异常端口访问
        if (isUnusualPortAccess(packet)) {
            alertService.sendSecurityAlert("检测到异常端口访问", packet);
        }
        
        // 检测大量连接
        if (isConnectionFlooding(packet)) {
            alertService.sendSecurityAlert("检测到连接洪水攻击", packet);
        }
    }
}
```

## 11. 安全与故障排查

### 安全注意事项

网络抓包应在授权且符合法律法规的环境中进行，使用适当的管理员权限。数据包可能包含敏感信息，应妥善保管。大量抓包可能影响性能，应合理设置过滤规则及数据保留时间。数据库控制台凭据应按实际部署环境配置。

### 常见问题

| 现象 | 排查与处理 |
| --- | --- |
| Native library not found | 检查本地库是否安装完整。Windows 检查或重新安装 Npcap，确认启用 WinPcap API 兼容模式；Linux/macOS 检查 libpcap。 |
| Permission denied、Access denied 或无法启动抓包 | 先检查抓包权限，以管理员身份启动，或按 Linux 权限配置处理。如有防火墙拦截证据，检查 Java 放行规则。原 Windows 指南还提出临时关闭防火墙测试，定位后应恢复。 |
| No network interfaces found | 检查 Npcap/libpcap、本地库加载日志、应用权限及网络接口工作状态，不应只认定为权限问题。 |
| WebSocket connection failed | 检查应用是否成功监听 8080、端口是否被其他程序占用、防火墙设置及浏览器 WebSocket 支持情况。 |