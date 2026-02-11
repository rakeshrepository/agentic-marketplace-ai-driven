/**
 * AI Service - Integrates LLM (Ollama) with MCP tools
 * 
 * This service acts as the "MCP Host" - it contains the LLM logic
 * and orchestrates tool calls to MCP servers.
 */

import { mcpService, McpTool } from './mcpClient';

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
  toolCalls?: ToolCall[];
  toolResults?: ToolResult[];
}

export interface ToolCall {
  id: string;
  name: string;
  arguments: Record<string, any>;
  server: string;
}

export interface ToolResult {
  toolCallId: string;
  content: string;
  isError?: boolean;
}

export interface ChatResponse {
  message: string;
  toolCalls?: ToolCall[];
  reasoning?: string;
}

class AIService {
  private ollamaUrl: string;
  private model: string;
  private temperature: number;
  private numPredict: number;
  private conversationHistory: Map<string, ChatMessage[]> = new Map();

  constructor() {
    // Use relative proxy URL so it works from browser
    this.ollamaUrl = import.meta.env.VITE_OLLAMA_URL || '/ollama';
    this.model = import.meta.env.VITE_OLLAMA_MODEL || 'llama3.2:latest';
    this.temperature = parseFloat(import.meta.env.VITE_OLLAMA_TEMPERATURE) || 0.7;
    this.numPredict = parseInt(import.meta.env.VITE_OLLAMA_NUM_PREDICT) || 500;
  }

  /**
   * Parse natural language intent and map to MCP tools
   */
  private async parseIntentWithLLM(
    userMessage: string,
    availableTools: Array<McpTool & { server: string }>
  ): Promise<{ toolCall?: ToolCall; reasoning: string }> {
    const toolDescriptions = availableTools
      .map(
        (tool) =>
          `- ${tool.name} (${tool.server}): ${tool.description}\n  Parameters: ${JSON.stringify(
            tool.inputSchema.properties,
            null,
            2
          )}`
      )
      .join('\n\n');

    const systemPrompt = `You are an AI assistant that helps users interact with various services through tools. 
Available tools:
${toolDescriptions}

Analyze the user's request and determine if you need to use any tools. If yes, respond with:
TOOL_CALL: <tool_name>
SERVER: <server_name>
ARGUMENTS: <JSON arguments>
REASONING: <why you chose this tool>

If no tool is needed, respond normally.`;

    try {
      console.log(`[AI Service] Calling Ollama at: ${this.ollamaUrl}/api/generate with model: ${this.model}`);
      
      const response = await fetch(`${this.ollamaUrl}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: this.model,
          prompt: `${systemPrompt}\n\nUser: ${userMessage}\n\nAssistant:`,
          stream: false,
          options: {
            temperature: this.temperature,
            num_predict: this.numPredict,
          },
        }),
      });

      if (!response.ok) {
        throw new Error(`Ollama returned status ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      console.log('[AI Service] Ollama response received:', data);
      const llmResponse = data.response;

      // Parse LLM response for tool calls
      const toolCallMatch = llmResponse.match(/TOOL_CALL:\s*(\w+)/);
      const serverMatch = llmResponse.match(/SERVER:\s*(\w+)/);
      const argsMatch = llmResponse.match(/ARGUMENTS:\s*({[\s\S]*?})/);
      const reasoningMatch = llmResponse.match(/REASONING:\s*(.+?)(?:\n|$)/);

      if (toolCallMatch && serverMatch && argsMatch) {
        return {
          toolCall: {
            id: crypto.randomUUID(),
            name: toolCallMatch[1],
            server: serverMatch[1],
            arguments: JSON.parse(argsMatch[1]),
          },
          reasoning: reasoningMatch ? reasoningMatch[1].trim() : 'Using tool based on request',
        };
      }

      return {
        reasoning: llmResponse,
      };
    } catch (error) {
      console.error('Failed to parse intent with LLM:', error);
      return {
        reasoning: 'I encountered an error processing your request.',
      };
    }
  }

  /**
   * Generate a natural language response from tool results
   */
  private async generateResponse(
    userMessage: string,
    toolResults: ToolResult[]
  ): Promise<string> {
    const resultsText = toolResults
      .map((result) => `Tool result: ${result.content}`)
      .join('\n');

    const prompt = `User asked: ${userMessage}

Tool execution results:
${resultsText}

Generate a friendly, natural language response to the user based on these results. Be concise and helpful.`;

    try {
      const response = await fetch(`${this.ollamaUrl}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: this.model,
          prompt,
          stream: false,
        }),
      });

      const data = await response.json();
      return data.response;
    } catch (error) {
      console.error('Failed to generate response:', error);
      return resultsText; // Fallback to raw results
    }
  }

  /**
   * Main chat method - handles user queries with MCP tool integration
   */
  async chat(userMessage: string, sessionId?: string): Promise<ChatResponse> {
    const sid = sessionId || 'default';

    // Initialize conversation history if needed
    if (!this.conversationHistory.has(sid)) {
      this.conversationHistory.set(sid, []);
    }

    const history = this.conversationHistory.get(sid)!;
    history.push({ role: 'user', content: userMessage });

    try {
      // Step 1: Get available tools from all MCP servers
      const availableTools = await mcpService.getAllTools();

      if (availableTools.length === 0) {
        return {
          message: "I'm sorry, I don't have access to any tools right now. Please check if the MCP servers are running.",
        };
      }

      // Step 2: Use LLM to understand intent and decide on tool usage
      const { toolCall, reasoning } = await this.parseIntentWithLLM(
        userMessage,
        availableTools
      );

      // Step 3: If tool call is needed, execute it
      if (toolCall) {
        const toolResult = await mcpService.callTool(
          toolCall.server,
          toolCall.name,
          toolCall.arguments
        );

        const resultText = toolResult.content
          .map((c) => c.text)
          .join('\n');

        // Step 4: Generate natural language response from tool results
        const finalResponse = await this.generateResponse(userMessage, [
          {
            toolCallId: toolCall.id,
            content: resultText,
            isError: toolResult.isError,
          },
        ]);

        history.push({
          role: 'assistant',
          content: finalResponse,
          toolCalls: [toolCall],
        });

        return {
          message: finalResponse,
          toolCalls: [toolCall],
          reasoning,
        };
      }

      // No tool needed - return reasoning as response
      history.push({ role: 'assistant', content: reasoning });
      
      return {
        message: reasoning,
      };
    } catch (error) {
      const errorMsg = `I encountered an error: ${error instanceof Error ? error.message : 'Unknown error'}`;
      history.push({ role: 'assistant', content: errorMsg });
      
      return {
        message: errorMsg,
      };
    }
  }

  /**
   * Get conversation history for a session
   */
  getHistory(sessionId: string = 'default'): ChatMessage[] {
    return this.conversationHistory.get(sessionId) || [];
  }

  /**
   * Clear conversation history
   */
  clearHistory(sessionId: string = 'default'): void {
    this.conversationHistory.delete(sessionId);
  }

  /**
   * Simple direct call to Ollama (without MCP tools)
   */
  async simpleChat(message: string): Promise<string> {
    try {
      const response = await fetch(`${this.ollamaUrl}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: this.model,
          prompt: message,
          stream: false,
        }),
      });

      const data = await response.json();
      return data.response;
    } catch (error) {
      console.error('Failed to call Ollama:', error);
      return 'I encountered an error processing your request.';
    }
  }
}

// Export singleton instance
export const aiService = new AIService();
