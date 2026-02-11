package com.agentic.marketplace.registry.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "agents")
public class Agent {
    @Id
    private String id;
    
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category_id")
    private String categoryId;
    
    @Column(length = 50)
    private String status;
    
    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;
    
    @Column(name = "health_check_path")
    private String healthCheckPath;
    
    @Column(length = 50)
    private String icon;
    
    @Column(length = 50)
    private String color;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<AgentCapability> capabilities = new ArrayList<>();
    
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<AgentExampleQuery> exampleQueries = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
