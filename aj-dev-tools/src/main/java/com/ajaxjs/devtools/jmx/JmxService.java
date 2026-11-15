package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.*;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

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

        replaceConnection(JMXConnectorFactory.connect(new JMXServiceURL(address)));
    }

    public void attachRemoteJvm(String host, String port) throws IOException {
        if (host == null || host.trim().isEmpty() || port == null || port.trim().isEmpty())
            throw new IllegalArgumentException("服务器地址和端口不能为空");

        String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host.trim(), port.trim());
        replaceConnection(JMXConnectorFactory.connect(new JMXServiceURL(url)));
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

                if (mBeanAttributeInfo.isReadable()) {
                    try {
                        attribute = helper.getMsc().getAttribute(o, mBeanAttributeInfo.getName());
                    } catch (Exception e) {
                        attribute = "不可用";
                    }
                } else
                    attribute = "不可读";

                BeanAttributeInfo beanAttributeInfo = new BeanAttributeInfo();
                beanAttributeInfo.setName(mBeanAttributeInfo.getName());
                beanAttributeInfo.setAttributeInfo(mBeanAttributeInfo);
                BeanAttributeValue beanAttributeValue = new BeanAttributeValue();
                beanAttributeValue.setCompositeData(true);

                if (mBeanAttributeInfo.getType().equals("javax.management.openmbean.CompositeData") && mBeanAttributeInfo.isReadable())
                    beanAttributeValue.setData(JmxHelper.analyzeCompositeData(attribute));
                else if (mBeanAttributeInfo.getType().equals("[Ljavax.management.openmbean.CompositeData;") && mBeanAttributeInfo.isReadable()) {
                    CompositeData[] compositeDataArray = (CompositeData[]) attribute;
                    List<SortedMap<String, Object>> mapList = new ArrayList<>(compositeDataArray.length);

                    for (CompositeData compositeData : compositeDataArray)
                        mapList.add(JmxHelper.analyzeCompositeData(compositeData));

                    beanAttributeValue.setData(mapList);
                } else if (mBeanAttributeInfo.getType().equals("javax.management.openmbean.TabularData")) {
                    List<SortedMap<String, Object>> mapList = new ArrayList<>();
                    TabularDataSupport tabularDataSupport = (TabularDataSupport) attribute;

                    for (Map.Entry<Object, Object> entry : tabularDataSupport.entrySet()) {
                        SortedMap<String, Object> map = JmxHelper.analyzeCompositeData(entry.getValue());
                        assert map != null;

                        SortedMap<String, Object> normalMap = new TreeMap<>();
                        normalMap.put("name", map.getOrDefault("key", ""));
                        normalMap.put("value", map.getOrDefault("value", ""));
                        mapList.add(normalMap);
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
