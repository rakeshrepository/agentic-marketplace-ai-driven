# Agentic Marketplace AI-Driven

An MVP Agentic Marketplace with LLM-powered natural language processing, agent isolation, and a simple onboarding/config mechanism—all in a single compose file.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web App (React)                          │
│                        http://localhost:3000                     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Topic Management Agent                         │
│                     http://localhost:8080                        │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │
│  │  REST API   │────▶│   Ollama    │────▶│ MCP Client  │       │
│  │             │     │   Service   │     │             │       │
│  └─────────────┘     └─────────────┘     └─────────────┘       │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
┌───────────────────────┐       ┌───────────────────────────────┐
│    Ollama LLM         │       │     Kafka MCP Server          │
│ http://localhost:11434│       │    http://localhost:8081      │
│    (llama3.2)         │       │                               │
└───────────────────────┘       └───────────────┬───────────────┘
                                                │
                                                ▼
                                ┌───────────────────────────────┐
                                │          Kafka                │
                                │    localhost:9092             │
                                │         + Zookeeper           │
                                └───────────────────────────────┘
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
| Web App | http://localhost:3000 |
| Agent API | http://localhost:8080/api/agent/health |
| MCP Server | http://localhost:8081/api/health |
| Kafka | localhost:9092 |
| Ollama | http://localhost:11434 |

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
