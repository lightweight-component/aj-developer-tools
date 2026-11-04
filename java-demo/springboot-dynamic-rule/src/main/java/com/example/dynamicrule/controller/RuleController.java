package com.example.dynamicrule.controller;

import com.example.dynamicrule.entity.RuleExecuteRequest;
import com.example.dynamicrule.entity.RuleExecuteResponse;
import com.example.dynamicrule.entity.RuleScript;
import com.example.dynamicrule.service.DynamicRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {
    private final DynamicRuleEngine dynamicRuleEngine;

    @PostMapping
    public String addRule(@RequestBody RuleScript ruleScript) {
        try {
            dynamicRuleEngine.addRule(ruleScript);
            return "规则添加成功";
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "添加失败: " + e.getMessage(), e);
        }
    }

    @PutMapping("/{ruleName}")
    public String updateRule(@PathVariable String ruleName, @RequestBody RuleScript ruleScript) {
        try {
            dynamicRuleEngine.updateRule(ruleName, ruleScript);
            return "规则更新成功";
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "更新失败: " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/{ruleName}")
    public String deleteRule(@PathVariable String ruleName) {
        try {
            dynamicRuleEngine.deleteRule(ruleName);
            return "规则删除成功";
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "删除失败: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{ruleName}")
    public RuleScript getRule(@PathVariable String ruleName) {
        return dynamicRuleEngine.getRule(ruleName);
    }

    @GetMapping
    public List<RuleScript> getAllRules() {
        return dynamicRuleEngine.getAllRules();
    }

    @PostMapping("/execute")
    public RuleExecuteResponse executeRule(@RequestBody RuleExecuteRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getParams();

        return dynamicRuleEngine.executeRule(request.getRuleName(), params);
    }

    @PostMapping("/execute/{ruleName}")
    public RuleExecuteResponse executeRuleByName(
            @PathVariable String ruleName,
            @RequestBody Map<String, Object> params) {
        return dynamicRuleEngine.executeRule(ruleName, params);
    }
}