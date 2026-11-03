# 支付服务历史迁移

这里归档原支付服务的 V1、V2 SQL，内容保持不变，仅供追溯，不由剩余服务的 Flyway 执行。

移除支付服务不删除已有 `picture_payment` 数据库、订单或 Flyway 历史。
图片服务仍保留原 `V6__add_space_tier.sql`，避免改变已应用迁移的校验和；
`t_private_space` 中的套餐字段保留，现有代码不再读取或更新它们。
不要为此次移除执行 DROP DATABASE、DROP TABLE 或删除 COS 对象。
