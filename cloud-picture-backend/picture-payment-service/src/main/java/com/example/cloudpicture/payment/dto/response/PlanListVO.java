package com.example.cloudpicture.payment.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class PlanListVO {

    /** 是否已创建私有空间；未创建时全部套餐不可购买，前端引导先创建空间 */
    private boolean spaceCreated;
    private String currentTier;
    private String currentTierName;
    /** 当前档位到期时间；FREE 为 null */
    private LocalDateTime expireTime;
    private List<PlanVO> plans;
}