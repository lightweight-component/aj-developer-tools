package com.ajaxjs.devtools.netcapture.service;

import com.ajaxjs.devtools.netcapture.model.PacketInfo;
import com.ajaxjs.devtools.netcapture.model.PacketInfoDto;
import com.ajaxjs.devtools.netcapture.repository.NetCaptureRepository;
import com.ajaxjs.sqlman.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PacketQueryService {
    private final NetCaptureRepository repository;

    public PageResult<PacketInfoDto> queryPackets(String protocol, String sourceIp, String destinationIp,
                                                  Integer sourcePort, Integer destinationPort,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  int pageNo, int pageSize) {
        PageResult<PacketInfo> result = repository.queryPackets(protocol, sourceIp, destinationIp,
                sourcePort, destinationPort, startTime, endTime, pageNo, pageSize);
        PageResult<PacketInfoDto> page = new PageResult<>();
        page.setStart(result.getStart());
        page.setPageSize(result.getPageSize());
        page.setTotalCount(result.getTotalCount());
        page.setTotalPage(result.getTotalPage());
        page.setCurrentPage(result.getCurrentPage());
        page.setZero(result.isZero());
        page.setList(result.getList().stream().map(this::convertToDto).collect(Collectors.toList()));

        return page;
    }

    public List<PacketInfo> getRecentPackets(int limit) {
        return repository.getRecentPackets(limit);
    }

    public PacketInfo getPacketDetail(Long id) {
        return repository.getPacketDetail(id);
    }

    private PacketInfoDto convertToDto(PacketInfo packet) {
        PacketInfoDto dto = new PacketInfoDto();
        dto.setId(packet.getId());
        dto.setCaptureTime(packet.getCaptureTime());
        dto.setSourceIp(packet.getSourceIp());
        dto.setDestinationIp(packet.getDestinationIp());
        dto.setSourcePort(packet.getSourcePort());
        dto.setDestinationPort(packet.getDestinationPort());
        dto.setProtocol(packet.getProtocol());
        dto.setPacketLength(packet.getPacketLength());
        dto.setHttpMethod(packet.getHttpMethod());
        dto.setHttpUrl(packet.getHttpUrl());
        dto.setHttpStatus(packet.getHttpStatus());
        dto.setTcpFlags(packet.getTcpFlags());
        dto.setNetworkInterface(packet.getNetworkInterface());

        dto.setSummary(generatePacketSummary(packet));

        return dto;
    }

    private String generatePacketSummary(PacketInfo packet) {
        StringBuilder summary = new StringBuilder();
        summary.append(packet.getProtocol());

        if (packet.getSourceIp() != null && packet.getDestinationIp() != null)
            summary.append(" ")
                    .append(packet.getSourceIp())
                    .append(":")
                    .append(packet.getSourcePort() != null ? packet.getSourcePort() : "")
                    .append(" -> ")
                    .append(packet.getDestinationIp())
                    .append(":")
                    .append(packet.getDestinationPort() != null ? packet.getDestinationPort() : "");

        if (packet.getHttpMethod() != null)
            summary.append(" [").append(packet.getHttpMethod()).append("]");

        if (packet.getHttpUrl() != null)
            summary.append(" ").append(packet.getHttpUrl());

        if (packet.getHttpStatus() != null)
            summary.append(" [").append(packet.getHttpStatus()).append("]");

        if (packet.getPacketLength() != null)
            summary.append(" (").append(packet.getPacketLength()).append(" bytes)");

        return summary.toString();
    }
}