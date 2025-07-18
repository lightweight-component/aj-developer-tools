package com.ajaxjs.devtools.springmonitor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * Instance status with details fetched from the info endpoint.
 */
@Data
public class StatusInfo {
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_RESTRICTED = "RESTRICTED";
    private static final List<String> STATUS_ORDER = asList(STATUS_DOWN, STATUS_OUT_OF_SERVICE, STATUS_OFFLINE, STATUS_UNKNOWN, STATUS_RESTRICTED, STATUS_UP);
    private final String status;
    private final Map<String, Object> details;

    private StatusInfo(String status, @Nullable Map<String, ?> details) {
        Assert.hasText(status, "'status' must not be empty.");

        this.status = status.toUpperCase();
        this.details = details != null ? new HashMap<>(details) : Collections.emptyMap();
    }

    public static StatusInfo valueOf(String statusCode, @Nullable Map<String, ?> details) {
        return new StatusInfo(statusCode, details);
    }

    public static StatusInfo valueOf(String statusCode) {
        return valueOf(statusCode, null);
    }

    public static StatusInfo ofUnknown() {
        return valueOf(STATUS_UNKNOWN, null);
    }

    public static StatusInfo ofUp() {
        return ofUp(null);
    }

    public static StatusInfo ofDown() {
        return ofDown(null);
    }

    public static StatusInfo ofOffline() {
        return ofOffline(null);
    }

    public static StatusInfo ofUp(@Nullable Map<String, Object> details) {
        return valueOf(STATUS_UP, details);
    }

    public static StatusInfo ofDown(@Nullable Map<String, Object> details) {
        return valueOf(STATUS_DOWN, details);
    }

    public static StatusInfo ofOffline(@Nullable Map<String, Object> details) {
        return valueOf(STATUS_OFFLINE, details);
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    @JsonIgnore
    public boolean isUp() {
        return STATUS_UP.equals(status);
    }

    @JsonIgnore
    public boolean isOffline() {
        return STATUS_OFFLINE.equals(status);
    }

    @JsonIgnore
    public boolean isDown() {
        return STATUS_DOWN.equals(status);
    }

    @JsonIgnore
    public boolean isUnknown() {
        return STATUS_UNKNOWN.equals(status);
    }

    public static Comparator<String> severity() {
        return Comparator.comparingInt(STATUS_ORDER::indexOf);
    }

    @SuppressWarnings("unchecked")
    public static StatusInfo from(Map<String, ?> body) {
        return StatusInfo.valueOf((String) (body).get("status"), (Map<String, ?>) body.get("details"));
    }
}
