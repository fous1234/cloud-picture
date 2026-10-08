package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.entity.PrivateSpace;
import java.time.LocalDateTime;

/**
 * 套餐档位解析：定时任务负责把过期档位写回 FREE，这里在读时再做一次兜底，
 * 避免任务未跑成或服务刚重启时把已过期的高档位当成有效的，从而放大配额
 */
public final class TierSupport {

    private TierSupport() {
    }

    /** 空间当前生效的档位；已过期（到期时间不晚于此刻）一律按 FREE */
    public static TierPlan currentPlan(PrivateSpace space) {
        if (space == null) {
            return TierPlan.FREE;
        }
        LocalDateTime expireTime = space.getTierExpireTime();
        if (expireTime != null && !expireTime.isAfter(LocalDateTime.now())) {
            return TierPlan.FREE;
        }
        return TierPlan.of(space.getTier());
    }
}