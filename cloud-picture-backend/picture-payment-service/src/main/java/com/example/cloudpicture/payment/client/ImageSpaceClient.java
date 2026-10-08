package com.example.cloudpicture.payment.client;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.payment.dto.request.SpaceGrantPayload;
import com.example.cloudpicture.payment.dto.response.SpaceStateVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** 内部接口：私有空间档位查询与授权，令牌由 FeignConfig 统一携带 */
@FeignClient(name = "picture-image-service", path = "/space/internal")
public interface ImageSpaceClient {

    /** 查该用户空间的生效档位；未创建空间时 data 为 null */
    @GetMapping("/mine")
    ApiResponse<SpaceStateVO> mine(@RequestParam("userId") Long userId);

    /** 支付成功后授权；覆盖写，幂等 */
    @PostMapping("/grant")
    ApiResponse<Boolean> grant(@RequestBody SpaceGrantPayload payload);
}