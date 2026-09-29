package com.example.cloudpicture.image.client;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.image.dto.response.UserBriefVO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 内部接口：批量查询上传者基本信息，令牌由 FeignConfig 统一携带 */
@FeignClient(name = "picture-user-service", path = "/user")
public interface UserServiceClient {

    @GetMapping("/internal/batch")
    ApiResponse<List<UserBriefVO>> batchGetUsers(@RequestParam("ids") List<Long> ids);
}