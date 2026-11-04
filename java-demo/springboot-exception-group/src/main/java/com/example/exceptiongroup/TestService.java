package com.example.exceptiongroup;

import lombok.extern.slf4j.Slf4j;
import com.example.exceptiongroup.fingerprint.TraceIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用于测试错误指纹功能的服务类
 */
@Service
@Slf4j
public class TestService {


    @Autowired
    private TraceIdGenerator traceIdGenerator;

    /**
     * 模拟用户服务中的空指针异常
     */
    public void simulateUserServiceError() {
        String traceId = traceIdGenerator.getCurrentTraceId();
        log.info("Starting user service operation with traceId: {}", traceId);

        try {
            // 模拟空指针异常
            String nullString = null;
            int length = nullString.length(); // 第45行
        } catch (Exception e) {
            log.error("Error in user service operation", e);
            throw e;
        }
    }

    /**
     * 模拟订单服务中的参数异常
     */
    public void simulateOrderServiceError() {
        String traceId = traceIdGenerator.getCurrentTraceId();
        log.info("Starting order calculation with traceId: {}", traceId);

        try {
            // 模拟参数异常
            calculateOrderTotal(-100); // 第59行
        } catch (Exception e) {
            log.error("Error in order calculation", e);
            throw e;
        }
    }

    private void calculateOrderTotal(double amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Order amount cannot be negative: " + amount);
    }

    /**
     * 模拟数据访问层的IO异常
     */
    public void simulateDataAccessError() {
        String traceId = traceIdGenerator.getCurrentTraceId();
        log.info("Starting data access operation with traceId: {}", traceId);

        try {
            // 模拟IO异常
            accessDatabase(); // 第77行
        } catch (Exception e) {
            log.error("Error in data access operation", e);
        }
    }

    private void accessDatabase() throws java.io.IOException {
        throw new java.io.IOException("Database connection failed - connection timeout");
    }
}