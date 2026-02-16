package com.ajaxjs.devtools.springmonitor.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Abstract Event regarding registered instances
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class InstanceEvent {
    private String instance;

    private long version;

    private String type;

    private Instant timestamp;
}
