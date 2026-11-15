package com.ajaxjs.devtools.sysmonitor.linux;

import com.ajaxjs.util.io.DataReader;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

/**
 * 利用 runtime.exec() 获取CPU利用率、内存使用情况及CPU温度
 *
 * @author <a href="https://blog.csdn.net/hj7jay/article/details/51979939">...</a>
 */
@Slf4j
public class LinuxInfo {
    /**
     * 内存的信息，单位 kb
     */
    public static LinuxMemInfo getMemInfo() {
        LinuxMemInfo info = new LinuxMemInfo();

        try {
            DataReader dataReader = new DataReader(getFileInputStream("/proc/meminfo"), StandardCharsets.UTF_8);
            dataReader.readAsLineString(line -> {
                StringTokenizer token = new StringTokenizer(line);

                if (!token.hasMoreTokens())
                    return;

                line = token.nextToken();

                if (!token.hasMoreTokens())
                    return;

                if (line.equalsIgnoreCase("MemTotal:"))
                    info.setTotal(token.nextToken());
                else if (line.equalsIgnoreCase("MemFree:"))
                    info.setFree(token.nextToken());
                else if (line.equalsIgnoreCase("SwapTotal:"))
                    info.setSwapTotal(token.nextToken());
                else if (line.equalsIgnoreCase("SwapFree:"))
                    info.setSwapFree(token.nextToken());
            });
        } catch (IOException e) {
            log.warn("Errors when getting memory info.", e);
            throw new UncheckedIOException(e);
        }

        return info;
    }

    private static InputStream getFileInputStream(String filePath) throws IOException {
        return Files.newInputStream(new File(filePath).toPath());
    }

    /**
     * 读取两次 /proc/stat 计算 CPU 使用率（0～1）。
     * idle 和 iowait 计为空闲；guest/guest_nice 已包含在 user/nice 中，不重复累计。
     * 读取失败、计数回退或采样无增量时明确报错，不返回伪造的 0%。
     */
    public static float getCpuUsage() {
        return getCpuUsage(Path.of("/proc/stat"), () -> sleep(1));
    }

    static float getCpuUsage(Path statFile, Runnable sampleInterval) {
        try {
            long[] before = readCpuTicks(statFile);
            sampleInterval.run();
            return calculateCpuUsage(before, readCpuTicks(statFile));
        } catch (IOException e) {
            log.warn("读取或计算 CPU 使用率失败", e);
            throw new UncheckedIOException("CPU 使用率采集失败: " + e.getMessage(), e);
        }
    }

    private static long[] readCpuTicks(Path statFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(statFile, StandardCharsets.UTF_8)) {
            return parseCpuTicks(reader.readLine());
        }
    }

    static long[] parseCpuTicks(String line) throws IOException {
        if (line == null)
            throw new IOException("CPU 统计文件为空");

        String[] fields = line.trim().split("\\s+");

        if (fields.length < 5 || !"cpu".equals(fields[0]))
            throw new IOException("CPU 汇总行缺失或字段不完整");

        long[] ticks = new long[8];

        try {
            for (int i = 0; i < ticks.length && i + 1 < fields.length; i++) {
                ticks[i] = Long.parseLong(fields[i + 1]);

                if (ticks[i] < 0)
                    throw new IOException("CPU 累计计数不能为负数");
            }
        } catch (NumberFormatException e) {
            throw new IOException("CPU 累计计数不是有效的 long 整数", e);
        }

        return ticks;
    }

    static float calculateCpuUsage(long[] before, long[] after) throws IOException {
        long total = 0, idle = 0;

        try {
            for (int i = 0; i < 8; i++) {
                if (before[i] < 0 || after[i] < before[i])
                    throw new IOException("CPU 计数发生回退，采样无效");

                long delta = after[i] - before[i];
                total = Math.addExact(total, delta);

                if (i == 3 || i == 4)
                    idle = Math.addExact(idle, delta);
            }
        } catch (ArithmeticException e) {
            throw new IOException("CPU 采样增量超出 long 范围", e);
        }

        if (total <= 0)
            throw new IOException("CPU 采样没有产生有效增量");

        return (float) (total - idle) / total;
    }

    public static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CPU 采样等待已中断", e);
        }
    }

    /**
     * Linux 磁盘IO统计
     */
    private static LinuxDiskIoStats getLinuxDiskIOStats() {
        String line;
        long readBytes = 0, writeBytes = 0, readOps = 0, writeOps = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/diskstats"))) {

            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");

                if (fields.length >= 14) {
                    readOps += Long.parseLong(fields[3]);
                    writeOps += Long.parseLong(fields[7]);
                    readBytes += Long.parseLong(fields[5]) * 512; // 扇区数 * 512字节
                    writeBytes += Long.parseLong(fields[9]) * 512;
                }
            }

            LinuxDiskIoStats linuxDiskIoStats = new LinuxDiskIoStats();
            linuxDiskIoStats.setReadBytes(readBytes);
            linuxDiskIoStats.setWriteBytes(writeBytes);
            linuxDiskIoStats.setReadOps(readOps);
            linuxDiskIoStats.setWriteOps(writeOps);

            return linuxDiskIoStats;
        } catch (Exception e) {
            log.warn("获取Linux磁盘IO统计失败", e);
            return null;
        }
    }

    /**
     * Linux 网络统计
     */
    private static LinuxNetworkStats geLinuxtLinuxNetworkStats() {
        String line;
        long rxBytes = 0, txBytes = 0, rxPackets = 0, txPackets = 0, rxErrors = 0, txErrors = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/dev"))) {
            reader.readLine(); // 跳过前两行头部
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split(":\\s+");

                if (fields.length == 2) {
                    String[] stats = fields[1].trim().split("\\s+");

                    if (stats.length >= 16) {
                        rxBytes += Long.parseLong(stats[0]);
                        rxPackets += Long.parseLong(stats[1]);
                        rxErrors += Long.parseLong(stats[2]);
                        txBytes += Long.parseLong(stats[8]);
                        txPackets += Long.parseLong(stats[9]);
                        txErrors += Long.parseLong(stats[10]);
                    }
                }
            }

            LinuxNetworkStats linuxNetworkStats = new LinuxNetworkStats();
            linuxNetworkStats.setRxBytes(rxBytes);
            linuxNetworkStats.setTxBytes(txBytes);
            linuxNetworkStats.setRxPackets(rxPackets);
            linuxNetworkStats.setTxPackets(txPackets);
            linuxNetworkStats.setRxErrors(rxErrors);
            linuxNetworkStats.setTxErrors(txErrors);

            return linuxNetworkStats;
        } catch (Exception e) {
            log.warn("获取Linux网络统计失败", e);
            return null;
        }
    }
}