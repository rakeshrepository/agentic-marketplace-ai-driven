package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    private List<String> columns;
    private Map<String, Object> whereConditions;
    private String orderBy;
    private Integer limit;
    private Integer offset;
}
