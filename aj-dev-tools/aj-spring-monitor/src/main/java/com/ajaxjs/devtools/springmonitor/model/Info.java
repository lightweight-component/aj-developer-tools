package com.ajaxjs.devtools.springmonitor.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the info fetched from the info actuator endpoint
 */
@Data
public class Info {
    private static final Info EMPTY = new Info(Collections.emptyMap());

    private final Map<String, Object> values;

    private Info(Map<String, Object> values) {
        this.values = values.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Info from(@Nullable Map<String, Object> values) {
        if (values == null || values.isEmpty())
            return empty();

        return new Info(values);
    }

    public static Info empty() {
        return EMPTY;
    }

    @JsonAnyGetter
    public Map<String, Object> getValues() {
        return this.values;
    }
}
