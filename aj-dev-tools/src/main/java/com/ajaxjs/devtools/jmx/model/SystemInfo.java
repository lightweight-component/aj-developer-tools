package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * SystemInfo
 */
@Data
public class SystemInfo {
//    private String objectName;

    /**
     * 操作系统打开的文件描述符数量
     */
    private Long openFileDescriptorCount;

    /**
     * 操作系统最大文件描述符数量
     */
    private Long maxFileDescriptorCount;

    /**
     * Returns the amount of virtual memory that is guaranteed to be available to the running process in bytes
     * , or -1 if this operation is not supported
     */
    private Long committedVirtualMemorySize;

    /**
     * Returns the total amount of swap space in bytes
     */
    private Long totalSwapSpaceSize;

    /**
     * Returns the amount of free swap space in bytes
     */
    private Long freeSwapSpaceSize;

    /**
     * Returns the amount of free physical memory in bytes
     */
    private Long freePhysicalMemorySize;

    /**
     * Returns the total amount of physical memory in bytes
     */
    private Long totalPhysicalMemorySize;

    /**
     * Returns the CPU time used by the process on which the Java virtual machine is running in nanoseconds
     */
    private Long processCpuTime;

    /**
     * returns the CPU usage of the whole system
     */
    private Double systemCpuLoad;

    /**
     * returns the CPU usage of the JVM
     */
    private Double processCpuLoad;

    /**
     * Returns the operating system name.
     * This method is equivalent to System.getProperty("os.name")
     */
    private String name;

    /**
     * Returns the number of processors available to the Java virtual machine
     */
    private Integer availableProcessors;

    /**
     * Returns the operating system architecture.
     * This method is equivalent to <tt>System.getProperty("os.arch")</tt>.
     */
    private String arch;
    /**
     * Returns the operating system version.
     * This method is equivalent to <tt>System.getProperty("os.version")</tt>.
     */
    private String version;

    /**
     * Returns the system load average for the last minute.
     */
    private Double systemLoadAverage;


}
