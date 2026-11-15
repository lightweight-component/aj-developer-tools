package com.ajaxjs.devtools.sysmonitor.linux;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/linux")
public class LinuxInfoController {
    @GetMapping("/cpu_usage")
    public float getCpuUsage() {
        return LinuxInfo.getCpuUsage() * 100;
    }

    @GetMapping("/mem_info")
    public LinuxMemInfo getMem() {
        return LinuxInfo.getMemInfo();
    }

}
