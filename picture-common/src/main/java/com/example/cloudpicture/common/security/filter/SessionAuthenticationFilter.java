package com.example.cloudpicture.common.security.filter;

import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.common.security.session.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 Redis 会话还原登录态并写入 Spring Security 上下文，同时填充 CurrentUser
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final SessionStore sessionStore;

    public SessionAuthenticationFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            CurrentUser currentUser = sessionStore.get(resolveToken(request));
            if (currentUser != null) {
                sessionStore.refresh(currentUser.getToken(), currentUser.getId());
                CurrentUser.set(currentUser);
                Authentication authentication = new UsernamePasswordAuthenticationToken(currentUser, null,
                        List.of(new SimpleGrantedAuthority(ROLE_PREFIX + currentUser.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.startsWith(BEARER_PREFIX) ? value.substring(BEARER_PREFIX.length()) : value;
    }
}
