package com.example.dynamicrule.controller;

import com.example.dynamicrule.entity.Order;
import com.example.dynamicrule.entity.OrderProcessResult;
import com.example.dynamicrule.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/process")
    public OrderProcessResult processOrder(@RequestBody Order order) {
        return orderService.processOrder(order);
    }

    @PostMapping("/simulate")
    public OrderProcessResult simulateOrder(@RequestParam String userLevel, @RequestParam BigDecimal amount) {

        Order sampleOrder = orderService.createSampleOrder(userLevel, amount);
        return orderService.processOrder(sampleOrder);

    }
}