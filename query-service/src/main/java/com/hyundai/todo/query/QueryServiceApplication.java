package com.hyundai.todo.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.hyundai.todo") // 공통 DTO를 찾기 위함
public class QueryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryServiceApplication.class, args);
    }
}