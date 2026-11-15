package com.ajaxjs.devtools.sysmonitor;

import org.junit.jupiter.api.Test;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemMonitorProcessTest {
    @Test
    void returnsLightweightSnapshotWithoutLosingLowCpuUsage() {
        OperatingSystem os = mock(OperatingSystem.class);
        SystemMonitor monitor = spy(new SystemMonitor());
        doReturn(os).when(monitor).getOperatingSystem();
        OSProcess process = mock(OSProcess.class);
        when(process.getName()).thenReturn("java");
        when(process.getProcessID()).thenReturn(42);
        when(process.getProcessCpuLoadCumulative()).thenReturn(0.00123);
        when(os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, 10)).thenReturn(List.of(process));

        assertEquals(List.of(Map.of("name", "java", "pid", 42, "cpu", 0.00123)), monitor.getProcessList());
        assertEquals(List.of("name:java PID: 42 CPU:0.123%"), monitor.getProcessList2());
        verify(process, never()).getCommandLine();
    }

    @Test
    void preservesEmptyQueryResults() {
        OperatingSystem os = mock(OperatingSystem.class);
        SystemMonitor monitor = spy(new SystemMonitor());
        doReturn(os).when(monitor).getOperatingSystem();
        when(os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, 10)).thenReturn(List.of());
        assertTrue(monitor.getProcessList().isEmpty());
        assertTrue(monitor.getProcessList2().isEmpty());
    }
}
