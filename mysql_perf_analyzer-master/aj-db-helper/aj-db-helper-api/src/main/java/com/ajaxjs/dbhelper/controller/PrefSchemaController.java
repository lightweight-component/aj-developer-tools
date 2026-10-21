package com.ajaxjs.dbhelper.controller;

import com.ajaxjs.spring.annotation.BizAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pref_schema")
public interface PrefSchemaController {
    /**
     * Display MySQL metadata locks
     *
     * @return
     */
    @GetMapping("/metadata_lock")
    @BizAction("Display MySQL metadata locks")
    List<Map<String, Object>> metaDataLock();

    /**
     * Display MySQL global memory allocation by event name
     *
     * @return
     */
    @GetMapping("/memory_global")
    @BizAction("Display MySQL global memory allocation by event name")
    List<Map<String, Object>> memoryGlobal();

    /**
     * Display MySQL global memory allocation by event name and thread
     *
     * @param threadId
     * @return
     */
    @GetMapping("/memory_thread")
    @BizAction("Display MySQL global memory allocation by event name and thread")
    List<Map<String, Object>> memoryThread(@RequestParam(required = false) String threadId);

    /**
     * Display MySQL sql digest summary
     *
     * @return
     */
    @GetMapping("/top_queries")
    @BizAction("Display MySQL sql digest summary")
    List<Map<String, Object>> topQueries(
            @RequestParam String by,
            @RequestParam(required = false, defaultValue = "1") int backToMins,
            @RequestParam(required = false, defaultValue = "5") int count);

    /**
     * 查询单个 SQL 摘要的累计指标，前端负责保存基准及计算变化。
     *
     * @param schemaName 数据库名称，空字符串表示未指定默认数据库（SCHEMA_NAME 为 NULL）
     * @param digest 32 或 64 位十六进制摘要标识，不支持空 Digest 汇总桶
     * @return 单条摘要记录；摘要不存在时返回 null
     */
    @GetMapping("/top_queries/changes")
    @BizAction("Display MySQL sql digest summary")
    Map<String, Object> topQueriesChanges(@RequestParam String schemaName,
                                                @RequestParam String digest);
}
