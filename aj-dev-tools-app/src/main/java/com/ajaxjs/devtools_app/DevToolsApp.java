package com.ajaxjs.devtools_app;

import com.ajaxjs.framework.spring.PrintBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.ajaxjs.devtools_app")
public class DevToolsApp {
    public static void main(String[] args) {
        SpringApplication.run(DevToolsApp.class, args);
        PrintBanner.showOk("AJ Developer Tools");
    }
}
