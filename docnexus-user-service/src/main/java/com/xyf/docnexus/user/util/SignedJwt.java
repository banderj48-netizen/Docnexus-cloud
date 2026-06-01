package com.xyf.docnexus.user.util;

import java.time.Instant;

/**
 * JWT 签发结果，包含写入 Redis 所需的 jti 和过期时间。
 */

public record SignedJwt(
        String token,
        String jwtId,
        Long issuedAtMillis,
        Long expiresAtMillis,
        Long tokenVersion
) {
}