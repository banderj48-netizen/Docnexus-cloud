package com.xyf.docnexus.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Gateway 安全基础配置。
 *
 * <p>网关使用 Bearer Token，不依赖 Cookie Session，所以禁用 CSRF、formLogin、httpBasic 和默认 logout。
 * 实际 JWT 鉴权仍由 MyGlobalFilter 完成，Spring Security 只负责关闭默认安全行为并放行请求进入网关链路。</p>
 */
@Configuration
public class GatewaySecurityConfig {

    private final GatewayCorsProperties corsProperties;

    public GatewaySecurityConfig(GatewayCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 配置 WebFlux Security 链，避免默认 Basic 认证拦截网关请求。
     */
    @Bean
    public SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }

    /**
     * 配置全局 CORS，前端跨域访问只需要通过 Gateway。
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (Boolean.TRUE.equals(corsProperties.getEnabled())) {
            configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
            configuration.setAllowedMethods(corsProperties.getAllowedMethods());
            configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
            configuration.setExposedHeaders(corsProperties.getExposedHeaders());
            configuration.setAllowCredentials(Boolean.TRUE.equals(corsProperties.getAllowCredentials()));
            configuration.setMaxAge(corsProperties.getMaxAgeSeconds());
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
