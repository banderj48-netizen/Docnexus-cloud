package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway 跨域配置属性。
 *
 * <p>所有跨域策略统一在网关层处理，下游服务不再重复配置 CORS，避免多服务规则不一致。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway.cors")
public class GatewayCorsProperties {

    /**
     * 是否启用网关全局 CORS。
     */
    private Boolean enabled = true;

    /**
     * 允许访问的前端来源。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 允许的 HTTP 方法。
     */
    private List<String> allowedMethods = new ArrayList<>();

    /**
     * 允许的请求头。
     */
    private List<String> allowedHeaders = new ArrayList<>();

    /**
     * 暴露给浏览器读取的响应头。
     */
    private List<String> exposedHeaders = new ArrayList<>();

    /**
     * 是否允许携带 Cookie 等凭证。
     */
    private Boolean allowCredentials = false;

    /**
     * 预检请求缓存时间，单位秒。
     */
    private Long maxAgeSeconds = 3600L;
}
