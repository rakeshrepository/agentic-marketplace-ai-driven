# Kafka MCP Server

AI-powered Kafka management using the Model Context Protocol (MCP).

## 🎯 Overview

This project implements a **clean MCP architecture** where:
- **VS Code + GitHub Copilot** = MCP Client (with Claude Sonnet 4.5)
- **Kafka MCP Server** = Pure tool executor (NO LLM, stateless)
- **Infrastructure** = Docker containers (Kafka, Zookeeper, Kafka UI)

Users interact with Kafka infrastructure through natural language via Claude in VS Code.

---

## 📐 Architecture

```
┌─────────────────────────────────────────────────────┐
│        VS Code + GitHub Copilot (MCP Client)       │
│        Claude Sonnet 4.5 (AI Model)                 │
└────────────────┬────────────────────────────────────┘
                 │ JSON-RPC 2.0 over STDIO
┌────────────────▼────────────────────────────────────┐
│         Kafka MCP Server (Java, Stateless)          │
│  - 7 Kafka Admin Tools                              │
│  - STDIO Transport                                  │
│  - No LLM - Pure tool execution                     │
└────────────────┬────────────────────────────────────┘
                 │ Kafka Admin API
┌────────────────▼────────────────────────────────────┐
│         Kafka Infrastructure (Docker)               │
│  - Kafka Broker (9092)                              │
│  - Zookeeper (2181)                                 │
│  - Kafka UI (8088)                                  │
└─────────────────────────────────────────────────────┘
```

### Data Flow

```
1. You type in VS Code: "Create a Kafka topic called orders with 5 partitions"
   ↓
2. Claude (via GitHub Copilot) analyzes the request
   ↓
3. Claude discovers `create_topic` tool from MCP server
   ↓
4. Claude calls the tool via JSON-RPC: 
   tools/call("create_topic", {topicName: "orders", partitions: 5})
   ↓
5. Kafka MCP Server executes KafkaAdminService.createTopic()
   ↓
6. Topic is created in Kafka cluster
   ↓
7. Result flows back: MCP → Claude → You
   ↓
8. Claude responds: "✓ Topic 'orders' created with 5 partitions"
```

---

## 📦 Services

| Service | Port | Description |
|---------|------|-------------|
| **Kafka MCP Server** | 8081 | MCP tools for Kafka admin |
| **Kafka UI** | 8088 | Kafka management web interface |
| **Kafka Broker** | 9092 | Kafka cluster |
| **Zookeeper** | 2181 | Kafka coordination |

---

## 🚀 Quick Start

### Prerequisites

- **Docker & Docker Compose**
- **VS Code** with **GitHub Copilot** extension
- **Java 17+** (for building MCP server)
- **GitHub Copilot subscription**

### Step 1: Start Infrastructure

```bash
# Start all Docker containers
docker-compose up -d

# Verify services are healthy (wait 30-60 seconds)
docker-compose ps
```

### Step 2: Build MCP Server

```bash
# Build the Kafka MCP Server
./mvnw clean package -DskipTests

# Verify JAR was created
ls -la mcp-server/kafka-mcp-server/target/kafka-mcp-server-*.jar

# Make launcher executable
chmod +x run-mcp-stdio.sh
```

### Step 3: Configure VS Code

1. Install **GitHub Copilot** extension in VS Code
2. Open this workspace in VS Code
3. Open Command Palette (`Cmd+Shift+P` or `Ctrl+Shift+P`)
4. Run: **`Developer: Reload Window`** (loads MCP configuration)

The `.vscode/mcp.json` configuration is pre-configured.

### Step 4: Use in VS Code

Open GitHub Copilot Chat and try:

```
List all Kafka topics
Create a Kafka topic called "orders" with 5 partitions
Describe the "orders" topic
Delete the topic "test-kafka"
Show me the Kafka cluster overview
```

Claude will use the MCP tools to execute these operations!

---

## 🎯 Key Features

- ✅ **Clean MCP Architecture**: AI (Claude) in VS Code, tools in Java server
- ✅ **7 Kafka Admin Tools**: Full topic management via natural language
- ✅ **STDIO Transport**: Efficient subprocess communication
- ✅ **No External API Costs**: Uses GitHub Copilot's Claude
- ✅ **Docker Ready**: One command to run all infrastructure
- ✅ **Kafka UI**: Visual verification at http://localhost:8088

---

## 📝 Available MCP Tools

The Kafka MCP Server provides 7 tools:

