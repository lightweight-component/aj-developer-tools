/**
 * 网络抓包、报文查询及流量统计。
 *
 * <p>持久化由 {@link com.ajaxjs.devtools.netcapture.repository.NetCaptureRepository}
 * 使用 SqlMan 完成，不再需要 MyBatis-Plus Mapper 或 XML 映射文件。
 * 启用 {@code devtools.netcapture.enable=true} 时，宿主应用需要提供 JDBC DataSource，
 * 并预先创建 packet_info、traffic_statistics 表，沿用原抓包工程的字段结构。</p>
 *
 * <p>Repository 使用 Spring DataSourceUtils 获取和释放连接，可加入宿主的 Spring JDBC
 * 事务；异步分析和定时统计也会各自获取、释放连接。历史数据清理中的两次删除在同一事务内执行。
 * 筛选值通过 SQL 参数绑定，分页及最近记录查询对数量作了限制。</p>
 *
 * <p>报文分页返回 SqlMan PageResult，记录位于 list，页码为 currentPage，总数为 totalCount；
 * 调用方应使用这些字段读取结果。趋势图的时间标签在 Java 中格式化。</p>
 */
package com.ajaxjs.devtools.netcapture;
