package com.agentic.marketplace.sdk.service;

import com.agentic.marketplace.sdk.model.ConversationHistory;
import com.agentic.marketplace.sdk.model.ConversationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConversationService {
    
    private final Map<String, ConversationHistory> sessions = new ConcurrentHashMap<>();
    
    /**
     * Get or create a conversation session
     */
    public ConversationHistory getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            log.info("Creating new conversation session: {}", id);
            return ConversationHistory.builder()
                .sessionId(id)
                .messages(new ArrayList<>())
                .context(new HashMap<>())
                .lastActivity(LocalDateTime.now())
                .build();
        });
    }
    
    /**
     * Get a session if it exists, null otherwise
     */
    public ConversationHistory getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Check if a session exists
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    /**
     * Add a user message to the conversation
     */
    public void addUserMessage(String sessionId, String message) {
        addUserMessage(sessionId, message, null);
    }
    
    /**
     * Add a user message with metadata to the conversation
     */
    public void addUserMessage(String sessionId, String message, Map<String, Object> metadata) {
        ConversationHistory history = getOrCreateSession(sessionId);
        history.addMessage("user", message, metadata);
        log.debug("Added user message to session {}: {}", sessionId, message);
    }
    
    /**
     * Add an assistant response to the conversation
     */
    public void addAssistantMessage(String sessionId, String message) {
        addAssistantMessage(sessionId, message, null);
    }
    
    /**
     * Add an assistant response with metadata to the conversation
     */
    public void addAssistantMessage(String sessionId, String message, Map<String, Object> metadata) {
        ConversationHistory history = getOrCreateSession(sessionId);
        history.addMessage("assistant", message, metadata);
        
        // Store metadata as context for future reference
        if (metadata != null && !metadata.isEmpty()) {
            metadata.forEach(history::updateContext);
        }
        
        log.debug("Added assistant message to session {}: {}", sessionId, message);
    }
    
    /**
     * Update context for a session
     */
    public void updateContext(String sessionId, String key, Object value) {
        ConversationHistory history = getOrCreateSession(sessionId);
        history.updateContext(key, value);
        log.debug("Updated context for session {}: {}={}", sessionId, key, value);
    }
    
    /**
     * Update multiple context values at once
     */
    public void updateContext(String sessionId, Map<String, Object> contextUpdates) {
        if (contextUpdates != null && !contextUpdates.isEmpty()) {
            ConversationHistory history = getOrCreateSession(sessionId);
            contextUpdates.forEach(history::updateContext);
            log.debug("Updated context for session {} with {} values", sessionId, contextUpdates.size());
        }
    }
    
    /**
     * Get conversation context (for context-aware follow-ups)
     */
    public Map<String, Object> getContext(String sessionId) {
        ConversationHistory history = sessions.get(sessionId);
        if (history == null || history.getContext() == null) {
            return new HashMap<>();
        }
        return new HashMap<>(history.getContext()); // Return copy to prevent external modification
    }
    
    /**
     * Get a specific context value
     */
    public Object getContextValue(String sessionId, String key) {
        ConversationHistory history = sessions.get(sessionId);
        return history != null ? history.getContextValue(key) : null;
    }
    
    /**
     * Get all messages in a conversation
     */
    public List<ConversationMessage> getAllMessages(String sessionId) {
        ConversationHistory history = sessions.get(sessionId);
        if (history == null || history.getMessages() == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(history.getMessages());
    }
    
    /**
     * Get recent conversation history for LLM (last N messages)
     */
    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        ConversationHistory history = sessions.get(sessionId);
        if (history == null || history.getMessages() == null) {
            return Collections.emptyList();
        }
        
        List<ConversationMessage> messages = history.getMessages();
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }
    
    /**
     * Get conversation statistics
     */
    public int getMessageCount(String sessionId) {
        ConversationHistory history = sessions.get(sessionId);
        return history != null ? history.getMessageCount() : 0;
    }
    
    /**
     * Clear context for a session (keep messages)
     */
    public void clearContext(String sessionId) {
        ConversationHistory history = sessions.get(sessionId);
        if (history != null) {
            history.clearContext();
            log.info("Cleared context for session: {}", sessionId);
        }
    }
    
    /**
     * Clear a session completely (logout, timeout, explicit clear)
     */
    public void clearSession(String sessionId) {
        ConversationHistory removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Cleared session: {} with {} messages", sessionId, removed.getMessageCount());
        }
    }
    
    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Cleanup expired sessions (run periodically)
     * Default: Remove sessions inactive for more than 30 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredSessions() {
        cleanupExpiredSessions(30);
    }
    
    /**
     * Cleanup sessions inactive for more than specified minutes
     */
    public int cleanupExpiredSessions(int inactiveMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(inactiveMinutes);
        int removed = 0;
        
        Iterator<Map.Entry<String, ConversationHistory>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ConversationHistory> entry = iterator.next();
            if (entry.getValue().getLastActivity().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} expired conversation sessions (inactive > {} min)", removed, inactiveMinutes);
        }
        
        return removed;
    }
}
