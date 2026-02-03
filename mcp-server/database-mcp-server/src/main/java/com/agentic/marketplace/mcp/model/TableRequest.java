package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableRequest {
    private String tableName;
    private List<ColumnInfo> columns;
}
