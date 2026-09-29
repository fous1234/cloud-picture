package com.example.cloudpicture.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.session.SessionStore;
import com.example.cloudpicture.user.dto.request.UserQueryRequest;
import com.example.cloudpicture.user.dto.response.UserVO;
import com.example.cloudpicture.user.entity.User;
import com.example.cloudpicture.user.mapper.UserMapper;
import java.util.List;
import com.example.cloudpicture.user.service.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int STATUS_DISABLED = 0;

    private final UserMapper userMapper;
    private final SessionStore sessionStore;

    public AdminUserServiceImpl(UserMapper userMapper, SessionStore sessionStore) {
        this.userMapper = userMapper;
        this.sessionStore = sessionStore;
    }

    public PageData<UserVO> listUsers(UserQueryRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(request.getAccount()), User::getUserAccount, request.getAccount())
                .like(StringUtils.hasText(request.getName()), User::getUserName, request.getName())
                .orderByDesc(User::getCreateTime);
        Page<User> page = userMapper.selectPage(Page.of(request.getCurrent(), request.getSize()), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserVO::from).toList();
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public boolean updateStatus(Long id, Integer status) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        boolean updated = userMapper.updateById(update) > 0;
        if (updated && status == STATUS_DISABLED) {
            // 禁用后立刻踢下线，避免旧 Token 继续可用
            sessionStore.removeAllByUserId(id);
        }
        return updated;
    }
}
