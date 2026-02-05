package com.agentic.marketplace.agent.util;

import com.agentic.marketplace.agent.model.ParsedIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing JSON responses from LLM providers.
 * Centralizes JSON parsing logic to avoid duplication across different LLM service implementations.
 */
@Slf4j
public final class JsonParsingUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonParsingUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Parse JSON response string into ParsedIntent object.
     * 
     * @param jsonResponse JSON string from LLM
     * @return ParsedIntent with extracted fields, or empty ParsedIntent on parse failure
     */
    public static ParsedIntent parseJsonResponse(String jsonResponse) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(jsonResponse);
            
            return ParsedIntent.builder()
                    .action(getTextValue(node, "action"))
                    .topicName(getTextValue(node, "topicName"))
                    .partitions(getIntValue(node, "partitions"))
                    .replicationFactor(getIntValue(node, "replicationFactor"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            // Return empty intent on parse failure - orchestrator will handle validation
            return ParsedIntent.builder().build();
        }
    }

    /**
     * Extract text value from JSON node.
     * 
     * @param node JsonNode to extract from
     * @param field Field name to extract
     * @return Field value as String, or null if field is missing or null
     */
    public static String getTextValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    /**
     * Extract integer value from JSON node.
     * 
     * @param node JsonNode to extract from
     * @param field Field name to extract
     * @return Field value as Integer, or null if field is missing or null
     */
    public static Integer getIntValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }
}
