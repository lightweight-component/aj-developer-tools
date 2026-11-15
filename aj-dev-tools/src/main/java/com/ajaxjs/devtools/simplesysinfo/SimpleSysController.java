package com.ajaxjs.devtools.simplesysinfo;

import com.ajaxjs.devtools.simplesysinfo.model.JvmInfo;
import com.ajaxjs.devtools.simplesysinfo.model.LinuxMemInfo;
import com.ajaxjs.devtools.simplesysinfo.model.SysInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys_monitor")
public class SimpleSysController {
    @GetMapping("/sys_info")
    public SysInfo getSys() {
        return BaseInfo.getSysInfo();
    }

    @GetMapping("/jvm_info")
    public JvmInfo getJvm() {
        return BaseInfo.getJvmInfo();
    }

    @GetMapping("/mem_info")
    public LinuxMemInfo getMem() {
        return LinuxInfo.getMemInfo();
    }

    @GetMapping("/cpu_usage")
    public float getCpuUsage() {
        return LinuxInfo.getCpuUsage() * 100;
    }
}
