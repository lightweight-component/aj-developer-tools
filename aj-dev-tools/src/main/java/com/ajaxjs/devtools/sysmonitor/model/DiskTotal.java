package com.ajaxjs.devtools.sysmonitor.model;

import lombok.Data;

import java.util.List;

@Data
public class DiskTotal {
    private List<DiskInfo> diskInfoList;

    private long usableSpace;

    private long totalSpace;

    private double usedSize;

    /**
     * 统计所有磁盘的使用率
     */
    private double diskUsePercent;
}
