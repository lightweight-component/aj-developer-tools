/**
 * JVM 堆快照采集、HPROF 解析与内存可视化。
 * <p>控制器提供本地文件转储及图查询，解析服务生成按类聚合的大小统计、
 * 大实例字段明细和有限的类间引用关系。图是面向诊断的摘要，
 * 引用边采用候选采样，保留大小汇总可能重叠，不表示完整对象拓扑或精确去重内存。</p>
 * <p>通过 devtools.memviz.enable=true 启用组件；堆转储与解析均为同步操作。</p>
 *
 * @see com.ajaxjs.devtools.memviz.MemvizController
 * @see com.ajaxjs.devtools.memviz.HprofParseService
 */
package com.ajaxjs.devtools.memviz;
