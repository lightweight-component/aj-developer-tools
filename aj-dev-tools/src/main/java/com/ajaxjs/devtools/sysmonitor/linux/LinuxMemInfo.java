package com.ajaxjs.devtools.sysmonitor.linux;

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
