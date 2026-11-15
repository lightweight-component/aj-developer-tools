package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.Node;
import com.ajaxjs.devtools.jmx.model.NodeType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.BiConsumer;

@Slf4j
public class JmxHelper {
    public JmxHelper() {
    }

    public JmxHelper(MBeanServerConnection msc) {
        this.msc = msc;
    }

    /**
     * 连接实例
     */
    @Getter
    @Setter
    private MBeanServerConnection msc;

    /**
     * 连接对象
     */
    @Getter
    @Setter
    private JMXConnector connector;

    public MBeanAttributeInfo[] getAttributes(ObjectName objName) {
        return getMBeanInfo(msc, objName).getAttributes();
    }

    public void everyAttribute(ObjectName objName, BiConsumer<String, Object> fn) {
        for (MBeanAttributeInfo attribute : getAttributes(objName)) {
            if (!attribute.isReadable())
                continue;

            Object value;

            try {
                value = msc.getAttribute(objName, attribute.getName());
            } catch (IOException e) {
                throw new UncheckedIOException("读取 MBean 属性失败: " + objName, e);
            } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException e) {
                log.warn("跳过不可用的 MBean 属性: {}.{}", objName, attribute.getName(), e);
                continue;
            }

            fn.accept(attribute.getName(), value);
        }
    }

    /**
     * 列出所有的对象。
     * 下面方法同等效果
     * <code>
     * for (String domain : msc.getDomains())
     * System.out.println(domain);
     * List<Node> tomcat = MonitorUtils.getObjectNamesByDomain(msc, "Tomcat");
     * </code>
     */
    void listObjectInstance() {
        try {
            Set<ObjectInstance> MBeanset = msc.queryMBeans(null, null);

            for (ObjectInstance objectInstance : MBeanset) {
                ObjectName objectName = objectInstance.getObjectName();
                String canonicalName = objectName.getCanonicalName();
                System.out.println("canonicalName : " + canonicalName);

                // 可以查询集群
//                if (canonicalName.equals("Catalina:host=localhost,type=Cluster")) {
//                    // Get details of cluster MBeans
//                    // getMBeansDetails(canonicalName);
//                    String canonicalKeyPropList = objectName.getCanonicalKeyPropertyListString();
//                }
            }
        } catch (IOException e) {
            log.warn("Errors when listObjectInstance", e);
            throw new UncheckedIOException(e);
        }
    }

    public List<Node> getObjectNamesByDomain(String domain) {
        return getObjectNamesByDomain(msc, domain);
    }

    public static JMXConnector connect(String jmxUrl) {
        try {
            return JmxTimeouts.connect(jmxUrl);
        } catch (IOException e) {
            log.warn("Errors when connect to a JMX Service: " + jmxUrl, e);
            throw new UncheckedIOException("连接 JMX 服务失败: " + jmxUrl, e);
        }
    }

    public static JMXConnector connect(String host, String port) {
        return connect(String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host, port));
    }

    /**
     * 根据命名空间获取对象名称列表
     * 可以先打印出所有的命名空间：
     * <code>
     * for (String domain : msc.getDomains())
     * System.out.println(domain);
     * </code>
     *
     * @param msc    MBeanServerConnection
     * @param domain 命名空间
     * @return 对象名称列表
     */
    public static List<Node> getObjectNamesByDomain(MBeanServerConnection msc, String domain) {
        List<Node> nodes = new ArrayList<>();
        Map<String, List<ObjectName>> groups = new TreeMap<>();
        List<ObjectName> names = new ArrayList<>(queryNames(msc, domain + ":*"));
        names.sort(Comparator.comparing(ObjectName::getCanonicalName));

        for (ObjectName name : names) {
            String type = name.getKeyProperty("type");

            if (type == null)
                nodes.add(beanNode(name));
            else
                groups.computeIfAbsent(type, ignored -> new ArrayList<>()).add(name);
        }

        for (Map.Entry<String, List<ObjectName>> group : groups.entrySet()) {
            if (group.getValue().size() == 1) {
                nodes.add(beanNode(group.getValue().get(0)));
                continue;
            }

            Node parent = new Node();
            parent.setKey("group:" + domain.length() + ":" + domain + ":" + group.getKey());
            parent.setTitle(group.getKey());
            parent.setNodeType("group");
            List<Node> children = new ArrayList<>();

            for (ObjectName name : group.getValue())
                children.add(beanNode(name));

            parent.setChildren(children);
            nodes.add(parent);
        }

        return nodes;
    }

    private static Node beanNode(ObjectName name) {
        Node node = new Node();
        String fullName = name.getCanonicalName();
        String title = name.getKeyProperty("name");
        node.setTitle(title == null || title.isEmpty() ? fullName : title);
        node.setFullName(fullName);
        node.setKey(fullName);
        node.setNodeType(NodeType.OBJECTNAME.getName());

        return node;
    }

    public static SortedMap<String, Object> analyzeCompositeData(Object compositeData) {
        if (!(compositeData instanceof CompositeData))
            return null;

        CompositeData data = (CompositeData) compositeData;
        SortedMap<String, Object> values = new TreeMap<>();

        for (String key : data.getCompositeType().keySet())
            values.put(key, data.get(key));

        return values;
    }

    /**
     * 实例化都抛出异常，麻烦
     */
    public static ObjectName objectNameFactory(String name) {
        try {
            return new ObjectName(name);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("无效的 MBean 名称: " + name, e);
        }
    }

    public Set<ObjectName> queryNames(String name) {
        return queryNames(msc, name);
    }

    public static Set<ObjectName> queryNames(MBeanServerConnection msc, String name) {
        try {
            return msc.queryNames(objectNameFactory(name), null);
        } catch (IOException e) {
            throw new UncheckedIOException("查询 MBeans 失败: " + name, e);
        }
    }

    public MBeanInfo getMBeanInfo(ObjectName o) {
        return getMBeanInfo(msc, o);
    }

    public static MBeanInfo getMBeanInfo(MBeanServerConnection msc, ObjectName o) {
        try {
            return msc.getMBeanInfo(o);
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException | IOException e) {
            throw new IllegalStateException("读取 MBean 元数据失败: " + o, e);
        }
    }

    /**
     * 注册一个 MBean
     * 接口必须以 MBean 结尾，且提供 String getStatus(); 方法。
     * <pre>
     * public interface CustomTomcatThreadPoolMBean {
     *     String getStatus();
     * }
     * </pre>
     *
     * @param mBean MBean 对象，须实现接口方法
     * @param name  ObjectName 须符合一定格式，如 "qww:type=CustomTomcatThreadPool"
     */
    public void registerMBean(Object mBean, String name) {
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(mBean, new ObjectName(name));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException |
                 MalformedObjectNameException e) {
            log.error("registerMBean", e);
        }
    }

    public static void dumpMBean(MBeanServer server, ObjectName objName, MBeanInfo mbi, Writer writer) throws Exception {
        writer.write(String.format("MBeanClassName=%s%n", mbi.getClassName()));
        Map<String, String> props = new HashMap<>();
        int idx = 0;

        for (MBeanAttributeInfo mf : mbi.getAttributes()) {
            idx++;

            try {
                Object attr = server.getAttribute(objName, mf.getName());

                if (attr != null)
                    props.put(mf.getName(), attr.toString());
            } catch (Exception ex) {
                // sun.management.RuntimeImpl: java.lang.UnsupportedOperationException(Boot class path mechanism is not supported)
                props.put("error_" + idx, ex.getClass().getName() + " " + ex.getMessage());
            }
        }

        // sort by hashmap keys
        for (String sKey : new TreeSet<>(props.keySet()))
            writer.write(String.format("%s=%s%n", sKey, props.get(sKey)));
    }

    /**
     * Dump MBean management properties, all beans or named bean {@code dumpMBean.jsp?name=ConnectionPool,ContainerMBean}
     * JSP as below:
     * <pre>{@code
     * <%@ page contentType="text/plain; charset=UTF-8"  pageEncoding="UTF-8" import="com.ajaxjs.devtools.jmxmonitor.JmxHelper"%>
     * <%
     *     JmxHelper.printMBean(request, out);
     * %>
     * }</pre>
     */
    public static void printMBean(HttpServletRequest request, Writer out) throws Exception {
        String val = request.getParameter("name");
        String[] names = val != null ? val.trim().split(",") : new String[0];

        if (names.length == 1 && names[0].isEmpty())
            names = new String[0];

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        for (ObjectName objName : server.queryNames(null, null)) {
            MBeanInfo mbi = server.getMBeanInfo(objName);

            boolean match = names.length < 1;
            String name = mbi.getClassName();

            for (String s : names) {
                if (name.endsWith(s)) {
                    match = true;
                    break;
                }
            }

            if (match) {
                JmxHelper.dumpMBean(server, objName, mbi, out);
                out.write("");
            }
        }

        out.flush();
    }
}
