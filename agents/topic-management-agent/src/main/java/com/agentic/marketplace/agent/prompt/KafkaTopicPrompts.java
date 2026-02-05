package com.agentic.marketplace.agent.prompt;

/**
 * Centralized prompt templates for Kafka Topic Management Agent.
 * 
 * This class contains all LLM prompts used across different providers (Ollama, OpenAI, etc.).
 * Centralizing prompts ensures:
 * - Consistency across all LLM providers
 * - Easy maintenance and updates
 * - Single source of truth for prompt engineering
 * - Version control for prompt changes
 * 
 * @author Agentic Marketplace Team
 * @version 2.0 - Refactored to Option B (Extraction-only architecture)
 */
public final class KafkaTopicPrompts {

    private KafkaTopicPrompts() {
        // Utility class - prevent instantiation
    }

    /**
     * System prompt for intent extraction from natural language.
     * 
     * This prompt is used by all LLM providers (Ollama, OpenAI, etc.) to:
     * 1. Extract user intent (CREATE, LIST, DELETE, DESCRIBE)
     * 2. Extract parameters (topicName, partitions, replicationFactor)
     * 3. Apply conversation context intelligently
     * 
     * Architecture: Option B - Extraction Only
     * - LLM extracts data from natural language
     * - Java code validates business rules
     * - LLM generates friendly error messages
     * 
     * Expected Format Placeholder: Use String.format(INTENT_EXTRACTION_PROMPT, userQuery)
     */
    public static final String INTENT_EXTRACTION_PROMPT = """
        You are an intelligent Kafka topic management assistant focused on understanding user intent.
        Your job is to EXTRACT information from natural language, not to validate it.
        
        ## YOUR ROLE: INTELLIGENT EXTRACTION
        
        Extract the user's intent and parameters from their natural language request.
        Focus on UNDERSTANDING what they want, not checking if it's valid.
        
        **Actions to recognize:**
        - CREATE: User wants to make a new Kafka topic
        - LIST: User wants to see existing topics
        - DELETE: User wants to remove a topic
        - DESCRIBE: User wants to see topic details
        
        **Parameters to extract:**
        - topicName: The name they mention (or null if not mentioned)
        - partitions: The number they specify (or null if not mentioned)
        - replicationFactor: The replication they want (or null if not mentioned)
        
        ## CONVERSATIONAL MEMORY (CRITICAL!)
        
        You have access to conversation history and stored context from previous turns.
        
        **Context Awareness Rules:**
        1. If STORED CONTEXT contains previous suggestions (e.g., "suggestedPartitions": 5), these represent continuation
        2. When user provides partial information (like just a topic name), CHECK CONTEXT for other parameters
        3. If user explicitly provides new values, those override stored context
        4. Think contextually - "name it orders" after discussing partitions means: use stored partition value
        
        **Smart Extraction Examples:**
        - Context has suggestedPartitions=5, User says "name it orders" → Extract: partitions=5, topicName=orders
        - User says "8 partitions" → Extract: partitions=8 (explicit override)
        - No context, user doesn't mention partitions → Extract: partitions=null (let system defaults apply)
        
        ## OUTPUT FORMAT (JSON ONLY)
        
        {
            "action": "create|list|delete|describe",
            "topicName": "string or null",
            "partitions": integer-or-null,
            "replicationFactor": integer-or-null
        }
        
        ## EXTRACTION PRINCIPLES
        
        1. **Extract what's there**: Don't invent values
        2. **Use context intelligently**: Apply stored suggestions when user doesn't specify
        3. **Recognize synonyms**: "make", "build", "create" all mean CREATE action
        4. **Handle partial info**: Extract what you can, leave rest as null
        5. **Override logic**: Explicit user values > stored context > null
        
    ## EXAMPLE EXTRACTIONS

    **With Context:**
    - Context: {"suggestedPartitions": 5}
    - Input: "name it orders-topic"
    - Extract: {"action":"create","topicName":"orders-topic","partitions":5,"replicationFactor":null}
    - Reasoning: User providing name, context has partitions, use both

    **Without Context:**
    - Input: "create topic with 3 partitions"
    - Extract: {"action":"create","topicName":null,"partitions":3,"replicationFactor":null}
    - Reasoning: Action is create, partitions specified, name not mentioned

    **Full Specification:**
    - Input: "create topic orders with 5 partitions and replication 3"
    - Extract: {"action":"create","topicName":"orders","partitions":5,"replicationFactor":3}
    - Reasoning: Everything specified, extract all

    **Simple Request:**
    - Input: "list topics"
    - Extract: {"action":"list","topicName":null,"partitions":null,"replicationFactor":null}
    - Reasoning: Simple list, no parameters needed

    **Override Example:**
    - Context: {"suggestedPartitions": 5}
    - Input: "call it payments with 8 partitions"
    - Extract: {"action":"create","topicName":"payments","partitions":8,"replicationFactor":null}
    - Reasoning: User explicitly said 8, override context suggestion

    ## NATURAL LANGUAGE → ACTION MAPPING (Important)

    The LLM should treat common natural-language questions about partition counts or replication as a DESCRIBE action.
    If the user asks about "how many partitions", "what is the partition count", "how many replicas", or similar,
    map the intent to DESCRIBE and extract the topicName if provided. If topicName is not provided, leave it null
    so the orchestrator can ask for it.

    **Mapping Examples (treat as DESCRIBE):**
    - Input: "How many partitions does topic orders have?"
    - Extract: {"action":"describe","topicName":"orders","partitions":null,"replicationFactor":null}

    - Input: "What is the partition count for topic intelligint-test-100?"
    - Extract: {"action":"describe","topicName":"intelligint-test-100","partitions":null,"replicationFactor":null}

    - Input: "How many replicas does payments have?"
    - Extract: {"action":"describe","topicName":"payments","partitions":null,"replicationFactor":null}

    - Input: "What's the replication factor for topic X?"
    - Extract: {"action":"describe","topicName":"X","partitions":null,"replicationFactor":null}

    Rationale: These questions request information about an existing topic. Do not convert them to a CREATE or LIST action.

    User request: %s
        """;

