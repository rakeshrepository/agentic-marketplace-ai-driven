# Agentic Marketplace AI-Driven

An MVP Agentic Marketplace with LLM-powered natural language processing, agent isolation, and a simple onboarding/config mechanism—all in a single compose file.

![Architecture Diagram](docs/images/architecture-diagram.svg)

## 📐 Layered Architecture

The system follows a modular, layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                            │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  React Web App (Vite + TypeScript)                     │     │
│  │  - AgentList, AgentCard, ChatInterface, SearchBar      │     │
│  │  - agentService, agentRegistry                         │     │
│  │  - Nginx Proxy (/agents/* routing)                     │     │
│  └────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                   ORCHESTRATION LAYER                            │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  Agent Registry Service (Spring Boot)                  │     │
│  │  - Agent CRUD operations                               │     │
│  │  - Self-registration endpoint                          │     │
│  │  - Agent metadata & capabilities management            │     │
│  │  - Status management (active/pending/coming-soon)      │     │
│  └────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                     AGENT LAYER                                  │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │ Topic Management Agent       │  │ Database Management Agent│ │
│  │ (Spring Boot)                │  │ (Spring Boot)            │ │
│  │ - AgentController            │  │ - AgentController        │ │
│  │ - AgentOrchestrator          │  │ - AgentOrchestrator      │ │
│  │ - OllamaService              │  │ - OllamaService          │ │
│  │ - McpKafkaService            │  │ - McpDatabaseService     │ │
│  └──────────────────────────────┘  └──────────────────────────┘ │
│                                                                   │
│  Self-registrable agents with:                                   │
│  - Natural language processing (Ollama integration)              │
│  - MCP client for tool execution                                 │
│  - Null filtering & response formatting                          │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                      AI/LLM LAYER                                │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  Ollama Service (llama3.2)                             │     │
│  │  - Intent parsing & understanding                      │     │
│  │  - Natural language to structured commands             │     │
│  │  - Response generation & formatting                    │     │
│  └────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                  TOOL/CAPABILITY LAYER (MCP)                     │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │ Kafka MCP Server             │  │ Database MCP Server      │ │
│  │ - Topic CRUD operations      │  │ - Query execution        │ │
│  │ - KafkaAdminService          │  │ - Schema operations      │ │
│  │ - Topic metadata retrieval   │  │ - Connection management  │ │
│  └──────────────────────────────┘  └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↕ Native Protocols
┌─────────────────────────────────────────────────────────────────┐
│                   INFRASTRUCTURE LAYER                           │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │ Apache Kafka + Zookeeper     │  │ PostgreSQL / MySQL       │ │
│  │ - Message broker             │  │ - Data storage           │ │
│  │ - Event streaming            │  │ - Relational data        │ │
│  └──────────────────────────────┘  └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      SHARED LAYER                                │
│  ┌────────────────────────────────────────────────────────┐     │
│  │  Agent SDK (Common Library)                            │     │
│  │  - AgentMetadata, AgentRegistry models                 │     │
│  │  - AgentRequest, AgentResponse DTOs                    │     │
│  │  - AgentRegistryLoader (agent-list.json)               │     │
│  │  - CategoryMetadata                                    │     │
│  └────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
```

### Key Principles

1. **Separation of Concerns**: Each layer has a distinct responsibility
2. **Loose Coupling**: Layers communicate via well-defined HTTP/REST APIs
3. **Extensibility**: New agents can be added without modifying existing layers
4. **Self-Registration**: Agents can register dynamically with pending approval flow
5. **Protocol Abstraction**: MCP layer abstracts different tools (Kafka, Database, etc.)

### Data Flow Example (User Query → Response)

```
1. User types "Create topic orders" in Web App
   ↓
2. ChatInterface sends POST to /agents/topic-management-agent/api/agent/query
   ↓
3. Agent's AgentController receives request
   ↓
4. AgentOrchestrator calls OllamaService for intent parsing
   ↓
5. Ollama LLM returns structured intent (action: "create_topic", params: {...})
   ↓
6. AgentOrchestrator calls McpKafkaService with parsed intent
   ↓
7. McpKafkaService sends HTTP request to Kafka MCP Server
   ↓
8. Kafka MCP Server executes KafkaAdminService.createTopic()
   ↓
9. Result flows back up: MCP → Agent → LLM (for summary) → Web App
   ↓
10. ChatInterface displays formatted response with null filtering
```

### Self-Registration Flow

```
1. External Agent → POST /api/agents/register (Agent Registry)
   - Payload: name, description, categoryId, endpointUrl, capabilities
   ↓
2. AgentService validates & transforms Docker URL to nginx path
   - e.g., http://my-agent:8080 → /agents/my-agent
   ↓
3. Agent stored with status="pending"
   ↓
4. Web App displays agent with "Pending Review" badge
   ↓
5. Admin approves → status updated to "active"
   ↓
6. Agent becomes fully operational in marketplace
```

## 📁 Project Structure

```
agentic-marketplace-ai-driven/
├── common/
│   └── agent-sdk/                    # Shared models and agent registry
├── agents/
│   └── topic-management-agent/       # LLM-powered Kafka agent
├── mcp-server/
│   └── kafka-mcp-server/             # Kafka admin REST API
├── web-app/                          # React + Vite frontend
├── docker-compose.yml                # All services
├── start.sh                          # Build and run script
└── pom.xml                           # Parent Maven POM
```

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- 8GB+ RAM recommended (for Ollama LLM)

### Start the Stack

```bash
# Make start script executable
chmod +x start.sh

# Start all services
./start.sh start

# Or with a custom LLM model
OLLAMA_MODEL=mistral ./start.sh start
```

### Access the Application

| Service | URL |
|---------|-----|
| **Web Application** | |
| Web App | http://localhost:3000 |
| **Agent Services** | |
| Topic Management Agent | http://localhost:8080/api/agent/health |
| Database Management Agent | http://localhost:8082/api/agent/health |
| Agent Registry Service | http://localhost:8090/api/health |
| **MCP Servers** | |
| Kafka MCP Server | http://localhost:8081/api/health |
| Database MCP Server | http://localhost:8083/api/health |
| Email MCP Server | http://localhost:8084/api/emails/health |
| **Infrastructure** | |
| Kafka Broker | localhost:9092 |
| Kafka UI | http://localhost:8088 |
| Zookeeper | localhost:2181 |
| PostgreSQL Database | localhost:5432 |
| MailHog Web UI | http://localhost:8025 |
| MailHog SMTP | localhost:1025 |
| Ollama LLM | http://localhost:11434 |

### Other Commands

```bash
./start.sh stop       # Stop all services
./start.sh logs       # View logs
./start.sh status     # Check status
./start.sh clean      # Clean up everything
```

## 💬 Using the Chat Interface

1. Open http://localhost:3000
2. Select "Kafka Topic Management Agent"
3. Try commands like:
   - "Create a topic called orders"
   - "List all topics"
   - "Describe topic orders"
   - "Delete topic orders"

## ⚙️ Configuration

### LLM Model
```bash
OLLAMA_MODEL=mistral ./start.sh start
```

### Agent Registry
Edit `common/agent-sdk/src/main/resources/agent-list.json` to add agents.

## 📜 License

MIT License
