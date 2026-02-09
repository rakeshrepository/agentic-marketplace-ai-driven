package com.agentic.marketplace.registry.controller;

import com.agentic.marketplace.registry.model.Agent;
import com.agentic.marketplace.registry.model.AgentRegistrationRequest;
import com.agentic.marketplace.registry.model.AgentRegistrationResponse;
import com.agentic.marketplace.registry.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {
    private final AgentService agentService;
    
    @GetMapping
    public ResponseEntity<List<Agent>> getAllAgents(@RequestParam(required = false) String status) {
        List<Agent> agents = status != null && status.equals("active") 
            ? agentService.getActiveAgents() 
            : agentService.getAllAgents();
        return ResponseEntity.ok(agents);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Agent> getAgentById(@PathVariable String id) {
        return agentService.getAgentById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Agent>> getAgentsByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(agentService.getAgentsByCategory(categoryId));
    }
    
    @PostMapping("/register")
    public ResponseEntity<AgentRegistrationResponse> registerAgent(@RequestBody AgentRegistrationRequest request) {
        try {
            // Validate required fields
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new AgentRegistrationResponse(false, "Agent name is required", null));
            }
            
            if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new AgentRegistrationResponse(false, "Description is required", null));
            }
            
            if (request.getCategoryId() == null || request.getCategoryId().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new AgentRegistrationResponse(false, "Category is required", null));
            }
            
            if (request.getEndpointUrl() == null || request.getEndpointUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new AgentRegistrationResponse(false, "Endpoint URL is required", null));
            }
            
            Agent agent = agentService.registerAgent(request);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AgentRegistrationResponse(
                    true, 
                    "Agent registered successfully! It will be reviewed and activated soon.", 
                    agent.getId()
                ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new AgentRegistrationResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AgentRegistrationResponse(false, "Failed to register agent: " + e.getMessage(), null));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable String id) {
        try {
            boolean deleted = agentService.deleteAgent(id);
            if (deleted) {
                return ResponseEntity.ok().body(new AgentRegistrationResponse(
                    true,
                    "Agent deleted successfully",
                    id
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new AgentRegistrationResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AgentRegistrationResponse(false, "Failed to delete agent: " + e.getMessage(), null));
        }
    }
}
