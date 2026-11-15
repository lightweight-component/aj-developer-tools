package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.BeanAttributeInfo;
import com.ajaxjs.devtools.jmx.model.BeanInfo;
import org.junit.jupiter.api.Test;

import javax.management.*;
import javax.management.openmbean.*;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JmxAttributeTest {
    @Test
    void preservesAllColumnsOfGenericTabularData() throws Exception {
        String[] columns = {"id", "name", "value", "enabled"};
        CompositeType type = new CompositeType("Row", "Row", columns, columns,
                new OpenType<?>[]{SimpleType.INTEGER, SimpleType.STRING, SimpleType.LONG, SimpleType.BOOLEAN});
        CompositeData row = new CompositeDataSupport(type, columns, new Object[]{1, "worker", 25L, true});
        TabularDataSupport table = new TabularDataSupport(new TabularType("Rows", "Rows", type, new String[]{"id"}));
        table.put(row);
        Object data = tableValue("test:type=Rows", "Rows", table);
        assertEquals(Collections.singletonList(JmxHelper.analyzeCompositeData(row)), data);
    }

    @Test
    void runtimeSystemPropertiesKeepNameValueCompatibility() throws Exception {
        CompositeType type = rowType();
        CompositeData row = new CompositeDataSupport(type, new String[]{"key", "value"}, new Object[]{"java.version", "17"});
        TabularDataSupport table = new TabularDataSupport(new TabularType("Properties", "Properties", type, new String[]{"key"}));
        table.put(row);
        Map<String, Object> expected = new TreeMap<>();
        expected.put("name", "java.version");
        expected.put("value", "17");
        assertEquals(Collections.singletonList(expected), tableValue("java.lang:type=Runtime", "SystemProperties", table));
    }

    private static Object tableValue(String fullName, String attribute, TabularData table) throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName name = new ObjectName(fullName);
        when(connection.queryNames(name, null)).thenReturn(Collections.singleton(name));
        when(connection.getMBeanInfo(name)).thenReturn(new MBeanInfo("Test", "", new MBeanAttributeInfo[]{
                new MBeanAttributeInfo(attribute, TabularData.class.getName(), "", true, false, false)
        }, null, null, null));
        when(connection.getAttribute(name, attribute)).thenReturn(table);
        JmxService service = connectedService(connection);

        try {
            return attribute(service.getProperties(fullName), attribute).getValue().getData();
        } finally {
            service.destroy();
        }
    }

    @Test
    void readsRealMemoryCompositeDataOnJdk17WithoutOpeningModules() throws Exception {
        JmxService service = connectedService(ManagementFactory.getPlatformMBeanServer());

        try {
            BeanInfo info = service.getProperties("java.lang:type=Memory");
            BeanAttributeInfo heap = attribute(info, "HeapMemoryUsage");
            assertTrue(heap.getValue().isCompositeData());
            Map<?, ?> values = assertInstanceOf(Map.class, heap.getValue().getData());
            assertEquals(new TreeSet<>(Arrays.asList("init", "used", "committed", "max")), values.keySet());
            assertInstanceOf(Long.class, values.get("used"));
        } finally {
            service.destroy();
        }
    }

    @Test
    void readsCompositeDataThroughItsPublicInterface() throws Exception {
        CompositeType type = rowType();
        CompositeData data = mock(CompositeData.class);
        when(data.getCompositeType()).thenReturn(type);
        when(data.get("key")).thenReturn("java.version");
        when(data.get("value")).thenReturn("17");

        Map<String, Object> expected = new TreeMap<>();
        expected.put("key", "java.version");
        expected.put("value", "17");
        assertEquals(expected, JmxHelper.analyzeCompositeData(data));
        assertNull(JmxHelper.analyzeCompositeData(null));
    }

    @Test
    void failedUnreadableAndNullAttributesDoNotAbortOtherAttributes() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName name = new ObjectName("test:type=Attributes");
        String[] types = { CompositeData.class.getName(), CompositeData[].class.getName(), TabularData.class.getName() };
        List<MBeanAttributeInfo> metadata = new ArrayList<>();

        for (int i = 0; i < types.length; i++) {
            metadata.add(new MBeanAttributeInfo("Failed" + i, types[i], "", true, false, false));
            when(connection.getAttribute(name, "Failed" + i)).thenThrow(new MBeanException(new Exception("read failed")));
        }

        metadata.add(new MBeanAttributeInfo("Unreadable", TabularData.class.getName(), "", false, true, false));
        metadata.add(new MBeanAttributeInfo("NullValue", CompositeData[].class.getName(), "", true, false, false));
        metadata.add(new MBeanAttributeInfo("Healthy", "int", "", true, false, false));
        when(connection.getAttribute(name, "Healthy")).thenReturn(42);
        when(connection.queryNames(name, null)).thenReturn(Collections.singleton(name));
        when(connection.getMBeanInfo(name)).thenReturn(new MBeanInfo("Test", "", metadata.toArray(new MBeanAttributeInfo[0]), null, null, null));
        JmxService service = connectedService(connection);

        try {
            BeanInfo info = service.getProperties(name.toString());
            assertEquals(6, info.getBeanAttributeInfos().size());

            for (int i = 0; i < types.length; i++) {
                assertEquals("不可用", attribute(info, "Failed" + i).getValue().getData());
                assertFalse(attribute(info, "Failed" + i).getValue().isCompositeData());
            }

            assertEquals("不可读", attribute(info, "Unreadable").getValue().getData());
            verify(connection, never()).getAttribute(name, "Unreadable");
            assertNull(attribute(info, "NullValue").getValue().getData());
            assertEquals(42, attribute(info, "Healthy").getValue().getData());
        } finally {
            service.destroy();
        }
    }

    @Test
    void convertsCompositeArraysAndTabularInterfaceWithoutChangingPropertyRows() throws Exception {
        CompositeType type = rowType();
        CompositeData row = new CompositeDataSupport(type, new String[]{"key", "value"}, new Object[]{"java.version", "17"});
        TabularData table = mock(TabularData.class);
        doReturn(Collections.singletonList(row)).when(table).values();
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName name = new ObjectName("test:type=Composite");
        when(connection.queryNames(name, null)).thenReturn(Collections.singleton(name));
        when(connection.getMBeanInfo(name)).thenReturn(new MBeanInfo("Test", "", new MBeanAttributeInfo[]{
                new MBeanAttributeInfo("Rows", CompositeData[].class.getName(), "", true, false, false),
                new MBeanAttributeInfo("Properties", TabularData.class.getName(), "", true, false, false)
        }, null, null, null));
        when(connection.getAttribute(name, "Rows")).thenReturn(new CompositeData[]{row});
        when(connection.getAttribute(name, "Properties")).thenReturn(table);
        JmxService service = connectedService(connection);

        try {
            BeanInfo info = service.getProperties(name.toString());
            assertEquals(Collections.singletonList(JmxHelper.analyzeCompositeData(row)), attribute(info, "Rows").getValue().getData());
            Map<String, Object> expected = new TreeMap<>();
            expected.put("key", "java.version");
            expected.put("value", "17");
            assertEquals(Collections.singletonList(expected), attribute(info, "Properties").getValue().getData());
            assertTrue(attribute(info, "Properties").getValue().isCompositeData());
        } finally {
            service.destroy();
        }
    }

    private static CompositeType rowType() throws OpenDataException {
        return new CompositeType("Property", "Property", new String[]{"key", "value"}, new String[]{"key", "value"},
                new OpenType<?>[]{SimpleType.STRING, SimpleType.STRING});
    }

    private static JmxService connectedService(MBeanServerConnection connection) throws IOException {
        JMXConnector connector = mock(JMXConnector.class);
        when(connector.getMBeanServerConnection()).thenReturn(connection);
        JmxService service = new JmxService();
        service.replaceConnection(connector);
        return service;
    }

    private static BeanAttributeInfo attribute(BeanInfo info, String name) {
        return info.getBeanAttributeInfos().stream().filter(item -> item.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
    }
}
