package com.ajaxjs.devtools.springmonitor.event;

import com.ajaxjs.devtools.springmonitor.model.Registration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

/**
 * This event gets emitted when an instance is registered.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceRegisteredEvent extends InstanceEvent {
    private final Registration registration;

    public InstanceRegisteredEvent(String instance, long version, Registration registration) {
        this(instance, version, Instant.now(), registration);
    }

    public InstanceRegisteredEvent(String instance, long version, Instant timestamp, Registration registration) {
        super(instance, version, "REGISTERED", timestamp);
        this.registration = registration;
    }
}
