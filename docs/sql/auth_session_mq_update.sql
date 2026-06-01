UPDATE user_account
SET token_version = 1,
    update_time = NOW()
WHERE token_version IS NULL OR token_version <= 0;

UPDATE user_session
SET token_version = 1,
    update_time = NOW()
WHERE token_version IS NULL OR token_version <= 0;

UPDATE user_session
SET online_status = 'OFFLINE',
    update_time = NOW()
WHERE status = 'ACTIVE'
  AND (online_status IS NULL OR online_status = '');

UPDATE user_session
SET status = 'EXPIRED',
    online_status = 'OFFLINE',
    expired_at = COALESCE(logout_at, expired_at, NOW()),
    close_reason = COALESCE(close_reason, 'LOGOUT'),
    update_time = NOW()
WHERE status = 'LOGOUT';

UPDATE user_session
SET status = 'EXPIRED',
    online_status = 'OFFLINE',
    expired_at = COALESCE(expired_at, NOW()),
    close_reason = COALESCE(close_reason, 'REFRESH_EXPIRED'),
    update_time = NOW()
WHERE status = 'ACTIVE'
  AND refresh_expires_at <= NOW();