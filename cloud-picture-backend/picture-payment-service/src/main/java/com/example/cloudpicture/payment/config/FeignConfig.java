package com.example.cloudpicture.payment.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /** 服务间调用统一携带内部令牌，image-service 据此放行 /space/internal/** */
    @Bean
    public RequestInterceptor internalTokenInterceptor(@Value("${picture.internal.token:}") String internalToken) {
        return template -> template.header("X-Internal-Token", internalToken);
    }
}