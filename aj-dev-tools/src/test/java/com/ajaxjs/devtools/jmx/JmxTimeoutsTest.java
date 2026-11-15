package com.ajaxjs.devtools.jmx;

import org.junit.jupiter.api.Test;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JmxTimeoutsTest {
    @Test
    void closesConnectorThatArrivesAfterConnectTimeout() throws Exception {
        JMXConnector late = mock(JMXConnector.class);
        CountDownLatch release = new CountDownLatch(1);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(SocketTimeoutException.class, () -> JmxTimeouts.connect(() -> {
                        awaitIgnoringInterrupt(release);
                        return late;
                    }, 200, 200)));
        } finally {
            release.countDown();
        }

        verify(late, timeout(3000)).close();
    }

    @Test
    void queryTimeoutInvalidatesSessionAndReleasesServiceLock() throws Exception {
        JMXConnector raw = mock(JMXConnector.class);
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        CountDownLatch release = new CountDownLatch(1);
        when(raw.getMBeanServerConnection()).thenReturn(connection);
        when(connection.getDomains()).thenAnswer(call -> {
            awaitIgnoringInterrupt(release);
            return new String[0];
        });
        JmxService service = new JmxService();
        JMXConnector timed = JmxTimeouts.connect(() -> raw, 1000, 200);
        service.replaceConnection(timed);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(SocketTimeoutException.class, service::getDomains));
            assertThrows(IOException.class, service::getDomains);
            JMXConnector next = mock(JMXConnector.class);
            MBeanServerConnection healthy = mock(MBeanServerConnection.class);
            when(next.getMBeanServerConnection()).thenReturn(healthy);
            when(healthy.getDomains()).thenReturn(new String[0]);
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> service.replaceConnection(next));
            assertTrue(service.getDomains().isEmpty());
            verify(raw, timeout(3000)).close();
        } finally {
            release.countDown();
            service.destroy();
        }
    }

    @Test
    void preservesTransportFailureAndDoesNotReuseBrokenSession() throws Exception {
        JMXConnector raw = mock(JMXConnector.class);
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        when(raw.getMBeanServerConnection()).thenReturn(connection);
        when(connection.getDomains()).thenThrow(new IOException("disconnected"));
        JMXConnector timed = JmxTimeouts.connect(() -> raw, 1000, 1000);
        MBeanServerConnection proxy = timed.getMBeanServerConnection();

        assertEquals("disconnected", assertThrows(IOException.class, proxy::getDomains).getMessage());
        assertThrows(IOException.class, proxy::getDomains);
        verify(connection, times(1)).getDomains();
        verify(raw, timeout(3000)).close();
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;

        try {
            while (true) {
                try {
                    if (!latch.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("test did not release provider");

                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
