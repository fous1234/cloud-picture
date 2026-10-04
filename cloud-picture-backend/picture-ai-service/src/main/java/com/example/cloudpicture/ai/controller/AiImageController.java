package com.example.cloudpicture.ai.controller;

import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.ai.service.ImageMetadataService;
import com.example.cloudpicture.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai")
public class AiImageController {

    private final ImageMetadataService imageMetadataService;

    public AiImageController(ImageMetadataService imageMetadataService) {
        this.imageMetadataService = imageMetadataService;
    }

    /** 外部经网关 POST /api/ai/image-metadata 转发到此；需登录，返回的只是建议 */
    @PostMapping("/image-metadata")
    public ApiResponse<ImageMetadataVO> imageMetadata(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(imageMetadataService.suggest(file));
    }
}