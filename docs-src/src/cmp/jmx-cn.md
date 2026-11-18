---
title: JMX 监控与依赖配置
subTitle: 采集容错、连接超时与 JDK 版本差异
description: 说明 JMX 采集容错和超时机制、JDK 17 与 JDK 8 的 Attach API 依赖差异，以及 jmxtools 和 Tomcat 远程管理组件的用途。
tags:
  - JMX
  - JVM 监控
  - JDK
  - Attach API
  - Tomcat
layout: layouts/docs-cn.njk
---

# JMX 监控与依赖配置

## 采集容错与超时

属性级读取失败时，属性响应保留展示文本，并通过 `value.error` 说明错误。overview 不将失败文本强转为数值或内存对象；缺失数值使用可空类型返回 `null`，避免将未知值当作 0。连接中断和超时属于传输错误，会结束本次采集并向上抛出，不会伪装成成功的监控结果。

普通 `TabularData` 保留每行的全部列；仅 `java.lang:type=Runtime` 的 `SystemProperties` 转换为前端使用的 `name/value` 列表。

本地 Attach/远程连接以及单次远程调用默认超时均为 10000 毫秒，可使用 JVM 系统属性配置：

```text
-Ddevtools.jmx.connectTimeoutMillis=10000
-Ddevtools.jmx.requestTimeoutMillis=10000
```

请求超时后，连接被标记为失效，不再复用，并异步关闭；后续需重新连接。超时限制针对一次连接过程或一次远程方法调用，不代表整个 overview 的总时长。

调用执行器最多使用 8 个线程、32 个排队任务；连接清理执行器最多使用 2 个线程、16 个排队任务，资源满时明确报错。中断和关闭不能保证强制终止所有第三方 JMX Provider 的底层网络调用，因此后台使用有界资源，避免超时后无限创建线程。对于不响应中断且关闭也无法完成的 Provider，可能需要等待底层网络恢复，不能把调用方超时等同于底层操作已经结束。

Tomcat 简单信息采集分别支持 Tomcat 自有线程池、JDK `ThreadPoolExecutor` 和 `StandardThreadExecutor`；尚未初始化或不提供统计能力的 Executor 返回状态说明，不进行不安全的类型转换。

## JDK 17

当前 `aj-dev-tools` 使用 Java 17 构建。`com.sun.tools.attach.VirtualMachine`、`VirtualMachineDescriptor` 等 Attach API 由 JDK 的 `jdk.attach` 模块提供，无需添加 `com.sun:tools` 或引用 JDK 8 的 `tools.jar`。

请使用包含 `jdk.attach` 的完整 JDK，并让 IDE 和 Maven 使用相同的 JDK。可通过 `mvn -version` 检查 Maven 实际使用的版本。

## JDK 8 的 tools.jar

JDK 8 下 Attach API 的包名不变，但类位于 JDK 安装目录的 `lib/tools.jar`，需要加入编译和运行类路径。

对于需要使用 JDK 8 构建的独立工程，可以在 `pom.xml` 的 `project` 下添加仅在 JDK 8 激活的 profile：

