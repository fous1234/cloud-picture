package com.example.cloudpicture.user.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.user.dto.request.UserLoginRequest;
import com.example.cloudpicture.user.dto.request.UserRegisterRequest;
import com.example.cloudpicture.user.dto.response.LoginUserVO;
import com.example.cloudpicture.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Long> register(@Valid @RequestBody UserRegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginUserVO> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        authService.logout();
        return ApiResponse.success(true);
    }
}