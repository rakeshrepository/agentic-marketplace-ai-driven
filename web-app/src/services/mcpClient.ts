/**
 * MCP (Model Context Protocol) Client Implementation
 * 
 * This client connects to MCP servers (like Kafka MCP Server) via SSE
 * and implements the JSON-RPC 2.0 protocol for tool discovery and execution.
 */

export interface McpTool {
  name: string;
  description: string;
  inputSchema: {
    type: string;
    properties: Record<string, any>;
    required?: string[];
  };
}

export interface McpServerConfig {
  name: string;
  url: string;
  description: string;
}

export interface ToolCallResult {
  content: Array<{
    type: string;
    text: string;
  }>;
  isError?: boolean;
}

class McpClient {
  private serverUrl: string;
  private serverName: string;
  private requestId: number;

  constructor(serverUrl: string, serverName: string) {
    this.serverUrl = serverUrl;
    this.serverName = serverName;
    this.requestId = parseInt(import.meta.env.VITE_MCP_REQUEST_ID_START) || 1;
  }

  /**
   * Discover available tools from the MCP server
   */
  async listTools(): Promise<McpTool[]> {
    try {
      const response = await fetch(`${this.serverUrl}/mcp/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          jsonrpc: '2.0',
          id: this.requestId++,
          method: 'tools/list',
          params: {},
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      
      if (data.error) {
        throw new Error(`MCP Error: ${data.error.message}`);
      }

      return data.result?.tools || [];
    } catch (error) {
      console.error(`Failed to list tools from ${this.serverName}:`, error);
      return [];
    }
  }

  /**
   * Call a tool on the MCP server
   */
  async callTool(toolName: string, args: Record<string, any>): Promise<ToolCallResult> {
    try {
      const response = await fetch(`${this.serverUrl}/mcp/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          jsonrpc: '2.0',
          id: this.requestId++,
          method: 'tools/call',
          params: {
            name: toolName,
            arguments: args,
          },
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      
      if (data.error) {
        throw new Error(`Tool execution error: ${data.error.message}`);
      }

      return data.result || { content: [{ type: 'text', text: 'No result' }] };
    } catch (error) {
      console.error(`Failed to call tool ${toolName}:`, error);
      return {
        content: [{ type: 'text', text: `Error: ${error instanceof Error ? error.message : 'Unknown error'}` }],
        isError: true,
      };
    }
  }

  /**
   * Initialize connection to MCP server
   */
  async initialize(): Promise<boolean> {
    try {
      const response = await fetch(`${this.serverUrl}/mcp/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          jsonrpc: '2.0',
          id: this.requestId++,
          method: 'initialize',
          params: {
            protocolVersion: '2024-11-05',
            capabilities: {
              roots: { listChanged: true },
              sampling: {},
            },
            clientInfo: {
              name: 'agentic-marketplace-web',
              version: '1.0.0',
            },
          },
        }),
      });

      if (!response.ok) {
        return false;
      }

      const data = await response.json();
      return !data.error;
    } catch (error) {
      console.error(`Failed to initialize MCP connection to ${this.serverName}:`, error);
      return false;
    }
  }
}

/**
 * MCP Service Manager
 * Manages connections to multiple MCP servers
 */
export class McpService {
  private clients: Map<string, McpClient> = new Map();
  private initialized: boolean = false;

  constructor() {
    // Configure MCP servers
    const servers: McpServerConfig[] = [
      {
        name: 'kafka',
        url: import.meta.env.VITE_KAFKA_MCP_URL || '/mcp/kafka',
        description: 'Kafka MCP Server for topic management',
      },
      // Add more MCP servers here as needed
      // {
      //   name: 'database',
      //   url: import.meta.env.VITE_DATABASE_MCP_URL || '/mcp/database',
      //   description: 'Database MCP Server',
      // },
    ];

    // Initialize clients
    servers.forEach((server) => {
      this.clients.set(server.name, new McpClient(server.url, server.name));
    });
  }

  /**
   * Initialize all MCP server connections
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    const initPromises = Array.from(this.clients.entries()).map(
      async ([name, client]) => {
        const success = await client.initialize();
        if (success) {
          console.log(`✓ Connected to ${name} MCP server`);
        } else {
          console.warn(`✗ Failed to connect to ${name} MCP server`);
        }
      }
    );

    await Promise.all(initPromises);
    this.initialized = true;
  }

  /**
   * Get all available tools from all MCP servers
   */
  async getAllTools(): Promise<Array<McpTool & { server: string }>> {
    await this.initialize();

    const toolPromises = Array.from(this.clients.entries()).map(
      async ([serverName, client]) => {
        const tools = await client.listTools();
        return tools.map((tool) => ({ ...tool, server: serverName }));
      }
    );

    const toolArrays = await Promise.all(toolPromises);
    return toolArrays.flat();
  }

  /**
   * Call a tool on the appropriate MCP server
   */
  async callTool(
    serverName: string,
    toolName: string,
    args: Record<string, any>
  ): Promise<ToolCallResult> {
    await this.initialize();

    // Make server name lookup case-insensitive
    const serverNameLower = serverName.toLowerCase();
    const client = this.clients.get(serverNameLower);
    if (!client) {
      return {
        content: [{ type: 'text', text: `Unknown MCP server: ${serverName}` }],
        isError: true,
      };
    }

    return client.callTool(toolName, args);
  }

  /**
   * Get tools for a specific server
   */
  async getServerTools(serverName: string): Promise<McpTool[]> {
    await this.initialize();

    // Make server name lookup case-insensitive
    const serverNameLower = serverName.toLowerCase();
    const client = this.clients.get(serverNameLower);
    if (!client) {
      return [];
    }

    return client.listTools();
  }
}

// Export singleton instance
export const mcpService = new McpService();
