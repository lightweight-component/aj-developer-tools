package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.model.NullValue;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * 防火墙数据访问共用的结果转换和写入辅助方法。
 */
public abstract class FirewallRepositorySupport {
    protected static List<Map<String, Object>> normalizeKeys(List<Map<String, Object>> result) {
        if (result == null)
            return List.of();

        List<Map<String, Object>> rows = new ArrayList<>(result.size());
        for (Map<String, Object> row : result) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                    .toLowerCase(Locale.ROOT), value));
            rows.add(normalized);
        }
        return rows;
    }

    protected final int write(String sql, Object... args) {
        return new Action(sql).update(args).execute().getEffectedRows();
    }

    /**
     * 创建写入副本，将可支持的空字段转换为 SqlMan 的显式 NULL 标记。
     * Boolean、BigDecimal 等无标记类型继续采用 SqlMan 的 null 省略语义。
     */
    @SuppressWarnings("unchecked")
    protected static <T> T writeCopy(T source) {
        T copy = (T) BeanUtils.instantiateClass(source.getClass());
        BeanUtils.copyProperties(source, copy);
        BeanWrapperImpl bean = new BeanWrapperImpl(copy);

        for (var property : bean.getPropertyDescriptors()) {
            if (property.getWriteMethod() == null || property.getName().equals("id")
                    || property.getName().equals("createdTime") || property.getName().equals("updatedTime")
                    || bean.getPropertyValue(property.getName()) != null)
                continue;
            Class<?> type = property.getPropertyType();
            if (type == String.class)
                bean.setPropertyValue(property.getName(), NullValue.NULL_STRING);
            else if (type == Integer.class)
                bean.setPropertyValue(property.getName(), NullValue.NULL_INT);
            else if (type == Long.class)
                bean.setPropertyValue(property.getName(), NullValue.NULL_LONG);
            else if (type == LocalDateTime.class)
                bean.setPropertyValue(property.getName(), NullValue.NULL_LOCAL_DATE_TIME);
        }

        return copy;
    }

    protected final <T> int batchInsert(String prefix, String row, List<T> items, Function<T, Object[]> values) {
        if (items.isEmpty())
            return 0;

        List<Object> args = new ArrayList<>();
        for (T item : items)
            Collections.addAll(args, values.apply(item));

        return write(prefix + String.join(", ", Collections.nCopies(items.size(), row)), args.toArray());
    }

    protected final int updateIds(String prefix, List<Long> ids, Boolean enabled) {
        if (ids.isEmpty())
            return 0;

        List<Object> args = new ArrayList<>();
        args.add(enabled);
        args.addAll(ids);

        return write(prefix + "(" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")", args.toArray());
    }

}
