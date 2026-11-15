package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.Node;
import org.junit.jupiter.api.Test;

import javax.management.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JmxHelperTest {
    @Test
    void treeRetainsEveryBeanAndUsesUniqueGroupKeys() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        Set<ObjectName> names = new HashSet<>(Arrays.asList(new ObjectName("test:type=Pool,name=one"),
                new ObjectName("test:type=Pool,name=two"), new ObjectName("test:name=withoutType"),
                new ObjectName("test:name=another"), new ObjectName("test:type=Single")));
        when(connection.queryNames(new ObjectName("test:*"), null)).thenReturn(names);

        List<Node> nodes = JmxHelper.getObjectNamesByDomain(connection, "test");
        Set<String> keys = new HashSet<>(), leaves = new HashSet<>();
        collect(nodes, keys, leaves);
        Set<String> expected = new HashSet<>();

        for (ObjectName name : names)
            expected.add(name.getCanonicalName());

        assertEquals(expected, leaves);
        assertEquals(6, keys.size());
        verify(connection, times(1)).queryNames(any(ObjectName.class), isNull());
    }

    @Test
    void failuresKeepTheirCauseInsteadOfReturningNull() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName name = new ObjectName("test:type=Bean");
        IOException failure = new IOException("connection lost");
        when(connection.queryNames(name, null)).thenThrow(failure);
        when(connection.getMBeanInfo(name)).thenThrow(failure);

        assertSame(failure, assertThrows(UncheckedIOException.class, () -> JmxHelper.queryNames(connection, name.toString())).getCause());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> JmxHelper.getMBeanInfo(connection, name)).getCause());
        assertThrows(IllegalArgumentException.class, () -> JmxHelper.queryNames(connection, "invalid"));
        assertThrows(UncheckedIOException.class, () -> JmxHelper.connect("invalid"));
        verify(connection, never()).queryNames(isNull(), isNull());
    }

    @Test
    void skipsUnavailableAttributeAndContinuesReadingOthers() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName name = new ObjectName("test:type=Bean");
        MBeanAttributeInfo[] attributes = {
                new MBeanAttributeInfo("Unreadable", "int", "", false, true, false),
                new MBeanAttributeInfo("Unsupported", "int", "", true, false, false),
                new MBeanAttributeInfo("Healthy", "int", "", true, false, false)
        };
        when(connection.getMBeanInfo(name)).thenReturn(new MBeanInfo("Test", "", attributes, null, null, null));
        when(connection.getAttribute(name, "Unsupported")).thenThrow(new AttributeNotFoundException());
        when(connection.getAttribute(name, "Healthy")).thenReturn(42);
        Map<String, Object> values = new HashMap<>();
        new JmxHelper(connection).everyAttribute(name, values::put);

        assertEquals(Collections.singletonMap("Healthy", 42), values);
        verify(connection, never()).getAttribute(name, "Unreadable");
    }

    private static void collect(List<Node> nodes, Set<String> keys, Set<String> leaves) {
        for (Node node : nodes) {
            assertTrue(keys.add(node.getKey()), "Duplicate key: " + node.getKey());

            if (node.getChildren() == null || node.getChildren().isEmpty())
                leaves.add(node.getFullName());
            else {
                assertNull(node.getFullName());
                collect(node.getChildren(), keys, leaves);
            }
        }
    }
}
