# Agentic Marketplace AI-Driven - Kafka MCP Focus# Agentic Marketplace AI-Driven



A self-service marketplace with AI-powered Kafka management using the Model Context Protocol (MCP).An MVP Agentic Marketplace with LLM-powered natural language processing, agent isolation, and a simple onboarding/config mechanism—all in a single compose file.



## 🎯 Overview![Architecture Diagram](docs/images/architecture-diagram.svg)



This project implements a **clean MCP architecture** where:## 📐 Layered Architecture

- **Web App** = MCP Host (contains LLM - Ollama)

- **Kafka MCP Server** = Pure tool executor (NO LLM, stateless)The system follows a modular, layered architecture with clear separation of concerns:

- Users interact with Kafka through natural language in the web UI

```

## 📐 Architecture┌─────────────────────────────────────────────────────────────────┐

│                    PRESENTATION LAYER                            │

```│  ┌────────────────────────────────────────────────────────┐     │

┌───────────────────────────────────────────────────────────┐│  │  React Web App (Vite + TypeScript)                     │     │

│                    WEB APPLICATION                        ││  │  - AgentList, AgentCard, ChatInterface, SearchBar      │     │

│                     (MCP HOST)                            ││  │  - agentService, agentRegistry                         │     │

│  ┌─────────────────────────────────────────────────────┐  ││  │  - Nginx Proxy (/agents/* routing)                     │     │

│  │  React Frontend (Vite + TypeScript)                 │  ││  └────────────────────────────────────────────────────────┘     │

│  │  - Chat Interface                                   │  │└─────────────────────────────────────────────────────────────────┘

│  │  - Agent Registry UI                                │  │                              ↕ HTTP/REST

│  │  - MCP Client Implementation                        │  │┌─────────────────────────────────────────────────────────────────┐

│  └─────────────────────────────────────────────────────┘  ││                   ORCHESTRATION LAYER                            │

│                         │                                  ││  ┌────────────────────────────────────────────────────────┐     │

│  ┌─────────────────────────────────────────────────────┐  ││  │  Agent Registry Service (Spring Boot)                  │     │

│  │  LLM Layer (Ollama)                                 │  ││  │  - Agent CRUD operations                               │     │

│  │  - Natural language understanding                   │  ││  │  - Self-registration endpoint                          │     │

│  │  - Tool discovery & selection                       │  ││  │  - Agent metadata & capabilities management            │     │

│  │  - Response generation                              │  ││  │  - Status management (active/pending/coming-soon)      │     │

│  └─────────────────────────────────────────────────────┘  ││  └────────────────────────────────────────────────────────┘     │

└───────────────────────────────────────────────────────────┘└─────────────────────────────────────────────────────────────────┘

                          │                              ↕ HTTP/REST

                          │ JSON-RPC 2.0 over SSE┌─────────────────────────────────────────────────────────────────┐

                          │ (MCP Protocol)│                     AGENT LAYER                                  │

                          ↓│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │

┌───────────────────────────────────────────────────────────┐│  │ Topic Management Agent       │  │ Database Management Agent│ │

│              KAFKA MCP SERVER (Port 8081)                 ││  │ (Spring Boot)                │  │ (Spring Boot)            │ │

│                    (NO LLM, STATELESS)                    ││  │ - AgentController            │  │ - AgentController        │ │

│  ┌─────────────────────────────────────────────────────┐  ││  │ - AgentOrchestrator          │  │ - AgentOrchestrator      │ │

│  │  6 MCP Tools:                                       │  ││  │ - OllamaService              │  │ - OllamaService          │ │

│  │  - create_topic                                     │  ││  │ - McpKafkaService            │  │ - McpDatabaseService     │ │

│  │  - list_topics                                      │  ││  └──────────────────────────────┘  └──────────────────────────┘ │

│  │  - describe_topic                                   │  ││                                                                   │

│  │  - delete_topic                                     │  ││  Self-registrable agents with:                                   │

│  │  - topic_exists                                     │  ││  - Natural language processing (Ollama integration)              │

│  │  - cluster_overview                                 │  ││  - MCP client for tool execution                                 │

│  └─────────────────────────────────────────────────────┘  ││  - Null filtering & response formatting                          │

└───────────────────────────────────────────────────────────┘└─────────────────────────────────────────────────────────────────┘

                          │                              ↕ HTTP/REST

                          ↓┌─────────────────────────────────────────────────────────────────┐

                   [Kafka Cluster]│                      AI/LLM LAYER                                │

```│  ┌────────────────────────────────────────────────────────┐     │

│  │  Ollama Service (llama3.2)                             │     │

## 🚀 Quick Start│  │  - Intent parsing & understanding                      │     │

│  │  - Natural language to structured commands             │     │

```bash│  │  - Response generation & formatting                    │     │

# 1. Start Ollama locally│  └────────────────────────────────────────────────────────┘     │

ollama pull mistral└─────────────────────────────────────────────────────────────────┘

                              ↕ HTTP/REST

# 2. Start all services┌─────────────────────────────────────────────────────────────────┐

docker-compose up -d│                  TOOL/CAPABILITY LAYER (MCP)                     │

│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │

# 3. Access web UI│  │ Kafka MCP Server             │  │ Database MCP Server      │ │

open http://localhost:3000│  │ - Topic CRUD operations      │  │ - Query execution        │ │

```│  │ - KafkaAdminService          │  │ - Schema operations      │ │

│  │ - Topic metadata retrieval   │  │ - Connection management  │ │

## 📦 Services│  └──────────────────────────────┘  └──────────────────────────┘ │

└─────────────────────────────────────────────────────────────────┘

| Service | Port | Description |                              ↕ Native Protocols

|---------|------|-------------|┌─────────────────────────────────────────────────────────────────┐

| Web UI | 3000 | Main application |│                   INFRASTRUCTURE LAYER                           │

| Agent Registry | 8090 | Agent metadata API |│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │

| Kafka MCP Server | 8081 | MCP tools for Kafka |│  │ Apache Kafka + Zookeeper     │  │ PostgreSQL / MySQL       │ │

| Kafka UI | 8088 | Kafka management |│  │ - Message broker             │  │ - Data storage           │ │

| Kafka Broker | 9092 | Kafka cluster |│  │ - Event streaming            │  │ - Relational data        │ │

│  └──────────────────────────────┘  └──────────────────────────┘ │

## 🎯 Key Features└─────────────────────────────────────────────────────────────────┘



- ✅ **Clean MCP Architecture**: LLM in client, tools in server┌─────────────────────────────────────────────────────────────────┐

- ✅ **6 Kafka Tools**: Full topic management via natural language│                      SHARED LAYER                                │

- ✅ **Ollama Integration**: Local LLM, no external API costs│  ┌────────────────────────────────────────────────────────┐     │

- ✅ **Self-Service UI**: Agent registration and chat interface│  │  Agent SDK (Common Library)                            │     │

- ✅ **Docker Ready**: One command to run everything│  │  - AgentMetadata, AgentRegistry models                 │     │

│  │  - AgentRequest, AgentResponse DTOs                    │     │

## 📚 Documentation│  │  - AgentRegistryLoader (agent-list.json)               │     │

│  │  - CategoryMetadata                                    │     │

See [MCP Official Docs](https://modelcontextprotocol.io) for protocol details.│  └────────────────────────────────────────────────────────┘     │

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
