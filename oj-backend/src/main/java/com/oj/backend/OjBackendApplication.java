package com.oj.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OjBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OjBackendApplication.class, args);
        System.out.println("Server started successfully!");
    }

}
