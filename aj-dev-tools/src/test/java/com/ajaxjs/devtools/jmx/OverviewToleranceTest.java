package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.*;
import org.junit.jupiter.api.Test;

import javax.management.remote.JMXConnector;
import java.lang.management.ManagementFactory;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverviewToleranceTest {
    @Test
    void keepsHealthyMetricsWhenOtherAttributesAreMissingOrHaveWrongTypes() throws Exception {
        JmxService service = spy(new JmxService());
        JMXConnector connector = mock(JMXConnector.class);
        when(connector.getMBeanServerConnection()).thenReturn(ManagementFactory.getPlatformMBeanServer());
        service.replaceConnection(connector);
        BeanAttributeInfo failed = attribute("HeapMemoryUsage", "不可用");
        failed.getValue().setError("denied");
        doReturn(bean(failed, attribute("NonHeapMemoryUsage", Collections.singletonMap("used", 25L))))
                .when(service).getProperties("java.lang:type=Memory");
        doReturn(bean(attribute("ThreadCount", "wrong"), attribute("DaemonThreadCount", 7), attribute("PeakThreadCount", null)))
                .when(service).getProperties("java.lang:type=Threading");
        doReturn(bean(attribute("Name", "Test VM"), attribute("SystemProperties", "不可用")))
                .when(service).getProperties("java.lang:type=Runtime");
        doReturn(bean(attribute("loadedClassCount", "wrong")))
                .when(service).getProperties("java.lang:type=ClassLoading");

        try {
            Overview info = new MonitorDashboardService(service).overview();
            assertNull(info.getHeapMemoryUsage());
            assertEquals(25L, info.getNonHeapMemoryUsage().getUsed());
            assertNull(info.getNonHeapMemoryUsage().getCommitted());
            assertNull(info.getThreadInfo().getLiveThreadCount());
            assertEquals(7, info.getThreadInfo().getDaemonThreadCount());
            assertNull(info.getThreadInfo().getLivePeakThreadCount());
            assertNull(info.getClassLoadingInfo().getLoadedClassCount());
            assertEquals("Test VM", info.getJvmInfo().getName());
            assertNull(info.getJvmInfo().getSystemProperties());
        } finally {
            service.destroy();
        }
    }

    private static BeanInfo bean(BeanAttributeInfo... attributes) {
        BeanInfo info = new BeanInfo();
        info.setBeanAttributeInfos(Arrays.asList(attributes));
        return info;
    }

    private static BeanAttributeInfo attribute(String name, Object data) {
        BeanAttributeValue value = new BeanAttributeValue();
        value.setData(data);
        BeanAttributeInfo attribute = new BeanAttributeInfo();
        attribute.setName(name);
        attribute.setValue(value);
        return attribute;
    }
}
