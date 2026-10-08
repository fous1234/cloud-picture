package com.example.cloudpicture.payment.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

/** image-service 内部接口 GET /space/internal/mine 的响应：档位已按"是否过期"解析过 */
@Data
public class SpaceStateVO {

    private Long spaceId;
    /** 当前生效档位（已过期返回 FREE） */
    private String tier;
    /** 当前生效到期时间；FREE 为 null */
    private LocalDateTime tierExpireTime;
}