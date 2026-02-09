package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRequest {
    private Map<String, Object> data;
    private Map<String, Object> whereConditions;
}
