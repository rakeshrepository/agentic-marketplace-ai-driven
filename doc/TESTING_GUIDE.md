# 🧪 Kafka MCP Testing Guide

## ✅ **Current Status: PRODUCTION READY**

The Kafka MCP Server is integrated with VS Code using Claude Sonnet 4.5 via the GitHub Copilot extension.

---

## 📋 **Architecture Overview**

```
You (VS Code) → Claude 4.5 (GitHub Copilot) → MCP Server (STDIO) → Kafka (Docker)
```

**Current Stack:**
- **MCP Client**: VS Code with GitHub Copilot extension
- **AI Model**: Claude Sonnet 4.5
- **MCP Server**: Java-based Kafka admin tool (STDIO transport)
- **Infrastructure**: Docker containers (Kafka, Zookeeper, etc.)

---

## 📋 **Pre-requisites Checklist**

### ✅ **Already Done:**
- [x] MCP Server built (kafka-mcp-server JAR)
- [x] STDIO transport implemented (run-mcp-stdio.sh)
- [x] VS Code MCP configuration (.vscode/mcp.json)
- [x] Docker Compose setup
- [x] 7 Kafka admin tools available

### ⚠️ **You Need to Do:**
- [ ] Install GitHub Copilot extension in VS Code
- [ ] Build the MCP server
- [ ] Start Docker containers
- [ ] Configure and start MCP server in VS Code

---

## 🚀 **Step-by-Step Testing Instructions**

### **Step 1: Install GitHub Copilot Extension**

1. Open VS Code
2. Press `Cmd+Shift+X` (Mac) or `Ctrl+Shift+X` (Windows/Linux)
3. Search for "**GitHub Copilot**"
4. Click **Install**
5. Sign in with your GitHub account (requires Copilot subscription)

> **Note:** MCP server support is built into GitHub Copilot - no additional plugin needed!

---

### **Step 2: Build the MCP Server**

```bash
cd /Users/rgr/agentic-marketplace-ai-driven

# Build the project
./mvnw clean package -DskipTests

# Verify JAR was created
ls -la mcp-server/kafka-mcp-server/target/kafka-mcp-server-*.jar

# Make the launcher executable
chmod +x run-mcp-stdio.sh
```

Expected output: JAR file should exist (~50MB)

---

### **Step 3: Start Docker Infrastructure**

```bash
cd /Users/rgr/agentic-marketplace-ai-driven

# Start all services
docker-compose up -d

# Wait 30-60 seconds for services to be healthy
docker-compose ps
```

Expected status:
```
NAME                   STATUS
zookeeper              Up (healthy)
kafka                  Up (healthy)
kafka-ui               Up
kafka-mcp-server       Up
```

Verify Kafka is accessible:
```bash
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

---

### **Step 4: Configure MCP Server in VS Code**

The `.vscode/mcp.json` file is already configured:

```json
{
  "mcpServers": {
    "kafkaAdmin": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "args": [],
      "env": {
        "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092",
        "KAFKA_ADMIN_TIMEOUT": "10000"
      }
    }
  }
}
```

To start the MCP server:

1. Open **Command Palette** (`Cmd+Shift+P` or `Ctrl+Shift+P`)
2. Run: **`Developer: Reload Window`** (to load MCP configuration)
3. Open GitHub Copilot Chat panel
4. The MCP server should automatically start when needed

Alternative manual start:
1. Open **Command Palette**
2. Run: **`MCP: Restart Server`**

---

### **Step 5: Test Kafka Operations via Claude**

Open GitHub Copilot Chat in VS Code and try these commands:

#### **Test 1: List Topics**
```
List all Kafka topics
```

Expected: Claude uses the `list_topics` tool and shows you all topics.

#### **Test 2: Create Topic**
```
Create a Kafka topic called "orders" with 5 partitions
```

Expected: Claude calls `create_topic` and confirms creation.

#### **Test 3: Describe Topic**
```
Describe the "orders" topic
```

Expected: Shows partition count and other details.

#### **Test 4: Check if Topic Exists**
```
Does the topic "orders" exist?
```

Expected: Claude uses `topic_exists` and confirms.

#### **Test 5: Cluster Overview**
```
Show me the Kafka cluster overview
```

Expected: Displays cluster information.

#### **Test 6: Delete Topic**
```
Delete the Kafka topic "test-kafka"
```

Expected: Claude calls `delete_topic` and confirms deletion.

---

## 🔍 **Verification**

### **Check MCP Server Logs**

View VS Code output panel:
1. Open **Output** panel (`Cmd+Shift+U` or `Ctrl+Shift+U`)
2. Select **"Model Context Protocol"** from the dropdown
3. Look for:
   ```
   [info] Starting MCP server kafkaAdmin
   [info] MCP server kafkaAdmin started successfully
   ```

### **Check Kafka UI**

Open http://localhost:8088 in your browser to visually verify:
- Topics created/deleted
- Partition counts
- Cluster health

### **Test with Terminal**

Verify operations directly:
```bash
# List topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Describe a topic
docker exec -it kafka kafka-topics --describe --topic orders --bootstrap-server localhost:9092
```

---

## 🐛 **Troubleshooting**

### **Issue: "MCP server failed to start"**

Check logs in VS Code Output panel. Common issues:
- JAR file not built: Run `./mvnw clean package -DskipTests`
- Script not executable: Run `chmod +x run-mcp-stdio.sh`
- Java not found: Ensure Java 17+ is installed

### **Issue: "Kafka connection failed"**

```bash
# Check if Kafka container is running
docker ps | grep kafka

