package com.ajaxjs.dbhelper.controller;

import com.ajaxjs.dbhelper.model.ProfilingResult;
import com.ajaxjs.spring.annotation.BizAction;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 性能剖析控制器
 */
@RestController
@RequestMapping("/profiling")
public interface ProfilingController {
    /**
     * 只执行一次查询，合并返回会话统计与阶段分析。
     *
     * @param sql 待分析的 SELECT 查询
     * @return 两类分析结果及阶段分析的可用性说明
     */
    @PostMapping("/analyze")
    @BizAction("profiling_analyze")
    ProfilingResult analyze(@RequestParam String sql);

    /**
     * 实际执行 SELECT，返回同一连接上的会话状态增量及执行统计。
     *
     * @param sql 待分析的查询
     * @return 变量名称和值；数值使用字符串保留精度
     */
    @PostMapping("/session_stats")
    @BizAction("session_stats")
    List<Map<String, String>> sessionStats(@RequestParam String sql);

    /**
     * explain_plan
     *
     * @param sql SQL
     * @return explain_plan
     */
    @PostMapping("/explain_plan")
    @BizAction("explain_plan")
    List<Map<String, Object>> explainPlan(@RequestParam String sql);

    /**
     * explain_plan_json
     *
     * @param sql SQL
     * @return explain_plan
     */
    @PostMapping("/explain_plan_json")
    @BizAction("explain_plan_json")
    Map<String, Object> explainPlanJson(@RequestParam String sql);
}
