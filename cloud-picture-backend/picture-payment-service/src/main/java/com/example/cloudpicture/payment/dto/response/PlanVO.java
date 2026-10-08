package com.example.cloudpicture.payment.dto.response;

import lombok.Data;

/** 套餐卡片：purchasable / current / badge 三个字段直接驱动前端三态按钮 */
@Data
public class PlanVO {

    private String tier;
    private String name;
    private Integer imageLimit;
    private Long sizeLimitBytes;
    /** 价格（分）；FREE 为 0 */
    private Integer priceFen;
    /** 有效期天数 */
    private Integer planDays;
    /** 当前用户是否可购买（低档位与未创建空间时为 false） */
    private boolean purchasable;
    private boolean current;
    /** 徽标：当前套餐 / 推荐 */
    private String badge;
}