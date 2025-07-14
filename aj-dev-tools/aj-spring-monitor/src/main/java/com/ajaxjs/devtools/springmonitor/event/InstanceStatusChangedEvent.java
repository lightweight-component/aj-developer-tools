package com.ajaxjs.devtools.springmonitor.event;

import com.ajaxjs.devtools.springmonitor.model.StatusInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

/**
 * This event gets emitted when an instance changes its status.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceStatusChangedEvent extends InstanceEvent {
    private final StatusInfo statusInfo;

    public InstanceStatusChangedEvent(String instance, long version, StatusInfo statusInfo) {
        this(instance, version, Instant.now(), statusInfo);
    }

    public InstanceStatusChangedEvent(String instance, long version, Instant timestamp, StatusInfo statusInfo) {
        super(instance, version, "STATUS_CHANGED", timestamp);
        this.statusInfo = statusInfo;
    }
}
