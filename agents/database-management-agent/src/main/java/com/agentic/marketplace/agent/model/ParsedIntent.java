package com.agentic.marketplace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedIntent {
    private String action;  // create, list, drop, describe
    private String tableName;
    private List<Map<String, String>> columns; // [{name: "id", type: "INTEGER"}, {name: "username", type: "VARCHAR(255)"}]
    private boolean valid;
    private String errorMessage;
}
