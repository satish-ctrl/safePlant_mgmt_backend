package com.safeops.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SafeOpsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeOpsBackendApplication.class, args);
    }
}
