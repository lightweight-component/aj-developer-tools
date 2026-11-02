package com.ajaxjs.devtools.netcapture.repository;

import com.ajaxjs.devtools.netcapture.model.PacketInfo;
import com.ajaxjs.devtools.netcapture.model.TrafficStatistics;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.page.PageResult;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/**
 * SqlMan persistence; releases owned connections and joins Spring-bound transactions.
 */
@Repository
public class NetCaptureRepository {
    private final DataSource dataSource;

    public NetCaptureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private <T> T execute(Function<Connection, T> operation) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return operation.apply(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private <T> List<T> rows(String sql, Class<T> type, Object... args) {
        return execute(conn -> {
            List<Map<String, Object>> result = new Action(conn, sql).query(args).list();
            List<T> beans = new ArrayList<>();
            if (result != null)
                for (Map<String, Object> row : result) beans.add(bean(row, type));
            return beans;
        });
    }

    /**
     * Adapt JDBC CLOB/time values before the connection is released.
     */
    private static <T> T bean(Map<String, Object> row, Class<T> type) {
        if (row == null)
            return null;

        try {
            T value = type.getDeclaredConstructor().newInstance();
            BeanWrapperImpl target = new BeanWrapperImpl(value);

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey().replace("_", "");
                for (java.beans.PropertyDescriptor property : target.getPropertyDescriptors()) {
                    if (property.getWriteMethod() == null || !property.getName().equalsIgnoreCase(key)) continue;
                    Object data = entry.getValue();
                    if (data instanceof java.sql.Clob clob) data = clob.getSubString(1, Math.toIntExact(clob.length()));
                    if (data instanceof Timestamp time && property.getPropertyType() == LocalDateTime.class)
                        data = time.toLocalDateTime();
                    target.setPropertyValue(property.getName(), data);
                }
            }

            return value;
        } catch (ReflectiveOperationException | java.sql.SQLException e) {
            throw new IllegalStateException("Cannot map capture record", e);
        }
    }

    private List<Map<String, Object>> maps(String sql, Object... args) {
        return execute(conn -> {
            List<Map<String, Object>> result = new Action(conn, sql).query(args).list();

            return result == null ? Collections.emptyList() : result;
        });
    }

    public PageResult<PacketInfo> queryPackets(String protocol, String sourceIp, String destinationIp,
                                               Integer sourcePort, Integer destinationPort, LocalDateTime start, LocalDateTime end,
                                               int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 500)
            throw new IllegalArgumentException("pageNo must be positive; pageSize must be 1..500");

        StringBuilder sql = new StringBuilder("SELECT * FROM packet_info WHERE 1=1");
        List<Object> args = new ArrayList<>();
        condition(sql, args, "protocol = ?", text(protocol));
        condition(sql, args, "source_ip LIKE ?", like(sourceIp));
        condition(sql, args, "destination_ip LIKE ?", like(destinationIp));
        condition(sql, args, "source_port = ?", sourcePort);
        condition(sql, args, "destination_port = ?", destinationPort);
        condition(sql, args, "capture_time >= ?", timestamp(start));
        condition(sql, args, "capture_time <= ?", timestamp(end));
        sql.append(" ORDER BY capture_time DESC, id DESC");

