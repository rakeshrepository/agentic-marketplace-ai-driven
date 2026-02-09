package com.agentic.marketplace.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlterTableRequest {
    private String action; // ADD_COLUMN, DROP_COLUMN, MODIFY_COLUMN
    private ColumnInfo column;
}
