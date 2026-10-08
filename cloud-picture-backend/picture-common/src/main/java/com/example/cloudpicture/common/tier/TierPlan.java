package com.example.cloudpicture.common.tier;

import java.util.Arrays;
import lombok.Getter;

/**
 * 私有空间套餐档位：限额与价格集中定义，避免 payment（要价格与限额）与 image（要限额）两处定义漂移。
 * 上限单位：张数为整数，容量为字节；价格单位为分。1 MB = 1024 × 1024 字节。
 */
@Getter
public enum TierPlan {

    FREE("普通", 200, 200L * 1024 * 1024, 0, false),
    PRO("PRO", 350, 350L * 1024 * 1024, 990, true),
    MAX("MAX", 600, 600L * 1024 * 1024, 1990, true);

    private final String displayName;
    private final int imageLimit;
    private final long sizeLimitBytes;
    private final int priceFen;
    /** 是否可通过支付购买（FREE 为默认档，不出售） */
    private final boolean purchasable;

    TierPlan(String displayName, int imageLimit, long sizeLimitBytes, int priceFen, boolean purchasable) {
        this.displayName = displayName;
        this.imageLimit = imageLimit;
        this.sizeLimitBytes = sizeLimitBytes;
        this.priceFen = priceFen;
        this.purchasable = purchasable;
    }

    /** 未知值或空值一律按 FREE 处理，避免脏数据把配额放大 */
    public static TierPlan of(String tier) {
        if (tier == null || tier.isBlank()) {
            return FREE;
        }
        return Arrays.stream(values())
                .filter(plan -> plan.name().equalsIgnoreCase(tier.trim()))
                .findFirst()
                .orElse(FREE);
    }

    public long getSizeLimitMb() {
        return sizeLimitBytes / (1024 * 1024);
    }
}