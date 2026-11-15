package com.ajaxjs.devtools.jmx;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JmxServiceTest {
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