1. **create_topic** - Create new Kafka topics with custom partitions/replication
2. **list_topics** - List all topics in the cluster
3. **describe_topic** - Get detailed topic information (partitions, config)
4. **update_topic** - Update partition count
5. **delete_topic** - Delete topics (irreversible!)
6. **topic_exists** - Check if a topic exists
7. **cluster_overview** - Get cluster metadata and statistics

---

## 📚 Documentation

- **[VS Code Integration Guide](doc/VSCODE_INTEGRATION.md)** - Complete setup, configuration, and troubleshooting
- **[Testing Guide](doc/TESTING_GUIDE.md)** - Step-by-step testing instructions
- **[MCP Language Decision Guide](doc/MCP_LANGUAGE_DECISION_GUIDE.md)** - Java vs Python vs Go vs TypeScript comparison (includes why agentic frameworks aren't needed for IDE-integrated MCP)
- **[Memory Implementation Guide](doc/MEMORY_IMPLEMENTATION_GUIDE.md)** - Building memory layer: RAG, session, preferences, audit
- **[Configuration Reference](doc/CONFIGURATION_REFERENCE.md)** - All configuration options
- **[Memory Design Proposal](doc/MEMORY_DESIGN_PROPOSAL.md)** - Future memory system design
- **[Tool Design FAQ](doc/TOOL_DESIGN_FAQ.md)** - Best practices for MCP tool design

---

## 📁 Project Structure

```
kafka-mcp-server/
├── .vscode/
│   └── mcp.json                      # VS Code MCP configuration
├── mcp-server/
│   └── kafka-mcp-server/             # Java MCP server (STDIO)
│       ├── src/main/java/.../
│       │   ├── StdioKafkaMcpServer.java   # Main STDIO server
│       │   └── KafkaAdminService.java     # Kafka operations
│       └── target/
│           └── kafka-mcp-server-*.jar     # Built JAR
├── doc/                              # Documentation
├── docker-compose.yml                # All infrastructure services
├── run-mcp-stdio.sh                  # MCP server launcher (for VS Code)
├── test-mcp-response.sh              # Manual testing script
└── pom.xml                           # Parent Maven POM
```

---

## 🔍 Verification

### Check Infrastructure

```bash
# View running containers
docker-compose ps

# Check Kafka topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# View Kafka UI
open http://localhost:8088
```

### Check MCP Server

View VS Code Output panel:
1. Open **Output** panel (`Cmd+Shift+U` or `Ctrl+Shift+U`)
2. Select **"Model Context Protocol"** from dropdown
3. Look for startup messages

### Test Manually

```bash
# Test the MCP server directly
./test-mcp-response.sh

# Or send manual JSON-RPC request
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | ./run-mcp-stdio.sh
```

---

## 🐛 Troubleshooting

### MCP Server won't start
- Ensure JAR is built: `./mvnw clean package -DskipTests`
- Check Java version: `java -version` (must be 17+)
- Make script executable: `chmod +x run-mcp-stdio.sh`

### Kafka connection failed
- Verify Kafka is running: `docker ps | grep kafka`
- Check Kafka logs: `docker logs kafka`
- Test connectivity: `nc -zv localhost 9092`

### Tools not appearing in VS Code
- Reload window: `Developer: Reload Window`
- Restart MCP server: `MCP: Restart Server`
- Check Output panel for errors

---

## 🔄 Development

### Rebuild MCP Server

```bash
./mvnw clean package -DskipTests
```

Then restart MCP server in VS Code:
- Command Palette → `MCP: Restart Server`

### View Logs

```bash
# View all container logs
docker-compose logs -f

# View specific service
docker logs -f kafka
docker logs -f kafka-mcp-server
```

### Stop Services

```bash
docker-compose down
```

---

## 🆘 Support

For issues or questions:

1. Check **[TESTING_GUIDE.md](doc/TESTING_GUIDE.md)** for common problems
2. Check **[VSCODE_INTEGRATION.md](doc/VSCODE_INTEGRATION.md)** for VS Code setup
3. Review VS Code Output panel (Model Context Protocol)
4. Check Docker logs: `docker-compose logs -f`

---

## 📜 License

MIT License

---

## 🎉 What Makes This Special

This project demonstrates a **proper MCP architecture**:

1. **Clean Separation**: AI intelligence (Claude) is separate from tool execution (Java MCP server)
2. **Stateless Tools**: MCP server has no LLM, just executes operations
3. **STDIO Transport**: Efficient subprocess communication (standard for MCP)
4. **No API Costs**: Uses your GitHub Copilot subscription
5. **Real Infrastructure**: Actual Kafka cluster management, not mock data
6. **IDE Integration**: Natural language commands directly in your development environment

**You can extend this pattern to any infrastructure tool: databases, cloud services, monitoring tools, etc.**
