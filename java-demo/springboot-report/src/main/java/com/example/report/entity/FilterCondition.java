package com.example.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FilterCondition {
    private final String field;
    private final String operator;
    private final String value;
}