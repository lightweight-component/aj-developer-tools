package com.ajaxjs.devtools.memviz.model.vo;

import lombok.Data;

/**
 * 堆快照创建结果，向客户端返回服务端 HPROF 文件的绝对路径。
 * <p>不包含文件内容；Lombok 生成属性访问器及常规数据对象方法。</p>
 */
@Data
public class SnapshotResponse {
    /**
     * 服务端生成的 HPROF 文件绝对路径，供后续图模型查询使用。
     */
    private String file;
}
