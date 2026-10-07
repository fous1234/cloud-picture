package com.example.cloudpicture.image.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.security.annotation.RequireRole;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.AdminSpaceQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.AdminSpaceVO;
import com.example.cloudpicture.image.service.AdminSpaceService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端私有空间管理：只有元信息与改名/删除，没有图片级接口 */
@Validated
@RestController
@RequestMapping("/admin/space")
@RequireRole(CurrentUser.ROLE_ADMIN)
public class AdminSpaceController {

    private final AdminSpaceService adminSpaceService;

    public AdminSpaceController(AdminSpaceService adminSpaceService) {
        this.adminSpaceService = adminSpaceService;
    }

    @GetMapping("/list")
    public ApiResponse<PageData<AdminSpaceVO>> listSpaces(@Valid AdminSpaceQueryRequest request) {
        return ApiResponse.success(adminSpaceService.pageSpaces(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> rename(@PathVariable("id") Long id,
                                       @Valid @RequestBody SpaceRenameRequest request) {
        return ApiResponse.success(adminSpaceService.rename(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        return ApiResponse.success(adminSpaceService.delete(id));
    }
}