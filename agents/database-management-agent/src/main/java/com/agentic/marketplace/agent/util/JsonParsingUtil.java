package com.agentic.marketplace.agent.util;

import com.agentic.marketplace.agent.model.ParsedIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JsonParsingUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static ParsedIntent parseJsonResponse(String jsonResponse) {
        try {
            // Clean up the response - remove markdown code blocks if present
            String cleanedJson = jsonResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();
            
            log.debug("Cleaned JSON: {}", cleanedJson);
            
            // Parse JSON to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(cleanedJson, Map.class);
            
            // Build ParsedIntent from map
            ParsedIntent.ParsedIntentBuilder builder = ParsedIntent.builder();
            
            // Extract basic fields
            if (jsonMap.containsKey("action")) {
                builder.action(normalizeString(jsonMap.get("action")));
            }
            if (jsonMap.containsKey("tableName")) {
                builder.tableName(normalizeString(jsonMap.get("tableName")));
            }
            if (jsonMap.containsKey("username")) {
                builder.username(normalizeString(jsonMap.get("username")));
            }
            if (jsonMap.containsKey("password")) {
                builder.password(normalizeString(jsonMap.get("password")));
            }
            if (jsonMap.containsKey("email")) {
                builder.email(normalizeString(jsonMap.get("email")));
            }
            if (jsonMap.containsKey("alterAction")) {
                builder.alterAction(normalizeString(jsonMap.get("alterAction")));
            }
            if (jsonMap.containsKey("orderBy")) {
                builder.orderBy(normalizeString(jsonMap.get("orderBy")));
            }
            
            // Extract lists
            if (jsonMap.containsKey("privileges")) {
                builder.privileges(parseStringList(jsonMap.get("privileges")));
            }
            if (jsonMap.containsKey("selectColumns")) {
                builder.selectColumns(parseStringList(jsonMap.get("selectColumns")));
            }
            if (jsonMap.containsKey("searchColumns")) {
                builder.searchColumns(parseStringList(jsonMap.get("searchColumns")));
            }
            
            // Extract columns
            if (jsonMap.containsKey("columns")) {
                builder.columns(parseColumns(jsonMap.get("columns")));
            }
            
            // Extract columnToAlter
            if (jsonMap.containsKey("columnToAlter")) {
                builder.columnToAlter(parseColumn(jsonMap.get("columnToAlter")));
            }
            
            // Extract data maps
            if (jsonMap.containsKey("data")) {
                builder.data(parseObjectMap(jsonMap.get("data")));
            }
            if (jsonMap.containsKey("whereConditions")) {
                builder.whereConditions(parseObjectMap(jsonMap.get("whereConditions")));
            }
            
            // Extract numbers
            if (jsonMap.containsKey("limit")) {
                builder.limit(parseInteger(jsonMap.get("limit")));
            }
            if (jsonMap.containsKey("offset")) {
                builder.offset(parseInteger(jsonMap.get("offset")));
            }
            
            // Note: No validation here - extracted intent will be validated by IntentValidator
            return builder.build();
            
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            return ParsedIntent.builder().build();
        }
    }
    
    private static String normalizeString(Object value) {
        if (value == null) return null;
        return value.toString().trim().toLowerCase();
    }
    
    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .toList();
        }
        return new ArrayList<>();
    }
    
    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> parseColumns(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            List<Map<String, String>> columns = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    Map<String, String> column = new HashMap<>();
                    ((Map<?, ?>) item).forEach((k, v) -> 
                        column.put(k.toString(), v != null ? v.toString() : null)
                    );
                    columns.add(column);
                }
            }
            return columns;
        }
        return new ArrayList<>();
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, String> parseColumn(Object value) {
        if (value == null) return new HashMap<>();
        if (value instanceof Map) {
            Map<String, String> column = new HashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> 
                column.put(k.toString(), v != null ? v.toString() : null)
            );
            return column;
        }
        return new HashMap<>();
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObjectMap(Object value) {
        if (value == null) return new HashMap<>();
        if (value instanceof Map) {
            Map<String, Object> map = new HashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> map.put(k.toString(), v));
            return map;
        }
        return new HashMap<>();
    }
    
    private static Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer: {}", value);
            return null;
        }
    }
    
    private JsonParsingUtil() {
        // Private constructor to prevent instantiation
    }
}
