package com.example.cloudpicture.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.dto.request.SpaceGrantRequest;
import com.example.cloudpicture.image.dto.response.InternalSpaceVO;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 私有空间的内部接口：只服务间调用（X-Internal-Token），不提供任何图片级能力。
 * 续费顺延公式由调用方 payment 计算，这里只做覆盖写
 */
@Slf4j
@Service
public class SpaceInternalService {

    private final PrivateSpaceMapper privateSpaceMapper;

    public SpaceInternalService(PrivateSpaceMapper privateSpaceMapper) {
        this.privateSpaceMapper = privateSpaceMapper;
    }

    /** 按 userId 查生效中的档位；该用户还没有空间时返回 null */
    public InternalSpaceVO findByUserId(Long userId) {
        PrivateSpace space = privateSpaceMapper.selectOne(new LambdaQueryWrapper<PrivateSpace>()
                .eq(PrivateSpace::getOwnerId, userId));
        if (space == null) {
            return null;
        }
        return InternalSpaceVO.of(space, TierSupport.currentPlan(space));
    }

    /**
     * 支付成功后授权：覆盖写 tier 与 tier_expire_time。
     * 覆盖写天然幂等——同一份授权重复执行结果一致，故 payment 侧授权失败可直接重试
     */
    public boolean grant(SpaceGrantRequest request) {
        TierPlan plan = TierPlan.of(request.getTier());
        if (!plan.isPurchasable()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只有 PRO / MAX 可被授予");
        }
        if (!request.getTierExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "套餐到期时间必须晚于当前时间");
        }
        int rows = privateSpaceMapper.update(null, new LambdaUpdateWrapper<PrivateSpace>()
                .set(PrivateSpace::getTier, plan.name())
                .set(PrivateSpace::getTierExpireTime, request.getTierExpireTime())
                .eq(PrivateSpace::getOwnerId, request.getUserId()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "私有空间不存在，无法授予套餐");
        }
        log.info("私有空间套餐授予成功: userId={}, tier={}, expireTime={}",
                request.getUserId(), plan.name(), request.getTierExpireTime());
        return true;
    }
}