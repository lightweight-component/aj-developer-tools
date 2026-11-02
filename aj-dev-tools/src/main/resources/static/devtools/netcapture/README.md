# NetCapture 前端

Vue 3 CDN 版本，无需 npm 安装或打包。由 Spring Boot 静态资源服务提供，访问：

`{应用上下文}/devtools/netcapture/index.html`

- `index.html`：网卡选择、BPF 过滤、启停抓包、当前窗口统计。
- `packets.html`：协议、IP、端口、时间筛选，分页及完整报文详情。
- `statistics.html`：协议分布、流量趋势、Top IP、历史统计，30 秒刷新。
- `realtime.html`：WebSocket 数据流、关键词过滤、实时日志及最近数据包。

四个入口共用 `app.js`（Vue 组件、状态及请求）和 `app.css`。Vue 固定为 3.5.22，Chart.js 固定为 4.4.8，通过 jsDelivr 加载，需要浏览器可以访问该 CDN。

接口使用当前同源应用的 `/api/capture`、`/api/packets`、`/api/statistics`，实时连接使用 `/ws/packets`，自动保留 `/cms` 等应用上下文。请通过 HTTP(S) 打开页面，不使用 file 协议。

需启用后端 `devtools.netcapture.enable=true`，配置数据源、数据库表和服务器的 Npcap/libpcap。启动抓包要求选择网卡，避免后端自动选择接口时忽略用户输入的 BPF 规则。

分页读取 SqlMan 的 `list`、`currentPage`、`totalCount`、`totalPage`。HTTP 请求 15 秒超时；仪表盘每 5 秒刷新；轮询不重叠。统计图展示最新 20 个历史统计点，时间从左向右排列。

实时过滤使用后端的 `filter:关键词` 协议，匹配协议、源/目标 IP 或 HTTP URL，多个条件组合不是当前后端支持的语法。日志最多保存 500 条，最近报文最多 50 条，页面计数指过滤后的接收数据。意外断线 5 秒后重连，手动断开和离开页面会停止重连。报文详情以文本展示，不执行报文中的 HTML。
