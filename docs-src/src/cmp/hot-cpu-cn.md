---
title: CPU 热点
subTitle: CPU 飙升排查：方法级采样火焰图
description: 基于 ThreadMXBean 定时采样线程调用栈，在 Spring Boot 应用中生成方法级火焰图，辅助定位 CPU 热点与性能瓶颈。
tags:
  - CPU 热点
  - 火焰图
  - 性能分析
layout: layouts/docs-cn.njk
---

# CPU 热点

<!-- https://juejin.cn/post/7543362371340091435 -->

在生产环境里，大家或多或少都遇到过这种场景：某个 Spring Boot 应用突然 CPU 飙升，打满服务器资源；监控报警狂响，业务接口开始超时；登录服务器，`top` 一看，只能看到是 `java` 进程在耗 CPU；再往下就蒙了 —— **到底是哪段代码在吃 CPU？** 如果你和我一样，不想每次都靠运维层面“重启大法”，而是希望快速定位到 **具体的类和方法**，那你就需要一把更锋利的武器。

本文要介绍的就是：如何在 SpringBoot 程序里，自制一个 **方法级采样火焰图工具**，三分钟锁定 CPU 热点。

![image.png](https://p9-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/32229416616640778f4c97f28271b031~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793826469&x-signature=2dfN9T1be29uXx2Mwd5jNWW9P6g%3D)

![image.png](https://p9-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/a8d3d2f5e53146089ac425a2f4402d9f~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793826469&x-signature=bvyfyVtuYhsawsEpwVLWozyFITA%3D)

![image.png](https://p9-xtjj-sign.byteimg.com/tos-cn-i-73owjymdk6/8326d8320f5e48fcae619a9380fb7ca4~tplv-73owjymdk6-jj-mark-v1:0:0:0:0:5o6Y6YeR5oqA5pyv56S-5Yy6IEAg6ZmI56u55LiD:q75.awebp?rk3s=f64ab15b&x-expires=1793826469&x-signature=uFmKkrAhVkkqPjC4wUQmKwp8oJo%3D)

## 痛点分析

常见的排查思路：

- `top`  **/**  `htop`：只能看到进程或线程 ID，定位不到代码。
- `jstack`：能 dump 出线程栈，但静态快照往往抓不到真正的热点方法（CPU 飙升的时候，线程可能在不停切换）。
- `async-profiler`  **、**  `arthas`：功能很强大，但对于一些没有安装权限的生产环境，落地成本比较高。

所以我的需求是：

- **轻量化**：不用复杂部署，应用自身就能带。
- **方法级采样**：不仅要看到类名，还要定位到具体的方法。
- **火焰图可视化**：一眼看出热点，而不是一堆堆日志。

## 思路设计

核心思路其实很简单：

1. 在应用运行时，定时对线程栈进行采样；
1. 把每次采样到的「调用栈」进行统计和合并；
1. 输出为 **火焰图格式数据**（一般是 Flame Graph 的 stack collapse 格式）；
1. 前端页面用现成的 JS 库（比如 d3-flame-graph）渲染成交互式图谱。

这样我们就能在浏览器里看到：**哪些方法被调用得最多，消耗了 CPU。**

## 技术选型

- **采样方式**：基于 JDK 自带的 `ThreadMXBean` + `getThreadInfo`，周期性拉取栈帧；
- **数据存储**：用内存里的 `ConcurrentHashMap` 统计调用栈频次；
- **可视化输出**：提供一个 `/flamegraph` HTTP 接口，返回 collapse 格式数据；
- **前端展示**：引入 `d3-flame-graph`绘制火焰图。
 

## 实战应用场景

1. CPU 突然飙高：马上打开 flamegraph 页面，一眼就能看到是 `XxxService.doQuery()` 占比较高。
1. 定时任务异常：有些死循环、重试逻辑，传统日志难发现，但火焰图能直接看出占比最高的方法。
1. 性能优化前后对比：采集一段时间的火焰图，优化后再对比，能量化效果。

## 总结

通过上面的方式，我们用 **极少的代码** 在 SpringBoot 程序里实现了一个轻量级的 **CPU 火焰图采样器**。

1. 不依赖第三方大工具，**随项目自带**
1. 方法级别可视化，能快速定位 CPU 热点；
1. 火焰图展示直观、体验好；

___

当然，这种方案有局限：

1. 采样频率过高会带来一定开销
1. 精度比不上专业的 async-profiler
1. 火焰图数据需要定期清理，避免内存膨胀。