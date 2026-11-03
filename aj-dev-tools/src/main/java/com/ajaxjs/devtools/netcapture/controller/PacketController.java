package com.ajaxjs.devtools.netcapture.controller;

import com.ajaxjs.devtools.netcapture.model.PacketInfo;
import com.ajaxjs.devtools.netcapture.model.PacketInfoDto;
import com.ajaxjs.devtools.netcapture.service.PacketQueryService;
import com.ajaxjs.sqlman.page.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/packets")
@RequiredArgsConstructor
public class PacketController {
    private final PacketQueryService packetQueryService;

    @GetMapping
    public PageResult<PacketInfoDto> queryPackets(
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String destinationIp,
            @RequestParam(required = false) Integer sourcePort,
            @RequestParam(required = false) Integer destinationPort,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return packetQueryService.queryPackets(protocol, sourceIp, destinationIp, sourcePort, destinationPort, startTime, endTime, pageNo, pageSize);
    }

    @GetMapping("/recent")
    public List<PacketInfo> getRecentPackets(@RequestParam(defaultValue = "100") int limit) {
        return packetQueryService.getRecentPackets(limit);
    }

    @GetMapping("/{id}")
    public PacketInfo getPacketDetail(@PathVariable Long id) {
        return packetQueryService.getPacketDetail(id);
    }
}