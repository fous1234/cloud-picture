# 移除支付与套餐

- 删除支付服务、网关支付路由、前端购买/订单入口及内部套餐授权接口。
- 私有空间保留一人一个、统计、上传、搜索、预览、下载、改名、删除和所有者校验。
- 取消空间累计张数/容量配额，保留单张文件大小、格式、内容校验及失败清理。
- 不修改已有图片库迁移，不删除已有支付库、数据卷、图片记录或 COS 对象。
- 支付服务历史迁移归档在 `cloud-picture-backend/archived-migrations/picture-payment-service`。

## 验证

在后端运行 `mvnw.cmd clean test`，在前端运行 `npm ci`、`npm run build`。
新增回归覆盖超过原额度后上传仍成功，以及超大/无效文件在上传 COS 前被拒绝。
使用实际 COS、AI、Pexels 凭据的端到端联调需要在运行环境另行完成。

## 部署切换

1. 用新源码重新构建镜像；旧 `cloud-picture-images.tar` / `cloud-picture-demo.zip` 不能作为新版本使用。
2. 已有 Docker 部署先通过旧 Compose 配置停止并移除支付容器，再使用新配置启动其他服务；不删除数据卷。
3. 已通过 IDE 启动的旧支付进程也需手动停止。不要执行 `docker compose down -v` 或删库。
4. 前后端一起更新：私有空间响应不再包含 tier、tierName、tierExpireTime、imageLimit、sizeLimitBytes。
   旧套餐和订单页面不再存在；外部调用方若依赖这些接口，需要停止调用。
5. 本次不调整 JVM 内存，不读取、删除或改写本地私有 `.env`，其中旧支付变量可以暂留但不再生效。
