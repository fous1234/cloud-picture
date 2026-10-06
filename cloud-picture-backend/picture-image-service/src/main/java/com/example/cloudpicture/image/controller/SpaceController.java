package com.example.cloudpicture.image.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.request.SpaceCreateRequest;
import com.example.cloudpicture.image.dto.request.SpaceImageQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.SpaceImageVO;
import com.example.cloudpicture.image.dto.response.SpaceVO;
import com.example.cloudpicture.image.service.SpaceService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 用户私有空间：只能操作自己的空间与图片，管理员无图片级入口 */
@Validated
@RestController
@RequestMapping("/space")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /** 未创建空间时 data 为 null，由前端引导创建 */
    @GetMapping
    public ApiResponse<SpaceVO> getMine() {
        return ApiResponse.success(spaceService.getMine());
    }

    @PostMapping
    public ApiResponse<SpaceVO> create(@Valid @RequestBody SpaceCreateRequest request) {
        return ApiResponse.success(spaceService.create(request));
    }

    @PutMapping
    public ApiResponse<Boolean> rename(@Valid @RequestBody SpaceRenameRequest request) {
        return ApiResponse.success(spaceService.rename(request));
    }

    @DeleteMapping
    public ApiResponse<Boolean> deleteMine() {
        return ApiResponse.success(spaceService.deleteMine());
    }

    @PostMapping("/image/upload")
    public ApiResponse<SpaceImageVO> upload(@RequestPart("file") MultipartFile file,
                                            @Valid ImageUploadRequest request) {
        return ApiResponse.success(spaceService.upload(file, request));
    }

    @GetMapping("/image/list")
    public ApiResponse<PageData<SpaceImageVO>> listImages(@Valid SpaceImageQueryRequest request) {
        return ApiResponse.success(spaceService.pageImages(request));
    }

    @GetMapping("/image/{id}/download")
    public ApiResponse<String> download(@PathVariable("id") Long id) {
        return ApiResponse.success(spaceService.download(id));
    }

    @DeleteMapping("/image/{id}")
    public ApiResponse<Boolean> deleteImage(@PathVariable("id") Long id) {
        return ApiResponse.success(spaceService.deleteImage(id));
    }
}