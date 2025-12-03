-- V1: 删除 display_name 字段
-- 原因: 采用 JIT Provisioning 模式，认证中心不再存储显示名称
-- 用户资料（包括昵称）将在首次登录后由 Core-Service 创建

ALTER TABLE auth_users DROP COLUMN IF EXISTS display_name;
