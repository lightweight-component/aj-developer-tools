package com.ajaxjs.devtools.springmonitor.event;

import com.ajaxjs.devtools.springmonitor.model.Endpoints;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

/**
 * This event gets emitted when all instance's endpoints are discovered.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceEndpointsDetectedEvent extends InstanceEvent {
    private final Endpoints endpoints;

    public InstanceEndpointsDetectedEvent(String instance, long version, Endpoints endpoints) {
        this(instance, version, Instant.now(), endpoints);
    }

    public InstanceEndpointsDetectedEvent(String instance, long version, Instant timestamp, Endpoints endpoints) {
        super(instance, version, "ENDPOINTS_DETECTED", timestamp);
        this.endpoints = endpoints;
    }
}
