-- DocNexus 登录会话修复脚本
-- 用途：
-- 1. 修复 token_version 默认值，避免新注册用户签发非法 JWT。
-- 2. 把历史 token_version=0 的用户和会话修正为 1。
-- 3. 把 refreshToken 已过期但仍是 ACTIVE 的会话标记为 EXPIRED。

USE `docnexus_cloud`;

ALTER TABLE `user_account`
  MODIFY `token_version` BIGINT NOT NULL DEFAULT 1 COMMENT '令牌版本号，修改密码或重置密码后递增，用于让旧 JWT 失效';

ALTER TABLE `user_session`
  MODIFY `token_version` BIGINT NOT NULL DEFAULT 1 COMMENT '创建或刷新该会话时使用的 tokenVersion';

UPDATE `user_account`
SET `token_version` = 1,
    `update_time` = NOW()
WHERE `token_version` IS NULL OR `token_version` <= 0;

UPDATE `user_session`
SET `token_version` = 1,
    `update_time` = NOW()
WHERE `token_version` IS NULL OR `token_version` <= 0;

UPDATE `user_session`
SET `status` = 'EXPIRED',
    `logout_at` = NOW(),
    `update_time` = NOW()
WHERE `status` = 'ACTIVE'
  AND `refresh_expires_at` <= NOW();
