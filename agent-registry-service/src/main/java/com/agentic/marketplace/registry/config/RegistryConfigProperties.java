package com.agentic.marketplace.registry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Agent Registry initialization
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent-registry")
public class RegistryConfigProperties {
    
    /**
     * Data initialization settings
     */
    private DataInit dataInit = new DataInit();
    
    /**
     * Category color mapping
     */
    private Map<String, String> categoryColors = new HashMap<>();
    
    @Data
    public static class DataInit {
        /**
         * Minimum categories expected in the database
         */
        private int minCategories = 9;
        
        /**
         * Minimum agents expected in the database
         */
        private int minAgents = 27;
        
        /**
         * Whether to clear and reinitialize if partial data found
         */
        private boolean reinitializeOnPartialData = true;
    }
    
    /**
     * Get category color by category ID
     */
    public String getCategoryColor(String categoryId) {
        return categoryColors.getOrDefault(categoryId, "#4ECDC4");
    }
}
