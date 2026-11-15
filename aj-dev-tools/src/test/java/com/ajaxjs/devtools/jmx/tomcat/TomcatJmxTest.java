package com.ajaxjs.devtools.jmx.tomcat;

import com.ajaxjs.devtools.jmx.tomcat.model.TomcatInfo;
import com.ajaxjs.devtools.jmx.JmxTimeouts;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.management.*;
import javax.management.openmbean.*;
import javax.management.remote.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TomcatJmxTest {
    private static final String URL = "service:jmx:rmi:///jndi/rmi://localhost:12345/jmxrmi";

    @Test
    void discoversCatalinaNio2PoolAndReadsDistinctHeapAndNonHeapUsage() throws Exception {
        ObjectName pool = new ObjectName("Catalina:type=ThreadPool,name=\"https-jsse-nio2-8443\"");
        ObjectName other = new ObjectName("Catalina:type=ThreadPool,name=\"http-apr-8080\"");
        MBeanServerConnection connection = connection(new HashSet<>(Arrays.asList(pool, other)), pool, 200, 100, 80, 20);

        TomcatInfo info = read(connection, 8443);
        assertEquals(100, info.jvmInfo.getUsedMemory());
        assertEquals(200, info.jvmInfo.getCommitMemory());
        assertEquals(50, info.jvmInfo.getHeap());
        assertEquals(20, info.jvmInfo.getNonUsedMemory());
        assertEquals(25, info.jvmInfo.getNonHeap());
        assertEquals(7, info.threadPool.getCurrentThreadsBusy());
        assertEquals("1970-01-01T00:00:00Z", info.systemInfo.getStartTime());
        assertEquals("2天 01:00:00.000", info.systemInfo.getUptime());
        verify(connection).queryNames(new ObjectName("Catalina:type=Manager,*"), null);
        verify(connection, never()).getMBeanInfo(other);
    }

    @Test
    void supportsCustomDomainAndPoolNameAndZeroCommittedMemory() throws Exception {
        ObjectName pool = new ObjectName("Company:type=ThreadPool,name=worker");
        MBeanServerConnection connection = connection(Collections.singleton(pool), pool, 0, 0, 0, 0);

        TomcatInfo info = read(connection, null);
        assertEquals(0, info.jvmInfo.getHeap());
        assertEquals(0, info.jvmInfo.getNonHeap());
        verify(connection).queryNames(new ObjectName("Company:type=Manager,*"), null);
    }

    @Test
    void mapsCustomPoolToConnectorPortWithoutAssumingNamePrefix() throws Exception {
        ObjectName pool = new ObjectName("Company:type=ThreadPool,name=worker");
        ObjectName connector = new ObjectName("Company:type=Connector,port=9000");
        MBeanServerConnection connection = connection(new HashSet<>(Arrays.asList(pool, connector)), pool, 200, 100, 80, 20);
        when(connection.getAttribute(connector, "port")).thenReturn(9000);

        assertEquals(7, read(connection, 9000).threadPool.getCurrentThreadsBusy());
    }

    @Test
    void rejectsAmbiguousPoolsAndClosesConnectionOnFailure() throws Exception {
        ObjectName pool = new ObjectName("Catalina:type=ThreadPool,name=\"http-nio-8080\"");
        ObjectName other = new ObjectName("Catalina:type=ThreadPool,name=\"http-nio-8081\"");
        MBeanServerConnection connection = connection(new HashSet<>(Arrays.asList(pool, other)), pool, 200, 100, 80, 20);

        assertThrows(IllegalArgumentException.class, () -> read(connection, null));
    }

    @Test
    void reportsConnectFailureWithoutNullPointerException() {
        try (MockedStatic<JmxTimeouts> factory = mockStatic(JmxTimeouts.class)) {
            factory.when(() -> JmxTimeouts.connect(URL)).thenThrow(new IOException("refused"));
            UncheckedIOException error = assertThrows(UncheckedIOException.class, () -> new TomcatJmx().getInfo(URL));
            assertEquals("refused", error.getCause().getMessage());
        }
    }

    private static TomcatInfo read(MBeanServerConnection connection, Integer port) throws Exception {
        JMXConnector connector = mock(JMXConnector.class);
        when(connector.getMBeanServerConnection()).thenReturn(connection);
        TomcatJmx monitor = new TomcatJmx();

        try (MockedStatic<JmxTimeouts> factory = mockStatic(JmxTimeouts.class)) {
            factory.when(() -> JmxTimeouts.connect(URL)).thenReturn(connector);

            try {
                return monitor.getInfo(URL, port);
            } finally {
                verify(connector).close();
                assertNull(monitor.getMsc());
            }
        }
    }

    private static MBeanServerConnection connection(Set<ObjectName> names, ObjectName pool, long heapCommitted, long heapUsed,
                                                    long nonHeapCommitted, long nonHeapUsed) throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        when(connection.queryNames(any(ObjectName.class), isNull())).thenAnswer(call -> {
            ObjectName pattern = call.getArgument(0);
            return names.stream().filter(pattern::apply).collect(Collectors.toSet());
        });
        ObjectName memory = new ObjectName("java.lang:type=Memory"), runtime = new ObjectName("java.lang:type=Runtime");
        when(connection.getAttribute(memory, "HeapMemoryUsage")).thenReturn(memory(heapCommitted, heapUsed));
        when(connection.getAttribute(memory, "NonHeapMemoryUsage")).thenReturn(memory(nonHeapCommitted, nonHeapUsed));
        when(connection.getMBeanInfo(pool)).thenReturn(metadata("currentThreadsBusy"));
        when(connection.getAttribute(pool, "currentThreadsBusy")).thenReturn(7);
        when(connection.getMBeanInfo(runtime)).thenReturn(metadata("StartTime", "Uptime"));
        when(connection.getAttribute(runtime, "StartTime")).thenReturn(0L);
        when(connection.getAttribute(runtime, "Uptime")).thenReturn((2 * 24 + 1) * 3600000L);

        return connection;
    }

    private static MBeanInfo metadata(String... names) {
        MBeanAttributeInfo[] attributes = Arrays.stream(names)
                .map(name -> new MBeanAttributeInfo(name, "long", "", true, false, false)).toArray(MBeanAttributeInfo[]::new);
        return new MBeanInfo("Test", "", attributes, null, null, null);
    }

    private static CompositeData memory(long committed, long used) throws OpenDataException {
        String[] names = {"init", "used", "committed", "max"};
        CompositeType type = new CompositeType("MemoryUsage", "MemoryUsage", names, names,
                new OpenType<?>[]{SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG});
        return new CompositeDataSupport(type, names, new Object[]{0L, used, committed, 1000L});
    }
}
