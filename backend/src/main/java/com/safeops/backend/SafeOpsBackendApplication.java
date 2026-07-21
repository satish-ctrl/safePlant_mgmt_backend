package com.safeops.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafeOpsBackendApplication {

    public static void main(String[] args) {
        // Find the .env file path safely to avoid directories triggering IOException
        java.nio.file.Path envPath = java.nio.file.Paths.get("./.env");
        String dotenvDir = null;

        if (java.nio.file.Files.isRegularFile(envPath)) {
            dotenvDir = "./";
        } else {
            envPath = java.nio.file.Paths.get("../.env");
            if (java.nio.file.Files.isRegularFile(envPath)) {
                dotenvDir = "../";
            }
        }

        if (dotenvDir != null) {
            Dotenv dotenv = Dotenv.configure()
                .directory(dotenvDir)
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

            dotenv.entries().forEach(entry -> {
                if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        }

        SpringApplication.run(SafeOpsBackendApplication.class, args);
    }
}
