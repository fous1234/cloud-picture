package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.service.TierSupport;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SpaceVO {

    private Long id;
    private String name;
    /** 空间内未删除图片数 */
    private Long imageCount;
    /** 空间内图片总大小（字节） */
    private Long totalSize;
    /** 当前生效档位：FREE/PRO/MAX（已过期返回 FREE） */
    private String tier;
    /** 档位展示名：普通/PRO/MAX */
    private String tierName;
    /** 套餐到期时间；FREE 为 null */
    private LocalDateTime tierExpireTime;
    /** 当前档位的图片数量上限 */
    private Integer imageLimit;
    /** 当前档位的容量上限（字节） */
    private Long sizeLimitBytes;
    private LocalDateTime createTime;

    public static SpaceVO from(PrivateSpace space, long imageCount, long totalSize) {
        if (space == null) {
            return null;
        }
        TierPlan plan = TierSupport.currentPlan(space);
        SpaceVO vo = new SpaceVO();
        vo.id = space.getId();
        vo.name = space.getName();
        vo.imageCount = imageCount;
        vo.totalSize = totalSize;
        vo.tier = plan.name();
        vo.tierName = plan.getDisplayName();
        // 已过期时不再回传旧到期时间，避免前端显示成"已过期的会员"
        vo.tierExpireTime = plan == TierPlan.FREE ? null : space.getTierExpireTime();
        vo.imageLimit = plan.getImageLimit();
        vo.sizeLimitBytes = plan.getSizeLimitBytes();
        vo.createTime = space.getCreateTime();
        return vo;
    }
}