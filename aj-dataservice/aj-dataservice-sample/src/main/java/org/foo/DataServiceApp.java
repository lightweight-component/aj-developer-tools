package org.foo;

import com.ajaxjs.framework.spring.PrintBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(DataServiceApp.class, args);
        PrintBanner.showOk("AJ-Data Service");
    }
}
