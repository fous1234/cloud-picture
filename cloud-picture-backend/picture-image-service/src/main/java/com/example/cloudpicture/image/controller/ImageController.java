package com.example.cloudpicture.image.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageUpdateRequest;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.service.ImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/image")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/upload")
    public ApiResponse<ImageVO> upload(@RequestPart("file") MultipartFile file,
                                       @Valid ImageUploadRequest request) {
        return ApiResponse.success(imageService.upload(file, request));
    }

    @GetMapping("/list")
    public ApiResponse<PageData<ImageVO>> listImages(@Valid ImageQueryRequest request) {
        return ApiResponse.success(imageService.pageImages(request));
    }

    @GetMapping("/mine")
    public ApiResponse<PageData<ImageVO>> listMyImages(@Valid ImageQueryRequest request) {
        return ApiResponse.success(imageService.pageMyImages(request));
    }

    @GetMapping("/tags")
    public ApiResponse<List<String>> listTags(
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(imageService.listTagNames(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImageVO> getImageById(@PathVariable("id") Long id) {
        return ApiResponse.success(imageService.getImageById(id));
    }

    @GetMapping("/{id}/download")
    public ApiResponse<String> download(@PathVariable("id") Long id) {
        return ApiResponse.success(imageService.download(id));
    }

    @PutMapping
    public ApiResponse<Boolean> updateImage(@Valid @RequestBody ImageUpdateRequest request) {
        return ApiResponse.success(imageService.updateImage(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteImage(@PathVariable("id") Long id) {
        return ApiResponse.success(imageService.deleteImage(id));
    }
}
