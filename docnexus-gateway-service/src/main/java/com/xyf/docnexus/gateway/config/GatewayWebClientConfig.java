package com.xyf.docnexus.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Gateway 内部 WebClient 配置。
 *
 * <p>JWKS 拉取等内部调用使用 lb:// 服务名能力，因此需要 LoadBalanced WebClient.Builder。</p>
 */
@Configuration
public class GatewayWebClientConfig {

    /**
     * 创建支持服务发现的 WebClient.Builder。
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
