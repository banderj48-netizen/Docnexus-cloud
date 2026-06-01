package com.xyf.docnexus.user.Service;

public interface TokenVersionService {
    /**
     * 获取当前用户真实 tokenVersion。
     *
     * <p>实现类应优先读取 Redis，Redis 不存在时回源 MySQL。</p>
     *
     * @param userId 用户 ID
     * @return 当前 tokenVersion
     */
    Long getCurrentTokenVersion(Long userId);

    /**
     * 用业务流程中已经拿到的 tokenVersion 补热 Redis。
     *
     * <p>登录接口通常已经从 user_account 查询到了 token_version。
     * 这种情况下不需要为了补写 Redis 再查询一次 MySQL，直接把已知版本写入 Redis 即可。
     * 如果传入版本为空或非法，实现类应回退到 {@link #getCurrentTokenVersion(Long)}。</p>
     *
     * @param userId 用户 ID
     * @param knownTokenVersion 已知的 tokenVersion，通常来自本次 user_account 查询结果
     * @return 最终用于签发 JWT 的 tokenVersion
     */
    Long warmTokenVersion(Long userId, Long knownTokenVersion);

    /**
     * 递增当前用户 tokenVersion。
     *
     * <p>递增后，旧 JWT 中携带的 tokenVersion 会小于最新版本，
     * 网关校验时会拒绝旧 JWT。</p>
     *
     * @param userId 用户 ID
     * @return 递增后的最新 tokenVersion
     */
    Long increaseTokenVersion(Long userId);
}
