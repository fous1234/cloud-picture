package com.example.cloudpicture.image.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.image.dto.request.SpaceGrantRequest;
import com.example.cloudpicture.image.dto.response.InternalSpaceVO;
import com.example.cloudpicture.image.service.SpaceInternalService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 服务间内部接口：只认 X-Internal-Token（picture.internal.paths），不经网关路由 */
@Validated
@RestController
@RequestMapping("/space/internal")
public class SpaceInternalController {

    private final SpaceInternalService spaceInternalService;

    public SpaceInternalController(SpaceInternalService spaceInternalService) {
        this.spaceInternalService = spaceInternalService;
    }

    /** 供 payment 建单前校验：该用户是否已有私有空间；没有时 data 为 null */
    @GetMapping("/mine")
    public ApiResponse<InternalSpaceVO> mine(@RequestParam("userId") Long userId) {
        return ApiResponse.success(spaceInternalService.findByUserId(userId));
    }

    /** 支付成功后授权：覆盖写档位与到期时间（幂等） */
    @PostMapping("/grant")
    public ApiResponse<Boolean> grant(@Valid @RequestBody SpaceGrantRequest request) {
        return ApiResponse.success(spaceInternalService.grant(request));
    }
}