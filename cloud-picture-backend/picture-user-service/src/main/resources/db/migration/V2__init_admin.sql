-- 初始管理员账号（规范一：管理员账号通过初始化数据创建，不开放用户自助提升角色）
-- 演示账号：admin / Admin@123456
-- 正式部署前请修改该密码；MVP 未提供改密接口，可自行更新 user_password 为新的 BCrypt 密文
INSERT INTO `t_user` (`id`, `user_account`, `user_password`, `user_name`, `user_role`, `status`)
VALUES (1, 'admin', '$2a$10$8EhzN0f.5sZOmTtw7i/O2ORpylm5uS3iKRGRhXqJ8ilPfOPgnbPCW', '管理员', 'ADMIN', 1);