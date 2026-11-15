package com.ajaxjs.devtools.sysmonitor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemMonitorMappingTest {
    @Test
    void registersAllControllerMethodsIncludingBaseInfoMethods() {
        try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.registerSingleton("systemMonitor", SystemMonitor.class);
            context.refresh();
            RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(context);
            mapping.afterPropertiesSet();

            Set<String> paths = mapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
            assertEquals(Set.of("/system_monitor/sys_info", "/system_monitor/jvm_info",
                    "/system_monitor/cpu_info", "/system_monitor/memory_info", "/system_monitor/disk_info",
                    "/system_monitor/net_io_info", "/system_monitor/computer_system", "/system_monitor/process_list"), paths);
        }
    }
}
