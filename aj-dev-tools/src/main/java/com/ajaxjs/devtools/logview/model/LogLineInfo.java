package com.ajaxjs.devtools.logview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 日志行信息类
 */
@Data
@AllArgsConstructor
public class LogLineInfo {
    private LocalDateTime timestamp;
    private String level;
    private String content;
    private String originalLine;

    public boolean matchesFilter(LogQueryRequest request) {
        // 时间范围过滤
        if (timestamp != null) {
            if (request.getStartTime() != null && timestamp.isBefore(request.getStartTime()))
                return false;

            if (request.getEndTime() != null && timestamp.isAfter(request.getEndTime()))
                return false;
        }

        // 日志级别过滤
        if (StringUtils.hasText(request.getLevel()) && !level.equalsIgnoreCase(request.getLevel()))
            return false;

        // 关键字过滤
        return !StringUtils.hasText(request.getKeyword()) || originalLine.toLowerCase().contains(request.getKeyword().toLowerCase());
    }
}
