package com.example.cloudpicture.common.security.context;

import lombok.Data;

/**
 * 当前登录用户上下文，由 AuthInterceptor 在请求进入时写入、请求结束时清理
 */
@Data
public class CurrentUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private Long id;
    private String account;
    private String name;
    private String role;
    private String token;

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}