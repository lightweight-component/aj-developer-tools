package com.ajaxjs.devtools.jmx.tomcat;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.core.StandardThreadExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 简单获取 Tomcat 信息
 */
public class TomcatSimpleInfo {
    /**
     * 通过 Tomcat 自身 API 获取的实时信息
     * 例子
     * <pre>{@code
     * Map<String, String> tomcatSimplyInfo = TomcatInfo.getTomcatSimplyInfo(EmbeddedTomcatStarter.TOMCAT);
     * TestHelper.printJson(tomcatSimplyInfo);
     * }</pre>
     *
     * @param tomcat Tomcat 实例
     * @return Tomcat 简单的信息
     */
    public static Map<String, String> getTomcatSimplyInfo(Tomcat tomcat) {
        if (tomcat == null)
            throw new IllegalArgumentException("Tomcat 实例不能为空");

        Executor candidate = tomcat.getConnector().getProtocolHandler().getExecutor();

        Map<String, String> returnMap = new LinkedHashMap<>();
        if (candidate == null) {
            returnMap.put("线程池状态", "尚未初始化");
            return returnMap;
        }

        returnMap.put("线程池类型", candidate.getClass().getName());

        if (candidate instanceof StandardThreadExecutor) {
            StandardThreadExecutor shared = (StandardThreadExecutor) candidate;
            returnMap.put("核心线程数(corePoolSize)", String.valueOf(shared.getCorePoolSize()));
            returnMap.put("最大线程数(maximumPoolSize)", String.valueOf(shared.getMaxThreads()));
            returnMap.put("活跃线程数(activeCount)", String.valueOf(shared.getActiveCount()));
            returnMap.put("池中当前线程数(poolSize)", String.valueOf(shared.getPoolSize()));
            returnMap.put("历史最大线程数(largestPoolSize)", String.valueOf(shared.getLargestPoolSize()));
            returnMap.put("工作队列任务数量(workQueue.size)", String.valueOf(shared.getQueueSize()));
            returnMap.put("历史完成任务的总数(近似值)(completedTaskCount)", String.valueOf(shared.getCompletedTaskCount()));
            return returnMap;
        }

        if (candidate instanceof org.apache.tomcat.util.threads.ThreadPoolExecutor) {
            org.apache.tomcat.util.threads.ThreadPoolExecutor executor = (org.apache.tomcat.util.threads.ThreadPoolExecutor) candidate;
            returnMap.put("核心线程数(corePoolSize)", String.valueOf(executor.getCorePoolSize()));
            returnMap.put("最大线程数(maximumPoolSize)", String.valueOf(executor.getMaximumPoolSize()));
            returnMap.put("活跃线程数(activeCount)", String.valueOf(executor.getActiveCount()));
            returnMap.put("池中当前线程数(poolSize)", String.valueOf(executor.getPoolSize()));
            returnMap.put("历史最大线程数(largestPoolSize)", String.valueOf(executor.getLargestPoolSize()));
            returnMap.put("工作队列任务数量(workQueue.size)", String.valueOf(executor.getQueue().size()));
            returnMap.put("线程允许空闲时间/s(keepAliveTime)", String.valueOf(executor.getKeepAliveTime(TimeUnit.SECONDS)));
            returnMap.put("核心线程数是否允许被回收(allowCoreThreadTimeOut)", String.valueOf(executor.allowsCoreThreadTimeOut()));
            returnMap.put("提交任务总数(submittedCount)", String.valueOf(executor.getSubmittedCount()));
            returnMap.put("历史执行任务的总数(近似值)(taskCount)", String.valueOf(executor.getTaskCount()));
            returnMap.put("历史完成任务的总数(近似值)(completedTaskCount)", String.valueOf(executor.getCompletedTaskCount()));
            returnMap.put("拒绝策略(ejectedExecutionHandler.class)", executor.getRejectedExecutionHandler().getClass().getCanonicalName());


            return returnMap;
        }

        if (!(candidate instanceof ThreadPoolExecutor)) {
            returnMap.put("线程池状态", "此 Executor 不提供线程池统计接口");
            return returnMap;
        }

        ThreadPoolExecutor executor = (ThreadPoolExecutor) candidate;
        returnMap.put("核心线程数(corePoolSize)", String.valueOf(executor.getCorePoolSize()));
        returnMap.put("最大线程数(maximumPoolSize)", String.valueOf(executor.getMaximumPoolSize()));
        returnMap.put("活跃线程数(activeCount)", String.valueOf(executor.getActiveCount()));
        returnMap.put("池中当前线程数(poolSize)", String.valueOf(executor.getPoolSize()));
        returnMap.put("历史最大线程数(largestPoolSize)", String.valueOf(executor.getLargestPoolSize()));
        returnMap.put("工作队列任务数量(workQueue.size)", String.valueOf(executor.getQueue().size()));
        returnMap.put("线程允许空闲时间/s(keepAliveTime)", String.valueOf(executor.getKeepAliveTime(TimeUnit.SECONDS)));
        returnMap.put("核心线程数是否允许被回收(allowCoreThreadTimeOut)", String.valueOf(executor.allowsCoreThreadTimeOut()));

        returnMap.put("历史执行任务的总数(近似值)(taskCount)", String.valueOf(executor.getTaskCount()));
        returnMap.put("历史完成任务的总数(近似值)(completedTaskCount)", String.valueOf(executor.getCompletedTaskCount()));
        returnMap.put("拒绝策略(ejectedExecutionHandler.class)", executor.getRejectedExecutionHandler().getClass().getCanonicalName());

        return returnMap;
    }

