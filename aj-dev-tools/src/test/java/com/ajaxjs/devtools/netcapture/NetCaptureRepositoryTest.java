package com.ajaxjs.devtools.netcapture;

import com.ajaxjs.devtools.netcapture.model.*;
import com.ajaxjs.devtools.netcapture.repository.NetCaptureRepository;
import com.ajaxjs.devtools.netcapture.service.PacketQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class NetCaptureRepositoryTest {
    private EmbeddedDatabase database;
    private NetCaptureRepository repository;

    @BeforeEach void setup() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("netcapture/schema.sql").build();
        repository = new NetCaptureRepository(database);
    }

    @org.junit.jupiter.api.AfterEach void close() { database.shutdown(); }

    private PacketInfo packet(String ip, String protocol, LocalDateTime time) {
        PacketInfo packet = new PacketInfo();
        packet.setCaptureTime(time);
        packet.setSourceIp(ip);
        packet.setDestinationIp("10.0.0.2");
        packet.setSourcePort(1234);
        packet.setDestinationPort(80);
        packet.setProtocol(protocol);
        packet.setPacketLength(100);
        packet.setHttpBody("<body>'quoted'</body>");
        repository.insertPacket(packet);
        return packet;
    }

    @Test void insertDetailFiltersAndDtoPagination() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PacketInfo first = packet("10.0.0.1", "TCP", now);
        packet("10.0.0.3", "UDP", now.minusMinutes(1));
        assertNotNull(first.getId());
        assertEquals(now, repository.getPacketDetail(first.getId()).getCaptureTime());
        assertEquals(first.getHttpBody(), repository.getPacketDetail(first.getId()).getHttpBody());
        var page = new PacketQueryService(repository).queryPackets("TCP", "0.0.1", null, 1234, 80,
                now.minusSeconds(1), now.plusSeconds(1), 1, 10);
        assertEquals(1, page.getTotalCount());
        assertEquals(first.getId(), page.getList().get(0).getId());
        assertEquals(2, repository.getRecentPackets(10).size());
        assertTrue(repository.queryPackets(null, "' OR 1=1 --", null, null, null, null, null, 1, 10).isZero());
        assertNull(repository.getPacketDetail(999L));
        assertThrows(IllegalArgumentException.class, () -> repository.getRecentPackets(-1));
    }

    @Test void aggregatesStatisticsAndRetention() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        packet("10.0.0.1", "TCP", now);
        packet("10.0.0.1", "TCP", now);
        PacketInfo old = packet("old", "UDP", now.minusDays(2));
        assertEquals(2L, ((Number) repository.getProtocolStatistics(now.minusHours(1)).get(0).get("count")).longValue());
        assertEquals("10.0.0.1", repository.getTopSourceIps(now.minusHours(1)).get(0).get("source_ip"));
        TrafficStatistics stats = new TrafficStatistics();
        stats.setStatisticsTime(now);
        stats.setTimeWindow("1min");
        stats.setTotalPackets(2L);
        stats.setTotalBytes(200L);
        repository.insertStatistics(stats);
        assertNotNull(stats.getId());
        assertEquals(2L, repository.findRecentStatistics(now.minusHours(1), 10).get(0).getTotalPackets());
        assertEquals(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                repository.getTrafficTrend(now.minusHours(1)).get(0).get("timeWindow"));
        repository.cleanupBefore(now.minusDays(1));
        assertNull(repository.getPacketDetail(old.getId()));
        assertEquals(2, repository.getRecentPackets(10).size());
    }

    @Test void joinsSpringTransactionWithoutCommittingIt() {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.execute(status -> {
            packet("rollback", "TCP", LocalDateTime.now());
            assertEquals(1, repository.getRecentPackets(10).size());
            status.setRollbackOnly();
            return null;
        });
        assertTrue(repository.getRecentPackets(10).isEmpty());
    }
}
