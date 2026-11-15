package com.ajaxjs.devtools.jmx;

import com.ajaxjs.devtools.jmx.model.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.management.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jvm")
public class JvmMonitorController {
    private final JmxService jmxService;

    private final MonitorDashboardService monitorDashboardService;

    public JvmMonitorController(JmxService jmxService, MonitorDashboardService monitorDashboardService) {
        this.jmxService = jmxService;
        this.monitorDashboardService = monitorDashboardService;
    }

    @GetMapping("/jdbc_pool_status")
    public Map<String, Object> jdbcPoolStatus() {
        return jmxService.jdbcPoolStatus();
    }

    @GetMapping("/dashboard/overview")
    public Overview overview() {
        return monitorDashboardService.overview();
    }

    @GetMapping("/localJvms")
    public List<Vm> getLocalJvmProcessList() {
        return jmxService.getLocalJvms();
    }

    @GetMapping("/attachLocalJvm")
    public Boolean attachLocalJvm(Integer pid) throws Exception {
        jmxService.attachLocalJvm(pid);

        return true;
    }

    @GetMapping("/attachRemoteJvm")
    public boolean attachRemoteJvm(String host, String port) throws IOException {
        jmxService.attachRemoteJvm(host, port);

        return true;
    }

    @GetMapping("/domains")
    public List<Node> getDomains() throws IOException {
        return jmxService.getDomains();
    }

    @GetMapping("/properties")
    public BeanInfo getPropertyList(String fullName) {
        return jmxService.getProperties(fullName);
    }

    @GetMapping("/run")
    public Boolean runMethod(String fullName, String methodName) throws ReflectionException, InstanceNotFoundException, MBeanException, IOException {
        jmxService.invokeMethod(fullName, methodName);

        return true;
    }
}