    /**
     * 通过 JMX 获取 Tomcat JDBC Pool 监控情况
     * JDBC Pool 必须先注册 MBeanServer
     * 一般来说 Tomcat 会自动注册但是我们现在手动使用 Pool，于是也得手动地注册到 MBean
     * <pre>{@code
     * try {
     *     MBeanServer server = ManagementFactory.getPlatformMBeanServer();
     *     ObjectName on = new ObjectName("org.apache.tomcat.jdbc.pool.jmx.ConnectionPool:type=Logging2");
     *     server.registerMBean(ds.getPool().getJmxPool(), on);
     * } catch (Throwable e) {
     *     System.err.println(e);
     * }
     * }</pre>
     */
    public static Map<String, Object> jdbcPoolStatus() {
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

    public final static Map<String, String> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("Name", "连接池的名称");
        FIELDS.put("PoolName", "连接池的名称");
        FIELDS.put("InitialSize", "初始连接数");
        FIELDS.put("Size", "当前连接池大小");
        FIELDS.put("Active", "当前正在使用的连接数");
        FIELDS.put("CreatedCount", "创建的连接数");
        FIELDS.put("BorrowedCount", "已借用的连接数");
        FIELDS.put("Idle", "空闲的连接数");
        FIELDS.put("MaxActive", "最大活跃连接数");
        FIELDS.put("MaxAge", "连接的最大存活时间");
        FIELDS.put("MaxIdle", "最大空闲连接数");
        FIELDS.put("MaxWait", "获取连接的最大等待时间");
        FIELDS.put("MinIdle", "最小空闲连接数");
        FIELDS.put("NumActive", "当前活跃的连接数");
        FIELDS.put("NumIdle", "当前空闲的连接数");
        FIELDS.put("ReconnectedCount", "重连的次数");
        FIELDS.put("ReleasedCount", "已释放的连接数");
        FIELDS.put("ReleasedIdleCount", "已释放但仍然处于空闲状态的连接数");
        FIELDS.put("RemoveAbandoned", "是否移除被遗弃的连接");
        FIELDS.put("RemoveAbandonedCount", "移除的被遗弃连接数");
        FIELDS.put("RemoveAbandonedTimeout", "被遗弃连接的超时时间");
        FIELDS.put("ReturnedCount", "已返回的连接数");
        FIELDS.put("AbandonWhenPercentageFull", "当连接池使用率达到指定百分比时，是否放弃获取连接，默认为 0，表示不放弃");
        FIELDS.put("AccessToUnderlyingConnectionAllowed", "是否允许直接访问底层连接对象，默认为 true");
        FIELDS.put("AlternateUsernameAllowed", "是否允许使用替代用户名（alternative username）");
        FIELDS.put("CommitOnReturn", "返回连接时是否自动提交事务，默认为 false");
        FIELDS.put("DbProperties", "数据库连接属性");
        FIELDS.put("DefaultAutoCommit", "默认是否自动提交事务");
        FIELDS.put("DefaultTransactionIsolation", "默认事务隔离级别");
        FIELDS.put("DriverClassName", "数据库驱动类名");
        FIELDS.put("FairQueue", "是否使用公平队列");
        FIELDS.put("IgnoreExceptionOnPreLoad", "在预加载时是否忽略异常");
        FIELDS.put("JdbcInterceptorsAsArray", "连接池拦截器定义的数组");
        FIELDS.put("LogAbandoned", "是否记录被遗弃的连接");
        FIELDS.put("LogValidationErrors", "是否记录验证错误");
        FIELDS.put("MinEvictableIdleTimeMillis", "连接在池中最小空闲时间，超过该时间将被清除");
        FIELDS.put("NumTestsPerEvictionRun", "每次空闲连接回收执行的测试数量");
        FIELDS.put("PoolSweeperEnabled", "是否开启连接池扫描器");
        FIELDS.put("PropagateInterruptState", "是否传播中断状态");
        FIELDS.put("RollbackOnReturn", "返回连接时是否回滚事务，默认为 false");
        FIELDS.put("SuspectTimeout", "连接的可疑超时时间，用于检测连接是否超时");
        FIELDS.put("TestOnBorrow", "借用连接时是否执行测试");
        FIELDS.put("TestOnConnect", "连接时是否执行测试");
        FIELDS.put("TestOnReturn", "归还连接时是否执行测试");
        FIELDS.put("TestWhileIdle", "连接处于空闲状态时是否定期执行测试");
        FIELDS.put("TimeBetweenEvictionRunsMillis", "空闲连接检测线程运行的时间间隔");
        FIELDS.put("UseDisposableConnectionFacade", "是否使用可释放连接外观类");
        FIELDS.put("UseLock", "是否使用锁");
        FIELDS.put("UseStatementFacade", "是否使用可释放语句外观类");
        FIELDS.put("ValidationInterval", "验证连接的间隔时间");
        FIELDS.put("ValidationQueryTimeout", "验证连接的超时时间");
        FIELDS.put("WaitCount", "等待获取连接的数量");
    }
}
