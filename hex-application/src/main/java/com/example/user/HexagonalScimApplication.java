package com.example.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.user")
public class HexagonalScimApplication {
    public static void main(String[] args) {
        SpringApplication.run(HexagonalScimApplication.class, args);
    }
}

