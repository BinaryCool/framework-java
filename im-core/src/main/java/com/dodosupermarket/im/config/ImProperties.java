package com.dodosupermarket.im.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * No credentials in toString or response DTOs. Disabled until explicitly configured.
 */
@Getter
@Setter
@ConfigurationProperties("dudu.im")
public class ImProperties {
    private boolean enabled;
    // 区分客户端与管理后台的 Redis Session；其他身份域由其自己的服务核验。
    private String sessionRealm = "app";
    private String jdbcUrl = "";
    private String jdbcUsername = "";
    private String jdbcPassword = "";
    // 每个应用实例独立限流，避免连接准备和租约巡检反复建立数据库物理连接。
    private int jdbcPoolSize = 8;
    private String productUrl = "http://127.0.0.1:5001";
    private String serverUrl = "ws://127.0.0.1:5200";
    private long leaseSeconds = 900;
    private long sweepMs = 5000;
    // Private persistent volume. Blank keeps images disabled; never expose this directory as static files.
    private String attachmentDirectory = "";
    // Enable only after the existing business DAO scopes have been verified for the deployment.
    private boolean cardsEnabled;
    // Existing application account IDs -> explicitly assigned customer account IDs.
    // This is server configuration, never a role selected by the client.
    private java.util.Map<Long, java.util.List<Long>> agentScopes = new java.util.HashMap<>();
}