# Check Kafka logs
docker logs kafka

# Verify Kafka port is accessible
nc -zv localhost 9092
```

### **Issue: "MCP server keeps waiting for initialize response"**

This was fixed in the latest code. If you still see it:
1. Rebuild the server: `./mvnw clean package -DskipTests`
2. Restart VS Code: `Developer: Reload Window`
3. Check that you're using the latest code

### **Issue: "Tools not appearing"**

1. Open Command Palette
2. Run: `MCP: Restart Server`
3. Check Output panel for errors
4. Verify `.vscode/mcp.json` exists and is valid

---

## 📊 **Verification Checklist**

After testing, verify:

- [ ] ✅ GitHub Copilot extension installed
- [ ] ✅ JAR file built successfully
- [ ] ✅ Docker containers running (Kafka, Zookeeper)
- [ ] ✅ MCP server starts without errors
- [ ] ✅ Claude can list Kafka topics
- [ ] ✅ Claude can create topics
- [ ] ✅ Claude can describe topics
- [ ] ✅ Claude can delete topics
- [ ] ✅ Changes visible in Kafka UI (localhost:8088)
- [ ] ✅ Claude understands natural language requests

---

## 🎉 **Success Criteria**

You'll know it's working when:

1. ✅ You ask Claude to "list Kafka topics" and get actual topics
2. ✅ You ask to "create a topic" and it appears in Kafka
3. ✅ Changes are visible in Kafka UI (http://localhost:8088)
4. ✅ Claude provides intelligent responses based on Kafka state
5. ✅ No timeout errors in VS Code output panel

---

## 🔄 **Alternative Testing Methods**

### **Direct STDIO Test**

Test the MCP server directly without VS Code:

```bash
cd /Users/rgr/agentic-marketplace-ai-driven

# Run the test script
./test-mcp-response.sh
```

This sends a test JSON-RPC request and shows the response.

### **Manual JSON-RPC Test**

```bash
# Send an initialize request
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | ./run-mcp-stdio.sh

# Send a tools/list request
echo '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | ./run-mcp-stdio.sh
```

---

## 📝 **Available MCP Tools**

The Kafka MCP Server provides 7 tools:

1. **create_topic** - Create a new Kafka topic
2. **list_topics** - List all topics
3. **describe_topic** - Get detailed topic information
4. **update_topic** - Update partition count
5. **delete_topic** - Delete a topic (irreversible)
6. **topic_exists** - Check if a topic exists
7. **cluster_overview** - Get cluster information

---

## 🆘 **Need Help?**

If you encounter issues:

1. Check VS Code Output panel (Model Context Protocol)
2. Check Docker logs: `docker-compose logs -f kafka`
3. Verify ports: 9092 (Kafka), 8088 (Kafka UI)
4. Test Kafka independently: `docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092`
5. Rebuild MCP server if code changed: `./mvnw clean package -DskipTests`

**You're ready to test! Good luck! 🚀**
