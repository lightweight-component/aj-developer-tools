package com.ajaxjs.devtools.simplesysinfo.model;

import lombok.Data;

@Data
public class LinuxMemInfo {
    /**
     * 总计
     */
    private String total;

    /**
     * 未使用
     */
    private String free;

    private String swapTotal;

    private String swapFree;
}
