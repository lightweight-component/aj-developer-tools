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
        try {
            Map<String, Object> statistics = statisticsService.getCurrentStatistics();
            return statistics;
        } catch (Exception e) {
            log.error("Error getting current statistics", e);
            throw new IllegalStateException("Network capture request failed", e);
        }
    }

    @GetMapping("/recent")
    public List<TrafficStatistics> getRecentStatistics(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int limit) {
        List<TrafficStatistics> statistics = statisticsService.getRecentStatistics(hours, limit);
        return statistics;
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> getTrafficTrend(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<Map<String, Object>> trend = statisticsService.getTrafficTrend(hours);
            return trend;
        } catch (Exception e) {
            log.error("Error getting traffic trend", e);
            throw new IllegalStateException("Network capture request failed", e);
        }
    }

    @GetMapping("/protocols")
    public List<Map<String, Object>> getProtocolStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<Map<String, Object>> protocolStats = statisticsService.getProtocolStatistics(hours);
            return protocolStats;
        } catch (Exception e) {
            log.error("Error getting protocol statistics", e);
            throw new IllegalStateException("Network capture request failed", e);
        }
    }

    @GetMapping("/top-sources")
    public List<Map<String, Object>> getTopSourceIps(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<Map<String, Object>> topSources = statisticsService.getTopSourceIps(hours);
            return topSources;
        } catch (Exception e) {
            log.error("Error getting top source IPs", e);
            throw new IllegalStateException("Network capture request failed", e);
        }
    }

    @GetMapping("/top-destinations")
    public List<Map<String, Object>> getTopDestinationIps(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<Map<String, Object>> topDestinations = statisticsService.getTopDestinationIps(hours);
            return topDestinations;
        } catch (Exception e) {
            log.error("Error getting top destination IPs", e);
            throw new IllegalStateException("Network capture request failed", e);
        }
    }
}