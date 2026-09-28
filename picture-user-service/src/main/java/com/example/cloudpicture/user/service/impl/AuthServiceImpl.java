package com.example.cloudpicture.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.common.security.session.SessionStore;
import com.example.cloudpicture.user.dto.request.UserLoginRequest;
import com.example.cloudpicture.user.dto.request.UserRegisterRequest;
import com.example.cloudpicture.user.dto.response.LoginUserVO;
import com.example.cloudpicture.user.entity.User;
import com.example.cloudpicture.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.cloudpicture.user.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final SessionStore sessionStore;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserMapper userMapper, SessionStore sessionStore, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.sessionStore = sessionStore;
        this.passwordEncoder = passwordEncoder;
    }

    public Long register(UserRegisterRequest request) {
        if (!request.getUserPassword().equals(request.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        if (findByAccount(request.getUserAccount()) != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_CONFLICT, "账号已存在");
        }
        User user = new User();
        user.setUserAccount(request.getUserAccount());
        user.setUserPassword(passwordEncoder.encode(request.getUserPassword()));
        user.setUserName(StringUtils.hasText(request.getUserName())
                ? request.getUserName() : request.getUserAccount());
        user.setUserRole(CurrentUser.ROLE_USER);
        userMapper.insert(user);
        return user.getId();
    }

    public LoginUserVO login(UserLoginRequest request) {
        User user = findByAccount(request.getUserAccount());
        if (user == null || !passwordEncoder.matches(request.getUserPassword(), user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.NO_AUTH, "账号已被禁用");
        }
        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(user.getId());
        currentUser.setAccount(user.getUserAccount());
        currentUser.setName(user.getUserName());
        currentUser.setRole(user.getUserRole());
        sessionStore.create(currentUser);

        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setAccount(user.getUserAccount());
        loginUserVO.setName(user.getUserName());
        loginUserVO.setAvatar(user.getUserAvatar());
        loginUserVO.setRole(user.getUserRole());
        loginUserVO.setToken(currentUser.getToken());
        return loginUserVO;
    }

    public void logout() {
        sessionStore.remove(CurrentUser.get().getToken(), CurrentUser.get().getId());
    }

    private User findByAccount(String userAccount) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount));
    }
}
