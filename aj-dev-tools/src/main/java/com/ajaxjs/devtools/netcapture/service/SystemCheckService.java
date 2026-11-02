package com.ajaxjs.devtools.netcapture.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.PcapNetworkInterface;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemCheckService {
    private final PacketCaptureService packetCaptureService;

    @EventListener(ApplicationReadyEvent.class)
    public void performSystemCheck() {
        log.info("开始系统环境检查...");

        checkNativeLibrary();
        checkNetworkInterfaces();

        log.info("系统环境检查完成");
    }

    private void checkNativeLibrary() {
        try {
            // 尝试加载本地库
            System.loadLibrary("wpcap");
            log.info("✅ WinPcap/Npcap 本地库加载成功");
        } catch (UnsatisfiedLinkError e) {
            log.warn("⚠️ WinPcap/Npcap 本地库未找到");
            log.warn("请安装 Npcap: https://npcap.com/");
            log.warn("或者使用管理员权限运行应用程序");
        }
    }

    private void checkNetworkInterfaces() {
        List<PcapNetworkInterface> interfaces = packetCaptureService.getAvailableNetworkInterfaces();

        if (interfaces.isEmpty()) 
            log.warn("⚠️ 没有找到可用的网络接口");
         else {
            log.info("✅ 找到 {} 个网络接口", interfaces.size());
            interfaces.forEach(iface -> log.debug("网络接口: {} - {}", iface.getName(), iface.getDescription()));
        }
    }
}