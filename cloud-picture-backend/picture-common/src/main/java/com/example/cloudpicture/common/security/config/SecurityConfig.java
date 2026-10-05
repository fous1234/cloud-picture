package com.example.cloudpicture.common.security.config;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.filter.SessionAuthenticationFilter;
import com.example.cloudpicture.common.security.session.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * Spring Security 登录认证：会话过滤器还原登录态，未登录 401、无权限 403 均返回统一 JSON；
 * 角色边界仍由 @RequireRole + AOP 切面检查
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final SessionStore sessionStore;
    private final List<String> publicRequests;
    /** 服务间内部接口路径（逗号分隔，走 X-Internal-Token 校验），默认仅用户服务内部接口 */
    private final String[] internalPathPatterns;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public SecurityConfig(SessionStore sessionStore,
                          @Value("${picture.auth.public-requests:}") List<String> publicRequests,
                          @Value("${picture.internal.paths:/user/internal/**}") List<String> internalPaths,
                          @Value("${picture.internal.token:}") String internalToken,
                          ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.publicRequests = publicRequests;
        this.internalPathPatterns = internalPaths == null ? new String[0]
                : internalPaths.stream().filter(StringUtils::hasText).toArray(String[]::new);
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll();
                    if (internalPathPatterns.length > 0) {
                        authorize.requestMatchers(internalPathPatterns).access(internalCallAuthorizationManager());
                    }
                    authorize.requestMatchers(buildPublicMatchers()).permitAll()
                            .anyRequest().authenticated();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticatedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(new SessionAuthenticationFilter(sessionStore),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 内部接口只认服务间令牌：未配置令牌或请求头不匹配一律拒绝 */
    private AuthorizationManager<RequestAuthorizationContext> internalCallAuthorizationManager() {
        return (authentication, context) -> new AuthorizationDecision(
                StringUtils.hasText(internalToken)
                        && internalToken.equals(context.getRequest().getHeader(INTERNAL_TOKEN_HEADER)));
    }

    /** 白名单条目格式为 "METHOD /path" */
    private RequestMatcher[] buildPublicMatchers() {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return publicRequests.stream()
                .filter(StringUtils::hasText)
                .map(entry -> entry.trim().split("\\s+", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> (RequestMatcher) request -> parts[0].equalsIgnoreCase(request.getMethod())
                        && pathMatcher.match(parts[1], request.getRequestURI()))
                .toArray(RequestMatcher[]::new);
    }

    private AuthenticationEntryPoint unauthenticatedEntryPoint() {
        return (request, response, exception) -> writeError(response, ErrorCode.NOT_LOGIN, "未登录或会话已过期");
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, exception) -> writeError(response, ErrorCode.NO_AUTH, "无权限访问");
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, message)));
    }
}