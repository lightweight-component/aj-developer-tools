package com.ajaxjs.devtools.jmx.model;

import lombok.Data;

/**
 * BeanAttributeValue
 */
@Data
public class BeanAttributeValue {
    private boolean isCompositeData;

    private Object data;

    /** 属性级读取错误；失败值仅用于展示，不参与指标计算。 */
    private String error;
}
