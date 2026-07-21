package com.safeops.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafeOpsBackendApplication {

    public static void main(String[] args) {
        // Try to load .env from the current directory, falling back to the parent directory if needed
        Dotenv dotenv = Dotenv.configure()
            .directory("./")
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

        if (dotenv.get("JWT_SECRET") == null) {
            dotenv = Dotenv.configure()
                .directory("../")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        }

        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        SpringApplication.run(SafeOpsBackendApplication.class, args);
    }
}
