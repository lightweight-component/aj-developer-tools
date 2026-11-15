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

        try {
            // Use Pcap4J's actual library lookup rather than a Windows-only System.loadLibrary check.
            checkNetworkInterfaces();
        } catch (LinkageError | RuntimeException e) {
            log.warn("抓包环境检查失败，应用继续启动，抓包功能暂不可用: {}", e.toString());
            log.debug("抓包环境检查失败详情", e);
        }

        log.info("系统环境检查完成");
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
