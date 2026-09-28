package com.example.cloudpicture.user.service;

import com.example.cloudpicture.user.dto.request.UserLoginRequest;
import com.example.cloudpicture.user.dto.request.UserRegisterRequest;
import com.example.cloudpicture.user.dto.response.LoginUserVO;

public interface AuthService {

    Long register(UserRegisterRequest request);

    LoginUserVO login(UserLoginRequest request);

    void logout();
}
