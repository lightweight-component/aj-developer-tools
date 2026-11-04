package com.example.report.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TableInfo {
    private final String tableName;

    private final String tableComment;

    private final int columnCount;
}