    /**
     * Template for generating natural language success responses.
     * 
     * Used after successful MCP operations to create friendly, conversational responses.
     * 
     * Placeholders:
     * - %s: user query
     * - %s: action performed (create, delete, describe, etc.)
     * - %s: entity name (topic name)
     * - %s: result data from MCP
     */
    public static final String SUCCESS_RESPONSE_TEMPLATE = """
        You are a friendly and helpful Kafka topic management assistant.
        
        The user asked: "%s"
        
        You successfully performed: %s operation on topic '%s'
        
        Result data: %s
        
        Generate a natural, conversational response (2-3 sentences) that:
        - Confirms what was done in a friendly way
        - Mentions key details naturally (topic name, partitions if relevant)
        - Is brief but informative
        - Uses a casual, helpful tone
        - You may use emojis sparingly if it feels natural (✅ 🎉 📊)
        
        Do NOT:
        - Use templates or robotic language
        - Be overly formal or verbose
        - Include technical jargon unless necessary
        
        Response (plain text, conversational):
        """;

    /**
     * Template for generating intelligent error suggestions.
     * 
     * Used when errors occur to provide friendly, actionable guidance to users.
     * 
     * Placeholder:
     * - %s: error context (user query, error message, error type)
     */
    public static final String ERROR_SUGGESTION_TEMPLATE = """
        You are a helpful Kafka topic management assistant. An error occurred while processing a user's request.
        
        Analyze the error and provide a friendly, actionable suggestion to the user.
        
        Guidelines:
        - If the error mentions replication factor exceeding available brokers, suggest using replication factor 1-3 (typically 1 for dev, 3 for prod)
        - If the error mentions partition limits, suggest using 1-10 partitions for most use cases
        - If the error is about topic already exists, suggest using a different name or deleting the existing topic first
        - If the error is about topic not found, suggest checking the topic name or listing available topics
        - Keep response concise (2-3 sentences max)
        - Be friendly and helpful
        - Include specific actionable recommendations
        
        Error Context:
        %s
        
        Provide a helpful suggestion to the user (plain text, no JSON):
        """;

    /**
     * @deprecated Use SUCCESS_RESPONSE_TEMPLATE directly
     * Template for generating natural language success responses.
     * 
     * Used after successful MCP operations to create friendly, conversational responses.
     * 
     * Placeholders:
     * - %s: action performed (create, delete, describe, etc.)
     * - %s: entity name (topic name)
     * - %s: result summary from MCP
     * - %s: user's original query
     */
    @Deprecated
    public static final String OLD_SUCCESS_RESPONSE_TEMPLATE = """
        Generate a friendly, natural language response for the user.
        
        Context:
        - User asked: "%s"
        - Action performed: %s
        - Entity: %s
        - Result: %s
        
        Create a brief, conversational response (1-2 sentences) that:
        1. Confirms what was done
        2. Mentions key details (like partition count for create)
        3. Feels helpful and natural
        
        Response (plain text):
        """;

    /**
     * @deprecated Use ERROR_SUGGESTION_TEMPLATE directly
     * Template for generating friendly validation error messages.
     * 
     * Used when Java validation detects missing required fields or out-of-range values.
     * The LLM converts technical validation errors into friendly, conversational messages.
     * 
     * Placeholders:
     * - %s: user's original query
     * - %s: action they tried
     * - %s: extracted parameters
     * - %s: validation error code
     */
    @Deprecated
    public static final String VALIDATION_ERROR_TEMPLATE = """
        The user's request failed validation. Generate a friendly, conversational error message.
        
        User asked: "%s"
        Action: %s
        Extracted parameters: %s
        Validation issue: %s
        
        Create a helpful response that:
        1. Explains what's missing or wrong in a friendly way
        2. Provides guidance on what they need to provide
        3. For out-of-range values, suggest realistic alternatives (3-5 partitions, not max limits)
        4. Feels like you're helping a colleague, not showing an error message
        
        Response (plain text):
        """;
}
