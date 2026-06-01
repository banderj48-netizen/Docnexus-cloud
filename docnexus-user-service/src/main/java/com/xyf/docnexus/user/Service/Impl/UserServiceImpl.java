package com.xyf.docnexus.user.Service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.xyf.docnexus.common.DTO.ChangePasswordRequest;
import com.xyf.docnexus.common.DTO.LoginRequest;
import com.xyf.docnexus.common.DTO.PasswordRecoveryVerifyRequest;
import com.xyf.docnexus.common.DTO.PasswordResetRequest;
import com.xyf.docnexus.common.DTO.RefreshTokenRequest;
import com.xyf.docnexus.common.DTO.RegisterRequest;
import com.xyf.docnexus.common.DTO.SessionHeartbeatRequest;
import com.xyf.docnexus.common.DTO.UserDTO;
import com.xyf.docnexus.common.DTO.UserProfileUpdateRequest;
import com.xyf.docnexus.common.VO.LoginResponse;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.VO.PasswordRecoveryVerifyResponse;
import com.xyf.docnexus.common.VO.UserProfileResponse;
import com.xyf.docnexus.common.VO.UserSessionResponse;
import com.xyf.docnexus.common.exception.BusinessException;
import com.xyf.docnexus.common.pojo.RedisTokenSession;
import com.xyf.docnexus.common.pojo.User;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.user.Mapper.SessionMapper;
import com.xyf.docnexus.user.Mapper.UserMapper;
import com.xyf.docnexus.user.Service.LoginSecurityService;
import com.xyf.docnexus.user.Service.TokenVersionService;
import com.xyf.docnexus.user.Service.UserService;
import com.xyf.docnexus.user.config.UserJwtProperties;
import com.xyf.docnexus.user.entity.UserSession;
import com.xyf.docnexus.user.entity.UserSessionLogoutParam;
import com.xyf.docnexus.user.entity.UserSessionQueryParam;
import com.xyf.docnexus.user.event.SessionExpiredEvent;
import com.xyf.docnexus.user.event.SessionEventPublisher;
import com.xyf.docnexus.user.event.SessionOfflineEvent;
import com.xyf.docnexus.user.util.CacheProtectionSupport;
import com.xyf.docnexus.user.util.HeartbeatSessionCacheStore;
import com.xyf.docnexus.user.util.JwtSignTool;
import com.xyf.docnexus.user.util.LoginUserCacheStore;
import com.xyf.docnexus.user.util.PasswordResetPermitStore;
import com.xyf.docnexus.user.util.RedisTokenSessionStore;
import com.xyf.docnexus.user.util.RefreshTokenTool;
import com.xyf.docnexus.user.util.SignedJwt;
import com.xyf.docnexus.user.util.UserProfileCacheStore;
import com.xyf.docnexus.user.util.UserSessionListCacheStore;
import com.xyf.docnexus.user.util.UserSessionOnlineStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.xyf.docnexus.common.constant.ResponseCode.DATA_ALREADY_EXISTS;
import static com.xyf.docnexus.common.constant.UserConstant.DEFAULT_ROLE;
import static com.xyf.docnexus.common.constant.UserConstant.USER_STATUS_DISABLED;
import static com.xyf.docnexus.common.constant.UserConstant.USER_STATUS_ENABLED;
import static com.xyf.docnexus.user.constant.UserSessionConstants.*;

