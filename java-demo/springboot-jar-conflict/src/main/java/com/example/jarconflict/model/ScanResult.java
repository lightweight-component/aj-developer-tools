package com.example.jarconflict.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次扫描请求的完整响应。
 * <p>包含依赖清单、冲突明细、汇总、扫描模式和服务端耗时。耗时覆盖扫描或模拟数据构造、
 * 冲突检测和建议生成，不包含网络传输、前端渲染及随后构造汇总的时间。
 */
@Data
@NoArgsConstructor
public class ScanResult {
    /**
     * 扫描成功的依赖或模拟依赖。
     */
    private List<JarInfo> jars;
    /**
     * 已生成修复建议的冲突记录。
     */
    private List<ConflictInfo> conflicts;
    /**
     * 本次扫描的聚合统计。
     */
    private ScanSummary summary;
    /**
     * 扫描、检测和建议生成的耗时，单位为毫秒。
     */
    private long scanTimeMs;
    /**
     * 扫描来源标记，当前接口使用 auto 或 test。
     */
    private String scanMode;
}