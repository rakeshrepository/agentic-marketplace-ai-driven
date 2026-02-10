# MCP Client Implementation

This directory contains the **MCP (Model Context Protocol) Client** implementation for the web application.

## Architecture

```
┌──────────────────────────────────────────────────────┐
│           Web Application (MCP HOST)                 │
│  ┌────────────────────────────────────────────────┐  │
│  │         McpChatInterface.tsx                   │  │
│  │  - User interface for chat                     │  │
│  └────────────────────────────────────────────────┘  │
│                      ↓                                │
│  ┌────────────────────────────────────────────────┐  │
│  │         aiService.ts (LLM LAYER)               │  │
│  │  - Contains Ollama integration                 │  │
│  │  - Parses user intent                          │  │
│  │  - Generates responses                         │  │
│  └────────────────────────────────────────────────┘  │
│                      ↓                                │
│  ┌────────────────────────────────────────────────┐  │
│  │         mcpClient.ts (MCP CLIENT)              │  │
│  │  - Connects to MCP servers via SSE             │  │
│  │  - Discovers tools (tools/list)                │  │
│  │  - Executes tools (tools/call)                 │  │
│  │  - JSON-RPC 2.0 protocol                       │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
                      ↓ MCP Protocol
        ┌─────────────────────────────────┐
        │    Kafka MCP Server (8081)      │
        │  - create_topic                 │
        │  - list_topics                  │
        │  - describe_topic               │
        │  - delete_topic                 │
        │  - topic_exists                 │
        │  - cluster_overview             │
        └─────────────────────────────────┘
```

## Services

### 1. mcpClient.ts
The **MCP Protocol Client** that implements JSON-RPC 2.0 communication with MCP servers.

**Key Features:**
- Connects to multiple MCP servers
- Discovers available tools via `tools/list`
- Executes tools via `tools/call`
- Handles JSON-RPC 2.0 protocol

**Usage:**
```typescript
import { mcpService } from './services/mcpClient';

// Initialize connections
await mcpService.initialize();

// Get all available tools
const tools = await mcpService.getAllTools();

// Call a tool
const result = await mcpService.callTool(
  'kafka',           // Server name
  'create_topic',    // Tool name
  { topic_name: 'orders', partitions: 3 }  // Arguments
);
```

### 2. aiService.ts
The **AI/LLM Layer** that integrates Ollama with MCP tools.

**Key Features:**
- Integrates with Ollama for natural language understanding
- Parses user intent and maps to MCP tools
- Generates human-friendly responses
- Maintains conversation history

**Usage:**
```typescript
import { aiService } from './services/aiService';

// Chat with AI (will auto-discover and use MCP tools)
const response = await aiService.chat(
  "Create a Kafka topic called orders with 3 partitions"
);

console.log(response.message);
// "I've successfully created the Kafka topic 'orders' with 3 partitions for you!"
```

### 3. McpChatInterface.tsx
The **Chat UI Component** that provides the user interface.

**Features:**
- Full-screen chat interface
- Shows connected MCP servers and available tools
- Displays tool usage in messages
- Real-time typing indicators

## Environment Variables

Create a `.env` file in the `web-app` directory:

```bash
# MCP Server URLs
VITE_KAFKA_MCP_URL=http://localhost:8081
VITE_DATABASE_MCP_URL=http://localhost:8083

# Ollama Configuration (LLM)
VITE_OLLAMA_URL=http://localhost:11434
VITE_OLLAMA_MODEL=llama3

# Existing configs
VITE_API_BASE_URL=
VITE_AGENT_REGISTRY_URL=http://localhost:8090
```

## How It Works

### 1. User Types a Query
```
User: "Create a Kafka topic called orders with 3 partitions"
```

### 2. AI Service Processes Intent
```typescript
// aiService discovers available tools
const tools = await mcpService.getAllTools();
// [{ name: 'create_topic', server: 'kafka', ... }, ...]

// Ollama LLM analyzes the request
const intent = await parseIntentWithLLM(userMessage, tools);
// {
//   toolCall: {
//     name: 'create_topic',
//     server: 'kafka',
//     arguments: { topic_name: 'orders', partitions: 3 }
//   }
// }
```

### 3. MCP Client Executes Tool
```typescript
// Call the Kafka MCP Server
const result = await mcpService.callTool(
  'kafka',
  'create_topic',
  { topic_name: 'orders', partitions: 3 }
);
```

### 4. MCP Server Responds
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      { "type": "text", "text": "✓ Topic created: orders" }
    ]
  }
}
```

### 5. AI Service Generates Response
```typescript
// Ollama generates human-friendly response
const finalResponse = await generateResponse(userMessage, toolResult);
// "I've successfully created the Kafka topic 'orders' with 3 partitions for you!"
```

## Usage in Your App

### Option 1: Use the McpChatInterface Component

```typescript
import { McpChatInterface } from './components/McpChatInterface';

function App() {
  return <McpChatInterface />;
}
```

### Option 2: Use the Services Directly

```typescript
import { aiService } from './services/aiService';
import { mcpService } from './services/mcpClient';

// In your component
const handleUserQuery = async (query: string) => {
  const response = await aiService.chat(query);
  console.log(response.message);
};

// Or call MCP tools directly
const createKafkaTopic = async () => {
  const result = await mcpService.callTool(
    'kafka',
    'create_topic',
    { topic_name: 'test-topic', partitions: 1 }
  );
};
```

## Benefits of This Architecture

✅ **Standard MCP Pattern**: Follows official MCP architecture
✅ **Separation of Concerns**: LLM (client) vs Tool Execution (server)
✅ **Flexibility**: Swap LLMs without changing MCP servers
✅ **Scalability**: Add more MCP servers easily
✅ **No Spring Boot**: Pure MCP protocol, lightweight
✅ **Tool Discovery**: Dynamically discovers available tools
✅ **Natural Language**: Users speak naturally, AI maps to tools

## Testing

1. **Start MCP Servers**:
```bash
# Terminal 1: Start Kafka MCP Server
cd mcp-server/kafka-mcp-server
mvn spring-boot:run

# Terminal 2: Start Ollama
ollama serve
```

2. **Start Web App**:
```bash
cd web-app
npm install
npm run dev
```

3. **Test the Chat**:
- Open http://localhost:5173
- Type: "Create a Kafka topic called orders"
- Watch as the AI discovers the `create_topic` tool and executes it!

## Troubleshooting

### MCP Server Not Connecting
- Check if Kafka MCP Server is running on port 8081
- Verify VITE_KAFKA_MCP_URL in .env
- Check browser console for errors

### Ollama Not Responding
- Ensure Ollama is running: `ollama serve`
- Pull the model: `ollama pull llama3`
- Verify VITE_OLLAMA_URL in .env

### No Tools Discovered
- Check MCP server health: `curl http://localhost:8081/health`
- Verify tools/list endpoint: `curl -X POST http://localhost:8081/mcp/message -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'`

## Next Steps

1. ✅ Delete topic-management-agent (DONE)
2. ✅ Create MCP client layer (DONE)
3. ⏳ Test with running Kafka MCP server
4. ⏳ Add more MCP servers (database, etc.)
5. ⏳ Enhance LLM prompts for better tool selection
6. ⏳ Add streaming responses from Ollama
