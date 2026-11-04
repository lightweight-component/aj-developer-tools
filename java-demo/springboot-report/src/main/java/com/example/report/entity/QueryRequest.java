package com.example.report.entity;

import lombok.Data;

import java.util.List;

@Data
public class QueryRequest {
    private String tableName;
    private List<String> dimensions;
    private List<String> metrics;
    private List<FilterCondition> filters;
    private int limit = 100;
}