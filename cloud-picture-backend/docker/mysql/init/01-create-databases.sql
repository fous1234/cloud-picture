-- 各服务独立的逻辑库（规范一：同一 MySQL 实例的独立逻辑库）
-- 注意：本脚本只在数据卷为空时执行；已有环境新增库需手动执行同样的 CREATE DATABASE
CREATE DATABASE IF NOT EXISTS `picture_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `picture_image` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `picture_payment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;