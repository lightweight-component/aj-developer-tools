package com.example.report;

import com.example.report.entity.ColumnInfo;
import com.example.report.entity.QueryRequest;
import com.example.report.entity.TableInfo;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report")
public interface ReportController {
    @GetMapping("/databases")
    List<String> getDatabases();

    @GetMapping("/tables/{database}")
    public List<TableInfo> getTables(@PathVariable String database);

    @GetMapping("/columns/{database}/{table}")
    public List<ColumnInfo> getColumns(@PathVariable String database, @PathVariable String table);

    @PostMapping("/query/{database}")
    public List<Map<String, Object>> executeQuery(@PathVariable String database, @RequestBody QueryRequest request);
}