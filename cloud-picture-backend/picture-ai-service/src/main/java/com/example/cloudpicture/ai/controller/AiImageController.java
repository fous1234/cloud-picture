package com.example.cloudpicture.ai.controller;

import com.example.cloudpicture.ai.dto.request.ModerationRequest;
import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.ai.dto.response.ImageModerationVO;
import com.example.cloudpicture.ai.service.ImageMetadataService;
import com.example.cloudpicture.ai.service.ImageModerationService;
import com.example.cloudpicture.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai")
public class AiImageController {

    private final ImageMetadataService imageMetadataService;
    private final ImageModerationService imageModerationService;

    public AiImageController(ImageMetadataService imageMetadataService,
                             ImageModerationService imageModerationService) {
        this.imageMetadataService = imageMetadataService;
        this.imageModerationService = imageModerationService;
    }

    /** 外部经网关 POST /api/ai/image-metadata 转发到此；需登录，返回的只是建议 */
    @PostMapping("/image-metadata")
    public ApiResponse<ImageMetadataVO> imageMetadata(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(imageMetadataService.suggest(file));
    }

    /** 内部接口：image-service 凌晨审核任务调用，经 X-Internal-Token 放行（picture.internal.paths），不经网关路由 */
    @PostMapping("/image-moderation")
    public ApiResponse<ImageModerationVO> imageModeration(@Valid @RequestBody ModerationRequest request) {
        return ApiResponse.success(imageModerationService.moderate(request.getImageUrl(), request.getOwnerId()));
    }
}