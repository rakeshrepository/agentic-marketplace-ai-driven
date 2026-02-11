package com.agentic.marketplace.mcp;

import lombok.Data;

/**
 * HTTP Server Configuration
 * All values are loaded from environment variables or have sensible defaults
 */
@Data
public class HttpServerConfig {
    private final int port;
    private final int shutdownTimeoutSeconds;
    private final int backlog;
    
    // CORS Configuration
    private final boolean corsEnabled;
    private final String corsAllowedOrigins;
    private final String corsAllowedMethods;
    private final String corsAllowedHeaders;
    
    // Response Configuration
    private final String defaultContentType;
    private final int statusSuccess;
    private final int statusNoContent;
    private final int statusMethodNotAllowed;
    private final int statusInternalError;
    
    public HttpServerConfig() {
        this.port = getEnvAsInt("HTTP_SERVER_PORT", 8081);
        this.shutdownTimeoutSeconds = getEnvAsInt("HTTP_SERVER_SHUTDOWN_TIMEOUT", 5);
        this.backlog = getEnvAsInt("HTTP_SERVER_BACKLOG", 0);
        
        this.corsEnabled = getEnvAsBoolean("HTTP_CORS_ENABLED", true);
        this.corsAllowedOrigins = getEnv("HTTP_CORS_ALLOWED_ORIGINS", "*");
        this.corsAllowedMethods = getEnv("HTTP_CORS_ALLOWED_METHODS", "POST, OPTIONS");
        this.corsAllowedHeaders = getEnv("HTTP_CORS_ALLOWED_HEADERS", "Content-Type");
        
        this.defaultContentType = getEnv("HTTP_RESPONSE_CONTENT_TYPE", "application/json");
        this.statusSuccess = getEnvAsInt("HTTP_STATUS_SUCCESS", 200);
        this.statusNoContent = getEnvAsInt("HTTP_STATUS_NO_CONTENT", 204);
        this.statusMethodNotAllowed = getEnvAsInt("HTTP_STATUS_METHOD_NOT_ALLOWED", 405);
        this.statusInternalError = getEnvAsInt("HTTP_STATUS_INTERNAL_ERROR", 500);
    }
    
    private static String getEnv(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }
    
    private static int getEnvAsInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static boolean getEnvAsBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }
}
