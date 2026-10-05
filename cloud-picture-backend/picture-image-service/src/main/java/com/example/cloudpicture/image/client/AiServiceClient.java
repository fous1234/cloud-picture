package com.example.cloudpicture.image.client;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.image.dto.request.AiModerationRequest;
import com.example.cloudpicture.image.dto.response.AiModerationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 内部接口：AI 图片审核，令牌由 FeignConfig 统一携带 */
@FeignClient(name = "picture-ai-service", path = "/ai")
public interface AiServiceClient {

    @PostMapping("/image-moderation")
    ApiResponse<AiModerationVO> moderate(@RequestBody AiModerationRequest request);
}
