# 🧪 Kafka MCP Testing Guide - End-to-End

## ✅ **Current Status: READY FOR TESTING**

Your UI is now **fully integrated** with the Kafka MCP implementation. Here's how to test it:

---

## 📋 **Pre-requisites Checklist**

### ✅ **Already Done:**
- [x] `.env` file created with MCP configuration
- [x] `McpChatInterface` component integrated in App.tsx
- [x] "MCP Kafka Chat" button added to UI header
- [x] MCP Client service configured
- [x] AI Service (Ollama integration) ready
- [x] Kafka MCP Server code completed
- [x] Kafka MCP registered in agent-list.json
- [x] Docker compose configured

### ⚠️ **You Need to Do:**
- [ ] Install Ollama (if not installed)
- [ ] Pull llama3 model
- [ ] Install Node.js dependencies
- [ ] Start services

---

## 🚀 **Step-by-Step Testing Instructions**

### **Step 1: Install Ollama (LLM)**

```bash
# macOS - Install Ollama
brew install ollama

# Or download from: https://ollama.ai/download

# Start Ollama service
ollama serve

# In a new terminal, pull the llama3 model
ollama pull llama3

# Verify it's working
curl http://localhost:11434/api/tags
```

Expected output: JSON with llama3 in the list

---

### **Step 2: Install Web App Dependencies**

```bash
cd /Users/rgr/agentic-marketplace-ai-driven/web-app

# Install dependencies
npm install

# Verify .env file exists
cat .env
```

Expected output: Should show MCP URLs and Ollama config

---

### **Step 3: Start Backend Services with Docker**

```bash
cd /Users/rgr/agentic-marketplace-ai-driven

# Start all services
docker-compose up -d

# Wait 30-60 seconds for services to be healthy, then verify
docker-compose ps
```

Expected status:
```
NAME                        STATUS
zookeeper                   Up (healthy)
kafka                       Up (healthy)
kafka-ui                    Up
postgres                    Up (healthy)
agent-registry              Up
kafka-mcp-server            Up
```

---

### **Step 4: Verify Kafka MCP Server is Running**

```bash
# Test health endpoint
curl http://localhost:8081/health

# Test MCP tools/list endpoint
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }'
```

Expected output:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "create_topic",
        "description": "Creates a new Kafka topic",
        ...
      },
      {
        "name": "list_topics",
        ...
      }
      // ... 9 tools total
    ]
  }
}
```

---

### **Step 5: Start Web App Development Server**

```bash
cd /Users/rgr/agentic-marketplace-ai-driven/web-app

# Start Vite dev server
npm run dev
```

Expected output:
```
VITE v5.0.12  ready in 523 ms

➜  Local:   http://localhost:5173/
➜  Network: use --host to expose
```

---

### **Step 6: Open UI and Test MCP Chat**

1. **Open browser:** http://localhost:5173

2. **You'll see the Agentic Marketplace home page with:**
   - ⚡ **"MCP Kafka Chat"** button (NEW - green/teal color)
   - ✨ "Register Your Agent" button
   - Kafka MCP agent card in the agent list

3. **Click the "⚡ MCP Kafka Chat" button**

4. **You'll see the MCP Chat interface with:**
   - Welcome message from AI assistant
   - Tool count indicator (should show available tools)
   - Connected servers list
   - Chat input box

---

### **Step 7: Test Kafka Operations via Natural Language**

Try these queries in the MCP Chat:

#### **Test 1: List Topics**
```
User: "Show me all Kafka topics"
or: "List all topics in the cluster"
```

Expected behavior:
- LLM analyzes the request
- Discovers `list_topics` tool from Kafka MCP Server
- Calls the tool via MCP protocol
- Returns list of topics (empty if none exist yet)

#### **Test 2: Create Topic**
```
User: "Create a Kafka topic called user-events with 3 partitions"
```

Expected behavior:
- LLM parses topic name and partition count
- Calls `create_topic` tool with arguments
- Returns success message
- Topic is created in Kafka

#### **Test 3: Describe Topic**
```
User: "Describe the user-events topic"
or: "Show me details of user-events"
```

Expected behavior:
- Calls `describe_topic` tool
- Returns partition count, replication factor, config

#### **Test 4: Check Cluster Health**
```
User: "Check cluster health"
or: "Show cluster overview"
```

Expected behavior:
- Calls `cluster_overview` tool
- Returns broker info, topic count, etc.

#### **Test 5: Delete Topic**
```
User: "Delete the test-topic"
```

Expected behavior:
- Calls `delete_topic` tool
- Confirms deletion

---

## 🔍 **Verify End-to-End Flow**

### **Browser Developer Console (F12)**

You should see logs like:
```
✓ MCP initialized: 9 tools from 1 servers
✓ Connected to kafka MCP server
```

### **Network Tab**

You should see requests to:
- `http://localhost:11434/api/chat` (Ollama LLM)
- `http://localhost:8081/mcp/message` (Kafka MCP Server)

