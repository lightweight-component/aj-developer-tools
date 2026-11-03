package com.ajaxjs.devtools.netcapture.controller;

import com.ajaxjs.devtools.netcapture.model.TrafficStatistics;
import com.ajaxjs.devtools.netcapture.service.TrafficStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    private final TrafficStatisticsService statisticsService;

    @GetMapping("/current")
    public Map<String, Object> getCurrentStatistics() {
        return statisticsService.getCurrentStatistics();
    }

    @GetMapping("/recent")
    public List<TrafficStatistics> getRecentStatistics(@RequestParam(defaultValue = "24") int hours, @RequestParam(defaultValue = "100") int limit) {
        return statisticsService.getRecentStatistics(hours, limit);
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> getTrafficTrend(@RequestParam(defaultValue = "24") int hours) {
        return statisticsService.getTrafficTrend(hours);
    }

    @GetMapping("/protocols")
    public List<Map<String, Object>> getProtocolStatistics(@RequestParam(defaultValue = "24") int hours) {
        return statisticsService.getProtocolStatistics(hours);
    }

    @GetMapping("/top-sources")
    public List<Map<String, Object>> getTopSourceIps(@RequestParam(defaultValue = "24") int hours) {
        return statisticsService.getTopSourceIps(hours);
    }

    @GetMapping("/top-destinations")
    public List<Map<String, Object>> getTopDestinationIps(@RequestParam(defaultValue = "24") int hours) {
        return statisticsService.getTopDestinationIps(hours);
    }
}