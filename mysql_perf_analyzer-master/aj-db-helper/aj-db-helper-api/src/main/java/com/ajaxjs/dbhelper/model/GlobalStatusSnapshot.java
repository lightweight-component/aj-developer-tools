package com.ajaxjs.dbhelper.model;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

/** 一次全局状态采样，计数器保留字符串形式，避免大整数精度丢失。 */
@Getter
@Setter
public class GlobalStatusSnapshot {
    /** 采样完成时间，单位为毫秒。 */
    private long sampledAt;
    /** 数据库已运行秒数，用于识别重启后的基准失效。 */
    private Long uptime;
    /** 统一为 VARIABLE_NAME、VARIABLE_VALUE 两列的指标。 */
    private List<Map<String, String>> variables;
}
