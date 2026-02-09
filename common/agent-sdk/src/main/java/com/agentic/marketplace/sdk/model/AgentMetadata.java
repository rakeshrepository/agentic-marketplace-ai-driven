package com.agentic.marketplace.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentMetadata {
    private String id;
    private String name;
    private String description;
    private String category;
    private String endpoint;
    private String icon;
    private List<String> capabilities;
    private String status; // active, coming-soon, deprecated
}
