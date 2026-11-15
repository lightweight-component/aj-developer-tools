package com.ajaxjs.devtools.jmx;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

@Slf4j
public class Utils {
    /**
     * 设置 Java Bean 的值
     *
     * @param bean      Bean 实体
     * @param fieldName 字段名
     * @param value     值
     */
    public static void setBeanValue(Object bean, String fieldName, Object value) {
        try {
            Field field = bean.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            if (value == null)
                return;

            Class<?> type = field.getType();

            if (value instanceof Number) {
                Number number = (Number) value;

                if (type == Long.class || type == long.class)
                    value = number.longValue();
                else if (type == Integer.class || type == int.class)
                    value = number.intValue();
                else if (type == Double.class || type == double.class)
                    value = number.doubleValue();
            }

            if (!type.isPrimitive() && !type.isInstance(value))
                return;

            field.set(bean, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // the exception here doesn't need to handle
            log.warn("访问字段时候 {} 失败", fieldName);
        } catch (NoSuchFieldException e) {
            log.warn("No Such Field: {}", fieldName);
        }
    }
}
