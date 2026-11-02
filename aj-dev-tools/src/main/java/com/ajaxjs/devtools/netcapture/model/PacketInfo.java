package com.ajaxjs.devtools.netcapture.model;

import com.ajaxjs.sqlman.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("packet_info")
public class PacketInfo {
    private Long id;

    private LocalDateTime captureTime;

    private String sourceIp;

    private String destinationIp;

    private Integer sourcePort;

    private Integer destinationPort;

    private String protocol;

    private Integer packetLength;

    private String payload;

    private String httpMethod;

    private String httpUrl;

    private String httpHeaders;

    private String httpBody;

    private Integer httpStatus;

    private Long tcpSeqNumber;

    private Long tcpAckNumber;

    private String tcpFlags;

    private String networkInterface;
}
