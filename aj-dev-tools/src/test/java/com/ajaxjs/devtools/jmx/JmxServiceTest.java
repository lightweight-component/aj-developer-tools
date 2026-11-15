package com.ajaxjs.devtools.jmx;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;
import java.lang.management.ManagementFactory;
import com.ajaxjs.devtools.jmx.model.BeanInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JmxServiceTest {
    @Test
    void overviewUsesOneLockedConnectionForAllQueries() throws Exception {
        JmxService service = new JmxService() {
            @Override
            public BeanInfo getProperties(String fullName) {
                assertTrue(Thread.holdsLock(this), "overview must hold the connection lock across queries");
                return super.getProperties(fullName);
            }
        };
        JMXConnector connector = mock(JMXConnector.class);
        when(connector.getMBeanServerConnection()).thenReturn(ManagementFactory.getPlatformMBeanServer());
        service.replaceConnection(connector);

        try {
            assertNotNull(new MonitorDashboardService(service).overview().getHeapMemoryUsage());
        } finally {
            service.destroy();
        }
    }

    @Test
    void replacementWaitsUntilSamplingCompletes() throws Exception {
        JmxService service = new JmxService();
        JMXConnector first = mock(JMXConnector.class), second = mock(JMXConnector.class);
        when(first.getMBeanServerConnection()).thenReturn(mock(MBeanServerConnection.class));
        when(second.getMBeanServerConnection()).thenReturn(mock(MBeanServerConnection.class));
        service.replaceConnection(first);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch sampling = new CountDownLatch(1), release = new CountDownLatch(1), replacing = new CountDownLatch(1);

        try {
            Future<Integer> sample = executor.submit(() -> service.withConnection(() -> {
                sampling.countDown();

                try {
                    if (!release.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("sampling timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }

                return 42;
            }));
            assertTrue(sampling.await(5, TimeUnit.SECONDS));
            Future<?> replacement = executor.submit(() -> {
                replacing.countDown();
                service.replaceConnection(second);
                return null;
            });
            assertTrue(replacing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> replacement.get(100, TimeUnit.MILLISECONDS));
            verify(first, never()).close();
            release.countDown();
            assertEquals(42, sample.get(5, TimeUnit.SECONDS));
            replacement.get(5, TimeUnit.SECONDS);
            verify(first).close();
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            service.destroy();
        }
    }

    @Test
    void createsBeansWithCircularReferencesDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.register(JmxService.class, MonitorDashboardService.class, JvmMonitorController.class);
            context.refresh();

            assertNotNull(context.getBean(JvmMonitorController.class));
            assertNotNull(context.getBean(MonitorDashboardService.class));
        }
    }

    @Test
    void rejectsQueriesBeforeConnecting() {
        JmxService service = new JmxService();

        assertThrows(IllegalStateException.class, service::getDomains);
        assertThrows(IllegalStateException.class, () -> service.getProperties("java.lang:type=Memory"));
        assertThrows(IllegalStateException.class, () -> service.invokeMethod("java.lang:type=Memory", "gc"));
    }

    @Test
    void initializesSessionImmediatelyAndSwitchesQueriesToNewConnection() throws Exception {
        JmxService service = new JmxService();
        JMXConnector first = mock(JMXConnector.class), second = mock(JMXConnector.class);
        MBeanServerConnection firstSession = mock(MBeanServerConnection.class), secondSession = mock(MBeanServerConnection.class);
        ObjectName collector = new ObjectName("java.lang:name=Test,type=GarbageCollector");
        ObjectName pattern = new ObjectName("java.lang:name=*,type=GarbageCollector");
        when(first.getMBeanServerConnection()).thenReturn(firstSession);
        when(second.getMBeanServerConnection()).thenReturn(secondSession);
        when(firstSession.getDomains()).thenReturn(new String[0]);
        when(secondSession.getDomains()).thenReturn(new String[0]);
        when(secondSession.queryNames(pattern, null)).thenReturn(Collections.singleton(collector));

        try {
            service.replaceConnection(first);
            assertTrue(service.getDomains().isEmpty());
            service.replaceConnection(second);
            assertTrue(service.getDomains().isEmpty());
            assertEquals(Collections.singleton(collector), service.getGarbageCollectorObjectNames());
            service.invokeMethod("java.lang:type=Memory", "gc");

            verify(first).close();
            verify(firstSession, times(1)).getDomains();
            verify(secondSession).getDomains();
            verify(secondSession).invoke(new ObjectName("java.lang:type=Memory"), "gc", null, null);
        } finally {
            service.destroy();
        }

        verify(second).close();
        assertThrows(IllegalStateException.class, service::getDomains);
    }

    @Test
    void failedReplacementClosesCandidateAndRetainsWorkingConnection() throws Exception {
        JmxService service = new JmxService();
        JMXConnector working = mock(JMXConnector.class), failed = mock(JMXConnector.class);
        MBeanServerConnection session = mock(MBeanServerConnection.class);
        when(working.getMBeanServerConnection()).thenReturn(session);
        when(session.getDomains()).thenReturn(new String[0]);
        when(failed.getMBeanServerConnection()).thenThrow(new IOException("connection failed"));

        try {
            service.replaceConnection(working);
            assertThrows(IOException.class, () -> service.replaceConnection(failed));
            assertTrue(service.getDomains().isEmpty());

            verify(failed).close();
            verify(working, never()).close();
            verify(session).getDomains();
        } finally {
            service.destroy();
        }
    }

    @Test
    void controllerPropagatesConnectionFailureInsteadOfReturningTrue() throws Exception {
        JmxService service = mock(JmxService.class);
        JvmMonitorController controller = new JvmMonitorController(service, mock(MonitorDashboardService.class));
        doThrow(new IOException("connection failed")).when(service).attachRemoteJvm("localhost", "1234");

        assertThrows(IOException.class, () -> controller.attachRemoteJvm("localhost", "1234"));
    }
}
