package com.example.cloudpicture.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.example.cloudpicture",
        exclude = UserDetailsServiceAutoConfiguration.class)
public class PictureAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureAiServiceApplication.class, args);
    }
}