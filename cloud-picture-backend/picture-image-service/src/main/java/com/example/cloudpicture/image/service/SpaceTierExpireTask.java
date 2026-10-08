package com.example.cloudpicture.image.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 套餐到期降级：每日凌晨把到期空间写回 FREE。
 * 只降档不删图——降级后已超额的图片保留，只是不能再上传；
 * 读取侧还有 TierSupport 兜底，任务漏跑也不会误放行超配额上传
 */
@Slf4j
@Service
public class SpaceTierExpireTask {

    private final PrivateSpaceMapper privateSpaceMapper;

    public SpaceTierExpireTask(PrivateSpaceMapper privateSpaceMapper) {
        this.privateSpaceMapper = privateSpaceMapper;
    }

    /** 演示时可把 SPACE_TIER_EXPIRE_CRON 临时调成高频 */
    @Scheduled(cron = "${picture.space-tier.expire-cron:0 0 0 * * ?}")
    public void downgradeExpiredTiers() {
        int rows = privateSpaceMapper.update(null, new LambdaUpdateWrapper<PrivateSpace>()
                .set(PrivateSpace::getTier, TierPlan.FREE.name())
                .set(PrivateSpace::getTierExpireTime, (Object) null)
                .ne(PrivateSpace::getTier, TierPlan.FREE.name())
                .le(PrivateSpace::getTierExpireTime, LocalDateTime.now()));
        log.info("套餐到期降级任务完成: 降级空间数={}", rows);
    }
}