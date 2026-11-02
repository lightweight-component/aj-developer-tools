package com.ajaxjs.devtools.netcapture.model;

import com.ajaxjs.sqlman.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("traffic_statistics")
public class TrafficStatistics {
    private Long id;

    private LocalDateTime statisticsTime;

    private String timeWindow;

    private Long totalPackets;

    private Long totalBytes;

    private Long httpPackets;

    private Long tcpPackets;

    private Long udpPackets;

    private Long icmpPackets;

    private String topSourceIp;

    private String topDestinationIp;

    private Integer topSourcePort;

    private Integer topDestinationPort;

    private Double averagePacketSize;
}