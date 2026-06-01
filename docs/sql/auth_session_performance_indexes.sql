-- 用户会话查询性能优化索引
-- 适用场景：
-- 1. 账户中心会话列表：where user_id = ? and status = 'ACTIVE' order by last_active_at desc, login_at desc limit ?, ?
-- 2. 会话数量统计：where user_id = ? and status = 'ACTIVE'
-- 3. 账户中心最近登录时间：where user_id = ? order by login_at desc limit 1
-- 4. 用户维度授权过期清理兜底：where user_id = ? and status = 'ACTIVE' and refresh_expires_at <= ?
--
-- 当前在线/离线实时状态只看 Redis auth:presence:{sessionId}。
-- 普通浏览器离线不再扫描或更新 MySQL online_status，因此不再建议为 online_status 单独加高频索引。
--
-- 执行前建议先查看现有索引，避免重复创建：
-- SHOW INDEX FROM user_session;

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_user_status_order`
  (`user_id`, `status`, `last_active_at` DESC, `login_at` DESC);

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_user_login`
  (`user_id`, `login_at` DESC);

ALTER TABLE `user_session`
  ADD INDEX `idx_user_session_user_status_refresh`
  (`user_id`, `status`, `refresh_expires_at`);

-- 如果你之前已经创建了以下旧索引，并且已经切换为 Redis presence 在线状态模型，
-- 可以在确认没有旧定时离线任务依赖后手动删除它们，以减少登录、refresh、退出时的写索引成本：
--
-- ALTER TABLE `user_session` DROP INDEX `idx_user_session_online_active`;
-- ALTER TABLE `user_session` DROP INDEX `idx_user_session_online_status_lastactive`;
