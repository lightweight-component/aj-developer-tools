package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.*;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Supplier;

/**
 * 管理当前监控目标的连接及 MBean 操作。
 */
@Slf4j
@Service
public class JmxService implements DisposableBean {
    private static final String LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private JMXConnector connector;

    private MBeanServerConnection connection;

    public List<Vm> getLocalJvms() {
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        List<Vm> list = new ArrayList<>(vms.size());

        for (VirtualMachineDescriptor descriptor : vms) {
            Vm jvm = new Vm();
            jvm.setPid(Integer.parseInt(descriptor.id()));
            jvm.setName(descriptor.displayName().split(" ")[0]);
            list.add(jvm);
        }

        return list;
    }

    /**
     * 复用目标 JVM 已启动的管理 Agent，未启动时通过 Attach API 启动。
     * 无论成功或失败，都释放 Attach 句柄。
     */
    public void attachLocalJvm(Integer pid) throws Exception {
        if (pid == null || pid <= 0)
            throw new IllegalArgumentException("pid 必须为正整数");

        replaceConnection(JmxTimeouts.connect(() -> {
            VirtualMachine vm = VirtualMachine.attach(Integer.toString(pid));
            String address;

            try {
                address = vm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS);

                if (address == null)
                    address = vm.startLocalManagementAgent();

                if (address == null || address.isEmpty())
                    throw new IOException("未获取到本地 JMX 连接地址");
            } finally {
                vm.detach();
            }

            return JMXConnectorFactory.connect(new JMXServiceURL(address));
        }));
    }

    public void attachRemoteJvm(String host, String port) throws IOException {
        if (host == null || host.trim().isEmpty() || port == null || port.trim().isEmpty())
            throw new IllegalArgumentException("服务器地址和端口不能为空");

        String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host.trim(), port.trim());
        replaceConnection(JmxTimeouts.connect(url));
    }

    /**
     * 新连接可用后再替换监控目标，失败时保留原连接。
     */
    synchronized void replaceConnection(JMXConnector next) throws IOException {
        Objects.requireNonNull(next, "JMX 连接不能为空");
        MBeanServerConnection nextConnection;

        try {
            nextConnection = Objects.requireNonNull(next.getMBeanServerConnection(), "MBean 连接不能为空");
        } catch (IOException | RuntimeException e) {
            closeConnector(next);
            throw e;
        }

        JMXConnector previous = connector;
        connector = next;
        connection = nextConnection;

        if (previous != next)
            closeConnector(previous);
    }

    /**
     * 在同一连接生命周期内完成一组采集，期间不允许替换或关闭连接。
     */
    public synchronized <T> T withConnection(Supplier<T> action) {
        requireConnection();
        return action.get();
    }

    private MBeanServerConnection requireConnection() {
        if (connection == null)
            throw new IllegalStateException("你需要先连接一个 JVM 进程");

        return connection;
    }

    public synchronized List<Node> getDomains() throws IOException {
        MBeanServerConnection msc = requireConnection();
        List<Node> nodes = new ArrayList<>();

        for (String domain : msc.getDomains()) {
            Node node = new Node();
            node.setKey(domain);
            node.setTitle(domain);
            node.setNodeType(NodeType.DOMAIN.getName());
            node.setChildren(JmxHelper.getObjectNamesByDomain(msc, domain));
            nodes.add(node);
        }

        return nodes;
    }

    public synchronized Set<ObjectName> getGarbageCollectorObjectNames() {
        try {
            return requireConnection().queryNames(new ObjectName("java.lang:name=*,type=GarbageCollector"), null);
        } catch (IOException | MalformedObjectNameException e) {
            throw new IllegalStateException("查询垃圾收集器失败", e);
        }
    }

    public synchronized BeanInfo getProperties(String fullName) {
        BeanInfo beanInfo = new BeanInfo();
        List<BeanAttributeInfo> list = new ArrayList<>();

        JmxHelper helper = new JmxHelper(requireConnection());

        for (ObjectName o : helper.queryNames(fullName)) {
            MBeanInfo mBeanInfo = helper.getMBeanInfo(o);
            beanInfo.setOperationInfos(mBeanInfo.getOperations());
            beanInfo.setNotificationInfos(mBeanInfo.getNotifications());

            for (MBeanAttributeInfo mBeanAttributeInfo : mBeanInfo.getAttributes()) {
                Object attribute;
                String readError = null;

                if (mBeanAttributeInfo.isReadable()) {
                    try {
                        attribute = helper.getMsc().getAttribute(o, mBeanAttributeInfo.getName());
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException("读取 JMX 属性失败", e);
                    } catch (Exception e) {
                        readError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        attribute = "不可用";
                    }
                } else {
                    readError = "属性不可读";
                    attribute = "不可读";
                }

                BeanAttributeInfo beanAttributeInfo = new BeanAttributeInfo();
                beanAttributeInfo.setName(mBeanAttributeInfo.getName());
                beanAttributeInfo.setAttributeInfo(mBeanAttributeInfo);
                BeanAttributeValue beanAttributeValue = new BeanAttributeValue();
                beanAttributeValue.setError(readError);

                // 按实际读取到的值转换，读取失败的提示文本和 null 不参与复合类型转换。
                if (attribute instanceof CompositeData) {
                    beanAttributeValue.setCompositeData(true);
                    beanAttributeValue.setData(JmxHelper.analyzeCompositeData(attribute));
                } else if (attribute instanceof CompositeData[]) {
                    beanAttributeValue.setCompositeData(true);
                    CompositeData[] compositeDataArray = (CompositeData[]) attribute;
                    List<SortedMap<String, Object>> mapList = new ArrayList<>(compositeDataArray.length);

                    for (CompositeData compositeData : compositeDataArray)
                        mapList.add(JmxHelper.analyzeCompositeData(compositeData));

                    beanAttributeValue.setData(mapList);
                } else if (attribute instanceof TabularData) {
                    beanAttributeValue.setCompositeData(true);
                    List<SortedMap<String, Object>> mapList = new ArrayList<>();
                    TabularData tabularData = (TabularData) attribute;

                    for (Object row : tabularData.values()) {
                        SortedMap<String, Object> map = JmxHelper.analyzeCompositeData(row);

                        if (map == null)
                            continue;

                        if (o.equals(JmxHelper.objectNameFactory("java.lang:type=Runtime")) && "SystemProperties".equals(mBeanAttributeInfo.getName())) {
                            SortedMap<String, Object> property = new TreeMap<>();
                            property.put("name", map.get("key"));
                            property.put("value", map.get("value"));
                            mapList.add(property);
                        } else
                            mapList.add(map);
                    }

                    beanAttributeValue.setData(mapList);
                } else
                    beanAttributeValue.setData(attribute);

                beanAttributeInfo.setValue(beanAttributeValue);
                list.add(beanAttributeInfo);
            }
        }

        beanInfo.setBeanAttributeInfos(list);

        return beanInfo;
    }

    public synchronized void invokeMethod(String fullName, String methodName) throws ReflectionException, InstanceNotFoundException, MBeanException, IOException {
        requireConnection().invoke(JmxHelper.objectNameFactory(fullName), methodName, null, null);
    }

    public Map<String, Object> jdbcPoolStatus() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = server.queryNames(null, null);
        Map<String, Object> result = new HashMap<>();

        try {
            for (ObjectName name : objectNames) {
                MBeanInfo info = server.getMBeanInfo(name);

                if (info.getClassName().equals("org.apache.tomcat.jdbc.pool.jmx.ConnectionPool")) {
                    for (MBeanAttributeInfo mf : info.getAttributes()) {
                        Object attributeValue = server.getAttribute(name, mf.getName());

                        if (attributeValue != null)
                            result.put(mf.getName(), attributeValue);
                    }

                    break;
                }
            }
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException | MBeanException |
                 AttributeNotFoundException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    @Override
    public synchronized void destroy() {
        JMXConnector previous = connector;
        connector = null;
        connection = null;
        closeConnector(previous);
    }

    private void closeConnector(JMXConnector value) {
        if (value == null)
            return;

        try {
            value.close();
        } catch (IOException e) {
            log.warn("关闭 JMX 连接失败", e);
        }
    }
}
