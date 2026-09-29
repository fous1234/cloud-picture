package com.example.cloudpicture.gateway.filter;

import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 内部接口不对公网开放：命中内部路径一律 403（服务间调用走 Nacos 直连，不经过网关） */
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_PATH_PREFIX = "/api/user/internal/";
    private static final byte[] FORBIDDEN_BODY =
            "{\"code\":\"NO_AUTH\",\"message\":\"无权限访问\",\"data\":null}".getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getURI().getPath().startsWith(INTERNAL_PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(FORBIDDEN_BODY)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}