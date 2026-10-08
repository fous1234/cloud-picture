package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.entity.PrivateSpace;
import java.time.LocalDateTime;
import lombok.Data;

/** 服务间查询结果：只给 payment 建单校验与续费顺延计算用，不暴露图片信息 */
@Data
public class InternalSpaceVO {

    private Long spaceId;
    /** 当前生效档位（已过期按 FREE 返回，避免调用方自己实现过期判断） */
    private String tier;
    /** 当前生效的到期时间；FREE 为 null */
    private LocalDateTime tierExpireTime;

    public static InternalSpaceVO of(PrivateSpace space, TierPlan plan) {
        InternalSpaceVO vo = new InternalSpaceVO();
        vo.spaceId = space.getId();
        vo.tier = plan.name();
        vo.tierExpireTime = plan == TierPlan.FREE ? null : space.getTierExpireTime();
        return vo;
    }
}