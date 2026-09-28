-- 两个服务各自独立的逻辑库（规范一：同一 MySQL 实例的独立逻辑库）
CREATE DATABASE IF NOT EXISTS `picture_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `picture_image` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;