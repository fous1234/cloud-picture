package com.example.cloudpicture.user.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.security.annotation.RequireRole;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.user.dto.request.UserQueryRequest;
import com.example.cloudpicture.user.dto.request.UserStatusUpdateRequest;
import com.example.cloudpicture.user.dto.response.UserVO;
import com.example.cloudpicture.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/user")
@RequireRole(CurrentUser.ROLE_ADMIN)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/list")
    public ApiResponse<PageData<UserVO>> listUsers(@Valid UserQueryRequest request) {
        return ApiResponse.success(adminUserService.listUsers(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Boolean> updateStatus(@PathVariable("id") Long id,
                                             @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success(adminUserService.updateStatus(id, request.getStatus()));
    }
}