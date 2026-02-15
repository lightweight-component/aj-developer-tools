package com.ajaxjs.framework.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PageVO<T> {
    T data;
    int total;
}