        return execute(conn -> {
            PageResult<Map<String, Object>> raw = new Action(conn, sql.toString()).query(args.toArray())
                    .pageByPageNo(pageNo, pageSize);
            PageResult<PacketInfo> page = new PageResult<>();
            page.setStart(raw.getStart());
            page.setPageSize(raw.getPageSize());
            page.setTotalCount(raw.getTotalCount());
            page.setTotalPage(raw.getTotalPage());
            page.setCurrentPage(raw.getCurrentPage());
            page.setZero(raw.isZero());
            List<PacketInfo> rows = new ArrayList<>();

            if (raw.getList() != null)
                for (Map<String, Object> row : raw.getList()) rows.add(bean(row, PacketInfo.class));

            page.setList(rows);

            return page;
        });
    }

    private static void condition(StringBuilder sql, List<Object> args, String clause, Object value) {
        if (value != null) {
            sql.append(" AND ").append(clause);
            args.add(value);
        }
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String like(String value) {
        return text(value) == null ? null : "%" + value + "%";
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static void limit(int value) {
        if (value < 1 || value > 10000)
            throw new IllegalArgumentException("limit must be 1..10000");
    }

    public List<PacketInfo> getRecentPackets(int count) {
        limit(count);
        return rows("SELECT * FROM packet_info ORDER BY capture_time DESC, id DESC LIMIT ?", PacketInfo.class, count);
    }

    public PacketInfo getPacketDetail(Long id) {
        if (id == null || id < 1)
            throw new IllegalArgumentException("id must be positive");
        return execute(conn -> bean(new Action(conn, "SELECT * FROM packet_info WHERE id=?").query(id).one(), PacketInfo.class));
    }

    public void insertPacket(PacketInfo packet) {
        packet.setId(insert("packet_info", packet,
                "capture_time,source_ip,destination_ip,source_port,destination_port,protocol,packet_length,payload,http_method,http_url,http_headers,http_body,http_status,tcp_seq_number,tcp_ack_number,tcp_flags,network_interface"));
    }

    public void insertStatistics(TrafficStatistics statistics) {
        statistics.setId(insert("traffic_statistics", statistics,
                "statistics_time,time_window,total_packets,total_bytes,http_packets,tcp_packets,udp_packets,icmp_packets,top_source_ip,top_destination_ip,top_source_port,top_destination_port,average_packet_size"));
    }

    /**
     * Table and columns are internal constants; all data values remain bound parameters.
     */
    private Long insert(String table, Object bean, String columns) {
        Map<String, Object> values = new LinkedHashMap<>();
        BeanWrapperImpl properties = new BeanWrapperImpl(bean);
        for (String column : columns.split(",")) {
            StringBuilder property = new StringBuilder();
            boolean uppercase = false;
            for (char c : column.toCharArray()) {
                if (c == '_') uppercase = true;
                else {
                    property.append(uppercase ? Character.toUpperCase(c) : c);
                    uppercase = false;
                }
            }
            Object value = properties.getPropertyValue(property.toString());
            values.put(column, value instanceof LocalDateTime ? timestamp((LocalDateTime) value) : value);
        }
        return execute(conn -> new Action(conn, values, table).create().execute(true, Long.class).getNewlyId());
    }

    /**
     * Both retention deletes share a connection and commit or roll back together.
     */
    public void cleanupBefore(LocalDateTime cutoff) {
        new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)).execute(status ->
                execute(conn -> {
                    new Action(conn, "DELETE FROM packet_info WHERE capture_time < ?").update(timestamp(cutoff)).execute();
                    new Action(conn, "DELETE FROM traffic_statistics WHERE statistics_time < ?").update(timestamp(cutoff)).execute();
                    return null;
                }));
    }

    public List<TrafficStatistics> findRecentStatistics(LocalDateTime start, int count) {
        limit(count);
        return rows("SELECT * FROM traffic_statistics WHERE statistics_time>=? ORDER BY statistics_time DESC, id DESC LIMIT ?",
                TrafficStatistics.class, timestamp(start), count);
    }

    public List<Map<String, Object>> getTrafficTrend(LocalDateTime start) {
        // Format in Java, avoiding the original H2-only FORMATDATETIME function.
        List<Map<String, Object>> result = new ArrayList<>();
        for (TrafficStatistics row : findRecentStatistics(start, 20)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("timeWindow", row.getStatisticsTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            value.put("totalPackets", row.getTotalPackets());
            value.put("totalBytes", row.getTotalBytes());
            result.add(value);
        }
        return result;
    }

    public List<Map<String, Object>> getProtocolStatistics(LocalDateTime start) {
        return aggregate("protocol", "protocol", start, false);
    }

    public List<Map<String, Object>> getTopSourceIps(LocalDateTime start) {
        return aggregate("source_ip", "source_ip", start, true);
    }

    public List<Map<String, Object>> getTopDestinationIps(LocalDateTime start) {
        return aggregate("destination_ip", "destination_ip", start, true);
    }

    private List<Map<String, Object>> aggregate(String column, String key, LocalDateTime start, boolean top) {
        List<Map<String, Object>> values = maps("SELECT " + column + " AS item_value, COUNT(*) AS amount FROM packet_info "
                + "WHERE capture_time>=? AND " + column + " IS NOT NULL GROUP BY " + column
                + " ORDER BY amount DESC" + (top ? " LIMIT 10" : ""), timestamp(start));
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(key, column(value, "itemValue"));
            row.put("count", column(value, "amount"));
            result.add(row);
        }

        return result;
    }

    private static Object column(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet())
            if (entry.getKey().replace("_", "").equalsIgnoreCase(key))
                return entry.getValue();

        return null;
    }
}
