package com.ajaxjs.dbhelper.model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 同一次查询执行得到的会话统计及阶段分析。 */
@Getter
@Setter
public class ProfilingResult {
    /** 会话计数器增量和客户端计时，数值以字符串保留精度。 */
    private List<Map<String, String>> sessionStats = new ArrayList<>();
    /** 按执行顺序排列的阶段；阶段名称允许重复，耗时单位为秒。 */
    private List<Map<String, String>> profileInfo = new ArrayList<>();
    /** 本次是否成功取得阶段分析，失败时仍可返回会话统计。 */
    private boolean profileAvailable;
    /** 不支持、不可用或未取得目标查询资料时的说明。 */
    private String profileMessage;
}
