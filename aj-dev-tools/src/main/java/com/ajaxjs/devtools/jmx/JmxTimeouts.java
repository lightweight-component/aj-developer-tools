package com.ajaxjs.devtools.jmx;

import lombok.extern.slf4j.Slf4j;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 限制调用方等待连接及远程查询的时间。后台线程和队列均有上限，超时连接不再复用。
 * 部分 JMX Provider 不响应线程中断，因此连接关闭放在独立的有界线程池中执行。
 */
@Slf4j
public final class JmxTimeouts {
    private static final ThreadPoolExecutor CALLS = executor(8, 32, "jmx-call");
    private static final ThreadPoolExecutor CLEANUP = executor(2, 16, "jmx-close");

    private JmxTimeouts() {
    }

    private static ThreadPoolExecutor executor(int threads, int queueSize, String name) {
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueSize), task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public static JMXConnector connect(String url) throws IOException {
        return connect(() -> JMXConnectorFactory.connect(new JMXServiceURL(url)));
    }

    public static JMXConnector connect(Callable<JMXConnector> factory) throws IOException {
        return connect(factory,
                Long.getLong("devtools.jmx.connectTimeoutMillis", 10000L),
                Long.getLong("devtools.jmx.requestTimeoutMillis", 10000L));
    }

    static JMXConnector connect(Callable<JMXConnector> factory, long connectMillis, long requestMillis) throws IOException {
        if (connectMillis <= 0 || requestMillis <= 0)
            throw new IllegalArgumentException("JMX 超时时间必须大于 0");

        Session session = new Session(requestMillis);

        try {
            session.call(() -> {
                session.connector = factory.call();

                if (session.invalid.get())
                    session.closeAsync();

                return session.connector;
            }, connectMillis);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("建立 JMX 连接失败", e);
        }

        return (JMXConnector) Proxy.newProxyInstance(JmxTimeouts.class.getClassLoader(), new Class<?>[]{JMXConnector.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                        return objectMethod(proxy, method, args);

                    if ("close".equals(method.getName())) {
                        session.invalidate();
                        return null;
                    }

                    Object result = session.call(() -> invoke(session.connector, method, args), requestMillis);

                    if (result instanceof MBeanServerConnection)
                        return session.wrap((MBeanServerConnection) result);

                    return result;
                });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        if ("equals".equals(method.getName()))
            return proxy == args[0];

        if ("hashCode".equals(method.getName()))
            return System.identityHashCode(proxy);

        return "TimedJmxConnection";
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception)
                throw (Exception) e.getCause();

            if (e.getCause() instanceof Error)
                throw (Error) e.getCause();

            throw new IOException(e.getCause());
        }
    }

    private static final class Session {
        private final long timeoutMillis;
        private final AtomicBoolean invalid = new AtomicBoolean();
        private final AtomicBoolean closing = new AtomicBoolean();
        private volatile JMXConnector connector;

        private Session(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        private MBeanServerConnection wrap(MBeanServerConnection target) {
            return (MBeanServerConnection) Proxy.newProxyInstance(JmxTimeouts.class.getClassLoader(),
                    new Class<?>[]{MBeanServerConnection.class}, (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class)
                            return objectMethod(proxy, method, args);

                        return call(() -> invoke(target, method, args), timeoutMillis);
                    });
        }

        private <T> T call(Callable<T> action, long timeout) throws Exception {
            if (invalid.get())
                throw new IOException("JMX 连接已关闭或超时，请重新连接");

            Future<T> task;

            try {
                task = CALLS.submit(action);
            } catch (RejectedExecutionException e) {
                invalidate();
                throw new IOException("JMX 调用资源已满，请稍后重试", e);
            }

            try {
                return task.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                invalidate();
                task.cancel(true);
                CALLS.purge();
                SocketTimeoutException error = new SocketTimeoutException("JMX 调用超时（" + timeout + " ms），请重新连接");
                error.initCause(e);
                throw error;
            } catch (InterruptedException e) {
                invalidate();
                task.cancel(true);
                CALLS.purge();
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("JMX 调用已中断");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException)
                    invalidate();

                if (e.getCause() instanceof Exception)
                    throw (Exception) e.getCause();

                if (e.getCause() instanceof Error)
                    throw (Error) e.getCause();

                throw new IOException(e.getCause());
            }
        }

        private void invalidate() {
            invalid.set(true);
            closeAsync();
        }

        private void closeAsync() {
            JMXConnector value = connector;

            if (value == null || !closing.compareAndSet(false, true))
                return;

            try {
                CLEANUP.execute(() -> {
                    try {
                        value.close();
                    } catch (IOException e) {
                        log.warn("关闭 JMX 连接失败", e);
                    }
                });
            } catch (RejectedExecutionException e) {
                log.warn("JMX 连接清理队列已满", e);
            }
        }
    }
}
