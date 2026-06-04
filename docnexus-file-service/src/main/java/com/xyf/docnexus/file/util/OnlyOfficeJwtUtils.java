package com.xyf.docnexus.file.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OnlyOffice HS256 JWT 工具。
 *
 * <p>这里不复用用户登录 JWT，OnlyOffice 只使用共享密钥签发编辑器配置、源文件地址和回调地址 token。</p>
 */
@Component
public class OnlyOfficeJwtUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /**
     * 签发 HS256 JWT。
     */
    public String sign(Map<String, Object> payload, String secret, long ttlSeconds) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>(payload == null ? Map.of() : payload);
            if (ttlSeconds > 0) {
                claims.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
            }
            String header = base64Json(Map.of("alg", "HS256", "typ", "JWT"));
            String body = base64Json(claims);
            String signingInput = header + "." + body;
            return signingInput + "." + signInput(signingInput, secret);
        } catch (Exception exception) {
            throw new IllegalStateException("签发 OnlyOffice JWT 失败", exception);
        }
    }

    /**
     * 验证并解析 HS256 JWT。
     */
    public Map<String, Object> verify(String token, String secret) {
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("OnlyOffice token 不能为空");
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("OnlyOffice token 格式不合法");
            }
            String signingInput = parts[0] + "." + parts[1];
            String expected = signInput(signingInput, secret);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("OnlyOffice token 签名无效");
            }
            Map<String, Object> claims = OBJECT_MAPPER.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            Object exp = claims.get("exp");
            if (exp instanceof Number number && number.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("OnlyOffice token 已过期");
            }
            return claims;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("解析 OnlyOffice token 失败", exception);
        }
    }

    /**
     * 将对象序列化为 Base64Url JSON。
     */
    private String base64Json(Object value) throws Exception {
        return URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
    }

    /**
     * 对 JWT header.payload 进行 HMAC-SHA256 签名。
     */
    private String signInput(String signingInput, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }
}
