package com.ajaxjs.devtools.memviz.model.vo;

import lombok.Data;

/**
 * 快照响应对象
 * 用于返回生成的 HPROF 快照文件路径
 */
@Data
public class SnapshotResponse {
    /**
     * HPROF 快照文件的绝对路径
     */
    private String file;
}
