package com.example.cloudpicture.image.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.security.annotation.RequireRole;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageReviewRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.service.AdminImageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/image")
@RequireRole(CurrentUser.ROLE_ADMIN)
public class AdminImageController {

    private final AdminImageService adminImageService;

    public AdminImageController(AdminImageService adminImageService) {
        this.adminImageService = adminImageService;
    }

    @GetMapping("/list")
    public ApiResponse<PageData<ImageVO>> listImages(@Valid ImageQueryRequest request) {
        return ApiResponse.success(adminImageService.pageImages(request));
    }

    @PostMapping("/review")
    public ApiResponse<Boolean> review(@Valid @RequestBody ImageReviewRequest request) {
        return ApiResponse.success(adminImageService.review(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteImage(@PathVariable("id") Long id) {
        return ApiResponse.success(adminImageService.deleteImage(id));
    }
}