package com.ajaxjs.devtools.hotcpu;

import com.ajaxjs.framework.spring.PrintBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        PrintBanner.showOk("aj-devtools HotCpu");
    }
}
