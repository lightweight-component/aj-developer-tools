package com.ajaxjs.devtools.sysmonitor.model;

import lombok.Data;

/**
 * 磁盘信息
 */
@Data
public class DiskInfo {
    /**
     * 名称
     */
    private String name;

    /**
     * 文件系统的卷名
     */
    private String volume;

    /**
     * 标签
     */
    private String label;

    /**
     * 文件系统的逻辑卷名
     */
    private String logicalVolume;

    /**
     * 文件系统的挂载点
     */
    private String mount;

    /**
     * 文件系统的描述
     */
    private String description;

    /**
     * 文件系统的选项
     */
    private String options;

    /**
     * 文件系统的类型（FAT、NTFS、etx2、ext4等）
     */
    private String type;

    /**
     * UUID/GUID
     */
    private String UUID;

    /**
     * 分区大小
     */
    private String size;

    private Long totalSpace;

    /**
     * 已使用
     */
    private String used;

    private Long usableSpace;

    /**
     * 可用
     */
    private String avail;

    /**
     * 已使用百分比
     */
    private double usePercent;
}
