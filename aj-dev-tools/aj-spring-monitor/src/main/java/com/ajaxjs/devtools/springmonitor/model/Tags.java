package com.ajaxjs.devtools.springmonitor.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;

import static java.util.stream.Collectors.toMap;

@EqualsAndHashCode
@ToString
public class Tags {
    private final Map<String, String> values;
    private static final Tags EMPTY = new Tags(Collections.emptyMap());

    private Tags(Map<String, String> tags) {
        if (tags.isEmpty()) {
            this.values = Collections.emptyMap();
        } else {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        }
    }

    @JsonAnyGetter
    public Map<String, String> getValues() {
        return values;
    }

    public Tags append(Tags other) {
        Map<String, String> newTags = new LinkedHashMap<>(values);
        newTags.putAll(other.values);

        return new Tags(newTags);
    }

    public static Tags empty() {
        return EMPTY;
    }

    public static Tags from(Map<String, ?> map) {
        return from(map, null);
    }

    @SuppressWarnings("unchecked")
    public static Tags from(Map<String, ?> map, @Nullable String prefix) {
        if (map.isEmpty())
            return empty();

        if (StringUtils.hasText(prefix)) {
            Object nestedTags = map.get(prefix);

            if (nestedTags instanceof Map)
                return from((Map<String, Object>) nestedTags);

            String flatPrefix = prefix + ".";

            return from(map.entrySet()
                    .stream()
                    .filter(e -> e.getKey().toLowerCase().startsWith(flatPrefix))
                    .collect(toLinkedHashMap(e -> e.getKey().substring(flatPrefix.length()), Map.Entry::getValue)));
        }

        return new Tags(map.entrySet()
                .stream()
                .collect(toLinkedHashMap(Map.Entry::getKey, e -> Objects.toString(e.getValue()))));
    }

    private static <T, K, U> Collector<T, ?, LinkedHashMap<K, U>> toLinkedHashMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper,
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                LinkedHashMap::new
        );
    }
}
