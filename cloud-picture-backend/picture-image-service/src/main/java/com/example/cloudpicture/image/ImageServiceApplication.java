package com.example.cloudpicture.image;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.cloudpicture",
        exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.example.cloudpicture.image.mapper")
@EnableFeignClients(basePackages = "com.example.cloudpicture.image.client")
@EnableScheduling
public class ImageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageServiceApplication.class, args);
    }
}