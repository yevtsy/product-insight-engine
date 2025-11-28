package com.google.ai.insight.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for MCP (Model Context Protocol) servers.
 * Supports both DataHub and Vertica MCP servers.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpServerProperties {

    private DataHubConfig datahub = new DataHubConfig();
    private VerticaConfig vertica = new VerticaConfig();

    @Data
    public static class DataHubConfig {
        private boolean enabled = true;
        private String command = "npx";
        private String serverPackage = "@acryldata/mcp-server-datahub";
        private String gmsUrl;
        private String token;
        private int timeout = 30000; // 30 seconds
        private int maxRetries = 3;
    }

    @Data
    public static class VerticaConfig {
        private boolean enabled = true;
        private String command = "npx";
        private String serverPackage = "mcp-vertica";
        private String host;
        private int port = 5433;
        private String database;
        private String username;
        private String password;
        private int timeout = 30000; // 30 seconds
        private int maxRetries = 3;
    }
}