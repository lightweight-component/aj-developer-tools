package com.ajaxjs.devtools.jmx.tomcat;

import com.ajaxjs.devtools.jmx.JmxHelper;
import com.ajaxjs.devtools.jmx.tomcat.model.*;
import lombok.extern.slf4j.Slf4j;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

/**
 * 获取 Tomcat 的 JMX 信息
 * 要通过端口获取，比较受限
 * <a href="https://blog.csdn.net/zhangxin09/article/details/132136748">...</a>
 */
@Slf4j
public class TomcatJmx extends JmxHelper {
    public TomcatInfo getInfo(String jmxURL) {
        return getInfo(jmxURL, null);
    }

    public synchronized TomcatInfo getInfo(String jmxURL, Integer port) {
        TomcatInfo info = new TomcatInfo();

        try (JMXConnector connect = connect(jmxURL)) {
            MBeanServerConnection msc = connect.getMBeanServerConnection();
            setMsc(msc);

            SystemInfo systemInfo = new SystemInfo();
            ThreadPool threadPool = new ThreadPool();
            info.jvmInfo = jvm();
            info.systemInfo = systemInfo;
            ObjectName pool = findThreadPool(msc, port);
            info.session = getSession(pool.getDomain());
            info.threadPool = threadPool;

            everyAttribute(pool, (key, value) -> setBeanValue(threadPool, key, value));
            everyAttribute(objectNameFactory("java.lang:type=Runtime"), (key, value) -> {
                if ("StartTime".equals(key))
                    setBeanValue(systemInfo, key, Instant.ofEpochMilli(((Number) value).longValue()).toString());
                else if ("Uptime".equals(key))
                    setBeanValue(systemInfo, key, formatTimeSpan(((Number) value).longValue()));
                else
                    setBeanValue(systemInfo, key, value);
            });
        } catch (IOException e) {
            log.warn("TomcatJmx.getInfo", e);
            throw new UncheckedIOException(e);
        } finally {
            setMsc(null);
        }

        return info;
    }

    /**
     * 使用实际 ObjectName 查找线程池，支持不同域及 NIO/NIO2/APR 等名称。
     */
    private static ObjectName findThreadPool(MBeanServerConnection msc, Integer port) {
        List<ObjectName> pools = new ArrayList<>(queryNames(msc, "*:type=ThreadPool,*"));
        pools.sort(Comparator.comparing(ObjectName::getCanonicalName));

        if (pools.isEmpty())
            throw new IllegalStateException("未发现 Tomcat 线程池 MBean");

        if (port == null) {
            if (pools.size() == 1)
                return pools.get(0);

            throw new IllegalArgumentException("发现多个 Tomcat 线程池，请指定连接器端口");
        }

        List<ObjectName> matches = new ArrayList<>();

        for (ObjectName pool : pools) {
            String name = pool.getKeyProperty("name");

            if (name != null && name.startsWith("\""))
                name = ObjectName.unquote(name);

            Object configuredPort = optionalAttribute(msc, pool, "port");

            if ((name != null && name.endsWith("-" + port)) || port.toString().equals(String.valueOf(configuredPort)))
                matches.add(pool);
        }

        // 自定义线程池名不包含端口时，通过同域 Connector 关联唯一线程池。
        if (matches.isEmpty()) {
            Set<ObjectName> connectors = queryNames(msc, "*:type=Connector,*");

            for (ObjectName connector : connectors) {
                Object value = optionalAttribute(msc, connector, "port");

                if (value == null)
                    value = connector.getKeyProperty("port");

                if (!port.toString().equals(String.valueOf(value)))
                    continue;

                List<ObjectName> sameDomain = new ArrayList<>();

                for (ObjectName pool : pools)
                    if (pool.getDomain().equals(connector.getDomain()))
                        sameDomain.add(pool);

                if (sameDomain.size() == 1 && !matches.contains(sameDomain.get(0)))
                    matches.add(sameDomain.get(0));
            }
        }

        if (matches.size() != 1)
            throw new IllegalArgumentException("端口 " + port + " 未对应唯一的 Tomcat 线程池");

        return matches.get(0);
    }

