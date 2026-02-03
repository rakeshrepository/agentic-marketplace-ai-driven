package com.agentic.marketplace.registry.model;

import lombok.Data;
import java.util.List;

@Data
public class AgentRegistrationRequest {
    private String name;
    private String description;
    private String categoryId;
    private String endpointUrl;
    private String icon;
    private String color;
    private String developerName;
    private String developerEmail;
    private String developerOrganization;
    private List<CapabilityRequest> capabilities;
    private List<String> exampleQueries;
    
    @Data
    public static class CapabilityRequest {
        private String capabilityId;
        private String capabilityName;
        private String description;
    }
}
