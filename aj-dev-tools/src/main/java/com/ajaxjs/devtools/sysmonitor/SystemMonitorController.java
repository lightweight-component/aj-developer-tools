package com.ajaxjs.devtools.sysmonitor;

import com.ajaxjs.devtools.sysmonitor.model.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统信息
 */
@RestController
@RequestMapping("/system_monitor")
public interface SystemMonitorController {
    /**
     * 获取系统信息
     *
     * @return 系统信息
     */
    @GetMapping("/sys_info")
    SysInfo getSysInfo();

    /**
     * 获取 JVM 虚拟机信息
     *
     * @return JVM 虚拟机信息
     */
    @GetMapping("/jvm_info")
    JvmInfo getJvmInfo();

    /**
     * 获取 CPU 信息
     *
     * @return CPU 信息
     */
    @GetMapping("/cpu_info")
    CpuInfo getCpuInfo();

    /**
     * 获取内存信息
     *
     * @return 内存信息
     */
    @GetMapping("/memory_info")
    MemoryInfo getMemoryInfo();

    /**
     * 磁盘信息
     */
    @GetMapping("/disk_info")
    DiskTotal disk();

    /**
     * 网络收发速率，采样约需 3 秒。
     */
    @GetMapping("/net_io_info")
    NetIoInfo getNetIoInfo();

    /**
     * 计算机硬件、主板和固件信息。
     */
    @GetMapping("/computer_system")
    Map<String, Object> getComputerSystem();

    /**
     * 按累计平均 CPU 使用率降序返回前 10 个进程。
     */
    @GetMapping("/process_list")
    List<Map<String, Object>> getProcessList();
}