    private static Object optionalAttribute(MBeanServerConnection msc, ObjectName name, String attribute) {
        try {
            return msc.getAttribute(name, attribute);
        } catch (IOException e) {
            throw new UncheckedIOException("读取连接器属性失败", e);
        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException e) {
            return null;
        }
    }

    private List<Session> getSession(String domain) {
        Set<ObjectName> objectNames = queryNames(domain + ":type=Manager,*");
        List<Session> list = new ArrayList<>(objectNames.size());

        for (ObjectName obj : objectNames) {
//            List<Node> tomcat = JmxUtils.getObjectNamesByDomain(msc, "Tomcat");
//                System.out.println("应用名:" + obj.getKeyProperty("path"));
//                System.out.println("currentThreadCount:" + msc.getAttribute(threadObjName, "currentThreadCount"));// tomcat的线程数对应的属性值

            Session session = new Session();
            everyAttribute(objectNameFactory(obj.getCanonicalName()), (key, value) -> setBeanValue(session, key, value));
            list.add(session);
        }

        return list;
    }

    private JvmInfo jvm() {
        try {
            // 堆使用率
            ObjectName heapObjName = objectNameFactory("java.lang:type=Memory");
            MemoryUsage heapMemoryUsage = MemoryUsage.from((CompositeData) getMsc().getAttribute(heapObjName, "HeapMemoryUsage"));

            // 堆当前分配
            long commitMemory = heapMemoryUsage.getCommitted(), usedMemory = heapMemoryUsage.getUsed();
            JvmInfo jvmInfo = new JvmInfo();
            jvmInfo.setMaxMemory(heapMemoryUsage.getMax());
            jvmInfo.setCommitMemory(commitMemory);
            jvmInfo.setUsedMemory(usedMemory);
            jvmInfo.setHeap(percent(usedMemory, commitMemory));

            MemoryUsage nonheapMemoryUsage = MemoryUsage.from((CompositeData) getMsc().getAttribute(heapObjName, "NonHeapMemoryUsage"));
            long nonCommitMemory = nonheapMemoryUsage.getCommitted(), nonUsedMemory = nonheapMemoryUsage.getUsed();
            jvmInfo.setNonCommitMemory(nonCommitMemory);
            jvmInfo.setNonUsedMemory(nonUsedMemory);
            jvmInfo.setNonHeap(percent(nonUsedMemory, nonCommitMemory));

//            ObjectName permObjName = new ObjectName("java.lang:type=MemoryPool,name=Perm Gen");
//            MemoryUsage permGenUsage = MemoryUsage.from((CompositeData) getMsc().getAttribute(permObjName, "Usage"));
//            long committed = permGenUsage.getCommitted();
//            long used = heapMemoryUsage.getUsed();
//
//            jvmInfo.setCommitted(committed);
//            jvmInfo.setUsed(used);
//            jvmInfo.setPermUse(((Long) (used * 100 / committed)).intValue());

            return jvmInfo;
        } catch (ReflectionException | AttributeNotFoundException | InstanceNotFoundException | MBeanException |
                 IOException e) {
            throw new IllegalStateException("读取 JVM 内存失败", e);
        }
    }

    private static int percent(long used, long committed) {
        if (committed <= 0)
            return 0;

        return (int) (used * 100.0 / committed);
    }

    private static String formatTimeSpan(long span) {
        long minSeconds = span % 1000;

        span = span / 1000;
        long seconds = span % 60;

        span = span / 60;
        long min = span % 60;

        span = span / 60;
        long hours = span % 24;

        span = span / 24;
        long days = span;

        try (Formatter formatter = new Formatter()) {
            return formatter.format("%1$d天 %2$02d:%3$02d:%4$02d.%5$03d", days, hours, min, seconds, minSeconds).toString();
        }
    }

    /**
     * 设置 Java Bean 的值
     *
     * @param bean      Bean 实体
     * @param fieldName 字段名
     * @param value     值
     */
    public static void setBeanValue(Object bean, String fieldName, Object value) {
        try {
            Field field = bean.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            // the exception here doesn't need to handle
            log.warn("访问字段时候 {} 失败", fieldName);
        } catch (NoSuchFieldException e) {
            log.warn("No Such Field: {}", fieldName);
        }
    }
}
