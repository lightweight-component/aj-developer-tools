package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.util.*;

/**
 * MonitorDashboardService 采集如下几个指标：
 * 1. 内存占用
 * 2. CPU 使用率
 * 3. 线程数量
 * 4. 类加载
 * 5. 操作系统信息 通过 OperatingSystemMXBean 或者直接查询 java.lang:type=OperatingSystem
 * 6. jvm 信息 通过 RuntimeMXBean 或者直接查询 java.lang:type=Runtime
 * 7. 垃圾收集器
 *
 * @author fengzheng
 */
@Service
@RequiredArgsConstructor
public class MonitorDashboardService {
    private final JmxService jmxService;

    public Overview overview() {
        return jmxService.withConnection(this::collectOverview);
    }

    private Overview collectOverview() {
        Overview overview = new Overview();
        overview.setSystemInfo(buildSystemInfo());
        overview.setJvmInfo(buildJvmInfo());
        overview.setMetaSpace(buildMetaspace());
        overview.setThreadInfo(buildThreadInfo());
        overview.setClassLoadingInfo(buildClassInfo());
        overview.setGarbageCollectorInfo(buildGarbageCollectorInfo());
        buildMemoryInfo(overview);

        return overview;
    }

    /**
     * 系统参数
     */
    private SystemInfo buildSystemInfo() {
        SystemInfo info = new SystemInfo();

        for (BeanAttributeInfo beanInfo : getBeanAttributeInfoList(ManagementFactory.getOperatingSystemMXBean())) {
            String name = parseName(beanInfo);

            if ("objectName".equals(name))// 忽略这个 会报错
                continue;

            Utils.setBeanValue(info, name, getData(beanInfo));
        }

        return info;
    }

    /**
     * jvm 参数
     */
    private JvmInfo buildJvmInfo() {
        JvmInfo info = new JvmInfo();

        for (BeanAttributeInfo beanInfo : getBeanAttributeInfoList(ManagementFactory.getRuntimeMXBean())) {
            String name = parseName(beanInfo);

            if ("objectName".equals(name))// 忽略这个 会报错
                continue;

            if (!name.equals("systemProperties"))
                Utils.setBeanValue(info, name, getData(beanInfo));
            else
                info.setSystemProperties(systemProperties(getData(beanInfo)));
        }

        return info;
    }

    /**
     * jvm 内存信息 堆和非堆
     */
    private void buildMemoryInfo(Overview overview) {
        for (BeanAttributeInfo beanInfo : getBeanAttributeInfoList(ManagementFactory.getMemoryMXBean())) {
            if (beanInfo.getName().equals("HeapMemoryUsage"))
                overview.setHeapMemoryUsage(buildMemoryUsage(beanInfo));
            else if (beanInfo.getName().equals("NonHeapMemoryUsage"))
                overview.setNonHeapMemoryUsage(buildMemoryUsage(beanInfo));
        }
    }

    private MemoryUsage buildMemoryUsage(BeanAttributeInfo beanInfo) {
        MemoryUsage info = new MemoryUsage();

        Object data = getData(beanInfo);

        if (!(data instanceof Map))
            return null;

        Map<?, ?> map = (Map<?, ?>) data;

        Utils.setBeanValue(info, "init", map.get("init"));
        Utils.setBeanValue(info, "used", map.get("used"));
        Utils.setBeanValue(info, "committed", map.get("committed"));
        Utils.setBeanValue(info, "max", map.get("max"));

        return info;
    }

    /**
     * MetaSpace 元空间信息
     */
    private MemoryUsage buildMetaspace() {
        for (BeanAttributeInfo beanInfo : jmxService.getProperties("java.lang:name=Metaspace,type=MemoryPool").getBeanAttributeInfos()) {
            if (beanInfo.getName().equals("Usage"))
                return buildMemoryUsage(beanInfo);
        }

        return null;
    }

