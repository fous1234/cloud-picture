package com.example.cloudpicture.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 私有空间：一人一个，删除即物理删除（uk_owner_id 放行重建），故不加 {@code @TableLogic}
 */
@Data
@TableName("t_private_space")
public class PrivateSpace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerId;

    private String name;

    /** 套餐档位：FREE/PRO/MAX；配额上限由 TierPlan 决定 */
    private String tier;

    /** 套餐到期时间；FREE 为 null。到期后由定时任务降回 FREE，读时也做一次兜底判断 */
    private LocalDateTime tierExpireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}