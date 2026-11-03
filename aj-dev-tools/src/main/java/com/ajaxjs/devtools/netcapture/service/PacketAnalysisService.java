package com.ajaxjs.devtools.netcapture.service;
import com.ajaxjs.devtools.netcapture.repository.NetCaptureRepository;

import com.ajaxjs.devtools.netcapture.model.PacketInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PacketAnalysisService {
    private final NetCaptureRepository repository;

    private final ProtocolAnalyzer protocolAnalyzer;

    private final TrafficStatisticsService statisticsService;

    private final ThreadPoolTaskExecutor analysisTaskExecutor;

    public PacketAnalysisService(NetCaptureRepository repository,
                                 ProtocolAnalyzer protocolAnalyzer,
                                 TrafficStatisticsService statisticsService,
                                 @Qualifier("analysisTaskExecutor") ThreadPoolTaskExecutor analysisTaskExecutor) {
        this.repository = repository;
        this.protocolAnalyzer = protocolAnalyzer;
        this.statisticsService = statisticsService;
        this.analysisTaskExecutor = analysisTaskExecutor;
    }

    public void analyzePacket(PacketInfo packetInfo) {
        analysisTaskExecutor.execute(() -> {
            enhancePacketInfo(packetInfo);
            repository.insertPacket(packetInfo);
            statisticsService.updateStatistics(packetInfo);
        });
    }

    private void enhancePacketInfo(PacketInfo packetInfo) {
        if ("TCP".equals(packetInfo.getProtocol())) 
            protocolAnalyzer.analyzeTcpPacket(packetInfo);
         else if ("UDP".equals(packetInfo.getProtocol())) 
            protocolAnalyzer.analyzeUdpPacket(packetInfo);
    }
}