### **MCP Chat Interface**

You should see:
- User messages in blue/right side
- Assistant responses in gray/left side
- Tool call indicators showing which tools were used

---

## 🎯 **Architecture Flow During Test**

```
1. User types: "Create topic orders"
   ↓
2. React UI → aiService.chat(message)
   ↓
3. aiService → mcpService.getAllTools()
   ↓
4. mcpClient → Kafka MCP Server (tools/list)
   ← Returns: 9 tools (create_topic, list_topics, etc.)
   ↓
5. aiService → Ollama LLM with tools list
   ← LLM decides: "I should call create_topic tool"
   ↓
6. aiService → mcpService.callTool('kafka', 'create_topic', {topicName: 'orders'})
   ↓
7. mcpClient → Kafka MCP Server (tools/call)
   ↓
8. Kafka MCP Server → KafkaAdminService.createTopic()
   ↓
9. KafkaAdminService → Kafka Cluster Admin API
   ← Topic created successfully
   ↓
10. Response flows back through stack
    ↓
11. UI displays: "✓ Topic 'orders' created successfully"
```

---

## 🐛 **Troubleshooting**

### **Issue: "Failed to connect to MCP server"**
```bash
# Check if Kafka MCP Server is running
docker logs kafka-mcp-server

# Check if port 8081 is accessible
curl http://localhost:8081/health
```

### **Issue: "Ollama not responding"**
```bash
# Check if Ollama is running
ps aux | grep ollama

# Restart Ollama
ollama serve

# Check if llama3 is pulled
ollama list
```

### **Issue: "No tools found"**
```bash
# Test MCP endpoint directly
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### **Issue: "Kafka connection failed"**
```bash
# Check Kafka health
docker logs kafka

# Verify Kafka is accessible
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### **Issue: "npm run dev fails"**
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
npm run dev
```

---

## 📊 **Verification Checklist**

After testing, verify:

- [ ] ✅ Ollama is running on port 11434
- [ ] ✅ Kafka cluster is healthy
- [ ] ✅ Kafka MCP Server is running on port 8081
- [ ] ✅ Agent Registry Service is running on port 8090
- [ ] ✅ Web app is accessible at port 5173
- [ ] ✅ "MCP Kafka Chat" button is visible
- [ ] ✅ MCP Chat shows "9 tools from 1 servers"
- [ ] ✅ Can list Kafka topics
- [ ] ✅ Can create Kafka topics
- [ ] ✅ Can describe topics
- [ ] ✅ Can delete topics
- [ ] ✅ LLM understands natural language

---

## 🎉 **Success Criteria**

You'll know it's working when:

1. ✅ You click "MCP Kafka Chat" and see the chat interface
2. ✅ Chat shows "9 tools from 1 servers" indicator
3. ✅ You type "Create a topic called test" and it actually creates the topic
4. ✅ You can verify the topic exists in Kafka UI (http://localhost:8088)
5. ✅ You can list topics and see your newly created topic
6. ✅ The LLM understands various phrasings of the same intent

---

## 🔄 **Alternative: Test via Agent Registry (Old Method)**

If you want to test via the agent card instead:

1. Click on the "Kafka Management (MCP)" card in the home page
2. This will use the old `ChatInterface` component
3. It connects to the agent endpoint, not directly to MCP

**Note:** The new MCP Chat method is recommended as it's the proper MCP implementation.

---

## 📝 **Next Steps After Successful Test**

Once Kafka MCP is working:

1. **Add Database MCP Server** (already discussed)
2. **Add Redis MCP Server** (follow same pattern)
3. **Enhance LLM prompts** for better tool selection
4. **Add streaming responses** for real-time chat
5. **Add conversation history persistence**
6. **Deploy to production**

---

## 🆘 **Need Help?**

If you encounter issues:

1. Check browser console (F12) for errors
2. Check docker logs: `docker-compose logs -f kafka-mcp-server`
3. Verify all ports are accessible
4. Ensure Ollama is running with llama3 model
5. Test each component independently before integration

**You're ready to test! Good luck! 🚀**
