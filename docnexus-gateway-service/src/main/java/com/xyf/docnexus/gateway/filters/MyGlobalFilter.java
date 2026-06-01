package com.xyf.docnexus.gateway.filters;

import com.auth0.jwt.exceptions.JWTVerificationException;

import com.xyf.docnexus.gateway.config.GatewayAuthCacheProperties;
import com.xyf.docnexus.gateway.config.GatewayJwtProperties;
import com.xyf.docnexus.gateway.security.GatewayAuthCache;
import com.xyf.docnexus.gateway.security.ReactiveRedisTokenSessionStore;
import com.xyf.docnexus.gateway.util.JwtVerifyTool;
import com.xyf.docnexus.gateway.util.JwtVerifyTool.UserTokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 网关全局过滤器。
 *
 * <p>当前职责：
 * 1. 登录、注册、健康检查等白名单路径直接放行；
 * 2. 非白名单接口必须携带 Authorization: Bearer xxx；
 * 3. 校验通过后把用户身份写入请求头，再转发给下游服务。</p>
 */
@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MyGlobalFilter.class);

    private final GatewayJwtProperties jwtProperties;
    private final GatewayAuthCacheProperties authCacheProperties;
    private final JwtVerifyTool jwtVerifyTool;
    private final ReactiveRedisTokenSessionStore redisTokenSessionStore;
    private final GatewayAuthCache gatewayAuthCache;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public MyGlobalFilter(GatewayJwtProperties jwtProperties,
                          GatewayAuthCacheProperties authCacheProperties,
                          JwtVerifyTool jwtVerifyTool,
                          ReactiveRedisTokenSessionStore redisTokenSessionStore,
                          GatewayAuthCache gatewayAuthCache) {
        this.jwtProperties = jwtProperties;
        this.authCacheProperties = authCacheProperties;
        this.jwtVerifyTool = jwtVerifyTool;
        this.redisTokenSessionStore = redisTokenSessionStore;
        this.gatewayAuthCache = gatewayAuthCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String clientIp = resolveClientIp(request);

        // 白名单接口直接放行，例如登录、注册、找回密码、健康检查。
        if (isWhitePath(path)) {
            log.debug("网关白名单放行，path={}", path);
            ServerHttpRequest trustedRequest = buildTrustedClientIpRequest(request, clientIp);
            return chain.filter(exchange.mutate().request(trustedRequest).build());
        }

        String accessToken;
        try {
            // 先提取 Bearer token。普通接口需要用 token hash 查询 L1 本地缓存。
            accessToken = jwtVerifyTool.extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        } catch (IllegalArgumentException exception) {
            log.warn("网关 Authorization 解析失败，path={}, reason={}", path, exception.getMessage());
            return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
        }

        boolean strictPath = isStrictPath(path);
        String tokenHash = gatewayAuthCache.hashToken(accessToken, clientIp);
        if (!strictPath) {
            UserTokenPayload cachedPayload = gatewayAuthCache.getIfValid(tokenHash);
            if (cachedPayload != null) {
                ServerHttpRequest newRequest = buildTrustedUserRequest(request, cachedPayload);
                log.debug("网关 L1 鉴权缓存命中，path={}, userId={}", path, cachedPayload.userId());
                return chain.filter(exchange.mutate().request(newRequest).build());
            }
        }

        UserTokenPayload payload;
        try {
            // L1 未命中或强校验路径：执行 JWT RSA 验签、issuer 校验和过期时间校验。
            payload = jwtVerifyTool.verifyToken(accessToken);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            log.warn("网关 JWT 校验失败，path={}, reason={}", path, exception.getMessage());
            return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
        }

        // L2 Redis 校验：使用 MGET 一次读取 blacklist / access session / tokenVersion。
        return redisTokenSessionStore.isTokenActive(payload, clientIp)
                .flatMap(active -> {
                    if (!active) {
                        log.warn("网关拦截无效登录态，path={}, userId={}, jwtId={}",
                                path, payload.userId(), payload.jwtId());
                        return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
                    }

                    if (!strictPath) {
                        gatewayAuthCache.put(tokenHash, payload);
                    }

                    ServerHttpRequest newRequest = buildTrustedUserRequest(request, payload);

                    log.debug("网关鉴权通过，path={}, userId={}, role={}",
                            path, payload.userId(), payload.role());

                    return chain.filter(exchange.mutate().request(newRequest).build());
                });
    }


    @Override
    public int getOrder() {
        // 数字越小优先级越高，鉴权过滤器应尽量靠前执行。
        return 0;
    }

    /**
     * 判断当前请求路径是否在白名单中。
     */
    private boolean isWhitePath(String path) {
        return jwtProperties.getWhiteList()
                .stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 判断当前路径是否需要强校验。
     *
     * <p>强校验路径会绕过 L1 Caffeine 本地缓存，每次都执行 JWT 验签和 Redis MGET。
     * 退出登录、改密码、上传删除等高风险接口必须放在该列表中。</p>
     */
    private boolean isStrictPath(String path) {
        return authCacheProperties.getStrictPaths()
                .stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 构建带可信用户身份的新请求。
     *
     * <p>注意：必须先删除前端可能伪造的 X-User-* 请求头，
     * 再由网关注入可信身份。</p>
     */
    private ServerHttpRequest buildTrustedUserRequest(ServerHttpRequest request, UserTokenPayload payload) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-User-Role");
                    headers.remove("X-Access-Jti");
                    headers.remove("X-Client-IP");
                })
                .header("X-User-Id", String.valueOf(payload.userId()))
                .header("X-Username", payload.username() == null ? "" : payload.username())
                .header("X-User-Role", payload.role() == null ? "" : payload.role())
                .header("X-Access-Jti", payload.jwtId() == null ? "" : payload.jwtId())
                .header("X-Client-IP", resolveClientIp(request))
                .build();
    }

    /**
     * 为白名单请求注入可信客户端 IP。
     *
     * <p>登录和 refresh 虽然不需要 JWT 鉴权，但 user-service 需要用客户端 IP 计算 deviceId
     * 和校验 IP 绑定。因此网关仍要删除前端伪造的身份头和 X-Client-IP，再注入自己解析出的 IP。</p>
     */
    private ServerHttpRequest buildTrustedClientIpRequest(ServerHttpRequest request, String clientIp) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-User-Role");
                    headers.remove("X-Access-Jti");
                    headers.remove("X-Client-IP");
                })
                .header("X-Client-IP", clientIp)
                .build();
    }

    /**
     * 解析当前请求真实 IP。
     *
     * <p>这里优先使用 Gateway 直接看到的 remoteAddress，不信任客户端可伪造的 X-Forwarded-For。
     * 如果生产环境前面还有可信反向代理，需要在网关层统一清洗后再扩展该方法。</p>
     */
    private String resolveClientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "0.0.0.0";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 返回统一 401 JSON 响应。
     */
    private Mono<Void> writeUnauthorizedResponse(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

}