/**
 * 用户业务实现。
 *
 * <p>当前用户登录态由三部分组成：</p>
 * <ol>
 *     <li>accessToken：JWT，前端访问业务接口时放入 Authorization 请求头；</li>
 *     <li>Redis access session：网关根据 JWT 的 jti 查询 Redis，判断 accessToken 是否仍然有效；</li>
 *     <li>MySQL user_session：保存 refreshToken 的哈希和设备会话状态，支持多设备同时登录与刷新续期。</li>
 * </ol>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long RESET_TOKEN_EXPIRE_SECONDS = 600L;
    private static final Duration DEVICE_LOGIN_LOCK_TTL = Duration.ofSeconds(5);
    private static final String DEVICE_ID_VERSION_PREFIX = "v2_";


    private final PasswordResetPermitStore passwordResetPermitStore;
    private final UserMapper userMapper;
    private final JwtSignTool jwtSignTool;
    private final TokenVersionService tokenVersionService;
    private final RedisTokenSessionStore redisTokenSessionStore;
    private final UserProfileCacheStore userProfileCacheStore;
    private final LoginSecurityService loginSecurityService;
    private final LoginUserCacheStore loginUserCacheStore;
    private final CacheProtectionSupport cacheProtectionSupport;
    private final SessionMapper sessionMapper;
    private final RefreshTokenTool refreshTokenTool;
    private final UserJwtProperties jwtProperties;
    private final UserSessionOnlineStore userSessionOnlineStore;
    private final HeartbeatSessionCacheStore heartbeatSessionCacheStore;
    private final UserSessionListCacheStore userSessionListCacheStore;
    /**
     * Spring 事务管理器。
     *
     * <p>用于在 MQ 投递失败时开启独立事务同步更新 MySQL。
     * 这样即使外层业务方法随后抛出“请重新登录”等异常，兜底落库也不会被外层事务回滚。</p>
     */
    private final PlatformTransactionManager transactionManager;
    /**
     * 用户会话事件发送器。
     *
     * <p>用于把退出登录、refreshToken 过期、tokenVersion 失效等事件投递到 RocketMQ，
     * 由 Consumer 异步更新 MySQL，降低高并发退出时的数据库写压力。</p>
     */
    private final SessionEventPublisher sessionEventPublisher;

    public UserServiceImpl(PasswordResetPermitStore passwordResetPermitStore,
                           UserMapper userMapper,
                           JwtSignTool jwtSignTool,
                           TokenVersionService tokenVersionService,
                           RedisTokenSessionStore redisTokenSessionStore,
                           UserProfileCacheStore userProfileCacheStore,
                           LoginSecurityService loginSecurityService,
                           LoginUserCacheStore loginUserCacheStore,
                           CacheProtectionSupport cacheProtectionSupport,
                           SessionMapper sessionMapper,
                           RefreshTokenTool refreshTokenTool,
                           UserJwtProperties jwtProperties,
                           UserSessionOnlineStore userSessionOnlineStore,
                           HeartbeatSessionCacheStore heartbeatSessionCacheStore,
                           UserSessionListCacheStore userSessionListCacheStore,
                           PlatformTransactionManager transactionManager,
                           SessionEventPublisher sessionEventPublisher) {
        this.passwordResetPermitStore = passwordResetPermitStore;
        this.userMapper = userMapper;
        this.jwtSignTool = jwtSignTool;
        this.tokenVersionService = tokenVersionService;
        this.redisTokenSessionStore = redisTokenSessionStore;
        this.userProfileCacheStore = userProfileCacheStore;
        this.loginSecurityService = loginSecurityService;
        this.loginUserCacheStore = loginUserCacheStore;
        this.cacheProtectionSupport = cacheProtectionSupport;
        this.sessionMapper = sessionMapper;
        this.refreshTokenTool = refreshTokenTool;
        this.jwtProperties = jwtProperties;
        this.userSessionOnlineStore = userSessionOnlineStore;
        this.heartbeatSessionCacheStore = heartbeatSessionCacheStore;
        this.userSessionListCacheStore = userSessionListCacheStore;
        this.transactionManager = transactionManager;
        this.sessionEventPublisher = sessionEventPublisher;
    }

    /**
     * 用户登录。
     *
     * <p>普通登录不会递增 tokenVersion，这样同一个用户可以多设备同时在线。
     * 每次登录都会创建独立的 sessionId、refreshToken 和 accessToken jti，
     * 因此退出当前设备时只会影响当前 session，不会误伤其他设备。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        if (request == null) {
            throw new RuntimeException("登录参数不能为空");
        }

        String username = trim(request.getUsername());
        String password = trim(request.getPassword());
        log.info("用户登录开始，username={}, clientIp={}", username, clientIp);

        if (!StringUtils.hasText(username)) {
            log.warn("用户登录失败：用户名为空，clientIp={}", clientIp);
            throw new RuntimeException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            log.warn("用户登录失败：密码为空，username={}, clientIp={}", username, clientIp);
            throw new RuntimeException("密码不能为空");
        }
        if (!isBase64(password)) {
            log.warn("用户登录失败：密码格式不是 Base64，username={}, clientIp={}", username, clientIp);
            throw new RuntimeException("密码格式不正确");
        }

        // 登录前先做安全检查：IP 限流 + 用户名失败次数锁定。
        loginSecurityService.checkLoginAllowed(username, clientIp);

        // 登录是安全入口：每次都查询 MySQL 校验用户名、密码、账号状态和 tokenVersion。
        // 普通业务接口的高并发压力由 Gateway Caffeine + Redis 承担，登录接口则通过限流、
        // 账号锁定、username 唯一索引和连接池控制压力，不用登录快照缓存参与密码校验。
        UserDTO user = userMapper.selectByUsername(username);
        if (user == null || !password.equals(user.getPassword())) {
            loginSecurityService.recordLoginFailure(username);
            log.warn("用户登录失败：用户名或密码错误，username={}, clientIp={}", username, clientIp);
            throw new RuntimeException("用户名或密码错误");
        }
        if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
            log.warn("用户登录失败：账号被禁用，username={}, userId={}, status={}, clientIp={}",
                    username, user.getId(), user.getStatus(), clientIp);
            throw new RuntimeException("账号已被禁用");
        }

        // userMapper.selectByUsername 已经从 MySQL 查出 token_version。
        // 这里只把该版本补写到 Redis，保证 Gateway MGET 能拿到版本号，不再额外查一次 MySQL。
        Long tokenVersion = tokenVersionService.warmTokenVersion(user.getId(), user.getTokenVersion());
        user.setTokenVersion(tokenVersion);
        SignedJwt signedJwt = jwtSignTool.signWithSessionInfo(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                tokenVersion
        );

        IssuedSession issuedSession = createSessionAndAccessCache(user, signedJwt, clientIp, userAgent, request);

        // 登录成功后清理用户名维度的失败次数和锁定标记。
        loginSecurityService.recordLoginSuccess(username);

        log.info("用户登录成功，username={}, userId={}, sessionId={}, jwtId={}, clientIp={}",
                username, user.getId(), issuedSession.sessionId(), signedJwt.jwtId(), clientIp);

        return buildLoginResponse(user, signedJwt, issuedSession);
    }

    @Override
    public void register(RegisterRequest request) {
        if (request == null) {
            throw new RuntimeException("注册参数不能为空");
        }

        String username = trim(request.getUsername());
        String password = trim(request.getPassword());
        String confirmPassword = trim(request.getConfirmPassword());
        String email = trim(request.getEmail());
        String phone = trim(request.getPhone());
        log.info("用户注册开始，username={}, email={}, phone={}", username, maskEmail(email), maskPhone(phone));

        if (!StringUtils.hasText(username)) {
            log.warn("用户注册失败：用户名为空");
            throw new RuntimeException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 32) {
            log.warn("用户注册失败：用户名长度不合法，username={}, length={}", username, username.length());
            throw new RuntimeException("用户名长度应为 3-32 个字符");
        }
        if (!StringUtils.hasText(password)) {
            log.warn("用户注册失败：密码为空，username={}", username);
            throw new RuntimeException("密码不能为空");
        }
        if (!StringUtils.hasText(confirmPassword)) {
            log.warn("用户注册失败：确认密码为空，username={}", username);
            throw new RuntimeException("确认密码不能为空");
        }
        if (!isBase64(password) || !isBase64(confirmPassword)) {
            log.warn("用户注册失败：密码格式不是 Base64，username={}", username);
            throw new RuntimeException("密码格式不正确");
        }
        if (!password.equals(confirmPassword)) {
            log.warn("用户注册失败：两次密码不一致，username={}", username);
            throw new RuntimeException("两次输入的密码不一致");
        }
        if (!StringUtils.hasText(email)) {
            log.warn("用户注册失败：邮箱为空，username={}", username);
            throw new RuntimeException("邮箱不能为空");
        }
        if (!StringUtils.hasText(phone)) {
            log.warn("用户注册失败：手机号为空，username={}", username);
            throw new RuntimeException("手机号不能为空");
        }
        if (userMapper.countByUsername(username) > 0) {
            log.warn("用户注册失败：用户名已存在，username={}", username);
            throw new BusinessException(DATA_ALREADY_EXISTS, "用户名已存在");
        }

        request.setUsername(username);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        request.setEmail(email);
        request.setPhone(phone);
        request.setRole(DEFAULT_ROLE);

        User user = BeanUtil.copyProperties(request, User.class);
        user.setRole(DEFAULT_ROLE);
        user.setStatus(USER_STATUS_ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        try {
            int rows = userMapper.insertUser(user);
            if (rows != 1) {
                log.error("用户注册失败：数据库写入行数异常，username={}, rows={}", username, rows);
                throw new RuntimeException("注册失败，请稍后再试");
            }
        } catch (DuplicateKeyException exception) {
            log.warn("用户注册失败：用户名已存在，username={}", username);
            throw new BusinessException(DATA_ALREADY_EXISTS, "用户名已存在");
        }

        log.info("用户注册成功，username={}", username);
    }

    /**
     * 退出当前浏览器设备。
     *
     * <p>这里采用“Redis 立即失效 + RocketMQ 异步落库”的方式。</p>
     *
     * <p>执行顺序：</p>
     * <ol>
     *     <li>解析当前 accessToken，拿到 JWT 的 jti；</li>
     *     <li>根据 jti 查询当前 ACTIVE 会话；</li>
     *     <li>如果会话存在，立即删除 Redis 登录态，让旧 token 马上失效；</li>
     *     <li>发送 RocketMQ 会话失效事件，由 Consumer 异步更新 MySQL；</li>
     *     <li>如果 RocketMQ 发送失败，则当前线程同步更新 MySQL 兜底。</li>
     * </ol>
     *
     * <p>这样做的好处：
     * Redis 是网关鉴权的实时依据，所以 Redis 失效后，用户马上无法继续访问；
     * MySQL 是最终展示和审计数据，可以异步更新，降低高并发退出时的数据库压力。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String authorizationHeader) {
        // 解析当前 accessToken，拿到 JWT 的 jti
        DecodedJWT jwt = jwtSignTool.verify(authorizationHeader);
        String jwtId = jwt.getId();
        String subject = jwt.getSubject();
        String username = jwt.getClaim("username").asString();
        Long expiresAtMillis = jwt.getClaim("expiresAtMillis").asLong();

        if (!StringUtils.hasText(jwtId)) {
            throw new RuntimeException("退出登录失败：令牌缺少 jti");
        }
        if (!StringUtils.hasText(subject)) {
            throw new RuntimeException("退出登录失败：令牌缺少用户身份");
        }
        if (expiresAtMillis == null) {
            throw new RuntimeException("退出登录失败：令牌缺少 expiresAtMillis");
        }
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setAccessJti(jwtId);
        UserSession session = sessionMapper.selectActiveByAccessJti(queryParam);

        if (session != null) {
            // 会话存在：走标准失效流程，Redis 立即失效，MySQL 通过 RocketMQ 异步更新。
            expireSessionByEvent(session, CLOSE_REASON_LOGOUT, LocalDateTime.now());
            deleteLoginSnapshotCache(username);
        } else {
            // 会话不存在：说明 MySQL 中可能已经被其他请求更新过。
            // 但为了安全，仍然要把当前 accessToken 写入 Redis 黑名单。
            redisTokenSessionStore.revokeSession(jwtId, expiresAtMillis);
            clearBestEffortLogoutCaches(subject, username);
        }
        log.info("用户退出当前设备成功，userId={}, jwtId={}", subject, jwtId);
    }

    @Override
    public PasswordRecoveryVerifyResponse verifyPasswordRecovery(PasswordRecoveryVerifyRequest request) {
        if (request == null) {
            throw new RuntimeException("找回密码参数不能为空");
        }

        String username = trim(request.getUsername());
        String email = trim(request.getEmail());
        String phone = trim(request.getPhone());
        log.info("找回密码身份验证开始，username={}", username);

        if (!StringUtils.hasText(username)) {
            log.warn("找回密码身份验证失败：用户名为空");
            throw new RuntimeException("用户名不能为空");
        }
        if (!StringUtils.hasText(email)) {
            log.warn("找回密码身份验证失败：邮箱为空，username={}", username);
            throw new RuntimeException("邮箱不能为空");
        }
        if (!StringUtils.hasText(phone)) {
            log.warn("找回密码身份验证失败：手机号为空，username={}", username);
            throw new RuntimeException("手机号不能为空");
        }
        if (!isBase64(email) || !isBase64(phone)) {
            log.warn("找回密码身份验证失败：邮箱或手机号格式不是 Base64，username={}", username);
            throw new RuntimeException("邮箱或手机号格式不正确");
        }

        UserDTO user = userMapper.selectByUsername(username);
        if (user == null) {
            log.warn("找回密码身份验证失败：用户不存在，username={}", username);
            throw new RuntimeException("用户不存在");
        }
        if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
            log.warn("找回密码身份验证失败：账号被禁用，username={}, userId={}, status={}",
                    username, user.getId(), user.getStatus());
            throw new RuntimeException("账号已被禁用");
        }

        String databaseEmailBase64 = encodeBase64(user.getEmail());
        String databasePhoneBase64 = encodeBase64(user.getPhone());
        if (!email.equals(databaseEmailBase64) || !phone.equals(databasePhoneBase64)) {
            log.warn("找回密码身份验证失败：邮箱或手机号不匹配，username={}, userId={}", username, user.getId());
            throw new RuntimeException("邮箱或手机号验证失败");
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        passwordResetPermitStore.save(username, user.getId(), resetToken, RESET_TOKEN_EXPIRE_SECONDS * 1000L);

        log.info("找回密码身份验证通过，username={}, userId={}, expireSeconds={}",
                username, user.getId(), RESET_TOKEN_EXPIRE_SECONDS);

        return new PasswordRecoveryVerifyResponse(true, resetToken, RESET_TOKEN_EXPIRE_SECONDS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(PasswordResetRequest request) {
        if (request == null) {
            throw new RuntimeException("重置密码参数不能为空");
        }

        String username = trim(request.getUsername());
        String resetToken = trim(request.getResetToken());
        String password = trim(request.getPassword());
        String confirmPassword = trim(request.getConfirmPassword());
        log.info("重置密码开始，username={}", username);

        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("用户名不能为空");
        }
        if (!StringUtils.hasText(resetToken)) {
            throw new RuntimeException("缺少重置密码许可");
        }
        if (!StringUtils.hasText(password)) {
            throw new RuntimeException("新密码不能为空");
        }
        if (!StringUtils.hasText(confirmPassword)) {
            throw new RuntimeException("确认密码不能为空");
        }
        if (!isBase64(password) || !isBase64(confirmPassword)) {
            throw new RuntimeException("新密码格式不正确");
        }
        if (!password.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的新密码不一致");
        }

        PasswordResetPermitStore.ResetPermit permit = passwordResetPermitStore.get(username);
        if (permit == null) {
            throw new RuntimeException("重置密码许可无效或已过期，请重新验证身份");
        }
        if (!resetToken.equals(permit.resetToken())) {
            log.warn("重置密码失败：resetToken 不匹配，username={}, userId={}", username, permit.userId());
            throw new RuntimeException("重置密码许可无效");
        }
        if (permit.expireAtMillis() == null || System.currentTimeMillis() >= permit.expireAtMillis()) {
            passwordResetPermitStore.delete(username);
            throw new RuntimeException("重置密码许可已过期，请重新验证身份");
        }

        int rows = userMapper.updatePasswordById(permit.userId(), password);
        if (rows != 1) {
            log.error("重置密码失败：数据库更新行数异常，username={}, userId={}, rows={}",
                    username, permit.userId(), rows);
            throw new RuntimeException("重置密码失败，请稍后再试");
        }

        // 重置密码属于高风险操作：递增 tokenVersion 让旧 JWT 失效，并让旧 refreshToken 全部失效。
        tokenVersionService.increaseTokenVersion(permit.userId());
        logoutAllSessionsByUserId(permit.userId());
        loginUserCacheStore.delete(username);
        passwordResetPermitStore.delete(username);

        log.info("重置密码成功，username={}, userId={}", username, permit.userId());
    }

    @Override
    public UserProfileResponse getCurrentProfile(Long userId) {
        validateUserId(userId);
        UserProfileResponse cachedProfile = userProfileCacheStore.get(userId);
        if (cachedProfile != null) {
            return cachedProfile;
        }
        if (userProfileCacheStore.isNullCached(userId)) {
            throw new RuntimeException("用户不存在");
        }

        String lockToken = userProfileCacheStore.tryLock(userId);
        if (StringUtils.hasText(lockToken)) {
            try {
                UserDTO user = userMapper.selectById(userId);
                if (user == null) {
                    userProfileCacheStore.saveNull(userId);
                    throw new RuntimeException("用户不存在");
                }
                if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
                    throw new RuntimeException("账号已被禁用");
                }
                UserProfileResponse response = toUserProfileResponse(user, queryLatestLoginAtMillis(userId));
                userProfileCacheStore.save(response);
                return response;
            } finally {
                userProfileCacheStore.unlock(userId, lockToken);
            }
        }

        userProfileCacheStore.shortWait();
        UserProfileResponse retryProfile = userProfileCacheStore.get(userId);
        if (retryProfile != null) {
            return retryProfile;
        }
        if (userProfileCacheStore.isNullCached(userId)) {
            throw new RuntimeException("用户不存在");
        }

        UserDTO user = getEnabledUserById(userId);
        UserProfileResponse response = toUserProfileResponse(user, queryLatestLoginAtMillis(userId));
        userProfileCacheStore.save(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateCurrentProfile(Long userId, UserProfileUpdateRequest request) {
        validateUserId(userId);
        if (request == null) {
            throw new RuntimeException("修改资料参数不能为空");
        }

        String email = trim(request.getEmail());
        String phone = trim(request.getPhone());
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (!StringUtils.hasText(phone)) {
            throw new RuntimeException("手机号不能为空");
        }

        request.setUserId(userId);

        int rows = userMapper.updateProfileById(request);
        if (rows != 1) {
            throw new RuntimeException("修改用户资料失败，请稍后再试");
        }

        UserDTO updatedUser = getEnabledUserById(userId);
        deleteUserProfileCacheAfterCommit(userId);
        loginUserCacheStore.delete(updatedUser.getUsername());
        return toUserProfileResponse(updatedUser, queryLatestLoginAtMillis(userId));
    }

    @Override
    public void clearCurrentProfileCache(Long userId) {
        validateUserId(userId);
        userProfileCacheStore.delete(userId);
    }

    /**
     * 修改当前登录用户密码。
     *
     * <p>修改密码后会递增 tokenVersion，并把旧会话全部标记为退出。
     * 随后给当前前端重新签发一套新的 accessToken、refreshToken、sessionId，
     * 这样用户无需重新登录也可以继续请求。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse changeCurrentPassword(Long userId,
                                               String authorizationHeader,
                                               ChangePasswordRequest request,
                                               String clientIp) {
        validateUserId(userId);
        if (request == null) {
            throw new RuntimeException("修改密码参数不能为空");
        }

        String oldPassword = trim(request.getOldPassword());
        String newPassword = trim(request.getNewPassword());
        String confirmPassword = trim(request.getConfirmPassword());

        if (!StringUtils.hasText(oldPassword)) {
            throw new RuntimeException("原始密码不能为空");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new RuntimeException("新密码不能为空");
        }
        if (!StringUtils.hasText(confirmPassword)) {
            throw new RuntimeException("确认新密码不能为空");
        }
        if (!isBase64(oldPassword) || !isBase64(newPassword) || !isBase64(confirmPassword)) {
            throw new RuntimeException("密码格式不正确");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        validatePasswordPolicy(decodeBase64ToString(newPassword));

        UserDTO user = getEnabledUserById(userId);
        if (!oldPassword.equals(user.getPassword())) {
            throw new RuntimeException("原始密码不正确");
        }

        DecodedJWT oldJwt = jwtSignTool.verify(authorizationHeader);
        String oldJwtId = oldJwt.getId();
        Long oldExpiresAtMillis = oldJwt.getClaim("expiresAtMillis").asLong();

        int rows = userMapper.updatePasswordById(userId, newPassword);
        if (rows != 1) {
            throw new RuntimeException("修改密码失败，请稍后再试");
        }

        Long newTokenVersion = tokenVersionService.increaseTokenVersion(userId);
        logoutAllSessionsByUserId(userId);
        loginUserCacheStore.delete(user.getUsername());

        SignedJwt signedJwt = jwtSignTool.signWithSessionInfo(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                newTokenVersion
        );
        IssuedSession issuedSession = createSessionAndAccessCache(user, signedJwt, clientIp, null, null);

        if (StringUtils.hasText(oldJwtId) && oldExpiresAtMillis != null) {
            redisTokenSessionStore.revokeSession(oldJwtId, oldExpiresAtMillis);
        }
        deleteUserProfileCacheAfterCommit(userId);

        log.info("用户修改密码成功并重新签发登录态，userId={}, newSessionId={}, newJwtId={}",
                userId, issuedSession.sessionId(), signedJwt.jwtId());

        return buildLoginResponse(user, signedJwt, issuedSession);
    }

    /**
     * 使用 refreshToken 刷新 accessToken。
     *
     * <p>刷新成功后会轮换 refreshToken：旧 refreshToken 立即失效，
     * 前端必须保存本次响应里的新 refreshToken。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse refreshAccessToken(RefreshTokenRequest request, String clientIp) {
        if (request == null) {
            throw new RuntimeException("刷新登录态参数不能为空");
        }

        String sessionId = trim(request.getSessionId());
        String refreshToken = trim(request.getRefreshToken());
        if (!StringUtils.hasText(sessionId)) {
            throw new RuntimeException("sessionId 不能为空");
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new RuntimeException("refreshToken 不能为空");
        }

        // 会话级 revoked 是 refreshToken 的实时黑名单。
        // 主动退出后 MySQL 可能还没被 MQ 更新成 EXPIRED，但这里必须立即拒绝旧 refreshToken 续签。
        if (redisTokenSessionStore.isSessionRevoked(sessionId)) {
            throw new RuntimeException("登录会话已退出，请重新登录");
        }

        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setSessionId(sessionId);
        UserSession session = sessionMapper.selectActiveBySessionId(queryParam);
        if (session == null) {
            throw new RuntimeException("登录会话不存在或已退出");
        }

        if (StringUtils.hasText(session.getClientIp())
                && StringUtils.hasText(clientIp)
                && !session.getClientIp().equals(clientIp)) {
            throw new RuntimeException("登录 IP 已变化，请重新登录");
        }

        LocalDateTime now = LocalDateTime.now();
        // 1. refreshToken 过期
        if (session.getRefreshExpiresAt() == null || !session.getRefreshExpiresAt().isAfter(now)) {
            // refreshToken 已经过期：该 session 不能再续签，需要立即失效。
            expireSessionByEvent(session, CLOSE_REASON_REFRESH_EXPIRED, now);
            throw new RuntimeException("refreshToken 已过期，请重新登录");
        }
        // 2. refreshToken 不匹配
        String requestRefreshTokenHash = refreshTokenTool.sha256(refreshToken);
        if (!requestRefreshTokenHash.equals(session.getRefreshTokenHash())) {
            // refreshToken hash 不匹配，常见原因是同设备新浏览器已经接管了该 session，
            // 数据库中保存的是新 refreshToken hash，旧浏览器仍拿旧 refreshToken 尝试续签。
            // 这里不能把当前 ACTIVE session 标记为 EXPIRED，否则旧浏览器一次刷新失败会把新浏览器接管后的会话误踢下线。
            // 正确做法是拒绝本次旧 refreshToken，旧浏览器清理本地登录态并回到登录页。

            log.warn("刷新登录态失败：refreshToken hash 不匹配，sessionId={}, userId={}",
                    sessionId, session.getUserId());

            throw new RuntimeException("已在相同设备的其他地方登录");
        }
        // 3. tokenVersion 不一致
        Long currentTokenVersion = tokenVersionService.getCurrentTokenVersion(session.getUserId());
        if (!currentTokenVersion.equals(session.getTokenVersion())) {
            // tokenVersion 不一致，说明用户可能修改了密码、重置了密码，或者被强制下线。
            // 当前 session 必须失效。
            expireSessionByEvent(session, CLOSE_REASON_TOKEN_VERSION_CHANGED, now);
            throw new RuntimeException("登录状态已失效，请重新登录");
        }

        UserDTO user = getEnabledUserById(session.getUserId());
        SignedJwt signedJwt = jwtSignTool.signWithSessionInfo(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                currentTokenVersion
        );

        String newRefreshToken = refreshTokenTool.generateRefreshToken();
        LocalDateTime accessExpiresAt = toLocalDateTime(signedJwt.expiresAtMillis());
        LocalDateTime refreshExpiresAt = now.plusSeconds(getRefreshTokenExpireSeconds());

        String oldAccessJti = session.getAccessJti();
        LocalDateTime oldAccessExpiresAt = session.getAccessExpiresAt();

        session.setRefreshTokenHash(refreshTokenTool.sha256(newRefreshToken));
        session.setAccessJti(signedJwt.jwtId());
        session.setTokenVersion(signedJwt.tokenVersion());
        session.setLastActiveAt(now);

        // refresh 本身需要轮换 token 并更新 user_session，因此顺手把 MySQL 的在线快照恢复为 ONLINE。
        // 页面实时在线/离线仍然只看 Redis presence；普通浏览器离线不会为了 online_status 单独写 MySQL。
        session.setOnlineStatus(ONLINE_STATUS_ONLINE);
        session.setOfflineAt(null);

        session.setAccessExpiresAt(accessExpiresAt);
        session.setRefreshExpiresAt(refreshExpiresAt);
        session.setUpdateTime(now);

        int rows = sessionMapper.rotateSessionToken(session);
        if (rows != 1) {
            throw new RuntimeException("刷新登录态失败，请重新登录");
        }

        redisTokenSessionStore.saveSession(buildRedisSession(
                user,
                signedJwt,
                sessionId,
                toEpochMillis(refreshExpiresAt),
                session.getClientIp(),
                session.getDeviceId()
        ));
        userSessionOnlineStore.markOnline(sessionId);
        heartbeatSessionCacheStore.save(session);
        // refresh 只是轮换当前会话的 token，不会新增或删除会话列表成员。
        // 如果这里递增会话列表缓存版本，浏览器每次刷新页面后都会重新 count/select MySQL，
        // 账户中心就会出现明显卡顿。因此会话列表缓存只在登录、退出、过期等成员变化时失效。
        if (StringUtils.hasText(oldAccessJti) && oldAccessExpiresAt != null) {
            redisTokenSessionStore.revokeSession(oldAccessJti, toEpochMillis(oldAccessExpiresAt));
        }

        log.info("刷新登录态成功，userId={}, sessionId={}, newJwtId={}",
                user.getId(), sessionId, signedJwt.jwtId());

        return buildLoginResponse(
                user,
                signedJwt,
                new IssuedSession(newRefreshToken, sessionId, toEpochMillis(refreshExpiresAt), false)
        );
    }

    @Override
    public PageResponse<UserSessionResponse> listCurrentSessions(
            Long userId,
        String currentSessionId,
        Integer pageNum,
        Integer pageSize) {
        validateUserId(userId);

        int safePageSize = normalizeSessionPageSize(pageSize);
        int requestedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        String sessionListVersion = userSessionListCacheStore.currentVersion(userId);
        UserSessionListCacheStore.CacheValue cachedPage =
                userSessionListCacheStore.get(userId, sessionListVersion, requestedPageNum, safePageSize);
        if (cachedPage != null) {
            return buildSessionPageFromCachedValue(cachedPage, currentSessionId);
        }

        long total = sessionMapper.countActiveByUserId(buildUserSessionQuery(userId));
        long pages = total == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
        int safePageNum = normalizeSessionPageNum(pageNum, pages);

        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        queryParam.setPageNum(safePageNum);
        queryParam.setPageSize(safePageSize);
        queryParam.setOffset((safePageNum - 1) * safePageSize);

        LocalDateTime now = LocalDateTime.now();
        List<UserSession> sessions = sessionMapper.selectActivePageByUserId(queryParam);

        List<UserSession> visibleSessions = sessions.stream()
                .filter(session -> {
                    if (isRevokedSession(session)) {
                        return false;
                    }
                    if (session.getRefreshExpiresAt() != null && !session.getRefreshExpiresAt().isAfter(now)) {
                        expireSessionByEvent(session, CLOSE_REASON_REFRESH_EXPIRED, now);
                        userSessionListCacheStore.invalidate(userId);
                        return false;
                    }
                    return true;
                })
                .toList();

        long visibleTotal = Math.max(0L, total - (sessions.size() - visibleSessions.size()));
        userSessionListCacheStore.save(userId, sessionListVersion, safePageNum, safePageSize, visibleTotal, visibleSessions);

        List<UserSessionResponse> records = visibleSessions.stream()
                .map(session -> toUserSessionResponse(session, currentSessionId))
                .toList();

        return PageResponse.of(records, visibleTotal, safePageNum, safePageSize);
    }

    /**
     * 当前浏览器会话 heartbeat。
     *
     * <p>heartbeat 只维护“在线状态”，不会新签发 token，也不会延长 refreshToken 授权期限。
     * 这样可以把“浏览器是否还开着”和“该设备是否仍被授权登录”拆开，避免关闭浏览器后误以为已经退出登录。</p>
     */
    @Override
    public void heartbeatCurrentSession(Long userId, String accessJti, SessionHeartbeatRequest request) {
        validateUserId(userId);
        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            throw new RuntimeException("sessionId 不能为空");
        }

        String sessionId = trim(request.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        HeartbeatSessionCacheStore.HeartbeatSessionSnapshot cachedSession = heartbeatSessionCacheStore.get(sessionId);
        if (cachedSession != null && cachedSession.getUserId().equals(userId)) {
            if (heartbeatSessionCacheStore.isExpired(cachedSession, now)) {
                heartbeatSessionCacheStore.delete(sessionId);
                throw new RuntimeException("登录会话已过期，请重新登录");
            }
            userSessionOnlineStore.markOnline(sessionId);
            return;
        }

        RedisTokenSession redisSession = redisTokenSessionStore.getSession(accessJti);
        if (redisSession != null
                && userId.equals(redisSession.getUserId())
                && sessionId.equals(redisSession.getSessionId())
                && redisSession.getRefreshExpiresAtMillis() != null
                && redisSession.getRefreshExpiresAtMillis() > System.currentTimeMillis()) {
            heartbeatSessionCacheStore.save(redisSession);
            userSessionOnlineStore.markOnline(sessionId);
            return;
        }

        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        queryParam.setSessionId(sessionId);

        UserSession session = sessionMapper.selectActiveBySessionId(queryParam);
        if (session == null) {
            throw new RuntimeException("登录会话不存在或已退出");
        }

        if (session.getRefreshExpiresAt() == null || !session.getRefreshExpiresAt().isAfter(now)) {
            // heartbeat 时发现 refreshToken 已过期，说明该会话已经不能继续使用。
            // 这里同样走 Redis 立即失效 + MQ 异步落库。
            expireSessionByEvent(session, CLOSE_REASON_REFRESH_EXPIRED, now);
            throw new RuntimeException("登录会话已过期，请重新登录");
        }

        userSessionOnlineStore.markOnline(sessionId);
        heartbeatSessionCacheStore.save(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logoutCurrentUserSession(Long userId, String sessionId) {
        validateUserId(userId);
        if (!StringUtils.hasText(sessionId)) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        queryParam.setSessionId(sessionId);

        UserSession session = sessionMapper.selectActiveBySessionId(queryParam);
        if (session == null) {
            throw new RuntimeException("会话不存在或已退出");
        }

        expireSessionByEvent(session, CLOSE_REASON_LOGOUT, LocalDateTime.now());

        log.info("用户退出指定会话成功，userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 清理 Redis 中已经超时的在线索引。
     *
     * <p>浏览器普通离线不在请求线程里同步写 MySQL。实时在线状态只以 Redis presence 为准：
     * heartbeat 会刷新 `auth:presence:{sessionId}` 的短 TTL，浏览器关闭后该 Key 自然过期，
     * 页面展示时 Redis miss 即表示离线。</p>
     *
     * <p>该任务只扫描 Redis 的 `auth:presence:lastseen`，没有超时成员时不会访问 MySQL。
     * 发现超时成员后发送 SESSION_OFFLINE 消息，由 Consumer 异步更新 MySQL 的
     * online_status / offline_at / last_active_at，保证页面实时性由 Redis 提供，
     * MySQL 最终一致用于审计和后续统计。</p>
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void syncInactiveSessionsOffline() {
        List<UserSessionOnlineStore.OfflineCandidate> expiredSessions = userSessionOnlineStore.cleanupExpiredOnlineSessionIndex(
                System.currentTimeMillis() - 35_000L,
                500L
        );
        if (expiredSessions.isEmpty()) {
            return;
        }
        LocalDateTime offlineAt = LocalDateTime.now();
        for (UserSessionOnlineStore.OfflineCandidate candidate : expiredSessions) {
            try {
                sessionEventPublisher.publishSessionOffline(new SessionOfflineEvent(
                        UUID.randomUUID().toString(),
                        candidate.getSessionId(),
                        candidate.getLastSeenMillis(),
                        offlineAt
                ));
            } catch (Exception exception) {
                log.warn("发送会话离线事件失败，sessionId={}", candidate.getSessionId(), exception);
            }
        }
        log.info("清理 Redis 超时在线索引完成，count={}", expiredSessions.size());
    }

    private IssuedSession createSessionAndAccessCache(UserDTO user,
                                                      SignedJwt signedJwt,
                                                      String clientIp,
                                                      String userAgent,
                                                      LoginRequest request) {
        String refreshToken = refreshTokenTool.generateRefreshToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiresAt = toLocalDateTime(signedJwt.expiresAtMillis());
        LocalDateTime refreshExpiresAt = now.plusSeconds(getRefreshTokenExpireSeconds());
        String safeClientIp = normalizeClientIp(clientIp);
        String deviceId = buildDeviceId(user.getId(), safeClientIp, request == null ? null : request.getDeviceFingerprint(), userAgent);
        String deviceName = resolveLoginDeviceName(request, userAgent);
        String lockKey = AuthRedisKeys.deviceLoginLockKey(user.getId(), deviceId);
        String lockToken = cacheProtectionSupport.tryLock(lockKey, DEVICE_LOGIN_LOCK_TTL);
        if (!StringUtils.hasText(lockToken)) {
            cacheProtectionSupport.shortWait();
            lockToken = cacheProtectionSupport.tryLock(lockKey, DEVICE_LOGIN_LOCK_TTL);
        }
        if (!StringUtils.hasText(lockToken)) {
            throw new RuntimeException("当前设备正在登录，请稍后重试");
        }

        try {
            UserSession existingSession = findTakeoverSession(user.getId(), deviceId, safeClientIp, deviceName, userAgent);
            if (existingSession != null) {
                String oldAccessJti = existingSession.getAccessJti();
                LocalDateTime oldAccessExpiresAt = existingSession.getAccessExpiresAt();

                existingSession.setRefreshTokenHash(refreshTokenTool.sha256(refreshToken));
                existingSession.setAccessJti(signedJwt.jwtId());
                existingSession.setTokenVersion(signedJwt.tokenVersion());
                existingSession.setClientIp(safeClientIp);
                existingSession.setUserAgent(userAgent);
                existingSession.setDeviceId(deviceId);
                existingSession.setDeviceName(deviceName);
                existingSession.setLastActiveAt(now);
                existingSession.setOnlineStatus(ONLINE_STATUS_ONLINE);
                existingSession.setOfflineAt(null);
                existingSession.setAccessExpiresAt(accessExpiresAt);
                existingSession.setRefreshExpiresAt(refreshExpiresAt);
                existingSession.setUpdateTime(now);

                int rows = sessionMapper.updateTakeoverSessionToken(existingSession);
                if (rows != 1) {
                    throw new RuntimeException("接管当前设备会话失败，请稍后重试");
                }

                if (StringUtils.hasText(oldAccessJti) && oldAccessExpiresAt != null) {
                    redisTokenSessionStore.revokeSession(oldAccessJti, toEpochMillis(oldAccessExpiresAt));
                }
                redisTokenSessionStore.saveSession(buildRedisSession(
                        user,
                        signedJwt,
                        existingSession.getSessionId(),
                        toEpochMillis(refreshExpiresAt),
                        safeClientIp,
                        deviceId
                ));
                userSessionOnlineStore.markOnline(existingSession.getSessionId());
                heartbeatSessionCacheStore.save(existingSession);
                expireDuplicateSameDeviceSessions(user.getId(), safeClientIp, existingSession.getSessionId(), deviceId, deviceName, userAgent, now);
                userSessionListCacheStore.invalidate(user.getId());
                deleteUserProfileCacheAfterCommit(user.getId());
                return new IssuedSession(refreshToken, existingSession.getSessionId(), toEpochMillis(refreshExpiresAt), true);
            }

            String sessionId = UUID.randomUUID().toString().replace("-", "");
            UserSession userSession = new UserSession();
            userSession.setSessionId(sessionId);
            userSession.setUserId(user.getId());
            userSession.setRefreshTokenHash(refreshTokenTool.sha256(refreshToken));
            userSession.setAccessJti(signedJwt.jwtId());
            userSession.setTokenVersion(signedJwt.tokenVersion());
            userSession.setDeviceId(deviceId);
            userSession.setClientIp(safeClientIp);
            userSession.setDeviceName(deviceName);
            userSession.setUserAgent(userAgent);
            userSession.setStatus(SESSION_STATUS_ACTIVE);
            userSession.setOnlineStatus(ONLINE_STATUS_ONLINE);
            userSession.setLoginAt(now);
            userSession.setLastActiveAt(now);
            userSession.setAccessExpiresAt(accessExpiresAt);
            userSession.setRefreshExpiresAt(refreshExpiresAt);
            userSession.setCreateTime(now);
            userSession.setUpdateTime(now);

            int rows = sessionMapper.insertSession(userSession);
            if (rows != 1) {
                log.error("写入用户会话表失败，userId={}, rows={}", user.getId(), rows);
                throw new RuntimeException("登录失败，请稍后再试");
            }

            redisTokenSessionStore.saveSession(buildRedisSession(
                    user,
                    signedJwt,
                    sessionId,
                    toEpochMillis(refreshExpiresAt),
                    safeClientIp,
                    deviceId
            ));
            userSessionOnlineStore.markOnline(sessionId);
            heartbeatSessionCacheStore.save(userSession);
            expireDuplicateSameDeviceSessions(user.getId(), safeClientIp, sessionId, deviceId, deviceName, userAgent, now);
            userSessionListCacheStore.invalidate(user.getId());
            deleteUserProfileCacheAfterCommit(user.getId());
            return new IssuedSession(refreshToken, sessionId, toEpochMillis(refreshExpiresAt), false);
        } finally {
            cacheProtectionSupport.unlock(lockKey, lockToken);
        }
    }

    /**
     * 查找本次登录应该接管的同设备会话。
     *
     * <p>第一优先级按新的设备级 deviceId 精确查找；如果找不到，再按
     * userId + clientIp 查询历史 ACTIVE 会话，并用操作系统族做兜底匹配。
     * 这个兜底用于兼容旧版本“浏览器级 deviceId”留下的 Edge / Chrome 双会话。</p>
     */
    private UserSession findTakeoverSession(Long userId,
                                            String deviceId,
                                            String clientIp,
                                            String deviceName,
                                            String userAgent) {
        UserSessionQueryParam exactQuery = new UserSessionQueryParam();
        exactQuery.setUserId(userId);
        exactQuery.setDeviceId(deviceId);
        UserSession exactSession = sessionMapper.selectActiveByUserIdAndDeviceId(exactQuery);
        if (exactSession != null && !isRevokedSession(exactSession)) {
            return exactSession;
        }

        UserSessionQueryParam ipQuery = new UserSessionQueryParam();
        ipQuery.setUserId(userId);
        ipQuery.setClientIp(clientIp);
        return sessionMapper.selectActiveByUserIdAndClientIp(ipQuery)
                .stream()
                .filter(session -> !isRevokedSession(session))
                .filter(session -> isLegacyDeviceId(session.getDeviceId()))
                .filter(session -> isSameDeviceFamily(session, deviceName, userAgent))
                .findFirst()
                .orElse(null);
    }

    /**
     * 清理同设备下重复存在的 ACTIVE 会话。
     *
     * <p>用户当前数据库中可能已经存在 Edge 和 Chrome 两条旧 ACTIVE 会话。
     * 新登录接管其中一条后，其他同 IP、同操作系统族的会话必须立即吊销 Redis token，
     * 并通过 MQ 异步落库为 EXPIRED，避免账户中心继续展示两个在线会话。</p>
     */
    private void expireDuplicateSameDeviceSessions(Long userId,
                                                   String clientIp,
                                                   String keepSessionId,
                                                   String currentDeviceId,
                                                   String deviceName,
                                                   String userAgent,
                                                   LocalDateTime occurredAt) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        queryParam.setClientIp(clientIp);
        List<UserSession> sessions = sessionMapper.selectActiveByUserIdAndClientIp(queryParam);
        for (UserSession session : sessions) {
            if (session == null || keepSessionId.equals(session.getSessionId())) {
                continue;
            }
            if (isRevokedSession(session)) {
                continue;
            }
            if (!isSameDeviceSessionForCleanup(session, currentDeviceId, deviceName, userAgent)) {
                continue;
            }
            expireSessionByEvent(session, CLOSE_REASON_SAME_DEVICE_TAKEOVER, occurredAt);
        }
    }

    /**
     * 判断某个会话是否属于本次登录要清理的同设备重复会话。
     *
     * <p>新版本 deviceId 带有 v2_ 前缀，新会话之间必须精确匹配 deviceId 才能互相接管；
     * 只有历史无版本前缀的旧 deviceId，才允许按同 IP + 操作系统族兜底合并。</p>
     */
    private boolean isSameDeviceSessionForCleanup(UserSession session,
                                                  String currentDeviceId,
                                                  String currentDeviceName,
                                                  String currentUserAgent) {
        if (currentDeviceId.equals(session.getDeviceId())) {
            return true;
        }
        return isLegacyDeviceId(session.getDeviceId())
                && isSameDeviceFamily(session, currentDeviceName, currentUserAgent);
    }

    /**
     * 判断是否为旧版本浏览器级 deviceId。
     *
     * <p>旧版本 deviceId 没有版本前缀，可能把 Edge 和 Chrome 算成两个设备。
     * 新版本统一使用 v2_ 前缀，后续不同电脑即使同 IP + 同操作系统，也不会被兜底误合并。</p>
     */
    private boolean isLegacyDeviceId(String deviceId) {
        return !StringUtils.hasText(deviceId) || !deviceId.startsWith(DEVICE_ID_VERSION_PREFIX);
    }

    /**
     * 判断历史会话是否属于本次登录的同一台设备。
     *
     * <p>纯 Web 无法获取真实硬件 ID，因此这里采用“可信 IP + 操作系统族”的兼容判断。
     * 浏览器名称和浏览器版本不参与判断，避免 Edge、Chrome 被识别成两台设备。</p>
     */
    private boolean isSameDeviceFamily(UserSession session, String currentDeviceName, String currentUserAgent) {
        String currentFamily = resolveDeviceFamily(currentDeviceName, currentUserAgent);
        String sessionFamily = resolveDeviceFamily(session.getDeviceName(), session.getUserAgent());
        return StringUtils.hasText(currentFamily)
                && !"UNKNOWN".equals(currentFamily)
                && currentFamily.equals(sessionFamily);
    }

    private UserSessionResponse toUserSessionResponse(UserSession session, String currentSessionId) {
        return new UserSessionResponse(
                session.getSessionId(),
                StringUtils.hasText(currentSessionId) && currentSessionId.equals(session.getSessionId()),
                userSessionOnlineStore.isOnline(session.getSessionId()),
                StringUtils.hasText(session.getClientIp()) ? session.getClientIp() : "未知 IP",
                StringUtils.hasText(session.getDeviceName()) ? session.getDeviceName() : resolveDeviceName(session.getUserAgent()),
                StringUtils.hasText(session.getUserAgent()) ? session.getUserAgent() : "未知客户端",
                session.getStatus(),
                toEpochMillis(session.getLoginAt()),
                toEpochMillis(session.getLastActiveAt()),
                toEpochMillis(session.getRefreshExpiresAt())
        );
    }

    /**
     * 根据 Redis 缓存的会话分页记录构建前端响应。
     *
     * <p>缓存中保存的是 user_session 原始记录，不直接保存 online 布尔值。
     * 返回前仍然通过 Redis presence 重新计算在线状态，避免短 TTL 缓存让在线/离线标签长期不准确。</p>
     */
    private PageResponse<UserSessionResponse> buildSessionPageFromCachedValue(
            UserSessionListCacheStore.CacheValue cachedPage,
            String currentSessionId) {
        List<UserSessionResponse> records = cachedPage.getRecords()
                .stream()
                .filter(session -> !isRevokedSession(session))
                // 缓存页保存的是“列表成员快照”，online 字段仍实时查 Redis。
                // refresh 会更新数据库中的 refresh_expires_at，但不会让列表成员变化；
                // 因此缓存命中时不能再用旧的 refresh_expires_at 过滤，否则用户刷新后可能误看不到仍有效的当前会话。
                .map(session -> toUserSessionResponse(session, currentSessionId))
                .toList();
        long removedFromCurrentPage = cachedPage.getRecords().size() - records.size();
        long visibleTotal = Math.max(0L, cachedPage.getTotal() - removedFromCurrentPage);
        return PageResponse.of(records, visibleTotal, cachedPage.getPageNum(), cachedPage.getPageSize());
    }

    private UserSessionQueryParam buildUserSessionQuery(Long userId) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        return queryParam;
    }

    private int normalizeSessionPageSize(Integer pageSize) {
        if (pageSize == null) {
            return 5;
        }
        if (pageSize == 5 || pageSize == 10 || pageSize == 20) {
            return pageSize;
        }
        return 5;
    }

    private int normalizeSessionPageNum(Integer pageNum, long pages) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        if (pages > 0 && pageNum > pages) {
            return (int) pages;
        }
        return pageNum;
    }

    private String resolveDeviceName(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "未知设备";
        }

        String value = userAgent.toLowerCase();
        String os;
        if (value.contains("windows")) {
            os = "Windows";
        } else if (value.contains("mac os") || value.contains("macintosh")) {
            os = "macOS";
        } else if (value.contains("iphone")) {
            os = "iPhone";
        } else if (value.contains("ipad")) {
            os = "iPad";
        } else if (value.contains("android")) {
            os = "Android";
        } else if (value.contains("linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }

        String browser;
        if (value.contains("edg/")) {
            browser = "Edge";
        } else if (value.contains("chrome/")) {
            browser = "Chrome";
        } else if (value.contains("firefox/")) {
            browser = "Firefox";
        } else if (value.contains("safari/")) {
            browser = "Safari";
        } else {
            browser = "浏览器";
        }

        return os + " · " + browser;
    }

    /**
     * 解析设备操作系统族。
     *
     * <p>同设备接管只用操作系统族做兜底，不读取浏览器名称和浏览器版本。
     * 这样 Edge、Chrome、Firefox 等不同浏览器会归到同一台 Windows/macOS 设备。</p>
     */
    private String resolveDeviceFamily(String deviceName, String userAgent) {
        String value = ((deviceName == null ? "" : deviceName) + " " + (userAgent == null ? "" : userAgent)).toLowerCase();
        if (value.contains("windows")) {
            return "WINDOWS";
        }
        if (value.contains("iphone")) {
            return "IPHONE";
        }
        if (value.contains("ipad")) {
            return "IPAD";
        }
        if (value.contains("android")) {
            return "ANDROID";
        }
        if (value.contains("mac os") || value.contains("macintosh") || value.contains("macos")) {
            return "MACOS";
        }
        if (value.contains("linux")) {
            return "LINUX";
        }
        return "UNKNOWN";
    }

    /**
     * 解析登录展示用设备名称。
     *
     * <p>deviceName 只用于页面展示，不参与安全判断；后端会截断长度，避免异常输入污染数据库。</p>
     */
    private String resolveLoginDeviceName(LoginRequest request, String userAgent) {
        String deviceName = request == null ? null : trim(request.getDeviceName());
        if (!StringUtils.hasText(deviceName)) {
            deviceName = resolveDeviceName(userAgent);
        }
        return deviceName.length() > 64 ? deviceName.substring(0, 64) : deviceName;
    }

    /**
     * 计算同设备会话归属 ID。
     *
     * <p>deviceId 是 userId、登录 IP 和设备指纹的哈希结果，用于“同设备单 ACTIVE 会话”。
     * 它是工程近似值，不作为唯一安全认证依据。新版本统一带 v2_ 前缀，
     * 方便兼容合并旧浏览器级 deviceId，同时避免后续不同电脑被 OS 兜底误合并。</p>
     */
    private String buildDeviceId(Long userId, String clientIp, String deviceFingerprint, String userAgent) {
        String safeFingerprint = StringUtils.hasText(deviceFingerprint)
                ? deviceFingerprint.trim()
                : "unknown-device";
        if (safeFingerprint.length() > 512) {
            safeFingerprint = safeFingerprint.substring(0, 512);
        }
        return DEVICE_ID_VERSION_PREFIX + sha256Hex(userId + "|" + normalizeClientIp(clientIp) + "|" + safeFingerprint);
    }

    /**
     * 标准化登录 IP。
     */
    private String normalizeClientIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "0.0.0.0";
    }

    /**
     * 计算 SHA-256 十六进制字符串。
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("计算设备 ID 失败", exception);
        }
    }

    private RedisTokenSession buildRedisSession(UserDTO user,
                                                SignedJwt signedJwt,
                                                String sessionId,
                                                Long refreshExpiresAtMillis,
                                                String boundIp,
                                                String deviceId) {
        RedisTokenSession session = new RedisTokenSession();
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setRole(user.getRole());
        session.setJwtId(signedJwt.jwtId());
        session.setSessionId(sessionId);
        session.setIssuedAtMillis(signedJwt.issuedAtMillis());
        session.setExpiresAtMillis(signedJwt.expiresAtMillis());
        session.setRefreshExpiresAtMillis(refreshExpiresAtMillis);
        session.setTokenVersion(signedJwt.tokenVersion());
        session.setBoundIp(boundIp);
        session.setDeviceId(deviceId);
        return session;
    }

    private LoginResponse buildLoginResponse(UserDTO user, SignedJwt signedJwt, IssuedSession issuedSession) {
        LoginResponse response = new LoginResponse();
        response.setToken(signedJwt.token());
        response.setRefreshToken(issuedSession.refreshToken());
        response.setSessionId(issuedSession.sessionId());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setAccessTokenExpiresAtMillis(signedJwt.expiresAtMillis());
        response.setRefreshTokenExpiresAtMillis(issuedSession.refreshTokenExpiresAtMillis());
        response.setSessionTakeover(issuedSession.sessionTakeover());
        response.setTakeoverMessage(Boolean.TRUE.equals(issuedSession.sessionTakeover())
                ? "在该设备上已存在会话，已接管原会话"
                : null);
        return response;
    }

    /**
     * 让指定会话失效，并优先通过 RocketMQ 异步更新 MySQL。
     *
     * <p>这是当前会话改造的核心方法，退出登录、refreshToken 过期、refreshToken 非法、
     * tokenVersion 变化等场景都应该走这里，避免每个业务入口重复写一套失效逻辑。</p>
     *
     * <p>执行顺序固定为：</p>
     * <ol>
     *     <li>先删除 Redis 登录态，并写入 accessToken 黑名单，让旧 token 立即不可用；</li>
     *     <li>再构造带 eventId 的会话失效事件，发送到 RocketMQ；</li>
     *     <li>如果 RocketMQ 不可用，则同步更新 MySQL 兜底，避免数据库长期保留 ACTIVE 脏状态。</li>
     * </ol>
     *
     * <p>这样做能保证安全性优先：Redis 是网关鉴权依据，必须立即变更；
     * MySQL 是展示和审计依据，可以最终一致。</p>
     */
    private void expireSessionByEvent(UserSession session, String closeReason, LocalDateTime occurredAt) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }

        LocalDateTime safeOccurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        String safeCloseReason = StringUtils.hasText(closeReason) ? closeReason : CLOSE_REASON_UNKNOWN;

        revokeRedisAccessSession(session);
        userSessionListCacheStore.invalidate(session.getUserId());
        userProfileCacheStore.delete(session.getUserId());

        SessionExpiredEvent event = new SessionExpiredEvent(
                UUID.randomUUID().toString(),
                session.getUserId(),
                session.getSessionId(),
                session.getAccessJti(),
                toEpochMillis(session.getAccessExpiresAt()),
                safeCloseReason,
                resolveLastActiveMillis(session, safeOccurredAt),
                safeOccurredAt
        );

        try {
            sessionEventPublisher.publishSessionExpired(event);
        } catch (Exception exception) {
            log.error("发送会话失效事件失败，降级为同步更新 MySQL，sessionId={}, userId={}, reason={}",
                    session.getSessionId(), session.getUserId(), safeCloseReason, exception);
            expireSessionInMysql(session, safeCloseReason, safeOccurredAt);
        }
    }

    /**
     * 同步把会话标记为 EXPIRED。
     *
     * <p>该方法只作为 RocketMQ 投递失败时的兜底方案，正常路径不直接依赖同步写库。
     * SQL 使用 sessionId + userId + status=ACTIVE 条件，因此重复调用是幂等的；
     * 如果 rows=0，说明该会话已经被其他请求或 MQ 消费更新过，可以视为成功。</p>
     */
    private void expireSessionInMysql(UserSession session, String closeReason, LocalDateTime occurredAt) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }

        LocalDateTime safeOccurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        String safeCloseReason = StringUtils.hasText(closeReason) ? closeReason : CLOSE_REASON_UNKNOWN;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> {
            UserSessionLogoutParam param = new UserSessionLogoutParam();
            param.setSessionId(session.getSessionId());
            param.setUserId(session.getUserId());
            param.setAccessJti(session.getAccessJti());
            param.setStatus(SESSION_STATUS_EXPIRED);
            param.setCloseReason(safeCloseReason);
            param.setExpiredAt(safeOccurredAt);
            param.setLogoutAt(CLOSE_REASON_LOGOUT.equals(safeCloseReason) ? safeOccurredAt : null);
            param.setLastActiveAt(toLocalDateTime(resolveLastActiveMillis(session, safeOccurredAt)));
            param.setUpdateTime(safeOccurredAt);
            sessionMapper.expireBySessionId(param);
        });
    }

    /**
     * 根据 accessToken jti 让当前会话失效。
     *
     * <p>该方法保留给内部扩展场景使用。真正的状态值统一写为 EXPIRED，
     * 主动退出原因通过 close_reason=LOGOUT 表达。</p>
     */
    private void logoutSessionByAccessJti(String accessJti) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setAccessJti(accessJti);
        UserSession session = sessionMapper.selectActiveByAccessJti(queryParam);

        if (session != null) {
            expireSessionByEvent(session, CLOSE_REASON_LOGOUT, LocalDateTime.now());
        }
    }

    /**
     * 根据 sessionId 让当前会话失效。
     *
     * <p>该方法保留给内部扩展场景使用。它会先查 ACTIVE 会话，
     * 然后复用统一的 Redis 立即失效 + MQ 异步落库流程。</p>
     */
    private void logoutSessionBySessionId(String sessionId) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setSessionId(sessionId);
        UserSession session = sessionMapper.selectActiveBySessionId(queryParam);
        if (session != null) {
            expireSessionByEvent(session, CLOSE_REASON_LOGOUT, LocalDateTime.now());
        }
    }

    /**
     * 让某个用户的所有 ACTIVE 会话失效。
     *
     * <p>修改密码、重置密码会递增 tokenVersion，旧会话不能再继续刷新。
     * 这里逐个吊销 Redis 登录态并发送 MQ 事件；如果 MQ 投递失败，会单个会话同步落库兜底。</p>
     */
    private void logoutAllSessionsByUserId(Long userId) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        List<UserSession> activeSessions = sessionMapper.selectActiveByUserId(queryParam);

        LocalDateTime now = LocalDateTime.now();
        activeSessions.forEach(session ->
                expireSessionByEvent(session, CLOSE_REASON_TOKEN_VERSION_CHANGED, now)
        );
    }

    /**
     * 立即吊销某个会话对应的 Redis access 登录态。
     *
     * <p>这里会删除 auth:session:{jti}，并把 jti 写入 auth:blacklist:{jti}。
     * blacklist 设置到 JWT 自然过期为止，防止并发请求拿着旧 JWT 再次通过网关。</p>
     */
    private void revokeRedisAccessSession(UserSession session) {
        if (session == null || !StringUtils.hasText(session.getAccessJti()) || session.getAccessExpiresAt() == null) {
            return;
        }
        redisTokenSessionStore.revokeSessionWithRefreshBlock(
                session.getAccessJti(),
                toEpochMillis(session.getAccessExpiresAt()),
                session.getSessionId(),
                toEpochMillis(session.getRefreshExpiresAt())
        );
    }

    /**
     * 判断会话是否已经被 Redis 会话级黑名单吊销。
     *
     * <p>该判断用于 refresh、登录接管和会话列表。它的目的不是替代 MySQL 最终状态，
     * 而是在 MQ 异步落库前先阻止旧 refreshToken 续签，并让账户中心不展示刚退出的旧会话。</p>
     */
    private boolean isRevokedSession(UserSession session) {
        return session != null && redisTokenSessionStore.isSessionRevoked(session.getSessionId());
    }

    private Long getRefreshTokenExpireSeconds() {
        Long expireSeconds = jwtProperties.getRefreshTokenExpireSeconds();
        if (expireSeconds == null || expireSeconds <= 0) {
            throw new RuntimeException("refreshToken 过期时间配置无效");
        }
        return expireSeconds;
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            throw new RuntimeException("时间戳不能为空");
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 解析会话最后活跃时间。
     *
     * <p>优先使用 Redis presence lastSeen，因为 heartbeat 不再同步写 MySQL；
     * 如果 Redis 中没有该值，则使用事件发生时间兜底。</p>
     */
    private Long resolveLastActiveMillis(UserSession session, LocalDateTime fallbackTime) {
        if (session != null && StringUtils.hasText(session.getSessionId())) {
            Long lastSeenMillis = userSessionOnlineStore.getLastSeenMillis(session.getSessionId());
            if (lastSeenMillis != null && lastSeenMillis > 0) {
                return lastSeenMillis;
            }
        }
        return toEpochMillis(fallbackTime == null ? LocalDateTime.now() : fallbackTime);
    }

    /**
     * 尽力清理退出登录相关的用户缓存。
     *
     * <p>正常退出可以从 MySQL ACTIVE 会话中拿到 userId，并由 expireSessionByEvent 清理资料缓存、
     * 会话列表缓存、登录态和在线态。这里主要兜底处理“会话已经不存在但用户又点击退出”的场景：
     * JWT 仍能解析出 userId 和 username，因此可以继续清理用户资料缓存、会话列表缓存版本、
     * 以及历史遗留的登录快照缓存。</p>
     */
    private void clearBestEffortLogoutCaches(String subject, String username) {
        Long userId = parseLongQuietly(subject);
        if (userId != null && userId > 0) {
            userProfileCacheStore.delete(userId);
            userSessionListCacheStore.invalidate(userId);
        }
        deleteLoginSnapshotCache(username);
    }

    /**
     * 删除用户名维度登录快照缓存。
     *
     * <p>当前登录流程已经改为每次查 MySQL，不再用该缓存参与密码校验。
     * 但旧环境或灰度期间 Redis 中可能残留 `auth:user:login:{username}`，
     * 退出、改密码、重置密码时继续清理它，避免历史缓存干扰后续行为。</p>
     */
    private void deleteLoginSnapshotCache(String username) {
        if (StringUtils.hasText(username)) {
            loginUserCacheStore.delete(username);
        }
    }

    /**
     * 安全解析 Long，解析失败时返回 null。
     */
    private Long parseLongQuietly(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 在数据库事务提交成功后删除用户资料缓存。
     *
     * <p>用户资料页缓存属于典型的 cache-aside 读缓存。
     * 修改资料、修改密码和登录会影响资料页展示字段，因此必须先让 MySQL 事务成功提交，
     * 再删除 Redis 缓存，避免“先删缓存、后更新数据库”时并发读请求把旧数据重新写回缓存。</p>
     */
    private void deleteUserProfileCacheAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userProfileCacheStore.delete(userId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userProfileCacheStore.delete(userId);
            }
        });
    }

    private boolean isBase64(String value) {
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 解码前端传入的 Base64 密码。
     *
     * <p>当前项目为了避免明文密码直接出现在请求体中，前端会先把密码做 Base64 编码。
     * 数据库存储和旧密码比对仍然沿用编码后的字符串，但密码强度校验必须针对用户真实输入的密码执行。</p>
     */
    private String decodeBase64ToString(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /**
     * 校验新密码是否满足账户安全策略。
     *
     * <p>规则与前端修改密码弹窗保持一致：
     * 1. 至少 8 个字符；
     * 2. 大写字母、小写字母、数字、特殊符号四类中至少满足两类。
     * 后端再次校验可以防止用户绕过前端直接调用接口提交弱密码。</p>
     */
    private void validatePasswordPolicy(String password) {
        if (!StringUtils.hasText(password)) {
            throw new RuntimeException("新密码不能为空");
        }
        if (password.length() < 8) {
            throw new RuntimeException("新密码至少需要 8 个字符");
        }

        int categoryCount = 0;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasNumber = false;
        boolean hasSpecial = false;
        for (int index = 0; index < password.length(); index++) {
            char current = password.charAt(index);
            if (Character.isUpperCase(current)) {
                hasUpper = true;
            } else if (Character.isLowerCase(current)) {
                hasLower = true;
            } else if (Character.isDigit(current)) {
                hasNumber = true;
            } else if (!Character.isWhitespace(current)) {
                hasSpecial = true;
            }
        }

        if (hasUpper) {
            categoryCount++;
        }
        if (hasLower) {
            categoryCount++;
        }
        if (hasNumber) {
            categoryCount++;
        }
        if (hasSpecial) {
            categoryCount++;
        }
        if (categoryCount < 2) {
            throw new RuntimeException("新密码需在大写字母、小写字母、数字、特殊符号中至少满足两类");
        }
    }

    private String encodeBase64(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("用户身份无效");
        }
    }

    private UserDTO getEnabledUserById(Long userId) {
        UserDTO user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }
        return user;
    }

    private UserProfileResponse toUserProfileResponse(UserDTO user, Long lastLoginAtMillis) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                resolveAccountStatusText(user.getStatus()),
                lastLoginAtMillis,
                toEpochMillis(user.getCreateTime())
        );
    }

    /**
     * 查询当前用户最近一次登录时间。
     *
     * <p>该字段用于账户中心顶部展示，后端从 user_session 表计算，
     * 再随用户资料写入 Redis 缓存，避免前端多设备并发时各自保存出不一致的时间。</p>
     */
    private Long queryLatestLoginAtMillis(Long userId) {
        UserSessionQueryParam queryParam = new UserSessionQueryParam();
        queryParam.setUserId(userId);
        return toEpochMillis(sessionMapper.selectLatestLoginAtByUserId(queryParam));
    }

    /**
     * 将数据库账号状态转换为前端展示文案。
     *
     * <p>数据库仍然保存稳定枚举值，页面展示由后端统一转换，
     * 这样后续如果新增 LOCKED、PENDING 等状态，不需要每个前端页面重复维护映射。</p>
     */
    private String resolveAccountStatusText(String status) {
        if (USER_STATUS_ENABLED.equals(status)) {
            return "正常";
        }
        if (USER_STATUS_DISABLED.equals(status)) {
            return "禁用";
        }
        return "未知";
    }

    /**
     * 签发会话时需要返回给前端的 refreshToken 信息。
     */
    private record IssuedSession(String refreshToken,
                                 String sessionId,
                                 Long refreshTokenExpiresAtMillis,
                                 Boolean sessionTakeover) {
    }
}