    /**
     * 线程信息
     */
    private ThreadInfo buildThreadInfo() {
        ThreadInfo info = new ThreadInfo();

        for (BeanAttributeInfo beanInfo : getBeanAttributeInfoList(ManagementFactory.getThreadMXBean())) {
            switch (beanInfo.getName()) {
                case "ThreadCount":
                    info.setLiveThreadCount(getData(beanInfo, int.class));
                    break;
                case "DaemonThreadCount":
                    info.setDaemonThreadCount(getData(beanInfo, int.class));
                    break;
                case "TotalStartedThreadCount":
                    info.setTotalStartedThreadCount(getData(beanInfo, long.class));
                    break;
                case "PeakThreadCount":
                    info.setLivePeakThreadCount(getData(beanInfo, int.class));
                    break;
            }
        }

        return info;
    }

    /**
     * class 信息
     */
    private ClassLoadingInfo buildClassInfo() {
        ClassLoadingInfo info = new ClassLoadingInfo();

        for (BeanAttributeInfo beanInfo : getBeanAttributeInfoList(ManagementFactory.getClassLoadingMXBean())) {
            String name = beanInfo.getName();

            if (ignore(name))
                continue;

            Utils.setBeanValue(info, toLowerCaseFirstChar(name), getData(beanInfo));
        }

        return info;
    }

    /**
     * 垃圾收集器信息
     */
    private List<GarbageInfo> buildGarbageCollectorInfo() {
        List<GarbageInfo> garbageInfoList = new ArrayList<>();
        Set<ObjectName> objectNames = jmxService.getGarbageCollectorObjectNames();

        for (ObjectName objectName : objectNames) {
            GarbageInfo info = new GarbageInfo();

            for (BeanAttributeInfo beanInfo : jmxService.getProperties(objectName.getCanonicalName()).getBeanAttributeInfos()) {
                switch (beanInfo.getName()) {
                    case "Name":
                        info.setName(getData(beanInfo, String.class));
                        break;
                    case "CollectionCount":
                        info.setCollectionCount(getData(beanInfo, long.class));
                        break;
                    case "MemoryPoolNames":
                        info.setMemoryPoolNames(getData(beanInfo, String[].class));
                        break;
                }
            }

            garbageInfoList.add(info);
        }

        return garbageInfoList;
    }

    private List<BeanAttributeInfo> getBeanAttributeInfoList(PlatformManagedObject obj) {
        BeanInfo beanInfo = jmxService.getProperties(obj.getObjectName().getCanonicalName());

        return beanInfo.getBeanAttributeInfos();
    }

    private static boolean ignore(String str) {
        return "Verbose".equals(str) || "ObjectName".equals(str);
    }

    private static String toLowerCaseFirstChar(String input) {
        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    private static Object getData(BeanAttributeInfo info) {
        if (info == null || info.getValue() == null || info.getValue().getError() != null)
            return null;

        return info.getValue().getData();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getData(BeanAttributeInfo info, Class<T> clz) {
        Object value = getData(info);

        if ((clz == int.class || clz == long.class) && value instanceof Number)
            return (T) (clz == int.class ? (Object) Integer.valueOf(((Number) value).intValue()) : Long.valueOf(((Number) value).longValue()));

        return clz.isInstance(value) ? clz.cast(value) : null;
    }

    private static List<TreeMap<String, Object>> systemProperties(Object data) {
        if (!(data instanceof List))
            return null;

        List<TreeMap<String, Object>> properties = new ArrayList<>();

        for (Object row : (List<?>) data) {
            if (!(row instanceof Map))
                continue;

            Map<?, ?> values = (Map<?, ?>) row;
            TreeMap<String, Object> property = new TreeMap<>();
            property.put("name", values.get("name"));
            property.put("value", values.get("value"));
            properties.add(property);
        }

        return properties;
    }

    private static String parseName(BeanAttributeInfo info) {
        String name = info.getName(), firstCharLowerCase = name.substring(0, 1).toLowerCase();

        return name.replaceFirst("[A-Z]", firstCharLowerCase);
    }

}
