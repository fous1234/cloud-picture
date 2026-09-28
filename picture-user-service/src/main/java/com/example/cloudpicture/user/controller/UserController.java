package com.example.cloudpicture.user.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.user.dto.request.UserUpdateRequest;
import com.example.cloudpicture.user.dto.response.UserBriefVO;
import com.example.cloudpicture.user.dto.response.UserVO;
import com.example.cloudpicture.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser() {
        return ApiResponse.success(userService.getCurrentUser());
    }

    @PutMapping("/me")
    public ApiResponse<Boolean> updateCurrentUser(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateCurrentUser(request));
    }

    /** 内部接口：图片服务批量查询上传者基本信息，供共享图库展示 */
    @GetMapping("/internal/batch")
    public ApiResponse<List<UserBriefVO>> listByIds(@RequestParam("ids") List<Long> ids) {
        return ApiResponse.success(userService.listBriefByIds(ids));
    }
}