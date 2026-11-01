/**
 * 基于 Spring MVC 拦截器的 API 请求监控、分级时间桶统计与管理接口。
 * <p>启用项为 devtools.apidashboard.enable。请求由拦截器计时，存入当前 JVM 的注册表，
 * 控制器负责查询和清理，调度任务按方法闲置时长淘汰条目。
 * 累计统计与范围统计分别使用全量计数和单一粒度时间桶，不进行跨桶级重复累加。</p>
 * <p>耗时单位为毫秒，时间戳为 Unix 纪元毫秒，成功率使用百分数。
 * 读取不保证跨字段的强一致快照，范围查询按整桶近似匹配；不提供持久化和跨实例聚合。</p>
 *
 * @see com.ajaxjs.devtools.apidashboard.MetricsInterceptor
 * @see com.ajaxjs.devtools.apidashboard.MetricsRegistry
 * @see com.ajaxjs.devtools.apidashboard.model.HierarchicalMethodMetrics
 */
package com.ajaxjs.devtools.apidashboard;
