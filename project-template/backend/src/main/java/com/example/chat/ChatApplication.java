package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring AI 聊天应用主类
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
        System.out.println("=================================");
        System.out.println("Spring AI Chat Backend Started!");
        System.out.println("API Base URL: http://localhost:8080/api/chat");
        System.out.println("Health Check: http://localhost:8080/api/chat/health");
        System.out.println("=================================");
    }
}
