-- 更新初始化管理员密码；明文密码不写入迁移文件
UPDATE `t_user`
SET `user_password` = '$2a$10$RLKyOhzS9GcCs3xeNMDAyuIoVQ5vAei28.bblHzLDdAHjkpBw2Rry'
WHERE `user_account` = 'admin'
  AND `user_role` = 'ADMIN';
