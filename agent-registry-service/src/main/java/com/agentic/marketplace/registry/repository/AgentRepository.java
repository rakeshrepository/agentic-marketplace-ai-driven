package com.agentic.marketplace.registry.repository;

import com.agentic.marketplace.registry.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
    List<Agent> findByCategoryId(String categoryId);
    List<Agent> findByStatus(String status);
}
