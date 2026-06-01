-- 同设备会话接管与 IP 绑定鉴权索引
-- 执行前建议先查看现有索引，避免重复创建：
-- SHOW INDEX FROM user_session;
--
-- 适用场景：
-- 1. 登录时按 userId + deviceId 查找同设备 ACTIVE 会话，用于“新浏览器接管旧浏览器会话”。
-- 2. 登录时按 userId + clientIp 兜底合并历史浏览器级 deviceId 会话。
-- 3. 按 sessionId 幂等更新会话失效、离线状态。
-- 4. 按 access_jti 找到当前 accessToken 对应的 ACTIVE 会话。

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_user_device_status`
  (`user_id`, `device_id`, `status`);

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_user_ip_status`
  (`user_id`, `client_ip`, `status`);

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_session_status`
  (`session_id`, `status`);

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_access_jti_status`
  (`access_jti`, `status`);
