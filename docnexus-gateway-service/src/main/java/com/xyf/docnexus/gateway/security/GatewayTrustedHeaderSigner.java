package com.xyf.docnexus.gateway.security;

import com.xyf.docnexus.gateway.config.GatewayTrustedHeaderProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gateway 可信请求头签名工具。
 *
 * <p>签名内容只包含身份路由所需的轻量字段，下游服务用同样密钥复算签名即可判断请求头是否被伪造。</p>
 */
@Component
public class GatewayTrustedHeaderSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final GatewayTrustedHeaderProperties properties;

    public GatewayTrustedHeaderSigner(GatewayTrustedHeaderProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成内部可信头签名。
     */
    public String sign(String requestId, String timestamp, String clientIp, String userId, String accessJti) {
        String payload = String.join("|",
                safe(requestId),
                safe(timestamp),
                safe(clientIp),
                safe(userId),
                safe(accessJti)
        );
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getSignSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("生成 Gateway 可信头签名失败", exception);
        }
    }

    /**
     * 避免空值造成签名串不稳定。
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
