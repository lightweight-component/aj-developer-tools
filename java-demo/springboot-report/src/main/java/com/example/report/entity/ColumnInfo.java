package com.example.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ColumnInfo {
    private final String columnName;
    private final String dataType;
    private final String columnComment;
    private final boolean isNullable;
    private final String columnType;
}