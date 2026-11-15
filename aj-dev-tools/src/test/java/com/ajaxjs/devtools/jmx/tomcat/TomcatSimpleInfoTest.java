package com.ajaxjs.devtools.jmx.tomcat;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.ProtocolHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TomcatSimpleInfoTest {
    @Test
    void handlesUninitializedAndUnknownExecutors() {
        assertEquals("尚未初始化", info(null).get("线程池状态"));
        assertEquals("此 Executor 不提供线程池统计接口", info(Runnable::run).get("线程池状态"));
        assertThrows(IllegalArgumentException.class, () -> TomcatSimpleInfo.getTomcatSimplyInfo(null));
    }

    @Test
    void readsJdkThreadPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 3, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        try {
            assertEquals("3", info(pool).get("最大线程数(maximumPoolSize)"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void readsTomcatThreadPoolAndSharedExecutor() {
        org.apache.tomcat.util.threads.ThreadPoolExecutor pool = new org.apache.tomcat.util.threads.ThreadPoolExecutor(
                1, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        try {
            assertEquals("4", info(pool).get("最大线程数(maximumPoolSize)"));
        } finally {
            pool.shutdownNow();
        }

        StandardThreadExecutor shared = mock(StandardThreadExecutor.class);
        when(shared.getMaxThreads()).thenReturn(5);
        assertEquals("5", info(shared).get("最大线程数(maximumPoolSize)"));
    }

    private static Map<String, String> info(Executor executor) {
        Tomcat tomcat = mock(Tomcat.class);
        Connector connector = mock(Connector.class);
        ProtocolHandler handler = mock(ProtocolHandler.class);
        when(tomcat.getConnector()).thenReturn(connector);
        when(connector.getProtocolHandler()).thenReturn(handler);
        when(handler.getExecutor()).thenReturn(executor);
        return TomcatSimpleInfo.getTomcatSimplyInfo(tomcat);
    }
}