```xml
<profiles>
    <profile>
        <id>jdk8-attach</id>
        <activation>
            <jdk>[1.8,1.9)</jdk>
        </activation>
        <properties>
            <jdk8.tools.jar>${java.home}/../lib/tools.jar</jdk8.tools.jar>
        </properties>
        <dependencies>
            <dependency>
                <groupId>com.sun</groupId>
                <artifactId>tools</artifactId>
                <version>1.8</version>
                <scope>system</scope>
                <systemPath>${jdk8.tools.jar}</systemPath>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

标准 JDK 8 中，Maven 的 `${java.home}` 通常指向 JDK 内部的 `jre`，因此使用 `../lib/tools.jar`。若安装目录结构不同，可以显式指定绝对路径，无需设置环境变量：

```powershell
mvn "-Djdk8.tools.jar=C:/soft/dev/JDK/jdk1.8.0_181/lib/tools.jar" compile
```

此命令需由 JDK 8 运行 Maven，profile 才会自动激活。也可直接将 profile 中的属性值写成本机 JDK 8 的绝对路径，但该配置不便于跨机器使用。

注意：

- `systemPath` 最终必须解析为存在的绝对文件路径；版本号是该系统依赖的 Maven 标识，不会触发下载。
- `system` 依赖不通过仓库解析，也不会自动作为普通传递依赖提供给其他工程。
- 普通应用 JAR 不会自动包含 `tools.jar`，运行时需将它加入类路径；可执行 JAR 是否包含它取决于打包插件配置。
- 该 profile 只解决 JDK 8 的 Attach API 依赖，不会让 Java 17 工程自动兼容 Java 8。代码、字节码版本及 Spring 等依赖都需单独适配。
- JDK 17 程序监控 JDK 8 目标进程，不需要引入目标 JDK 的 `tools.jar`；跨版本 Attach 是否可用需实际验证。

## jmxtools 与 tools.jar 的区别

`com.sun.jdmk:jmxtools:1.2.1` 是另一个库，包含 `com.sun.jdmk.comm.HtmlAdaptorServer` 等类，不是 JDK Attach API 的来源，不能替代 `tools.jar`，也不是使用标准 JMX RMI 连接的必需依赖。

该库用于早期 JMX HTML 管理界面、通信和跟踪辅助功能。当前项目通过 Vue、Spring 接口及 JDK 标准 JMX API 实现监控，没有直接使用这些类，因此已移除 Maven 依赖及 `aj-dev-tools/jar/jmxtools-1.2.1.jar`。

原 POM 中“有版权，所以 Maven 仓库基本都下架了”的说法未经核实，已随依赖删除。有版权不代表禁止使用，具体使用和分发权限应以原始许可证为准；此处不对该版本的许可及下架原因作结论。

以下仅作历史备忘：如果将来确实需要该库，应先取得来源及许可明确的 JAR，再安装到本地 Maven 仓库。命令中的 JAR 路径为示例，当前项目已不保留该文件：

```powershell
mvn install:install-file "-Dfile=jar/jmxtools-1.2.1.jar" -DgroupId=com.sun.jdmk -DartifactId=jmxtools -Dversion=1.2.1 -Dpackaging=jar
```

然后按普通依赖声明，不再设置 `system` 和 `systemPath`：

```xml
<dependency>
    <groupId>com.sun.jdmk</groupId>
    <artifactId>jmxtools</artifactId>
    <version>1.2.1</version>
</dependency>
```

团队和 CI 使用时，可以按组织的制品及许可管理要求上传至私有 Maven 仓库，避免每台机器手动安装。安装到本地仓库并不代表公共 Maven 仓库能够下载该制品。

## Tomcat 的 catalina-jmx-remote

`org.apache.tomcat:tomcat-catalina-jmx-remote` 提供 Tomcat 服务端的 `org.apache.catalina.mbeans.JmxRemoteLifecycleListener`，不是通用 JMX 客户端库。当前 `TomcatJmx` 使用 JDK 标准的 `JMXConnector`、`MBeanServerConnection` 读取监控数据，没有使用这个 Listener，因此已从 `aj-dev-tools` 删除该依赖。

典型使用场景是：被监控的 Tomcat 位于防火墙或 NAT 后，需要通过该 Listener 固定 RMI 注册表端口与 RMI 服务端口。例如在支持该 Listener 的 Tomcat 版本中，配置 `server.xml`：

```xml
<Listener
    className="org.apache.catalina.mbeans.JmxRemoteLifecycleListener"
    rmiRegistryPortPlatform="10001"
    rmiServerPortPlatform="10002" />
```

只有选择这种服务端配置方式时，才需要在目标 Tomcat 的类路径（通常为其 `lib` 目录）提供对应 JAR；应核对目标 Tomcat 版本是否支持该 Listener，并使用匹配的组件版本，不应固定套用旧版 `8.5.75`。如果代码直接创建该 Listener，也需要相应的编译依赖。

普通 JMX 客户端连接、同 JVM 查询 MBeans，以及通过 JDK 的 JMX 启动参数开启远程管理，都不要求该包。固定 RMI 端口也可通过 JDK 启动参数实现，并非必须使用这个 Listener。远程部署还需单独配置可达的主机地址、端口映射、认证和 TLS；以上片段仅说明 Listener 的用途。
