package com.example.cloudpicture.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.cloudpicture",
        exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.example.cloudpicture.payment.mapper")
@EnableFeignClients(basePackages = "com.example.cloudpicture.payment.client")
@EnableScheduling
public class PicturePaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PicturePaymentServiceApplication.class, args);
    }
}