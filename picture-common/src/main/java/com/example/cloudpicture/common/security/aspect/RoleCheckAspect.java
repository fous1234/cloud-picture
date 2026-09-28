package com.example.cloudpicture.common.security.aspect;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.annotation.RequireRole;
import com.example.cloudpicture.common.security.context.CurrentUser;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

/**
 * 角色校验切面：未登录 401，角色不匹配 403
 */
@Aspect
@Component
public class RoleCheckAspect {

    @Before("@annotation(com.example.cloudpicture.common.security.annotation.RequireRole)"
            + " || @within(com.example.cloudpicture.common.security.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        CurrentUser currentUser = CurrentUser.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录或会话已过期");
        }
        RequireRole requireRole = resolveAnnotation(joinPoint);
        if (requireRole != null && !requireRole.value().equals(currentUser.getRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限访问");
        }
    }

    private RequireRole resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequireRole requireRole = signature.getMethod().getAnnotation(RequireRole.class);
        if (requireRole != null) {
            return requireRole;
        }
        return AopUtils.getTargetClass(joinPoint.getTarget()).getAnnotation(RequireRole.class);
    }
}